package com.example.cookiebrowser.plugins;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;

import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.bridge.JsBridgeEngine;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * 原生软键盘控制与避让感知插件 (electron.keyboard)
 * 支持手动收起/弹出软键盘，以及动态监听软键盘弹起高度与显隐状态
 */
public class KeyboardPlugin implements IBridgePlugin {

    private final WeakReference<MainActivity> activityRef;
    private JsBridgeEngine bridgeEngine;
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;
    private boolean isKeyboardVisible = false;
    private int lastKeyboardHeight = 0;

    public KeyboardPlugin(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
        initLayoutObserver();
    }

    public void setBridgeEngine(JsBridgeEngine engine) {
        this.bridgeEngine = engine;
    }

    private void initLayoutObserver() {
        MainActivity activity = activityRef.get();
        if (activity == null) return;

        View rootView = activity.getWindow().getDecorView().getRootView();
        if (rootView == null) return;

        layoutListener = () -> {
            try {
                Rect rect = new Rect();
                rootView.getWindowVisibleDisplayFrame(rect);
                int screenHeight = rootView.getRootView().getHeight();
                int keypadHeight = screenHeight - rect.bottom;

                // 当高度差大于 0.15 屏幕高度时认为软键盘弹出
                boolean visible = keypadHeight > screenHeight * 0.15;
                if (visible != isKeyboardVisible || (visible && keypadHeight != lastKeyboardHeight)) {
                    isKeyboardVisible = visible;
                    lastKeyboardHeight = visible ? keypadHeight : 0;
                    if (bridgeEngine != null) {
                        JSONObject eventData = new JSONObject();
                        eventData.put("visible", isKeyboardVisible);
                        eventData.put("height", lastKeyboardHeight);
                        bridgeEngine.sendEvent("keyboard.onVisibilityChange", eventData);
                    }
                }
            } catch (Exception ignored) {}
        };

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    @Override
    public String getModuleName() {
        return "keyboard";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_0_PUBLIC;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        MainActivity activity = activityRef.get();
        if (activity == null || activity.isFinishing()) {
            callback.error(500, "Activity context is released");
            return;
        }

        switch (action) {
            case "hide": {
                activity.runOnUiThread(() -> {
                    try {
                        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                        View currentFocus = activity.getCurrentFocus();
                        if (imm != null && currentFocus != null) {
                            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                        } else if (imm != null) {
                            imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
                        }
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to hide keyboard: " + e.getMessage());
                    }
                });
                break;
            }

            case "show": {
                activity.runOnUiThread(() -> {
                    try {
                        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                        View currentFocus = activity.getCurrentFocus();
                        if (imm != null && currentFocus != null) {
                            imm.showSoftInput(currentFocus, InputMethodManager.SHOW_IMPLICIT);
                        } else if (imm != null) {
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                        }
                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to show keyboard: " + e.getMessage());
                    }
                });
                break;
            }

            case "isVisible": {
                JSONObject res = new JSONObject();
                res.put("visible", isKeyboardVisible);
                res.put("height", lastKeyboardHeight);
                callback.success(res);
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'keyboard'");
                break;
        }
    }
}
