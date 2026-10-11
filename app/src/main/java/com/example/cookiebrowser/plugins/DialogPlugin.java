package com.example.cookiebrowser.plugins;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * 原生对话框与交互插件 (Native Dialog & Interaction Plugin)
 * 支持系统级 Alert、Confirm、Prompt、ActionSheet 底部操作列表与系统级 Share 分享面板
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
        if (activity == null || activity.isFinishing()) {
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

            case "actionSheet": {
                String title = params.optString("title", "请选择");
                JSONArray optionsArr = params.optJSONArray("options");
                if (optionsArr == null) {
                    optionsArr = new JSONArray();
                }
                final String[] items = new String[optionsArr.length()];
                for (int i = 0; i < optionsArr.length(); i++) {
                    items[i] = optionsArr.optString(i);
                }

                String cancelText = params.optString("cancelButtonText", "取消");

                mainHandler.post(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    if (!TextUtils.isEmpty(title)) {
                        builder.setTitle(title);
                    }
                    builder.setItems(items, (d, which) -> {
                        try {
                            JSONObject res = new JSONObject();
                            res.put("index", which);
                            res.put("value", items[which]);
                            res.put("cancelled", false);
                            callback.success(res);
                        } catch (Exception ignored) {}
                    });
                    builder.setNegativeButton(cancelText, (d, which) -> {
                        try {
                            JSONObject res = new JSONObject();
                            res.put("index", -1);
                            res.put("cancelled", true);
                            callback.success(res);
                        } catch (Exception ignored) {}
                    });
                    builder.show();
                });
                break;
            }

            case "share": {
                String title = params.optString("title", "分享");
                String text = params.optString("text", "");
                String url = params.optString("url", "");

                mainHandler.post(() -> {
                    try {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");

                        StringBuilder shareBody = new StringBuilder();
                        if (!TextUtils.isEmpty(text)) {
                            shareBody.append(text);
                        }
                        if (!TextUtils.isEmpty(url)) {
                            if (shareBody.length() > 0) shareBody.append(" ");
                            shareBody.append(url);
                        }

                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
                        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody.toString());

                        activity.startActivity(Intent.createChooser(shareIntent, title));

                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        callback.success(res);
                    } catch (Exception e) {
                        callback.error(500, "Failed to launch system share: " + e.getMessage());
                    }
                });
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'dialog'");
                break;
        }
    }
}
