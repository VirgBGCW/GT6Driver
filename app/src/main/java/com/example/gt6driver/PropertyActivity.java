package com.example.gt6driver;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.gt6driver.model.VehicleDetail;
import com.google.android.material.button.MaterialButton;

public class PropertyActivity extends AppCompatActivity {

    private static final String TAG = "GT6-Property";

    // Vehicle panel fields
    private TextView panelLot, panelDesc, panelVinLabel, panelVinValue, panelLocationName, panelRowCol;
    private ImageView panelImage;

    // Bottom button
    private MaterialButton btnCloseProperty;

    private int eventId = -1;
    private String eventName = "";
    private String driver = "";
    private String crmId = "";

    private VehicleDetail vehicle;
    private String lotLegacy = "", descLegacy = "", vinLegacy = "", thumbLegacy = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_property);

        bindViews();

        // Root is optional, but recommended for insets
        applyEdgeToEdgeInsets();

        readExtras(getIntent());
        populateHeader();

        if (btnCloseProperty != null) {
            btnCloseProperty.setOnClickListener(v -> finish());
        } else {
            Log.w(TAG, "btnCloseProperty not found in layout.");
        }
    }

    private void bindViews() {
        panelLot = findViewById(R.id.panelLot);
        panelDesc = findViewById(R.id.panelDesc);

        // NEW: VIN label (safe if not in layout yet)
        panelVinLabel = findViewById(R.id.panelVinLabel);

        panelVinValue = findViewById(R.id.panelVinValue);
        panelLocationName = findViewById(R.id.panelLocationName);
        panelRowCol = findViewById(R.id.panelRowCol);
        panelImage = findViewById(R.id.panelImage);

        btnCloseProperty = findViewById(R.id.btnCloseProperty);

        // Only hard-require the ones your screen cannot function without
        if (panelLot == null || panelDesc == null || panelVinValue == null || panelImage == null) {
            Log.e(TAG, "Required views missing. Check activity_property.xml IDs: panelLot/panelDesc/panelVinValue/panelImage");
            finish();
        }
    }

    /**
     * Push content below status bar and keep CLOSE button above nav/gesture bar.
     * Uses androidx.core.graphics.Insets (not WindowInsetsCompat.Insets) to avoid the compile errors you hit.
     */
    private void applyEdgeToEdgeInsets() {
        final View root = findViewById(R.id.propertyRoot);
        if (root == null) {
            Log.w(TAG, "propertyRoot not found; skipping insets.");
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Top padding so content isn't under status bar
            v.setPadding(
                    v.getPaddingLeft(),
                    sysBars.top,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );

            // Lift CLOSE button above nav bar
            if (btnCloseProperty != null && btnCloseProperty.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) btnCloseProperty.getLayoutParams();

                // Keep your standard 24dp margin + add nav bar inset
                lp.bottomMargin = dp(24) + sysBars.bottom;

                btnCloseProperty.setLayoutParams(lp);
            }

            return insets;
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void readExtras(Intent intent) {
        if (intent == null) return;

        vehicle = intent.getParcelableExtra(Nav.EXTRA_VEHICLE);
        eventId = intent.getIntExtra(Nav.EXTRA_EVENT_ID, -1);
        eventName = safe(intent.getStringExtra(Nav.EXTRA_EVENT_NAME));
        driver = safe(intent.getStringExtra(Nav.EXTRA_DRIVER));
        crmId = safe(intent.getStringExtra(Nav.EXTRA_OPPORTUNITY_ID));

        lotLegacy = safe(intent.getStringExtra(Nav.EXTRA_LOT));
        descLegacy = safe(intent.getStringExtra(Nav.EXTRA_DESC));
        vinLegacy = safe(intent.getStringExtra(Nav.EXTRA_VIN));
        thumbLegacy = safe(intent.getStringExtra(Nav.EXTRA_THUMB));

        // Accept legacy keys
        if (crmId.isEmpty()) crmId = safe(intent.getStringExtra("opportunityId"));
        if (crmId.isEmpty()) crmId = safe(intent.getStringExtra("crmId"));

        Log.d(TAG, "Extras: eventId=" + eventId + " eventName=" + eventName + " driver=" + driver + " crmId=" + crmId);
    }

    private void populateHeader() {
        String lotStr = (vehicle != null && vehicle.lotnumber != null)
                ? String.valueOf(vehicle.lotnumber) : lotLegacy;

        String title = (vehicle != null && vehicle.title != null && !vehicle.title.isEmpty())
                ? vehicle.title
                : (vehicle != null ? safe(vehicle.marketingdescription) : descLegacy);

        String vinStr = (vehicle != null) ? safe(vehicle.vin) : vinLegacy;
        String thumbUrl = (vehicle != null) ? safe(vehicle.thumbUrl) : thumbLegacy;

        if (panelLot != null) panelLot.setText(lotStr.isEmpty() ? "" : ("LOT # " + lotStr));
        if (panelDesc != null) panelDesc.setText(title);

        // VIN label + VIN value
        if (panelVinLabel != null) panelVinLabel.setText("VIN:");
        if (panelVinValue != null) panelVinValue.setText(vinStr);

        // Parking info (safe even if not present in layout)
        if (panelLocationName != null) {
            String locationName = (vehicle != null) ? safe(vehicle.tentid) : "";
            panelLocationName.setText(locationName);
        }

        if (panelRowCol != null) {
            String row = (vehicle != null) ? safe(vehicle.row) : "";
            String col = (vehicle != null) ? safe(vehicle.col) : "";
            String rowColText = "";
            if (!row.isEmpty() || !col.isEmpty()) {
                rowColText = defaulted(col, "—") + " - " + defaulted(row, "—");
            }
            panelRowCol.setText(rowColText);
        }

        float density = getResources().getDisplayMetrics().density;
        int radiusPx = (int) (12 * density);

        if (panelImage != null) {
            if (!thumbUrl.isEmpty()) {
                Glide.with(this)
                        .load(thumbUrl)
                        .transform(new CenterCrop(), new RoundedCorners(radiusPx))
                        .placeholder(android.R.color.darker_gray)
                        .error(android.R.color.darker_gray)
                        .into(panelImage);
            } else {
                panelImage.setImageResource(android.R.color.darker_gray);
            }
        }
    }

    private String defaulted(String v, String d) {
        return (v != null && !v.trim().isEmpty()) ? v : d;
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }
}




