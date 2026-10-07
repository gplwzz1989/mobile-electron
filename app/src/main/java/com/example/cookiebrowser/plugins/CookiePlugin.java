package com.example.cookiebrowser.plugins;

import android.text.TextUtils;
import android.webkit.CookieManager;

import com.example.cookiebrowser.CookieHelper;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.core.WebViewPool;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

/**
 * 原生 Cookie 控制插件（支持指定 Profile / Page 独立隔离读写）
 */
public class CookiePlugin implements IBridgePlugin {

    private final WebViewPool webViewPool;

    public CookiePlugin() {
        this(null);
    }

    public CookiePlugin(WebViewPool webViewPool) {
        this.webViewPool = webViewPool;
    }

    @Override
    public String getModuleName() {
        return "cookie";
    }

    @Override
    public int getRequiredTier(String action) {
        // Cookie 读写属于核心高危特权
        return DomainWhitelistManager.TIER_2_CORE;
    }

    private CookieManager resolveCookieManager(JSONObject params) {
        if (webViewPool == null) {
            return CookieManager.getInstance();
        }

        String pageId = params.optString("pageId");
        if (!TextUtils.isEmpty(pageId)) {
            WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
            if (page != null) {
                return webViewPool.getCookieManager(page.profileName);
            }
        }

        String profile = params.optString("profile", params.optString("partition"));
        if (!TextUtils.isEmpty(profile)) {
            return webViewPool.getCookieManager(profile);
        }

        // 默认匹配当前前台活跃页面的 Profile
        WebViewPool.ManagedPage active = webViewPool.getActiveForegroundPage();
        if (active != null) {
            return webViewPool.getCookieManager(active.profileName);
        }

        return CookieManager.getInstance();
    }

    private String resolveProfileName(JSONObject params) {
        if (webViewPool == null) {
            return "default";
        }
        String pageId = params.optString("pageId");
        if (!TextUtils.isEmpty(pageId)) {
            WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
            if (page != null) return page.profileName;
        }
        String profile = params.optString("profile", params.optString("partition"));
        if (!TextUtils.isEmpty(profile)) return profile;

        WebViewPool.ManagedPage active = webViewPool.getActiveForegroundPage();
        if (active != null) return active.profileName;
        return "default";
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        CookieManager cm = resolveCookieManager(params);
        String profileName = resolveProfileName(params);

        switch (action) {
            case "get": {
                String targetUrl = params.optString("url", callingUrl);
                if (TextUtils.isEmpty(targetUrl)) {
                    callback.error(400, "Missing target URL parameter");
                    return;
                }
                String cookies = CookieHelper.exportCookiesAsStandardString(cm, targetUrl);
                JSONObject result = new JSONObject();
                result.put("cookies", cookies);
                result.put("profile", profileName);
                callback.success(result);
                break;
            }

            case "import": {
                String targetUrl = params.optString("url", callingUrl);
                String input = params.optString("input", "");
                if (TextUtils.isEmpty(targetUrl) || TextUtils.isEmpty(input)) {
                    callback.error(400, "URL and input content cannot be empty");
                    return;
                }

                int count = CookieHelper.importCookies(cm, targetUrl, input);
                JSONObject result = new JSONObject();
                result.put("count", count);
                result.put("profile", profileName);
                callback.success(result);
                break;
            }

            case "clear": {
                cm.removeAllCookies(value -> {
                    cm.flush();
                    try {
                        JSONObject result = new JSONObject();
                        result.put("success", true);
                        result.put("profile", profileName);
                        callback.success(result);
                    } catch (Exception ignored) {}
                });
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'cookie'");
                break;
        }
    }
}
