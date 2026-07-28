package com.tktool.test;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private TextView floatingBall;
    private View actionPanel;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams panelParams;
    private boolean panelShowing = false;

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

        // 创建圆形悬浮球，使用 APP 图标
        floatingBall = new TextView(this);
        floatingBall.setGravity(Gravity.CENTER);

        // 使用 APP 图标作为圆形背景
        try {
            android.graphics.drawable.Drawable icon = getPackageManager().getApplicationIcon(getPackageName());
            if (icon != null) {
                // 创建圆形裁剪的图标
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                icon.setBounds(0, 0, size, size);
                icon.draw(canvas);

                // 圆形裁剪
                android.graphics.Bitmap roundBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas roundCanvas = new android.graphics.Canvas(roundBitmap);
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setAntiAlias(true);
                android.graphics.Path path = new android.graphics.Path();
                path.addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW);
                roundCanvas.clipPath(path);
                roundCanvas.drawBitmap(bitmap, 0, 0, null);

                android.graphics.drawable.BitmapDrawable roundDrawable = new android.graphics.drawable.BitmapDrawable(getResources(), roundBitmap);
                floatingBall.setBackground(roundDrawable);
            }
        } catch (Exception e) {
            // 图标加载失败，使用绿色圆形作为兜底
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            shape.setColor(0xDD6A9A7A);
            floatingBall.setBackground(shape);
        }
        floatingBall.setText("");

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

        // 悬浮球拖动
        floatingBall.setOnTouchListener(new FloatingTouchListener(ballParams, windowManager, floatingBall));
    }

    private View createActionPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF0222222);
        int pad = dpToPx(8);
        panel.setPadding(pad, pad, pad, pad);

        // 读取剪贴板按钮
        TextView readBtn = new TextView(this);
        readBtn.setText("📋 读取剪贴板");
        readBtn.setTextSize(14);
        readBtn.setTextColor(0xFFFFFFFF);
        readBtn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        readBtn.setBackgroundColor(0x00000000);
        readBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readClipboardAndOpen();
                hideActionPanel();
            }
        });

        // 打开 APP 按钮
        TextView openBtn = new TextView(this);
        openBtn.setText("📱 打开工具");
        openBtn.setTextSize(14);
        openBtn.setTextColor(0xFFFFFFFF);
        openBtn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        openBtn.setBackgroundColor(0x00000000);
        openBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FloatingService.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                hideActionPanel();
            }
        });

        // 关闭悬浮窗
        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕ 关闭悬浮窗");
        closeBtn.setTextSize(14);
        closeBtn.setTextColor(0xFFFF8888);
        closeBtn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        closeBtn.setBackgroundColor(0x00000000);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideActionPanel();
                stopSelf();
            }
        });

        panel.addView(readBtn);
        panel.addView(openBtn);
        panel.addView(closeBtn);
        return panel;
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
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            String content = "";
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null) {
                        content = text.toString().trim();
                    }
                }
            }

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("clipboard_content", content);
            startActivity(intent);

            if (!content.isEmpty()) {
                Toast.makeText(this, "已读取剪贴板内容", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "读取失败", Toast.LENGTH_SHORT).show();
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
                    return false; // 不消耗，让 onClick 也能触发
                case android.view.MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - initialTouchX;
                    float deltaY = event.getRawY() - initialTouchY;
                    if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
                        params.x = initialX + (int) deltaX;
                        params.y = initialY + (int) deltaY;
                        wm.updateViewLayout(view, params);
                        return true;
                    }
                    return false;
                case android.view.MotionEvent.ACTION_UP:
                    if (Math.abs(event.getRawX() - initialTouchX) < 5
                            && Math.abs(event.getRawY() - initialTouchY) < 5) {
                        return false; // 点击事件
                    }
                    return true;
            }
            return false;
        }
    }
}
