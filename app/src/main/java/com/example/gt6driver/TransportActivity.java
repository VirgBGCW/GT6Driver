package com.example.gt6driver;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.gt6driver.model.VehicleDetail;
import com.google.android.material.button.MaterialButton;

/**
 * Transport confirmation screen.
 * Shows vehicle panel + CONFIRM ONSITE button.
 */
public class TransportActivity extends AppCompatActivity {

    // Vehicle panel views
    private ImageView ivCheckInThumb;
    private TextView panelLot;
    private TextView panelDesc;
    private TextView panelVin;

    private MaterialButton btnConfirmOnsite;

    // Vehicle (source of truth)
    private VehicleDetail vehicle;

    // Legacy fallbacks
    private String lotLegacy = "";
    private String descLegacy = "";
    private String vinLegacy = "";
    private String thumbLegacy = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transport);

        bindViews();
        readExtras(getIntent());
        populateVehiclePanel();

        btnConfirmOnsite.setOnClickListener(v -> {
            // Just return to ActionActivity for now
            finish();
        });
    }

    private void bindViews() {
        ivCheckInThumb = findViewById(R.id.ivCheckInThumb);
        panelLot = findViewById(R.id.panelLot);
        panelDesc = findViewById(R.id.panelDesc);
        panelVin = findViewById(R.id.panelVin);

        btnConfirmOnsite = findViewById(R.id.btnConfirmOnsite);
    }

    private void readExtras(Intent intent) {
        vehicle = intent.getParcelableExtra(Nav.EXTRA_VEHICLE);

        lotLegacy = safe(intent.getStringExtra(Nav.EXTRA_LOT));
        descLegacy = safe(intent.getStringExtra(Nav.EXTRA_DESC));
        vinLegacy = safe(intent.getStringExtra(Nav.EXTRA_VIN));
        thumbLegacy = safe(intent.getStringExtra(Nav.EXTRA_THUMB));
    }

    private void populateVehiclePanel() {
        String lot = (vehicle != null && vehicle.lotnumber != null)
                ? "LOT # " + vehicle.lotnumber
                : (lotLegacy.isEmpty() ? "" : "LOT # " + lotLegacy);

        String desc = (vehicle != null && vehicle.title != null && !vehicle.title.isEmpty())
                ? vehicle.title
                : (vehicle != null ? safe(vehicle.marketingdescription) : descLegacy);

        String vin = (vehicle != null)
                ? safe(vehicle.vin)
                : vinLegacy;

        String thumbUrl = (vehicle != null)
                ? safe(vehicle.thumbUrl)
                : thumbLegacy;

        panelLot.setText(lot);
        panelDesc.setText(desc);
        panelVin.setText(vin);

        if (!thumbUrl.isEmpty()) {
            Glide.with(this)
                    .load(thumbUrl)
                    .centerCrop()
                    .placeholder(android.R.color.darker_gray)
                    .error(android.R.color.darker_gray)
                    .into(ivCheckInThumb);
        } else {
            ivCheckInThumb.setImageResource(android.R.color.darker_gray);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}

