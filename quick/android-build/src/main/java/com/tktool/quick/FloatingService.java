package com.tktool.quick;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private View floatingBall;
    private View primaryPanel;
    private View secondaryPanel;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams panelParams;
    private boolean panelShowing = false;
    private boolean isSecondary = false;
    private int currentAlpha = 204;

    private WebView backgroundWebView;
    private boolean webViewReady = false;
    private String pendingClipboardText = null;
    private String pendingBtnKey = null;

    private static final int NOTIFICATION_ID = 2002;
    private static final String PAGE_URL = "https://doctorstrange1122.github.io/tktool/quick/?v=";

    // 肥料按钮配置（简略名称）
    private static final String[][] BTN_CONFIGS = {
        {"500ss", "阳光"},
        {"3w",    "3万"},
        {"4w1",   "4万①"},
        {"4w2",   "4万②"},
        {"5w1",   "5万①"},
        {"5w2",   "5万②"},
        {"6w",    "6万"}
    };

    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        createFloatingBall();
        initBackgroundWebView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("alpha")) {
            setAlpha(intent.getIntExtra("alpha", 80));
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "floating_service_quick",
                    "快跳淘宝悬浮窗",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("悬浮窗服务运行中");
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, "floating_service_quick");
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("快跳淘宝")
                .setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createFloatingBall() {
        int size = dpToPx(52);
        android.widget.ImageView iv = new android.widget.ImageView(this);
        floatingBall = iv;

        try {
            Bitmap srcBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.floating_icon);
            if (srcBitmap != null) {
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(srcBitmap, size, size, true);
                Bitmap roundBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(roundBitmap);
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setAntiAlias(true);
                android.graphics.Path path = new android.graphics.Path();
                path.addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW);
                canvas.clipPath(path);
                canvas.drawBitmap(scaledBitmap, 0, 0, paint);
                BitmapDrawable drawable = new BitmapDrawable(getResources(), roundBitmap);
                iv.setImageDrawable(drawable);
            } else {
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                shape.setColor(0xDD6A9A7A);
                iv.setBackground(shape);
            }
        } catch (Exception e) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(0xDD6A9A7A);
            iv.setBackground(shape);
        }

        applyAlpha();

        ballParams = new WindowManager.LayoutParams(
                size, size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        ballParams.gravity = Gravity.TOP | Gravity.START;
        ballParams.x = 0;
        ballParams.y = dpToPx(100);

        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.TOP | Gravity.START;

        windowManager.addView(floatingBall, ballParams);

        floatingBall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (panelShowing) {
                    hidePanel();
                } else {
                    showPrimaryPanel();
                }
            }
        });

        floatingBall.setOnTouchListener(new FloatingTouchListener(ballParams, windowManager, floatingBall));
    }

    private void setAlpha(int percent) {
        currentAlpha = (int) (255 * (percent / 100f));
        if (currentAlpha < 26) currentAlpha = 26;
        if (currentAlpha > 255) currentAlpha = 255;
        applyAlpha();
    }

    private void applyAlpha() {
        if (floatingBall != null) {
            floatingBall.setAlpha(currentAlpha / 255f);
        }
    }

    // ====== 一级面板 ======
    private View createPrimaryPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        // 背景透明
        panel.setBackgroundColor(0x00000000);
        int pad = dpToPx(4);
        panel.setPadding(pad, pad, pad, pad);

        // 低饱和度绿色（主色 #6a9a7a 降低饱和度）
        int btnColor = 0xFF7a93ac; // 低饱和蓝绿色，与主界面一致
        int btnRadius = dpToPx(10);

        // 扫码输入按钮
        panel.addView(createMainButton("扫码输入", btnColor, btnRadius, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScan();
                hidePanel();
            }
        }));

        // 间距
        View spacer1 = new View(this);
        spacer1.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(6)));
        panel.addView(spacer1);

        // 读取剪贴板按钮
        panel.addView(createMainButton("读取剪贴板", btnColor, btnRadius, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSecondaryPanel();
            }
        }));

        // 间距
        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(10)));
        panel.addView(spacer2);

        // 打开工具
        panel.addView(createTextItem("打开工具", 0xFFFFFFFF, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FloatingService.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                hidePanel();
            }
        }));

        // 关闭悬浮窗
        panel.addView(createTextItem("关闭悬浮窗", 0xFFFF6666, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hidePanel();
                stopSelf();
            }
        }));

        return panel;
    }

    private View createMainButton(String text, int color, int radius, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(28), dpToPx(12), dpToPx(28), dpToPx(12));
        tv.setOnClickListener(listener);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(radius);
        bg.setColor(color);
        tv.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }

    private View createTextItem(String text, int color, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(20), dpToPx(10), dpToPx(20), dpToPx(10));
        tv.setOnClickListener(listener);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    // ====== 二级面板 ======
    private View createSecondaryPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.setBackgroundColor(0x00000000); // 透明背景
        int pad = dpToPx(4);
        panel.setPadding(pad, pad, pad, pad);

        int btnColor = 0xFF7a93ac; // 低饱和度颜色
        int btnRadius = dpToPx(8);

        // 返回 + 标题行
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView backBtn = new TextView(this);
        backBtn.setText("← 返回");
        backBtn.setTextSize(13);
        backBtn.setTextColor(0xFFFFFFFF);
        backBtn.setPadding(dpToPx(8), dpToPx(8), dpToPx(16), dpToPx(8));
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPrimaryPanel();
            }
        });
        headerRow.addView(backBtn);

        TextView titleTv = new TextView(this);
        titleTv.setText("选择肥料");
        titleTv.setTextSize(14);
        titleTv.setTextColor(0xFFFFFFFF);
        titleTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleTv.setLayoutParams(titleLp);
        headerRow.addView(titleTv);

        View rightSpace = new View(this);
        rightSpace.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(48), dpToPx(10)));
        headerRow.addView(rightSpace);

        panel.addView(headerRow);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(8)));
        panel.addView(spacer);

        // 按钮网格（2列）
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < BTN_CONFIGS.length; i++) {
            final String key = BTN_CONFIGS[i][0];
            final String label = BTN_CONFIGS[i][1];
            View btn = createFertilizerButton(label, key, btnColor, btnRadius);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = GridLayout.LayoutParams.WRAP_CONTENT;
            glp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            glp.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            btn.setLayoutParams(glp);
            grid.addView(btn);
        }
        panel.addView(grid);

        return panel;
    }

    private View createFertilizerButton(final String label, final String key, int color, int radius) {
        FrameLayout container = new FrameLayout(this);

        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(14);
        btn.setTextColor(0xFFFFFFFF);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14));
        btn.setMinWidth(dpToPx(80));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(radius);
        bg.setColor(color);
        btn.setBackground(bg);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleFertilizerClick(key);
            }
        });

        container.addView(btn, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        // 使用次数角标
        TextView badge = new TextView(this);
        badge.setId(View.generateViewId());
        badge.setText("0");
        badge.setTextSize(10);
        badge.setTextColor(0xFFFFFFFF);
        badge.setGravity(Gravity.CENTER);
        badge.setBackgroundResource(android.R.drawable.ic_menu_close_clear_cancel);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(0xFFE74C3C);
        badge.setBackground(badgeBg);
        badge.setMinWidth(dpToPx(18));
        badge.setMinHeight(dpToPx(18));
        badge.setPadding(dpToPx(4), 0, dpToPx(4), 0);

        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.TOP | Gravity.END;
        badgeLp.topMargin = dpToPx(-4);
        badgeLp.rightMargin = dpToPx(-4);
        badge.setLayoutParams(badgeLp);

        container.addView(badge);

        // 存储badge引用，方便后续更新
        btn.setTag(badge);
        badge.setTag("badge_" + key);

        return container;
    }

    private void updateBadgeCount(String key, int count) {
        if (secondaryPanel == null) return;
        View badge = secondaryPanel.findViewWithTag("badge_" + key);
        if (badge instanceof TextView) {
            ((TextView) badge).setText(String.valueOf(count));
            badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        }
    }

    // ====== 面板切换 ======
    private void showPrimaryPanel() {
        hidePanelInternal();
        if (primaryPanel == null) {
            primaryPanel = createPrimaryPanel();
        }
        isSecondary = false;
        showPanel(primaryPanel);
    }

    private void showSecondaryPanel() {
        hidePanelInternal();
        if (secondaryPanel == null) {
            secondaryPanel = createSecondaryPanel();
        }
        isSecondary = true;
        showPanel(secondaryPanel);
        // 拉取最新使用次数
        if (webViewReady) {
            fetchUsageCounts();
        } else {
            refreshUsageCounts();
        }
    }

    private void showPanel(View panel) {
        try {
            int[] location = new int[2];
            floatingBall.getLocationOnScreen(location);
            panelParams.x = location[0] + floatingBall.getWidth() + dpToPx(4);
            panelParams.y = location[1];
            windowManager.addView(panel, panelParams);
            panelShowing = true;
        } catch (Exception e) {
            // ignore
        }
    }

    private void hidePanelInternal() {
        if (!panelShowing) return;
        try {
            if (isSecondary && secondaryPanel != null) {
                windowManager.removeView(secondaryPanel);
            } else if (!isSecondary && primaryPanel != null) {
                windowManager.removeView(primaryPanel);
            }
            panelShowing = false;
        } catch (Exception e) {
            // ignore
        }
    }

    private void hidePanel() {
        hidePanelInternal();
        isSecondary = false;
    }

    // ====== 扫码 ======
    private void startScan() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("start_scan", true);
        startActivity(intent);
    }

    // ====== 肥料按钮点击处理 ======
    private void handleFertilizerClick(final String btnKey) {
        // 读取剪贴板
        String clipboardText = readClipboardText();
        if (clipboardText == null || clipboardText.trim().isEmpty()) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }

        hidePanel();
        Toast.makeText(this, "正在生成并跳转...", Toast.LENGTH_SHORT).show();

        if (webViewReady) {
            callFloatGenerate(clipboardText, btnKey);
        } else {
            pendingClipboardText = clipboardText;
            pendingBtnKey = btnKey;
            // 等待WebView加载
            Toast.makeText(this, "页面加载中，请稍候...", Toast.LENGTH_SHORT).show();
        }
    }

    private String readClipboardText() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).getText();
            return text == null ? null : text.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ====== 后台 WebView ======
    private void initBackgroundWebView() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                backgroundWebView = new WebView(FloatingService.this);
                WebSettings settings = backgroundWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setAllowFileAccess(true);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                settings.setCacheMode(WebSettings.LOAD_DEFAULT);

                backgroundWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        webViewReady = true;
                        // 如果有待处理的请求
                        if (pendingClipboardText != null && pendingBtnKey != null) {
                            final String text = pendingClipboardText;
                            final String key = pendingBtnKey;
                            pendingClipboardText = null;
                            pendingBtnKey = null;
                            mainHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    callFloatGenerate(text, key);
                                }
                            }, 500);
                        }
                        // 拉取使用次数
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                fetchUsageCounts();
                            }
                        }, 800);
                    }
                });

                backgroundWebView.setWebChromeClient(new WebChromeClient());

                // 添加JS接口（用于接收异步结果）
                backgroundWebView.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public void onGenerateResult(final String resultJson) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                handleGenerateResult(resultJson);
                            }
                        });
                    }

                    @JavascriptInterface
                    public void onUsageUpdate(final String countsJson) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                handleUsageUpdate(countsJson);
                            }
                        });
                    }
                }, "FloatBridge");

                // 加载页面
                backgroundWebView.loadUrl(PAGE_URL + System.currentTimeMillis());
            }
        });
    }

    private void callFloatGenerate(String clipboardText, String btnKey) {
        if (backgroundWebView == null) return;
        // 对剪贴板内容做转义
        final String escaped = clipboardText.replace("\\", "\\\\")
                .replace("'", "\\'").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");

        final String js = "javascript:(function(){" +
                "if (window.floatGenerate) {" +
                "  window.floatGenerate('" + escaped + "', '" + btnKey + "').then(function(r){" +
                "    if (window.FloatBridge) window.FloatBridge.onGenerateResult(r);" +
                "  }).catch(function(e){" +
                "    if (window.FloatBridge) window.FloatBridge.onGenerateResult(JSON.stringify({success:false,error:e.message||'生成失败'}));" +
                "  });" +
                "} else {" +
                "  if (window.FloatBridge) window.FloatBridge.onGenerateResult(JSON.stringify({success:false,error:'页面未加载完成'}));" +
                "}" +
                "})()";

        backgroundWebView.evaluateJavascript(js, null);
    }

    private void fetchUsageCounts() {
        if (backgroundWebView == null) return;
        final String js = "javascript:(function(){" +
                "try {" +
                "  var data = JSON.parse(localStorage.getItem('btn_daily_usage') || '{\"date\":\"\",\"counts\":{}}');" +
                "  var today = new Date();" +
                "  var todayStr = today.getFullYear() + '-' + (today.getMonth()+1) + '-' + today.getDate();" +
                "  if (data.date !== todayStr) data = {date: todayStr, counts: {}};" +
                "  if (window.FloatBridge) window.FloatBridge.onUsageUpdate(JSON.stringify(data.counts));" +
                "} catch(e) {}" +
                "})()";
        backgroundWebView.evaluateJavascript(js, null);
    }

    private void handleGenerateResult(String resultJson) {
        try {
            // 简单解析JSON
            if (resultJson.contains("\"success\":true")) {
                // 提取link
                int linkStart = resultJson.indexOf("\"link\":\"") + 8;
                int linkEnd = resultJson.indexOf("\"", linkStart);
                String link = resultJson.substring(linkStart, linkEnd);
                link = link.replace("\\/", "/");

                // 直接跳转淘宝
                openTaobao(link);
            } else {
                int errStart = resultJson.indexOf("\"error\":\"");
                String error = "生成失败";
                if (errStart >= 0) {
                    errStart += 9;
                    int errEnd = resultJson.indexOf("\"", errStart);
                    if (errEnd > errStart) {
                        error = resultJson.substring(errStart, errEnd);
                    }
                }
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "解析结果失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void openTaobao(String url) {
        try {
            // 优先 tbopen:// deep link
            String tbopen = "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&h5Url=" +
                    Uri.encode(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(tbopen));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                // 降级 taobao://
                String taobaoUrl = "taobao://" + url.replace("https://", "");
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(taobaoUrl));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(this, "跳转失败，请检查是否安装淘宝", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void refreshUsageCounts() {
        // 从SharedPreferences读取使用次数并更新角标
        SharedPreferences sp = getSharedPreferences("usage_counts", MODE_PRIVATE);
        for (String[] cfg : BTN_CONFIGS) {
            String key = cfg[0];
            int count = sp.getInt(key, 0);
            updateBadgeCount(key, count);
        }
    }

    private void handleUsageUpdate(String countsJson) {
        try {
            SharedPreferences sp = getSharedPreferences("usage_counts", MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            for (String[] cfg : BTN_CONFIGS) {
                String key = cfg[0];
                // 简单解析：找 "key":number
                int idx = countsJson.indexOf("\"" + key + "\":");
                if (idx >= 0) {
                    int numStart = idx + key.length() + 3;
                    int numEnd = numStart;
                    while (numEnd < countsJson.length() &&
                            (countsJson.charAt(numEnd) >= '0' && countsJson.charAt(numEnd) <= '9')) {
                        numEnd++;
                    }
                    if (numEnd > numStart) {
                        int count = Integer.parseInt(countsJson.substring(numStart, numEnd));
                        editor.putInt(key, count);
                        updateBadgeCount(key, count);
                    }
                }
            }
            editor.apply();
        } catch (Exception e) {
            // ignore
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hidePanel();
        if (floatingBall != null) {
            try {
                windowManager.removeView(floatingBall);
            } catch (Exception e) {
                // ignore
            }
        }
        if (backgroundWebView != null) {
            try {
                backgroundWebView.destroy();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // 触摸拖动监听器
    private static class FloatingTouchListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private final WindowManager wm;
        private final View view;
        private float initialTouchX;
        private float initialTouchY;
        private int initialX;
        private int initialY;

        FloatingTouchListener(WindowManager.LayoutParams params, WindowManager wm, View view) {
            this.params = params;
            this.wm = wm;
            this.view = view;
        }

        @Override
        public boolean onTouch(View v, android.view.MotionEvent event) {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    initialX = params.x;
                    initialY = params.y;
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - initialTouchX;
                    float deltaY = event.getRawY() - initialTouchY;
                    if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
                        params.x = initialX + (int) deltaX;
                        params.y = initialY + (int) deltaY;
                        wm.updateViewLayout(view, params);
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    if (Math.abs(event.getRawX() - initialTouchX) < 5
                            && Math.abs(event.getRawY() - initialTouchY) < 5) {
                        view.performClick();
                    }
                    return true;
            }
            return false;
        }
    }
}
