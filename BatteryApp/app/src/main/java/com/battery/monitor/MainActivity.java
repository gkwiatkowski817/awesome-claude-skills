package com.battery.monitor;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup.LayoutParams;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    public static final String CHANNEL_ID = "battery_protection";
    static final String PREFS = "batt_prefs";
    static final String KEY_ENABLED = "protection_enabled";

    private TextView tvBatteryLevel, tvBatteryStatus, tvBatteryHealth;
    private TextView tvBatteryTemp, tvBatteryVoltage, tvChargingType;
    private Switch protectionSwitch;
    private TextView tvProtectionDesc;

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int percent = (scale > 0) ? (int) ((level / (float) scale) * 100) : 0;
            int status   = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health   = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int temp     = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            int voltage  = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            int plugged  = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            tvBatteryLevel.setText(percent + "%");
            tvBatteryStatus.setText("Status: " + getStatusString(status));
            tvBatteryHealth.setText("Health: " + getHealthString(health));
            tvBatteryTemp.setText("Temp: " + (temp / 10.0f) + " °C");
            tvBatteryVoltage.setText("Voltage: " + voltage + " mV");
            tvChargingType.setText("Charging: " + getPluggedString(plugged));

            int color;
            if (percent >= 60)      color = Color.rgb(76, 175, 80);
            else if (percent >= 30) color = Color.rgb(255, 152, 0);
            else                    color = Color.rgb(244, 67, 54);
            tvBatteryLevel.setTextColor(color);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();
        buildUI();
        restoreSwitchState();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        int pad = dp(24);
        root.setPadding(pad, dp(40), pad, pad);

        // Title
        TextView title = makeText("Battery Monitor", 24, Color.WHITE, true);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        // Large battery % indicator
        tvBatteryLevel = makeText("--", 96, Color.rgb(76, 175, 80), true);
        tvBatteryLevel.setGravity(Gravity.CENTER);
        tvBatteryLevel.setPadding(0, 0, 0, dp(16));
        root.addView(tvBatteryLevel);

        // Stats card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1E1E1E"));
        int cp = dp(16);
        card.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(20);
        card.setLayoutParams(cardParams);

        tvBatteryStatus  = makeText("Status: --",   18, Color.parseColor("#EEEEEE"), false);
        tvBatteryHealth  = makeText("Health: --",   18, Color.parseColor("#EEEEEE"), false);
        tvBatteryTemp    = makeText("Temp: --",     18, Color.parseColor("#EEEEEE"), false);
        tvBatteryVoltage = makeText("Voltage: --",  18, Color.parseColor("#EEEEEE"), false);
        tvChargingType   = makeText("Charging: --", 18, Color.parseColor("#EEEEEE"), false);

        for (TextView tv : new TextView[]{tvBatteryStatus, tvBatteryHealth,
                tvBatteryTemp, tvBatteryVoltage, tvChargingType}) {
            tv.setPadding(0, 0, 0, dp(6));
            card.addView(tv);
        }
        root.addView(card);

        // Protection card
        LinearLayout protCard = new LinearLayout(this);
        protCard.setOrientation(LinearLayout.VERTICAL);
        protCard.setBackgroundColor(Color.parseColor("#1A2A1A"));
        protCard.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams protParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        protCard.setLayoutParams(protParams);

        // Row: label + switch
        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        switchRow.setLayoutParams(rowParams);

        TextView switchLabel = makeText("Charge Protection", 18, Color.WHITE, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        switchLabel.setLayoutParams(labelParams);
        switchRow.addView(switchLabel);

        protectionSwitch = new Switch(this);
        protectionSwitch.setTextColor(Color.WHITE);
        switchRow.addView(protectionSwitch);
        protCard.addView(switchRow);

        // Description
        tvProtectionDesc = makeText(
            "OFF — alerts you to unplug at 100% and replug at 80%",
            13, Color.parseColor("#AAAAAA"), false);
        tvProtectionDesc.setPadding(0, dp(8), 0, 0);
        protCard.addView(tvProtectionDesc);

        root.addView(protCard);
        setContentView(root);

        protectionSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton btn, boolean checked) {
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, checked).apply();
                Intent svc = new Intent(MainActivity.this, ChargingProtectionService.class);
                if (checked) {
                    startForegroundService(svc);
                    tvProtectionDesc.setText("ON — will alert you to unplug at 100%, replug at 80%");
                    tvProtectionDesc.setTextColor(Color.rgb(76, 175, 80));
                    Toast.makeText(MainActivity.this,
                        "Protection ON: you'll be alerted at 100% and 80%",
                        Toast.LENGTH_LONG).show();
                } else {
                    stopService(svc);
                    tvProtectionDesc.setText("OFF — alerts you to unplug at 100% and replug at 80%");
                    tvProtectionDesc.setTextColor(Color.parseColor("#AAAAAA"));
                }
            }
        });
    }

    private void restoreSwitchState() {
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
        protectionSwitch.setChecked(enabled);
        if (enabled) {
            tvProtectionDesc.setText("ON — will alert you to unplug at 100%, replug at 80%");
            tvProtectionDesc.setTextColor(Color.rgb(76, 175, 80));
            startForegroundService(new Intent(this, ChargingProtectionService.class));
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Battery Protection",
            NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Alerts for charge protection (unplug/replug)");
        channel.enableLights(true);
        channel.setLightColor(Color.GREEN);
        channel.enableVibration(true);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(batteryReceiver);
    }

    private TextView makeText(String text, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(sp);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String getStatusString(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING:     return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:  return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL:         return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not Charging";
            default: return "Unknown";
        }
    }

    private String getHealthString(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD:          return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:      return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD:          return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:  return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_COLD:          return "Cold";
            default: return "Unknown";
        }
    }

    private String getPluggedString(int p) {
        switch (p) {
            case BatteryManager.BATTERY_PLUGGED_AC:       return "AC";
            case BatteryManager.BATTERY_PLUGGED_USB:      return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless";
            case 0: return "Not charging";
            default: return "Unknown";
        }
    }
}
