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

    // Two separate channels:
    //   CHANNEL_SILENT  – used for the foreground "heartbeat" notification (no sound/vibration)
    //   CHANNEL_ALERT   – used only when 100% or 80% is hit (loud alarm)
    static final String CHANNEL_SILENT = "battery_silent";
    static final String CHANNEL_ALERT  = "battery_alert";

    private static final int NOTIF_ONGOING = 1;
    private static final int NOTIF_ALERT   = 2;

    private enum State { IDLE, CHARGED }
    private State state = State.IDLE;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int level   = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale   = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            if (scale <= 0) return;
            int percent = (int) ((level / (float) scale) * 100);
            int plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0);
            boolean charging = plugged != 0;

            // ── State transitions (trigger alerts) ──────────────────────────
            if (state == State.IDLE && percent >= 100 && charging) {
                state = State.CHARGED;
                fireAlert("Unplug charger now!",
                    "Battery reached 100%. Unplug to protect battery health.", true);
            } else if (state == State.CHARGED && percent <= 80 && !charging) {
                state = State.IDLE;
                fireAlert("Plug in charger",
                    "Battery is at " + percent + "%. Safe to charge again.", false);
            }

            // ── Ongoing silent notification ──────────────────────────────────
            // Only shown (with content) when between 80 and 100 %.
            // Outside that range the foreground notification is still required
            // by Android but we keep it invisible (IMPORTANCE_MIN channel).
            if (percent >= 80 && percent <= 100) {
                String text = charging
                    ? percent + "% — charging (protection active)"
                    : percent + "% — unplugged";
                updateOngoing(text, state == State.CHARGED);
            } else {
                // Minimal placeholder — keeps the foreground service alive silently
                updateOngoing(null, false);
            }
        }
    };

    // -----------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        // Start foreground immediately with a silent placeholder
        startForeground(NOTIF_ONGOING, buildOngoing(null, false));
        registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        getSystemService(NotificationManager.class).cancel(NOTIF_ONGOING);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // -----------------------------------------------------------------------

    private void updateOngoing(String text, boolean atLimit) {
        getSystemService(NotificationManager.class)
            .notify(NOTIF_ONGOING, buildOngoing(text, atLimit));
    }

    /**
     * @param text  null → minimal invisible placeholder (used outside 80-100 % range)
     */
    private Notification buildOngoing(String text, boolean atLimit) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_SILENT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true);  // never make any sound even if channel somehow allows it

        if (text != null) {
            b.setContentTitle("Charge Protection")
             .setContentText(text)
             .setColor(atLimit ? Color.RED : Color.GREEN);
        } else {
            // Outside 80-100 %: hide the notification as much as possible.
            // IMPORTANCE_MIN channel keeps it out of the status bar entirely.
            b.setContentTitle("Battery Monitor")
             .setContentText("Charge protection running")
             .setColor(Color.DKGRAY);
        }

        return b.build();
    }

    // ── Alarm-level alert (only fires at 100% and 80%) ─────────────────────

    private void fireAlert(String title, String message, boolean unplug) {
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
            .setSmallIcon(unplug
                ? android.R.drawable.ic_dialog_alert
                : android.R.drawable.ic_lock_idle_charging)
            .setColor(unplug ? Color.RED : Color.GREEN)
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

    // ── Channel setup ───────────────────────────────────────────────────────

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);

        // Silent channel — for the ongoing foreground notification (zero noise)
        NotificationChannel silent = new NotificationChannel(
            CHANNEL_SILENT, "Charge Protection Status",
            NotificationManager.IMPORTANCE_MIN);      // IMPORTANCE_MIN = no sound, no status bar icon
        silent.setDescription("Ongoing silent status notification for charge protection");
        silent.setSound(null, null);
        silent.enableVibration(false);
        silent.enableLights(false);
        nm.createNotificationChannel(silent);

        // Alert channel — loud alarm only at 100% / 80%
        NotificationChannel alertCh = new NotificationChannel(
            CHANNEL_ALERT, "Charge Protection Alerts",
            NotificationManager.IMPORTANCE_HIGH);
        alertCh.setDescription("Alarm when battery hits 100% or drops to 80%");
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
