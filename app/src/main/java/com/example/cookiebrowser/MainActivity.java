package com.example.cookiebrowser;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.cookiebrowser.bridge.JsBridgeEngine;
import com.example.cookiebrowser.core.AppConfig;
import com.example.cookiebrowser.core.AppConfigManager;
import com.example.cookiebrowser.core.HardwareConfig;
import com.example.cookiebrowser.core.WebViewPool;
import com.example.cookiebrowser.plugins.AppPlugin;
import com.example.cookiebrowser.plugins.CookiePlugin;
import com.example.cookiebrowser.plugins.DevicePlugin;
import com.example.cookiebrowser.plugins.NetworkPlugin;
import com.example.cookiebrowser.plugins.PagePlugin;
import com.example.cookiebrowser.plugins.WindowPlugin;
import com.example.cookiebrowser.plugins.TabBarPlugin;
import com.example.cookiebrowser.plugins.DebugPlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.RelativeLayout;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 商业级 Mobile Electron 容器主界面（支持多 WebView 前后台自由切换与全 Web 界面驱动）
 */
public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutTopBar;
    private LinearLayout layoutBottomBar;

    // 顶部组件
    private ImageView ivSecurityLock;
    private EditText etUrl;
    private ImageView ivClearUrl;
    private ImageView btnRefresh;
    private ImageView btnTopConsole;
    private ProgressBar progressBar;

    // 容器与动态主视图
    private FrameLayout webviewContainer;
    private WebView webView;
    private FrameLayout offscreenContainer;

    // 底部导航栏组件
    private LinearLayout btnNavBack;
    private ImageView ivNavBack;
    private LinearLayout btnNavForward;
    private ImageView ivNavForward;
    private LinearLayout btnNavCookieHub;
    private TextView tvCookieBadge;
    private LinearLayout btnNavPages;
    private TextView tvPagesBadge;
    private LinearLayout btnNavSecurity;

    // 原生容器底座
    private WebViewPool webViewPool;
    private JsBridgeEngine bridgeEngine;

    // 动态原生 TabBar 组件与状态 (核心要求 1)
    private LinearLayout layoutNativeTabBar;
    private ImageView btnFloatingDebug;
    private Dialog frameworkDebugDialog;

    public static class TabBarItemModel {
        public String id;
        public String title;
        public String icon;
        public String selectedIcon;
        public String badge;
        public ImageView ivIcon;
        public TextView tvTitle;
        public TextView tvBadge;
        public LinearLayout container;
    }

    private final List<TabBarItemModel> tabBarItemModels = new ArrayList<>();
    private String currentSelectedTabId = "";
    private int currentSelectedTabIndex = 0;
    private String tabBarBgColor = "#FFFFFF";
    private String tabBarTextColor = "#64748B";
    private String tabBarSelectedColor = "#4F46E5";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 初始化框架应用配置
        AppConfigManager.getInstance().init(this);
        AppConfig config = AppConfigManager.getInstance().getConfig();

        // 2. 初始化视图与原生底座
        initViews();
        initContainerEngine();
        setupWebView();
        setupListeners();

        // 3. 应用配置（状态栏沉浸、全屏模式与调试工具条）
        applyStatusBarSettings(config.getStatusBarColor(), config.isStatusBarDarkIcons(), config.isImmersiveStatusBar());
        setFullscreenMode(config.isFullscreen());
        setDebugToolbarVisible(config.isShowNativeDebugToolbar());

        // 4. 加载配置中指定的默认启动网页
        loadUrl(config.getDefaultUrl());
    }

    private void initViews() {
        layoutTopBar = findViewById(R.id.layout_top_bar);
        layoutBottomBar = findViewById(R.id.layout_bottom_bar);

        ivSecurityLock = findViewById(R.id.iv_security_lock);
        etUrl = findViewById(R.id.et_url);
        ivClearUrl = findViewById(R.id.iv_clear_url);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnTopConsole = findViewById(R.id.btn_top_console);
        progressBar = findViewById(R.id.progress_bar);

        webviewContainer = findViewById(R.id.webview_container);
        webView = findViewById(R.id.web_view);
        offscreenContainer = findViewById(R.id.offscreen_container);

        btnNavBack = findViewById(R.id.btn_nav_back);
        ivNavBack = findViewById(R.id.iv_nav_back);
        btnNavForward = findViewById(R.id.btn_nav_forward);
        ivNavForward = findViewById(R.id.iv_nav_forward);
        btnNavCookieHub = findViewById(R.id.btn_nav_cookie_hub);
        tvCookieBadge = findViewById(R.id.tv_cookie_badge);
        btnNavPages = findViewById(R.id.btn_nav_pages);
        tvPagesBadge = findViewById(R.id.tv_pages_badge);
        btnNavSecurity = findViewById(R.id.btn_nav_security);
        layoutNativeTabBar = findViewById(R.id.layout_native_tab_bar);
        btnFloatingDebug = findViewById(R.id.btn_floating_debug);
    }

    private void initContainerEngine() {
        AppConfig config = AppConfigManager.getInstance().getConfig();
        HardwareConfig hwConfig = HardwareConfig.fromPresetOrParams(config.getHardwarePreset(), null);

        webViewPool = new WebViewPool(this, offscreenContainer);
        webViewPool.setForegroundContainer(webviewContainer);
        webViewPool.registerMainPage("main", webView, config.getDefaultProfile(), hwConfig);

        // 统一为池中所有 WebView（无论是主页面还是后续创建的独立 Profile 页面）绑定 JS Bridge
        webViewPool.setPageConfigurator((targetWebView, pageId, profileName) -> {
            JsBridgeEngine pageEngine = new JsBridgeEngine(targetWebView);
            pageEngine.registerPlugin(new CookiePlugin(webViewPool));
            pageEngine.registerPlugin(new PagePlugin(webViewPool, MainActivity.this));
            pageEngine.registerPlugin(new NetworkPlugin());
            pageEngine.registerPlugin(new DevicePlugin(MainActivity.this));
            pageEngine.registerPlugin(new WindowPlugin(MainActivity.this, webViewPool));
            pageEngine.registerPlugin(new AppPlugin(MainActivity.this));
            pageEngine.registerPlugin(new com.example.cookiebrowser.plugins.FilePlugin(MainActivity.this));
            pageEngine.registerPlugin(new com.example.cookiebrowser.plugins.StoragePlugin(MainActivity.this, webViewPool));
            pageEngine.registerPlugin(new com.example.cookiebrowser.plugins.DialogPlugin(MainActivity.this));
            pageEngine.registerPlugin(new TabBarPlugin(MainActivity.this));
            pageEngine.registerPlugin(new DebugPlugin(MainActivity.this));
            targetWebView.addJavascriptInterface(pageEngine, "AndroidBridge");
        });

        // 监听前台活跃页面切换事件
        webViewPool.setOnActivePageChangedListener(newActivePage -> {
            if (newActivePage != null && newActivePage.webView != null) {
                String activeUrl = newActivePage.webView.getUrl();
                etUrl.setText(activeUrl != null ? activeUrl : "");
                updateNavState();
                updateCookieBadge();
                updatePagesBadge();
                updateSecurityIndicator(activeUrl);
            }
        });

        bridgeEngine = new JsBridgeEngine(webView);
        bridgeEngine.registerPlugin(new CookiePlugin(webViewPool));
        bridgeEngine.registerPlugin(new PagePlugin(webViewPool, this));
        bridgeEngine.registerPlugin(new NetworkPlugin());
        bridgeEngine.registerPlugin(new DevicePlugin(this));
        bridgeEngine.registerPlugin(new WindowPlugin(this, webViewPool));
        bridgeEngine.registerPlugin(new AppPlugin(this));
        bridgeEngine.registerPlugin(new com.example.cookiebrowser.plugins.FilePlugin(this));
        bridgeEngine.registerPlugin(new com.example.cookiebrowser.plugins.StoragePlugin(this, webViewPool));
        bridgeEngine.registerPlugin(new com.example.cookiebrowser.plugins.DialogPlugin(this));
        bridgeEngine.registerPlugin(new TabBarPlugin(this));
        bridgeEngine.registerPlugin(new DebugPlugin(this));

        webView.addJavascriptInterface(bridgeEngine, "AndroidBridge");
    }

    private void setupWebView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (webViewPool != null && webViewPool.getAssetLoader() != null && request != null) {
                    WebResourceResponse response = webViewPool.getAssetLoader().shouldInterceptRequest(request.getUrl());
                    if (response != null) return response;
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (webViewPool != null && webViewPool.getAssetLoader() != null && url != null) {
                    WebResourceResponse response = webViewPool.getAssetLoader().shouldInterceptRequest(Uri.parse(url));
                    if (response != null) return response;
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                if (!etUrl.hasFocus()) {
                    etUrl.setText(url);
                }
                updateNavState();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                if (!etUrl.hasFocus()) {
                    etUrl.setText(url);
                }
                updateNavState();
                updateCookieBadge();
                updatePagesBadge();
                updateSecurityIndicator(url);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Toast.makeText(MainActivity.this, "检测到渲染进程异常，已自动恢复", Toast.LENGTH_SHORT).show();
                view.loadUrl(AppConfigManager.getInstance().getConfig().getDefaultUrl());
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void setupListeners() {
        etUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                navigateFromInput();
                return true;
            }
            return false;
        });

        etUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ivClearUrl.setVisibility(s.length() > 0 && etUrl.hasFocus() ? View.VISIBLE : View.GONE);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        etUrl.setOnFocusChangeListener((v, hasFocus) -> {
            ivClearUrl.setVisibility(hasFocus && etUrl.getText().length() > 0 ? View.VISIBLE : View.GONE);
            if (hasFocus) etUrl.selectAll();
        });

        ivClearUrl.setOnClickListener(v -> etUrl.setText(""));
        btnRefresh.setOnClickListener(v -> {
            WebView active = getActiveWebView();
            if (active != null) active.reload();
        });

        btnTopConsole.setOnClickListener(v -> toggleFrameworkDebugDialog());
        if (btnFloatingDebug != null) {
            btnFloatingDebug.setOnClickListener(v -> toggleFrameworkDebugDialog());
        }
        ivSecurityLock.setOnClickListener(v -> showSecurityWhitelistDialog());

        // 底部导航栏点击事件
        btnNavBack.setOnClickListener(v -> {
            WebView active = getActiveWebView();
            if (active != null && active.canGoBack()) active.goBack();
        });

        btnNavForward.setOnClickListener(v -> {
            WebView active = getActiveWebView();
            if (active != null && active.canGoForward()) active.goForward();
        });

        btnNavCookieHub.setOnClickListener(v -> showCookieHubDialog());
        btnNavPages.setOnClickListener(v -> showPageManagerDialog());
        btnNavSecurity.setOnClickListener(v -> showSecurityWhitelistDialog());
    }

    private WebView getActiveWebView() {
        WebViewPool.ManagedPage activePage = webViewPool.getActiveForegroundPage();
        return activePage != null && activePage.webView != null ? activePage.webView : webView;
    }

    private void navigateFromInput() {
        String input = etUrl.getText().toString().trim();
        if (TextUtils.isEmpty(input)) return;

        hideKeyboard(etUrl);

        if (!input.startsWith("http://") && !input.startsWith("https://") && !input.startsWith("file://") && !input.startsWith("local://")) {
            input = "https://" + input;
        }
        loadUrl(input);
    }

    public void loadUrl(String url) {
        if (url == null) return;
        String resolvedUrl = WebViewPool.resolveLocalUrl(url);
        if (etUrl != null) {
            etUrl.setText(url);
        }
        WebView active = getActiveWebView();
        if (active != null) {
            active.loadUrl(resolvedUrl);
        }
    }

    public void setDebugToolbarVisible(boolean visible) {
        if (layoutTopBar != null) layoutTopBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (layoutBottomBar != null) layoutBottomBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void applyStatusBarSettings(String colorHex, boolean darkIcons, boolean immersive) {
        try {
            Window window = getWindow();
            if (window == null) return;

            WindowCompat.setDecorFitsSystemWindows(window, !immersive);

            if (colorHex != null && !colorHex.trim().isEmpty()) {
                window.setStatusBarColor(Color.parseColor(colorHex.trim()));
            }

            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
            if (insetsController != null) {
                insetsController.setAppearanceLightStatusBars(darkIcons);
            }
        } catch (Exception ignored) {}
    }

    public void setFullscreenMode(boolean fullscreen) {
        try {
            Window window = getWindow();
            if (window == null) return;
            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
            if (insetsController != null) {
                if (fullscreen) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars());
                    insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars());
                }
            }
        } catch (Exception ignored) {}
    }

    // ==========================================
    // 动态原生 TabBar 核心实现 (核心要求 1)
    // ==========================================

    public void setTabBarItems(JSONArray items, JSONObject options) {
        if (layoutNativeTabBar == null) return;
        layoutNativeTabBar.removeAllViews();
        tabBarItemModels.clear();

        if (options != null) {
            tabBarBgColor = options.optString("backgroundColor", "#FFFFFF");
            tabBarTextColor = options.optString("color", "#64748B");
            tabBarSelectedColor = options.optString("selectedColor", "#4F46E5");
            currentSelectedTabId = options.optString("selectedId", "");
            if (options.has("visible")) {
                setTabBarVisible(options.optBoolean("visible", true));
            }
        }
        try {
            layoutNativeTabBar.setBackgroundColor(Color.parseColor(tabBarBgColor));
        } catch (Exception ignored) {}

        for (int i = 0; i < items.length(); i++) {
            JSONObject obj = items.optJSONObject(i);
            if (obj == null) continue;

            final int index = i;
            final TabBarItemModel model = new TabBarItemModel();
            model.id = obj.optString("id", "tab_" + i);
            model.title = obj.optString("title", "");
            model.icon = obj.optString("icon", "");
            model.selectedIcon = obj.optString("selectedIcon", "");
            model.badge = obj.optString("badge", "");

            if (TextUtils.isEmpty(currentSelectedTabId) && i == 0) {
                currentSelectedTabId = model.id;
            }

            LinearLayout itemLayout = new LinearLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            itemLayout.setLayoutParams(lp);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setGravity(Gravity.CENTER);
            itemLayout.setClickable(true);
            itemLayout.setFocusable(true);

            int[] attrs = new int[]{android.R.attr.selectableItemBackground};
            android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
            itemLayout.setBackground(ta.getDrawable(0));
            ta.recycle();

            RelativeLayout iconArea = new RelativeLayout(this);
            RelativeLayout.LayoutParams areaLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            iconArea.setLayoutParams(areaLp);

            ImageView iv = new ImageView(this);
            int iconPx = (int) (22 * getResources().getDisplayMetrics().density);
            RelativeLayout.LayoutParams ivLp = new RelativeLayout.LayoutParams(iconPx, iconPx);
            iv.setId(View.generateViewId());
            iv.setLayoutParams(ivLp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            model.ivIcon = iv;

            bindTabIcon(model, model.id.equals(currentSelectedTabId));
            iconArea.addView(iv);

            TextView badgeTv = new TextView(this);
            RelativeLayout.LayoutParams badgeLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.addRule(RelativeLayout.ALIGN_TOP, iv.getId());
            badgeLp.addRule(RelativeLayout.ALIGN_RIGHT, iv.getId());
            badgeLp.setMargins((int) (10 * getResources().getDisplayMetrics().density), 0, 0, 0);
            badgeTv.setLayoutParams(badgeLp);
            badgeTv.setBackgroundResource(R.drawable.bg_tab_badge);
            badgeTv.setTextColor(Color.WHITE);
            badgeTv.setTextSize(9);
            badgeTv.setText(model.badge);
            badgeTv.setVisibility(!TextUtils.isEmpty(model.badge) ? View.VISIBLE : View.GONE);
            model.tvBadge = badgeTv;
            iconArea.addView(badgeTv);

            itemLayout.addView(iconArea);

            TextView tv = new TextView(this);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tvLp.topMargin = (int) (2 * getResources().getDisplayMetrics().density);
            tv.setLayoutParams(tvLp);
            tv.setText(model.title);
            tv.setTextSize(10);
            model.tvTitle = tv;
            itemLayout.addView(tv);

            model.container = itemLayout;
            tabBarItemModels.add(model);

            itemLayout.setOnClickListener(v -> {
                currentSelectedTabId = model.id;
                currentSelectedTabIndex = index;
                updateTabBarSelection();

                try {
                    JSONObject evtData = new JSONObject();
                    evtData.put("id", model.id);
                    evtData.put("index", index);
                    evtData.put("title", model.title);
                    sendEventToActiveWebView("tabBar:click", evtData);
                } catch (Exception ignored) {}
            });

            layoutNativeTabBar.addView(itemLayout);
        }

        updateTabBarSelection();
    }

    private void bindTabIcon(TabBarItemModel model, boolean isSelected) {
        if (model.ivIcon == null) return;
        String iconName = isSelected && !TextUtils.isEmpty(model.selectedIcon) ? model.selectedIcon : model.icon;
        int tintColor;
        try {
            tintColor = Color.parseColor(isSelected ? tabBarSelectedColor : tabBarTextColor);
        } catch (Exception e) {
            tintColor = isSelected ? Color.parseColor("#4F46E5") : Color.parseColor("#64748B");
        }

        if (TextUtils.isEmpty(iconName)) {
            model.ivIcon.setImageResource(R.drawable.ic_tab_home);
            model.ivIcon.setColorFilter(tintColor);
            return;
        }

        if (iconName.startsWith("data:image/")) {
            try {
                int commaIdx = iconName.indexOf(",");
                String base64Str = commaIdx >= 0 ? iconName.substring(commaIdx + 1) : iconName;
                byte[] bytes = Base64.decode(base64Str, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                model.ivIcon.setImageBitmap(bmp);
                model.ivIcon.clearColorFilter();
                return;
            } catch (Exception ignored) {}
        }

        if (iconName.startsWith("http://") || iconName.startsWith("https://")) {
            loadRemoteTabIcon(model.ivIcon, iconName);
            return;
        }

        int resId = getBuiltinTabIconRes(iconName.toLowerCase());
        model.ivIcon.setImageResource(resId);
        model.ivIcon.setColorFilter(tintColor);
    }

    private void loadRemoteTabIcon(ImageView iv, String urlStr) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();
                InputStream is = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                conn.disconnect();
                if (bmp != null) {
                    runOnUiThread(() -> {
                        iv.setImageBitmap(bmp);
                        iv.clearColorFilter();
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private int getBuiltinTabIconRes(String name) {
        switch (name) {
            case "home": return R.drawable.ic_tab_home;
            case "search": return R.drawable.ic_tab_search;
            case "grid":
            case "category": return R.drawable.ic_tab_grid;
            case "user":
            case "profile":
            case "my": return R.drawable.ic_tab_user;
            case "settings": return R.drawable.ic_tab_settings;
            case "cart": return R.drawable.ic_tab_cart;
            case "bell":
            case "notice": return R.drawable.ic_tab_bell;
            case "cookie": return R.drawable.ic_cookie;
            case "bug":
            case "debug": return R.drawable.ic_bug;
            case "terminal":
            case "console": return R.drawable.ic_terminal;
            case "refresh": return R.drawable.ic_refresh;
            case "back": return R.drawable.ic_arrow_back;
            case "forward": return R.drawable.ic_arrow_forward;
            case "lock":
            case "shield": return R.drawable.ic_shield_check;
            default: return R.drawable.ic_tab_home;
        }
    }

    private void updateTabBarSelection() {
        for (int i = 0; i < tabBarItemModels.size(); i++) {
            TabBarItemModel m = tabBarItemModels.get(i);
            boolean isSelected = m.id.equals(currentSelectedTabId) || (currentSelectedTabId.isEmpty() && i == currentSelectedTabIndex);
            bindTabIcon(m, isSelected);
            if (m.tvTitle != null) {
                try {
                    m.tvTitle.setTextColor(Color.parseColor(isSelected ? tabBarSelectedColor : tabBarTextColor));
                } catch (Exception ignored) {}
            }
        }
    }

    public void setTabBarVisible(boolean visible) {
        if (layoutNativeTabBar != null) {
            layoutNativeTabBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public boolean toggleTabBarVisible() {
        boolean now = layoutNativeTabBar != null && layoutNativeTabBar.getVisibility() == View.VISIBLE;
        setTabBarVisible(!now);
        return !now;
    }

    public boolean setTabBarSelected(String id, int index) {
        if (!TextUtils.isEmpty(id)) {
            currentSelectedTabId = id;
        } else if (index >= 0 && index < tabBarItemModels.size()) {
            currentSelectedTabIndex = index;
            currentSelectedTabId = tabBarItemModels.get(index).id;
        } else {
            return false;
        }
        updateTabBarSelection();
        return true;
    }

    public boolean setTabBarBadge(String id, int index, String badge) {
        for (int i = 0; i < tabBarItemModels.size(); i++) {
            TabBarItemModel m = tabBarItemModels.get(i);
            if ((!TextUtils.isEmpty(id) && id.equals(m.id)) || (i == index)) {
                m.badge = badge;
                if (m.tvBadge != null) {
                    m.tvBadge.setText(badge);
                    m.tvBadge.setVisibility(!TextUtils.isEmpty(badge) ? View.VISIBLE : View.GONE);
                }
                return true;
            }
        }
        return false;
    }

    public JSONObject getTabBarState() {
        JSONObject state = new JSONObject();
        try {
            state.put("visible", layoutNativeTabBar != null && layoutNativeTabBar.getVisibility() == View.VISIBLE);
            state.put("selectedId", currentSelectedTabId);
            state.put("selectedIndex", currentSelectedTabIndex);
            JSONArray itemsArr = new JSONArray();
            for (TabBarItemModel m : tabBarItemModels) {
                JSONObject itemObj = new JSONObject();
                itemObj.put("id", m.id);
                itemObj.put("title", m.title);
                itemObj.put("icon", m.icon);
                itemObj.put("selectedIcon", m.selectedIcon);
                itemObj.put("badge", m.badge);
                itemsArr.put(itemObj);
            }
            state.put("items", itemsArr);
        } catch (Exception ignored) {}
        return state;
    }

    // ==========================================
    // 框架调试界面与 DevTools 控制 (核心要求 2)
    // ==========================================

    public void showFrameworkDebugDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (frameworkDebugDialog != null && frameworkDebugDialog.isShowing()) {
            return;
        }

        frameworkDebugDialog = new Dialog(this);
        frameworkDebugDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        frameworkDebugDialog.setContentView(R.layout.dialog_framework_debug);

        Window window = frameworkDebugDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }

        WebView active = getActiveWebView();
        String activeUrl = active != null ? active.getUrl() : "无";

        TextView tvUrl = frameworkDebugDialog.findViewById(R.id.tv_debug_active_url);
        if (tvUrl != null) {
            tvUrl.setText("活跃页面: " + (activeUrl != null ? activeUrl : ""));
        }

        TextView tvRuntime = frameworkDebugDialog.findViewById(R.id.tv_debug_runtime_info);
        if (tvRuntime != null) {
            long freeMb = Runtime.getRuntime().freeMemory() / (1024 * 1024);
            long totalMb = Runtime.getRuntime().totalMemory() / (1024 * 1024);
            tvRuntime.setText(String.format("平台: Android (API %d) | PID: %d | 堆内存: %dMB / %dMB",
                    Build.VERSION.SDK_INT, android.os.Process.myPid(), (totalMb - freeMb), totalMb));
        }

        TextView tvPages = frameworkDebugDialog.findViewById(R.id.tv_debug_pages_info);
        if (tvPages != null && webViewPool != null) {
            tvPages.setText(String.format("页面池: %d 个前台页面 | %d 个后台运行页面",
                    1, webViewPool.getHeadlessPageCount()));
        }

        ImageView ivClose = frameworkDebugDialog.findViewById(R.id.iv_close_debug_dialog);
        if (ivClose != null) {
            ivClose.setOnClickListener(v -> frameworkDebugDialog.dismiss());
        }

        Button btnOpenDevTools = frameworkDebugDialog.findViewById(R.id.btn_open_webview_devtools);
        if (btnOpenDevTools != null) {
            btnOpenDevTools.setOnClickListener(v -> {
                openDevToolsForActiveWebView();
                Toast.makeText(MainActivity.this, "已为当前 WebView 打开 DevTools", Toast.LENGTH_SHORT).show();
                frameworkDebugDialog.dismiss();
            });
        }

        Button btnCloseDevTools = frameworkDebugDialog.findViewById(R.id.btn_close_webview_devtools);
        if (btnCloseDevTools != null) {
            btnCloseDevTools.setOnClickListener(v -> {
                closeDevToolsForActiveWebView();
                Toast.makeText(MainActivity.this, "已关闭 DevTools", Toast.LENGTH_SHORT).show();
            });
        }

        Button btnCookieHub = frameworkDebugDialog.findViewById(R.id.btn_debug_cookie_hub);
        if (btnCookieHub != null) {
            btnCookieHub.setOnClickListener(v -> {
                frameworkDebugDialog.dismiss();
                showCookieHubDialog();
            });
        }

        Button btnPageManager = frameworkDebugDialog.findViewById(R.id.btn_debug_page_manager);
        if (btnPageManager != null) {
            btnPageManager.setOnClickListener(v -> {
                frameworkDebugDialog.dismiss();
                showPageManagerDialog();
            });
        }

        Button btnWhitelist = frameworkDebugDialog.findViewById(R.id.btn_debug_whitelist);
        if (btnWhitelist != null) {
            btnWhitelist.setOnClickListener(v -> {
                frameworkDebugDialog.dismiss();
                showSecurityWhitelistDialog();
            });
        }

        Button btnToggleTop = frameworkDebugDialog.findViewById(R.id.btn_debug_toggle_topbar);
        if (btnToggleTop != null) {
            btnToggleTop.setOnClickListener(v -> {
                boolean cur = layoutTopBar != null && layoutTopBar.getVisibility() == View.VISIBLE;
                if (layoutTopBar != null) layoutTopBar.setVisibility(cur ? View.GONE : View.VISIBLE);
            });
        }

        Button btnToggleTab = frameworkDebugDialog.findViewById(R.id.btn_debug_toggle_tabbar);
        if (btnToggleTab != null) {
            btnToggleTab.setOnClickListener(v -> toggleTabBarVisible());
        }

        Button btnReload = frameworkDebugDialog.findViewById(R.id.btn_debug_hard_reload);
        if (btnReload != null) {
            btnReload.setOnClickListener(v -> {
                if (active != null) {
                    active.clearCache(true);
                    active.reload();
                }
                frameworkDebugDialog.dismiss();
            });
        }

        frameworkDebugDialog.show();
    }

    public void dismissFrameworkDebugDialog() {
        if (frameworkDebugDialog != null && frameworkDebugDialog.isShowing()) {
            frameworkDebugDialog.dismiss();
        }
    }

    public boolean toggleFrameworkDebugDialog() {
        if (frameworkDebugDialog != null && frameworkDebugDialog.isShowing()) {
            frameworkDebugDialog.dismiss();
            return false;
        } else {
            showFrameworkDebugDialog();
            return true;
        }
    }

    public void setFloatingDebugButtonVisible(boolean visible) {
        if (btnFloatingDebug != null) {
            btnFloatingDebug.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public JSONObject openDevToolsForActiveWebView() {
        WebView active = getActiveWebView();
        JSONObject res = new JSONObject();
        try {
            if (active == null) {
                res.put("success", false);
                res.put("message", "No active WebView found");
                return res;
            }

            // 1. 确保 Chromium 远程调试开启
            WebView.setWebContentsDebuggingEnabled(true);

            // 2. 注入并呼出 Eruda DevTools 控制台
            String erudaScript = loadAssetString("eruda.js");
            if (erudaScript != null && !erudaScript.isEmpty()) {
                String js = "(function(){\n" +
                    "  if(window.eruda){\n" +
                    "    window.eruda.show();\n" +
                    "  } else {\n" +
                    "    try {\n" +
                    "      " + erudaScript + "\n" +
                    "      if(window.eruda){ window.eruda.init(); window.eruda.show(); }\n" +
                    "    } catch(e){\n" +
                    "      console.error('[DevTools] Eruda init failed:', e);\n" +
                    "    }\n" +
                    "  }\n" +
                    "})();";
                active.post(() -> active.evaluateJavascript(js, null));
            } else {
                String cdnJs = "(function(){\n" +
                    "  if(window.eruda){\n" +
                    "    window.eruda.show();\n" +
                    "  } else {\n" +
                    "    var s = document.createElement('script');\n" +
                    "    s.src = 'https://cdn.jsdelivr.net/npm/eruda';\n" +
                    "    s.onload = function(){ eruda.init(); eruda.show(); };\n" +
                    "    document.body.appendChild(s);\n" +
                    "  }\n" +
                    "})();";
                active.post(() -> active.evaluateJavascript(cdnJs, null));
            }

            res.put("success", true);
            res.put("devtoolsSupported", true);
            res.put("opened", true);
            res.put("activeUrl", active.getUrl());
            res.put("remoteDebugging", true);
            res.put("remotePort", 9222);
        } catch (Exception e) {
            try {
                res.put("success", false);
                res.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }
        return res;
    }

    public JSONObject closeDevToolsForActiveWebView() {
        WebView active = getActiveWebView();
        JSONObject res = new JSONObject();
        try {
            if (active != null) {
                String js = "(function(){ if(window.eruda){ window.eruda.hide(); } })();";
                active.post(() -> active.evaluateJavascript(js, null));
            }
            res.put("success", true);
            res.put("opened", false);
        } catch (Exception e) {
            try {
                res.put("success", false);
                res.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }
        return res;
    }

    public JSONObject toggleDevToolsForActiveWebView() {
        WebView active = getActiveWebView();
        JSONObject res = new JSONObject();
        try {
            if (active == null) {
                res.put("success", false);
                return res;
            }
            String js = "(function(){\n" +
                "  if(!window.eruda){\n" +
                "    return false;\n" +
                "  }\n" +
                "  var el = document.getElementById('eruda');\n" +
                "  if(el && el.style.display !== 'none'){\n" +
                "    window.eruda.hide();\n" +
                "    return false;\n" +
                "  } else {\n" +
                "    window.eruda.show();\n" +
                "    return true;\n" +
                "  }\n" +
                "})();";
            active.post(() -> active.evaluateJavascript(js, (val) -> {
                if ("false".equals(val) || "null".equals(val)) {
                    openDevToolsForActiveWebView();
                }
            }));
            res.put("success", true);
        } catch (Exception e) {
            try {
                res.put("success", false);
                res.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }
        return res;
    }

    public JSONObject getFrameworkDebugInfo() {
        JSONObject info = new JSONObject();
        try {
            info.put("platform", "android");
            info.put("sdkVersion", Build.VERSION.SDK_INT);
            info.put("pid", android.os.Process.myPid());
            WebView active = getActiveWebView();
            info.put("activeUrl", active != null ? active.getUrl() : "");
            info.put("headlessCount", webViewPool != null ? webViewPool.getHeadlessPageCount() : 0);
            info.put("tabBarVisible", layoutNativeTabBar != null && layoutNativeTabBar.getVisibility() == View.VISIBLE);
            info.put("devtoolsPort", 9222);
        } catch (Exception ignored) {}
        return info;
    }

    public void sendEventToActiveWebView(String eventName, JSONObject data) {
        WebView active = getActiveWebView();
        if (active != null) {
            String jsonStr = data != null ? data.toString() : "{}";
            String eventEnvelope = "{\"event\":\"" + eventName + "\",\"data\":" + jsonStr + "}";
            String js = "window.__onNativeRpcEvent && window.__onNativeRpcEvent(" + eventEnvelope + ");";
            active.post(() -> active.evaluateJavascript(js, null));
        }
    }

    private String loadAssetString(String filename) {
        try (InputStream is = getAssets().open(filename);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void updateNavState() {
        WebView active = getActiveWebView();
        boolean canBack = active != null && active.canGoBack();
        boolean canForward = active != null && active.canGoForward();

        ivNavBack.setAlpha(canBack ? 1.0f : 0.35f);
        ivNavForward.setAlpha(canForward ? 1.0f : 0.35f);
    }

    private void updateCookieBadge() {
        WebView active = getActiveWebView();
        String currentUrl = active != null ? active.getUrl() : "";
        if (TextUtils.isEmpty(currentUrl)) {
            tvCookieBadge.setVisibility(View.GONE);
            return;
        }

        WebViewPool.ManagedPage activePage = webViewPool.getActiveForegroundPage();
        CookieManager cm = activePage != null ? webViewPool.getCookieManager(activePage.profileName) : CookieManager.getInstance();
        String cookies = cm.getCookie(currentUrl);
        if (TextUtils.isEmpty(cookies)) {
            tvCookieBadge.setVisibility(View.GONE);
            return;
        }

        String[] pairs = cookies.split(";");
        int count = 0;
        for (String p : pairs) {
            if (!p.trim().isEmpty()) count++;
        }

        if (count > 0) {
            tvCookieBadge.setVisibility(View.VISIBLE);
            tvCookieBadge.setText(String.valueOf(count));
        } else {
            tvCookieBadge.setVisibility(View.GONE);
        }
    }

    private void updatePagesBadge() {
        int total = webViewPool.getPageCount();
        tvPagesBadge.setText(String.valueOf(total));
        tvPagesBadge.setVisibility(View.VISIBLE);
    }

    private void updateSecurityIndicator(String url) {
        boolean isHttps = url != null && url.startsWith("https://");
        boolean isCoreWhitelisted = DomainWhitelistManager.getInstance().checkPermission(url, DomainWhitelistManager.TIER_2_CORE);

        if (isCoreWhitelisted) {
            ivSecurityLock.setImageResource(R.drawable.ic_shield_check);
        } else if (isHttps) {
            ivSecurityLock.setImageResource(R.drawable.ic_lock);
        } else {
            ivSecurityLock.setImageResource(R.drawable.ic_lock);
        }
    }

    /**
     * 弹出商业级多页面与后台任务调度中心 (可将后台运行的 WebView 切换到前台显示)
     */
    private void showPageManagerDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_page_manager);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
        }

        TextView tvSubtitle = dialog.findViewById(R.id.tv_page_manager_subtitle);
        TextView tvProfileEngineStatus = dialog.findViewById(R.id.tv_profile_engine_status);
        Button btnCreateSample = dialog.findViewById(R.id.btn_create_sample_headless);
        Button btnCreateProfile = dialog.findViewById(R.id.btn_create_profile_headless);
        Button btnStartDouyinQr = dialog.findViewById(R.id.btn_start_douyin_qr_login);
        LinearLayout layoutPageCards = dialog.findViewById(R.id.layout_page_cards);
        ImageView ivClose = dialog.findViewById(R.id.iv_close_page_manager);

        if (btnStartDouyinQr != null) {
            btnStartDouyinQr.setOnClickListener(v -> {
                dialog.dismiss();
                com.example.cookiebrowser.core.DouyinQrLoginManager.getInstance().start(MainActivity.this, webViewPool);
            });
        }

        boolean multiProfileSupported = WebViewPool.isMultiProfileSupported();
        if (multiProfileSupported) {
            tvProfileEngineStatus.setText("🛡️ Chromium Multi-Profile：原生硬件级物理隔离 (Cookie/缓存独立分区)");
            tvProfileEngineStatus.setTextColor(0xFF15803D);
        } else {
            tvProfileEngineStatus.setText("⚠️ Multi-Profile 模式：当前系统 WebView 不支持硬件隔离，降级为沙箱兼容模式");
            tvProfileEngineStatus.setTextColor(0xFFD97706);
        }

        final Runnable[] renderPagesRef = new Runnable[1];
        renderPagesRef[0] = () -> {
            layoutPageCards.removeAllViews();
            List<WebViewPool.ManagedPage> allPages = webViewPool.getAllPages();
            String activeId = webViewPool.getActiveForegroundPageId();

            int headlessCount = 0;
            for (WebViewPool.ManagedPage p : allPages) {
                if (!p.pageId.equals(activeId)) headlessCount++;
            }
            tvSubtitle.setText("共 " + allPages.size() + " 个活跃页面（1 个前台展示中，" + headlessCount + " 个后台运行中）");

            for (WebViewPool.ManagedPage page : allPages) {
                boolean isCurrent = page.pageId.equals(activeId);
                String url = page.webView != null ? page.webView.getUrl() : "";
                String title = page.webView != null ? page.webView.getTitle() : "";
                boolean isDefaultProfile = "default".equalsIgnoreCase(page.profileName);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundResource(R.drawable.bg_card_cookie);
                card.setPadding(24, 18, 24, 18);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 14);
                card.setLayoutParams(lp);

                // 标题行
                LinearLayout rowTop = new LinearLayout(this);
                rowTop.setOrientation(LinearLayout.HORIZONTAL);
                rowTop.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvTitle = new TextView(this);
                tvTitle.setText(page.pageId.equals("main") ? "主窗口 (Main)" : "任务 [" + page.pageId + "]");
                tvTitle.setTextColor(0xFF0F172A);
                tvTitle.setTextSize(14);
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                rowTop.addView(tvTitle, tLp);

                TextView tvStatusTag = new TextView(this);
                if (isCurrent) {
                    tvStatusTag.setText("● 前台展示中");
                    tvStatusTag.setTextColor(0xFF10B981);
                    tvStatusTag.setBackgroundColor(0xFFECFDF5);
                } else {
                    tvStatusTag.setText("● 后台静默运行");
                    tvStatusTag.setTextColor(0xFF6366F1);
                    tvStatusTag.setBackgroundColor(0xFFEEF2FF);
                }
                tvStatusTag.setTextSize(11);
                tvStatusTag.setPadding(12, 4, 12, 4);
                rowTop.addView(tvStatusTag);
                card.addView(rowTop);

                // Profile 数据隔离标识行
                LinearLayout rowProfile = new LinearLayout(this);
                rowProfile.setOrientation(LinearLayout.HORIZONTAL);
                rowProfile.setGravity(Gravity.CENTER_VERTICAL);
                rowProfile.setPadding(0, 4, 0, 2);

                TextView tvProfileTag = new TextView(this);
                if (isDefaultProfile) {
                    tvProfileTag.setText("📦 数据分区: Default (共享会话)");
                    tvProfileTag.setTextColor(0xFF64748B);
                    tvProfileTag.setBackgroundColor(0xFFF1F5F9);
                } else {
                    tvProfileTag.setText("🔒 独立 Profile 沙箱: " + page.profileName + " (Cookie/存储独立隔离)");
                    tvProfileTag.setTextColor(0xFF7C3AED);
                    tvProfileTag.setBackgroundColor(0xFFF5F3FF);
                }
                tvProfileTag.setTextSize(10);
                tvProfileTag.setPadding(10, 3, 10, 3);
                rowProfile.addView(tvProfileTag);
                card.addView(rowProfile);

                // 硬件参数标识行
                LinearLayout rowHardware = new LinearLayout(this);
                rowHardware.setOrientation(LinearLayout.HORIZONTAL);
                rowHardware.setGravity(Gravity.CENTER_VERTICAL);
                rowHardware.setPadding(0, 2, 0, 4);

                TextView tvHwTag = new TextView(this);
                com.example.cookiebrowser.core.HardwareConfig hw = page.hardwareConfig != null ? page.hardwareConfig : new com.example.cookiebrowser.core.HardwareConfig();
                tvHwTag.setText("💻 硬件伪装: " + hw.cpuCores + "核CPU · " + hw.deviceMemory + "G RAM · " + hw.glRenderer);
                tvHwTag.setTextColor(0xFF0284C7);
                tvHwTag.setBackgroundColor(0xFFF0F9FF);
                tvHwTag.setTextSize(10);
                tvHwTag.setPadding(10, 3, 10, 3);
                rowHardware.addView(tvHwTag);
                card.addView(rowHardware);

                // URL 行
                TextView tvUrl = new TextView(this);
                tvUrl.setText("URL: " + (TextUtils.isEmpty(url) ? "about:blank" : url));
                tvUrl.setTextColor(0xFF64748B);
                tvUrl.setTextSize(12);
                tvUrl.setMaxLines(1);
                tvUrl.setEllipsize(TextUtils.TruncateAt.END);
                tvUrl.setPadding(0, 4, 0, 10);
                card.addView(tvUrl);

                // 动作按钮栏
                LinearLayout rowActions = new LinearLayout(this);
                rowActions.setOrientation(LinearLayout.HORIZONTAL);

                if (!isCurrent) {
                    Button btnSwitchToFront = new Button(this);
                    btnSwitchToFront.setText("切换到前台显示 ↗");
                    btnSwitchToFront.setTextColor(Color.WHITE);
                    btnSwitchToFront.setTextSize(12);
                    btnSwitchToFront.setBackgroundResource(R.drawable.bg_button_export);
                    LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, 78, 1f);
                    bLp.setMarginEnd(12);
                    btnSwitchToFront.setLayoutParams(bLp);
                    btnSwitchToFront.setOnClickListener(v -> {
                        webViewPool.switchToForeground(page.pageId);
                        dialog.dismiss();
                        Toast.makeText(this, "已将页面 " + page.pageId + " (" + page.profileName + ") 切换至前台！", Toast.LENGTH_SHORT).show();
                    });
                    rowActions.addView(btnSwitchToFront);

                    Button btnClose = new Button(this);
                    btnClose.setText("关闭任务");
                    btnClose.setTextColor(0xFFEF4444);
                    btnClose.setTextSize(12);
                    btnClose.setBackgroundResource(R.drawable.bg_address_bar);
                    LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 78);
                    btnClose.setLayoutParams(cLp);
                    btnClose.setOnClickListener(v -> {
                        webViewPool.destroyPage(page.pageId);
                        updatePagesBadge();
                        if (renderPagesRef[0] != null) renderPagesRef[0].run();
                        Toast.makeText(this, "任务已关闭", Toast.LENGTH_SHORT).show();
                    });
                    rowActions.addView(btnClose);
                } else {
                    TextView tvHint = new TextView(this);
                    tvHint.setText("当前正在全屏浏览该页面 (" + (isDefaultProfile ? "Default" : page.profileName) + ")");
                    tvHint.setTextColor(0xFF94A3B8);
                    tvHint.setTextSize(11);
                    rowActions.addView(tvHint);
                }

                card.addView(rowActions);
                layoutPageCards.addView(card);
            }
        };

        renderPagesRef[0].run();

        // 点击快速新建默认 Profile 任务
        btnCreateSample.setOnClickListener(v -> {
            WebViewPool.ManagedPage newPage = webViewPool.createHeadlessPage(null, "default");
            newPage.webView.loadUrl("https://m.baidu.com");
            updatePagesBadge();
            if (renderPagesRef[0] != null) renderPagesRef[0].run();
            Toast.makeText(this, "已启动默认会话后台任务: " + newPage.pageId, Toast.LENGTH_SHORT).show();
        });

        // 点击新建独立 Profile 沙箱任务 (支持自定义 CPU 核心数与内存大小)
        btnCreateProfile.setOnClickListener(v -> {
            int nextIdx = webViewPool.getPageCount();
            String defaultNewProfile = "sandbox_" + nextIdx;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("新建独立 Profile 与硬件指纹任务");

            LinearLayout form = new LinearLayout(this);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(48, 16, 48, 8);

            TextView tvProfileLbl = new TextView(this);
            tvProfileLbl.setText("Profile 分区名称:");
            tvProfileLbl.setTextSize(12);
            tvProfileLbl.setTextColor(0xFF475569);
            form.addView(tvProfileLbl);

            final EditText inputProfile = new EditText(this);
            inputProfile.setHint("如 " + defaultNewProfile);
            inputProfile.setText(defaultNewProfile);
            inputProfile.setSingleLine(true);
            form.addView(inputProfile);

            TextView tvCpuLbl = new TextView(this);
            tvCpuLbl.setText("CPU 核心数 (hardwareConcurrency):");
            tvCpuLbl.setTextSize(12);
            tvCpuLbl.setTextColor(0xFF475569);
            tvCpuLbl.setPadding(0, 16, 0, 0);
            form.addView(tvCpuLbl);

            final EditText inputCpu = new EditText(this);
            inputCpu.setHint("默认 8 核 (如 4, 8, 12, 16)");
            inputCpu.setText("8");
            inputCpu.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            inputCpu.setSingleLine(true);
            form.addView(inputCpu);

            TextView tvMemLbl = new TextView(this);
            tvMemLbl.setText("内存大小 GB (deviceMemory):");
            tvMemLbl.setTextSize(12);
            tvMemLbl.setTextColor(0xFF475569);
            tvMemLbl.setPadding(0, 16, 0, 0);
            form.addView(tvMemLbl);

            final EditText inputMem = new EditText(this);
            inputMem.setHint("默认 16 GB (如 4, 8, 16, 32)");
            inputMem.setText("16");
            inputMem.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            inputMem.setSingleLine(true);
            form.addView(inputMem);

            TextView tvUrlLbl = new TextView(this);
            tvUrlLbl.setText("初始网址 URL (支持自定义):");
            tvUrlLbl.setTextSize(12);
            tvUrlLbl.setTextColor(0xFF475569);
            tvUrlLbl.setPadding(0, 16, 0, 0);
            form.addView(tvUrlLbl);

            final EditText inputUrl = new EditText(this);
            inputUrl.setHint("如 https://creator.douyin.com 或 https://m.baidu.com");
            inputUrl.setText("https://m.baidu.com");
            inputUrl.setSingleLine(true);
            form.addView(inputUrl);

            builder.setView(form);

            builder.setPositiveButton("立即创建并注入", (d, w) -> {
                String name = inputProfile.getText().toString().trim();
                if (TextUtils.isEmpty(name)) name = defaultNewProfile;

                int cpu = 8;
                try {
                    cpu = Integer.parseInt(inputCpu.getText().toString().trim());
                } catch (Exception ignored) {}

                int mem = 16;
                try {
                    mem = Integer.parseInt(inputMem.getText().toString().trim());
                } catch (Exception ignored) {}

                String customUrl = inputUrl.getText().toString().trim();
                if (TextUtils.isEmpty(customUrl)) {
                    customUrl = "https://m.baidu.com";
                }

                com.example.cookiebrowser.core.HardwareConfig customHw = new com.example.cookiebrowser.core.HardwareConfig();
                customHw.cpuCores = cpu;
                customHw.deviceMemory = mem;

                WebViewPool.ManagedPage newPage = webViewPool.createHeadlessPage(null, name, customHw, customUrl);
                updatePagesBadge();
                if (renderPagesRef[0] != null) renderPagesRef[0].run();
                Toast.makeText(this, "已创建 [" + name + "] (加载: " + customUrl + " | " + cpu + "核/" + mem + "G)！", Toast.LENGTH_LONG).show();
            });
            builder.setNegativeButton("取消", null);
            builder.show();
        });

        ivClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * 弹出 Cookie 管理中心
     */
    private void showCookieHubDialog() {
        WebView active = getActiveWebView();
        String currentUrl = active != null ? active.getUrl() : "";
        if (TextUtils.isEmpty(currentUrl)) {
            Toast.makeText(this, "当前无加载的网页", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_cookie_hub);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
        }

        TextView tvSubtitle = dialog.findViewById(R.id.tv_hub_subtitle);
        TextView tvSummary = dialog.findViewById(R.id.tv_cookie_summary);
        ImageView ivClose = dialog.findViewById(R.id.iv_close_dialog);

        TextView tabInspect = dialog.findViewById(R.id.tab_inspect);
        TextView tabExport = dialog.findViewById(R.id.tab_export);
        TextView tabImport = dialog.findViewById(R.id.tab_import);

        LinearLayout viewInspect = dialog.findViewById(R.id.view_inspect_container);
        LinearLayout viewExport = dialog.findViewById(R.id.view_export_container);
        LinearLayout viewImport = dialog.findViewById(R.id.view_import_container);
        LinearLayout layoutCookieItems = dialog.findViewById(R.id.layout_cookie_items);

        Button btnFmtStandard = dialog.findViewById(R.id.btn_fmt_standard);
        Button btnFmtJson = dialog.findViewById(R.id.btn_fmt_json);
        EditText etExportCode = dialog.findViewById(R.id.et_export_code);
        Button btnCopyExport = dialog.findViewById(R.id.btn_copy_export);

        EditText etImportInput = dialog.findViewById(R.id.et_import_input);
        Button btnPaste = dialog.findViewById(R.id.btn_paste_from_clipboard);
        Button btnConfirmImport = dialog.findViewById(R.id.btn_confirm_import);
        TextView btnClearAll = dialog.findViewById(R.id.btn_clear_all_cookies);

        WebViewPool.ManagedPage activePage = webViewPool.getActiveForegroundPage();
        String currentProfile = activePage != null ? activePage.profileName : "default";
        CookieManager cm = activePage != null ? webViewPool.getCookieManager(activePage.profileName) : CookieManager.getInstance();

        String host = "";
        try {
            Uri uri = Uri.parse(currentUrl);
            host = uri.getHost();
        } catch (Exception ignored) {}
        tvSubtitle.setText("站点: " + (TextUtils.isEmpty(host) ? currentUrl : host) + "  |  Profile: " + currentProfile);

        String standardCookies = CookieHelper.exportCookiesAsStandardString(this, cm, currentProfile, currentUrl);
        String jsonCookies = CookieHelper.exportCookiesAsJson(this, cm, currentProfile, currentUrl);
        etExportCode.setText(standardCookies);

        List<CookieHelper.CookieItem> parsedCookies = CookieHelper.readCookiesWithDetails(this, cm, currentProfile, currentUrl, false);
        tvSummary.setText("共加载 " + parsedCookies.size() + " 项 Cookie (" + currentProfile + ")");

        layoutCookieItems.removeAllViews();
        if (parsedCookies.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("当前页面暂无 Cookie 记录");
            tvEmpty.setTextColor(0xFF94A3B8);
            tvEmpty.setPadding(20, 40, 20, 40);
            tvEmpty.setGravity(Gravity.CENTER);
            layoutCookieItems.addView(tvEmpty);
        } else {
            for (CookieHelper.CookieItem item : parsedCookies) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundResource(R.drawable.bg_card_cookie);
                card.setPadding(28, 20, 28, 20);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 16);
                card.setLayoutParams(lp);

                LinearLayout rowHeader = new LinearLayout(this);
                rowHeader.setOrientation(LinearLayout.HORIZONTAL);
                rowHeader.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvName = new TextView(this);
                tvName.setText(item.name);
                tvName.setTextColor(0xFF4F46E5);
                tvName.setTextSize(14);
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                rowHeader.addView(tvName);

                if (item.httpOnly) {
                    TextView tvHttpOnly = new TextView(this);
                    tvHttpOnly.setText(" HttpOnly ");
                    tvHttpOnly.setTextSize(10);
                    tvHttpOnly.setTextColor(0xFFFFFFFF);
                    tvHttpOnly.setBackgroundColor(0xFF0284C7);
                    LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    badgeLp.setMargins(16, 0, 0, 0);
                    tvHttpOnly.setLayoutParams(badgeLp);
                    rowHeader.addView(tvHttpOnly);
                }

                if (item.secure) {
                    TextView tvSecure = new TextView(this);
                    tvSecure.setText(" Secure ");
                    tvSecure.setTextSize(10);
                    tvSecure.setTextColor(0xFFFFFFFF);
                    tvSecure.setBackgroundColor(0xFF059669);
                    LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    badgeLp.setMargins(8, 0, 0, 0);
                    tvSecure.setLayoutParams(badgeLp);
                    rowHeader.addView(tvSecure);
                }

                card.addView(rowHeader);

                TextView tvDomain = new TextView(this);
                tvDomain.setText("Domain: " + item.domain + " | Path: " + item.path);
                tvDomain.setTextColor(0xFF64748B);
                tvDomain.setTextSize(11);
                tvDomain.setPadding(0, 4, 0, 2);
                card.addView(tvDomain);

                TextView tvVal = new TextView(this);
                tvVal.setText(item.value);
                tvVal.setTextColor(0xFF1E293B);
                tvVal.setTextSize(12);
                tvVal.setMaxLines(2);
                tvVal.setEllipsize(TextUtils.TruncateAt.END);
                tvVal.setPadding(0, 4, 0, 6);
                card.addView(tvVal);

                card.setOnClickListener(v -> {
                    copyToClipboard(item.name + "=" + item.value);
                    Toast.makeText(this, "已复制: " + item.name, Toast.LENGTH_SHORT).show();
                });

                layoutCookieItems.addView(card);
            }
        }

        View.OnClickListener tabSwitcher = v -> {
            tabInspect.setBackgroundColor(Color.TRANSPARENT);
            tabExport.setBackgroundColor(Color.TRANSPARENT);
            tabImport.setBackgroundColor(Color.TRANSPARENT);
            tabInspect.setTextColor(0xFF64748B);
            tabExport.setTextColor(0xFF64748B);
            tabImport.setTextColor(0xFF64748B);

            viewInspect.setVisibility(View.GONE);
            viewExport.setVisibility(View.GONE);
            viewImport.setVisibility(View.GONE);

            if (v == tabInspect) {
                tabInspect.setBackgroundColor(Color.WHITE);
                tabInspect.setTextColor(0xFF4F46E5);
                viewInspect.setVisibility(View.VISIBLE);
            } else if (v == tabExport) {
                tabExport.setBackgroundColor(Color.WHITE);
                tabExport.setTextColor(0xFF4F46E5);
                viewExport.setVisibility(View.VISIBLE);
            } else if (v == tabImport) {
                tabImport.setBackgroundColor(Color.WHITE);
                tabImport.setTextColor(0xFF4F46E5);
                viewImport.setVisibility(View.VISIBLE);
            }
        };

        tabInspect.setOnClickListener(tabSwitcher);
        tabExport.setOnClickListener(tabSwitcher);
        tabImport.setOnClickListener(tabSwitcher);

        btnFmtStandard.setOnClickListener(v -> {
            btnFmtStandard.setBackgroundResource(R.drawable.bg_button_export);
            btnFmtStandard.setTextColor(Color.WHITE);
            btnFmtJson.setBackgroundResource(R.drawable.bg_address_bar);
            btnFmtJson.setTextColor(0xFF0F172A);
            etExportCode.setText(standardCookies);
        });

        btnFmtJson.setOnClickListener(v -> {
            btnFmtJson.setBackgroundResource(R.drawable.bg_button_export);
            btnFmtJson.setTextColor(Color.WHITE);
            btnFmtStandard.setBackgroundResource(R.drawable.bg_address_bar);
            btnFmtStandard.setTextColor(0xFF0F172A);
            etExportCode.setText(jsonCookies);
        });

        btnCopyExport.setOnClickListener(v -> {
            String text = etExportCode.getText().toString();
            copyToClipboard(text);
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });

        btnPaste.setOnClickListener(v -> {
            String clip = getClipboardText();
            if (!TextUtils.isEmpty(clip)) {
                etImportInput.setText(clip);
                Toast.makeText(this, "已从剪贴板粘贴", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            }
        });

        btnConfirmImport.setOnClickListener(v -> {
            String input = etImportInput.getText().toString().trim();
            if (TextUtils.isEmpty(input)) {
                Toast.makeText(this, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            int count = CookieHelper.importCookies(cm, currentUrl, input);
            if (count > 0) {
                dialog.dismiss();
                updateCookieBadge();
                new AlertDialog.Builder(this)
                        .setTitle("注入成功")
                        .setMessage("已成功写入 " + count + " 项 Cookie 到 Profile [" + currentProfile + "]！\n是否立即刷新页面以使其生效？")
                        .setPositiveButton("立即刷新", (d, w) -> {
                            WebView a = getActiveWebView();
                            if (a != null) a.reload();
                        })
                        .setNegativeButton("稍后", null)
                        .show();
            } else {
                Toast.makeText(this, "未识别到有效的 Cookie 数据格式", Toast.LENGTH_LONG).show();
            }
        });

        btnClearAll.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("确认清空")
                    .setMessage("确定要清空 Profile [" + currentProfile + "] 的所有 Cookie 吗？")
                    .setPositiveButton("清空", (d, w) -> {
                        cm.removeAllCookies(val -> {
                            cm.flush();
                            dialog.dismiss();
                            updateCookieBadge();
                            WebView a = getActiveWebView();
                            if (a != null) a.reload();
                            Toast.makeText(this, "Profile [" + currentProfile + "] Cookie 已清空", Toast.LENGTH_SHORT).show();
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        ivClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /**
     * 弹出安全沙箱与白名单管理面板
     */
    private void showSecurityWhitelistDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_security_whitelist);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
        }

        TextView tvCurrentOrigin = dialog.findViewById(R.id.tv_current_origin);
        TextView tvTierBadge = dialog.findViewById(R.id.tv_tier_badge);
        TextView tvTierDesc = dialog.findViewById(R.id.tv_tier_desc);
        EditText etNewDomain = dialog.findViewById(R.id.et_new_domain);
        Button btnAddDomain = dialog.findViewById(R.id.btn_add_domain);
        LinearLayout layoutWhitelistRules = dialog.findViewById(R.id.layout_whitelist_rules);
        ImageView ivClose = dialog.findViewById(R.id.iv_close_whitelist);

        WebView active = getActiveWebView();
        String currentUrl = active != null ? active.getUrl() : "";
        tvCurrentOrigin.setText("当前站点: " + (currentUrl != null ? currentUrl : "无"));

        boolean isCore = DomainWhitelistManager.getInstance().checkPermission(currentUrl, DomainWhitelistManager.TIER_2_CORE);
        boolean isBusiness = DomainWhitelistManager.getInstance().checkPermission(currentUrl, DomainWhitelistManager.TIER_1_BUSINESS);

        if (isCore) {
            tvTierBadge.setText("Tier 2 (核心特权)");
            tvTierBadge.setTextColor(0xFF047857);
            tvTierDesc.setText("当前站点属于核心受信白名单，已开放 Cookie、无头任务与网络穿透权限。");
        } else if (isBusiness) {
            tvTierBadge.setText("Tier 1 (业务受信)");
            tvTierBadge.setTextColor(0xFF2563EB);
            tvTierDesc.setText("当前站点属于业务白名单，已开放基础存储、DOM 读写与系统设备权限。");
        } else {
            tvTierBadge.setText("Tier 0 (公开沙箱)");
            tvTierBadge.setTextColor(0xFF9A3412);
            tvTierDesc.setText("当前站点未在白名单中，高危特权 API 均已被隔离拦截，保证终端安全。");
        }

        Runnable renderRules = () -> {
            layoutWhitelistRules.removeAllViews();
            Set<String> rules = DomainWhitelistManager.getInstance().getTier2Whitelist();
            for (String rule : rules) {
                TextView tvRule = new TextView(this);
                tvRule.setText("• " + rule);
                tvRule.setTextColor(0xFF334155);
                tvRule.setTextSize(13);
                tvRule.setPadding(12, 10, 12, 10);
                layoutWhitelistRules.addView(tvRule);
            }
        };
        renderRules.run();

        btnAddDomain.setOnClickListener(v -> {
            String newDomain = etNewDomain.getText().toString().trim();
            if (!TextUtils.isEmpty(newDomain)) {
                DomainWhitelistManager.getInstance().addTier2Domain(newDomain);
                DomainWhitelistManager.getInstance().addTier1Domain(newDomain);
                etNewDomain.setText("");
                renderRules.run();
                updateSecurityIndicator(getActiveWebView().getUrl());
                Toast.makeText(this, "成功添加白名单规则: " + newDomain, Toast.LENGTH_SHORT).show();
            }
        });

        ivClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText("Cookie", text);
            cm.setPrimaryClip(clip);
        }
    }

    private String getClipboardText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
            return text != null ? text.toString() : "";
        }
        return "";
    }

    @Override
    public void onBackPressed() {
        WebView active = getActiveWebView();
        if (active != null && active.canGoBack()) {
            active.goBack();
        } else if (!webViewPool.getActiveForegroundPageId().equals("main")) {
            // 如果是在浏览后台页面，按返回键先切回主页
            webViewPool.switchToForeground("main");
            Toast.makeText(this, "已返回主页面", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        com.example.cookiebrowser.core.DouyinQrLoginManager.getInstance().stopMonitoring();
        if (bridgeEngine != null) {
            bridgeEngine.destroy();
        }
        if (webViewPool != null) {
            webViewPool.destroyAll();
        }
        super.onDestroy();
    }
}
