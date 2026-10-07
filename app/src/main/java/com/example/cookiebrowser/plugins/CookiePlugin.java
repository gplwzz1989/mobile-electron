package com.example.cookiebrowser.plugins;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.CookieManager;

import com.example.cookiebrowser.CookieHelper;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.core.WebViewPool;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 原生 Cookie 控制插件（支持指定 Profile / Page 独立隔离读写，支持全量提取包括 HttpOnly 的所有 Cookie）
 */
public class CookiePlugin implements IBridgePlugin {

    private final WebViewPool webViewPool;
    private final Context context;

    public CookiePlugin() {
        this(null, null);
    }

    public CookiePlugin(WebViewPool webViewPool) {
        this(webViewPool, webViewPool != null ? webViewPool.getContext() : null);
    }

    public CookiePlugin(WebViewPool webViewPool, Context context) {
        this.webViewPool = webViewPool;
        this.context = context != null ? context : (webViewPool != null ? webViewPool.getContext() : null);
    }

    @Override
    public String getModuleName() {
        return "cookie";
    }

    @Override
    public int getRequiredTier(String action) {
        // Cookie 读写属于核心特权
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

    private String resolveTargetUrl(String callingUrl, JSONObject params) {
        String targetUrl = params.optString("url", "");
        if (TextUtils.isEmpty(targetUrl)) {
            String pageId = params.optString("pageId");
            if (!TextUtils.isEmpty(pageId) && webViewPool != null) {
                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page != null && page.webView != null) {
                    targetUrl = page.webView.getUrl();
                }
            }
        }
        if (TextUtils.isEmpty(targetUrl) && !TextUtils.isEmpty(callingUrl)) {
            targetUrl = callingUrl;
        }
        return targetUrl;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        mainHandler.post(() -> {
            try {
                CookieManager cm = resolveCookieManager(params);
                String profileName = resolveProfileName(params);

                switch (action) {

            case "get":
            case "getAll": {
                boolean isGetAll = "getAll".equals(action) || params.optBoolean("all", false);
                String targetUrl = resolveTargetUrl(callingUrl, params);

                // 如果未传任何 url 且不是 getAll，但指定了 domain 参数
                String domain = params.optString("domain", "");
                String queryTarget = !TextUtils.isEmpty(targetUrl) ? targetUrl : domain;

                List<CookieHelper.CookieItem> items = CookieHelper.readCookiesWithDetails(
                        context,
                        cm,
                        profileName,
                        queryTarget,
                        isGetAll || TextUtils.isEmpty(queryTarget)
                );

                JSONArray detailsArray = new JSONArray();
                StringBuilder sb = new StringBuilder();
                for (CookieHelper.CookieItem item : items) {
                    detailsArray.put(item.toJson());
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(item.name).append("=").append(item.value);
                }

                JSONObject result = new JSONObject();
                result.put("cookies", sb.toString());
                result.put("details", detailsArray);
                result.put("count", items.size());
                result.put("profile", profileName);
                callback.success(result);
                break;
            }

            case "import":
            case "set": {
                String targetUrl = resolveTargetUrl(callingUrl, params);

                int count = 0;
                // 支持直接传入 Cookie 数组（含 httpOnly / domain / path 等结构体）
                JSONArray cookiesArr = params.optJSONArray("cookies");
                if (cookiesArr == null) cookiesArr = params.optJSONArray("items");

                if (cookiesArr != null) {
                    List<CookieHelper.CookieItem> list = new ArrayList<>();
                    for (int i = 0; i < cookiesArr.length(); i++) {
                        JSONObject obj = cookiesArr.optJSONObject(i);
                        if (obj != null) {
                            CookieHelper.CookieItem item = CookieHelper.CookieItem.fromJson(obj);
                            if (item != null) list.add(item);
                        }
                    }
                    count = CookieHelper.importCookies(cm, targetUrl, list);
                } else {
                    String input = params.optString("input", params.optString("cookies", ""));
                    if (TextUtils.isEmpty(input)) {
                        callback.error(400, "Cookie input content cannot be empty");
                        return;
                    }
                    count = CookieHelper.importCookies(cm, targetUrl, input);
                }

                JSONObject result = new JSONObject();
                result.put("success", true);
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
            } catch (Exception e) {
                callback.error(500, e.getMessage());
            }
        });
    }
}

