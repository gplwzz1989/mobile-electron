package com.example.cookiebrowser.plugins;

import android.app.Activity;
import android.webkit.WebView;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.bridge.JsBridgeEngine;
import com.example.cookiebrowser.core.HotUpdateManager;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

/**
 * 原生离线热更新插件 (electron.hotUpdate)
 * 提供离线包版本查询、增量下载、哈希校验、解压热重载与回滚能力
 */
public class HotUpdatePlugin implements IBridgePlugin {

    private final Activity activity;
    private final WebView webView;
    private JsBridgeEngine bridgeEngine;

    public HotUpdatePlugin(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public void setBridgeEngine(JsBridgeEngine bridgeEngine) {
        this.bridgeEngine = bridgeEngine;
    }

    @Override
    public String getModuleName() {
        return "hotUpdate";
    }

    @Override
    public int getRequiredTier(String action) {
        if ("getCurrentVersion".equalsIgnoreCase(action)) {
            return DomainWhitelistManager.TIER_0_PUBLIC;
        } else if ("checkForUpdate".equalsIgnoreCase(action)) {
            return DomainWhitelistManager.TIER_1_BUSINESS;
        }
        return DomainWhitelistManager.TIER_2_CORE;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        if ("getCurrentVersion".equalsIgnoreCase(action)) {
            JSONObject info = HotUpdateManager.getInstance().getActiveVersionInfo();
            callback.success(info);
            return;
        }

        if ("checkForUpdate".equalsIgnoreCase(action)) {
            String customUrl = params != null ? params.optString("url", null) : null;
            HotUpdateManager.getInstance().checkForUpdate(customUrl, new HotUpdateManager.Callback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    callback.success(result);
                }

                @Override
                public void onError(int code, String message) {
                    callback.error(code, message);
                }
            });
            return;
        }

        if ("downloadAndInstall".equalsIgnoreCase(action)) {
            if (params == null) {
                callback.error(400, "缺少参数");
                return;
            }
            String packageUrl = params.optString("packageUrl", "");
            String packageHash = params.optString("packageHash", "");
            JSONObject diffInfo = params.optJSONObject("diff");

            HotUpdateManager.getInstance().downloadAndInstall(
                    packageUrl,
                    packageHash,
                    diffInfo,
                    (percent, downloadedBytes, totalBytes) -> {
                        if (bridgeEngine != null) {
                            try {
                                JSONObject prog = new JSONObject();
                                prog.put("percent", percent);
                                prog.put("downloadedBytes", downloadedBytes);
                                prog.put("totalBytes", totalBytes);
                                bridgeEngine.sendEvent("hotUpdate:progress", prog);
                            } catch (Exception ignored) {}
                        }
                    },
                    new HotUpdateManager.Callback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject result) {
                            callback.success(result);
                        }

                        @Override
                        public void onError(int code, String message) {
                            callback.error(code, message);
                        }
                    }
            );
            return;
        }

        if ("applyAndReload".equalsIgnoreCase(action)) {
            activity.runOnUiThread(() -> {
                if (webView != null) {
                    webView.reload();
                }
            });
            JSONObject res = new JSONObject();
            res.put("applied", true);
            callback.success(res);
            return;
        }

        if ("rollback".equalsIgnoreCase(action)) {
            boolean ok = HotUpdateManager.getInstance().rollback();
            if (ok) {
                activity.runOnUiThread(() -> {
                    if (webView != null) {
                        webView.reload();
                    }
                });
                JSONObject res = new JSONObject();
                res.put("rolledBack", true);
                callback.success(res);
            } else {
                callback.error(500, "回滚失败");
            }
            return;
        }

        callback.error(404, "HotUpdatePlugin: 未知动作 " + action);
    }
}
