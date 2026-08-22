package com.automind.pinout2;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

public class DetailActivity extends Activity {
    private TextView tvTitle;
    private TextView tvCategory;
    private Button btnBack;
    private Button btnFav;
    private WebView webView;
    private ProgressBar progressBar;

    private int itemId;
    private String title;
    private String category;
    private String source;
    private String htmlRelPath;
    private boolean isFavorite = false;
    private SharedPreferences prefs;

    private static final String[] CANDIDATE_DATA_DIRS = new String[]{
        "/storage/emulated/0/PinOut2/data/",
        "/sdcard/PinOut2/data/",
        "/storage/emulated/0/Download/PinOut2/data/",
        "/data/data/com.termux/files/home/pin_out_2_project/data/"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        prefs = getSharedPreferences("pinout2_prefs", MODE_PRIVATE);

        itemId = getIntent().getIntExtra("id", 0);
        title = getIntent().getStringExtra("title");
        category = getIntent().getStringExtra("category");
        source = getIntent().getStringExtra("source");
        htmlRelPath = getIntent().getStringExtra("html_path");

        if (title == null) title = "Détails Pinout";
        if (category == null) category = "Calculateur";

        isFavorite = prefs.getBoolean("fav_" + itemId, false);

        tvTitle = (TextView) findViewById(R.id.detail_title);
        tvCategory = (TextView) findViewById(R.id.detail_category);
        btnBack = (Button) findViewById(R.id.btn_back);
        btnFav = (Button) findViewById(R.id.detail_btn_fav);
        webView = (WebView) findViewById(R.id.wv_content);
        progressBar = (ProgressBar) findViewById(R.id.pb_detail);

        tvTitle.setText(title);
        tvCategory.setText(category);
        updateFavButton();

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnFav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isFavorite = !isFavorite;
                prefs.edit().putBoolean("fav_" + itemId, isFavorite).apply();
                updateFavButton();
                Toast.makeText(DetailActivity.this, isFavorite ? "Ajouté aux favoris ⭐" : "Retiré des favoris", Toast.LENGTH_SHORT).show();
            }
        });

        setupWebView();
        loadPinoutContent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPinoutContent();
    }

    private void updateFavButton() {
        btnFav.setText(isFavorite ? "★" : "☆");
        btnFav.setTextColor(isFavorite ? Color.parseColor("#F59E0B") : Color.parseColor("#94A3B8"));
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDefaultTextEncodingName("UTF-8");

        webView.setBackgroundColor(Color.parseColor("#0F172A"));
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith("pinout://request_permission")) {
                    requestStoragePermission();
                    return true;
                } else if (url != null && url.startsWith("pinout://refresh")) {
                    loadPinoutContent();
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    return handleResource(request.getUrl());
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (url != null) {
                    return handleResource(Uri.parse(url));
                }
                return super.shouldInterceptRequest(view, url);
            }

            private WebResourceResponse handleResource(Uri uri) {
                if (uri == null) return null;
                String scheme = uri.getScheme();
                if ("file".equalsIgnoreCase(scheme)) {
                    String path = uri.getPath();
                    if (path != null) {
                        File f = new File(path);
                        if (f.exists() && f.isFile()) {
                            try {
                                String mime = getMimeType(f.getName());
                                return new WebResourceResponse(mime, "UTF-8", new FileInputStream(f));
                            } catch (Exception ignored) {}
                        }
                    }
                }
                return null;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Suppress default Android error page
            }
        });
    }

    private String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".htm") || lower.endsWith(".html")) return "text/html";
        return "application/octet-stream";
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                java.lang.reflect.Method method = Environment.class.getMethod("isExternalStorageManager");
                return (Boolean) method.invoke(null);
            } catch (Exception e) {
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1002);
        }
    }

    private void loadPinoutContent() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        if (htmlRelPath == null || htmlRelPath.isEmpty()) {
            showError("Chemin de fichier introuvable.");
            return;
        }

        if (!hasStoragePermission()) {
            showPermissionRequired();
            return;
        }

        File targetFile = null;
        for (String candidate : CANDIDATE_DATA_DIRS) {
            File f = new File(candidate, htmlRelPath);
            if (f.exists()) {
                targetFile = f;
                break;
            }
        }

        if (targetFile != null && targetFile.exists()) {
            try {
                FileInputStream fis = new FileInputStream(targetFile);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
                fis.close();
                byte[] bytes = baos.toByteArray();

                String rawHeader = new String(bytes, 0, Math.min(bytes.length, 1024), "ISO-8859-1");
                String charset = "UTF-8";
                if (rawHeader.toLowerCase().contains("windows-1251")) {
                    charset = "windows-1251";
                }

                String htmlContent = new String(bytes, charset);

                String css = "<style>" +
                             "body { background-color: #0F172A !important; color: #E2E8F0 !important; font-family: -apple-system, Roboto, sans-serif !important; padding: 14px !important; line-height: 1.6 !important; } " +
                             "a { color: #38BDF8 !important; text-decoration: none !important; } " +
                             "img { max-width: 100% !important; height: auto !important; border-radius: 10px !important; box-shadow: 0 4px 10px rgba(0,0,0,0.6) !important; margin: 14px auto !important; display: block !important; background: #1E293B !important; } " +
                             "table { width: 100% !important; border-collapse: collapse !important; margin: 14px 0 !important; background: #1E293B !important; border-radius: 10px !important; overflow: hidden !important; border: 1px solid #334155 !important; } " +
                             "th, td { padding: 10px 12px !important; border: 1px solid #334155 !important; font-size: 13px !important; } " +
                             "th { background-color: #334155 !important; color: #38BDF8 !important; font-weight: bold !important; } " +
                             "h1, h2, h3 { color: #60A5FA !important; } " +
                             "</style>" +
                             "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes'>";

                if (htmlContent.contains("<head>")) {
                    htmlContent = htmlContent.replace("<head>", "<head>" + css);
                } else {
                    htmlContent = css + htmlContent;
                }

                String baseUrl = "file://" + targetFile.getParent() + "/";
                webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null);
            } catch (Exception e) {
                showError("Erreur lors de la lecture du fichier : " + e.getMessage());
            }
        } else {
            showError("Fichier de données introuvable : " + htmlRelPath + "<br><br>Vérifiez que le dossier <b>/sdcard/PinOut2/data/</b> est présent.");
        }
    }

    private void showPermissionRequired() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                      "<style>" +
                      "body { background:#0F172A; color:#E2E8F0; font-family:sans-serif; text-align:center; padding:30px 20px; }" +
                      ".card { background:#1E293B; border-radius:12px; padding:24px; border:1px solid #334155; max-width:400px; margin:auto; }" +
                      ".icon { font-size:48px; margin-bottom:12px; }" +
                      "h3 { color:#F59E0B; margin:0 0 10px 0; font-size:18px; }" +
                      "p { color:#94A3B8; font-size:14px; line-height:1.5; margin:0 0 20px 0; }" +
                      ".btn { display:inline-block; background:#2563EB; color:#FFF !important; text-decoration:none; padding:12px 24px; border-radius:8px; font-weight:bold; font-size:14px; }" +
                      "</style></head><body>" +
                      "<div class='card'>" +
                      "<div class='icon'>📁</div>" +
                      "<h3>Autorisation requise</h3>" +
                      "<p>Pour afficher les schémas et images des calculateurs (/sdcard/PinOut2), veuillez autoriser l'accès à tous les fichiers.</p>" +
                      "<a class='btn' href='pinout://request_permission'>Accorder la permission</a>" +
                      "</div></body></html>";
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }

    private void showError(String msg) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                      "<style>" +
                      "body { background:#0F172A; color:#E2E8F0; font-family:sans-serif; text-align:center; padding:30px 20px; }" +
                      ".card { background:#1E293B; border-radius:12px; padding:24px; border:1px solid #EF4444; max-width:400px; margin:auto; }" +
                      "h3 { color:#EF4444; margin:0 0 10px 0; font-size:18px; }" +
                      "p { color:#94A3B8; font-size:13px; line-height:1.5; margin:0 0 15px 0; }" +
                      ".btn { display:inline-block; background:#334155; color:#38BDF8 !important; text-decoration:none; padding:10px 20px; border-radius:8px; font-size:13px; margin-top:10px; }" +
                      "</style></head><body>" +
                      "<div class='card'>" +
                      "<h3>⚠️ Fichier introuvable</h3>" +
                      "<p>" + msg + "</p>" +
                      "<a class='btn' href='pinout://refresh'>🔄 Réessayer</a>" +
                      "</div></body></html>";
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }
}
