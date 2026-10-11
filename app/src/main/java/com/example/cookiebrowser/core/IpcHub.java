package com.example.cookiebrowser.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * 跨窗口全双工 IPC 消息调度总线 (Cross-Window IPC Hub)
 * 支持主窗口、后台 Headless 沙箱页面与应用内画中画悬浮窗之间的消息互发与广播
 */
public class IpcHub {

    private static final String TAG = "MobileElectron-IpcHub";
    private static volatile IpcHub instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, WeakReference<WebView>> registeredViews = new HashMap<>();
    private WeakReference<WebViewPool> webViewPoolRef;

    private IpcHub() {}

    public static IpcHub getInstance() {
        if (instance == null) {
            synchronized (IpcHub.class) {
                if (instance == null) {
                    instance = new IpcHub();
                }
            }
        }
        return instance;
    }

    public void setWebViewPool(WebViewPool pool) {
        this.webViewPoolRef = pool != null ? new WeakReference<>(pool) : null;
    }

    /**
     * 注册可接收 IPC 消息的窗口 / 页面视图
     */
    public synchronized void registerView(String id, WebView webView) {
        if (id != null && webView != null) {
            registeredViews.put(id, new WeakReference<>(webView));
        }
    }

    /**
     * 注销指定视图
     */
    public synchronized void unregisterView(String id) {
        if (id != null) {
            registeredViews.remove(id);
        }
    }

    /**
     * 向指定目标窗口定向投递 IPC 消息
     */
    public boolean sendTo(String senderId, String targetId, String channel, Object payload) {
        if (targetId == null || channel == null) return false;

        WebView targetView = getTargetWebView(targetId);
        if (targetView == null) {
            Log.w(TAG, "Target view not found for id: " + targetId);
            return false;
        }

        dispatchIpcEvent(targetView, senderId, targetId, channel, payload);
        return true;
    }

    /**
     * 向所有其他活跃窗口广播 IPC 消息
     */
    public int broadcast(String senderId, String channel, Object payload) {
        if (channel == null) return 0;

        Map<String, WebView> targets = collectAllActiveViews();
        int count = 0;
        for (Map.Entry<String, WebView> entry : targets.entrySet()) {
            String viewId = entry.getKey();
            WebView wv = entry.getValue();
            // 不回声给发送者自身（如果指定了 senderId）
            if (senderId != null && senderId.equals(viewId)) {
                continue;
            }
            if (wv != null) {
                dispatchIpcEvent(wv, senderId, viewId, channel, payload);
                count++;
            }
        }
        return count;
    }

    private synchronized WebView getTargetWebView(String targetId) {
        // 1. 先查直接注册的视图 (包括主页面 "main")
        WeakReference<WebView> ref = registeredViews.get(targetId);
        if (ref != null && ref.get() != null) {
            return ref.get();
        }

        // 2. 查悬浮窗
        WebView floatWv = FloatingWindowManager.getInstance().getFloatingWebView(targetId);
        if (floatWv != null) {
            return floatWv;
        }

        // 3. 查 WebViewPool
        WebViewPool pool = webViewPoolRef != null ? webViewPoolRef.get() : null;
        if (pool != null) {
            WebViewPool.ManagedPage page = pool.getPage(targetId);
            if (page != null && page.webView != null) {
                return page.webView;
            }
        }

        return null;
    }

    private synchronized Map<String, WebView> collectAllActiveViews() {
        Map<String, WebView> all = new HashMap<>();

        // 1. 注册视图
        for (Map.Entry<String, WeakReference<WebView>> entry : registeredViews.entrySet()) {
            WebView wv = entry.getValue().get();
            if (wv != null) {
                all.put(entry.getKey(), wv);
            }
        }

        // 2. 悬浮窗视图
        Map<String, WebView> floatings = FloatingWindowManager.getInstance().getAllFloatingWebViews();
        all.putAll(floatings);

        // 3. WebViewPool 视图
        WebViewPool pool = webViewPoolRef != null ? webViewPoolRef.get() : null;
        if (pool != null) {
            for (WebViewPool.ManagedPage mp : pool.getAllPages()) {
                if (mp != null && mp.webView != null) {
                    all.put(mp.pageId, mp.webView);
                }
            }
        }

        return all;
    }

    private void dispatchIpcEvent(WebView webView, String senderId, String targetId, String channel, Object payload) {
        mainHandler.post(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("channel", channel);
                data.put("senderId", senderId != null ? senderId : "system");
                data.put("targetId", targetId != null ? targetId : "all");
                data.put("payload", payload != null ? payload : JSONObject.NULL);
                data.put("timestamp", System.currentTimeMillis());

                JSONObject event = new JSONObject();
                event.put("event", "ipc.message");
                event.put("data", data);

                String js = "window.__onNativeRpcEvent && window.__onNativeRpcEvent(" + event.toString() + ");";
                webView.evaluateJavascript(js, null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to dispatch IPC event: " + e.getMessage());
            }
        });
    }
}
