package com.example.cookiebrowser.plugins;

import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * 框架调试与 DevTools 桥接插件
 * 支持显示/隐藏框架调试中心，以及针对当前 WebView 开启/关闭/切换 DevTools
 */
public class DebugPlugin implements IBridgePlugin {

    private final WeakReference<MainActivity> activityRef;

    public DebugPlugin(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    @Override
    public String getModuleName() {
        return "debug";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_0_PUBLIC;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        MainActivity activity = activityRef.get();
        if (activity == null) {
            callback.error(500, "Activity context is released");
            return;
        }

        switch (action) {
            case "show": {
                activity.runOnUiThread(() -> {
                    activity.showFrameworkDebugDialog();
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("visible", true);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "hide": {
                activity.runOnUiThread(() -> {
                    activity.dismissFrameworkDebugDialog();
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("visible", false);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "toggle": {
                activity.runOnUiThread(() -> {
                    boolean visible = activity.toggleFrameworkDebugDialog();
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("visible", visible);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "openDevTools": {
                activity.runOnUiThread(() -> {
                    try {
                        JSONObject res = activity.openDevToolsForActiveWebView();
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to open DevTools: " + e.getMessage());
                    }
                });
                break;
            }

            case "closeDevTools": {
                activity.runOnUiThread(() -> {
                    try {
                        JSONObject res = activity.closeDevToolsForActiveWebView();
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to close DevTools: " + e.getMessage());
                    }
                });
                break;
            }

            case "toggleDevTools": {
                activity.runOnUiThread(() -> {
                    try {
                        JSONObject res = activity.toggleDevToolsForActiveWebView();
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to toggle DevTools: " + e.getMessage());
                    }
                });
                break;
            }

            case "getInfo": {
                activity.runOnUiThread(() -> {
                    try {
                        JSONObject info = activity.getFrameworkDebugInfo();
                        callback.success(info);
                    } catch (Exception e) {
                        callback.error(500, e.getMessage());
                    }
                });
                break;
            }

            case "setFloatingButtonVisible": {
                boolean visible = params.optBoolean("visible", true);
                activity.runOnUiThread(() -> {
                    activity.setFloatingDebugButtonVisible(visible);
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("visible", visible);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            default:
                callback.error(404, "Unknown action: " + action);
                break;
        }
    }
}
