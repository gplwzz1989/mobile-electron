package com.example.cookiebrowser.core;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.cookiebrowser.CookieHelper;
import com.example.cookiebrowser.MainActivity;
import com.example.cookiebrowser.R;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 抖音创作者中心 (creator.douyin.com) 后台隐形 WebView 登录与二维码双向状态监控管理器
 */
public class DouyinQrLoginManager {

    private static final String TAG = "DouyinQrLogin";
    private static final String DOUYIN_CREATOR_URL = "https://creator.douyin.com/";
    private static final String DOUYIN_PASSPORT_URL = "https://passport.douyin.com/";
    private static final String DOUYIN_PROFILE = "douyin_creator";

    // 桌面版 Chrome UA，强制触发创作者中心桌面端扫码登录界面
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    // 注入页面实时提取二维码及侦测状态的 JavaScript 脚本
    private static final String QR_DETECTION_SCRIPT =
            "(function() {\n" +
            "  try {\n" +
            "    var res = {\n" +
            "      status: 'PENDING',\n" +
            "      qrCode: '',\n" +
            "      statusText: '',\n" +
            "      url: window.location.href,\n" +
            "      title: document.title\n" +
            "    };\n" +
            "    // 1. 登录成功检测\n" +
            "    if (window.location.pathname.indexOf('/creator-micro') !== -1 ||\n" +
            "        window.location.pathname.indexOf('/home') !== -1 ||\n" +
            "        window.location.pathname.indexOf('/content') !== -1 ||\n" +
            "        document.querySelector('.header-user-info') ||\n" +
            "        document.querySelector('.semi-avatar') ||\n" +
            "        document.querySelector('[class*=\"avatar\"]')) {\n" +
            "      res.status = 'LOGGED_IN';\n" +
            "      res.statusText = '已登录成功';\n" +
            "      return JSON.stringify(res);\n" +
            "    }\n" +
            "    // 2. 准确定位二维码图片 (排除全屏背景粒子 Canvas)\n" +
            "    var qrImg = document.querySelector('img[aria-label*=\"二维码\"], img[alt*=\"二维码\"]');\n" +
            "    if (!qrImg) {\n" +
            "      var imgs = document.querySelectorAll('img');\n" +
            "      for (var i = 0; i < imgs.length; i++) {\n" +
            "        var img = imgs[i];\n" +
            "        if (img.src && img.src.indexOf('data:image') === 0) {\n" +
            "          var nw = img.naturalWidth || img.clientWidth;\n" +
            "          var nh = img.naturalHeight || img.clientHeight;\n" +
            "          if (nw >= 100 && nh >= 100 && nw <= 600 && nh <= 600 && Math.abs(nw - nh) <= 30) {\n" +
            "            qrImg = img;\n" +
            "            break;\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "    var qrCanvas = null;\n" +
            "    if (!qrImg) {\n" +
            "      var canvases = document.querySelectorAll('canvas');\n" +
            "      for (var j = 0; j < canvases.length; j++) {\n" +
            "        var c = canvases[j];\n" +
            "        var cw = c.width || c.clientWidth;\n" +
            "        var ch = c.height || c.clientHeight;\n" +
            "        if (cw >= 100 && ch >= 100 && cw <= 600 && ch <= 600 && Math.abs(cw - ch) <= 20) {\n" +
            "          qrCanvas = c;\n" +
            "          break;\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "    if (qrImg && qrImg.src) {\n" +
            "      res.qrCode = qrImg.src;\n" +
            "    } else if (qrCanvas) {\n" +
            "      try { res.qrCode = qrCanvas.toDataURL('image/png'); } catch(e){}\n" +
            "    }\n" +
            "    // 3. 状态检测\n" +
            "    var pageText = document.body ? document.body.innerText : '';\n" +
            "    var qrBox = qrImg ? (qrImg.closest('[class*=\"wrap\"], [class*=\"container\"], [class*=\"login\"]') || qrImg.parentElement) : null;\n" +
            "    var boxText = qrBox ? (qrBox.innerText || '') : '';\n" +
            "    if (pageText.indexOf('二维码已失效') !== -1 || pageText.indexOf('二维码已过期') !== -1 ||\n" +
            "        boxText.indexOf('已失效') !== -1 || boxText.indexOf('点击刷新') !== -1 || boxText.indexOf('已过期') !== -1) {\n" +
            "      res.status = 'EXPIRED';\n" +
            "      res.statusText = '二维码已失效，请点击刷新';\n" +
            "    } else if (pageText.indexOf('扫码成功') !== -1 || pageText.indexOf('请在手机上确认') !== -1 ||\n" +
            "               pageText.indexOf('已扫码') !== -1 || boxText.indexOf('已扫码') !== -1 || boxText.indexOf('确认') !== -1) {\n" +
            "      res.status = 'SCANNED';\n" +
            "      res.statusText = '已扫码，请在手机上点击确认';\n" +
            "    } else if (res.qrCode) {\n" +
            "      res.status = 'READY';\n" +
            "      res.statusText = '请使用抖音 App 扫码登录';\n" +
            "    }\n" +
            "    return JSON.stringify(res);\n" +
            "  } catch(err) {\n" +
            "    return JSON.stringify({ status: 'ERROR', error: err.toString() });\n" +
            "  }\n" +
            "})();";

    private static DouyinQrLoginManager instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isMonitoring = false;
    private String lastQrCodeHash = "";
    private WebViewPool.ManagedPage activeManagedPage;
    private Dialog activeDialog;

    private DouyinQrLoginManager() {}

    public static synchronized DouyinQrLoginManager getInstance() {
        if (instance == null) {
            instance = new DouyinQrLoginManager();
        }
        return instance;
    }

    /**
     * 启动抖音创作者中心隐形 WebView 登录与弹窗监控全流程
     */
    public synchronized void start(MainActivity activity, WebViewPool pool) {
        if (activity == null || pool == null) return;

        // 停止之前的监控任务并关闭之前的弹窗
        stopMonitoring();
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
        }

        // 1. 在后台创建完全不可见的 Headless WebView (使用专属 Profile douyin_creator 与桌面硬件伪装)
        HardwareConfig desktopHw = HardwareConfig.createDesktop();
        activeManagedPage = pool.createHeadlessPage(DESKTOP_UA, DOUYIN_PROFILE, desktopHw, DOUYIN_CREATOR_URL);
        final String pageId = activeManagedPage.pageId;
        final WebView targetWebView = activeManagedPage.webView;

        Log.i(TAG, "Spawned headless WebView [" + pageId + "] loading " + DOUYIN_CREATOR_URL);

        // 2. 弹出二维码监控原生高保真 Material 3 对话框
        Dialog dialog = new Dialog(activity);
        activeDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_qr_login);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
        }

        // 绑定弹窗组件
        ImageView ivClose = dialog.findViewById(R.id.iv_close_douyin_dialog);
        TextView tvStatusBadge = dialog.findViewById(R.id.tv_qr_status_badge);
        ProgressBar pbLoading = dialog.findViewById(R.id.pb_qr_loading);
        ImageView ivQrCode = dialog.findViewById(R.id.iv_douyin_qr_code);
        TextView tvQrHint = dialog.findViewById(R.id.tv_qr_hint);
        LinearLayout layoutSuccessCookie = dialog.findViewById(R.id.layout_success_cookie);
        TextView tvCookiePreview = dialog.findViewById(R.id.tv_cookie_preview);
        Button btnRefresh = dialog.findViewById(R.id.btn_refresh_douyin_qr);
        Button btnSwitchFront = dialog.findViewById(R.id.btn_switch_douyin_front);
        Button btnCopyCookie = dialog.findViewById(R.id.btn_copy_douyin_cookie);

        // 3. 按钮交互逻辑
        ivClose.setOnClickListener(v -> dialog.dismiss());

        final String[] currentStatus = new String[]{"PENDING"};

        // 刷新二维码逻辑 (支持点击按钮或点击失效的二维码本身触发刷新)
        final Runnable doRefresh = () -> {
            tvStatusBadge.setText("● 正在刷新二维码...");
            tvStatusBadge.setBackgroundColor(0xFFFEF3C7);
            tvStatusBadge.setTextColor(0xFFD97706);
            pbLoading.setVisibility(View.VISIBLE);
            ivQrCode.setAlpha(0.4f);
            lastQrCodeHash = "";

            if (targetWebView != null) {
                targetWebView.evaluateJavascript(
                        "(function() {\n" +
                        "  var qrImg = document.querySelector('img[aria-label*=\"二维码\"], img[alt*=\"二维码\"]');\n" +
                        "  if (qrImg) {\n" +
                        "    if (qrImg.parentElement) qrImg.parentElement.click();\n" +
                        "    qrImg.click();\n" +
                        "  }\n" +
                        "  var btns = document.querySelectorAll('[class*=\"refresh\"], [class*=\"reload\"], [class*=\"mask\"], [class*=\"expired\"], [class*=\"retry\"]');\n" +
                        "  for (var i = 0; i < btns.length; i++) {\n" +
                        "    var b = btns[i];\n" +
                        "    if (b.innerText && (b.innerText.indexOf('刷新') !== -1 || b.innerText.indexOf('重新') !== -1 || b.innerText.indexOf('失效') !== -1)) {\n" +
                        "      b.click(); return 'clicked';\n" +
                        "    }\n" +
                        "  }\n" +
                        "  location.reload();\n" +
                        "  return 'reloaded';\n" +
                        "})();", null);
            }
        };

        btnRefresh.setOnClickListener(v -> doRefresh.run());
        ivQrCode.setOnClickListener(v -> {
            if ("EXPIRED".equals(currentStatus[0])) {
                doRefresh.run();
            }
        });

        // 将隐形 WebView 一键切换至前台全屏展示
        btnSwitchFront.setOnClickListener(v -> {
            pool.switchToForeground(pageId);
            dialog.dismiss();
            Toast.makeText(activity, "已将抖音创作者平台切换至前台展示！", Toast.LENGTH_SHORT).show();
        });

        // 复制捕获的登录 Cookie
        btnCopyCookie.setOnClickListener(v -> {
            CookieManager cm = pool.getCookieManager(DOUYIN_PROFILE);
            String exportCookies = CookieHelper.exportCookiesAsStandardString(cm, DOUYIN_CREATOR_URL);
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && !TextUtils.isEmpty(exportCookies)) {
                ClipData clip = ClipData.newPlainText("DouyinCookie", exportCookies);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(activity, "已成功复制创作者平台登录 Cookie 📋", Toast.LENGTH_SHORT).show();
            }
        });

        // 弹窗关闭时自动注销轮询定时器
        dialog.setOnDismissListener(d -> {
            isMonitoring = false;
            mainHandler.removeCallbacksAndMessages(null);
            Log.d(TAG, "QR monitor dialog dismissed, polling halted.");
        });

        dialog.show();

        // 4. 启动周期性状态监控 (每 800ms 探测一次隐形页面的 DOM 与 Cookie)
        isMonitoring = true;
        lastQrCodeHash = "";
        startMonitoringLoop(activity, pool, targetWebView, tvStatusBadge, pbLoading, ivQrCode, tvQrHint, layoutSuccessCookie, tvCookiePreview, btnCopyCookie, currentStatus);
    }

    private void startMonitoringLoop(MainActivity activity, WebViewPool pool, WebView webView,
                                     TextView tvStatusBadge, ProgressBar pbLoading, ImageView ivQrCode,
                                     TextView tvQrHint, LinearLayout layoutSuccessCookie,
                                     TextView tvCookiePreview, Button btnCopyCookie,
                                     String[] currentStatusRef) {
        if (!isMonitoring || webView == null || activeDialog == null || !activeDialog.isShowing()) {
            return;
        }

        // 1. 优先检测 CookieManager 是否已截获 sessionid 登录令牌
        CookieManager cm = pool.getCookieManager(DOUYIN_PROFILE);
        String cookiesCreator = cm.getCookie(DOUYIN_CREATOR_URL);
        String cookiesPassport = cm.getCookie(DOUYIN_PASSPORT_URL);

        boolean isLoggedIn = (cookiesCreator != null && (cookiesCreator.contains("sessionid=") || cookiesCreator.contains("sid_guard="))) ||
                             (cookiesPassport != null && (cookiesPassport.contains("sessionid=") || cookiesPassport.contains("sid_guard=")));

        if (isLoggedIn) {
            currentStatusRef[0] = "LOGGED_IN";
            onLoginSuccess(activity, cm, tvStatusBadge, pbLoading, tvQrHint, layoutSuccessCookie, tvCookiePreview, btnCopyCookie);
            return;
        }

        // 2. 检查隐形页面的当前 URL
        String currentUrl = webView.getUrl();
        if (currentUrl != null && (currentUrl.contains("/creator-micro/") || currentUrl.contains("/home") || currentUrl.contains("/content"))) {
            currentStatusRef[0] = "LOGGED_IN";
            onLoginSuccess(activity, cm, tvStatusBadge, pbLoading, tvQrHint, layoutSuccessCookie, tvCookiePreview, btnCopyCookie);
            return;
        }

        // 3. 执行页面 JS DOM 侦测提取
        webView.evaluateJavascript(QR_DETECTION_SCRIPT, value -> {
            if (!isMonitoring || activeDialog == null || !activeDialog.isShowing()) {
                return;
            }

            try {
                if (value != null && !value.equals("null") && !value.equals("\"\"")) {
                    // 去除 evaluateJavascript 返回的外层引号包装
                    String jsonStr = value;
                    if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                        try {
                            jsonStr = new org.json.JSONTokener(jsonStr).nextValue().toString();
                        } catch (Exception e) {
                            jsonStr = unescapeJsJsonString(jsonStr);
                        }
                    }

                    JSONObject res = new JSONObject(jsonStr);
                    String status = res.optString("status", "PENDING");
                    String qrCode = res.optString("qrCode", "");
                    String statusText = res.optString("statusText", "");
                    currentStatusRef[0] = status;

                    // 状态同步到弹窗徽标
                    switch (status) {
                        case "LOGGED_IN":
                            onLoginSuccess(activity, cm, tvStatusBadge, pbLoading, tvQrHint, layoutSuccessCookie, tvCookiePreview, btnCopyCookie);
                            return;

                        case "SCANNED":
                            tvStatusBadge.setText("● 手机已扫码，请在抖音 App 上确认登录");
                            tvStatusBadge.setBackgroundColor(0xFFE0E7FF);
                            tvStatusBadge.setTextColor(0xFF4338CA);
                            tvQrHint.setText("📱 已侦测到手机扫码，请在手机端点击【确认登录】");
                            ivQrCode.setAlpha(0.85f);
                            break;

                        case "EXPIRED":
                            tvStatusBadge.setText("● 二维码已失效，请点击下方刷新");
                            tvStatusBadge.setBackgroundColor(0xFFFEE2E2);
                            tvStatusBadge.setTextColor(0xFFDC2626);
                            tvQrHint.setText("⚠️ 二维码已过期，请点击二维码或【刷新二维码】重新生成");
                            ivQrCode.setAlpha(0.35f);
                            break;

                        case "READY":
                        default:
                            if (!TextUtils.isEmpty(qrCode)) {
                                tvStatusBadge.setText("● 二维码就绪，等待手机扫码");
                                tvStatusBadge.setBackgroundColor(0xFFFEF3C7);
                                tvStatusBadge.setTextColor(0xFFD97706);
                                tvQrHint.setText("请打开手机【抖音 App】 ➔ 点击【我】 ➔ 右上角【扫一扫】");
                                ivQrCode.setAlpha(1.0f);
                            }
                            break;
                    }

                    // 二维码图片同步
                    if (!TextUtils.isEmpty(qrCode)) {
                        String currentHash = String.valueOf(qrCode.hashCode());
                        if (!currentHash.equals(lastQrCodeHash)) {
                            lastQrCodeHash = currentHash;
                            renderQrImage(activity, qrCode, ivQrCode, pbLoading);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing QR detection response: " + e.getMessage());
            }

            // 计划下一次探测
            if (isMonitoring) {
                mainHandler.postDelayed(() -> startMonitoringLoop(
                        activity, pool, webView, tvStatusBadge, pbLoading, ivQrCode, tvQrHint, layoutSuccessCookie, tvCookiePreview, btnCopyCookie, currentStatusRef
                ), 800);
            }
        });
    }

    private void onLoginSuccess(MainActivity activity, CookieManager cm, TextView tvStatusBadge,
                               ProgressBar pbLoading, TextView tvQrHint, LinearLayout layoutSuccessCookie,
                               TextView tvCookiePreview, Button btnCopyCookie) {
        isMonitoring = false;
        mainHandler.removeCallbacksAndMessages(null);

        tvStatusBadge.setText("🎉 抖音创作者中心登录成功！");
        tvStatusBadge.setBackgroundColor(0xFFDCFCE7);
        tvStatusBadge.setTextColor(0xFF15803D);

        pbLoading.setVisibility(View.GONE);
        tvQrHint.setText("🎉 登录态 Cookie 已成功截获并隔离保存在沙箱中，可切换至前台展示或直接复制");

        String fullCookies = CookieHelper.exportCookiesAsStandardString(cm, DOUYIN_CREATOR_URL);
        if (TextUtils.isEmpty(fullCookies)) {
            fullCookies = CookieHelper.exportCookiesAsStandardString(cm, DOUYIN_PASSPORT_URL);
        }

        layoutSuccessCookie.setVisibility(View.VISIBLE);
        tvCookiePreview.setText(!TextUtils.isEmpty(fullCookies) ? fullCookies : "Cookie 已存入独立分区 [douyin_creator]");
        btnCopyCookie.setVisibility(View.VISIBLE);

        Toast.makeText(activity, "🎉 抖音创作者平台扫码登录成功！", Toast.LENGTH_LONG).show();
        Log.i(TAG, "Douyin login successfully captured for profile [" + DOUYIN_PROFILE + "]!");
    }

    private void renderQrImage(MainActivity activity, String qrData, ImageView ivQrCode, ProgressBar pbLoading) {
        if (qrData.startsWith("data:image/")) {
            int comma = qrData.indexOf(",");
            if (comma > 0) {
                try {
                    String base64Str = qrData.substring(comma + 1);
                    byte[] decoded = Base64.decode(base64Str, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                    if (bitmap != null) {
                        ivQrCode.setImageBitmap(bitmap);
                        ivQrCode.setVisibility(View.VISIBLE);
                        pbLoading.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to decode base64 QR: " + e.getMessage());
                }
            }
        } else if (qrData.startsWith("http://") || qrData.startsWith("https://")) {
            new Thread(() -> {
                try {
                    URL url = new URL(qrData);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    conn.connect();
                    InputStream is = conn.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap != null) {
                        activity.runOnUiThread(() -> {
                            ivQrCode.setImageBitmap(bitmap);
                            ivQrCode.setVisibility(View.VISIBLE);
                            pbLoading.setVisibility(View.GONE);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to fetch QR image from url: " + e.getMessage());
                }
            }).start();
        }
    }

    private String unescapeJsJsonString(String str) {
        if (str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length() - 1);
        }
        return str.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r");
    }

    public synchronized void stopMonitoring() {
        isMonitoring = false;
        mainHandler.removeCallbacksAndMessages(null);
    }
}
