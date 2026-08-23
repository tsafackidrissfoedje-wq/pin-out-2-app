package com.automind.pinout2;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    private static final String GITHUB_USER = "tsafackidrissfoedje-wq";
    private static final String VIRTUAL_HOST = "app.pinout";
    private static final String VIRTUAL_BASE_URL = "https://" + VIRTUAL_HOST + "/";

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
                if (url != null) {
                    if (url.startsWith("pinout://refresh")) {
                        loadPinoutContent();
                        return true;
                    } else if (url.startsWith(VIRTUAL_BASE_URL)) {
                        view.loadUrl(url);
                        return true;
                    }
                }
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    WebResourceResponse response = handleVirtualResource(request.getUrl());
                    if (response != null) return response;
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (url != null) {
                    WebResourceResponse response = handleVirtualResource(Uri.parse(url));
                    if (response != null) return response;
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Suppress Android default error page
            }
        });
    }

    public static String getRepoForSource(String source, String cleanPath) {
        if (source != null) {
            if (source.equals("bsl_bootmode_tricore") || source.equals("pcmktm_bench_module71") || source.equals("cfpm_gbe_auto")) {
                return "pin-out-2-bench-bsl";
            } else if (source.equals("dm_bosch_siemens_marelli") || source.equals("hexportal_ecu_connections")) {
                return "pin-out-2-hexportal-dm";
            } else if (source.equals("ktag_instruction")) {
                return "pin-out-2-ktag";
            }
        }
        if (cleanPath != null) {
            if (cleanPath.startsWith("bsl_bootmode_tricore") || cleanPath.startsWith("pcmktm_bench_module71") || cleanPath.startsWith("cfpm_pinouts")) {
                return "pin-out-2-bench-bsl";
            } else if (cleanPath.startsWith("dm_bosch_siemens_marelli") || cleanPath.startsWith("hexportal_ecu_connections")) {
                return "pin-out-2-hexportal-dm";
            } else if (cleanPath.startsWith("ktag_instruction")) {
                return "pin-out-2-ktag";
            } else if (cleanPath.startsWith("assets/images")) {
                return "pin-out-cfpm-gbe-auto-237";
            }
        }
        return "pin-out-2-bench-bsl";
    }

    public static String getRawGitHubUrl(String repo, String relPath) {
        String[] parts = relPath.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("/");
            try {
                sb.append(URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20"));
            } catch (Exception e) {
                sb.append(parts[i]);
            }
        }
        return "https://raw.githubusercontent.com/" + GITHUB_USER + "/" + repo + "/main/" + sb.toString();
    }

    public static String getCdnUrl(String repo, String relPath) {
        String[] parts = relPath.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("/");
            try {
                sb.append(URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20"));
            } catch (Exception e) {
                sb.append(parts[i]);
            }
        }
        return "https://cdn.jsdelivr.net/gh/" + GITHUB_USER + "/" + repo + "@main/" + sb.toString();
    }

    private byte[] downloadUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "PinOut2-App");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(18000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code == 200) {
                InputStream is = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                is.close();
                conn.disconnect();
                return baos.toByteArray();
            }
            conn.disconnect();
        } catch (Exception e) {
            // connection error
        }
        return null;
    }

    private byte[] fetchResourceWithFallback(String repo, String cleanPath) {
        String rawUrl = getRawGitHubUrl(repo, cleanPath);
        byte[] data = downloadUrl(rawUrl);
        if (data == null || data.length == 0) {
            String cdnUrl = getCdnUrl(repo, cleanPath);
            data = downloadUrl(cdnUrl);
        }
        return data;
    }

    private File getCacheDirRoot() {
        File dir = new File(getFilesDir(), "pinout_cache");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File getLocalFile(String cleanPath) {
        // 1. App internal cache
        File cached = new File(getCacheDirRoot(), cleanPath);
        if (cached.exists() && cached.isFile() && cached.length() > 0) {
            return cached;
        }

        // 2. Candidate SD card dirs
        for (String cand : CANDIDATE_DATA_DIRS) {
            File f = new File(cand, cleanPath);
            if (f.exists() && f.isFile() && f.length() > 0) {
                return f;
            }
            File fExt = new File(cand, "extracted/" + cleanPath);
            if (fExt.exists() && fExt.isFile() && fExt.length() > 0) {
                return fExt;
            }
        }
        return null;
    }

    private void saveToCache(String cleanPath, byte[] data) {
        try {
            File target = new File(getCacheDirRoot(), cleanPath);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileOutputStream fos = new FileOutputStream(target);
            fos.write(data);
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private WebResourceResponse handleVirtualResource(Uri uri) {
        if (uri == null) return null;
        String host = uri.getHost();
        if (VIRTUAL_HOST.equalsIgnoreCase(host)) {
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) return null;
            if (path.startsWith("/")) path = path.substring(1);

            try {
                path = Uri.decode(path);
            } catch (Exception ignored) {}

            if (path.startsWith("extracted/")) {
                path = path.substring("extracted/".length());
            }

            String mime = getMimeType(path);

            // 1. Check local file
            File local = getLocalFile(path);
            byte[] bytes = null;
            if (local != null && local.exists()) {
                try {
                    bytes = readFileBytes(local);
                } catch (Exception e) {
                    bytes = null;
                }
            }

            // 2. If not local, download from GitHub / jsDelivr CDN
            if (bytes == null) {
                String repo = getRepoForSource(source, path);
                bytes = fetchResourceWithFallback(repo, path);
                if (bytes != null && bytes.length > 0) {
                    saveToCache(path, bytes);
                }
            }

            if (bytes != null && bytes.length > 0) {
                if (mime.equals("text/html")) {
                    String charset = detectCharset(bytes);
                    String html = "";
                    try {
                        html = new String(bytes, charset);
                    } catch (Exception e) {
                        html = new String(bytes);
                    }
                    html = processHtml(html);
                    byte[] outBytes = html.getBytes(StandardCharsets.UTF_8);
                    return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(outBytes));
                } else {
                    return new WebResourceResponse(mime, null, new ByteArrayInputStream(bytes));
                }
            } else {
                // Return dummy empty response for CSS / JS to avoid breaking WebView
                if (mime.equals("text/css") || mime.equals("application/javascript")) {
                    return new WebResourceResponse(mime, "UTF-8", new ByteArrayInputStream(new byte[0]));
                }
            }
        }
        return null;
    }

    private String processHtml(String html) {
        // 1. Remove base tags
        html = html.replaceAll("(?i)<base[^>]*>", "");

        // 2. Fix hardcoded Windows file:///
        html = html.replaceAll("(?i)src=[\"'](?:file:///?[a-zA-Z]:/[^\"']*/|file:///[^\"']*/)([^\"']+)[\"']", "src=\"$1\"");
        html = html.replaceAll("(?i)href=[\"'](?:file:///?[a-zA-Z]:/[^\"']*/|file:///[^\"']*/)([^\"']+)[\"']", "href=\"$1\"");

        // 3. Inject modern responsive dark theme CSS
        String css = "<style>" +
                     "body { background-color: #0F172A !important; color: #E2E8F0 !important; font-family: -apple-system, Roboto, 'Helvetica Neue', Arial, sans-serif !important; padding: 14px !important; line-height: 1.6 !important; margin: 0 !important; } " +
                     "a { color: #38BDF8 !important; text-decoration: none !important; } " +
                     "img { max-width: 100% !important; height: auto !important; border-radius: 10px !important; box-shadow: 0 4px 14px rgba(0,0,0,0.6) !important; margin: 16px auto !important; display: block !important; background: #1E293B !important; } " +
                     "table { width: 100% !important; border-collapse: collapse !important; margin: 16px 0 !important; background: #1E293B !important; border-radius: 10px !important; overflow: hidden !important; border: 1px solid #334155 !important; } " +
                     "th, td { padding: 10px 12px !important; border: 1px solid #334155 !important; font-size: 13px !important; } " +
                     "th { background-color: #334155 !important; color: #38BDF8 !important; font-weight: bold !important; } " +
                     "h1, h2, h3 { color: #60A5FA !important; } " +
                     ".texter { display: block !important; } " +
                     "div[id^='a'], div[id^='b'], div[id^='c'], div[id^='d'] { display: block !important; } " +
                     ".border_big, .border_small { border: 1px solid #334155 !important; border-radius: 8px !important; padding: 10px !important; margin: 10px 0 !important; background: #1E293B !important; } " +
                     "#de_controlpanel, .de_controlpanel, #header, #footer_bottom { display: none !important; } " +
                     "</style>" +
                     "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes'>";

        if (html.contains("<head>")) {
            html = html.replace("<head>", "<head>" + css);
        } else {
            html = css + html;
        }
        return html;
    }

    private byte[] readFileBytes(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = fis.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        fis.close();
        return baos.toByteArray();
    }

    private String detectCharset(byte[] bytes) {
        try {
            String rawHeader = new String(bytes, 0, Math.min(bytes.length, 1024), "ISO-8859-1");
            if (rawHeader.toLowerCase().contains("windows-1251")) {
                return "windows-1251";
            }
        } catch (Exception ignored) {}
        return "UTF-8";
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

    private void loadPinoutContent() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        if (htmlRelPath == null || htmlRelPath.isEmpty()) {
            showOfflineError("Chemin de fichier introuvable.");
            return;
        }

        String path = htmlRelPath;
        if (path.startsWith("extracted/")) {
            path = path.substring("extracted/".length());
        }
        while (path.startsWith("/")) path = path.substring(1);
        final String cleanPath = path;

        // Check if already in cache or local SD
        File local = getLocalFile(cleanPath);
        if (local != null && local.exists() && local.length() > 0) {
            String virtualUrl = VIRTUAL_BASE_URL + cleanPath;
            webView.loadUrl(virtualUrl);
        } else {
            // Fetch HTML asynchronously from GitHub / CDN
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... voids) {
                    String repo = getRepoForSource(source, cleanPath);
                    byte[] data = fetchResourceWithFallback(repo, cleanPath);
                    if (data != null && data.length > 0) {
                        saveToCache(cleanPath, data);
                        return true;
                    }
                    return false;
                }

                @Override
                protected void onPostExecute(Boolean success) {
                    if (success) {
                        String virtualUrl = VIRTUAL_BASE_URL + cleanPath;
                        webView.loadUrl(virtualUrl);
                    } else {
                        showOfflineError("Impossible de charger ce schéma depuis GitHub.<br><br>Vérifiez votre connexion Internet (Wi-Fi ou Données mobiles).");
                    }
                }
            }.execute();
        }
    }

    private void showOfflineError(String msg) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                      "<style>" +
                      "body { background:#0F172A; color:#E2E8F0; font-family:sans-serif; text-align:center; padding:30px 20px; }" +
                      ".card { background:#1E293B; border-radius:14px; padding:24px; border:1px solid #334155; max-width:420px; margin:auto; box-shadow:0 8px 24px rgba(0,0,0,0.5); }" +
                      ".icon { font-size:48px; margin-bottom:14px; }" +
                      "h3 { color:#F59E0B; margin:0 0 12px 0; font-size:18px; }" +
                      "p { color:#94A3B8; font-size:14px; line-height:1.6; margin:0 0 20px 0; }" +
                      ".btn { display:inline-block; background:#2563EB; color:#FFF !important; text-decoration:none; padding:12px 24px; border-radius:8px; font-weight:bold; font-size:14px; cursor:pointer; }" +
                      "</style></head><body>" +
                      "<div class='card'>" +
                      "<div class='icon'>📶</div>" +
                      "<h3>Connexion Internet requise</h3>" +
                      "<p>" + msg + "<br><br><small style='color:#64748B;'>Les schémas déjà consultés restent accessibles hors-ligne à tout moment.</small></p>" +
                      "<a class='btn' href='pinout://refresh'>🔄 Réessayer</a>" +
                      "</div></body></html>";
        webView.loadDataWithBaseURL("https://app.pinout/", html, "text/html", "UTF-8", null);
    }
}

