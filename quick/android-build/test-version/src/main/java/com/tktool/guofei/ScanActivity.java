package com.tktool.guofei;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class ScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private Camera camera;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private HandlerThread decodeThread;
    private Handler decodeHandler;
    private Reader reader;
    private boolean decoding = false;
    private boolean cameraReady = false;
    private Rect scanRect;
    private Paint scanPaint;
    private View scanOverlay;

    // 上次成功识别的时间，防止重复
    private long lastDecodeTime = 0;
    private static final long DECODE_INTERVAL = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏无标题
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.BLACK);

        // 相机预览
        surfaceView = new SurfaceView(this);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        layout.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // 扫码框叠加层（半透明遮罩 + 方形框）
        scanOverlay = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth();
                int h = getHeight();

                int frameSize = (int) (Math.min(w, h) * 0.6);
                int left = (w - frameSize) / 2;
                int top = (h - frameSize) / 2;
                int right = left + frameSize;
                int bottom = top + frameSize;

                scanRect = new Rect(left, top, right, bottom);

                Paint darkPaint = new Paint();
                darkPaint.setColor(Color.argb(160, 0, 0, 0));
                darkPaint.setStyle(Paint.Style.FILL);

                // 绘制四个暗角
                canvas.drawRect(0, 0, w, top, darkPaint);
                canvas.drawRect(0, bottom, w, h, darkPaint);
                canvas.drawRect(0, top, left, bottom, darkPaint);
                canvas.drawRect(right, top, w, bottom, darkPaint);

                // 边框
                Paint borderPaint = new Paint();
                borderPaint.setColor(Color.argb(200, 106, 154, 122));
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(4);
                canvas.drawRoundRect(new RectF(left, top, right, bottom), 16, 16, borderPaint);

                // 四角装饰
                Paint cornerPaint = new Paint();
                cornerPaint.setColor(Color.argb(255, 106, 154, 122));
                cornerPaint.setStyle(Paint.Style.STROKE);
                cornerPaint.setStrokeWidth(6);
                int cornerLen = 40;

                // 左上
                canvas.drawLine(left, top + cornerLen, left, top, cornerPaint);
                canvas.drawLine(left, top, left + cornerLen, top, cornerPaint);
                // 右上
                canvas.drawLine(right - cornerLen, top, right, top, cornerPaint);
                canvas.drawLine(right, top, right, top + cornerLen, cornerPaint);
                // 左下
                canvas.drawLine(left, bottom - cornerLen, left, bottom, cornerPaint);
                canvas.drawLine(left, bottom, left + cornerLen, bottom, cornerPaint);
                // 右下
                canvas.drawLine(right - cornerLen, bottom, right, bottom, cornerPaint);
                canvas.drawLine(right, bottom, right, bottom - cornerLen, cornerPaint);
            }
        };
        layout.addView(scanOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // 提示文字
        TextView hint = new TextView(this);
        hint.setText("将二维码放入框内，自动识别");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(16);
        hint.setAlpha(0.8f);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        hintParams.gravity = android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.BOTTOM;
        hintParams.bottomMargin = 120;
        layout.addView(hint, hintParams);

        // 关闭按钮
        ImageButton closeBtn = new ImageButton(this);
        closeBtn.setBackgroundColor(Color.argb(80, 0, 0, 0));
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeBtn.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        closeBtn.setPadding(12, 12, 12, 12);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                (int) (64 * getResources().getDisplayMetrics().density),
                (int) (64 * getResources().getDisplayMetrics().density));
        closeParams.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
        closeParams.topMargin = 48;
        closeParams.rightMargin = 16;
        layout.addView(closeBtn, closeParams);

        setContentView(layout);

        // ZXing 解码器
        reader = new MultiFormatReader();

        // 后台解码线程
        decodeThread = new HandlerThread("decode-thread");
        decodeThread.start();
        decodeHandler = new Handler(decodeThread.getLooper());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Android 6+ 运行时权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
                return;
            }
        }
        openCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            try {
                camera.stopPreview();
            } catch (Exception e) {}
            startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    private void openCamera() {
        try {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            if (camera == null) {
                camera = Camera.open(0);
            }
            startPreview();
        } catch (Exception e) {
            Toast.makeText(this, "相机打开失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void startPreview() {
        if (camera == null || surfaceHolder == null) return;

        try {
            Camera.Parameters params = camera.getParameters();

            // 找到最合适的预览尺寸
            Camera.Size bestSize = getBestPreviewSize(params);
            params.setPreviewSize(bestSize.width, bestSize.height);

            // 自动对焦：优先使用 continuous-picture，不支持的用 auto + 定时触发
            List<String> focusModes = params.getSupportedFocusModes();
            boolean useContinuousFocus = false;
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                    useContinuousFocus = true;
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    useContinuousFocus = true;
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }

            camera.setParameters(params);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(surfaceHolder);
            camera.setPreviewCallback(this);
            camera.startPreview();
            cameraReady = true;

            // 启动定时自动对焦（每 2 秒触发一次）
            startAutoFocus(useContinuousFocus);
        } catch (Exception e) {
            cameraReady = false;
        }
    }

    // 定时自动对焦，解决部分手机对不上焦的问题
    private void startAutoFocus(boolean continuousMode) {
        final Handler mainHandler = new Handler(getMainLooper());
        final Runnable focusRunnable = new Runnable() {
            @Override
            public void run() {
                if (camera == null || !cameraReady) return;
                try {
                    // 如果不是 continuous 模式，每次主动触发 autoFocus
                    if (!continuousMode) {
                        camera.autoFocus(new Camera.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success, Camera camera) {
                                // 对焦完成
                            }
                        });
                    } else {
                        // continuous 模式下也偶尔触发一下，确保对焦生效
                        camera.cancelAutoFocus();
                        camera.autoFocus(new Camera.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success, Camera camera) {}
                        });
                    }
                } catch (Exception e) {
                    // 忽略对焦异常
                }
                mainHandler.postDelayed(this, 2000);
            }
        };
        // 首次延迟 500ms，等相机完全启动后再对焦
        mainHandler.postDelayed(focusRunnable, 500);
    }

    private Camera.Size getBestPreviewSize(Camera.Parameters params) {
        List<Camera.Size> sizes = params.getSupportedPreviewSizes();
        if (sizes == null || sizes.isEmpty()) return params.getPreviewSize();

        int targetW = 1920;
        Camera.Size best = sizes.get(0);
        int minDiff = Math.abs(best.width - targetW);

        for (Camera.Size size : sizes) {
            // 优先选 16:9，然后选分辨率接近 1920 的
            float ratio = (float) size.width / size.height;
            if (ratio > 1.7 && ratio < 1.8) {
                int diff = Math.abs(size.width - targetW);
                if (diff < minDiff) {
                    minDiff = diff;
                    best = size;
                }
            }
        }
        return best;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (!cameraReady || decoding) return;

        // 防止过于频繁的解码
        long now = System.currentTimeMillis();
        if (now - lastDecodeTime < DECODE_INTERVAL) return;

        Camera.Size size = camera.getParameters().getPreviewSize();
        final int previewW = size.width;
        final int previewH = size.height;
        final byte[] dataCopy = data.clone();
        final Rect cropRect = scanRect != null ? new Rect(scanRect) : null;

        decoding = true;
        decodeHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    String result = decodeQR(dataCopy, previewW, previewH, cropRect);
                    if (result != null && !result.isEmpty()) {
                        lastDecodeTime = System.currentTimeMillis();
                        final String finalResult = result;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Intent data = new Intent();
                                data.putExtra("scan_result", finalResult);
                                setResult(RESULT_OK, data);
                                finish();
                            }
                        });
                    }
                } catch (Exception e) {
                    // 忽略解码异常
                }
                decoding = false;
            }
        });
    }

    private String decodeQR(byte[] data, int width, int height, Rect cropRect) {
        try {
            PlanarYUVLuminanceSource source;

            if (cropRect != null && surfaceView != null) {
                // 将屏幕坐标映射到预览帧坐标
                int viewW = surfaceView.getWidth();
                int viewH = surfaceView.getHeight();

                // 预览数据是横着的，需要旋转映射
                float scaleX = (float) width / viewH;
                float scaleY = (float) height / viewW;

                int left = (int) (cropRect.top * scaleX);
                int top = (int) ((viewW - cropRect.right) * scaleY);
                int cropW = (int) (cropRect.height() * scaleX);
                int cropH = (int) (cropRect.width() * scaleY);

                // 边界检查
                left = Math.max(0, left);
                top = Math.max(0, top);
                cropW = Math.min(cropW, width - left);
                cropH = Math.min(cropH, height - top);

                if (cropW > 0 && cropH > 0) {
                    source = new PlanarYUVLuminanceSource(data, width, height,
                            left, top, cropW, cropH, false);
                } else {
                    source = new PlanarYUVLuminanceSource(data, width, height,
                            0, 0, width, height, false);
                }
            } else {
                source = new PlanarYUVLuminanceSource(data, width, height,
                        0, 0, width, height, false);
            }

            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = reader.decode(bitmap);
            return result != null ? result.getText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void releaseCamera() {
        cameraReady = false;
        if (camera != null) {
            try {
                camera.cancelAutoFocus();
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {}
            camera = null;
        }
    }

    @Override
    protected void onDestroy() {
        releaseCamera();
        if (decodeThread != null) {
            decodeThread.quitSafely();
        }
        super.onDestroy();
    }
}
