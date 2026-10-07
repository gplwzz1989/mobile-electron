package com.example.cookiebrowser.plugins;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

/**
 * 原生设备交互与界面提示插件
 */
public class DevicePlugin implements IBridgePlugin {

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DevicePlugin(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String getModuleName() {
        return "device";
    }

    @Override
    public int getRequiredTier(String action) {
        if ("toast".equals(action)) {
            return DomainWhitelistManager.TIER_0_PUBLIC;
        }
        return DomainWhitelistManager.TIER_1_BUSINESS;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        switch (action) {
            case "toast": {
                String message = params.optString("message", "");
                mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
                callback.success(new JSONObject());
                break;
            }

            case "setClipboard": {
                String text = params.optString("text", "");
                mainHandler.post(() -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        ClipData clip = ClipData.newPlainText("MobileElectron", text);
                        cm.setPrimaryClip(clip);
                    }
                });
                JSONObject res = new JSONObject();
                res.put("success", true);
                callback.success(res);
                break;
            }

            case "getClipboard": {
                mainHandler.post(() -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    String text = "";
                    if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                        CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
                        if (cs != null) text = cs.toString();
                    }
                    try {
                        JSONObject res = new JSONObject();
                        res.put("text", text);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, e.getMessage());
                    }
                });
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'device'");
                break;
        }
    }
}
