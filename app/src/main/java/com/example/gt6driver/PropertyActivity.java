package com.example.gt6driver;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.gt6driver.model.PropertyItem;
import com.example.gt6driver.model.VehicleDetail;
import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.OpportunityApi;
import com.example.gt6driver.ui.PropertyAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * PropertyActivity
 *
 * Displays:
 *  - Vehicle panel (thumb, lot, desc, vin)
 *  - RecyclerView list of property panels:
 *      Row 1: propertyType (left) + quantity (right)
 *      Row 2: propertyDescription
 *      Row 3: notes
 *
 * Loads data from:
 *  GET https://member.api.barrett-jackson.com/api/v1/Opportunity/{opportunityId}/Property
 *
 * opportunityId is passed from ActionActivity via putCommonExtras():
 *  - Nav.EXTRA_OPPORTUNITY_ID (canonical)
 *  - "opportunityId" (legacy)
 *  - "crmId" (legacy)
 */
public class PropertyActivity extends AppCompatActivity {

    private static final String LOG_TAG = "PropertyActivity";
    private static final String HTTP_LOG_TAG = "GT6DriverHTTP";

    // Vehicle panel views (from your property.xml)
    private ImageView panelImage;
    private TextView panelLot;
    private TextView panelDesc;
    private TextView panelVinValue;        // If your property.xml still uses panelVinValue
    private TextView panelVin;             // If your property.xml uses panelVin (older/simple)
    private TextView panelLocationName;    // Optional if present
    private TextView panelRowCol;          // Optional if present

    // List UI
    private RecyclerView rvProperty;
    private View emptyState;               // optional if you add one
    private PropertyAdapter adapter;
    private View propertyEmptyState;

    // Close button
    private MaterialButton btnCloseProperty;

    // Inputs/state
    private VehicleDetail vehicle;
    private int eventId = -1;
    private String eventName = "";
    private String driver = "";

    private String opportunityId = "";

    // Legacy fallbacks
    private String lotLegacy = "", descLegacy = "", vinLegacy = "", thumbLegacy = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Optional: lock portrait like your other screens
        try { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); } catch (Throwable ignored) {}

        setContentView(R.layout.activity_property);

        bindViews();
        readExtras(getIntent());

        opportunityId = resolveOpportunityId(getIntent());
        Log.d(LOG_TAG, "Resolved opportunityId=" + opportunityId);

        if (opportunityId == null || opportunityId.trim().isEmpty()) {
            Toast.makeText(this, "Missing Opportunity ID", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        populateVehiclePanel();
        setupRecycler();
        loadProperty(opportunityId.trim());

        btnCloseProperty.setOnClickListener(v -> finish());
    }

    private void bindViews() {
        // Vehicle panel
        panelImage = findViewById(R.id.panelImage);
        panelLot = findViewById(R.id.panelLot);
        panelDesc = findViewById(R.id.panelDesc);

        // Depending on your layout, you might have one of these IDs:
        panelVinValue = safeFindText(R.id.panelVinValue); // property.xml you pasted has panelVinValue
        panelVin = safeFindText(R.id.panelVin);           // some screens use panelVin

        // Optional fields (if present)
        panelLocationName = safeFindText(R.id.panelLocationName);
        panelRowCol = safeFindText(R.id.panelRowCol);

        // RecyclerView (you must have this in activity_property.xml)
        rvProperty = findViewById(R.id.rvProperty);

        // Close button (exists in your property.xml)
        btnCloseProperty = findViewById(R.id.btnCloseProperty);
        propertyEmptyState = findViewById(R.id.propertyEmptyState);


        // Optional empty state placeholder if you add it later
        emptyState = findViewById(getResources().getIdentifier("propertyEmptyState", "id", getPackageName()));
    }

    private TextView safeFindText(int id) {
        try {
            View v = findViewById(id);
            if (v instanceof TextView) return (TextView) v;
        } catch (Throwable ignored) {}
        return null;
    }

    private void readExtras(@NonNull Intent intent) {
        vehicle = intent.getParcelableExtra(Nav.EXTRA_VEHICLE);

        eventId = intent.getIntExtra(Nav.EXTRA_EVENT_ID, -1);
        eventName = safe(intent.getStringExtra(Nav.EXTRA_EVENT_NAME));
        driver = safe(intent.getStringExtra(Nav.EXTRA_DRIVER));

        lotLegacy = safe(intent.getStringExtra(Nav.EXTRA_LOT));
        descLegacy = safe(intent.getStringExtra(Nav.EXTRA_DESC));
        vinLegacy = safe(intent.getStringExtra(Nav.EXTRA_VIN));
        thumbLegacy = safe(intent.getStringExtra(Nav.EXTRA_THUMB));
    }

    private void setupRecycler() {
        adapter = new PropertyAdapter();
        rvProperty.setLayoutManager(new LinearLayoutManager(this));
        rvProperty.setAdapter(adapter);
        rvProperty.setItemAnimator(null);
    }

    private void populateVehiclePanel() {
        String lotStr = (vehicle != null && vehicle.lotnumber != null)
                ? String.valueOf(vehicle.lotnumber)
                : lotLegacy;

        String title = (vehicle != null && vehicle.title != null && !vehicle.title.isEmpty())
                ? vehicle.title
                : (vehicle != null ? safe(vehicle.marketingdescription) : descLegacy);

        String vinStr = (vehicle != null) ? safe(vehicle.vin) : vinLegacy;
        String thumbUrl = (vehicle != null) ? safe(vehicle.thumbUrl) : thumbLegacy;

        if (panelLot != null) {
            panelLot.setText(lotStr.isEmpty() ? "" : ("LOT # " + lotStr));
        }
        if (panelDesc != null) {
            panelDesc.setText(title);
        }

        // Put VIN into whichever view exists in your layout
        if (panelVinValue != null) {
            panelVinValue.setText(vinStr);
        }
        if (panelVin != null) {
            panelVin.setText(vinStr);
        }

        // Optional: show location/rowcol if those fields are present
        if (panelLocationName != null && vehicle != null) {
            panelLocationName.setText(safe(vehicle.tentid));
        }
        if (panelRowCol != null && vehicle != null) {
            String row = safe(vehicle.row);
            String col = safe(vehicle.col);
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

    private void loadProperty(String oppId) {
        Log.d(HTTP_LOG_TAG, "GET /Opportunity/" + oppId + "/Property");

        OpportunityApi api = ApiClient.getMemberApi().create(OpportunityApi.class);
        Call<List<PropertyItem>> call = api.getOpportunityProperty(oppId);

        // Optional: log request URL
        try {
            Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url());
        } catch (Throwable ignored) {}

        call.enqueue(new Callback<List<PropertyItem>>() {
            @Override
            public void onResponse(Call<List<PropertyItem>> call, Response<List<PropertyItem>> response) {

                if (response.isSuccessful()) {
                    List<PropertyItem> list = response.body();

                    if (list == null || list.isEmpty()) {
                        // Successful but empty list
                        rvProperty.setVisibility(View.GONE);
                        propertyEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        adapter.setItems(list);
                        rvProperty.setVisibility(View.VISIBLE);
                        propertyEmptyState.setVisibility(View.GONE);
                    }

                } else if (response.code() == 404) {
                    // ✅ Explicit "no property found" case
                    rvProperty.setVisibility(View.GONE);
                    propertyEmptyState.setVisibility(View.VISIBLE);

                } else {
                    // Other errors
                    Toast.makeText(
                            PropertyActivity.this,
                            "Failed to load property (" + response.code() + ")",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }


            @Override
            public void onFailure(Call<List<PropertyItem>> call, Throwable t) {
                Log.e(HTTP_LOG_TAG, "Property GET network error", t);
                Toast.makeText(PropertyActivity.this,
                        "Network error loading property",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Unified resolver for OpportunityId. Matches your ActionActivity behavior.
     */
    private String resolveOpportunityId(Intent intent) {
        // canonical + legacy names
        String id = coalesce(
                safe(intent.getStringExtra(Nav.EXTRA_OPPORTUNITY_ID)),
                safe(intent.getStringExtra("opportunityId")),
                safe(intent.getStringExtra("crmId"))
        );

        if (!id.isEmpty()) return id;

        // if vehicle was passed, check fields there too
        if (vehicle != null) {
            String vOpp = safe(vehicle.opportunityId);
            if (!vOpp.isEmpty()) return vOpp;

            String vCrm = safe(vehicle.crmopportunityid);
            if (!vCrm.isEmpty()) return vCrm;
        }
        return "";
    }

    private static String coalesce(String... vals) {
        if (vals == null) return "";
        for (String s : vals) if (s != null && !s.trim().isEmpty()) return s.trim();
        return "";
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    private String defaulted(String v, String d) {
        return (v != null && !v.trim().isEmpty()) ? v : d;
    }
}





