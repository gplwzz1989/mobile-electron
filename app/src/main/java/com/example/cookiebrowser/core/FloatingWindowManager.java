package com.example.cookiebrowser.core;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 移动端画中画 / 悬浮多窗口管理器 (In-App Floating Window Overlay)
 * 支持原生拖拽、边缘吸附、折叠为浮标、多窗口通信与独立 WebView 实例
 */
public class FloatingWindowManager {

    private static volatile FloatingWindowManager instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, FloatingWindowHolder> activeWindows = new HashMap<>();

    private FloatingWindowManager() {}

    public static FloatingWindowManager getInstance() {
        if (instance == null) {
            synchronized (FloatingWindowManager.class) {
                if (instance == null) {
                    instance = new FloatingWindowManager();
                }
            }
        }
        return instance;
    }

    public static class FloatingWindowHolder {
        public String id;
        public String url;
        public String title;
        public int widthDp;
        public int heightDp;
        public int currentXDp;
        public int currentYDp;
        public boolean isMinimized = false;
        public boolean isVisible = true;
        public boolean draggable = true;

        public ViewGroup windowRootView;
        public ViewGroup contentLayout;
        public ViewGroup headerLayout;
        public TextView titleView;
        public WebView webView;
        public View miniBubbleView;
        public WeakReference<Activity> activityRef;
    }

    /**
     * 创建并挂载悬浮画中画窗口
     */
    public void createFloatingWindow(
            Activity activity,
            JSONObject options,
            CreateWindowCallback callback
    ) {
        if (activity == null) {
            callback.onError(500, "Activity context is released");
            return;
        }

        mainHandler.post(() -> {
            try {
                String id = options.optString("id", "");
                if (id == null || id.trim().isEmpty()) {
                    id = "float_" + UUID.randomUUID().toString().substring(0, 8);
                }

                // 如果已经存在同 ID 窗口，先移除
                if (activeWindows.containsKey(id)) {
                    closeFloatingWindowInternal(id);
                }

                String url = options.optString("url", "");
                String title = options.optString("title", "悬浮画中画");
                int widthDp = options.optInt("width", 280);
                int heightDp = options.optInt("height", 200);
                boolean draggable = options.optBoolean("draggable", true);
                boolean showControls = options.optBoolean("showControls", true);

                DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                int screenWidthDp = (int) (dm.widthPixels / dm.density);
                int screenHeightDp = (int) (dm.heightPixels / dm.density);

                // 默认位置居右靠上
                int defaultX = Math.max(16, screenWidthDp - widthDp - 20);
                int defaultY = 80;
                int xDp = options.has("x") ? options.optInt("x", defaultX) : defaultX;
                int yDp = options.has("y") ? options.optInt("y", defaultY) : defaultY;

                ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
                if (rootView == null) {
                    callback.onError(500, "Cannot locate root decor/content view");
                    return;
                }

                FloatingWindowHolder holder = new FloatingWindowHolder();
                holder.id = id;
                holder.url = url;
                holder.title = title;
                holder.widthDp = widthDp;
                holder.heightDp = heightDp;
                holder.currentXDp = xDp;
                holder.currentYDp = yDp;
                holder.draggable = draggable;
                holder.activityRef = new WeakReference<>(activity);

                // 1. 构建主悬浮窗视图容器
                FrameLayout windowWrapper = new FrameLayout(activity);
                FrameLayout.LayoutParams wrapperParams = new FrameLayout.LayoutParams(
                        dpToPx(activity, widthDp),
                        dpToPx(activity, heightDp)
                );
                windowWrapper.setLayoutParams(wrapperParams);
                windowWrapper.setX(dpToPx(activity, xDp));
                windowWrapper.setY(dpToPx(activity, yDp));
                windowWrapper.setElevation(dpToPx(activity, 10));

                // 外层卡片圆角背景与描边
                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setColor(Color.WHITE);
                cardBg.setCornerRadius(dpToPx(activity, 12));
                cardBg.setStroke(dpToPx(activity, 1), Color.parseColor("#E2E8F0"));
                windowWrapper.setBackground(cardBg);
                windowWrapper.setClipToOutline(true);

                // 2. 内部垂直布局 (包含标题栏与 WebView)
                LinearLayout contentLayout = new LinearLayout(activity);
                contentLayout.setOrientation(LinearLayout.VERTICAL);
                contentLayout.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));

                // 3. 顶部控制栏
                LinearLayout header = new LinearLayout(activity);
                header.setOrientation(LinearLayout.HORIZONTAL);
                header.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(activity, 34)
                ));
                header.setBackgroundColor(Color.parseColor("#F8FAFC"));
                header.setGravity(Gravity.CENTER_VERTICAL);
                header.setPadding(dpToPx(activity, 10), 0, dpToPx(activity, 6), 0);

                // 标题
                TextView tvTitle = new TextView(activity);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                tvTitle.setLayoutParams(titleParams);
                tvTitle.setText(title);
                tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tvTitle.setTextColor(Color.parseColor("#1E293B"));
                tvTitle.setMaxLines(1);
                tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                header.addView(tvTitle);

                // 最小化按钮
                TextView btnMin = new TextView(activity);
                btnMin.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(activity, 28), dpToPx(activity, 28)));
                btnMin.setGravity(Gravity.CENTER);
                btnMin.setText("—");
                btnMin.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                btnMin.setTextColor(Color.parseColor("#64748B"));
                final String currentId = id;
                btnMin.setOnClickListener(v -> minimizeFloatingWindow(currentId, true));
                header.addView(btnMin);

                // 关闭按钮
                TextView btnClose = new TextView(activity);
                btnClose.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(activity, 28), dpToPx(activity, 28)));
                btnClose.setGravity(Gravity.CENTER);
                btnClose.setText("✕");
                btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                btnClose.setTextColor(Color.parseColor("#EF4444"));
                btnClose.setOnClickListener(v -> closeFloatingWindowInternal(currentId));
                header.addView(btnClose);

                if (showControls) {
                    contentLayout.addView(header);
                }

                // 4. 独立 WebView
                WebView childWebView = new WebView(activity);
                LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                );
                childWebView.setLayoutParams(webParams);
                setupWebViewSettings(childWebView);
                if (url != null && !url.trim().isEmpty()) {
                    childWebView.loadUrl(url);
                }
                contentLayout.addView(childWebView);
                windowWrapper.addView(contentLayout);

                // 5. 拖拽手势绑定
                if (draggable) {
                    bindDragListener(activity, header, windowWrapper, holder);
                }

                // 6. 最小化微型悬浮球 (Mini Bubble)
                FrameLayout miniBubble = new FrameLayout(activity);
                FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(
                        dpToPx(activity, 48),
                        dpToPx(activity, 48)
                );
                miniBubble.setLayoutParams(bubbleParams);
                miniBubble.setX(dpToPx(activity, xDp));
                miniBubble.setY(dpToPx(activity, yDp));
                miniBubble.setElevation(dpToPx(activity, 12));

                GradientDrawable bubbleBg = new GradientDrawable();
                bubbleBg.setShape(GradientDrawable.OVAL);
                bubbleBg.setColor(Color.parseColor("#3B82F6"));
                miniBubble.setBackground(bubbleBg);

                TextView bubbleIcon = new TextView(activity);
                FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                );
                bubbleIcon.setLayoutParams(iconParams);
                bubbleIcon.setText("🗔");
                bubbleIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                bubbleIcon.setTextColor(Color.WHITE);
                miniBubble.addView(bubbleIcon);

                miniBubble.setVisibility(View.GONE);
                miniBubble.setOnClickListener(v -> minimizeFloatingWindow(currentId, false));
                if (draggable) {
                    bindDragListener(activity, miniBubble, miniBubble, holder);
                }

                // 挂载到根视图
                rootView.addView(windowWrapper);
                rootView.addView(miniBubble);

                holder.windowRootView = windowWrapper;
                holder.contentLayout = contentLayout;
                holder.headerLayout = header;
                holder.titleView = tvTitle;
                holder.webView = childWebView;
                holder.miniBubbleView = miniBubble;

                activeWindows.put(id, holder);

                JSONObject res = new JSONObject();
                res.put("success", true);
                res.put("id", id);
                res.put("width", widthDp);
                res.put("height", heightDp);
                res.put("x", xDp);
                res.put("y", yDp);
                callback.onSuccess(res);
            } catch (Exception e) {
                callback.onError(500, "Failed to create floating window: " + e.getMessage());
            }
        });
    }

    /**
     * 关闭指定悬浮窗
     */
    public boolean closeFloatingWindow(String id) {
        if (id == null || id.isEmpty()) {
            // 关闭任意最后一个
            if (!activeWindows.isEmpty()) {
                String firstKey = activeWindows.keySet().iterator().next();
                return closeFloatingWindowInternal(firstKey);
            }
            return false;
        }
        return closeFloatingWindowInternal(id);
    }

    private boolean closeFloatingWindowInternal(String id) {
        FloatingWindowHolder holder = activeWindows.remove(id);
        if (holder == null) return false;

        mainHandler.post(() -> {
            try {
                if (holder.windowRootView != null && holder.windowRootView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) holder.windowRootView.getParent()).removeView(holder.windowRootView);
                }
                if (holder.miniBubbleView != null && holder.miniBubbleView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) holder.miniBubbleView.getParent()).removeView(holder.miniBubbleView);
                }
                if (holder.webView != null) {
                    holder.webView.stopLoading();
                    holder.webView.clearHistory();
                    holder.webView.destroy();
                }
            } catch (Exception ignored) {}
        });
        return true;
    }

    /**
     * 最小化 / 恢复悬浮窗
     */
    public boolean minimizeFloatingWindow(String id, boolean minimize) {
        FloatingWindowHolder holder = activeWindows.get(id);
        if (holder == null) return false;

        holder.isMinimized = minimize;
        mainHandler.post(() -> {
            if (minimize) {
                if (holder.windowRootView != null) holder.windowRootView.setVisibility(View.GONE);
                if (holder.miniBubbleView != null) {
                    holder.miniBubbleView.setVisibility(View.VISIBLE);
                    holder.miniBubbleView.setX(dpToPx(holder.miniBubbleView.getContext(), holder.currentXDp));
                    holder.miniBubbleView.setY(dpToPx(holder.miniBubbleView.getContext(), holder.currentYDp));
                }
            } else {
                if (holder.miniBubbleView != null) holder.miniBubbleView.setVisibility(View.GONE);
                if (holder.windowRootView != null) {
                    holder.windowRootView.setVisibility(holder.isVisible ? View.VISIBLE : View.GONE);
                    holder.windowRootView.setX(dpToPx(holder.windowRootView.getContext(), holder.currentXDp));
                    holder.windowRootView.setY(dpToPx(holder.windowRootView.getContext(), holder.currentYDp));
                }
            }
        });
        return true;
    }

    /**
     * 显示/隐藏悬浮窗
     */
    public boolean setFloatingWindowVisible(String id, boolean visible) {
        FloatingWindowHolder holder = activeWindows.get(id);
        if (holder == null) return false;

        holder.isVisible = visible;
        mainHandler.post(() -> {
            if (!visible) {
                if (holder.windowRootView != null) holder.windowRootView.setVisibility(View.GONE);
                if (holder.miniBubbleView != null) holder.miniBubbleView.setVisibility(View.GONE);
            } else {
                if (holder.isMinimized) {
                    if (holder.miniBubbleView != null) holder.miniBubbleView.setVisibility(View.VISIBLE);
                } else {
                    if (holder.windowRootView != null) holder.windowRootView.setVisibility(View.VISIBLE);
                }
            }
        });
        return true;
    }

    /**
     * 更新悬浮窗位置与尺寸
     */
    public boolean updateFloatingWindow(String id, JSONObject params) {
        FloatingWindowHolder holder = activeWindows.get(id);
        if (holder == null) return false;

        mainHandler.post(() -> {
            Context ctx = holder.windowRootView != null ? holder.windowRootView.getContext() : null;
            if (ctx == null) return;

            if (params.has("width")) {
                holder.widthDp = params.optInt("width", holder.widthDp);
            }
            if (params.has("height")) {
                holder.heightDp = params.optInt("height", holder.heightDp);
            }
            if (params.has("x")) {
                holder.currentXDp = params.optInt("x", holder.currentXDp);
            }
            if (params.has("y")) {
                holder.currentYDp = params.optInt("y", holder.currentYDp);
            }

            if (holder.windowRootView != null) {
                ViewGroup.LayoutParams lp = holder.windowRootView.getLayoutParams();
                lp.width = dpToPx(ctx, holder.widthDp);
                lp.height = dpToPx(ctx, holder.heightDp);
                holder.windowRootView.setLayoutParams(lp);
                holder.windowRootView.setX(dpToPx(ctx, holder.currentXDp));
                holder.windowRootView.setY(dpToPx(ctx, holder.currentYDp));
            }
            if (holder.miniBubbleView != null) {
                holder.miniBubbleView.setX(dpToPx(ctx, holder.currentXDp));
                holder.miniBubbleView.setY(dpToPx(ctx, holder.currentYDp));
            }
        });
        return true;
    }

    /**
     * 向悬浮窗内执行 JS 脚本
     */
    public void evalFloatingWindow(String id, String script, EvalCallback callback) {
        FloatingWindowHolder holder = activeWindows.get(id);
        if (holder == null || holder.webView == null) {
            callback.onResult(null, "Window not found: " + id);
            return;
        }

        mainHandler.post(() -> {
            try {
                holder.webView.evaluateJavascript(script, value -> {
                    callback.onResult(value, null);
                });
            } catch (Exception e) {
                callback.onResult(null, e.getMessage());
            }
        });
    }

    /**
     * 获取浮窗状态
     */
    public JSONObject getFloatingWindowState(String id) {
        FloatingWindowHolder holder = null;
        if (id == null || id.isEmpty()) {
            if (!activeWindows.isEmpty()) {
                holder = activeWindows.values().iterator().next();
            }
        } else {
            holder = activeWindows.get(id);
        }

        if (holder == null) return null;

        try {
            JSONObject json = new JSONObject();
            json.put("id", holder.id);
            json.put("url", holder.url);
            json.put("title", holder.title);
            json.put("width", holder.widthDp);
            json.put("height", holder.heightDp);
            json.put("x", holder.currentXDp);
            json.put("y", holder.currentYDp);
            json.put("isMinimized", holder.isMinimized);
            json.put("isVisible", holder.isVisible);
            return json;
        } catch (Exception e) {
            return null;
        }
    }

    public void closeAll() {
        for (String id : new HashMap<>(activeWindows).keySet()) {
            closeFloatingWindowInternal(id);
        }
    }

    public WebView getFloatingWebView(String id) {
        FloatingWindowHolder holder = activeWindows.get(id);
        return holder != null ? holder.webView : null;
    }

    public Map<String, WebView> getAllFloatingWebViews() {
        Map<String, WebView> result = new HashMap<>();
        for (Map.Entry<String, FloatingWindowHolder> entry : activeWindows.entrySet()) {
            if (entry.getValue().webView != null) {
                result.put(entry.getKey(), entry.getValue().webView);
            }
        }
        return result;
    }

    // --- 内部辅助与拖拽手势 ---

    private void bindDragListener(Activity activity, View handleView, View moveTargetView, FloatingWindowHolder holder) {
        handleView.setOnTouchListener(new View.OnTouchListener() {
            private float initialTouchX, initialTouchY;
            private float initialViewX, initialViewY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        initialViewX = moveTargetView.getX();
                        initialViewY = moveTargetView.getY();
                        isDragging = false;
                        return false; // 允许子按钮点击

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            float newX = initialViewX + dx;
                            float newY = initialViewY + dy;

                            // 屏幕边界碰撞限制
                            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                            int maxX = dm.widthPixels - moveTargetView.getWidth();
                            int maxY = dm.heightPixels - moveTargetView.getHeight();

                            newX = Math.max(0, Math.min(newX, maxX));
                            newY = Math.max(0, Math.min(newY, maxY));

                            moveTargetView.setX(newX);
                            moveTargetView.setY(newY);
                            holder.currentXDp = pxToDp(activity, (int) newX);
                            holder.currentYDp = pxToDp(activity, (int) newY);
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            // 拖拽吸附边界：手指放开时吸附在最近的左/右边缘
                            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                            float curX = moveTargetView.getX();
                            int screenWidth = dm.widthPixels;
                            float targetX = (curX + moveTargetView.getWidth() / 2f < screenWidth / 2f)
                                    ? dpToPx(activity, 12)
                                    : screenWidth - moveTargetView.getWidth() - dpToPx(activity, 12);

                            moveTargetView.animate()
                                    .x(targetX)
                                    .setDuration(180)
                                    .start();
                            holder.currentXDp = pxToDp(activity, (int) targetX);
                            return true;
                        }
                        break;
                }
                return false;
            }
        });
    }

    private void setupWebViewSettings(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
    }

    private int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }

    private int pxToDp(Context context, int px) {
        return (int) (px / context.getResources().getDisplayMetrics().density);
    }

    public interface CreateWindowCallback {
        void onSuccess(JSONObject result);
        void onError(int code, String error);
    }

    public interface EvalCallback {
        void onResult(String result, String error);
    }
}
