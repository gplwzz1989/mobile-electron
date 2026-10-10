package com.example.cookiebrowser.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工业级全局应用配置与云控中心管理器 (AppConfigManager)
 * 支持：
 * 1. 启动时沙箱云控配置优先加载（无缓存则优雅回退至内置 assets/app-config.json）
 * 2. 运行时动态云端拉取、格式校验与原子持久化覆写
 * 3. 动态安全白名单同步 (DomainWhitelistManager 热生效)
 * 4. 出厂默认配置一键重置与回滚
 */
public class AppConfigManager {

    private static final String TAG = "AppConfigManager";
    private static final String CONFIG_ASSET_FILE = "app-config.json";
    private static final String REMOTE_CONFIG_FILE = "remote_app_config.json";

    private static volatile AppConfigManager instance;
    private AppConfig currentConfig;
    private String configSource = "builtin_asset"; // "builtin_asset" | "remote_cached"
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface RemoteConfigCallback {
        void onSuccess(AppConfig config, String source);
        void onError(int code, String message);
    }

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
        Context appContext = context.getApplicationContext();
        File remoteConfigFile = new File(appContext.getFilesDir(), REMOTE_CONFIG_FILE);

        // 1. 优先尝试从私有沙箱加载最新云控配置
        if (remoteConfigFile.exists() && remoteConfigFile.length() > 0) {
            try {
                String remoteJson = readFileString(remoteConfigFile);
                if (remoteJson != null && !remoteJson.trim().isEmpty()) {
                    JSONObject json = new JSONObject(remoteJson);
                    this.currentConfig = AppConfig.fromJson(json);
                    this.configSource = "remote_cached";
                    Log.i(TAG, "Loaded remote cached app config from sandbox. DefaultUrl: " + currentConfig.getDefaultUrl());
                    applySecurityPolicies();
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse remote cached config, falling back to assets: " + e.getMessage());
            }
        }

        // 2. 兜底加载内置 Assets 资源配置
        try {
            String jsonStr = loadAssetString(appContext, CONFIG_ASSET_FILE);
            if (jsonStr != null && !jsonStr.trim().isEmpty()) {
                JSONObject json = new JSONObject(jsonStr);
                this.currentConfig = AppConfig.fromJson(json);
                this.configSource = "builtin_asset";
                Log.i(TAG, "Loaded builtin asset app configuration. DefaultUrl: " + currentConfig.getDefaultUrl());
            } else {
                Log.w(TAG, "Config asset not found, using default fallback configuration.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse " + CONFIG_ASSET_FILE + ", fallback to defaults: " + e.getMessage());
        }

        applySecurityPolicies();
    }

    /**
     * 运行时向云端配置服务器拉取最新配置并持久化生效
     */
    public void fetchRemoteConfig(Context context, String urlString, RemoteConfigCallback callback) {
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code != 200) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError(code, "HTTP Error: " + code);
                    });
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();

                String jsonContent = sb.toString();
                JSONObject json = new JSONObject(jsonContent);
                AppConfig newConfig = AppConfig.fromJson(json);

                // 持久化保存至沙箱
                saveConfigToSandbox(appContext, jsonContent);

                synchronized (this) {
                    this.currentConfig = newConfig;
                    this.configSource = "remote_cached";
                    applySecurityPolicies();
                }

                Log.i(TAG, "Successfully fetched and applied remote app config from " + urlString);
                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(newConfig, "remote_cached");
                });

            } catch (Exception e) {
                Log.e(TAG, "fetchRemoteConfig error: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(500, "Fetch error: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 重置恢复为内置出厂配置，清理沙箱缓存
     */
    public synchronized boolean resetToDefault(Context context) {
        Context appContext = context.getApplicationContext();
        File remoteFile = new File(appContext.getFilesDir(), REMOTE_CONFIG_FILE);
        if (remoteFile.exists()) {
            remoteFile.delete();
        }

        try {
            String jsonStr = loadAssetString(appContext, CONFIG_ASSET_FILE);
            if (jsonStr != null && !jsonStr.trim().isEmpty()) {
                this.currentConfig = AppConfig.fromJson(new JSONObject(jsonStr));
            } else {
                this.currentConfig = new AppConfig();
            }
            this.configSource = "builtin_asset";
            applySecurityPolicies();
            Log.i(TAG, "App configuration reset to builtin factory default.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "resetToDefault error: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized void saveConfigToSandbox(Context context, String jsonContent) throws Exception {
        File file = new File(context.getFilesDir(), REMOTE_CONFIG_FILE);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(jsonContent.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }

    public synchronized String getConfigSource() {
        return configSource;
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

    private void applySecurityPolicies() {
        DomainWhitelistManager whitelistManager = DomainWhitelistManager.getInstance();
        if (currentConfig != null) {
            whitelistManager.setStrictHttps(!currentConfig.isAllowInsecureContent());
            if (currentConfig.getWhitelist() != null) {
                for (String domain : currentConfig.getWhitelist()) {
                    whitelistManager.addTier1Domain(domain);
                    whitelistManager.addTier2Domain(domain);
                }
            }
        }
    }

    private String readFileString(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String loadAssetString(Context context, String fileName) {
        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
