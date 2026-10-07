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

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'app'");
                break;
        }
    }
}
