package com.tktool.quick;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * 透明Activity：承载WebView执行JS，用户只看到loading弹窗
 */
public class FloatGenerateActivity extends Activity {

    private WebView webView;
    private ProgressDialog loadingDialog;
    private String btnKey;
    private String clipboardText;
    private boolean finished = false;

    private static final String PAGE_URL = "https://doctorstrange1122.github.io/tktool/quick/?v=";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 透明背景
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnKey = getIntent().getStringExtra("btn_key");
        clipboardText = getIntent().getStringExtra("clipboard");

        if (btnKey == null || clipboardText == null) {
            finish();
            return;
        }

        // 显示loading
        loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage("正在生成...");
        loadingDialog.setCancelable(true);
        loadingDialog.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(android.content.DialogInterface dialog) {
                finish();
            }
        });
        loadingDialog.show();

        // 创建WebView
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 页面加载完成后延迟调用JS，确保JS完全初始化
                webView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!finished) callFloatGenerate();
                    }
                }, 500);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onGenerateResult(final String resultJson) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleResult(resultJson);
                    }
                });
            }
        }, "FloatBridge");

        // 加载页面
        webView.loadUrl(PAGE_URL + System.currentTimeMillis());

        // 不设置contentView，WebView不显示，只用来执行JS
        // （Activity本身透明，用户只看到loading弹窗）
    }

    private void callFloatGenerate() {
        if (webView == null || finished) return;

        final String escaped = clipboardText.replace("\\", "\\\\")
                .replace("'", "\\'").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");

        final String jsCode = "(function(){" +
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

        try {
            webView.evaluateJavascript(jsCode, null);
        } catch (Exception e) {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.setMessage("调用失败");
            }
            finish();
        }
    }

    private void handleResult(String resultJson) {
        if (finished) return;
        finished = true;

        try {
            if (resultJson.contains("\"success\":true")) {
                int linkStart = resultJson.indexOf("\"link\":\"") + 8;
                int linkEnd = resultJson.indexOf("\"", linkStart);
                final String link = resultJson.substring(linkStart, linkEnd).replace("\\/", "/");

                if (loadingDialog != null && loadingDialog.isShowing()) {
                    loadingDialog.setMessage("生成成功，正在跳转...");
                }

                // 保存使用次数
                saveUsageCount(btnKey);

                // 延迟跳转
                webView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openTaobao(link);
                        finish();
                    }
                }, 300);
            } else {
                String error = "生成失败";
                int errStart = resultJson.indexOf("\"error\":\"");
                if (errStart >= 0) {
                    errStart += 9;
                    int errEnd = resultJson.indexOf("\"", errStart);
                    if (errEnd > errStart) error = resultJson.substring(errStart, errEnd);
                }
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            Toast.makeText(this, "解析结果失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void saveUsageCount(String key) {
        try {
            android.content.SharedPreferences sp =
                    getSharedPreferences("usage_counts", MODE_PRIVATE);
            String today = new java.text.SimpleDateFormat("yyyy-M-d",
                    java.util.Locale.getDefault()).format(new java.util.Date());
            String savedDate = sp.getString("date", "");
            android.content.SharedPreferences.Editor editor = sp.edit();
            if (!today.equals(savedDate)) {
                editor.putString("date", today);
            }
            int count = sp.getInt(key, 0) + 1;
            editor.putInt(key, count);
            editor.apply();
        } catch (Exception e) { /* ignore */ }
    }

    private void openTaobao(String url) {
        try {
            String taobaoUrl = "taobao://" + url.replace("https://", "").replace("http://", "");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(taobaoUrl));
            intent.setPackage("com.taobao.taobao");
            startActivity(intent);
        } catch (Exception e1) {
            try {
                String taobaoUrl = "taobao://" + url.replace("https://", "").replace("http://", "");
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(taobaoUrl));
                startActivity(intent);
            } catch (Exception e2) {
                try {
                    String tbopen = "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&h5Url=" +
                            Uri.encode(url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(tbopen));
                    startActivity(intent);
                } catch (Exception e3) {
                    Toast.makeText(this, "跳转失败，请检查是否安装淘宝", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        finished = true;
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
    }
}
