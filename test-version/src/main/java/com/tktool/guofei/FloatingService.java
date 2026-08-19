package com.tktool.guofei;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private View floatingBall;
    private View actionPanel;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams panelParams;
    private boolean panelShowing = false;
    private int currentAlpha = 204; // 默认 80% (255 * 0.8)

    private static final int NOTIFICATION_ID = 2001;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        createFloatingBall();
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
                    "floating_service",
                    "悬浮窗服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持悬浮窗运行");
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
            builder = new Notification.Builder(this, "floating_service");
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("过肥工具")
                .setContentText("悬浮窗运行中，点击打开")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createFloatingBall() {
        int size = dpToPx(52);

        // 创建圆形悬浮球，使用自定义图标
        android.widget.ImageView iv = new android.widget.ImageView(this);
        floatingBall = iv;

        try {
            // 从 drawable 资源加载自定义图标
            Bitmap srcBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.floating_icon);
            if (srcBitmap != null) {
                // 缩放到目标尺寸
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(srcBitmap, size, size, true);

                // 创建圆形裁剪的位图
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
                // 图标加载失败，使用绿色圆形作为兜底
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

        // 设置初始透明度
        applyAlpha();

        ballParams = new WindowManager.LayoutParams(
                size,
                size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        ballParams.gravity = Gravity.TOP | Gravity.START;
        ballParams.x = 0;
        ballParams.y = dpToPx(100);

        // 创建操作面板
        actionPanel = createActionPanel();

        panelParams = new WindowManager.LayoutParams(
                dpToPx(200),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.TOP | Gravity.START;

        windowManager.addView(floatingBall, ballParams);

        // 悬浮球点击事件
        floatingBall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (panelShowing) {
                    hideActionPanel();
                } else {
                    showActionPanel();
                }
            }
        });

        // 悬浮球拖动（不吸附边缘）
        floatingBall.setOnTouchListener(new FloatingTouchListener(ballParams, windowManager, floatingBall));
    }

    // 设置透明度 (0-100)
    public void setAlpha(int percent) {
        currentAlpha = (int) (255 * (percent / 100f));
        if (currentAlpha < 26) currentAlpha = 26;   // 最低 10%
        if (currentAlpha > 255) currentAlpha = 255; // 最高 100%
        applyAlpha();
    }

    private void applyAlpha() {
        float ratio = currentAlpha / 255f;
        if (floatingBall != null) {
            floatingBall.setAlpha(ratio);
        }
        // 面板透明度也跟随设置
        if (actionPanel != null) {
            actionPanel.setAlpha(ratio);
        }
    }

    private View createActionPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        // 圆角矩形背景
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(16));
        bg.setColor(0xF02C2C2C);
        bg.setStroke(dpToPx(1), 0x33FFFFFF);
        panel.setBackground(bg);
        panel.setElevation(dpToPx(12));

        // 裁剪子视图到圆角范围内
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            panel.setClipToOutline(true);
        }

        // 标题栏
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setShape(GradientDrawable.RECTANGLE);
        headerBg.setColor(0xFF3A6B5C);
        header.setBackground(headerBg);
        header.setPadding(dpToPx(16), dpToPx(10), dpToPx(8), dpToPx(10));

        TextView title = new TextView(this);
        title.setText("过肥工具");
        title.setTextSize(15);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeBtn = new TextView(this);
        closeBtn.setText("\u00D7");
        closeBtn.setTextSize(18);
        closeBtn.setTextColor(0xCCFFFFFF);
        closeBtn.setPadding(dpToPx(12), dpToPx(2), dpToPx(4), dpToPx(2));
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideActionPanel();
            }
        });

        header.addView(title);
        header.addView(closeBtn);
        panel.addView(header);

        // 读取剪贴板
        panel.addView(createPanelItem("读取剪贴板", 0xFFE8E8E8, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readClipboardAndOpen();
                hideActionPanel();
            }
        }));

        panel.addView(createDivider());

        // 扫码输入
        panel.addView(createPanelItem("扫码输入", 0xFFE8E8E8, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FloatingService.this, ScanActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                hideActionPanel();
            }
        }));

        panel.addView(createDivider());

        // 打开工具
        panel.addView(createPanelItem("打开工具", 0xFFE8E8E8, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FloatingService.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra("scroll_to_top", true);
                startActivity(intent);
                hideActionPanel();
            }
        }));

        panel.addView(createDivider());

        // 关闭悬浮窗
        panel.addView(createPanelItem("关闭悬浮窗", 0xFFFF6B6B, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideActionPanel();
                stopSelf();
            }
        }));

        // 底部留白
        panel.addView(createSpacer(dpToPx(4)));

        return panel;
    }

    private TextView createPanelItem(String text, int color, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dpToPx(20), dpToPx(13), dpToPx(20), dpToPx(13));
        // 按压反馈
        android.graphics.drawable.StateListDrawable itemBg = new android.graphics.drawable.StateListDrawable();
        itemBg.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.ColorDrawable(0x33FFFFFF));
        itemBg.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0x00000000));
        tv.setBackground(itemBg);
        tv.setOnClickListener(listener);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(0x1AFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        lp.setMargins(dpToPx(16), 0, dpToPx(16), 0);
        divider.setLayoutParams(lp);
        return divider;
    }

    private View createSpacer(int height) {
        View spacer = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height);
        spacer.setLayoutParams(lp);
        return spacer;
    }

    private void showActionPanel() {
        if (panelShowing) return;
        try {
            // 面板位置在悬浮球旁边
            int[] location = new int[2];
            floatingBall.getLocationOnScreen(location);
            panelParams.x = location[0] + floatingBall.getWidth() + dpToPx(4);
            panelParams.y = location[1];
            windowManager.addView(actionPanel, panelParams);
            panelShowing = true;
        } catch (Exception e) {
            // ignore
        }
    }

    private void hideActionPanel() {
        if (!panelShowing) return;
        try {
            windowManager.removeView(actionPanel);
            panelShowing = false;
        } catch (Exception e) {
            // ignore
        }
    }

    private void readClipboardAndOpen() {
        try {
            // Android 10+ 后台 Service 无法读剪贴板，改为通知 Activity 自己去读
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("read_clipboard", true);
            intent.putExtra("scroll_to_top", true);
            startActivity(intent);
            Toast.makeText(this, "正在打开工具并读取剪贴板...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "启动失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideActionPanel();
        if (floatingBall != null) {
            try {
                windowManager.removeView(floatingBall);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // 触摸拖动监听器（不吸附边缘）
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
                    return true; // 消耗 DOWN 事件，确保后续 MOVE/UP 能收到
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
                    // 如果是点击（位移很小），触发 click
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
