package com.octop.dashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Thin WebView client for the Octop dashboard. First launch asks for the
 * server address (persisted in SharedPreferences), then loads the dashboard.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "octop";
    private static final String KEY_URL = "server_url";
    private static final int PICK_FILE = 1001;

    private WebView web;
    private ValueCallback<Uri[]> filePicker;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri u = Uri.parse(url);
                String scheme = u.getScheme();
                if (scheme != null && !scheme.equals("http") && !scheme.equals("https")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePicker != null) {
                    filePicker.onReceiveValue(null);
                }
                filePicker = callback;
                try {
                    startActivityForResult(params.createIntent(), PICK_FILE);
                } catch (Exception e) {
                    filePicker = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new ServerPicker(), "Octop");
        setContentView(web);

        String url = prefs().getString(KEY_URL, null);
        if (url == null) {
            showConnectPage();
        } else {
            web.loadUrl(url);
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    /** Built-in first-run page that asks for the Octop server address. */
    private void showConnectPage() {
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>body{font-family:sans-serif;background:#0f172a;color:#e2e8f0;"
            + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0}"
            + ".card{width:82%}h1{font-size:22px;margin:0 0 4px}p{color:#94a3b8;font-size:14px}"
            + "input{width:100%;box-sizing:border-box;padding:12px;font-size:16px;border-radius:8px;"
            + "border:1px solid #334155;background:#1e293b;color:#fff}"
            + "button{margin-top:16px;width:100%;padding:12px;font-size:16px;border-radius:8px;"
            + "border:0;background:#2563eb;color:#fff}</style></head><body><div class='card'>"
            + "<h1>Octop</h1><p>Enter your Octop server address</p>"
            + "<input id='u' placeholder='http://192.168.1.10:8088'>"
            + "<button onclick='Octop.connect(document.getElementById(\"u\").value)'>"
            + "Connect</button></div></body></html>";
        String b64 = Base64.encodeToString(html.getBytes(), Base64.NO_WRAP);
        web.loadData(b64, "text/html; charset=utf-8", "base64");
    }

    /** JS bridge: persist the server URL and open it. */
    private class ServerPicker {
        @JavascriptInterface
        public void connect(String raw) {
            final String url = normalize(raw);
            if (url == null) {
                return;
            }
            prefs().edit().putString(KEY_URL, url).apply();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    web.loadUrl(url);
                }
            });
        }
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String url = raw.trim();
        if (url.isEmpty()) {
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return url;
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE && filePicker != null) {
            filePicker.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePicker = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
