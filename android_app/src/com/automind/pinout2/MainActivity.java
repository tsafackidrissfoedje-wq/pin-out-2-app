package com.automind.pinout2;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private EditText etSearch;
    private TextView btnClear;
    private TextView tvCount;
    private TextView tvEmpty;
    private TextView tvFavCounter;
    private ListView lvResults;
    private ProgressBar pbLoading;

    private Button chipAll, chipKtag, chipBench, chipBoot, chipHexportal, chipDm, chipFav;
    private Button[] categoryButtons;

    private Button brandAll, brandBosch, brandSiemens, brandDelphi, brandDenso, brandMarelli, brandPsa, brandVag, brandBmw, brandMercedes, brandRenault, brandFord;
    private Button[] brandButtons;

    private PinoutAdapter adapter;
    private final List<PinoutItem> allItems = new ArrayList<PinoutItem>();
    private final List<PinoutItem> filteredItems = new ArrayList<PinoutItem>();

    private String currentCategoryFilter = "ALL";
    private String currentBrandFilter = "ALL";
    private boolean currentFavFilter = false;
    private String currentSearchQuery = "";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("pinout2_prefs", MODE_PRIVATE);

        initViews();
        setupEvents();
        checkAndRequestStoragePermission();

        adapter = new PinoutAdapter(this, new PinoutAdapter.OnFavoriteClickListener() {
            @Override
            public void onFavoriteClick(PinoutItem item) {
                toggleFavorite(item);
            }
        });
        lvResults.setAdapter(adapter);

        new LoadDatabaseTask().execute();
    }

    private void checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) { // Android 11+
            boolean isManager = false;
            try {
                java.lang.reflect.Method method = Environment.class.getMethod("isExternalStorageManager");
                isManager = (Boolean) method.invoke(null);
            } catch (Exception e) {
                isManager = false;
            }

            if (!isManager) {
                new AlertDialog.Builder(this)
                    .setTitle("Autorisation requise 📁")
                    .setMessage("Pin Out 2 a besoin d'accéder aux schémas et images des calculateurs situés dans votre stockage (/sdcard/PinOut2/data).\n\nVeuillez autoriser l'accès aux fichiers.")
                    .setCancelable(false)
                    .setPositiveButton("Autoriser", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
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
                        }
                    })
                    .setNegativeButton("Plus tard", null)
                    .show();
            }
        } else if (Build.VERSION.SDK_INT >= 23) { // Android 6 - 10
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 1001);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFavorites();
        applyFilter();
    }

    private void initViews() {
        etSearch = (EditText) findViewById(R.id.et_search);
        btnClear = (TextView) findViewById(R.id.btn_clear);
        tvCount = (TextView) findViewById(R.id.tv_count);
        tvEmpty = (TextView) findViewById(R.id.tv_empty);
        tvFavCounter = (TextView) findViewById(R.id.tv_fav_counter);
        lvResults = (ListView) findViewById(R.id.lv_results);
        pbLoading = (ProgressBar) findViewById(R.id.pb_loading);

        chipAll = (Button) findViewById(R.id.chip_all);
        chipKtag = (Button) findViewById(R.id.chip_ktag);
        chipBench = (Button) findViewById(R.id.chip_bench);
        chipBoot = (Button) findViewById(R.id.chip_boot);
        chipHexportal = (Button) findViewById(R.id.chip_hexportal);
        chipDm = (Button) findViewById(R.id.chip_dm);
        chipFav = (Button) findViewById(R.id.chip_fav);

        categoryButtons = new Button[]{chipAll, chipKtag, chipBench, chipBoot, chipHexportal, chipDm, chipFav};

        brandAll = (Button) findViewById(R.id.brand_all);
        brandBosch = (Button) findViewById(R.id.brand_bosch);
        brandSiemens = (Button) findViewById(R.id.brand_siemens);
        brandDelphi = (Button) findViewById(R.id.brand_delphi);
        brandDenso = (Button) findViewById(R.id.brand_denso);
        brandMarelli = (Button) findViewById(R.id.brand_marelli);
        brandPsa = (Button) findViewById(R.id.brand_psa);
        brandVag = (Button) findViewById(R.id.brand_vag);
        brandBmw = (Button) findViewById(R.id.brand_bmw);
        brandMercedes = (Button) findViewById(R.id.brand_mercedes);
        brandRenault = (Button) findViewById(R.id.brand_renault);
        brandFord = (Button) findViewById(R.id.brand_ford);

        brandButtons = new Button[]{brandAll, brandBosch, brandSiemens, brandDelphi, brandDenso, brandMarelli, brandPsa, brandVag, brandBmw, brandMercedes, brandRenault, brandFord};
    }

    private void setupEvents() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                btnClear.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etSearch.setText("");
            }
        });

        // Category Filter Clicks
        setupCategoryClick(chipAll, "ALL", false);
        setupCategoryClick(chipKtag, "KTAG", false);
        setupCategoryClick(chipBench, "BENCH", false);
        setupCategoryClick(chipBoot, "BOOT", false);
        setupCategoryClick(chipHexportal, "HEX", false);
        setupCategoryClick(chipDm, "DM", false);
        setupCategoryClick(chipFav, "ALL", true);

        // Brand Filter Clicks
        setupBrandClick(brandAll, "ALL");
        setupBrandClick(brandBosch, "Bosch");
        setupBrandClick(brandSiemens, "Siemens");
        setupBrandClick(brandDelphi, "Delphi");
        setupBrandClick(brandDenso, "Denso");
        setupBrandClick(brandMarelli, "Marelli");
        setupBrandClick(brandPsa, "PSA");
        setupBrandClick(brandVag, "VAG");
        setupBrandClick(brandBmw, "BMW");
        setupBrandClick(brandMercedes, "Mercedes");
        setupBrandClick(brandRenault, "Renault");
        setupBrandClick(brandFord, "Ford");

        lvResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                PinoutItem item = filteredItems.get(position);
                Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                intent.putExtra("id", item.id);
                intent.putExtra("title", item.title);
                intent.putExtra("category", item.category);
                intent.putExtra("source", item.source);
                intent.putExtra("html_path", item.html);
                startActivity(intent);
            }
        });
    }

    private void setupCategoryClick(final Button btn, final String catCode, final boolean isFav) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentCategoryFilter = catCode;
                currentFavFilter = isFav;
                for (Button b : categoryButtons) {
                    b.setBackgroundResource(R.drawable.bg_chip_normal);
                    b.setTextColor(Color.parseColor("#94A3B8"));
                }
                btn.setBackgroundResource(R.drawable.bg_chip_active);
                btn.setTextColor(Color.parseColor("#F8FAFC"));
                applyFilter();
            }
        });
    }

    private void setupBrandClick(final Button btn, final String brandCode) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentBrandFilter = brandCode;
                for (Button b : brandButtons) {
                    b.setBackgroundResource(R.drawable.bg_chip_normal);
                    b.setTextColor(Color.parseColor("#94A3B8"));
                }
                btn.setBackgroundResource(R.drawable.bg_chip_active);
                btn.setTextColor(Color.parseColor("#F8FAFC"));
                applyFilter();
            }
        });
    }

    private void toggleFavorite(PinoutItem item) {
        item.isFavorite = !item.isFavorite;
        prefs.edit().putBoolean("fav_" + item.id, item.isFavorite).apply();
        refreshFavorites();
        applyFilter();
    }

    private void refreshFavorites() {
        int favCount = 0;
        for (PinoutItem item : allItems) {
            item.isFavorite = prefs.getBoolean("fav_" + item.id, false);
            if (item.isFavorite) favCount++;
        }
        tvFavCounter.setText("★ " + favCount);
    }

    private void applyFilter() {
        filteredItems.clear();
        for (PinoutItem item : allItems) {
            if (item.matches(currentSearchQuery, currentCategoryFilter, currentBrandFilter, currentFavFilter)) {
                filteredItems.add(item);
            }
        }
        adapter.updateData(filteredItems);
        tvCount.setText(filteredItems.size() + " calculateurs trouvés");
        tvEmpty.setVisibility(filteredItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private class LoadDatabaseTask extends AsyncTask<Void, Void, List<PinoutItem>> {
        @Override
        protected void onPreExecute() {
            pbLoading.setVisibility(View.VISIBLE);
        }

        @Override
        protected List<PinoutItem> doInBackground(Void... voids) {
            List<PinoutItem> list = new ArrayList<PinoutItem>();
            try {
                InputStream is = getAssets().open("pinout_app_index.json");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                String jsonStr = new String(buffer, "UTF-8");

                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    PinoutItem item = new PinoutItem();
                    item.id = obj.optInt("id");
                    item.title = obj.optString("title");
                    item.category = obj.optString("category");
                    item.source = obj.optString("source");
                    item.imagesCount = obj.optInt("images_count");
                    item.previewImg = obj.optString("preview_img");
                    item.html = obj.optString("html");
                    item.summary = obj.optString("summary");
                    item.tags = obj.optString("tags");

                    JSONArray brandsArr = obj.optJSONArray("brands");
                    if (brandsArr != null) {
                        for (int b = 0; b < brandsArr.length(); b++) {
                            item.brands.add(brandsArr.getString(b));
                        }
                    }

                    JSONArray mcusArr = obj.optJSONArray("mcus");
                    if (mcusArr != null) {
                        for (int m = 0; m < mcusArr.length(); m++) {
                            item.mcus.add(mcusArr.getString(m));
                        }
                    }

                    item.isFavorite = prefs.getBoolean("fav_" + item.id, false);
                    list.add(item);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return list;
        }

        @Override
        protected void onPostExecute(List<PinoutItem> result) {
            pbLoading.setVisibility(View.GONE);
            allItems.clear();
            allItems.addAll(result);
            refreshFavorites();
            applyFilter();
        }
    }
}
