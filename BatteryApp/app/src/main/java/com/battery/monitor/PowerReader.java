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
        // Samsung (and many other OEMs) violate the API contract:
        // BATTERY_PROPERTY_CURRENT_NOW and sysfs current_now return mA, not µA.
        // We detect the scale: if |value| >= 100,000 it's in µA (divide by 1000),
        // otherwise treat it as already in mA.
        long bmRaw = Long.MIN_VALUE;
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            bmRaw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        }

        long sysfsRaw = readSysfsLong(BATT_CURRENT_PATHS);
        long rawValue;

        if (sysfsRaw != Long.MIN_VALUE && sysfsRaw != 0) {
            rawValue = sysfsRaw;
            s.currentSource = "sysfs";
        } else if (bmRaw != Long.MIN_VALUE && bmRaw != 0) {
            rawValue = bmRaw;
            s.currentSource = "BatteryManager";
        } else {
            rawValue = 0;
            s.currentSource = "unavailable";
        }

        // Scale detection: |value| >= 100,000 → µA; otherwise already mA
        float battCurrentMaRaw = (Math.abs(rawValue) >= 100_000L)
                ? rawValue / 1000f
                : (float) rawValue;

        // Sign convention: positive = charging into battery, negative = discharging
        // Some kernels invert this — if sign contradicts plug state, flip it
        if (plugged && battCurrentMaRaw < -50f) {
            battCurrentMaRaw = -battCurrentMaRaw;
        }
        if (!plugged && battCurrentMaRaw > 50f) {
            battCurrentMaRaw = -battCurrentMaRaw;
        }

        s.battCurrentMa = battCurrentMaRaw;

        // --- Input current & voltage from charger sysfs ---
        long inputRawCurrent = readSysfsLong(INPUT_CURRENT_PATHS);
        long inputRawVoltage = readSysfsLong(INPUT_VOLTAGE_PATHS);

        if (plugged && inputRawCurrent > 0) {
            // Same scale detection for input current
            s.inputCurrentMa = (inputRawCurrent >= 100_000L)
                    ? inputRawCurrent / 1000f
                    : (float) inputRawCurrent;
            // Input voltage is reliably in µV in sysfs
            s.inputVoltageV  = (inputRawVoltage > 0)
                    ? inputRawVoltage / 1_000_000f
                    : 5.0f;
            s.inputPowerW = (s.inputCurrentMa / 1000f) * s.inputVoltageV;
        }

        // --- Derive charge power and device draw ---
        // s.battCurrentMa is now in mA, s.battVoltageV in V
        // Power (W) = current (A) * voltage (V) = (mA / 1000) * V
        if (plugged && s.battCurrentMa > 0) {
            s.chargePowerW = (s.battCurrentMa / 1000f) * s.battVoltageV;

            if (s.inputPowerW > 0) {
                s.deviceDrawW = Math.max(0, s.inputPowerW - s.chargePowerW);
            } else {
                // No sysfs input data — estimate via typical 88% charger→battery efficiency
                float estimatedInputW = s.chargePowerW / 0.88f;
                s.deviceDrawW = Math.max(0, estimatedInputW - s.chargePowerW);
                s.inputPowerW = estimatedInputW;
            }
        } else if (!plugged) {
            // Discharging: device draw = power leaving the battery
            float dischargeMa = Math.abs(s.battCurrentMa);
            s.deviceDrawW  = (dischargeMa / 1000f) * s.battVoltageV;
            s.chargePowerW = 0;
            s.inputPowerW  = 0;
        } else {
            // Plugged but current ≈ 0 (battery full / trickle maintenance)
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
