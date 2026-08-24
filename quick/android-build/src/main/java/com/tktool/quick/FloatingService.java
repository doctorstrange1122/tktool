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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private View floatingBall;
    private View primaryPanel;
    private View secondaryPanel;
    private View tertiaryPanel;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams fullscreenParams;
    private boolean panelShowing = false;
    private int panelLevel = 0; // 0=无, 1=一级, 2=二级, 3=三级

    private static final int NOTIFICATION_ID = 2002;

    // 默认按钮配置（key, 面板显示名, 对应用途统计key）
    // 可通过 updateButtonConfigs() 动态更新（与在线文件同步）
    private String[][] mBtnConfigs = {
        {"500ss", "阳光", "500ss"},
        {"yuanbao", "元宝", "yuanbao"},
        {"3w",    "3万",   "3w"},
        {"4w1",   "4万①", "4w1"},
        {"4w2",   "4万②", "4w2"},
        {"5w1",   "5万①", "5w1"},
        {"5w2",   "5万②", "5w2"},
        {"6w",    "6万",   "6w"}
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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // 处理透明度调节（仅作用于悬浮球图标）
            if (intent.hasExtra("alpha")) {
                final int alpha = intent.getIntExtra("alpha", 80);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setBallAlpha(alpha);
                    }
                });
            }
            // 处理动态按钮配置更新
            if (intent.hasExtra("btn_configs")) {
                String[] configs = intent.getStringArrayExtra("btn_configs");
                if (configs != null && configs.length > 0) {
                    updateButtonConfigsInternal(configs);
                }
            }
            // 通知生成完成（关闭三级面板）
            if (intent.hasExtra("generate_done")) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        hidePanel();
                    }
                });
            }
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "floating_service_quick", "快捷过肥悬浮窗",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, "floating_service_quick")
                : new Notification.Builder(this);
        return builder
                .setContentTitle("快捷过肥")
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
                iv.setImageDrawable(new BitmapDrawable(getResources(), roundBitmap));
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

        ballParams = new WindowManager.LayoutParams(size, size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        ballParams.gravity = Gravity.TOP | Gravity.START;
        ballParams.x = 0;
        ballParams.y = dpToPx(100);

        // 普通面板的LayoutParams
        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;

        // 全屏三级面板的LayoutParams（透明背景覆盖整个屏幕）
        fullscreenParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        fullscreenParams.gravity = Gravity.TOP | Gravity.START;

        windowManager.addView(floatingBall, ballParams);
        floatingBall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (panelShowing) hidePanel();
                else showPrimaryPanel();
            }
        });
        floatingBall.setOnTouchListener(new FloatingTouchListener(ballParams, windowManager, floatingBall));
    }

    // 设置悬浮球透明度（面板不受影响）
    private void setBallAlpha(int percent) {
        if (floatingBall == null) return;
        float alpha = Math.max(0.2f, Math.min(1.0f, percent / 100f));
        floatingBall.setAlpha(alpha);
        // 保存设置
        SharedPreferences sp = getSharedPreferences("floating_settings", MODE_PRIVATE);
        sp.edit().putInt("ball_alpha", percent).apply();
    }

    // ====== 一级面板 ======
    private View createPrimaryPanel() {
        int panelWidth = dpToPx(120);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadius(dpToPx(8));
        panelBg.setColor(0xE6222222);
        panel.setBackground(panelBg);

        int padV = dpToPx(8);
        int padH = dpToPx(8);
        panel.setPadding(padH, padV, padH, padV);

        int btnColor = 0xFF6a8a9a;
        int btnRadius = dpToPx(6);
        int btn2Color = 0xFF4a5568;
        int btnDanger = 0xFF8B3A3A;

        // 扫码输入
        panel.addView(createPanelButton("扫码输入", btnColor, btnRadius, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScan();
                hidePanel();
            }
        }));
        addSpacer(panel, 5);

        // 读取剪贴板（呼出二级面板 + 粘贴到主页面）
        panel.addView(createPanelButton("读取剪贴板", btnColor, btnRadius, new View.OnClickListener() {
            @Override
            public void onClick(View v) { showSecondaryPanel(); }
        }));
        addSpacer(panel, 6);

        // 分隔线
        addDivider(panel);
        addSpacer(panel, 6);

        // 打开工具
        panel.addView(createPanelButton("打开工具", btn2Color, btnRadius, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FloatingService.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                hidePanel();
            }
        }));
        addSpacer(panel, 5);

        // 关闭悬浮窗
        panel.addView(createPanelButton("关闭悬浮窗", btnDanger, btnRadius, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hidePanel();
                stopSelf();
            }
        }));

        return panel;
    }

    private View createPanelButton(String text, int color, int radius, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8));
        tv.setOnClickListener(listener);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(radius);
        bg.setColor(color);
        tv.setBackground(bg);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private void addSpacer(LinearLayout panel, int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(dp)));
        panel.addView(v);
    }

    private void addDivider(LinearLayout panel) {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        v.setLayoutParams(lp);
        v.setBackgroundColor(0x33FFFFFF);
        panel.addView(v);
    }

    // ====== 二级面板 ======
    private View createSecondaryPanel() {
        int panelWidth = dpToPx(170);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadius(dpToPx(8));
        panelBg.setColor(0xE6222222);
        panel.setBackground(panelBg);

        int padV = dpToPx(8);
        int padH = dpToPx(8);
        panel.setPadding(padH, padV, padH, padV);

        int btnColor = 0xFF6a8a9a;
        int btnRadius = dpToPx(6);

        // 顶部
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView backBtn = new TextView(this);
        backBtn.setText("← 返回");
        backBtn.setTextSize(11);
        backBtn.setTextColor(0xFF88CCFF);
        backBtn.setPadding(dpToPx(2), dpToPx(2), dpToPx(6), dpToPx(2));
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPrimaryPanel(); }
        });
        headerRow.addView(backBtn);

        TextView titleTv = new TextView(this);
        titleTv.setText("选择肥料");
        titleTv.setTextSize(13);
        titleTv.setTextColor(0xFFFFFFFF);
        titleTv.setGravity(Gravity.CENTER);
        titleTv.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleTv.setLayoutParams(titleLp);
        headerRow.addView(titleTv);

        View rightSpacer = new View(this);
        rightSpacer.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(30), dpToPx(8)));
        headerRow.addView(rightSpacer);

        panel.addView(headerRow);
        addSpacer(panel, 4);
        addDivider(panel);
        addSpacer(panel, 6);

        // 按钮网格
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < mBtnConfigs.length; i++) {
            final String key = mBtnConfigs[i][0];
            final String label = mBtnConfigs[i][1];
            View btn = createFertilizerButton(label, key, btnColor, btnRadius);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
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
        btn.setTextSize(12);
        btn.setTextColor(0xFFFFFFFF);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(radius);
        bg.setColor(color);
        btn.setBackground(bg);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { handleFertilizerClick(key); }
        });

        container.addView(btn, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 角标
        TextView badge = new TextView(this);
        badge.setText("0");
        badge.setTextSize(9);
        badge.setTextColor(0xFFFFFFFF);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(0xFFE74C3C);
        badge.setBackground(badgeBg);
        badge.setMinWidth(dpToPx(16));
        badge.setMinHeight(dpToPx(16));
        badge.setPadding(dpToPx(3), 0, dpToPx(3), 0);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.TOP | Gravity.END;
        badgeLp.topMargin = dpToPx(-3);
        badgeLp.rightMargin = dpToPx(-3);
        badge.setLayoutParams(badgeLp);
        container.addView(badge);
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

    private void refreshAllBadges() {
        SharedPreferences sp = getSharedPreferences("usage_counts", MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-M-d", Locale.getDefault()).format(new Date());
        String savedDate = sp.getString("date", "");
        if (!today.equals(savedDate)) {
            SharedPreferences.Editor editor = sp.edit();
            editor.putString("date", today);
            for (String[] cfg : mBtnConfigs) {
                editor.putInt(cfg[2], 0);
                updateBadgeCount(cfg[0], 0);
            }
            editor.apply();
            return;
        }
        for (String[] cfg : mBtnConfigs) {
            updateBadgeCount(cfg[0], sp.getInt(cfg[2], 0));
        }
    }

    // 动态更新按钮配置（与在线文件同步）
    private void updateButtonConfigsInternal(String[] flatConfigs) {
        // flatConfigs格式: key1,label1,countKey1,key2,label2,countKey2,...
        List<String[]> list = new ArrayList<>();
        for (int i = 0; i + 2 < flatConfigs.length; i += 3) {
            list.add(new String[]{ flatConfigs[i], flatConfigs[i+1], flatConfigs[i+2] });
        }
        if (list.size() > 0) {
            mBtnConfigs = list.toArray(new String[0][]);
            // 二级面板已创建则重建
            if (secondaryPanel != null) {
                boolean wasShowing = panelShowing && panelLevel == 2;
                if (wasShowing) hidePanelInternal();
                secondaryPanel = null;
                if (wasShowing) showSecondaryPanel();
            }
        }
    }

    // ====== 三级面板（加载中） ======
    private View createTertiaryPanel() {
        // 全屏透明背景
        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(0x66000000); // 半透明黑色背景

        // 居中容器
        LinearLayout centerLayout = new LinearLayout(this);
        centerLayout.setOrientation(LinearLayout.VERTICAL);
        centerLayout.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        centerLp.gravity = Gravity.CENTER;
        centerLayout.setLayoutParams(centerLp);

        // 转圈加载图标
        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        int pbSize = dpToPx(48);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(pbSize, pbSize);
        pbLp.gravity = Gravity.CENTER_HORIZONTAL;
        progressBar.setLayoutParams(pbLp);

        // 文字
        TextView textView = new TextView(this);
        textView.setText("自动执行中...");
        textView.setTextSize(14);
        textView.setTextColor(0xFFFFFFFF);
        textView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.gravity = Gravity.CENTER_HORIZONTAL;
        tvLp.topMargin = dpToPx(12);
        textView.setLayoutParams(tvLp);

        centerLayout.addView(progressBar);
        centerLayout.addView(textView);
        overlay.addView(centerLayout);

        // 点击空白区域不关闭（防止误触）
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 不响应点击
            }
        });

        return overlay;
    }

    // ====== 面板切换 ======
    private void showPrimaryPanel() {
        hidePanelInternal();
        if (primaryPanel == null) primaryPanel = createPrimaryPanel();
        panelLevel = 1;
        showPanel(primaryPanel);
    }

    private void showSecondaryPanel() {
        hidePanelInternal();
        if (secondaryPanel == null) secondaryPanel = createSecondaryPanel();
        panelLevel = 2;
        showPanel(secondaryPanel);
        refreshAllBadges();

        // 同步：将剪贴板内容粘贴到主页面输入框
        pasteClipboardToMainActivity();
    }

    private void showTertiaryPanel() {
        hidePanelInternal();
        if (tertiaryPanel == null) tertiaryPanel = createTertiaryPanel();
        panelLevel = 3;
        // 三级面板全屏显示
        try {
            fullscreenParams.x = 0;
            fullscreenParams.y = 0;
            fullscreenParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            fullscreenParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            windowManager.addView(tertiaryPanel, fullscreenParams);
            panelShowing = true;
        } catch (Exception e) {
            Toast.makeText(this, "面板显示失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPanel(View panel) {
        try {
            int panelWidth = panel.getLayoutParams().width;
            int panelHeight = panel.getLayoutParams().height;

            // 预先测量（确保宽度固定，避免时大时小）
            if (panelWidth == LinearLayout.LayoutParams.WRAP_CONTENT
                    || panelHeight == LinearLayout.LayoutParams.WRAP_CONTENT) {
                int w = panelWidth == LinearLayout.LayoutParams.WRAP_CONTENT
                        ? View.MeasureSpec.UNSPECIFIED : View.MeasureSpec.EXACTLY;
                int h = panelHeight == LinearLayout.LayoutParams.WRAP_CONTENT
                        ? View.MeasureSpec.UNSPECIFIED : View.MeasureSpec.EXACTLY;
                int ws = panelWidth == LinearLayout.LayoutParams.WRAP_CONTENT
                        ? 0 : panelWidth;
                int hs = panelHeight == LinearLayout.LayoutParams.WRAP_CONTENT
                        ? 0 : panelHeight;
                panel.measure(
                        View.MeasureSpec.makeMeasureSpec(ws, w),
                        View.MeasureSpec.makeMeasureSpec(hs, h));
                if (panelWidth == LinearLayout.LayoutParams.WRAP_CONTENT)
                    panelWidth = panel.getMeasuredWidth();
                if (panelHeight == LinearLayout.LayoutParams.WRAP_CONTENT)
                    panelHeight = panel.getMeasuredHeight();
            }

            int[] location = new int[2];
            floatingBall.getLocationOnScreen(location);

            int x = location[0] + floatingBall.getWidth() + dpToPx(6);
            int y = location[1];

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;

            if (x + panelWidth > screenWidth) x = location[0] - panelWidth - dpToPx(6);
            if (y + panelHeight > screenHeight) y = screenHeight - panelHeight - dpToPx(20);
            if (y < 0) y = 0;
            if (x < 0) x = 0;

            panelParams.x = x;
            panelParams.y = y;
            panelParams.width = panelWidth;
            panelParams.height = panelHeight;
            windowManager.addView(panel, panelParams);
            panelShowing = true;
        } catch (Exception e) {
            Toast.makeText(this, "面板显示失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void hidePanelInternal() {
        if (!panelShowing) return;
        try {
            if (panelLevel == 3 && tertiaryPanel != null)
                windowManager.removeView(tertiaryPanel);
            else if (panelLevel == 2 && secondaryPanel != null)
                windowManager.removeView(secondaryPanel);
            else if (panelLevel == 1 && primaryPanel != null)
                windowManager.removeView(primaryPanel);
            panelShowing = false;
        } catch (Exception e) { /* ignore */ }
    }

    private void hidePanel() {
        hidePanelInternal();
        panelLevel = 0;
    }

    // ====== 扫码 ======
    private void startScan() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("start_scan", true);
        startActivity(intent);
    }

    // ====== 粘贴剪贴板到主页面 ======
    private void pasteClipboardToMainActivity() {
        String text = readClipboardText();
        if (text == null || text.trim().isEmpty()) return;

        // 通过广播或启动MainActivity传递剪贴板内容
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("paste_clipboard", text);
        startActivity(intent);
    }

    // ====== 肥料按钮点击 ======
    private void handleFertilizerClick(final String btnKey) {
        String clipboardText = readClipboardText();
        if (clipboardText == null || clipboardText.trim().isEmpty()) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示三级加载面板
        showTertiaryPanel();

        // 启动MainActivity并执行快速生成
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("quick_btn_key", btnKey);
        intent.putExtra("quick_clipboard", clipboardText);
        startActivity(intent);
    }

    private String readClipboardText() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).getText();
            return text == null ? null : text.toString();
        } catch (Exception e) { return null; }
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
            try { windowManager.removeView(floatingBall); } catch (Exception e) { /* ignore */ }
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ====== 触摸拖动 ======
    private static class FloatingTouchListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private final WindowManager wm;
        private final View view;
        private final int touchSlop;
        private float initialTouchX, initialTouchY;
        private int initialX, initialY;
        private boolean hasMoved = false;

        FloatingTouchListener(WindowManager.LayoutParams params, WindowManager wm, View view) {
            this.params = params;
            this.wm = wm;
            this.view = view;
            this.touchSlop = (int) (20 * view.getResources().getDisplayMetrics().density);
        }

        @Override
        public boolean onTouch(View v, android.view.MotionEvent event) {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    initialX = params.x;
                    initialY = params.y;
                    hasMoved = false;
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - initialTouchX;
                    float dy = event.getRawY() - initialTouchY;
                    if (Math.abs(dx) > touchSlop / 2f || Math.abs(dy) > touchSlop / 2f) hasMoved = true;
                    if (hasMoved) {
                        params.x = initialX + (int) dx;
                        params.y = initialY + (int) dy;
                        wm.updateViewLayout(view, params);
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    if (!hasMoved) view.performClick();
                    return true;
            }
            return false;
        }
    }
}
