package com.battery.monitor;

import android.content.Context;
import android.graphics.Color;
import android.os.BatteryManager;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Reads real-time charging and power-draw metrics from BatteryManager
 * and sysfs nodes (multiple Samsung/Qualcomm fallback paths).
 *
 * Terminology used throughout:
 *   input  = power arriving from the wall charger
 *   charge = power stored into the battery cell
 *   draw   = power consumed by the phone's SoC + screen + radios
 *
 * When plugged in:  draw = input - charge  (some input goes to device, rest to battery)
 * When on battery:  draw = |battery discharge current| × battery voltage
 */
public class PowerReader {

    // Sysfs candidates for battery current (µA, negative = discharging on most Samsung)
    private static final String[] BATT_CURRENT_PATHS = {
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/bms/current_now",
    };

    // Sysfs candidates for battery voltage (µV)
    private static final String[] BATT_VOLTAGE_PATHS = {
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/bms/voltage_now",
    };

    // Sysfs candidates for charger input current (µA)
    private static final String[] INPUT_CURRENT_PATHS = {
        "/sys/class/power_supply/usb/input_current_settled",
        "/sys/class/power_supply/usb/current_now",
        "/sys/class/power_supply/ac/current_now",
        "/sys/class/power_supply/battery/input_current_now",
        "/sys/class/power_supply/main/current_now",
    };

    // Sysfs candidates for charger input voltage (µV)
    private static final String[] INPUT_VOLTAGE_PATHS = {
        "/sys/class/power_supply/usb/voltage_now",
        "/sys/class/power_supply/ac/voltage_now",
        "/sys/class/power_supply/main/voltage_now",
    };

    public static class Snapshot {
        /** Battery current in mA. Positive = charging, negative = discharging. */
        public float battCurrentMa;
        /** Battery terminal voltage in V */
        public float battVoltageV;
        /** Charger input current in mA (0 when not plugged in) */
        public float inputCurrentMa;
        /** Charger input voltage in V (0 when not plugged in) */
        public float inputVoltageV;
        /** Power going INTO the battery cell (W). Always >= 0. */
        public float chargePowerW;
        /** Power drawn by the phone hardware (W). Always >= 0. */
        public float deviceDrawW;
        /** Total input power from the wall (W). 0 when on battery. */
        public float inputPowerW;
        /** Source description for debug / UI footnote */
        public String currentSource;
        public boolean isCharging;
    }

    public static Snapshot read(Context ctx, int batteryVoltageMv, boolean plugged) {
        Snapshot s = new Snapshot();
        s.isCharging = plugged;

        // --- Battery voltage ---
        float sysfsVoltageUv = readSysfsLong(BATT_VOLTAGE_PATHS);
        if (sysfsVoltageUv > 0) {
            s.battVoltageV = sysfsVoltageUv / 1_000_000f;
        } else {
            s.battVoltageV = batteryVoltageMv / 1000f;
        }
        if (s.battVoltageV < 2.5f) s.battVoltageV = batteryVoltageMv / 1000f; // sanity

        // --- Battery current ---
        long bmCurrentUa = Long.MIN_VALUE;
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            bmCurrentUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        }

        long sysfsBattCurrentUa = readSysfsLong(BATT_CURRENT_PATHS);
        long battCurrentUa;

        if (sysfsBattCurrentUa != Long.MIN_VALUE && sysfsBattCurrentUa != 0) {
            battCurrentUa = sysfsBattCurrentUa;
            s.currentSource = "sysfs";
        } else if (bmCurrentUa != Long.MIN_VALUE && bmCurrentUa != 0) {
            battCurrentUa = bmCurrentUa;
            s.currentSource = "BatteryManager";
        } else {
            battCurrentUa = 0;
            s.currentSource = "unavailable";
        }

        // Normalise sign: positive = charging into battery
        // Some Samsung kernels report negative when charging — detect and flip
        if (plugged && battCurrentUa < -10_000) {
            battCurrentUa = -battCurrentUa;
        }
        if (!plugged && battCurrentUa > 10_000) {
            battCurrentUa = -battCurrentUa;
        }

        s.battCurrentMa = battCurrentUa / 1000f;

        // --- Input current & voltage from charger sysfs ---
        long inputCurrentUa = readSysfsLong(INPUT_CURRENT_PATHS);
        long inputVoltageUv  = readSysfsLong(INPUT_VOLTAGE_PATHS);

        if (plugged && inputCurrentUa > 0) {
            s.inputCurrentMa = inputCurrentUa / 1000f;
            s.inputVoltageV  = (inputVoltageUv > 0)
                    ? inputVoltageUv / 1_000_000f
                    : 5.0f; // USB default fallback
            s.inputPowerW = s.inputCurrentMa * s.inputVoltageV / 1000f;
        }

        // --- Derive charge power and device draw ---
        if (plugged && s.battCurrentMa > 0) {
            // Power going into battery cell
            s.chargePowerW = s.battCurrentMa * s.battVoltageV / 1000f;

            if (s.inputPowerW > 0) {
                // Device draw = what the charger delivers minus what goes to battery
                s.deviceDrawW = Math.max(0, s.inputPowerW - s.chargePowerW);
            } else {
                // No sysfs input power — estimate device draw from typical 88% charger efficiency
                float estimatedInputW = s.chargePowerW / 0.88f;
                s.deviceDrawW = Math.max(0, estimatedInputW - s.chargePowerW);
                s.inputPowerW = estimatedInputW;
            }
        } else if (!plugged) {
            // On battery: all power comes from discharge
            float dischargeMa = Math.abs(s.battCurrentMa);
            s.deviceDrawW = dischargeMa * s.battVoltageV / 1000f;
            s.chargePowerW = 0;
            s.inputPowerW  = 0;
        } else {
            // Plugged but current ≈ 0 (full / trickle)
            s.chargePowerW = 0;
            s.deviceDrawW  = (s.inputPowerW > 0) ? s.inputPowerW : 0;
        }

        return s;
    }

    /** Returns charging speed label matching Ampere-style tiers */
    public static String speedLabel(float currentMa, boolean plugged) {
        if (!plugged || currentMa <= 0) return "Not charging";
        if (currentMa < 100)  return "Trickle";
        if (currentMa < 800)  return "Slow";
        if (currentMa < 1500) return "Normal";
        if (currentMa < 3000) return "Fast";
        if (currentMa < 4500) return "Super Fast";
        return "Super Fast 2.0";          // Samsung 45 W territory
    }

    public static int speedColor(float currentMa, boolean plugged) {
        if (!plugged || currentMa <= 0) return Color.parseColor("#888888");
        if (currentMa < 800)  return Color.parseColor("#FF9800");  // amber
        if (currentMa < 1500) return Color.parseColor("#8BC34A");  // light green
        if (currentMa < 3000) return Color.parseColor("#4CAF50");  // green
        if (currentMa < 4500) return Color.parseColor("#00BCD4");  // cyan
        return Color.parseColor("#E040FB");                         // purple — blazing fast
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static long readSysfsLong(String[] candidates) {
        for (String path : candidates) {
            try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                String line = br.readLine();
                if (line != null && !line.isEmpty()) {
                    return Long.parseLong(line.trim());
                }
            } catch (Exception ignored) {}
        }
        return Long.MIN_VALUE;
    }

}
