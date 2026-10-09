package com.example.cookiebrowser.plugins;

import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * 原生 TabBar 桥接插件
 * 支持动态设置选项卡（标题、图标、角标徽标、背景色等）并在 Web 端监听点击事件
 */
public class TabBarPlugin implements IBridgePlugin {

    private final WeakReference<MainActivity> activityRef;

    public TabBarPlugin(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    @Override
    public String getModuleName() {
        return "tabbar";
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
            case "setItems": {
                JSONArray items = params.optJSONArray("items");
                if (items == null) {
                    items = new JSONArray();
                }
                final JSONArray finalItems = items;
                activity.runOnUiThread(() -> {
                    try {
                        activity.setTabBarItems(finalItems, params);
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("count", finalItems.length());
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to set tab bar items: " + e.getMessage());
                    }
                });
                break;
            }

            case "show":
            case "setVisible": {
                boolean visible = params.optBoolean("visible", true);
                activity.runOnUiThread(() -> {
                    activity.setTabBarVisible(visible);
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("visible", visible);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "hide": {
                activity.runOnUiThread(() -> {
                    activity.setTabBarVisible(false);
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
                    boolean nowVisible = activity.toggleTabBarVisible();
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("visible", nowVisible);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "setSelected": {
                String id = params.optString("id", "");
                int index = params.optInt("index", -1);
                activity.runOnUiThread(() -> {
                    boolean ok = activity.setTabBarSelected(id, index);
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", ok);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "setBadge": {
                String id = params.optString("id", "");
                int index = params.optInt("index", -1);
                String badge = params.optString("badge", "");
                activity.runOnUiThread(() -> {
                    boolean ok = activity.setTabBarBadge(id, index, badge);
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", ok);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "getState": {
                activity.runOnUiThread(() -> {
                    try {
                        JSONObject state = activity.getTabBarState();
                        callback.success(state);
                    } catch (Exception e) {
                        callback.error(500, e.getMessage());
                    }
                });
                break;
            }

            default:
                callback.error(404, "Unknown action: " + action);
                break;
        }
    }
}
