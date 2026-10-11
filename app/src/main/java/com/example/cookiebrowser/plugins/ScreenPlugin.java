package com.example.cookiebrowser.plugins;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.Window;
import android.view.WindowManager;

import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * 原生屏幕与显示控制插件 (electron.screen)
 * 支持锁定横竖屏、屏幕常亮防休眠、获取与调节屏幕亮度
 */
public class ScreenPlugin implements IBridgePlugin {

    private final WeakReference<MainActivity> activityRef;

    public ScreenPlugin(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    @Override
    public String getModuleName() {
        return "screen";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_0_PUBLIC;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        MainActivity activity = activityRef.get();
        if (activity == null || activity.isFinishing()) {
            callback.error(500, "Activity context is released");
            return;
        }

        switch (action) {
            case "setOrientation": {
                // orientation: 'portrait' | 'landscape' | 'sensor' | 'auto'
                String orientation = params.optString("orientation", "portrait").toLowerCase();
                activity.runOnUiThread(() -> {
                    try {
                        switch (orientation) {
                            case "landscape":
                                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                break;
                            case "sensor":
                            case "auto":
                                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                                break;
                            case "portrait":
                            default:
                                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                                break;
                        }
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("orientation", orientation);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to set orientation: " + e.getMessage());
                    }
                });
                break;
            }

            case "getOrientation": {
                activity.runOnUiThread(() -> {
                    try {
                        int currentOrientation = activity.getResources().getConfiguration().orientation;
                        String orientationStr = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) ? "landscape" : "portrait";
                        JSONObject res = new JSONObject();
                        res.put("orientation", orientationStr);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to get orientation: " + e.getMessage());
                    }
                });
                break;
            }

            case "setKeepScreenOn": {
                boolean keepScreenOn = params.optBoolean("keepScreenOn", true);
                activity.runOnUiThread(() -> {
                    try {
                        Window window = activity.getWindow();
                        if (window != null) {
                            if (keepScreenOn) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            }
                        }
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("keepScreenOn", keepScreenOn);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to set keep screen on: " + e.getMessage());
                    }
                });
                break;
            }

            case "setBrightness": {
                // brightness: 0.0 - 1.0, 传 -1 代表恢复系统默认自动亮度
                double brightness = params.optDouble("brightness", -1.0);
                activity.runOnUiThread(() -> {
                    try {
                        Window window = activity.getWindow();
                        if (window != null) {
                            WindowManager.LayoutParams lp = window.getAttributes();
                            lp.screenBrightness = (float) brightness;
                            window.setAttributes(lp);
                        }
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("brightness", brightness);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to set screen brightness: " + e.getMessage());
                    }
                });
                break;
            }

            case "getBrightness": {
                activity.runOnUiThread(() -> {
                    try {
                        Window window = activity.getWindow();
                        float brightness = -1.0f;
                        if (window != null) {
                            WindowManager.LayoutParams lp = window.getAttributes();
                            brightness = lp.screenBrightness;
                        }
                        JSONObject res = new JSONObject();
                        res.put("brightness", brightness);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to get screen brightness: " + e.getMessage());
                    }
                });
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'screen'");
                break;
        }
    }
}
