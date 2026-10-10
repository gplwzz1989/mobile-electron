package com.example.cookiebrowser.plugins;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.core.AppConfig;
import com.example.cookiebrowser.core.AppConfigManager;
import com.example.cookiebrowser.core.WebViewPool;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * 宿主应用系统级控制与框架配置插件
 */
public class AppPlugin implements IBridgePlugin {

    private final WeakReference<MainActivity> activityRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AppPlugin(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    @Override
    public String getModuleName() {
        return "app";
    }

    @Override
    public int getRequiredTier(String action) {
        if ("getInfo".equals(action)) {
            return DomainWhitelistManager.TIER_0_PUBLIC;
        }
        return DomainWhitelistManager.TIER_1_BUSINESS;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        MainActivity activity = activityRef.get();

        switch (action) {
            case "getInfo": {
                AppConfig config = AppConfigManager.getInstance().getConfig();
                JSONObject res = new JSONObject();
                res.put("appName", config.getAppName());
                res.put("appId", config.getAppId());
                res.put("version", config.getVersion());
                res.put("framework", "MobileElectron");
                res.put("frameworkVersion", "2.0.0");
                res.put("multiProfileSupported", WebViewPool.isMultiProfileSupported());
                res.put("defaultUrl", config.getDefaultUrl());
                res.put("config", config.toJson());
                callback.success(res);
                break;
            }

            case "exit": {
                mainHandler.post(() -> {
                    if (activity != null) {
                        activity.finishAffinity();
                        System.exit(0);
                    }
                });
                JSONObject res = new JSONObject();
                res.put("success", true);
                callback.success(res);
                break;
            }

            case "getConfig": {
                AppConfig config = AppConfigManager.getInstance().getConfig();
                callback.success(config.toJson());
                break;
            }

            case "getConfigStatus": {
                AppConfigManager mgr = AppConfigManager.getInstance();
                AppConfig config = mgr.getConfig();
                JSONObject res = new JSONObject();
                res.put("source", mgr.getConfigSource());
                res.put("defaultUrl", config.getDefaultUrl());
                res.put("whitelistCount", config.getWhitelist() != null ? config.getWhitelist().size() : 0);
                res.put("version", config.getVersion());
                callback.success(res);
                break;
            }

            case "fetchRemoteConfig": {
                if (activity == null) {
                    callback.error(500, "Activity context is null");
                    return;
                }
                String url = params.optString("url", "").trim();
                if (url.isEmpty()) {
                    callback.error(400, "Missing remote config URL");
                    return;
                }
                AppConfigManager.getInstance().fetchRemoteConfig(activity, url, new AppConfigManager.RemoteConfigCallback() {
                    @Override
                    public void onSuccess(AppConfig config, String source) {
                        try {
                            JSONObject res = new JSONObject();
                            res.put("success", true);
                            res.put("source", source);
                            res.put("config", config.toJson());
                            callback.success(res);
                        } catch (Exception e) {
                            callback.error(500, e.getMessage());
                        }
                    }

                    @Override
                    public void onError(int code, String message) {
                        callback.error(code, message);
                    }
                });
                break;
            }

            case "applyRemoteConfig": {
                if (activity == null) {
                    callback.error(500, "Activity context is null");
                    return;
                }
                JSONObject configJson = params.optJSONObject("config");
                if (configJson == null) {
                    callback.error(400, "Missing config object parameter");
                    return;
                }
                try {
                    AppConfig newConfig = AppConfig.fromJson(configJson);
                    boolean persist = params.optBoolean("persist", true);
                    if (persist) {
                        AppConfigManager.getInstance().saveConfigToSandbox(activity, configJson.toString(2));
                    }
                    AppConfigManager.getInstance().updateConfig(newConfig);

                    JSONObject res = new JSONObject();
                    res.put("success", true);
                    res.put("applied", true);
                    res.put("config", newConfig.toJson());
                    callback.success(res);
                } catch (Exception e) {
                    callback.error(500, "Failed to apply config: " + e.getMessage());
                }
                break;
            }

            case "resetRemoteConfig": {
                if (activity == null) {
                    callback.error(500, "Activity context is null");
                    return;
                }
                boolean reset = AppConfigManager.getInstance().resetToDefault(activity);
                JSONObject res = new JSONObject();
                res.put("success", reset);
                res.put("source", AppConfigManager.getInstance().getConfigSource());
                callback.success(res);
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'app'");
                break;
        }
    }
}
