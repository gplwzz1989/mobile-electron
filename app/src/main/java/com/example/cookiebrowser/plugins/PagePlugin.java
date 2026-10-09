package com.example.cookiebrowser.plugins;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.example.cookiebrowser.CookieHelper;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.core.HardwareConfig;
import com.example.cookiebrowser.core.WebViewPool;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 原生多页面、无头页面与独立 Profile 隔离控制插件
 */
public class PagePlugin implements IBridgePlugin {

    private final WebViewPool webViewPool;
    private final com.example.cookiebrowser.MainActivity mainActivity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PagePlugin(WebViewPool webViewPool, com.example.cookiebrowser.MainActivity mainActivity) {
        this.webViewPool = webViewPool;
        this.mainActivity = mainActivity;
    }

    public PagePlugin(WebViewPool webViewPool) {
        this(webViewPool, null);
    }

    @Override
    public String getModuleName() {
        return "page";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_2_CORE;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        switch (action) {
            case "create": {
                String ua = params.optString("userAgent", "");
                String profile = params.optString("profile", params.optString("partition", "default"));
                String preset = params.optString("preset", params.optString("hardwarePreset", ""));
                String initialUrl = params.optString("url", "");
                JSONObject hwJson = params.optJSONObject("hardware");
                HardwareConfig hwConfig = HardwareConfig.fromPresetOrParams(preset, hwJson);

                mainHandler.post(() -> {
                    try {
                        WebViewPool.ManagedPage page = webViewPool.createHeadlessPage(ua, profile, hwConfig, initialUrl);
                        JSONObject res = new JSONObject();
                        res.put("pageId", page.pageId);
                        res.put("profile", page.profileName);
                        res.put("url", initialUrl);
                        res.put("multiProfileSupported", WebViewPool.isMultiProfileSupported());
                        res.put("hardware", page.hardwareConfig != null ? page.hardwareConfig.toJson() : new JSONObject());
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to create headless page: " + e.getMessage());
                    }
                });
                break;
            }

            case "bringToFront":
            case "show": {
                String pageId = params.optString("pageId");
                if (TextUtils.isEmpty(pageId)) {
                    callback.error(400, "Missing pageId");
                    return;
                }

                mainHandler.post(() -> {
                    boolean ok = webViewPool.switchToForeground(pageId);
                    if (ok) {
                        try {
                            JSONObject res = new JSONObject();
                            res.put("success", true);
                            res.put("activePageId", pageId);
                            callback.success(res);
                        } catch (Exception ignored) {}
                    } else {
                        callback.error(404, "Page '" + pageId + "' cannot be switched to foreground");
                    }
                });
                break;
            }

            case "sendToBack":
            case "hide": {
                mainHandler.post(() -> {
                    boolean ok = webViewPool.switchToForeground("main");
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", ok);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "list": {
                mainHandler.post(() -> {
                    try {
                        List<WebViewPool.ManagedPage> all = webViewPool.getAllPages();
                        JSONArray arr = new JSONArray();
                        for (WebViewPool.ManagedPage p : all) {
                            JSONObject item = new JSONObject();
                            item.put("pageId", p.pageId);
                            item.put("url", p.webView != null ? p.webView.getUrl() : "");
                            item.put("title", p.webView != null ? p.webView.getTitle() : "");
                            item.put("isHeadless", p.isHeadless);
                            item.put("isForeground", p.isForeground);
                            item.put("profile", p.profileName);
                            item.put("hardware", p.hardwareConfig != null ? p.hardwareConfig.toJson() : new JSONObject());
                            arr.put(item);
                        }
                        JSONObject res = new JSONObject();
                        res.put("pages", arr);
                        res.put("multiProfileSupported", WebViewPool.isMultiProfileSupported());
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, e.getMessage());
                    }
                });
                break;
            }

            case "listProfiles": {
                mainHandler.post(() -> {
                    try {
                        List<String> list = webViewPool.getAllProfileNames();
                        JSONArray arr = new JSONArray();
                        for (String p : list) {
                            arr.put(p);
                        }
                        JSONObject res = new JSONObject();
                        res.put("profiles", arr);
                        res.put("multiProfileSupported", WebViewPool.isMultiProfileSupported());
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, e.getMessage());
                    }
                });
                break;
            }

            case "deleteProfile": {
                String profileName = params.optString("profile");
                if (TextUtils.isEmpty(profileName) || profileName.equalsIgnoreCase("default")) {
                    callback.error(400, "Cannot delete default profile or empty profile name");
                    return;
                }
                mainHandler.post(() -> {
                    boolean deleted = webViewPool.deleteProfile(profileName);
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", deleted);
                        res.put("profile", profileName);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, e.getMessage());
                    }
                });
                break;
            }

            case "goto": {
                String pageId = params.optString("pageId");
                String rawUrl = params.optString("url");
                String targetUrl = WebViewPool.resolveLocalUrl(rawUrl);
                int timeoutMs = params.optInt("timeoutMs", 15000);

                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page == null) {
                    callback.error(404, "Page ID '" + pageId + "' not found");
                    return;
                }

                mainHandler.post(() -> {
                    final boolean[] isResolved = {false};
                    final Runnable timeoutRunnable = () -> {
                        if (!isResolved[0]) {
                            isResolved[0] = true;
                            callback.error(408, "Page load timed out after " + timeoutMs + "ms");
                        }
                    };
                    mainHandler.postDelayed(timeoutRunnable, timeoutMs);

                    page.webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                            if (webViewPool.getAssetLoader() != null && request != null) {
                                WebResourceResponse response = webViewPool.getAssetLoader().shouldInterceptRequest(request.getUrl());
                                if (response != null) return response;
                            }
                            return super.shouldInterceptRequest(view, request);
                        }

                        @Override
                        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                            if (webViewPool.getAssetLoader() != null && url != null) {
                                WebResourceResponse response = webViewPool.getAssetLoader().shouldInterceptRequest(Uri.parse(url));
                                if (response != null) return response;
                            }
                            return super.shouldInterceptRequest(view, url);
                        }

                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            mainHandler.removeCallbacks(timeoutRunnable);
                            if (!isResolved[0]) {
                                isResolved[0] = true;
                                JSONObject res = new JSONObject();
                                try {
                                    res.put("pageId", pageId);
                                    res.put("url", url);
                                    res.put("profile", page.profileName);
                                } catch (Exception ignored) {}
                                callback.success(res);
                            }
                        }
                    });

                    page.webView.loadUrl(targetUrl);
                });
                break;
            }

            case "getCookies":
            case "getAllCookies": {
                String pageId = params.optString("pageId");
                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page == null) {
                    callback.error(404, "Page ID '" + pageId + "' not found");
                    return;
                }

                boolean isGetAll = "getAllCookies".equals(action) || params.optBoolean("all", false);
                String customUrl = params.optString("url");

                mainHandler.post(() -> {
                    String pageUrl = !TextUtils.isEmpty(customUrl) ? customUrl : (page.webView != null ? page.webView.getUrl() : "");
                    CookieManager cm = webViewPool.getCookieManager(page.profileName);

                    List<CookieHelper.CookieItem> items = CookieHelper.readCookiesWithDetails(
                            webViewPool.getContext(),
                            cm,
                            page.profileName,
                            pageUrl,
                            isGetAll || TextUtils.isEmpty(pageUrl)
                    );

                    JSONArray detailsArray = new JSONArray();
                    StringBuilder sb = new StringBuilder();
                    for (CookieHelper.CookieItem item : items) {
                        detailsArray.put(item.toJson());
                        if (sb.length() > 0) sb.append("; ");
                        sb.append(item.name).append("=").append(item.value);
                    }

                    try {
                        JSONObject res = new JSONObject();
                        res.put("cookies", sb.toString());
                        res.put("details", detailsArray);
                        res.put("count", items.size());
                        res.put("profile", page.profileName);
                        res.put("pageId", page.pageId);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, e.getMessage());
                    }
                });
                break;
            }

            case "setCookies": {
                String pageId = params.optString("pageId");
                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page == null) {
                    callback.error(404, "Page ID '" + pageId + "' not found");
                    return;
                }

                mainHandler.post(() -> {
                    String url = page.webView != null ? page.webView.getUrl() : "";
                    CookieManager cm = webViewPool.getCookieManager(page.profileName);

                    int count = 0;
                    JSONArray cookiesArr = params.optJSONArray("cookies");
                    if (cookiesArr == null) cookiesArr = params.optJSONArray("items");

                    if (cookiesArr != null) {
                        List<CookieHelper.CookieItem> list = new java.util.ArrayList<>();
                        for (int i = 0; i < cookiesArr.length(); i++) {
                            JSONObject obj = cookiesArr.optJSONObject(i);
                            if (obj != null) {
                                CookieHelper.CookieItem item = CookieHelper.CookieItem.fromJson(obj);
                                if (item != null) list.add(item);
                            }
                        }
                        count = CookieHelper.importCookies(cm, url, list);
                    } else {
                        String cookies = params.optString("cookies", params.optString("input", ""));
                        count = CookieHelper.importCookies(cm, url, cookies);
                    }

                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", count > 0);
                        res.put("count", count);
                        res.put("profile", page.profileName);
                        res.put("pageId", page.pageId);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, e.getMessage());
                    }
                });
                break;
            }

            case "getLocalStorage": {
                String pageId = params.optString("pageId");
                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page == null || page.webView == null) {
                    callback.error(404, "Page ID '" + pageId + "' not found");
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

                    page.webView.evaluateJavascript(script, rawVal -> {
                        try {
                            String unquoted = rawVal != null ? parseJsJsonString(rawVal) : "{}";
                            JSONObject evalRes = new JSONObject(unquoted);
                            if (evalRes.optBoolean("success", false)) {
                                JSONObject data = evalRes.optJSONObject("data");
                                if (data == null) data = new JSONObject();
                                JSONObject res = new JSONObject();
                                res.put("data", data);
                                res.put("count", data.length());
                                res.put("pageId", page.pageId);
                                res.put("url", page.webView.getUrl());
                                callback.success(res);
                            } else {
                                callback.error(500, evalRes.optString("error", "Failed to get localStorage"));
                            }
                        } catch (Exception e) {
                            callback.error(500, e.getMessage());
                        }
                    });
                });
                break;
            }

            case "setLocalStorage": {
                String pageId = params.optString("pageId");
                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page == null || page.webView == null) {
                    callback.error(404, "Page ID '" + pageId + "' not found");
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

                    page.webView.evaluateJavascript(script, rawVal -> {
                        try {
                            String unquoted = rawVal != null ? parseJsJsonString(rawVal) : "{}";
                            JSONObject evalRes = new JSONObject(unquoted);
                            if (evalRes.optBoolean("success", false)) {
                                JSONObject res = new JSONObject();
                                res.put("success", true);
                                res.put("count", evalRes.optInt("count", 0));
                                res.put("pageId", page.pageId);
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
                String pageId = params.optString("pageId");
                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page == null || page.webView == null) {
                    callback.error(404, "Page ID '" + pageId + "' not found");
                    return;
                }

                mainHandler.post(() -> {
                    page.webView.evaluateJavascript("localStorage.clear(); 'true';", rawVal -> {
                        JSONObject res = new JSONObject();
                        try {
                            res.put("success", true);
                            res.put("pageId", page.pageId);
                            callback.success(res);
                        } catch (Exception ignored) {}
                    });
                });
                break;
            }

            case "dumpStorage": {
                String pageId = params.optString("pageId");
                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page == null || page.webView == null) {
                    callback.error(404, "Page ID '" + pageId + "' not found");
                    return;
                }

                mainHandler.post(() -> {
                    String pageUrl = page.webView.getUrl();
                    CookieManager cm = webViewPool.getCookieManager(page.profileName);

                    List<CookieHelper.CookieItem> cookieList = CookieHelper.readCookiesWithDetails(
                            webViewPool.getContext(),
                            cm,
                            page.profileName,
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

                    page.webView.evaluateJavascript(script, rawVal -> {
                        try {
                            String unquoted = rawVal != null ? parseJsJsonString(rawVal) : "{}";
                            JSONObject storageObj = new JSONObject(unquoted);
                            JSONObject result = new JSONObject();
                            result.put("pageId", page.pageId);
                            result.put("profile", page.profileName);
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


            case "clearData": {
                String pageId = params.optString("pageId");
                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page == null) {
                    callback.error(404, "Page ID '" + pageId + "' not found");
                    return;
                }

                mainHandler.post(() -> {
                    try {
                        CookieManager cm = webViewPool.getCookieManager(page.profileName);
                        cm.removeAllCookies(null);
                        cm.flush();
                        WebStorage ws = webViewPool.getWebStorage(page.profileName);
                        ws.deleteAllData();
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("profile", page.profileName);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, e.getMessage());
                    }
                });
                break;
            }

            case "extractQrCode": {
                String pageId = params.optString("pageId");
                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page == null) {
                    callback.error(404, "Page ID '" + pageId + "' not found");
                    return;
                }

                mainHandler.post(() -> {
                    String js = "(function() {" +
                            "  var img = document.querySelector('img[src*=\"qr\"], img.qrcode, #qrcode img, .qrcode img');" +
                            "  if (img) {" +
                            "    if (img.src.startsWith('data:image')) return img.src;" +
                            "    var canvas = document.createElement('canvas');" +
                            "    canvas.width = img.naturalWidth || img.width || 200;" +
                            "    canvas.height = img.naturalHeight || img.height || 200;" +
                            "    var ctx = canvas.getContext('2d');" +
                            "    ctx.drawImage(img, 0, 0);" +
                            "    return canvas.toDataURL('image/png');" +
                            "  }" +
                            "  var canvas = document.querySelector('canvas.qrcode, #qrcode canvas, canvas');" +
                            "  if (canvas) return canvas.toDataURL('image/png');" +
                            "  return '';" +
                            "})();";

                    page.webView.evaluateJavascript(js, value -> {
                        try {
                            String base64 = value != null ? value.replace("\"", "") : "";
                            JSONObject res = new JSONObject();
                            res.put("base64", base64);
                            callback.success(res);
                        } catch (Exception e) {
                            callback.error(500, e.getMessage());
                        }
                    });
                });
                break;
            }

            case "evaluate": {
                String pageId = params.optString("pageId");
                String script = params.optString("script");
                WebViewPool.ManagedPage page = webViewPool.getPage(pageId);
                if (page == null) {
                    callback.error(404, "Page ID '" + pageId + "' not found");
                    return;
                }

                mainHandler.post(() -> {
                    page.webView.evaluateJavascript(script, value -> {
                        try {
                            JSONObject res = new JSONObject();
                            res.put("result", value);
                            callback.success(res);
                        } catch (Exception e) {
                            callback.error(500, e.getMessage());
                        }
                    });
                });
                break;
            }

            case "getHardware": {
                String pageId = params.optString("pageId");
                WebViewPool.ManagedPage page = !TextUtils.isEmpty(pageId) ? webViewPool.getPage(pageId) : webViewPool.getActiveForegroundPage();
                if (page == null) {
                    callback.error(404, "Page not found");
                    return;
                }
                JSONObject res = new JSONObject();
                res.put("pageId", page.pageId);
                res.put("profile", page.profileName);
                res.put("hardware", page.hardwareConfig != null ? page.hardwareConfig.toJson() : new JSONObject());
                callback.success(res);
                break;
            }

            case "setHardware": {
                String pageId = params.optString("pageId");
                WebViewPool.ManagedPage page = !TextUtils.isEmpty(pageId) ? webViewPool.getPage(pageId) : webViewPool.getActiveForegroundPage();
                if (page == null) {
                    callback.error(404, "Page not found");
                    return;
                }
                String preset = params.optString("preset", params.optString("hardwarePreset", ""));
                JSONObject hwJson = params.optJSONObject("hardware");
                if (hwJson == null) hwJson = params;
                HardwareConfig newConfig = HardwareConfig.fromPresetOrParams(preset, hwJson);

                mainHandler.post(() -> {
                    webViewPool.applyHardwareConfig(page.webView, newConfig);
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("pageId", page.pageId);
                        res.put("hardware", newConfig.toJson());
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, e.getMessage());
                    }
                });
                break;
            }

            case "startDouyinQrLogin": {
                mainHandler.post(() -> {
                    if (mainActivity != null) {
                        com.example.cookiebrowser.core.DouyinQrLoginManager.getInstance().start(mainActivity, webViewPool);
                        try {
                            JSONObject res = new JSONObject();
                            res.put("success", true);
                            res.put("message", "Douyin QR login monitoring dialog launched");
                            callback.success(res);
                        } catch (Exception ignored) {}
                    } else {
                        callback.error(500, "MainActivity context is not available");
                    }
                });
                break;
            }

            case "close": {
                String pageId = params.optString("pageId");
                mainHandler.post(() -> {
                    webViewPool.destroyPage(pageId);
                    callback.success(new JSONObject());
                });
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'page'");
                break;
        }
    }

    private static String parseJsJsonString(String rawVal) {
        if (rawVal == null) return "{}";
        String s = rawVal.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            try {
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

