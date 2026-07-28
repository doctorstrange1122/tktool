package com.tktool.test;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import android.text.Html;

public class MainActivity extends Activity {
    private WebView webView;
    private PermissionRequest pendingPermissionRequest;
    private static final int REQUEST_SCAN = 1003;

    private boolean pendingClipboardRead = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        setContentView(webView);

        // 处理分享传入的文本 或 悬浮窗传入的剪贴板内容
        handleShareIntent(getIntent());
        if (getIntent().getBooleanExtra("read_clipboard", false)) {
            pendingClipboardRead = true;
        }        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new JSBridge(), "NativeBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 页面加载完成后，处理待执行的剪贴板读取
                if (pendingClipboardRead && url != null && url.contains("doctorstrange1122")) {
                    pendingClipboardRead = false;
                    readClipboardAndFill();
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                view.loadData("<html><body><h2>加载失败</h2><p>" + description + "</p></body></html>", "text/html", "UTF-8");
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("taobao://") || url.startsWith("tmall://") ||
                    url.startsWith("alipay://") || url.startsWith("alipays://") ||
                    url.startsWith("tbopen://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                String[] resources = request.getResources();
                boolean needsCamera = false;
                for (String r : resources) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                        needsCamera = true;
                        break;
                    }
                }
                if (needsCamera) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                        } else {
                            pendingPermissionRequest = request;
                            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
                        }
                    } else {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    }
                } else {
                    request.grant(resources);
                }
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                pendingPermissionRequest = null;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void startFloatingService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 2002);
                return;
            }
        }
        Intent serviceIntent = new Intent(this, FloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "悬浮窗已开启", Toast.LENGTH_SHORT).show();
    }

    private void stopFloatingService() {
        Intent serviceIntent = new Intent(this, FloatingService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopFloatingService();
    }

    public class JSBridge {
        @JavascriptInterface
        public void startNativeScan() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
                            return;
                        }
                    }
                    Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                    startActivityForResult(intent, REQUEST_SCAN);
                }
            });
        }

        @JavascriptInterface
        public void saveImage(final String base64Data) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 去掉 data:image/png;base64, 前缀
                        String pureBase64 = base64Data;
                        if (base64Data.contains(",")) {
                            pureBase64 = base64Data.substring(base64Data.indexOf(",") + 1);
                        }
                        byte[] decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                        String filename = "qrcode_" + System.currentTimeMillis() + ".png";

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
                            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                            if (uri != null) {
                                OutputStream os = getContentResolver().openOutputStream(uri);
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                                os.close();
                                Toast.makeText(MainActivity.this, "已保存到图库", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                            File file = new File(dir, filename);
                            FileOutputStream fos = new FileOutputStream(file);
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.close();
                            // 通知相册刷新
                            Intent mediaScan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            mediaScan.setData(Uri.fromFile(file));
                            sendBroadcast(mediaScan);
                            Toast.makeText(MainActivity.this, "已保存到图库", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public String toggleFloatingWindow() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(MainActivity.this)) {
                    // 没有悬浮窗权限，引导用户开启
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, 2002);
                            Toast.makeText(MainActivity.this, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show();
                        }
                    });
                    return "need_permission";
                }
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isFloatingServiceRunning()) {
                        stopFloatingService();
                    } else {
                        startFloatingService();
                    }
                }
            });
            return "ok";
        }

        @JavascriptInterface
        public boolean isFloatingServiceActive() {
            return isFloatingServiceRunning();
        }
    }

    private boolean isFloatingServiceRunning() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (FloatingService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCAN) {
            if (resultCode == RESULT_OK && data != null) {
                String result = data.getStringExtra("scan_result");
                if (result != null && webView != null) {
                    final String escaped = result.replace("\\", "\\\\").replace("'", "\\'")
                            .replace("\n", "\\n").replace("\r", "");
                    webView.evaluateJavascript("onScanResult('" + escaped + "');", null);
                }
            }
        } else if (requestCode == 2002) {
            // 悬浮窗权限返回后重试启动服务
            startFloatingService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    pendingPermissionRequest = null;
                } else {
                    Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                    startActivityForResult(intent, REQUEST_SCAN);
                }
            } else {
                Toast.makeText(this, "相机权限被拒绝", Toast.LENGTH_SHORT).show();
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.deny();
                    pendingPermissionRequest = null;
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShareIntent(intent);
        if (intent.getBooleanExtra("read_clipboard", false)) {
            pendingClipboardRead = true;
            // 延迟读取，确保系统剪贴板同步完成
            webView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (pendingClipboardRead && webView.getUrl() != null
                            && webView.getUrl().contains("doctorstrange1122")) {
                        pendingClipboardRead = false;
                        readClipboardAndFill();
                    }
                }
            }, 800);
        }
    }

    private void handleShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && !sharedText.isEmpty() && webView != null) {
                // 将分享的文本填入输入框并自动生成
                final String escaped = sharedText
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\r", "");
                webView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript(
                            "setInputAndGenerate('" + escaped + "');", null);
                    }
                }, 500);
            }
        }
    }

    private void readClipboardAndFill() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 尝试多种方式读取最近一条剪贴板内容
            String content = null;
            ClipData.Item item = clip.getItemAt(0);

            CharSequence text = item.getText();
            if (text != null && !text.toString().trim().isEmpty()) {
                content = text.toString().trim();
            }

            if (content == null) {
                String html = item.getHtmlText();
                if (html != null && !html.trim().isEmpty()) {
                    content = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
                }
            }

            if (content == null) {
                Uri uri = item.getUri();
                if (uri != null) {
                    content = uri.toString();
                }
            }

            if (content == null) {
                content = item.coerceToText(MainActivity.this).toString().trim();
            }

            if (content == null || content.isEmpty()) {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 只填入输入框，不自动生成
            final String escaped = content
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "");
            webView.evaluateJavascript(
                "document.getElementById('productLink').value = '" + escaped + "';", null);
            Toast.makeText(this, "已粘贴到输入框", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "读取失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
