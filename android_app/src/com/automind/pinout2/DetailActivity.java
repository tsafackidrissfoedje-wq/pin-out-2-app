package com.automind.pinout2;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

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

    private static final String DATA_BASE_DIR = "/data/data/com.termux/files/home/pin_out_2_project/data/";

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
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webView.setBackgroundColor(Color.parseColor("#0F172A"));
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                injectCustomStyles();
            }
        });
    }

    private void injectCustomStyles() {
        String css = "body { background-color: #0F172A !important; color: #E2E8F0 !important; font-family: sans-serif !important; padding: 12px !important; } " +
                     "a { color: #38BDF8 !important; } " +
                     "img { max-width: 100% !important; height: auto !important; border-radius: 8px !important; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.5) !important; margin: 10px auto !important; display: block !important; } " +
                     "table { width: 100% !important; border-collapse: collapse !important; margin: 12px 0 !important; background: #1E293B !important; border-radius: 8px !important; overflow: hidden !important; } " +
                     "th, td { padding: 10px !important; border: 1px solid #334155 !important; font-size: 13px !important; } " +
                     "th { background-color: #334155 !important; color: #F8FAFC !important; font-weight: bold !important; } " +
                     "h1, h2, h3 { color: #60A5FA !important; } " +
                     "p { line-height: 1.6 !important; font-size: 14px !important; }";

        String js = "javascript:(function() {" +
                    "var parent = document.getElementsByTagName('head').item(0);" +
                    "var style = document.createElement('style');" +
                    "style.type = 'text/css';" +
                    "style.innerHTML = '" + css + "';" +
                    "parent.appendChild(style);" +
                    "})()";
        webView.loadUrl(js);
    }

    private void loadPinoutContent() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        if (htmlRelPath == null || htmlRelPath.isEmpty()) {
            showError("Chemin de fichier introuvable.");
            return;
        }

        File targetFile = new File(DATA_BASE_DIR, htmlRelPath);
        if (targetFile.exists()) {
            String baseUrl = "file://" + targetFile.getParent() + "/";
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(targetFile), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                webView.loadDataWithBaseURL(baseUrl, sb.toString(), "text/html", "UTF-8", null);
            } catch (Exception e) {
                // Fallback direct load
                webView.loadUrl("file://" + targetFile.getAbsolutePath());
            }
        } else {
            // Check fallback
            showError("Fichier de données introuvable : " + htmlRelPath);
        }
    }

    private void showError(String msg) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        String html = "<html><body style='background:#0F172A;color:#EF4444;text-align:center;padding:40px;font-family:sans-serif;'>" +
                      "<h3>⚠️ Erreur</h3><p style='color:#94A3B8;'>" + msg + "</p>" +
                      "</body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }
}
