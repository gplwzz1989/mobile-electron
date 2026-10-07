package com.example.cookiebrowser.plugins;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebView;

import com.example.cookiebrowser.CookieHelper;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.core.WebViewPool;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 综合存储控制插件 (支持原生持久化键值存储 + 任意 WebView 实例的 LocalStorage / SessionStorage 深度读写)
 */
public class StoragePlugin implements IBridgePlugin {

    private static final String PREF_NAME = "MobileElectron_Storage";
    private final Context context;
    private final SharedPreferences prefs;
    private final WebViewPool webViewPool;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public StoragePlugin(Context context) {
        this(context, null);
    }

    public StoragePlugin(Context context, WebViewPool webViewPool) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.webViewPool = webViewPool;
    }

    @Override
    public String getModuleName() {
        return "storage";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_1_BUSINESS;
    }

    private WebViewPool.ManagedPage resolveTargetPage(JSONObject params) {
        if (webViewPool == null) return null;
        String pageId = params.optString("pageId");
        if (!TextUtils.isEmpty(pageId)) {
            return webViewPool.getPage(pageId);
        }
        return webViewPool.getActiveForegroundPage();
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        switch (action) {
            // ==================== 1. 原生 SharedPreferences 持久化存储 ====================
            case "set": {
                String key = params.optString("key", "");
                String value = params.optString("value", "");
                if (key.isEmpty()) {
                    callback.error(400, "Missing key parameter");
                    return;
                }
                prefs.edit().putString(key, value).apply();
                JSONObject res = new JSONObject();
                res.put("success", true);
                callback.success(res);
                break;
            }

            case "get": {
                String key = params.optString("key", "");
                if (key.isEmpty()) {
                    callback.error(400, "Missing key parameter");
                    return;
                }
                String val = prefs.getString(key, null);
                JSONObject res = new JSONObject();
                res.put("value", val != null ? val : JSONObject.NULL);
                res.put("exists", val != null);
                callback.success(res);
                break;
            }

            case "remove": {
                String key = params.optString("key", "");
                prefs.edit().remove(key).apply();
                JSONObject res = new JSONObject();
                res.put("success", true);
                callback.success(res);
                break;
            }

            case "clear": {
                prefs.edit().clear().apply();
                JSONObject res = new JSONObject();
                res.put("success", true);
                callback.success(res);
                break;
            }

            case "keys": {
                Map<String, ?> all = prefs.getAll();
                JSONArray arr = new JSONArray();
                for (String k : all.keySet()) {
                    arr.put(k);
                }
                JSONObject res = new JSONObject();
                res.put("keys", arr);
                callback.success(res);
                break;
            }

            // ==================== 2. WebView LocalStorage 深度读写 ====================
            case "getLocalStorage": {
                WebViewPool.ManagedPage target = resolveTargetPage(params);
                if (target == null || target.webView == null) {
                    callback.error(404, "Target WebView page not found");
                    return;
                }

                mainHandler.post(() -> {
                    String script = "(function() {" +
                            "  try {" +
                            "    var res = {};" +
                            "    for (var i = 0; i < localStorage.length; i++) {" +
                            "      var k = localStorage.key(i);" +
                            "      if (k !== null) res[k] = localStorage.getItem(k);" +
                            "    }" +
                            "    return JSON.stringify({ success: true, data: res });" +
                            "  } catch(e) {" +
                            "    return JSON.stringify({ success: false, error: e.toString() });" +
                            "  }" +
                            "})();";

                    target.webView.evaluateJavascript(script, rawVal -> {
                        try {
                            String unquoted = rawVal != null ? parseJsJsonString(rawVal) : "{}";
                            JSONObject evalRes = new JSONObject(unquoted);
                            if (evalRes.optBoolean("success", false)) {
                                JSONObject data = evalRes.optJSONObject("data");
                                if (data == null) data = new JSONObject();
                                JSONObject res = new JSONObject();
                                res.put("data", data);
                                res.put("count", data.length());
                                res.put("pageId", target.pageId);
                                res.put("url", target.webView.getUrl());
                                callback.success(res);
                            } else {
                                callback.error(500, evalRes.optString("error", "Failed to get localStorage"));
                            }
                        } catch (Exception e) {
                            callback.error(500, "Error parsing localStorage result: " + e.getMessage());
                        }
                    });
                });
                break;
            }

            case "setLocalStorage": {
                WebViewPool.ManagedPage target = resolveTargetPage(params);
                if (target == null || target.webView == null) {
                    callback.error(404, "Target WebView page not found");
                    return;
                }

                JSONObject items = params.optJSONObject("data");
                if (items == null) items = params.optJSONObject("items");
                if (items == null) {
                    String key = params.optString("key");
                    String val = params.optString("value");
                    if (!TextUtils.isEmpty(key)) {
                        items = new JSONObject();
                        items.put(key, val);
                    }
                }

                if (items == null || items.length() == 0) {
                    callback.error(400, "No items provided to set in localStorage");
                    return;
                }

                final JSONObject toSet = items;
                mainHandler.post(() -> {
                    String escapedJson = JSONObject.quote(toSet.toString());
                    String script = "(function() {" +
                            "  try {" +
                            "    var items = JSON.parse(" + escapedJson + ");" +
                            "    for (var k in items) {" +
                            "      if (Object.prototype.hasOwnProperty.call(items, k)) {" +
                            "        localStorage.setItem(k, items[k]);" +
                            "      }" +
                            "    }" +
                            "    return JSON.stringify({ success: true, count: Object.keys(items).length });" +
                            "  } catch(e) {" +
                            "    return JSON.stringify({ success: false, error: e.toString() });" +
                            "  }" +
                            "})();";

                    target.webView.evaluateJavascript(script, rawVal -> {
                        try {
                            String unquoted = rawVal != null ? parseJsJsonString(rawVal) : "{}";
                            JSONObject evalRes = new JSONObject(unquoted);
                            if (evalRes.optBoolean("success", false)) {
                                JSONObject res = new JSONObject();
                                res.put("success", true);
                                res.put("count", evalRes.optInt("count", 0));
                                res.put("pageId", target.pageId);
                                callback.success(res);
                            } else {
                                callback.error(500, evalRes.optString("error", "Failed to set localStorage"));
                            }
                        } catch (Exception e) {
                            callback.error(500, e.getMessage());
                        }
                    });
                });
                break;
            }

            case "clearLocalStorage": {
                WebViewPool.ManagedPage target = resolveTargetPage(params);
                if (target == null || target.webView == null) {
                    callback.error(404, "Target WebView page not found");
                    return;
                }

                mainHandler.post(() -> {
                    target.webView.evaluateJavascript("localStorage.clear(); 'true';", rawVal -> {
                        JSONObject res = new JSONObject();
                        try {
                            res.put("success", true);
                            res.put("pageId", target.pageId);
                            callback.success(res);
                        } catch (Exception ignored) {}
                    });
                });
                break;
            }

            // ==================== 3. WebView SessionStorage 读写 ====================
            case "getSessionStorage": {
                WebViewPool.ManagedPage target = resolveTargetPage(params);
                if (target == null || target.webView == null) {
                    callback.error(404, "Target WebView page not found");
                    return;
                }

                mainHandler.post(() -> {
                    String script = "(function() {" +
                            "  try {" +
                            "    var res = {};" +
                            "    for (var i = 0; i < sessionStorage.length; i++) {" +
                            "      var k = sessionStorage.key(i);" +
                            "      if (k !== null) res[k] = sessionStorage.getItem(k);" +
                            "    }" +
                            "    return JSON.stringify({ success: true, data: res });" +
                            "  } catch(e) {" +
                            "    return JSON.stringify({ success: false, error: e.toString() });" +
                            "  }" +
                            "})();";

                    target.webView.evaluateJavascript(script, rawVal -> {
                        try {
                            String unquoted = rawVal != null ? parseJsJsonString(rawVal) : "{}";
                            JSONObject evalRes = new JSONObject(unquoted);
                            JSONObject data = evalRes.optJSONObject("data");
                            if (data == null) data = new JSONObject();
                            JSONObject res = new JSONObject();
                            res.put("data", data);
                            res.put("count", data.length());
                            res.put("pageId", target.pageId);
                            callback.success(res);
                        } catch (Exception e) {
                            callback.error(500, e.getMessage());
                        }
                    });
                });
                break;
            }

            case "setSessionStorage": {
                WebViewPool.ManagedPage target = resolveTargetPage(params);
                if (target == null || target.webView == null) {
                    callback.error(404, "Target WebView page not found");
                    return;
                }

                JSONObject items = params.optJSONObject("data");
                if (items == null) items = params.optJSONObject("items");
                if (items == null) {
                    String key = params.optString("key");
                    String val = params.optString("value");
                    if (!TextUtils.isEmpty(key)) {
                        items = new JSONObject();
                        items.put(key, val);
                    }
                }

                if (items == null) {
                    callback.error(400, "No items provided");
                    return;
                }

                final JSONObject toSet = items;
                mainHandler.post(() -> {
                    String escapedJson = JSONObject.quote(toSet.toString());
                    String script = "(function() {" +
                            "  try {" +
                            "    var items = JSON.parse(" + escapedJson + ");" +
                            "    for (var k in items) {" +
                            "      if (Object.prototype.hasOwnProperty.call(items, k)) {" +
                            "        sessionStorage.setItem(k, items[k]);" +
                            "      }" +
                            "    }" +
                            "    return JSON.stringify({ success: true });" +
                            "  } catch(e) {" +
                            "    return JSON.stringify({ success: false, error: e.toString() });" +
                            "  }" +
                            "})();";

                    target.webView.evaluateJavascript(script, rawVal -> {
                        JSONObject res = new JSONObject();
                        try {
                            res.put("success", true);
                            res.put("pageId", target.pageId);
                            callback.success(res);
                        } catch (Exception ignored) {}
                    });
                });
                break;
            }

            case "clearSessionStorage": {
                WebViewPool.ManagedPage target = resolveTargetPage(params);
                if (target == null || target.webView == null) {
                    callback.error(404, "Target WebView page not found");
                    return;
                }

                mainHandler.post(() -> {
                    target.webView.evaluateJavascript("sessionStorage.clear(); 'true';", rawVal -> {
                        JSONObject res = new JSONObject();
                        try {
                            res.put("success", true);
                            res.put("pageId", target.pageId);
                            callback.success(res);
                        } catch (Exception ignored) {}
                    });
                });
                break;
            }

            // ==================== 4. 一键导出全量存储 (Cookies含HttpOnly + LocalStorage) ====================
            case "dumpStorage": {
                WebViewPool.ManagedPage target = resolveTargetPage(params);
                if (target == null || target.webView == null) {
                    callback.error(404, "Target WebView page not found");
                    return;
                }

                mainHandler.post(() -> {
                    String pageUrl = target.webView.getUrl();
                    CookieManager cm = webViewPool.getCookieManager(target.profileName);

                    List<CookieHelper.CookieItem> cookieList = CookieHelper.readCookiesWithDetails(
                            context,
                            cm,
                            target.profileName,
                            pageUrl,
                            params.optBoolean("allCookies", false)
                    );

                    JSONArray cookieDetails = new JSONArray();
                    StringBuilder cookieStr = new StringBuilder();
                    for (CookieHelper.CookieItem c : cookieList) {
                        cookieDetails.put(c.toJson());
                        if (cookieStr.length() > 0) cookieStr.append("; ");
                        cookieStr.append(c.name).append("=").append(c.value);
                    }

                    String script = "(function() {" +
                            "  var ls = {}, ss = {};" +
                            "  try {" +
                            "    for (var i = 0; i < localStorage.length; i++) {" +
                            "      var k = localStorage.key(i);" +
                            "      if (k !== null) ls[k] = localStorage.getItem(k);" +
                            "    }" +
                            "  } catch(e) {}" +
                            "  try {" +
                            "    for (var j = 0; j < sessionStorage.length; j++) {" +
                            "      var sk = sessionStorage.key(j);" +
                            "      if (sk !== null) ss[sk] = sessionStorage.getItem(sk);" +
                            "    }" +
                            "  } catch(e) {}" +
                            "  return JSON.stringify({ localStorage: ls, sessionStorage: ss });" +
                            "})();";

                    target.webView.evaluateJavascript(script, rawVal -> {
                        try {
                            String unquoted = rawVal != null ? parseJsJsonString(rawVal) : "{}";
                            JSONObject storageObj = new JSONObject(unquoted);
                            JSONObject result = new JSONObject();
                            result.put("pageId", target.pageId);
                            result.put("profile", target.profileName);
                            result.put("url", pageUrl);
                            result.put("cookies", cookieDetails);
                            result.put("cookieString", cookieStr.toString());
                            result.put("localStorage", storageObj.optJSONObject("localStorage"));
                            result.put("sessionStorage", storageObj.optJSONObject("sessionStorage"));
                            callback.success(result);
                        } catch (Exception e) {
                            callback.error(500, e.getMessage());
                        }
                    });
                });
                break;
            }


            default:
                callback.error(404, "Unknown action '" + action + "' in module 'storage'");
                break;
        }
    }

    /**
     * evaluateJavascript 返回的结果是外层被 JSON 序列化的字符串（带外层引号和转义）
     */
    private static String parseJsJsonString(String rawVal) {
        if (rawVal == null) return "{}";
        String s = rawVal.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            try {
                // 利用 JSONObject 或 JSONTokener 解开外层字符串
                org.json.JSONTokener tokener = new org.json.JSONTokener(s);
                Object val = tokener.nextValue();
                return val != null ? val.toString() : "{}";
            } catch (Exception ignored) {
                if (s.length() >= 2) return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            }
        }
        return s;
    }
}
