package com.example.cookiebrowser.plugins;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * 原生对话框与交互插件 (Native Dialog & Interaction Plugin)
 */
public class DialogPlugin implements IBridgePlugin {

    private final WeakReference<MainActivity> activityRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DialogPlugin(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    @Override
    public String getModuleName() {
        return "dialog";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_0_PUBLIC;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        MainActivity activity = activityRef.get();
        if (activity == null) {
            callback.error(500, "Activity released");
            return;
        }

        switch (action) {
            case "alert": {
                String title = params.optString("title", "提示");
                String message = params.optString("message", "");
                String buttonText = params.optString("buttonText", "确定");

                mainHandler.post(() -> {
                    new AlertDialog.Builder(activity)
                            .setTitle(title)
                            .setMessage(message)
                            .setPositiveButton(buttonText, (d, w) -> {
                                try {
                                    JSONObject res = new JSONObject();
                                    res.put("confirmed", true);
                                    callback.success(res);
                                } catch (Exception ignored) {}
                            })
                            .setCancelable(false)
                            .show();
                });
                break;
            }

            case "confirm": {
                String title = params.optString("title", "请确认");
                String message = params.optString("message", "");
                String confirmText = params.optString("confirmText", "确认");
                String cancelText = params.optString("cancelText", "取消");

                mainHandler.post(() -> {
                    new AlertDialog.Builder(activity)
                            .setTitle(title)
                            .setMessage(message)
                            .setPositiveButton(confirmText, (d, w) -> {
                                try {
                                    JSONObject res = new JSONObject();
                                    res.put("confirmed", true);
                                    callback.success(res);
                                } catch (Exception ignored) {}
                            })
                            .setNegativeButton(cancelText, (d, w) -> {
                                try {
                                    JSONObject res = new JSONObject();
                                    res.put("confirmed", false);
                                    callback.success(res);
                                } catch (Exception ignored) {}
                            })
                            .setCancelable(false)
                            .show();
                });
                break;
            }

            case "prompt": {
                String title = params.optString("title", "请输入");
                String message = params.optString("message", "");
                String defaultVal = params.optString("defaultValue", "");
                String placeholder = params.optString("placeholder", "");
                String confirmText = params.optString("confirmText", "确定");
                String cancelText = params.optString("cancelText", "取消");

                mainHandler.post(() -> {
                    final EditText input = new EditText(activity);
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    input.setText(defaultVal);
                    input.setHint(placeholder);

                    new AlertDialog.Builder(activity)
                            .setTitle(title)
                            .setMessage(message)
                            .setView(input)
                            .setPositiveButton(confirmText, (d, w) -> {
                                try {
                                    JSONObject res = new JSONObject();
                                    res.put("confirmed", true);
                                    res.put("value", input.getText().toString());
                                    callback.success(res);
                                } catch (Exception ignored) {}
                            })
                            .setNegativeButton(cancelText, (d, w) -> {
                                try {
                                    JSONObject res = new JSONObject();
                                    res.put("confirmed", false);
                                    res.put("value", JSONObject.NULL);
                                    callback.success(res);
                                } catch (Exception ignored) {}
                            })
                            .setCancelable(false)
                            .show();
                });
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'dialog'");
                break;
        }
    }
}
