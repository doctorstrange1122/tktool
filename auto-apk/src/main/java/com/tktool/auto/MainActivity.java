package com.tktool.auto;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

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

    @JavascriptInterface
    public void copyToClipboard(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("tktool", text);
                    clipboard.setPrimaryClip(clip);
                    // 回调网页提示复制成功
                    webView.evaluateJavascript("if(typeof onCopySuccess==='function')onCopySuccess();", null);
                } catch (Exception e) {
                    webView.evaluateJavascript("if(typeof onCopyError==='function')onCopyError('" + e.getMessage() + "');", null);
                }
            }
        });
    }
}
