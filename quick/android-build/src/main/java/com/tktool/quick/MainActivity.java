package com.tktool.quick;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

public class MainActivity extends Activity {
    private WebView webView;
    private PermissionRequest pendingPermissionRequest;
    private static final int REQUEST_SCAN = 1003;

    private boolean pendingClipboardRead = false;
    private String pendingPasteText = null;

    // 快速生成模式（悬浮窗调用）
    private boolean quickGenerateMode = false;
    private String quickBtnKey;
    private String quickClipboardText;
    private boolean quickFinished = false;
    private boolean pageLoaded = false;
    private boolean quickNeedReadClipboard = false;
    private Runnable quickTimeoutRunnable;
    private SharedPreferences usageSp;
    private boolean pendingStartScan = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        usageSp = getSharedPreferences("usage_counts", MODE_PRIVATE);

        // 检查是否为快速生成模式
        Intent intent = getIntent();
        if (intent.hasExtra("quick_btn_key")) {
            quickGenerateMode = true;
            quickBtnKey = intent.getStringExtra("quick_btn_key");
            // 两种方式：直接传文本 或 让Activity自己读剪贴板
            if (intent.hasExtra("quick_clipboard")) {
                quickClipboardText = intent.getStringExtra("quick_clipboard");
            } else if (intent.getBooleanExtra("quick_read_clipboard", false)) {
                // 标记需要读剪贴板，等页面加载完 + Activity获取焦点后读取
                quickNeedReadClipboard = true;
            }
        }

        // 检查是否有待粘贴的剪贴板内容
        if (intent.hasExtra("paste_clipboard")) {
            pendingPasteText = intent.getStringExtra("paste_clipboard");
        }

        webView = new WebView(this);
        setContentView(webView);

        // 处理分享传入的文本
        handleShareIntent(getIntent());
        if (getIntent().getBooleanExtra("read_clipboard", false)) {
            pendingClipboardRead = true;
        }
        if (getIntent().getBooleanExtra("start_scan", false)) {
            pendingStartScan = true;
        }

        WebSettings settings = webView.getSettings();
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
                if (url == null || !url.contains("doctorstrange1122")) return;

                pageLoaded = true;

                // 页面加载完，同步按钮配置到悬浮窗
                syncButtonConfigsToFloating();

                // 快速生成模式：页面加载完成后再执行
                if (quickGenerateMode && !quickFinished) {
                    if (quickNeedReadClipboard) {
                        // 优先从输入框读取内容（避免重复读剪贴板），输入框为空才读剪贴板
                        webView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (quickFinished) return;
                                startQuickGenerateFromInputOrClipboard();
                            }
                        }, 300);
                    } else if (quickClipboardText != null) {
                        webView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!quickFinished) callQuickGenerate();
                            }
                        }, 200);
                    }
                }

                // 待粘贴剪贴板内容
                if (pendingPasteText != null) {
                    final String text = pendingPasteText;
                    pendingPasteText = null;
                    webView.postDelayed(new Runnable() {
                        @Override
                        public void run() { pasteTextToInput(text); }
                    }, 200);
                }

                // 待读取剪贴板
                if (pendingClipboardRead) {
                    pendingClipboardRead = false;
                    readClipboardAndFill();
                }

                // 待启动扫码
                if (pendingStartScan) {
                    pendingStartScan = false;
                    webView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startNativeScanActivity();
                        }
                    }, 300);
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

        webView.loadUrl("https://doctorstrange1122.github.io/tktool/quick/?v=" + System.currentTimeMillis());
    }

    // 同步按钮配置到悬浮窗（从网页JS读取配置）
    private void syncButtonConfigsToFloating() {
        if (!isFloatingServiceRunning()) return;
        webView.evaluateJavascript(
            "(function(){" +
            "if (window.floatBtnConfigs && Array.isArray(window.floatBtnConfigs)) {" +
            "  var arr = [];" +
            "  for (var i = 0; i < window.floatBtnConfigs.length; i++) {" +
            "    var c = window.floatBtnConfigs[i];" +
            "    arr.push(c.key, c.label, c.countKey || c.key);" +
            "  }" +
            "  return JSON.stringify(arr);" +
            "}" +
            "return '';" +
            "})()",
            new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (value == null || value.isEmpty() || "null".equals(value) || "\"\"".equals(value)) return;
                    try {
                        // 去掉两端的引号
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        // 转义处理
                        value = value.replace("\\\"", "\"").replace("\\\\", "\\");
                        // 解析JSON数组
                        org.json.JSONArray jsonArr = new org.json.JSONArray(value);
                        String[] configs = new String[jsonArr.length()];
                        for (int i = 0; i < jsonArr.length(); i++) {
                            configs[i] = jsonArr.getString(i);
                        }
                        if (configs.length > 0) {
                            Intent intent = new Intent(MainActivity.this, FloatingService.class);
                            intent.putExtra("btn_configs", configs);
                            startService(intent);
                        }
                    } catch (Exception e) { /* ignore parse errors */ }
                }
            }
        );
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
        // 启动后立即同步按钮配置
        webView.postDelayed(new Runnable() {
            @Override
            public void run() { syncButtonConfigsToFloating(); }
        }, 500);
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
                    startNativeScanActivity();
                }
            });
        }

        @JavascriptInterface
        public void saveImage(final String base64Data) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
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
        public void copyToClipboard(final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("tktool", text);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(MainActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void readClipboard() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    readClipboardAndFill();
                }
            });
        }

        @JavascriptInterface
        public String toggleFloatingWindow() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(MainActivity.this)) {
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

        @JavascriptInterface
        public void setFloatingOpacity(final int percent) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isFloatingServiceRunning()) {
                        Intent intent = new Intent(MainActivity.this, FloatingService.class);
                        intent.putExtra("alpha", percent);
                        startService(intent);
                    }
                }
            });
        }

        @JavascriptInterface
        public void onQuickGenerateResult(final String resultJson) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleQuickGenerateResult(resultJson);
                }
            });
        }

        @JavascriptInterface
        public void onUsageUpdate(final String countsJson) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isFloatingServiceRunning()) {
                        Intent intent = new Intent(MainActivity.this, FloatingService.class);
                        intent.putExtra("usage_update", countsJson);
                        startService(intent);
                    }
                }
            });
        }

        @JavascriptInterface
        public void openApp(final String packageName, final String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if ("com.tmall.wireless".equals(packageName)) {
                        openQuickDianTao(url);
                    } else if ("com.eg.android.AlipayGphone".equals(packageName)) {
                        openQuickAlipay(url);
                    } else {
                        openQuickTaobao(url);
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean isHarmonyOS() {
            try {
                Class<?> clazz = Class.forName("android.os.SystemProperties");
                java.lang.reflect.Method getMethod = clazz.getMethod("get", String.class);
                String harmonyVersion = (String) getMethod.invoke(null, "ro.build.version.harmonyos");
                if (harmonyVersion != null && !harmonyVersion.isEmpty()) return true;
                String osName = (String) getMethod.invoke(null, "ro.product.system.name");
                if (osName != null && osName.toLowerCase().contains("harmony")) return true;
            } catch (Exception e) { /* ignore */ }
            return false;
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

    // 启动原生扫码Activity（先检查相机权限）
    private void startNativeScanActivity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
                return;
            }
        }
        Intent intent = new Intent(MainActivity.this, ScanActivity.class);
        startActivityForResult(intent, REQUEST_SCAN);
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

        if (intent.hasExtra("paste_clipboard")) {
            String text = intent.getStringExtra("paste_clipboard");
            if (text != null && webView != null && webView.getUrl() != null
                    && webView.getUrl().contains("doctorstrange1122")) {
                pasteTextToInput(text);
            } else {
                pendingPasteText = text;
            }
        }

        if (intent.getBooleanExtra("read_clipboard", false)) {
            pendingClipboardRead = true;
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

        if (intent.getBooleanExtra("start_scan", false)) {
            pendingStartScan = true;
            if (pageLoaded && webView.getUrl() != null
                    && webView.getUrl().contains("doctorstrange1122")) {
                pendingStartScan = false;
                webView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startNativeScanActivity();
                    }
                }, 200);
            }
        }

        if (intent.hasExtra("quick_btn_key")) {
            quickGenerateMode = true;
            quickFinished = false;
            cancelQuickTimeout();
            quickBtnKey = intent.getStringExtra("quick_btn_key");

            if (intent.hasExtra("quick_clipboard")) {
                quickClipboardText = intent.getStringExtra("quick_clipboard");
                quickNeedReadClipboard = false;
                // 如果页面已加载完成，直接调用生成
                if (pageLoaded) {
                    webView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!quickFinished) callQuickGenerate();
                        }
                    }, 200);
                }
            } else if (intent.getBooleanExtra("quick_read_clipboard", false)) {
                quickNeedReadClipboard = true;
                quickClipboardText = null;
                // 如果页面已加载，先看输入框有没有内容，没有再读剪贴板
                if (pageLoaded) {
                    webView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (quickFinished) return;
                            startQuickGenerateFromInputOrClipboard();
                        }
                    }, 200);
                }
            }
        }
    }

    private void handleShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && !sharedText.isEmpty() && webView != null) {
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

    private void pasteTextToInput(String text) {
        if (text == null || webView == null) return;
        final String escaped = text.replace("\\", "\\\\")
                .replace("'", "\\'").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
        webView.evaluateJavascript(
            "if (window.setInputText) { window.setInputText('" + escaped + "'); }" +
            " else { var el = document.getElementById('productLink'); if (el) el.value = '" + escaped + "'; }",
            null);
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
                if (uri != null) content = uri.toString();
            }
            if (content == null) {
                content = item.coerceToText(MainActivity.this).toString().trim();
            }

            if (content == null || content.isEmpty()) {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
                return;
            }

            pasteTextToInput(content);
            Toast.makeText(this, "已粘贴到输入框", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "读取失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 快速生成专用：读取剪贴板文本，失败返回null
    private String readClipboardTextForQuick() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;

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
                if (uri != null) content = uri.toString();
            }
            if (content == null) {
                content = item.coerceToText(MainActivity.this).toString().trim();
            }

            if (content == null || content.isEmpty()) return null;
            return content;
        } catch (Exception e) {
            return null;
        }
    }

    // 快速生成：优先从输入框读内容，输入框为空再读剪贴板
    private void startQuickGenerateFromInputOrClipboard() {
        if (webView == null || quickFinished) return;

        // 先读取输入框内容
        webView.evaluateJavascript(
            "(function(){" +
            "var el = document.getElementById('productLink');" +
            "return el ? (el.value || '') : '';" +
            "})()",
            new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (quickFinished) return;

                    String inputText = null;
                    if (value != null && !"null".equals(value) && !"\"\"".equals(value)) {
                        // 去掉两端引号和转义
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            inputText = value.substring(1, value.length() - 1)
                                    .replace("\\\"", "\"").replace("\\\\", "\\")
                                    .replace("\\n", "\n").replace("\\r", "");
                        } else {
                            inputText = value;
                        }
                    }

                    if (inputText != null && !inputText.trim().isEmpty()) {
                        // 输入框有内容，直接用
                        quickClipboardText = inputText.trim();
                        quickNeedReadClipboard = false;
                        callQuickGenerate();
                    } else {
                        // 输入框为空，读剪贴板
                        String clipboardText = readClipboardTextForQuick();
                        if (clipboardText == null || clipboardText.trim().isEmpty()) {
                            quickFinishWithError("剪贴板为空，请先复制淘口令");
                            return;
                        }
                        quickClipboardText = clipboardText;
                        quickNeedReadClipboard = false;
                        pasteTextToInput(clipboardText);
                        callQuickGenerate();
                    }
                }
            }
        );
    }

    private void callQuickGenerate() {
        if (webView == null || quickFinished) return;

        final String escaped = quickClipboardText.replace("\\", "\\\\")
                .replace("'", "\\'").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");

        final String jsCode = "(function(){" +
                "if (window.floatGenerate) {" +
                "  window.floatGenerate('" + escaped + "', '" + quickBtnKey + "').then(function(r){" +
                "    if (window.NativeBridge && window.NativeBridge.onQuickGenerateResult) " +
                "window.NativeBridge.onQuickGenerateResult(r);" +
                "  }).catch(function(e){" +
                "    if (window.NativeBridge && window.NativeBridge.onQuickGenerateResult) " +
                "window.NativeBridge.onQuickGenerateResult(JSON.stringify({success:false,error:e.message||'生成失败'}));" +
                "  });" +
                "} else {" +
                "  if (window.NativeBridge && window.NativeBridge.onQuickGenerateResult) " +
                "window.NativeBridge.onQuickGenerateResult(JSON.stringify({success:false,error:'页面未加载完成'}));" +
                "}" +
                "})()";

        try {
            webView.evaluateJavascript(jsCode, null);

            // 设置超时保护：10秒未返回则提示超时（不关闭Activity）
            quickTimeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!quickFinished) {
                        quickFinishWithError("生成超时，请检查网络后重试");
                    }
                }
            };
            webView.postDelayed(quickTimeoutRunnable, 10000);
        } catch (Exception e) {
            quickFinishWithError("调用失败");
        }
    }

    private void cancelQuickTimeout() {
        if (quickTimeoutRunnable != null) {
            webView.removeCallbacks(quickTimeoutRunnable);
            quickTimeoutRunnable = null;
        }
    }

    private void handleQuickGenerateResult(String resultJson) {
        if (quickFinished) return;
        quickFinished = true;
        cancelQuickTimeout();

        try {
            // 解析JSON结果
            org.json.JSONObject json = new org.json.JSONObject(resultJson);
            boolean success = json.optBoolean("success", false);

            if (success) {
                String link = json.optString("link", "");
                boolean autoJump = json.optBoolean("autoJump", false);
                boolean autoJumpDianTao = json.optBoolean("autoJumpDianTao", false);

                // 保存使用次数
                saveQuickUsageCount(quickBtnKey);

                // 通知悬浮窗：生成完成，关闭三级面板
                notifyFloatingGenerateDone();

                // Java层主动复制链接到剪贴板（确保同步写入完成，避免JS异步复制的时序问题）
                copyLinkToClipboardSync(link);

                if (autoJump || autoJumpDianTao) {
                    final boolean jumpToAlipay = autoJumpDianTao;
                    // 延迟300ms跳转，确保剪贴板写入完成后再唤起APP
                    webView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (jumpToAlipay) {
                                openQuickAlipay(link);
                            } else {
                                openQuickTaobao(link);
                            }
                        }
                    }, 300);
                    // 延迟关闭Activity（仅自动跳转时才关闭）
                    webView.postDelayed(new Runnable() {
                        @Override public void run() {
                            quickGenerateMode = false;
                        }
                    }, 1200);
                } else {
                    // 不跳转：滚动到结果区，保留页面
                    webView.evaluateJavascript(
                        "if (window.scrollToResult) window.scrollToResult();" +
                        " else { var r = document.getElementById('resultArea'); if (r) r.scrollIntoView({behavior:'smooth'}); }",
                        null);
                    quickGenerateMode = false;
                }
            } else {
                String error = json.optString("error", "生成失败");
                quickFinishWithError(error);
            }
        } catch (Exception e) {
            quickFinishWithError("解析结果失败");
        }
    }

    private void notifyFloatingGenerateDone() {
        try {
            Intent intent = new Intent(this, FloatingService.class);
            intent.putExtra("generate_done", true);
            startService(intent);
        } catch (Exception e) { /* ignore */ }
    }

    private void saveQuickUsageCount(String key) {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-M-d",
                    java.util.Locale.getDefault()).format(new java.util.Date());
            String savedDate = usageSp.getString("date", "");
            SharedPreferences.Editor editor = usageSp.edit();
            if (!today.equals(savedDate)) {
                editor.putString("date", today);
            }
            int count = usageSp.getInt(key, 0) + 1;
            editor.putInt(key, count);
            editor.apply();
        } catch (Exception e) { /* ignore */ }
    }

    // 同步复制链接到剪贴板（Java层直接操作，确保写入完成）
    private void copyLinkToClipboardSync(String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("tktool", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "链接已复制到剪贴板", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // 忽略复制失败
        }
    }

    private void openQuickTaobao(String url) {
        // 第一优先：tbopen:// 淘宝官方 Deep Link（最可靠，支持直接打开H5页面）
        try {
            String tbopen = "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&h5Url=" +
                    Uri.encode(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(tbopen));
            intent.setPackage("com.taobao.taobao");
            startActivity(intent);
            return;
        } catch (Exception e1) {
            // tbopen 失败，继续尝试其他方式
        }

        // 第二优先：tbopen:// 不加包名（让系统找合适的应用）
        try {
            String tbopen = "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&h5Url=" +
                    Uri.encode(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(tbopen));
            startActivity(intent);
            return;
        } catch (Exception e2) {
            // 继续尝试 taobao://
        }

        // 第三优先：taobao:// scheme
        try {
            String taobaoUrl = "taobao://" + url.replace("https://", "").replace("http://", "");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(taobaoUrl));
            intent.setPackage("com.taobao.taobao");
            startActivity(intent);
            return;
        } catch (Exception e3) {
            // 继续尝试
        }

        // 第四优先：taobao:// 不加包名
        try {
            String taobaoUrl = "taobao://" + url.replace("https://", "").replace("http://", "");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(taobaoUrl));
            startActivity(intent);
            return;
        } catch (Exception e4) {
            Toast.makeText(this, "跳转失败，请检查是否安装淘宝", Toast.LENGTH_SHORT).show();
        }
    }

    // 跳转天猫APP（com.tmall.wireless）
    private void openQuickDianTao(String url) {
        // 第一优先：直接用HTTPS URL打开天猫（如果天猫注册了HTTP/HTTPS的Intent Filter）
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.tmall.wireless");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception e1) {
            // 天猫未注册HTTP处理器，继续尝试
        }

        // 第二优先：直接 LaunchIntent 打开天猫（链接已在剪贴板，用户可粘贴）
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.tmall.wireless");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(this, "链接已复制，请在天猫中粘贴使用", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception e2) {
            // 继续
        }

        // 第三优先：tmall:// scheme 不带URL参数（仅启动天猫）
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tmall://"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this, "链接已复制，请在天猫中粘贴使用", Toast.LENGTH_LONG).show();
            return;
        } catch (Exception e3) {
            // 继续
        }

        Toast.makeText(this, "跳转失败，请检查是否安装天猫", Toast.LENGTH_SHORT).show();
    }

    // 跳转支付宝APP（com.eg.android.AlipayGphone）
    // 使用 alipays://platformapi/startapp?appId=20000067&url= 打开支付宝内置浏览器加载指定页面
    private void openQuickAlipay(String url) {
        // 第一优先：alipays:// 打开支付宝内置浏览器并加载指定页面
        try {
            String alipayScheme = "alipays://platformapi/startapp?appId=20000067&url=" + Uri.encode(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(alipayScheme));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception e1) {
            // 继续
        }

        // 第二优先：alipay:// 打开支付宝内置浏览器并加载指定页面
        try {
            String alipayScheme = "alipay://platformapi/startapp?appId=20000067&url=" + Uri.encode(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(alipayScheme));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception e2) {
            // 继续
        }

        // 第三优先：直接 LaunchIntent 打开支付宝（链接已在剪贴板）
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.eg.android.AlipayGphone");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(this, "链接已复制，请在支付宝中粘贴使用", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception e3) {
            // 继续
        }

        Toast.makeText(this, "跳转失败，请检查是否安装支付宝", Toast.LENGTH_SHORT).show();
    }

    private void quickFinishWithError(String error) {
        // 错误时也通知悬浮窗关闭三级面板
        notifyFloatingGenerateDone();
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        quickFinished = true;
        quickGenerateMode = false;
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
