package com.example.cookiebrowser.plugins;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

/**
 * 原生生物识别认证插件 (electron.biometrics)
 * 支持指纹、人脸识别 (Face Unlock) 与系统锁屏凭据认证
 */
public class BiometricsPlugin implements IBridgePlugin {

    private final Activity activity;

    public BiometricsPlugin(Activity activity) {
        this.activity = activity;
    }

    @Override
    public String getModuleName() {
        return "biometrics";
    }

    @Override
    public int getRequiredTier(String action) {
        if ("isAvailable".equalsIgnoreCase(action)) {
            return DomainWhitelistManager.TIER_0_PUBLIC;
        }
        return DomainWhitelistManager.TIER_1_BUSINESS;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        if ("isAvailable".equalsIgnoreCase(action)) {
            checkAvailability(callback);
            return;
        }

        if ("authenticate".equalsIgnoreCase(action)) {
            authenticate(params, callback);
            return;
        }

        callback.error(404, "BiometricsPlugin: 未知动作 " + action);
    }

    private void checkAvailability(BridgeCallback callback) {
        JSONObject res = new JSONObject();
        try {
            PackageManager pm = activity.getPackageManager();
            boolean hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
            boolean hasFace = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                hasFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE);
            }

            KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
            boolean isSecure = km != null && km.isKeyguardSecure();

            boolean supported = (hasFingerprint || hasFace) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
            boolean available = supported && isSecure;

            res.put("available", available);
            res.put("supported", supported);
            res.put("deviceSecure", isSecure);
            res.put("hasFingerprint", hasFingerprint);
            res.put("hasFace", hasFace);
            res.put("biometricType", hasFace ? "face" : (hasFingerprint ? "fingerprint" : "none"));

            callback.success(res);
        } catch (Exception e) {
            callback.error(500, "检查生物识别状态失败: " + e.getMessage());
        }
    }

    private void authenticate(JSONObject params, BridgeCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            callback.error(501, "当前系统版本低于 Android 9.0，不支持 BiometricPrompt");
            return;
        }

        String title = params != null ? params.optString("title", "生物特征身份验证") : "生物特征身份验证";
        String subtitle = params != null ? params.optString("subtitle", "") : "";
        String description = params != null ? params.optString("description", "请验证指纹或面容以确认身份") : "请验证指纹或面容以确认身份";
        String cancelText = params != null ? params.optString("cancelText", "取消") : "取消";

        activity.runOnUiThread(() -> {
            try {
                CancellationSignal cancellationSignal = new CancellationSignal();

                BiometricPrompt.Builder builder = new BiometricPrompt.Builder(activity)
                        .setTitle(title)
                        .setDescription(description)
                        .setNegativeButton(cancelText, activity.getMainExecutor(), (dialog, which) -> {
                            cancellationSignal.cancel();
                            callback.error(1001, "用户取消了生物识别");
                        });

                if (subtitle != null && !subtitle.trim().isEmpty()) {
                    builder.setSubtitle(subtitle);
                }

                BiometricPrompt prompt = builder.build();
                prompt.authenticate(cancellationSignal, activity.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        try {
                            JSONObject res = new JSONObject();
                            res.put("success", true);
                            res.put("authenticated", true);
                            callback.success(res);
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // 单次识别失败，系统弹窗仍保留，直到用户取消或重试成功
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        callback.error(errorCode, "识别失败: " + errString);
                    }
                });

            } catch (Exception e) {
                callback.error(500, "启动生物识别异常: " + e.getMessage());
            }
        });
    }
}
