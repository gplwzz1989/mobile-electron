package com.example.cookiebrowser.plugins;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.bridge.JsBridgeEngine;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

/**
 * 原生电池状态感知插件 (electron.battery)
 * 支持获取当前电量、充电状态、充电方式，以及动态监听电池状态变更
 */
public class BatteryPlugin implements IBridgePlugin {

    private final Context context;
    private JsBridgeEngine bridgeEngine;
    private BroadcastReceiver batteryReceiver;
    private int lastLevel = -1;
    private boolean lastIsCharging = false;

    public BatteryPlugin(Context context) {
        this.context = context.getApplicationContext();
        registerBatteryReceiver();
    }

    public void setBridgeEngine(JsBridgeEngine engine) {
        this.bridgeEngine = engine;
    }

    private void registerBatteryReceiver() {
        try {
            batteryReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                        try {
                            JSONObject status = parseBatteryIntent(intent);
                            int level = status.optInt("level", -1);
                            boolean isCharging = status.optBoolean("isCharging", false);

                            if (level != lastLevel || isCharging != lastIsCharging) {
                                lastLevel = level;
                                lastIsCharging = isCharging;
                                if (bridgeEngine != null) {
                                    bridgeEngine.sendEvent("battery.onChange", status);
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            };
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            context.registerReceiver(batteryReceiver, filter);
        } catch (Exception ignored) {}
    }

    private JSONObject parseBatteryIntent(Intent intent) throws Exception {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = (int) ((level / (float) scale) * 100);

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL;

        int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        String pluggedType = "unplugged";
        if (chargePlug == BatteryManager.BATTERY_PLUGGED_USB) {
            pluggedType = "usb";
        } else if (chargePlug == BatteryManager.BATTERY_PLUGGED_AC) {
            pluggedType = "ac";
        } else if (chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS) {
            pluggedType = "wireless";
        }

        JSONObject res = new JSONObject();
        res.put("level", percent);
        res.put("isCharging", isCharging);
        res.put("plugged", pluggedType);
        return res;
    }

    @Override
    public String getModuleName() {
        return "battery";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_0_PUBLIC;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        if ("getStatus".equalsIgnoreCase(action)) {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                JSONObject res = parseBatteryIntent(batteryStatus);
                callback.success(res);
            } else {
                JSONObject res = new JSONObject();
                res.put("level", 100);
                res.put("isCharging", false);
                res.put("plugged", "unplugged");
                callback.success(res);
            }
            return;
        }

        callback.error(404, "Unknown action '" + action + "' in module 'battery'");
    }
}
