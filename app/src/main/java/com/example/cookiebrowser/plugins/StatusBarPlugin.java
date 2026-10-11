package com.example.cookiebrowser.plugins;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * 原生状态栏与沉浸式 UI 控制插件 (electron.statusBar)
 * 支持设置状态栏背景色、文字明暗模式、沉浸式全屏透明与显隐控制
 */
public class StatusBarPlugin implements IBridgePlugin {

    private final WeakReference<MainActivity> activityRef;

    public StatusBarPlugin(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    @Override
    public String getModuleName() {
        return "statusBar";
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
            case "setStyle": {
                // style: 'light' (浅色文字/图标，适用于深色背景) 或 'dark' (深色文字/图标，适用于浅色背景)
                String style = params.optString("style", "dark");
                boolean darkIcons = "dark".equalsIgnoreCase(style);
                activity.runOnUiThread(() -> {
                    try {
                        Window window = activity.getWindow();
                        if (window != null) {
                            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
                            if (insetsController != null) {
                                insetsController.setAppearanceLightStatusBars(darkIcons);
                            }
                        }
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("style", style);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to set status bar style: " + e.getMessage());
                    }
                });
                break;
            }

            case "setColor": {
                String colorHex = params.optString("color", "#FFFFFF");
                activity.runOnUiThread(() -> {
                    try {
                        Window window = activity.getWindow();
                        if (window != null) {
                            window.setStatusBarColor(Color.parseColor(colorHex.trim()));
                        }
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("color", colorHex);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to set status bar color: " + e.getMessage());
                    }
                });
                break;
            }

            case "setTransparent": {
                boolean transparent = params.optBoolean("transparent", true);
                activity.runOnUiThread(() -> {
                    try {
                        Window window = activity.getWindow();
                        if (window != null) {
                            WindowCompat.setDecorFitsSystemWindows(window, !transparent);
                            if (transparent) {
                                window.setStatusBarColor(Color.TRANSPARENT);
                            }
                        }
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("transparent", transparent);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to set status bar transparency: " + e.getMessage());
                    }
                });
                break;
            }

            case "show": {
                activity.runOnUiThread(() -> {
                    try {
                        Window window = activity.getWindow();
                        if (window != null) {
                            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
                            if (insetsController != null) {
                                insetsController.show(WindowInsetsCompat.Type.statusBars());
                            }
                        }
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("visible", true);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to show status bar: " + e.getMessage());
                    }
                });
                break;
            }

            case "hide": {
                activity.runOnUiThread(() -> {
                    try {
                        Window window = activity.getWindow();
                        if (window != null) {
                            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
                            if (insetsController != null) {
                                insetsController.hide(WindowInsetsCompat.Type.statusBars());
                            }
                        }
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("visible", false);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to hide status bar: " + e.getMessage());
                    }
                });
                break;
            }

            case "getInfo": {
                activity.runOnUiThread(() -> {
                    try {
                        int statusBarHeight = 0;
                        int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
                        if (resourceId > 0) {
                            statusBarHeight = activity.getResources().getDimensionPixelSize(resourceId);
                        }
                        JSONObject res = new JSONObject();
                        res.put("height", statusBarHeight);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to get status bar info: " + e.getMessage());
                    }
                });
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'statusBar'");
                break;
        }
    }
}
