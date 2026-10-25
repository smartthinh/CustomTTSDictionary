package com.smartthinh.customttsdictionary.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.smartthinh.customttsdictionary.R;
import com.smartthinh.customttsdictionary.model.DictionaryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WordAdapter extends BaseAdapter {
    private final Context context;
    private final List<DictionaryItem> originalList;
    private final List<DictionaryItem> displayedList;
    private final LayoutInflater inflater;
    private String currentQuery = "";

    public WordAdapter(Context context, List<DictionaryItem> items) {
        this.context = context;
        this.originalList = items;
        this.displayedList = new ArrayList<>(items);
        this.inflater = LayoutInflater.from(context);
    }

    /**
     * Loc danh sach thoi gian thuc khong phan biet hoa thuong
     */
    public void filter(String query) {
        this.currentQuery = query != null ? query.trim() : "";
        displayedList.clear();

        if (currentQuery.isEmpty()) {
            displayedList.addAll(originalList);
        } else {
            String lower = currentQuery.toLowerCase(Locale.ROOT);
            for (DictionaryItem item : originalList) {
                if (item.getOriginalWord().toLowerCase(Locale.ROOT).contains(lower) ||
                    item.getReplacementWord().toLowerCase(Locale.ROOT).contains(lower)) {
                    displayedList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void refresh() {
        filter(currentQuery);
    }

    public List<DictionaryItem> getDisplayedList() {
        return displayedList;
    }

    @Override
    public int getCount() {
        return displayedList.size();
    }

    @Override
    public DictionaryItem getItem(int position) {
        return displayedList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_word, parent, false);
            holder = new ViewHolder();
            holder.tvIndex = convertView.findViewById(R.id.tvWordIndex);
            holder.tvOriginal = convertView.findViewById(R.id.tvOriginalWord);
            holder.tvReplacement = convertView.findViewById(R.id.tvReplacementWord);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        DictionaryItem item = displayedList.get(position);
        holder.tvIndex.setText(String.valueOf(position + 1));
        holder.tvOriginal.setText(item.getOriginalWord());
        holder.tvReplacement.setText("→ " + item.getReplacementWord());

        return convertView;
    }

    private static class ViewHolder {
        TextView tvIndex;
        TextView tvOriginal;
        TextView tvReplacement;
    }
}
