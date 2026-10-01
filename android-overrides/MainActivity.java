package com.arif.cybertools;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends BridgeActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        this.bridge.getWebView().post(new Runnable() {
            @Override
            public void run() {
                setupWebView();
            }
        });
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

        webView.addJavascriptInterface(
            new NativeDownloadBridge(this, webView),
            "AndroidDownload"
        );

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition,
                                        String mimetype, long contentLength) {
                try {
                    if (url.startsWith("data:")) {
                        handleDataUrl(url);
                    } else if (url.startsWith("blob:")) {
                        convertBlobAndDownload(url);
                    } else {
                        handleHttpDownload(url, userAgent, contentDisposition, mimetype);
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                        "Download failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            }
        });

        webView.setOnLongClickListener(v -> true);
        webView.setHapticFeedbackEnabled(false);
    }

    private void handleHttpDownload(String url, String userAgent,
                                    String contentDisposition, String mimetype) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimetype);
            request.addRequestHeader("User-Agent", userAgent);
            request.setDescription("Downloading...");
            request.setTitle(fileName);
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, fileName
            );

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
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            );
            if (!downloadsDir.exists()) downloadsDir.mkdirs();
            File outFile = new File(downloadsDir, fileName);
            FileOutputStream fos = new FileOutputStream(outFile);
            fos.write(bytes);
            fos.close();

            android.media.MediaScannerConnection.scanFile(
                MainActivity.this,
                new String[]{outFile.getAbsolutePath()},
                new String[]{mime},
                null
            );

            Toast.makeText(MainActivity.this,
                "Saved to Downloads: " + fileName,
                Toast.LENGTH_LONG).show();
            notifyJS("onNativeDownloadComplete", fileName);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this,
                "Save failed: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
            notifyJS("onNativeDownloadError", e.getMessage());
        }
    }

    private void convertBlobAndDownload(final String blobUrl) {
        String js = "(function(){" +
            "fetch('" + blobUrl + "')" +
            ".then(r => r.blob())" +
            ".then(b => {" +
            "  const reader = new FileReader();" +
            "  reader.onloadend = () => {" +
            "    window.AndroidDownload.download(reader.result, 'cybertools.png');" +
            "  };" +
            "  reader.readAsDataURL(b);" +
            "});" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    private void notifyJS(String method, String arg) {
        final String safeArg = arg.replace("'", "\\'");
        final String js = "if(typeof window." + method + " === 'function'){ window." +
                          method + "('" + safeArg + "'); }";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    public class NativeDownloadBridge {
        Context context;
        WebView webView;

        NativeDownloadBridge(Context c, WebView w) {
            this.context = c;
            this.webView = w;
        }

        @JavascriptInterface
        public void download(String url, String filename) {
            if (url == null) return;
            if (url.startsWith("data:")) {
                handleDataUrl(url);
            } else {
                handleHttpDownload(url, "", "", "application/octet-stream");
            }
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
