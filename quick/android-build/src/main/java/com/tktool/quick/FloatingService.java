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
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private View floatingBall;
    private View primaryPanel;
    private View secondaryPanel;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams panelParams;
    private boolean panelShowing = false;
    private int panelLevel = 0; // 0=无, 1=一级, 2=二级

    private static final int NOTIFICATION_ID = 2002;

    private static final String[][] BTN_CONFIGS = {
        {"500ss", "阳光",  "500☀☀"},
        {"3w",    "3万",   "3万肥料"},
        {"4w1",   "4万①", "4万肥料①"},
        {"4w2",   "4万②", "4万肥料②"},
        {"5w1",   "5万①", "5万肥料①"},
        {"5w2",   "5万②", "5万肥料②"},
        {"6w",    "6万",   "6万肥料"}
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
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "floating_service_quick", "快跳淘宝悬浮窗",
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

        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;

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

    // ====== 一级面板 ======
    private View createPrimaryPanel() {
        int panelWidth = dpToPx(130);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadius(dpToPx(10));
        panelBg.setColor(0xE6222222);
        panel.setBackground(panelBg);

        int padV = dpToPx(10);
        int padH = dpToPx(10);
        panel.setPadding(padH, padV, padH, padV);

        int btnColor = 0xFF6a8a9a;
        int btnRadius = dpToPx(8);
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
        addSpacer(panel, 6);

        // 读取剪贴板
        panel.addView(createPanelButton("读取剪贴板", btnColor, btnRadius, new View.OnClickListener() {
            @Override
            public void onClick(View v) { showSecondaryPanel(); }
        }));
        addSpacer(panel, 8);

        // 分隔线
        addDivider(panel);
        addSpacer(panel, 8);

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
        addSpacer(panel, 6);

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
        tv.setTextSize(14);
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10));
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
        int panelWidth = dpToPx(180);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutParams(new LinearLayout.LayoutParams(panelWidth, LinearLayout.LayoutParams.WRAP_CONTENT));

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadius(dpToPx(10));
        panelBg.setColor(0xE6222222);
        panel.setBackground(panelBg);

        int padV = dpToPx(10);
        int padH = dpToPx(10);
        panel.setPadding(padH, padV, padH, padV);

        int btnColor = 0xFF6a8a9a;
        int btnRadius = dpToPx(8);

        // 顶部
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView backBtn = new TextView(this);
        backBtn.setText("← 返回");
        backBtn.setTextSize(12);
        backBtn.setTextColor(0xFF88CCFF);
        backBtn.setPadding(dpToPx(2), dpToPx(4), dpToPx(8), dpToPx(4));
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPrimaryPanel(); }
        });
        headerRow.addView(backBtn);

        TextView titleTv = new TextView(this);
        titleTv.setText("选择肥料");
        titleTv.setTextSize(14);
        titleTv.setTextColor(0xFFFFFFFF);
        titleTv.setGravity(Gravity.CENTER);
        titleTv.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleTv.setLayoutParams(titleLp);
        headerRow.addView(titleTv);

        View rightSpacer = new View(this);
        rightSpacer.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(36), dpToPx(10)));
        headerRow.addView(rightSpacer);

        panel.addView(headerRow);
        addSpacer(panel, 6);
        addDivider(panel);
        addSpacer(panel, 8);

        // 按钮网格
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < BTN_CONFIGS.length; i++) {
            final String key = BTN_CONFIGS[i][0];
            final String label = BTN_CONFIGS[i][1];
            View btn = createFertilizerButton(label, key, btnColor, btnRadius);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));
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
        btn.setTextSize(13);
        btn.setTextColor(0xFFFFFFFF);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dpToPx(6), dpToPx(10), dpToPx(6), dpToPx(10));

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
        badge.setTextSize(10);
        badge.setTextColor(0xFFFFFFFF);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(0xFFE74C3C);
        badge.setBackground(badgeBg);
        badge.setMinWidth(dpToPx(18));
        badge.setMinHeight(dpToPx(18));
        badge.setPadding(dpToPx(4), 0, dpToPx(4), 0);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.TOP | Gravity.END;
        badgeLp.topMargin = dpToPx(-4);
        badgeLp.rightMargin = dpToPx(-4);
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
            for (String[] cfg : BTN_CONFIGS) {
                editor.putInt(cfg[0], 0);
                updateBadgeCount(cfg[0], 0);
            }
            editor.apply();
            return;
        }
        for (String[] cfg : BTN_CONFIGS) {
            updateBadgeCount(cfg[0], sp.getInt(cfg[0], 0));
        }
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
    }

    private void showPanel(View panel) {
        try {
            int panelWidth = panel.getLayoutParams().width;
            int panelHeight = panel.getLayoutParams().height;

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

            int x = location[0] + floatingBall.getWidth() + dpToPx(8);
            int y = location[1];

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;

            if (x + panelWidth > screenWidth) x = location[0] - panelWidth - dpToPx(8);
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
            if (panelLevel == 2 && secondaryPanel != null)
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

    // ====== 肥料按钮点击 ======
    private void handleFertilizerClick(final String btnKey) {
        String clipboardText = readClipboardText();
        if (clipboardText == null || clipboardText.trim().isEmpty()) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 先启动Activity，再隐藏面板（和扫码输入同样的顺序）
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("quick_btn_key", btnKey);
        intent.putExtra("quick_clipboard", clipboardText);
        startActivity(intent);

        hidePanel();
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
