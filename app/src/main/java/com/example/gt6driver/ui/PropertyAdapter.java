package com.example.gt6driver.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gt6driver.R;
import com.example.gt6driver.model.PropertyItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.List;

public class PropertyAdapter extends RecyclerView.Adapter<PropertyAdapter.VH> {

    public interface OnCheckInTypeChangedListener {
        void onCheckInTypeChanged(@NonNull PropertyItem item, int position, @NonNull String newCheckInType);
    }

    private final List<PropertyItem> items = new ArrayList<>();
    private final OnCheckInTypeChangedListener listener;

    public PropertyAdapter(OnCheckInTypeChangedListener listener) {
        this.listener = listener;
    }

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
        String checkInType = normalizeCheckInType(it.checkInType);
        boolean canUpdate = !it.getPropertyIdForApi().isEmpty();
        boolean enabled = canUpdate && !it.isUpdatingCheckInType;

        h.tvType.setText(type);
        h.tvQty.setText(qty);

        h.tvDesc.setText(desc);
        h.tvDesc.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);

        h.tvNotes.setText(notes);
        h.tvNotes.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);

        boolean noProperty = isNoPropertyType(type);

        h.checkInGroup.clearOnButtonCheckedListeners();

        if (noProperty) {
            h.tvCheckInLabel.setVisibility(View.GONE);
            h.progressCheckIn.setVisibility(View.GONE);
            h.checkInGroup.clearChecked();
            h.checkInGroup.setVisibility(View.GONE);
            return;
        }

        h.tvCheckInLabel.setVisibility(View.VISIBLE);
        h.checkInGroup.setVisibility(View.VISIBLE);
        h.tvCheckInLabel.setText(canUpdate ? "Property Status" : "Property Status (unavailable)");
        h.progressCheckIn.setVisibility(it.isUpdatingCheckInType ? View.VISIBLE : View.GONE);

        h.btnAwaitingArrival.setEnabled(enabled);
        h.btnRemoved.setEnabled(enabled);
        h.btnLeftInCar.setEnabled(enabled);
        h.checkInGroup.setEnabled(enabled);

        h.checkInGroup.check(buttonIdForType(checkInType));

        h.checkInGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            int adapterPosition = h.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return;

            String newType = typeForButtonId(checkedId);
            String currentType = normalizeCheckInType(items.get(adapterPosition).checkInType);
            if (newType.equals(currentType)) return;

            if (listener != null) {
                listener.onCheckInTypeChanged(items.get(adapterPosition), adapterPosition, newType);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvType, tvQty, tvDesc, tvNotes, tvCheckInLabel;
        ProgressBar progressCheckIn;
        MaterialButtonToggleGroup checkInGroup;
        MaterialButton btnAwaitingArrival, btnRemoved, btnLeftInCar;

        VH(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvType);
            tvQty = itemView.findViewById(R.id.tvQty);
            tvDesc = itemView.findViewById(R.id.tvDesc);
            tvNotes = itemView.findViewById(R.id.tvNotes);
            tvCheckInLabel = itemView.findViewById(R.id.tvCheckInLabel);
            progressCheckIn = itemView.findViewById(R.id.progressCheckIn);
            checkInGroup = itemView.findViewById(R.id.checkInGroup);
            btnAwaitingArrival = itemView.findViewById(R.id.btnAwaitingArrival);
            btnRemoved = itemView.findViewById(R.id.btnRemoved);
            btnLeftInCar = itemView.findViewById(R.id.btnLeftInCar);
        }
    }

    private static int buttonIdForType(String type) {
        switch (normalizeCheckInType(type)) {
            case "Removed":
                return R.id.btnRemoved;
            case "LeftInCar":
                return R.id.btnLeftInCar;
            case "AwaitingArrival":
            default:
                return R.id.btnAwaitingArrival;
        }
    }

    private static String typeForButtonId(int checkedId) {
        if (checkedId == R.id.btnRemoved) return "Removed";
        if (checkedId == R.id.btnLeftInCar) return "LeftInCar";
        return "AwaitingArrival";
    }

    private static String normalizeCheckInType(String value) {
        if (value == null) return "AwaitingArrival";

        String normalized = value.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");

        if (normalized.equalsIgnoreCase("Removed")) return "Removed";
        if (normalized.equalsIgnoreCase("LeftInCar") || normalized.equalsIgnoreCase("LeftInVehicle")) {
            return "LeftInCar";
        }
        return "AwaitingArrival";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isNoPropertyType(String value) {
        return value != null && value.trim().equalsIgnoreCase("No Property");
    }
}
