package com.battery.monitor;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.graphics.Color;
import android.view.ViewGroup.LayoutParams;

public class MainActivity extends Activity {

    private TextView tvBatteryLevel;
    private TextView tvBatteryStatus;
    private TextView tvBatteryHealth;
    private TextView tvBatteryTemp;
    private TextView tvBatteryVoltage;
    private TextView tvChargingType;

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int percent = (scale > 0) ? (int) ((level / (float) scale) * 100) : 0;

            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            tvBatteryLevel.setText(percent + "%");
            tvBatteryStatus.setText("Status: " + getStatusString(status));
            tvBatteryHealth.setText("Health: " + getHealthString(health));
            tvBatteryTemp.setText("Temp: " + (temperature / 10.0f) + " °C");
            tvBatteryVoltage.setText("Voltage: " + voltage + " mV");
            tvChargingType.setText("Charging: " + getPluggedString(plugged));

            int color;
            if (percent >= 60) color = Color.rgb(76, 175, 80);
            else if (percent >= 30) color = Color.rgb(255, 152, 0);
            else color = Color.rgb(244, 67, 54);
            tvBatteryLevel.setTextColor(color);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#121212"));
        int pad = dp(32);
        root.setPadding(pad, pad, pad, pad);

        TextView title = makeText("Battery Monitor", 26, Color.WHITE, true);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        tvBatteryLevel = makeText("--", 96, Color.rgb(76, 175, 80), true);
        tvBatteryLevel.setPadding(0, 0, 0, dp(32));
        root.addView(tvBatteryLevel);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1E1E1E"));
        int cp = dp(16);
        card.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(cardParams);

        tvBatteryStatus  = makeText("Status: --", 18, Color.parseColor("#EEEEEE"), false);
        tvBatteryHealth  = makeText("Health: --", 18, Color.parseColor("#EEEEEE"), false);
        tvBatteryTemp    = makeText("Temp: --", 18, Color.parseColor("#EEEEEE"), false);
        tvBatteryVoltage = makeText("Voltage: --", 18, Color.parseColor("#EEEEEE"), false);
        tvChargingType   = makeText("Charging: --", 18, Color.parseColor("#EEEEEE"), false);

        int mb = dp(8);
        for (TextView tv : new TextView[]{tvBatteryStatus, tvBatteryHealth,
                tvBatteryTemp, tvBatteryVoltage, tvChargingType}) {
            tv.setPadding(0, 0, 0, mb);
            card.addView(tv);
        }

        root.addView(card);
        setContentView(root);
    }

    private TextView makeText(String text, int spSize, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(spSize);
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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

    private String getStatusString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not Charging";
            default: return "Unknown";
        }
    }

    private String getHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }

    private String getPluggedString(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "AC";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless";
            case 0: return "Not charging";
            default: return "Unknown";
        }
    }
}
