package com.example.gt6driver.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gt6driver.R;
import com.example.gt6driver.model.PropertyItem;

import java.util.ArrayList;
import java.util.List;

public class PropertyAdapter extends RecyclerView.Adapter<PropertyAdapter.VH> {

    private final List<PropertyItem> items = new ArrayList<>();

    public void setItems(List<PropertyItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_property_panel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PropertyItem it = items.get(position);

        String type = safe(it.propertyType);
        String qty = (it.quantity != null) ? ("x" + it.quantity) : "";
        String desc = safe(it.propertyDescription);
        String notes = safe(it.notes);

        h.tvType.setText(type);
        h.tvQty.setText(qty);

        h.tvDesc.setText(desc);
        h.tvDesc.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);

        h.tvNotes.setText(notes);
        h.tvNotes.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvType, tvQty, tvDesc, tvNotes;
        VH(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvType);
            tvQty = itemView.findViewById(R.id.tvQty);
            tvDesc = itemView.findViewById(R.id.tvDesc);
            tvNotes = itemView.findViewById(R.id.tvNotes);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
