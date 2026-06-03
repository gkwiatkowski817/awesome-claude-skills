package com.battery.monitor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Vibrator;
import android.os.VibrationEffect;

public class ChargingProtectionService extends Service {

    private static final int NOTIF_ONGOING = 1;
    private static final int NOTIF_ALERT   = 2;

    // State machine: IDLE → CHARGED (at 100%) → DISCHARGED (at 80%) → CHARGED ...
    private enum State { IDLE, CHARGED }
    private State state = State.IDLE;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            if (scale <= 0) return;
            int percent = (int) ((level / (float) scale) * 100);
            int plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0);

            if (state == State.IDLE && percent >= 100 && plugged != 0) {
                state = State.CHARGED;
                alertUser("Unplug charger now!",
                    "Battery is at 100%. Unplug to protect battery health.",
                    true);
            } else if (state == State.CHARGED && percent <= 80 && plugged == 0) {
                state = State.IDLE;
                alertUser("Plug in charger",
                    "Battery dropped to " + percent + "%. Plug in to charge.",
                    false);
            }

            updateOngoingNotification(percent, plugged != 0);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIF_ONGOING, buildOngoingNotification("Monitoring...", false));
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

    private void updateOngoingNotification(int percent, boolean charging) {
        String text = charging
            ? "Monitoring: " + percent + "% — charging"
            : "Monitoring: " + percent + "% — not charging";
        getSystemService(NotificationManager.class)
            .notify(NOTIF_ONGOING, buildOngoingNotification(text, state == State.CHARGED));
    }

    private Notification buildOngoingNotification(String text, boolean alert) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, MainActivity.CHANNEL_ID)
            .setContentTitle("Charge Protection Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setContentIntent(pi)
            .setColor(alert ? Color.RED : Color.GREEN)
            .build();
    }

    private void alertUser(String title, String message, boolean unplug) {
        // Full-screen / heads-up notification
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmUri == null) {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        Notification alert = new Notification.Builder(this, MainActivity.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(unplug
                ? android.R.drawable.ic_dialog_alert
                : android.R.drawable.ic_lock_idle_charging)
            .setColor(unplug ? Color.RED : Color.GREEN)
            .setPriority(Notification.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSound(alarmUri, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build())
            .setVibrate(new long[]{0, 500, 200, 500, 200, 500})
            .build();

        getSystemService(NotificationManager.class).notify(NOTIF_ALERT, alert);

        // Also vibrate directly
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createWaveform(
                new long[]{0, 500, 200, 500, 200, 500}, -1));
        }
    }
}
