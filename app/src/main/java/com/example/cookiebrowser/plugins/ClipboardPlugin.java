package com.example.cookiebrowser.plugins;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

/**
 * 原生剪贴板模块插件 (electron.clipboard)
 * 支持文本读写、是否有文本检测以及清空剪贴板
 */
public class ClipboardPlugin implements IBridgePlugin {

    private final Context context;
    private final Handler mainHandler;

    public ClipboardPlugin(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public String getModuleName() {
        return "clipboard";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_0_PUBLIC;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        mainHandler.post(() -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null) {
                    callback.error(500, "剪贴板服务不可用");
                    return;
                }

                if ("readText".equalsIgnoreCase(action)) {
                    String text = "";
                    if (clipboard.hasPrimaryClip()) {
                        ClipData clip = clipboard.getPrimaryClip();
                        if (clip != null && clip.getItemCount() > 0) {
                            CharSequence seq = clip.getItemAt(0).coerceToText(context);
                            if (seq != null) {
                                text = seq.toString();
                            }
                        }
                    }
                    JSONObject res = new JSONObject();
                    res.put("text", text);
                    callback.success(res);
                    return;
                }

                if ("writeText".equalsIgnoreCase(action)) {
                    String text = params != null ? params.optString("text", "") : "";
                    ClipData clip = ClipData.newPlainText("text", text);
                    clipboard.setPrimaryClip(clip);
                    JSONObject res = new JSONObject();
                    res.put("success", true);
                    callback.success(res);
                    return;
                }

                if ("hasText".equalsIgnoreCase(action)) {
                    boolean hasText = false;
                    if (clipboard.hasPrimaryClip()) {
                        ClipData clip = clipboard.getPrimaryClip();
                        if (clip != null && clip.getItemCount() > 0) {
                            CharSequence seq = clip.getItemAt(0).getText();
                            hasText = (seq != null && seq.length() > 0);
                        }
                    }
                    JSONObject res = new JSONObject();
                    res.put("hasText", hasText);
                    callback.success(res);
                    return;
                }

                if ("clear".equalsIgnoreCase(action)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip();
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                    }
                    JSONObject res = new JSONObject();
                    res.put("success", true);
                    callback.success(res);
                    return;
                }

                callback.error(404, "未知的剪贴板动作: " + action);
            } catch (Exception e) {
                callback.error(500, "剪贴板操作失败: " + e.getMessage());
            }
        });
    }
}
