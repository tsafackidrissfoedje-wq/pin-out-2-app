package com.automind.pinout2;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class PinoutAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private List<PinoutItem> items = new ArrayList<PinoutItem>();
    private final OnFavoriteClickListener favoriteListener;

    public interface OnFavoriteClickListener {
        void onFavoriteClick(PinoutItem item);
    }

    public PinoutAdapter(Context context, OnFavoriteClickListener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.favoriteListener = listener;
    }

    public void updateData(List<PinoutItem> newItems) {
        this.items = (newItems != null) ? newItems : new ArrayList<PinoutItem>();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public PinoutItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    private static class ViewHolder {
        TextView tvCategory;
        TextView tvMcu;
        TextView tvImgCount;
        TextView tvBtnFav;
        TextView tvTitle;
        TextView tvSummary;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_pinout, parent, false);
            holder = new ViewHolder();
            holder.tvCategory = (TextView) convertView.findViewById(R.id.item_category);
            holder.tvMcu = (TextView) convertView.findViewById(R.id.item_mcu);
            holder.tvImgCount = (TextView) convertView.findViewById(R.id.item_img_count);
            holder.tvBtnFav = (TextView) convertView.findViewById(R.id.item_btn_fav);
            holder.tvTitle = (TextView) convertView.findViewById(R.id.item_title);
            holder.tvSummary = (TextView) convertView.findViewById(R.id.item_summary);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final PinoutItem item = getItem(position);

        holder.tvTitle.setText(item.title);
        holder.tvCategory.setText(item.category);

        if (item.source.equals("pcmktm_bench_module71")) {
            holder.tvCategory.setTextColor(Color.parseColor("#38BDF8"));
        } else if (item.source.equals("bsl_bootmode_tricore")) {
            holder.tvCategory.setTextColor(Color.parseColor("#A855F7"));
        } else if (item.source.equals("ktag_instruction")) {
            holder.tvCategory.setTextColor(Color.parseColor("#3B82F6"));
        } else if (item.source.equals("hexportal_ecu_connections")) {
            holder.tvCategory.setTextColor(Color.parseColor("#10B981"));
        } else {
            holder.tvCategory.setTextColor(Color.parseColor("#F59E0B"));
        }

        if (item.mcus != null && !item.mcus.isEmpty()) {
            holder.tvMcu.setText(item.mcus.get(0));
            holder.tvMcu.setVisibility(View.VISIBLE);
        } else {
            holder.tvMcu.setVisibility(View.GONE);
        }

        if (item.imagesCount > 0) {
            holder.tvImgCount.setText("📷 " + item.imagesCount);
            holder.tvImgCount.setVisibility(View.VISIBLE);
        } else {
            holder.tvImgCount.setVisibility(View.GONE);
        }

        if (item.summary != null && !item.summary.isEmpty()) {
            holder.tvSummary.setText(item.summary);
            holder.tvSummary.setVisibility(View.VISIBLE);
        } else {
            holder.tvSummary.setVisibility(View.GONE);
        }

        holder.tvBtnFav.setText(item.isFavorite ? "★" : "☆");
        holder.tvBtnFav.setTextColor(item.isFavorite ? Color.parseColor("#F59E0B") : Color.parseColor("#64748B"));

        holder.tvBtnFav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (favoriteListener != null) {
                    favoriteListener.onFavoriteClick(item);
                }
            }
        });

        return convertView;
    }
}
