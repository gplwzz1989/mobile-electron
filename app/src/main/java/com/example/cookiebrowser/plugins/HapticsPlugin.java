package com.example.cookiebrowser.plugins;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

/**
 * 原生触感与震动反馈插件 (electron.haptics)
 * 提供轻量/中等/重度冲击反馈、成功/警告/失败通知反馈与刻度选择感
 */
public class HapticsPlugin implements IBridgePlugin {

    private final Context context;
    private final Vibrator vibrator;

    public HapticsPlugin(Context context) {
        this.context = context.getApplicationContext();
        this.vibrator = (Vibrator) this.context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public String getModuleName() {
        return "haptics";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_0_PUBLIC;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        if (vibrator == null || !vibrator.hasVibrator()) {
            JSONObject res = new JSONObject();
            res.put("supported", false);
            callback.success(res);
            return;
        }

        if ("impact".equalsIgnoreCase(action)) {
            String style = params != null ? params.optString("style", "medium").toLowerCase() : "medium";
            performImpact(style);
            JSONObject res = new JSONObject();
            res.put("success", true);
            callback.success(res);
            return;
        }

        if ("notification".equalsIgnoreCase(action)) {
            String type = params != null ? params.optString("type", "success").toLowerCase() : "success";
            performNotification(type);
            JSONObject res = new JSONObject();
            res.put("success", true);
            callback.success(res);
            return;
        }

        if ("selection".equalsIgnoreCase(action)) {
            performSelection();
            JSONObject res = new JSONObject();
            res.put("success", true);
            callback.success(res);
            return;
        }

        if ("vibrate".equalsIgnoreCase(action)) {
            long duration = params != null ? params.optLong("duration", 200) : 200;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
            JSONObject res = new JSONObject();
            res.put("success", true);
            callback.success(res);
            return;
        }

        callback.error(404, "HapticsPlugin: 未知动作 " + action);
    }

    private void performImpact(String style) {
        int durationMs;
        int amplitude;

        switch (style) {
            case "light":
                durationMs = 15;
                amplitude = 60;
                break;
            case "heavy":
                durationMs = 50;
                amplitude = 255;
                break;
            case "medium":
            default:
                durationMs = 30;
                amplitude = 150;
                break;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude));
        } else {
            vibrator.vibrate(durationMs);
        }
    }

    private void performNotification(String type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long[] timings;
            int[] amplitudes;

            switch (type) {
                case "warning":
                    timings = new long[]{0, 30, 40, 50};
                    amplitudes = new int[]{0, 160, 0, 200};
                    break;
                case "error":
                    timings = new long[]{0, 40, 30, 40, 30, 60};
                    amplitudes = new int[]{0, 220, 0, 220, 0, 255};
                    break;
                case "success":
                default:
                    timings = new long[]{0, 20, 50, 35};
                    amplitudes = new int[]{0, 100, 0, 220};
                    break;
            }
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        } else {
            vibrator.vibrate(60);
        }
    }

    private void performSelection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(10, 50));
        } else {
            vibrator.vibrate(10);
        }
    }
}
