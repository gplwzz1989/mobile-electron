package com.example.cookiebrowser.plugins;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
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
import java.util.List;

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
        if ("getInfo".equals(action) || "canOpenUrl".equals(action) || "openUrl".equals(action)) {
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

            case "canOpenUrl": {
                String targetUrl = params != null ? params.optString("url", "").trim() : "";
                if (targetUrl.isEmpty()) {
                    callback.error(400, "Missing 'url' parameter");
                    return;
                }
                boolean canOpen = false;
                try {
                    Intent intent;
                    if (targetUrl.startsWith("intent://")) {
                        intent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME);
                    } else {
                        intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
                    }
                    PackageManager pm = activity != null ? activity.getPackageManager() : null;
                    if (pm != null) {
                        ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                        canOpen = (info != null);
                        if (!canOpen) {
                            List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                            canOpen = (list != null && !list.isEmpty());
                        }
                    }
                } catch (Exception e) {
                    canOpen = false;
                }
                JSONObject res = new JSONObject();
                res.put("canOpen", canOpen);
                callback.success(res);
                break;
            }

            case "openUrl": {
                if (activity == null) {
                    callback.error(500, "Activity context is null");
                    return;
                }
                String targetUrl = params != null ? params.optString("url", "").trim() : "";
                if (targetUrl.isEmpty()) {
                    callback.error(400, "Missing 'url' parameter");
                    return;
                }
                String fallbackUrl = params != null ? params.optString("fallbackUrl", "").trim() : "";

                mainHandler.post(() -> {
                    try {
                        Intent intent;
                        if (targetUrl.startsWith("intent://")) {
                            intent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME);
                        } else {
                            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        try {
                            activity.startActivity(intent);

                            JSONObject res = new JSONObject();
                            res.put("success", true);
                            callback.success(res);
                        } catch (ActivityNotFoundException notFound) {
                            if (!fallbackUrl.isEmpty()) {
                                try {
                                    Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl));
                                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    activity.startActivity(fallbackIntent);

                                    JSONObject res = new JSONObject();
                                    res.put("success", true);
                                    res.put("fallback", true);
                                    callback.success(res);
                                    return;
                                } catch (Exception ignored) {}
                            }
                            JSONObject res = new JSONObject();
                            res.put("success", false);
                            res.put("reason", "app_not_installed");
                            res.put("message", "未找到能够处理该协议的应用程序");
                            callback.success(res);
                        }
                    } catch (Exception e) {
                        callback.error(500, "拉起外部协议失败: " + e.getMessage());
                    }
                });
                break;
            }


            case "minimize": {
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        try {
                            activity.moveTaskToBack(true);
                        } catch (Exception ignored) {}
                    });
                }
                JSONObject res = new JSONObject();
                res.put("success", true);
                callback.success(res);
                break;
            }

            case "relaunch": {
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        try {
                            Intent restartIntent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
                            if (restartIntent != null) {
                                restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(restartIntent);
                            }
                            activity.finishAffinity();
                            Runtime.getRuntime().exit(0);
                        } catch (Exception ignored) {}
                    });
                }
                JSONObject res = new JSONObject();
                res.put("success", true);
                callback.success(res);
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'app'");
                break;
        }
    }
}
