package com.example.cookiebrowser.core;

import android.content.Context;
import android.util.Log;

import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 全局应用配置管理器（读取 assets/app-config.json 并初始化框架环境）
 */
public class AppConfigManager {

    private static final String TAG = "AppConfigManager";
    private static final String CONFIG_ASSET_FILE = "app-config.json";

    private static volatile AppConfigManager instance;
    private AppConfig currentConfig;

    private AppConfigManager() {
        this.currentConfig = new AppConfig();
    }

    public static AppConfigManager getInstance() {
        if (instance == null) {
            synchronized (AppConfigManager.class) {
                if (instance == null) {
                    instance = new AppConfigManager();
                }
            }
        }
        return instance;
    }

    /**
     * 在 App 启动时调用，解析配置并注入安全沙箱策略
     */
    public synchronized void init(Context context) {
        try {
            String jsonStr = loadAssetString(context, CONFIG_ASSET_FILE);
            if (jsonStr != null && !jsonStr.trim().isEmpty()) {
                JSONObject json = new JSONObject(jsonStr);
                this.currentConfig = AppConfig.fromJson(json);
                Log.i(TAG, "Successfully loaded app configuration. DefaultUrl: " + currentConfig.getDefaultUrl());
            } else {
                Log.w(TAG, "Config asset not found, using default fallback configuration.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse " + CONFIG_ASSET_FILE + ", fallback to defaults: " + e.getMessage());
        }

        applySecurityPolicies();
    }

    private void applySecurityPolicies() {
        DomainWhitelistManager whitelistManager = DomainWhitelistManager.getInstance();
        if (currentConfig.getWhitelist() != null) {
            for (String domain : currentConfig.getWhitelist()) {
                whitelistManager.addTier1Domain(domain);
                whitelistManager.addTier2Domain(domain);
            }
        }
    }

    public synchronized AppConfig getConfig() {
        if (currentConfig == null) {
            currentConfig = new AppConfig();
        }
        return currentConfig;
    }

    public synchronized void updateConfig(AppConfig newConfig) {
        if (newConfig != null) {
            this.currentConfig = newConfig;
            applySecurityPolicies();
        }
    }

    private String loadAssetString(Context context, String fileName) {
        try {
            InputStream is = context.getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            is.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
