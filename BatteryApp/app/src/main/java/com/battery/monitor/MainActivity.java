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
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup.LayoutParams;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    /** @deprecated kept only so old code compiles; channels now live in ChargingProtectionService */
    public static final String CHANNEL_ID = ChargingProtectionService.CHANNEL_ALERT;
    static final String PREFS      = "batt_prefs";
    static final String KEY_ENABLED = "protection_enabled";

    // --- battery info ---
    private TextView tvLevel, tvStatus, tvHealth, tvTemp, tvVoltage, tvChargingType;

    // --- power section ---
    private TextView tvSpeedLabel, tvSpeedSub;
    private TextView tvCurrentMa;
    private TextView tvInputPower, tvChargePower, tvDeviceDraw;
    private TextView tvInputVoltage, tvInputCurrent;
    private TextView tvPowerSource;

    // --- protection ---
    private Switch   protectionSwitch;
    private TextView tvProtDesc;

    private int  lastVoltageMv = 3800;
    private boolean lastPlugged = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollPower = new Runnable() {
        @Override public void run() {
            refreshPower();
            handler.postDelayed(this, 1000);
        }
    };

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale   = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int percent = (scale > 0) ? (int) ((level / (float) scale) * 100) : 0;
            int status  = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health  = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int temp    = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            lastVoltageMv = voltage > 0 ? voltage : lastVoltageMv;
            lastPlugged   = plugged != 0;

            tvLevel.setText(percent + "%");
            tvStatus.setText("Status: " + statusStr(status));
            tvHealth.setText("Health: " + healthStr(health));
            tvTemp.setText("Temperature: " + (temp / 10.0f) + " °C");
            tvVoltage.setText("Battery Voltage: " + voltage + " mV");
            tvChargingType.setText("Plug type: " + plugStr(plugged));

            int col;
            if (percent >= 60)      col = Color.rgb(76, 175, 80);
            else if (percent >= 30) col = Color.rgb(255, 152, 0);
            else                    col = Color.rgb(244, 67, 54);
            tvLevel.setTextColor(col);

            refreshPower();
        }
    };

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestNotifPermission();
        setContentView(buildUI());
        restoreSwitchState();
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        handler.post(pollPower);
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterReceiver(batteryReceiver);
        handler.removeCallbacks(pollPower);
    }

    // -----------------------------------------------------------------------
    // Power refresh (called every second)
    // -----------------------------------------------------------------------

    private void refreshPower() {
        PowerReader.Snapshot snap = PowerReader.read(this, lastVoltageMv, lastPlugged);

        // --- Speed label ---
        String label = PowerReader.speedLabel(snap.battCurrentMa, snap.isCharging);
        tvSpeedLabel.setText(label);
        tvSpeedLabel.setTextColor(PowerReader.speedColor(snap.battCurrentMa, snap.isCharging));

        // Current
        String sign = snap.battCurrentMa >= 0 ? "+" : "";
        tvCurrentMa.setText(sign + String.format("%.0f mA", snap.battCurrentMa));
        tvCurrentMa.setTextColor(snap.isCharging
            ? PowerReader.speedColor(snap.battCurrentMa, true)
            : Color.rgb(255, 152, 0));

        // Sub-label
        if (snap.isCharging) {
            tvSpeedSub.setText("Charging");
            tvSpeedSub.setTextColor(Color.rgb(76,175,80));
        } else {
            tvSpeedSub.setText("Discharging");
            tvSpeedSub.setTextColor(Color.rgb(255,152,0));
        }

        // Power rows
        if (snap.isCharging) {
            tvInputCurrent.setText(String.format("Input current:   %.0f mA", snap.inputCurrentMa > 0 ? snap.inputCurrentMa : Math.abs(snap.battCurrentMa)));
            tvInputVoltage.setText(String.format("Input voltage:   %.2f V",  snap.inputVoltageV > 0 ? snap.inputVoltageV : 5.0f));
            tvInputPower.setText(String.format("Input power:     %.2f W",   snap.inputPowerW));
            tvChargePower.setText(String.format("Charge power:    %.2f W",   snap.chargePowerW));
            tvDeviceDraw.setText(String.format("Device draw:     %.2f W",   snap.deviceDrawW));
        } else {
            tvInputCurrent.setText("Input current:   — (not charging)");
            tvInputVoltage.setText("Input voltage:   — (not charging)");
            tvInputPower.setText("Input power:     0.00 W");
            tvChargePower.setText("Charge power:    0.00 W");
            tvDeviceDraw.setText(String.format("Device draw:     %.2f W",   snap.deviceDrawW));
        }

        tvPowerSource.setText("Source: " + snap.currentSource);
    }

    // -----------------------------------------------------------------------
    // UI builder
    // -----------------------------------------------------------------------

    private ScrollView buildUI() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#121212"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(20);
        root.setPadding(pad, dp(36), pad, pad);

        // Title
        root.addView(row("Battery Monitor", 24, Color.WHITE, true, 0, dp(6)));

        // Big % number
        tvLevel = makeText("--", 92, Color.rgb(76,175,80), true);
        tvLevel.setGravity(Gravity.CENTER);
        tvLevel.setPadding(0, 0, 0, dp(4));
        root.addView(tvLevel);

        // ── Battery info card ──────────────────────────────────────────────
        LinearLayout infoCard = card("#1E1E1E", dp(16), 0, dp(16));
        tvStatus      = infoRow(infoCard, "Status: --");
        tvHealth      = infoRow(infoCard, "Health: --");
        tvTemp        = infoRow(infoCard, "Temperature: --");
        tvVoltage     = infoRow(infoCard, "Battery Voltage: --");
        tvChargingType = infoRow(infoCard, "Plug type: --");
        root.addView(infoCard);

        // ── Power & Charging Speed card ────────────────────────────────────
        LinearLayout powerCard = card("#0D1F0D", dp(16), dp(16), dp(16));

        TextView powerTitle = makeText("⚡ Power & Charging Speed", 16, Color.parseColor("#AAFFAA"), true);
        powerTitle.setPadding(0, 0, 0, dp(12));
        powerCard.addView(powerTitle);

        // Speed label row
        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speedRow.setGravity(Gravity.CENTER_VERTICAL);
        speedRow.setPadding(0, 0, 0, dp(4));

        tvSpeedLabel = makeText("--", 28, Color.WHITE, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tvSpeedLabel.setLayoutParams(lp);
        speedRow.addView(tvSpeedLabel);

        tvSpeedSub = makeText("--", 16, Color.GRAY, false);
        speedRow.addView(tvSpeedSub);
        powerCard.addView(speedRow);

        // Current reading (large)
        tvCurrentMa = makeText("+0 mA", 42, Color.WHITE, true);
        tvCurrentMa.setGravity(Gravity.CENTER);
        tvCurrentMa.setPadding(0, dp(8), 0, dp(16));
        powerCard.addView(tvCurrentMa);

        // Divider
        powerCard.addView(divider());

        // Input section
        TextView inputTitle = makeText("FROM CHARGER", 12, Color.parseColor("#888888"), true);
        inputTitle.setPadding(0, dp(12), 0, dp(4));
        powerCard.addView(inputTitle);
        tvInputCurrent = infoRow(powerCard, "Input current:   --", Color.parseColor("#80DEEA"));
        tvInputVoltage = infoRow(powerCard, "Input voltage:   --", Color.parseColor("#80DEEA"));
        tvInputPower   = infoRow(powerCard, "Input power:     --", Color.parseColor("#4DD0E1"));

        // Divider
        powerCard.addView(divider());

        // Breakdown section
        TextView breakTitle = makeText("POWER BREAKDOWN", 12, Color.parseColor("#888888"), true);
        breakTitle.setPadding(0, dp(12), 0, dp(4));
        powerCard.addView(breakTitle);

        tvChargePower = infoRow(powerCard, "Charge power:    --", Color.parseColor("#A5D6A7"));
        tvDeviceDraw  = infoRow(powerCard, "Device draw:     --", Color.parseColor("#FFCC80"));

        // Footnote
        powerCard.addView(divider());
        tvPowerSource = makeText("Source: --", 11, Color.parseColor("#555555"), false);
        tvPowerSource.setPadding(0, dp(8), 0, 0);
        powerCard.addView(tvPowerSource);

        root.addView(powerCard);

        // ── Charge Protection card ─────────────────────────────────────────
        LinearLayout protCard = card("#1A2A1A", dp(16), dp(16), 0);

        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        switchRow.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView swLabel = makeText("Charge Protection  100%→80%", 16, Color.WHITE, true);
        swLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        switchRow.addView(swLabel);

        protectionSwitch = new Switch(this);
        switchRow.addView(protectionSwitch);
        protCard.addView(switchRow);

        tvProtDesc = makeText("OFF", 13, Color.parseColor("#AAAAAA"), false);
        tvProtDesc.setPadding(0, dp(6), 0, 0);
        protCard.addView(tvProtDesc);

        root.addView(protCard);

        protectionSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton btn, boolean on) {
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, on).apply();
                Intent svc = new Intent(MainActivity.this, ChargingProtectionService.class);
                if (on) {
                    startForegroundService(svc);
                    tvProtDesc.setText("ON — alarm at 100% (unplug) and 80% (replug)");
                    tvProtDesc.setTextColor(Color.rgb(76,175,80));
                } else {
                    stopService(svc);
                    tvProtDesc.setText("OFF");
                    tvProtDesc.setTextColor(Color.parseColor("#AAAAAA"));
                }
            }
        });

        sv.addView(root);
        return sv;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private LinearLayout card(String bg, int pad, int topMargin, int bottomMargin) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(Color.parseColor(bg));
        c.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin    = topMargin;
        lp.bottomMargin = bottomMargin;
        c.setLayoutParams(lp);
        return c;
    }

    private TextView infoRow(LinearLayout parent, String text) {
        return infoRow(parent, text, Color.parseColor("#EEEEEE"));
    }

    private TextView infoRow(LinearLayout parent, String text, int color) {
        TextView tv = makeText(text, 15, color, false);
        tv.setPadding(0, 0, 0, dp(5));
        parent.addView(tv);
        return tv;
    }

    private TextView row(String text, int sp, int color, boolean bold, int topM, int botM) {
        TextView tv = makeText(text, sp, color, bold);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin    = topM;
        lp.bottomMargin = botM;
        tv.setLayoutParams(lp);
        return tv;
    }

    private android.view.View divider() {
        android.view.View v = new android.view.View(this);
        v.setBackgroundColor(Color.parseColor("#333333"));
        v.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));
        return v;
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

    private void restoreSwitchState() {
        boolean on = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
        protectionSwitch.setChecked(on);
        if (on) {
            tvProtDesc.setText("ON — alarm at 100% (unplug) and 80% (replug)");
            tvProtDesc.setTextColor(Color.rgb(76,175,80));
            startForegroundService(new Intent(this, ChargingProtectionService.class));
        }
    }

    private void createNotificationChannel() {
        // Channels are now created by ChargingProtectionService.createChannels().
        // Nothing to do here — kept to avoid breaking the onCreate call.
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    // --- string helpers ---
    private String statusStr(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING:     return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:  return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL:         return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not Charging";
            default: return "Unknown";
        }
    }
    private String healthStr(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD:         return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:     return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD:         return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_COLD:         return "Cold";
            default: return "Unknown";
        }
    }
    private String plugStr(int p) {
        switch (p) {
            case BatteryManager.BATTERY_PLUGGED_AC:       return "AC / Fast charge";
            case BatteryManager.BATTERY_PLUGGED_USB:      return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless";
            case 0: return "Unplugged";
            default: return "Unknown";
        }
    }
}
