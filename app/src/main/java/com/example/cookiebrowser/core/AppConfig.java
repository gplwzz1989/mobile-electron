package com.example.cookiebrowser.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Mobile Electron 跨端开发框架全局应用配置模型
 */
public class AppConfig {

    // 默认加载的启动网站（支持本地 assets 路径或远程 HTTP/HTTPS 地址）
    private String defaultUrl = "http://127.0.0.1:5173";
    private String appName = "Mobile Electron";
    private String appId = "com.example.cookiebrowser";
    private String version = "2.0.0";

    // 窗口与显示配置
    private boolean fullscreen = false;
    private boolean immersiveStatusBar = true;
    private String statusBarColor = "#FFFFFF";
    private boolean statusBarDarkIcons = true;
    private boolean showNativeDebugToolbar = false; // 默认关闭原生工具条，实现100%全Web界面驱动

    // 独立 Profile 与硬件伪装预设
    private String defaultProfile = "default";
    private String hardwarePreset = "flagship";

    // 安全与白名单配置
    private final List<String> whitelist = new ArrayList<>();
    private boolean allowInsecureContent = true;

    public static AppConfig fromJson(JSONObject json) {
        AppConfig config = new AppConfig();
        if (json == null) return config;

        // 应用基本信息
        JSONObject appObj = json.optJSONObject("app");
        if (appObj != null) {
            config.appName = appObj.optString("name", config.appName);
            config.appId = appObj.optString("appId", config.appId);
            config.version = appObj.optString("version", config.version);
        }

        // 窗口配置
        JSONObject winObj = json.optJSONObject("window");
        if (winObj != null) {
            config.defaultUrl = winObj.optString("defaultUrl", config.defaultUrl);
            config.fullscreen = winObj.optBoolean("fullscreen", config.fullscreen);
            config.immersiveStatusBar = winObj.optBoolean("immersiveStatusBar", config.immersiveStatusBar);
            config.statusBarColor = winObj.optString("statusBarColor", config.statusBarColor);
            config.statusBarDarkIcons = winObj.optBoolean("statusBarDarkIcons", config.statusBarDarkIcons);
            config.showNativeDebugToolbar = winObj.optBoolean("showNativeDebugToolbar", config.showNativeDebugToolbar);
        } else if (json.has("defaultUrl")) {
            config.defaultUrl = json.optString("defaultUrl", config.defaultUrl);
        }

        // 运行时 Profile 配置
        JSONObject profileObj = json.optJSONObject("profile");
        if (profileObj != null) {
            config.defaultProfile = profileObj.optString("defaultProfile", config.defaultProfile);
            config.hardwarePreset = profileObj.optString("hardwarePreset", config.hardwarePreset);
        }

        // 安全与白名单
        JSONObject secObj = json.optJSONObject("security");
        if (secObj != null) {
            config.allowInsecureContent = secObj.optBoolean("allowInsecureContent", config.allowInsecureContent);
            JSONArray wlArray = secObj.optJSONArray("whitelist");
            if (wlArray != null) {
                for (int i = 0; i < wlArray.length(); i++) {
                    String pattern = wlArray.optString(i);
                    if (pattern != null && !pattern.trim().isEmpty()) {
                        config.whitelist.add(pattern.trim());
                    }
                }
            }
        }

        return config;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            JSONObject appObj = new JSONObject();
            appObj.put("name", appName);
            appObj.put("appId", appId);
            appObj.put("version", version);
            json.put("app", appObj);

            JSONObject winObj = new JSONObject();
            winObj.put("defaultUrl", defaultUrl);
            winObj.put("fullscreen", fullscreen);
            winObj.put("immersiveStatusBar", immersiveStatusBar);
            winObj.put("statusBarColor", statusBarColor);
            winObj.put("statusBarDarkIcons", statusBarDarkIcons);
            winObj.put("showNativeDebugToolbar", showNativeDebugToolbar);
            json.put("window", winObj);

            JSONObject profileObj = new JSONObject();
            profileObj.put("defaultProfile", defaultProfile);
            profileObj.put("hardwarePreset", hardwarePreset);
            json.put("profile", profileObj);

            JSONObject secObj = new JSONObject();
            secObj.put("allowInsecureContent", allowInsecureContent);
            JSONArray wlArray = new JSONArray();
            for (String w : whitelist) {
                wlArray.put(w);
            }
            secObj.put("whitelist", wlArray);
            json.put("security", secObj);
        } catch (Exception ignored) {}
        return json;
    }

    // Getters and Setters
    public String getDefaultUrl() { return defaultUrl; }
    public void setDefaultUrl(String defaultUrl) { this.defaultUrl = defaultUrl; }

    public String getAppName() { return appName; }
    public String getAppId() { return appId; }
    public String getVersion() { return version; }

    public boolean isFullscreen() { return fullscreen; }
    public void setFullscreen(boolean fullscreen) { this.fullscreen = fullscreen; }

    public boolean isImmersiveStatusBar() { return immersiveStatusBar; }
    public void setImmersiveStatusBar(boolean immersiveStatusBar) { this.immersiveStatusBar = immersiveStatusBar; }

    public String getStatusBarColor() { return statusBarColor; }
    public void setStatusBarColor(String statusBarColor) { this.statusBarColor = statusBarColor; }

    public boolean isStatusBarDarkIcons() { return statusBarDarkIcons; }
    public void setStatusBarDarkIcons(boolean statusBarDarkIcons) { this.statusBarDarkIcons = statusBarDarkIcons; }

    public boolean isShowNativeDebugToolbar() { return showNativeDebugToolbar; }
    public void setShowNativeDebugToolbar(boolean showNativeDebugToolbar) { this.showNativeDebugToolbar = showNativeDebugToolbar; }

    public String getDefaultProfile() { return defaultProfile; }
    public String getHardwarePreset() { return hardwarePreset; }

    public List<String> getWhitelist() { return whitelist; }
    public boolean isAllowInsecureContent() { return allowInsecureContent; }
}
