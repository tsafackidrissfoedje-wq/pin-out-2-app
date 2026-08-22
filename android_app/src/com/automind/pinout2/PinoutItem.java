package com.automind.pinout2;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PinoutItem implements Serializable {
    public int id;
    public String title;
    public String category;
    public String source;
    public List<String> brands = new ArrayList<String>();
    public List<String> mcus = new ArrayList<String>();
    public int imagesCount;
    public String previewImg;
    public String html;
    public String summary;
    public String tags;
    public boolean isFavorite;

    public PinoutItem() {
    }

    public boolean matches(String query, String categoryFilter, String brandFilter, boolean onlyFavorites) {
        if (onlyFavorites && !isFavorite) {
            return false;
        }

        if (categoryFilter != null && !categoryFilter.isEmpty() && !categoryFilter.equals("ALL")) {
            if (categoryFilter.equals("KTAG") && !source.equals("ktag_instruction")) return false;
            if (categoryFilter.equals("BENCH") && !source.equals("pcmktm_bench_module71")) return false;
            if (categoryFilter.equals("BOOT") && !source.equals("bsl_bootmode_tricore")) return false;
            if (categoryFilter.equals("HEX") && !source.equals("hexportal_ecu_connections")) return false;
            if (categoryFilter.equals("DM") && !source.equals("dm_bosch_siemens_marelli")) return false;
        }

        if (brandFilter != null && !brandFilter.isEmpty() && !brandFilter.equals("ALL")) {
            boolean hasBrand = false;
            for (String b : brands) {
                if (b.equalsIgnoreCase(brandFilter)) {
                    hasBrand = true;
                    break;
                }
            }
            if (!hasBrand && !tags.contains(brandFilter.toLowerCase())) {
                return false;
            }
        }

        if (query == null || query.trim().isEmpty()) {
            return true;
        }

        String[] tokens = query.toLowerCase().trim().split("\\s+");
        for (String token : tokens) {
            if (!tags.contains(token)) {
                return false;
            }
        }

        return true;
    }
}
