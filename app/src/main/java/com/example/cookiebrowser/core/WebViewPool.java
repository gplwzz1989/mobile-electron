package com.example.cookiebrowser.core;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 商业级多 WebView 实例池、独立 Profile 数据隔离、硬件参数指纹注入与前后台动态调度管理器
 */
public class WebViewPool {

    private static final String TAG = "WebViewPool";
    private static final int MAX_HEADLESS_INSTANCES = 8;

    public interface OnActivePageChangedListener {
        void onPageChanged(ManagedPage newActivePage);
    }

    public interface PageConfigurator {
        void configure(WebView webView, String pageId, String profileName);
    }

    public static class ManagedPage {
        public final String pageId;
        public final WebView webView;
        public boolean isHeadless;
        public boolean isForeground;
        public final String profileName;
        public final HardwareConfig hardwareConfig;
        public final long createdAt;

        public ManagedPage(String pageId, WebView webView, boolean isHeadless, boolean isForeground, String profileName, HardwareConfig hardwareConfig) {
            this.pageId = pageId;
            this.webView = webView;
            this.isHeadless = isHeadless;
            this.isForeground = isForeground;
            this.profileName = (profileName == null || profileName.trim().isEmpty()) ? "default" : profileName.trim();
            this.hardwareConfig = hardwareConfig != null ? hardwareConfig : new HardwareConfig();
            this.createdAt = System.currentTimeMillis();
        }

        public ManagedPage(String pageId, WebView webView, boolean isHeadless, boolean isForeground, String profileName) {
            this(pageId, webView, isHeadless, isForeground, profileName, new HardwareConfig());
        }

        public ManagedPage(String pageId, WebView webView, boolean isHeadless, boolean isForeground) {
            this(pageId, webView, isHeadless, isForeground, "default", new HardwareConfig());
        }
    }

    private final Context context;
    private ViewGroup foregroundContainer;
    private final ViewGroup offscreenContainer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Map<String, ManagedPage> pages = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(100);

    private String activeForegroundPageId = "main";
    private OnActivePageChangedListener pageChangedListener;
    private PageConfigurator pageConfigurator;
    private final WebViewAssetLoader assetLoader;

    public WebViewPool(Context context, ViewGroup offscreenContainer) {
        this.context = context;
        this.offscreenContainer = offscreenContainer;
        this.assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
                .build();
    }

    public WebViewAssetLoader getAssetLoader() {
        return assetLoader;
    }

    public static String resolveLocalUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.startsWith("local://")) {
            return "https://appassets.androidplatform.net/assets/" + trimmed.substring("local://".length());
        } else if (trimmed.startsWith("file:///dist/")) {
            return "https://appassets.androidplatform.net/assets/dist/" + trimmed.substring("file:///dist/".length());
        } else if (trimmed.startsWith("file:///android_asset/")) {
            return "https://appassets.androidplatform.net/assets/" + trimmed.substring("file:///android_asset/".length());
        }
        return trimmed;
    }

    public void setForegroundContainer(ViewGroup foregroundContainer) {
        this.foregroundContainer = foregroundContainer;
    }

    public void setOnActivePageChangedListener(OnActivePageChangedListener listener) {
        this.pageChangedListener = listener;
    }

    public void setPageConfigurator(PageConfigurator configurator) {
        this.pageConfigurator = configurator;
    }

    public Context getContext() {
        return context;
    }

    /**
     * 检测设备底层 Chromium 是否支持 MULTI_PROFILE 多 Profile 物理隔离
     */
    public static boolean isMultiProfileSupported() {
        try {
            return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 注册初始主页面 (支持硬件参数与 Stealth 防检测脚本 DocumentStart 注入)
     */
    public void registerMainPage(String pageId, WebView mainWebView) {
        registerMainPage(pageId, mainWebView, "default", HardwareConfig.createFlagship());
    }

    public void registerMainPage(String pageId, WebView mainWebView, String profileName) {
        registerMainPage(pageId, mainWebView, profileName, HardwareConfig.createFlagship());
    }

    public void registerMainPage(String pageId, WebView mainWebView, String profileName, HardwareConfig hardwareConfig) {
        this.activeForegroundPageId = pageId;
        HardwareConfig hw = hardwareConfig != null ? hardwareConfig : HardwareConfig.createFlagship();
        final String stealthScript = hw.generateInjectionScript();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(mainWebView, stealthScript, Collections.singleton("*"));
                Log.i(TAG, "Document-start hardware script registered for main page [CPU: " + hw.cpuCores + " cores, GPU: " + hw.glRenderer + "]");
            } catch (Exception e) {
                Log.w(TAG, "Failed to addDocumentStartJavaScript on main page: " + e.getMessage());
            }
        }

        ManagedPage mainPage = new ManagedPage(pageId, mainWebView, false, true, profileName, hw);
        pages.put(pageId, mainPage);
    }

    /**
     * 创建一个新的后台无头页面 (Headless WebView，使用默认 Profile)
     */
    public ManagedPage createHeadlessPage(String customUserAgent) {
        return createHeadlessPage(customUserAgent, "default", null, null);
    }

    public ManagedPage createHeadlessPage(String customUserAgent, String profileName) {
        return createHeadlessPage(customUserAgent, profileName, null, null);
    }

    public ManagedPage createHeadlessPage(String customUserAgent, String profileName, HardwareConfig hardwareConfig) {
        return createHeadlessPage(customUserAgent, profileName, hardwareConfig, null);
    }

    /**
     * 创建一个具备独立数据目录 (Profile)、自定义硬件参数与自定义初始地址的 WebView 实例
     * @param initialUrl 自定义初始地址 (如 https://creator.douyin.com)，传入即在创建后立即开始后台加载
     */
    public ManagedPage createHeadlessPage(String customUserAgent, String profileName, HardwareConfig hardwareConfig, String initialUrl) {
        if (pages.size() >= MAX_HEADLESS_INSTANCES + 1) {
            cleanOldestHeadlessPage();
        }

        final HardwareConfig hwConfig = hardwareConfig != null ? hardwareConfig : new HardwareConfig();
        String targetProfile = (profileName == null || profileName.trim().isEmpty()) ? "default" : profileName.trim();
        String prefix = targetProfile.equalsIgnoreCase("default") ? "page_headless_" : ("page_" + targetProfile + "_");
        String pageId = prefix + idGenerator.incrementAndGet();

        WebView webView = new WebView(context);

        // 1. 核心：在加载任何网页或执行 JS 前，必须通过 WebViewCompat.setProfile 绑定独立数据分区
        if (isMultiProfileSupported() && !targetProfile.equalsIgnoreCase("default")) {
            try {
                ProfileStore profileStore = ProfileStore.getInstance();
                profileStore.getOrCreateProfile(targetProfile);
                WebViewCompat.setProfile(webView, targetProfile);
                Log.i(TAG, "Successfully isolated profile [" + targetProfile + "] bound to WebView [" + pageId + "]");
            } catch (Exception e) {
                Log.e(TAG, "Failed to bind isolated profile [" + targetProfile + "]: " + e.getMessage());
            }
        } else if (!isMultiProfileSupported() && !targetProfile.equalsIgnoreCase("default")) {
            Log.w(TAG, "Device system WebView does not support MULTI_PROFILE. Running in emulated session mode.");
        }

        // 2. 硬件参数与设备指纹注入：在 DocumentStart 阶段预先注入 Stealth 伪装脚本
        final String stealthScript = hwConfig.generateInjectionScript();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(webView, stealthScript, Collections.singleton("*"));
                Log.i(TAG, "Document-start hardware script registered for [" + pageId + "] (CPU: "
                        + hwConfig.cpuCores + " cores, RAM: " + hwConfig.deviceMemory + "GB, GPU: " + hwConfig.glRenderer + ")");
            } catch (Exception e) {
                Log.w(TAG, "Failed to addDocumentStartJavaScript: " + e.getMessage());
            }
        }

        configureWebSettings(webView, customUserAgent, targetProfile);

        // 3. 渲染与页面监控 (含二次兜底注入与本地资产虚拟域名拦截)
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (assetLoader != null && request != null) {
                    WebResourceResponse response = assetLoader.shouldInterceptRequest(request.getUrl());
                    if (response != null) return response;
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (assetLoader != null && url != null) {
                    WebResourceResponse response = assetLoader.shouldInterceptRequest(Uri.parse(url));
                    if (response != null) return response;
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // 二级兜底注入，确保在任何老旧系统 WebView 上亦能绝对执行
                view.evaluateJavascript(stealthScript, null);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.e(TAG, "Headless WebView render process gone for " + pageId);
                destroyPage(pageId);
                return true;
            }
        });

        // 4. 统一桥接/配置回调（如注入 AndroidBridge JS 引擎）
        if (pageConfigurator != null) {
            pageConfigurator.configure(webView, pageId, targetProfile);
        }

        // 5. 挂载于后台离线容器（1dp 隐藏View，避免 Chromium 冻结定时器）
        if (offscreenContainer != null) {
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(1, 1);
            webView.setLayoutParams(lp);
            webView.setVisibility(View.INVISIBLE);
            offscreenContainer.addView(webView);
        }

        ManagedPage managedPage = new ManagedPage(pageId, webView, true, false, targetProfile, hwConfig);
        pages.put(pageId, managedPage);

        // 如果指定了自定义初始地址，立即在后台启动加载
        if (initialUrl != null && !initialUrl.trim().isEmpty()) {
            String url = resolveLocalUrl(initialUrl.trim());
            if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://") && !url.startsWith("about:")) {
                url = "https://" + url;
            }
            final String targetUrl = url;
            mainHandler.post(() -> webView.loadUrl(targetUrl));
        }

        return managedPage;
    }

    /**
     * 将硬件参数动态注入到现有 WebView 中
     */
    public void applyHardwareConfig(WebView targetWebView, HardwareConfig hwConfig) {
        if (targetWebView == null || hwConfig == null) return;
        final String stealthScript = hwConfig.generateInjectionScript();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(targetWebView, stealthScript, Collections.singleton("*"));
            } catch (Exception ignored) {}
        }
        targetWebView.evaluateJavascript(stealthScript, null);
    }

    /**
     * 将指定页面（无论来自何种 Profile 或前后台状态）平滑切换至前台全屏展示
     */
    public synchronized boolean switchToForeground(String pageId) {
        if (foregroundContainer == null) {
            Log.e(TAG, "Cannot switch to foreground: foregroundContainer is null");
            return false;
        }

        final ManagedPage targetPage = pages.get(pageId);
        if (targetPage == null) {
            Log.w(TAG, "Target page not found: " + pageId);
            return false;
        }

        if (pageId.equals(activeForegroundPageId) && targetPage.isForeground) {
            return true; // 已经是前台活跃页面
        }

        final ManagedPage currentActive = pages.get(activeForegroundPageId);

        // 1. 将原本在前台的 WebView 移至后台离线容器，并保留其完整 DOM/JS 运行时状态
        if (currentActive != null && currentActive.webView != null) {
            foregroundContainer.removeView(currentActive.webView);
            if (offscreenContainer != null) {
                ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(1, 1);
                currentActive.webView.setLayoutParams(lp);
                currentActive.webView.setVisibility(View.INVISIBLE);
                offscreenContainer.addView(currentActive.webView);
            }
            currentActive.isForeground = false;
        }

        // 2. 将目标 WebView 从后台容器中取出，挂载至前台主容器全屏渲染
        if (targetPage.webView != null) {
            if (offscreenContainer != null) {
                offscreenContainer.removeView(targetPage.webView);
            }
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            targetPage.webView.setLayoutParams(lp);
            targetPage.webView.setVisibility(View.VISIBLE);
            foregroundContainer.addView(targetPage.webView);
            targetPage.webView.onResume();
            targetPage.isForeground = true;
        }

        this.activeForegroundPageId = pageId;

        if (pageChangedListener != null) {
            pageChangedListener.onPageChanged(targetPage);
        }

        Log.i(TAG, "Successfully switched page [" + pageId + "] (Profile: " + targetPage.profileName + ") to foreground!");
        return true;
    }

    /**
     * 获取指定 Profile 对应的数据 CookieManager（实现隔离读写）
     */
    public CookieManager getCookieManager(String profileName) {
        if (isMultiProfileSupported() && profileName != null && !profileName.equalsIgnoreCase("default")) {
            try {
                Profile profile = ProfileStore.getInstance().getProfile(profileName);
                if (profile != null) {
                    return profile.getCookieManager();
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get profile CookieManager: " + e.getMessage());
            }
        }
        return CookieManager.getInstance();
    }

    /**
     * 获取指定 Profile 对应的 WebStorage（LocalStorage / IndexedDB 管理器）
     */
    public WebStorage getWebStorage(String profileName) {
        if (isMultiProfileSupported() && profileName != null && !profileName.equalsIgnoreCase("default")) {
            try {
                Profile profile = ProfileStore.getInstance().getProfile(profileName);
                if (profile != null) {
                    return profile.getWebStorage();
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get profile WebStorage: " + e.getMessage());
            }
        }
        return WebStorage.getInstance();
    }

    /**
     * 获取所有现存的 Profile 数据目录名称列表
     */
    public List<String> getAllProfileNames() {
        List<String> list = new ArrayList<>();
        list.add("default");
        if (isMultiProfileSupported()) {
            try {
                List<String> names = ProfileStore.getInstance().getAllProfileNames();
                for (String n : names) {
                    if (!list.contains(n)) {
                        list.add(n);
                    }
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    /**
     * 安全删除指定 Profile 并清空该分区下的所有磁盘持久化数据
     */
    public boolean deleteProfile(String profileName) {
        if (isMultiProfileSupported() && profileName != null && !profileName.equalsIgnoreCase("default")) {
            try {
                return ProfileStore.getInstance().deleteProfile(profileName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete profile: " + e.getMessage());
            }
        }
        return false;
    }

    private void configureWebSettings(WebView webView, String customUserAgent, String profileName) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        if (customUserAgent != null && !customUserAgent.isEmpty()) {
            settings.setUserAgentString(customUserAgent);
        }

        CookieManager cm = getCookieManager(profileName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            try {
                cm.setAcceptThirdPartyCookies(webView, true);
            } catch (Exception ignored) {}
        }
        try {
            cm.setAcceptCookie(true);
        } catch (Exception ignored) {}
    }

    public ManagedPage getPage(String pageId) {
        return pages.get(pageId);
    }

    public ManagedPage getActiveForegroundPage() {
        return pages.get(activeForegroundPageId);
    }

    public String getActiveForegroundPageId() {
        return activeForegroundPageId;
    }

    public List<ManagedPage> getAllPages() {
        return new ArrayList<>(pages.values());
    }

    public int getPageCount() {
        return pages.size();
    }

    public int getHeadlessPageCount() {
        int count = 0;
        for (ManagedPage page : pages.values()) {
            if (page.isHeadless) count++;
        }
        return count;
    }

    /**
     * 安全销毁指定页面实例，防止内存泄漏
     */
    public synchronized void destroyPage(String pageId) {
        // 如果正在销毁的是当前前台页面，先自动切回主页面
        if (pageId.equals(activeForegroundPageId) && !pageId.equals("main")) {
            switchToForeground("main");
        }

        ManagedPage page = pages.remove(pageId);
        if (page != null && page.webView != null) {
            WebView wv = page.webView;
            try {
                if (offscreenContainer != null) {
                    offscreenContainer.removeView(wv);
                }
                if (foregroundContainer != null) {
                    foregroundContainer.removeView(wv);
                }
                wv.stopLoading();
                wv.clearHistory();
                wv.loadUrl("about:blank");
                wv.onPause();
                wv.removeAllViews();
                wv.destroy();
                Log.d(TAG, "Page destroyed: " + pageId);
            } catch (Exception e) {
                Log.e(TAG, "Error destroying page: " + e.getMessage());
            }
        }
    }

    private void cleanOldestHeadlessPage() {
        String oldestId = null;
        long oldestTime = Long.MAX_VALUE;

        for (ManagedPage page : pages.values()) {
            if (page.isHeadless && !page.isForeground && page.createdAt < oldestTime) {
                oldestTime = page.createdAt;
                oldestId = page.pageId;
            }
        }

        if (oldestId != null) {
            destroyPage(oldestId);
        }
    }

    public void destroyAll() {
        for (String id : pages.keySet()) {
            destroyPage(id);
        }
        pages.clear();
    }
}
