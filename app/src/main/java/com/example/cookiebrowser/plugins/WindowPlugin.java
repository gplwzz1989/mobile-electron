package com.example.cookiebrowser.plugins;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.core.WebViewPool;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * 原生窗口控制、沉浸式状态栏与导航能力插件
 */
public class WindowPlugin implements IBridgePlugin {

    private final WeakReference<MainActivity> activityRef;
    private final WebViewPool webViewPool;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WindowPlugin(MainActivity activity, WebViewPool webViewPool) {
        this.activityRef = new WeakReference<>(activity);
        this.webViewPool = webViewPool;
    }

    @Override
    public String getModuleName() {
        return "window";
    }

    @Override
    public int getRequiredTier(String action) {
        if ("reload".equals(action) || "goBack".equals(action) || "canGoBack".equals(action)) {
            return DomainWhitelistManager.TIER_0_PUBLIC;
        }
        return DomainWhitelistManager.TIER_1_BUSINESS;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        MainActivity activity = activityRef.get();
        if (activity == null) {
            callback.error(500, "Activity context is released");
            return;
        }

        switch (action) {
            case "setStatusBar": {
                String colorHex = params.optString("color", "#FFFFFF");
                boolean darkIcons = params.optBoolean("darkIcons", true);
                boolean immersive = params.optBoolean("immersive", false);

                mainHandler.post(() -> {
                    try {
                        activity.applyStatusBarSettings(colorHex, darkIcons, immersive);
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to set status bar: " + e.getMessage());
                    }
                });
                break;
            }

            case "setFullscreen": {
                boolean fullscreen = params.optBoolean("fullscreen", true);
                mainHandler.post(() -> {
                    try {
                        activity.setFullscreenMode(fullscreen);
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("fullscreen", fullscreen);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to toggle fullscreen: " + e.getMessage());
                    }
                });
                break;
            }

            case "reload": {
                mainHandler.post(() -> {
                    WebView active = getActiveWebView();
                    if (active != null) active.reload();
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "goBack": {
                mainHandler.post(() -> {
                    WebView active = getActiveWebView();
                    boolean can = active != null && active.canGoBack();
                    if (can) active.goBack();
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", can);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "canGoBack": {
                mainHandler.post(() -> {
                    WebView active = getActiveWebView();
                    boolean can = active != null && active.canGoBack();
                    try {
                        JSONObject res = new JSONObject();
                        res.put("canGoBack", can);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "loadUrl": {
                String url = params.optString("url", "");
                if (url.isEmpty()) {
                    callback.error(400, "Missing url parameter");
                    return;
                }
                mainHandler.post(() -> {
                    activity.loadUrl(url);
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("url", url);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            case "setDebugToolbarVisible": {
                boolean visible = params.optBoolean("visible", true);
                mainHandler.post(() -> {
                    activity.setDebugToolbarVisible(visible);
                    try {
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("visible", visible);
                        callback.success(res);
                    } catch (Exception ignored) {}
                });
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'window'");
                break;
        }
    }

    private WebView getActiveWebView() {
        if (webViewPool == null) return null;
        WebViewPool.ManagedPage activePage = webViewPool.getActiveForegroundPage();
        return activePage != null ? activePage.webView : null;
    }
}
