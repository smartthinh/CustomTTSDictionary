package com.smartthinh.customttsdictionary.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.smartthinh.customttsdictionary.R;

import java.util.List;

public class DictionaryAdapter extends BaseAdapter {
    private final Context context;
    private final List<String> dictionaryNames;
    private String activeDictionaryName;
    private final LayoutInflater inflater;

    public DictionaryAdapter(Context context, List<String> dictionaryNames, String activeDictionaryName) {
        this.context = context;
        this.dictionaryNames = dictionaryNames;
        this.activeDictionaryName = activeDictionaryName;
        this.inflater = LayoutInflater.from(context);
    }

    public void setActiveDictionary(String activeDictionaryName) {
        this.activeDictionaryName = activeDictionaryName;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return dictionaryNames.size();
    }

    @Override
    public Object getItem(int position) {
        return dictionaryNames.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_dictionary, parent, false);
            holder = new ViewHolder();
            holder.tvIndex = convertView.findViewById(R.id.tvDictIndex);
            holder.tvName = convertView.findViewById(R.id.tvDictName);
            holder.tvStatus = convertView.findViewById(R.id.tvDictStatus);
            holder.tvBadge = convertView.findViewById(R.id.tvDictBadge);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String name = dictionaryNames.get(position);
        holder.tvIndex.setText(String.valueOf(position + 1));
        holder.tvName.setText(name);

        boolean isActive = name.equals(activeDictionaryName);
        if (isActive) {
            holder.tvStatus.setText("✓ ĐANG SỬ DỤNG (Chạm để xem từ)");
            holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"));
            holder.tvBadge.setText("★");
            holder.tvBadge.setTextColor(Color.parseColor("#2E7D32"));
            convertView.setBackgroundColor(Color.parseColor("#E8F5E9"));
        } else {
            holder.tvStatus.setText("Chạm để chọn sử dụng");
            holder.tvStatus.setTextColor(Color.parseColor("#757575"));
            holder.tvBadge.setText("›");
            holder.tvBadge.setTextColor(Color.parseColor("#757575"));
            convertView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView tvIndex;
        TextView tvName;
        TextView tvStatus;
        TextView tvBadge;
    }
}
