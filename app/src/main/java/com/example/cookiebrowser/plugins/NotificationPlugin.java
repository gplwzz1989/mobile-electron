package com.example.cookiebrowser.plugins;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 原生本地通知模块插件 (electron.notification)
 * 支持发送本地通知、自定义标题内容、点击唤醒应用与通知取消
 */
public class NotificationPlugin implements IBridgePlugin {

    public static final String CHANNEL_ID = "mobile_electron_notifications";
    public static final String CHANNEL_NAME = "应用系统通知";

    private final Context context;
    private final NotificationManagerCompat notificationManager;
    private static final AtomicInteger NOTIFICATION_ID_COUNTER = new AtomicInteger(1000);

    public NotificationPlugin(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = NotificationManagerCompat.from(this.context);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Mobile Electron 容器派发的本地通知");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public String getModuleName() {
        return "notification";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_1_BUSINESS;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        if ("show".equalsIgnoreCase(action)) {
            String appName = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
            String title = params != null ? params.optString("title", appName) : appName;
            String body = params != null ? params.optString("body", params.optString("message", "")) : "";
            int notifId = params != null && params.has("id") ? params.optInt("id", 0) : NOTIFICATION_ID_COUNTER.incrementAndGet();
            if (notifId == 0) {
                notifId = NOTIFICATION_ID_COUNTER.incrementAndGet();
            }

            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (params != null && params.has("data")) {
                launchIntent.putExtra("notification_data", params.optString("data"));
            }
            launchIntent.putExtra("notification_id", notifId);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(context, notifId, launchIntent, flags);

            int smallIcon = context.getApplicationInfo().icon;
            if (smallIcon == 0) {
                smallIcon = android.R.drawable.ic_dialog_info;
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(smallIcon)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            if (params != null && params.has("badge")) {
                builder.setNumber(params.optInt("badge", 0));
            }

            notificationManager.notify(notifId, builder.build());

            JSONObject res = new JSONObject();
            res.put("success", true);
            res.put("id", notifId);
            callback.success(res);
            return;
        }

        if ("cancel".equalsIgnoreCase(action)) {
            int notifId = params != null ? params.optInt("id", 0) : 0;
            if (notifId != 0) {
                notificationManager.cancel(notifId);
            }
            JSONObject res = new JSONObject();
            res.put("success", true);
            callback.success(res);
            return;
        }

        if ("cancelAll".equalsIgnoreCase(action)) {
            notificationManager.cancelAll();
            JSONObject res = new JSONObject();
            res.put("success", true);
            callback.success(res);
            return;
        }

        if ("areNotificationsEnabled".equalsIgnoreCase(action)) {
            boolean enabled = notificationManager.areNotificationsEnabled();
            JSONObject res = new JSONObject();
            res.put("enabled", enabled);
            callback.success(res);
            return;
        }

        callback.error(404, "未知的通知动作: " + action);
    }
}
