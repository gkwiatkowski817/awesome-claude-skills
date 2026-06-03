package com.battery.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class ChargingProtectionService extends Service {

    static final String CHANNEL_SILENT = "battery_silent";
    static final String CHANNEL_ALERT  = "battery_alert";

    private static final int NOTIF_ONGOING = 1;
    private static final int NOTIF_ALERT   = 2;

    // Thresholds
    private static final int STOP_PERCENT   = 80;   // disable charging at or above this
    private static final int RESUME_PERCENT = 30;   // re-enable charging at or below this

    private enum State { CHARGING_ALLOWED, CHARGING_STOPPED }
    private State state = State.CHARGING_ALLOWED;

    // Tracks whether we actually managed to stop charging via root
    private boolean rootControlActive = false;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int level   = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale   = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            if (scale <= 0) return;
            int percent = (int) ((level / (float) scale) * 100);
            boolean plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;

            // ── State machine ────────────────────────────────────────────────
            if (state == State.CHARGING_ALLOWED && percent >= STOP_PERCENT && plugged) {
                state = State.CHARGING_STOPPED;
                handleStopCharging(percent);

            } else if (state == State.CHARGING_STOPPED && percent <= RESUME_PERCENT) {
                state = State.CHARGING_ALLOWED;
                handleResumeCharging(percent);
            }

            // ── Ongoing silent notification (only shown between 30% and 100%) ──
            if (percent >= RESUME_PERCENT && percent <= 100) {
                String modeStr = rootControlActive ? "charging paused by app" : "alert mode";
                String text = plugged
                    ? percent + "% — charging  (" + modeStr + ")"
                    : percent + "% — unplugged";
                updateOngoing(text, state == State.CHARGING_STOPPED);
            } else {
                updateOngoing(null, false);
            }
        }
    };

    // -----------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(NOTIF_ONGOING, buildOngoing(null, false));
        registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Always re-enable charging when the service is killed
        if (rootControlActive) {
            ChargingController.enableCharging();
            rootControlActive = false;
        }
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        getSystemService(NotificationManager.class).cancel(NOTIF_ONGOING);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // -----------------------------------------------------------------------

    private void handleStopCharging(int percent) {
        ChargingController.Result result = ChargingController.disableCharging();

        switch (result) {
            case SUCCESS_SYSFS:
            case SUCCESS_ROOT_SH:
                // Root worked — charging is actually paused, show a silent status update
                rootControlActive = true;
                fireAlert(
                    "Charging paused at " + percent + "%",
                    "App stopped charging via root. Will resume at " + RESUME_PERCENT + "%.",
                    true);
                break;

            case NO_ROOT:
                // No root — fall back to alert so the user can unplug manually
                rootControlActive = false;
                fireAlert(
                    "Unplug charger — battery at " + percent + "%",
                    "Root not available. Please unplug manually to protect battery health.",
                    true);
                break;

            case NO_NODE:
                rootControlActive = false;
                fireAlert(
                    "Unplug charger — battery at " + percent + "%",
                    "Charging control node not found. Please unplug manually.",
                    true);
                break;

            default:
                rootControlActive = false;
                fireAlert(
                    "Battery at " + percent + "% — action needed",
                    "Could not stop charging automatically. Please unplug.",
                    true);
        }
    }

    private void handleResumeCharging(int percent) {
        if (rootControlActive) {
            ChargingController.enableCharging();
            rootControlActive = false;
            fireAlert(
                "Charging resumed at " + percent + "%",
                "App re-enabled charging. Plug in your charger.",
                false);
        } else {
            fireAlert(
                "Plug in charger — battery at " + percent + "%",
                "Battery dropped to " + percent + "%. Safe to charge again.",
                false);
        }
    }

    // ── Ongoing notification ────────────────────────────────────────────────

    private void updateOngoing(String text, boolean atLimit) {
        getSystemService(NotificationManager.class)
            .notify(NOTIF_ONGOING, buildOngoing(text, atLimit));
    }

    private Notification buildOngoing(String text, boolean atLimit) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_SILENT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true);

        if (text != null) {
            b.setContentTitle("Charge Protection  80% → 30%")
             .setContentText(text)
             .setColor(atLimit ? Color.RED : Color.GREEN);
        } else {
            b.setContentTitle("Battery Monitor")
             .setContentText("Charge protection running")
             .setColor(Color.DKGRAY);
        }
        return b.build();
    }

    // ── Alarm alert ─────────────────────────────────────────────────────────

    private void fireAlert(String title, String message, boolean urgent) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmUri == null)
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Notification alert = new Notification.Builder(this, CHANNEL_ALERT)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(urgent
                ? android.R.drawable.ic_dialog_alert
                : android.R.drawable.ic_lock_idle_charging)
            .setColor(urgent ? Color.RED : Color.GREEN)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSound(alarmUri, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).build())
            .setVibrate(new long[]{0, 600, 200, 600, 200, 600})
            .build();

        getSystemService(NotificationManager.class).notify(NOTIF_ALERT, alert);

        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createWaveform(
                new long[]{0, 600, 200, 600, 200, 600}, -1));
        }
    }

    // ── Channels ─────────────────────────────────────────────────────────────

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);

        NotificationChannel silent = new NotificationChannel(
            CHANNEL_SILENT, "Charge Protection Status",
            NotificationManager.IMPORTANCE_MIN);
        silent.setSound(null, null);
        silent.enableVibration(false);
        silent.enableLights(false);
        nm.createNotificationChannel(silent);

        NotificationChannel alertCh = new NotificationChannel(
            CHANNEL_ALERT, "Charge Protection Alerts",
            NotificationManager.IMPORTANCE_HIGH);
        alertCh.enableLights(true);
        alertCh.setLightColor(Color.RED);
        alertCh.enableVibration(true);
        alertCh.setVibrationPattern(new long[]{0, 600, 200, 600, 200, 600});
        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        alertCh.setSound(alarmUri, new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM).build());
        nm.createNotificationChannel(alertCh);
    }
}
