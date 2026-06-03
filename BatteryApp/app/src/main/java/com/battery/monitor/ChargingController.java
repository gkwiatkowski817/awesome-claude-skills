package com.battery.monitor;

import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;

/**
 * Attempts to directly enable/disable charging via sysfs.
 * Requires root. Falls back gracefully when root is unavailable.
 *
 * Samsung S21 (Exynos & Snapdragon) expose one of these nodes:
 *   /sys/class/power_supply/battery/charging_enabled   (write 0/1)
 *   /sys/class/power_supply/battery/battery_charging_enabled
 *   /sys/class/power_supply/usb/charger_detection_enabled
 *
 * We try direct file write first (works if the app has root via su wrapper),
 * then fall back to a `su -c` shell command.
 */
public class ChargingController {

    private static final String TAG = "ChargingController";

    // Candidate sysfs nodes in preference order (Samsung → AOSP generic)
    private static final String[] CHARGING_NODES = {
        "/sys/class/power_supply/battery/charging_enabled",
        "/sys/class/power_supply/battery/battery_charging_enabled",
        "/sys/class/power_supply/usb/charger_detection_enabled",
        "/sys/class/power_supply/battery/input_suspend",
    };

    public enum Result {
        SUCCESS_SYSFS,    // wrote directly to sysfs
        SUCCESS_ROOT_SH,  // used `su -c` shell command
        NO_ROOT,          // su not available
        NO_NODE,          // no writable sysfs node found
        ERROR
    }

    /** Disable charging (write 0). Returns the result so the UI can show a note. */
    public static Result disableCharging() {
        return writeCharging("0");
    }

    /** Enable charging (write 1). */
    public static Result enableCharging() {
        return writeCharging("1");
    }

    /** @return true if the device appears to have a recognised charging control node */
    public static boolean isSupported() {
        for (String path : CHARGING_NODES) {
            try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                br.readLine(); // readable → node exists
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    // -----------------------------------------------------------------------

    private static Result writeCharging(String value) {
        // 1. Try direct file write (works if app runs with root UID, rare)
        for (String path : CHARGING_NODES) {
            try (FileWriter fw = new FileWriter(path)) {
                fw.write(value);
                Log.i(TAG, "Direct write " + value + " → " + path);
                return Result.SUCCESS_SYSFS;
            } catch (Exception ignored) {}
        }

        // 2. Try via `su -c` (rooted devices with Magisk / KernelSU / SuperSU)
        if (isRootAvailable()) {
            for (String path : CHARGING_NODES) {
                try {
                    int exit = runSuCommand("echo " + value + " > " + path);
                    if (exit == 0) {
                        Log.i(TAG, "su write " + value + " → " + path);
                        return Result.SUCCESS_ROOT_SH;
                    }
                } catch (Exception ignored) {}
            }
            return Result.NO_NODE;
        }

        return Result.NO_ROOT;
    }

    private static boolean isRootAvailable() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            int exit = p.waitFor();
            if (exit != 0) return false;
            String out = new BufferedReader(new InputStreamReader(p.getInputStream()))
                    .readLine();
            return out != null && out.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private static int runSuCommand(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        return p.waitFor();
    }
}
