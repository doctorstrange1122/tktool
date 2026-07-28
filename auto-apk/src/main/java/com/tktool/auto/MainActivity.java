package com.tktool.auto;

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

public class MainActivity extends Activity {
    private WebView webView;
    private PermissionRequest pendingPermissionRequest;
    private static final int REQUEST_SCAN = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        setContentView(webView);

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
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
