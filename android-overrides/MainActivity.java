package com.arif.cybertools;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends BridgeActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // সাদা স্ক্রিন দূর করার জন্য WebView-এর ব্যাকগ্রাউন্ড কালো করা হলো
        this.getBridge().getWebView().setBackgroundColor(Color.BLACK);
        
        hideSystemUI();

        this.bridge.getWebView().post(new Runnable() {
            @Override
            public void run() {
                setupWebView();
            }
        });
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    private void setupWebView() {
        webView = this.bridge.getWebView();
        if (webView == null) return;

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new NativeDownloadBridge(this, webView), "AndroidDownload");
        webView.addJavascriptInterface(new ClipboardBridge(this), "AndroidClipboard");

        // ✅ নেটিভ ডাউনলোড লিসেনার
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                try {
                    if (url.startsWith("data:")) {
                        handleDataUrl(url);
                    } else if (url.startsWith("blob:")) {
                        convertBlobAndDownload(url);
                    } else {
                        handleHttpDownload(url, userAgent, contentDisposition, mimetype);
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        
        webView.setHapticFeedbackEnabled(false);

        // 🌟 গ্লোবাল ক্লিপবোর্ড ফিক্স 🌟
        // প্রতিবার নতুন পেজ লোড হলে ক্লিপবোর্ড স্ক্রিপ্টটি ইনজেক্ট হবে
        webView.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectClipboardScript(view);
            }
        });
    }

    private void injectClipboardScript(WebView view) {
        String js = "(function() {" +
            "  if (!navigator.clipboard) { navigator.clipboard = {}; }" +
            "  navigator.clipboard.readText = function() {" +
            "    return new Promise(function(resolve, reject) {" +
            "      try { resolve(window.AndroidClipboard.paste()); }" +
            "      catch(e) { reject(e); }" +
            "    });" +
            "  };" +
            "  navigator.clipboard.writeText = function(text) {" +
            "    return new Promise(function(resolve, reject) {" +
            "      try { window.AndroidClipboard.copy(text); resolve(); }" +
            "      catch(e) { reject(e); }" +
            "    });" +
            "  };" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    private void handleHttpDownload(String url, String userAgent, String contentDisposition, String mimetype) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimetype);
            request.addRequestHeader("User-Agent", userAgent);
            request.setDescription("Downloading...");
            request.setTitle(fileName);
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                notifyJS("onNativeDownloadComplete", fileName);
            }
        } catch (Exception e) {
            notifyJS("onNativeDownloadError", e.getMessage());
        }
    }

    private void handleDataUrl(String dataUrl) {
        try {
            String[] parts = dataUrl.split(",", 2);
            String meta = parts[0];
            String base64Data = parts[1];

            String mime = "image/png";
            if (meta.contains("image/jpeg") || meta.contains("image/jpg")) mime = "image/jpeg";
            else if (meta.contains("image/webp")) mime = "image/webp";
            else if (meta.contains("application/pdf")) mime = "application/pdf";

            String ext = mime.split("/")[1];
            String fileName = "cybertools-" + System.currentTimeMillis() + "." + ext;

            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) downloadsDir.mkdirs();
            File outFile = new File(downloadsDir, fileName);
            FileOutputStream fos = new FileOutputStream(outFile);
            fos.write(bytes);
            fos.close();

            android.media.MediaScannerConnection.scanFile(MainActivity.this, new String[]{outFile.getAbsolutePath()}, new String[]{mime}, null);

            Toast.makeText(MainActivity.this, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
            notifyJS("onNativeDownloadComplete", fileName);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            notifyJS("onNativeDownloadError", e.getMessage());
        }
    }

    private void convertBlobAndDownload(final String blobUrl) {
        String js = "(function(){" +
            "fetch('" + blobUrl + "').then(r => r.blob()).then(b => {" +
            "  const reader = new FileReader();" +
            "  reader.onloadend = () => { window.AndroidDownload.download(reader.result, 'cybertools.png'); };" +
            "  reader.readAsDataURL(b);" +
            "});})();";
        webView.evaluateJavascript(js, null);
    }

    private void notifyJS(String method, String arg) {
        final String safeArg = arg.replace("'", "\\'");
        final String js = "if(typeof window." + method + " === 'function'){ window." + method + "('" + safeArg + "'); }";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    public class NativeDownloadBridge {
        Context context; WebView webView;
        NativeDownloadBridge(Context c, WebView w) { this.context = c; this.webView = w; }
        @JavascriptInterface
        public void download(String url, String filename) {
            if (url == null) return;
            if (url.startsWith("data:")) handleDataUrl(url);
            else handleHttpDownload(url, "", "", "application/octet-stream");
        }
    }

    public class ClipboardBridge {
        Context context;
        ClipboardBridge(Context c) { this.context = c; }
        
        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied Text", text);
            if (clipboard != null) clipboard.setPrimaryClip(clip);
        }

        @JavascriptInterface
        public String paste() {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                if (item != null && item.getText() != null) {
                    return item.getText().toString();
                }
            }
            return "";
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
            }
