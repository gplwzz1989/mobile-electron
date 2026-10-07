package com.example.cookiebrowser.bridge;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 商业级 JSON-RPC 2.0 桥接调度引擎与权限网关
 */
public class JsBridgeEngine {

    private static final String TAG = "JsBridgeEngine";

    private final WeakReference<WebView> webViewRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    private final Map<String, IBridgePlugin> pluginMap = new ConcurrentHashMap<>();

    public JsBridgeEngine(WebView webView) {
        this.webViewRef = new WeakReference<>(webView);
    }

    public void registerPlugin(IBridgePlugin plugin) {
        if (plugin != null) {
            pluginMap.put(plugin.getModuleName().toLowerCase(), plugin);
        }
    }

    /**
     * JS 统一调用的 RPC 入口
     */
    @JavascriptInterface
    public void dispatch(String jsonString) {
        if (TextUtils.isEmpty(jsonString)) {
            Log.e(TAG, "Empty RPC dispatch payload");
            return;
        }

        threadPool.execute(() -> processRpcRequest(jsonString));
    }

    private void processRpcRequest(String jsonString) {
        String reqId = "";
        try {
            JSONObject req = new JSONObject(jsonString);
            reqId = req.optString("id", "");
            String module = req.optString("module", "").toLowerCase();
            String action = req.optString("action", "");
            JSONObject params = req.optJSONObject("params");
            if (params == null) {
                params = new JSONObject();
            }

            final String finalReqId = reqId;

            // 1. 获取当前调用方的真实 URL
            WebView webView = webViewRef.get();
            if (webView == null) {
                return;
            }

            final String currentUrl = getWebViewUrlSynchronously(webView);

            // 2. 查找对应插件
            IBridgePlugin plugin = pluginMap.get(module);
            if (plugin == null) {
                sendErrorResponse(finalReqId, 404, "Plugin module '" + module + "' not found");
                return;
            }

            // 3. 安全白名单与权限级别拦截
            int requiredTier = plugin.getRequiredTier(action);
            boolean authorized = DomainWhitelistManager.getInstance().checkPermission(currentUrl, requiredTier);

            if (!authorized) {
                Log.w(TAG, "Access Denied: URL [" + currentUrl + "] attempted to call [" + module + "." + action + "] (Tier " + requiredTier + ")");
                sendErrorResponse(finalReqId, 401, "Permission Denied: Domain not whitelisted for tier " + requiredTier);
                return;
            }

            // 4. 分发执行动作
            plugin.handleAction(currentUrl, action, params, new BridgeCallback() {
                @Override
                public void success(JSONObject data) {
                    sendSuccessResponse(finalReqId, data);
                }

                @Override
                public void error(int code, String message) {
                    sendErrorResponse(finalReqId, code, message);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "RPC Execution Error: " + e.getMessage(), e);
            sendErrorResponse(reqId, 500, "Native execution error: " + e.getMessage());
        }
    }

    private String getWebViewUrlSynchronously(WebView webView) {
        final String[] urlHolder = new String[1];
        if (Looper.myLooper() == Looper.getMainLooper()) {
            urlHolder[0] = webView.getUrl();
        } else {
            final Object lock = new Object();
            mainHandler.post(() -> {
                synchronized (lock) {
                    urlHolder[0] = webView.getUrl();
                    lock.notify();
                }
            });
            synchronized (lock) {
                try {
                    lock.wait(500);
                } catch (InterruptedException ignored) {}
            }
        }
        return urlHolder[0] != null ? urlHolder[0] : "";
    }

    private void sendSuccessResponse(String reqId, JSONObject data) {
        try {
            JSONObject resp = new JSONObject();
            resp.put("id", reqId);
            resp.put("code", 200);
            resp.put("data", data != null ? data : new JSONObject());
            resp.put("message", "success");

            invokeJsFunction("__onNativeRpcResponse", resp.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to build success response: " + e.getMessage());
        }
    }

    private void sendErrorResponse(String reqId, int code, String message) {
        try {
            JSONObject resp = new JSONObject();
            resp.put("id", reqId);
            resp.put("code", code);
            resp.put("data", new JSONObject());
            resp.put("message", message);

            invokeJsFunction("__onNativeRpcResponse", resp.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to build error response: " + e.getMessage());
        }
    }

    /**
     * 向前端广播主动事件
     */
    public void sendEvent(String eventName, JSONObject data) {
        try {
            JSONObject event = new JSONObject();
            event.put("event", eventName);
            event.put("data", data != null ? data : new JSONObject());

            invokeJsFunction("__onNativeRpcEvent", event.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to send event: " + e.getMessage());
        }
    }

    private void invokeJsFunction(String functionName, String jsonPayload) {
        mainHandler.post(() -> {
            WebView webView = webViewRef.get();
            if (webView != null) {
                // 安全转义 JSON 参数
                String js = "window." + functionName + " && window." + functionName + "(" + jsonPayload + ");";
                webView.evaluateJavascript(js, null);
            }
        });
    }

    public void destroy() {
        threadPool.shutdown();
        pluginMap.clear();
    }
}
