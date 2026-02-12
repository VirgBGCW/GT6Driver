package com.example.gt6driver;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
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
import com.example.gt6driver.net.PropertyCreateRequest;
import com.example.gt6driver.ui.PropertyAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PropertyActivity extends AppCompatActivity {

    private static final String LOG_TAG = "PropertyActivity";
    private static final String HTTP_LOG_TAG = "GT6DriverHTTP";

    /**
     * Property types from CSV (sorted by Name).
     * UI shows Name, but API must send Property ID as the "type" value.
     */
    private static final List<PropertyTypeOption> PROPERTY_TYPES = buildPropertyTypesSorted();

    // Vehicle panel views
    private ImageView panelImage;
    private TextView panelLot;
    private TextView panelDesc;
    private TextView panelVinValue;
    private TextView panelVin;
    private TextView panelLocationName;
    private TextView panelRowCol;

    // List UI
    private RecyclerView rvProperty;
    private PropertyAdapter adapter;
    private View propertyEmptyState;

    // Buttons
    private MaterialButton btnCloseProperty;
    private MaterialButton btnAddProperty;

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

        if (btnAddProperty != null) {
            btnAddProperty.setOnClickListener(v -> showAddPropertyDialog());
        }
    }

    private void bindViews() {
        // Vehicle panel
        panelImage = findViewById(R.id.panelImage);
        panelLot = findViewById(R.id.panelLot);
        panelDesc = findViewById(R.id.panelDesc);

        panelVinValue = safeFindText(R.id.panelVinValue);
        panelVin = safeFindText(R.id.panelVin);

        panelLocationName = safeFindText(R.id.panelLocationName);
        panelRowCol = safeFindText(R.id.panelRowCol);

        // RecyclerView
        rvProperty = findViewById(R.id.rvProperty);

        // Buttons
        btnCloseProperty = findViewById(R.id.btnCloseProperty);
        btnAddProperty = findViewById(R.id.btnAddProperty);

        // Empty state card
        propertyEmptyState = findViewById(R.id.propertyEmptyState);
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

        if (panelLot != null) panelLot.setText(lotStr.isEmpty() ? "" : ("LOT # " + lotStr));
        if (panelDesc != null) panelDesc.setText(title);

        if (panelVinValue != null) panelVinValue.setText(vinStr);
        if (panelVin != null) panelVin.setText(vinStr);

        if (panelLocationName != null && vehicle != null) panelLocationName.setText(safe(vehicle.tentid));

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

        try { Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url()); }
        catch (Throwable ignored) {}

        call.enqueue(new Callback<List<PropertyItem>>() {
            @Override
            public void onResponse(Call<List<PropertyItem>> call, Response<List<PropertyItem>> response) {

                if (response.isSuccessful()) {
                    List<PropertyItem> list = response.body();

                    if (list == null || list.isEmpty()) {
                        rvProperty.setVisibility(View.GONE);
                        if (propertyEmptyState != null) propertyEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        adapter.setItems(list);
                        rvProperty.setVisibility(View.VISIBLE);
                        if (propertyEmptyState != null) propertyEmptyState.setVisibility(View.GONE);
                    }

                } else if (response.code() == 404) {
                    rvProperty.setVisibility(View.GONE);
                    if (propertyEmptyState != null) propertyEmptyState.setVisibility(View.VISIBLE);

                } else {
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

    // ✅ MODAL: Add Property (updated for new dialog UI)
    private void showAddPropertyDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_property, null, false);

        TextInputLayout tilType = view.findViewById(R.id.tilPropertyType);
        MaterialAutoCompleteTextView actType = view.findViewById(R.id.actPropertyType);

        // NEW UI: Description (single line)
        TextInputLayout tilDescription = view.findViewById(R.id.tilDescription);
        TextInputEditText etDescription = view.findViewById(R.id.etDescription);

        // NEW UI: Notes (multi-line)
        TextInputLayout tilNotes = view.findViewById(R.id.tilNotes);
        TextInputEditText etNotes = view.findViewById(R.id.etNotes);

        // Left in Vehicle + Qty controls
        MaterialCheckBox chkLeftInVehicle = view.findViewById(R.id.chkLeftInVehicle);
        MaterialButton btnQtyMinus = view.findViewById(R.id.btnQtyMinus);
        MaterialButton btnQtyPlus = view.findViewById(R.id.btnQtyPlus);
        TextView tvQtyValue = view.findViewById(R.id.tvQtyValue);

        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
        MaterialButton btnAdd = view.findViewById(R.id.btnAdd);

        // Default qty = 1
        final int[] qty = new int[] { 1 };
        if (tvQtyValue != null) tvQtyValue.setText(String.valueOf(qty[0]));

        if (btnQtyMinus != null) {
            btnQtyMinus.setOnClickListener(v -> {
                if (qty[0] > 1) qty[0]--;
                if (tvQtyValue != null) tvQtyValue.setText(String.valueOf(qty[0]));
            });
        }

        if (btnQtyPlus != null) {
            btnQtyPlus.setOnClickListener(v -> {
                qty[0]++;
                if (tvQtyValue != null) tvQtyValue.setText(String.valueOf(qty[0]));
            });
        }

        // Dropdown adapter: show Names
        List<String> typeNames = new ArrayList<>();
        for (PropertyTypeOption opt : PROPERTY_TYPES) typeNames.add(opt.name);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                typeNames
        );
        actType.setAdapter(typeAdapter);

        final androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Add Property")
                        .setView(view)
                        .setCancelable(false)
                        .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            String typeName = (actType.getText() == null) ? "" : actType.getText().toString().trim();

            // NEW: Description required
            String description = (etDescription.getText() == null) ? "" : etDescription.getText().toString().trim();

            // NEW: Notes optional (<= 80)
            String notes = (etNotes.getText() == null) ? "" : etNotes.getText().toString().trim();

            boolean leftInVehicle = (chkLeftInVehicle != null) && chkLeftInVehicle.isChecked();

            boolean ok = true;

            if (typeName.isEmpty()) { tilType.setError("Select a type"); ok = false; }
            else tilType.setError(null);

            if (description.isEmpty()) { tilDescription.setError("Description is required"); ok = false; }
            else tilDescription.setError(null);

            if (notes.length() > 80) { tilNotes.setError("Max 80 characters"); ok = false; }
            else tilNotes.setError(null);

            int propertyTypeId = resolvePropertyTypeIdByName(typeName);
            if (propertyTypeId <= 0) {
                tilType.setError("Invalid type");
                ok = false;
            }

            if (!ok) return;

            // Non-breaking: encode qty + leftInVehicle into Notes so API/model doesn't change.
            // Keep it <= 80 chars.
            String finalNotes = buildNotesWithFlags(notes, qty[0], leftInVehicle);

            // Disable while saving
            btnAdd.setEnabled(false);
            btnAdd.setAlpha(0.6f);

            // API contract unchanged:
            // - type = property type ID
            // - property = Description field
            // - description = Notes field (with encoded flags)
            createProperty(String.valueOf(propertyTypeId), description, finalNotes,
                    () -> dialog.dismiss(),
                    () -> {
                        btnAdd.setEnabled(true);
                        btnAdd.setAlpha(1f);
                    });
        });

        dialog.show();
    }

    /**
     * Packs Qty + LeftInVehicle into the Notes string, without changing the API contract.
     * Ensures max 80 chars.
     */
    private String buildNotesWithFlags(String notes, int qty, boolean leftInVehicle) {
        String n = (notes == null) ? "" : notes.trim();

        // Example format:
        // "Qty:2 | LIV:Y | <notes...>"
        String prefix = "Qty:" + qty + " | LIV:" + (leftInVehicle ? "Y" : "N");
        String combined;

        if (n.isEmpty()) combined = prefix;
        else combined = prefix + " | " + n;

        if (combined.length() <= 80) return combined;
        return combined.substring(0, 80);
    }

    // ✅ POST to add, then refresh list
    private void createProperty(String type, String property, String description,
                                Runnable onSuccessDismiss,
                                Runnable onFailureReenable) {

        OpportunityApi api = ApiClient.getMemberApi().create(OpportunityApi.class);

        // NOTE: type is the Property ID string
        PropertyCreateRequest body = new PropertyCreateRequest(type, property, description);

        Call<Void> call = api.addOpportunityProperty(opportunityId.trim(), body);

        try { Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url()); }
        catch (Throwable ignored) {}

        Toast.makeText(this, "Adding property...", Toast.LENGTH_SHORT).show();

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(PropertyActivity.this, "Property added", Toast.LENGTH_SHORT).show();

                    // Refresh list
                    loadProperty(opportunityId.trim());

                    if (onSuccessDismiss != null) onSuccessDismiss.run();
                } else {
                    Toast.makeText(PropertyActivity.this,
                            "Add failed (" + response.code() + ")",
                            Toast.LENGTH_LONG).show();
                    if (onFailureReenable != null) onFailureReenable.run();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(HTTP_LOG_TAG, "Property POST network error", t);
                Toast.makeText(PropertyActivity.this,
                        "Network error adding property",
                        Toast.LENGTH_LONG).show();
                if (onFailureReenable != null) onFailureReenable.run();
            }
        });
    }

    /**
     * Unified resolver for OpportunityId. Matches your ActionActivity behavior.
     */
    private String resolveOpportunityId(Intent intent) {
        String id = coalesce(
                safe(intent.getStringExtra(Nav.EXTRA_OPPORTUNITY_ID)),
                safe(intent.getStringExtra("opportunityId")),
                safe(intent.getStringExtra("crmId"))
        );

        if (!id.isEmpty()) return id;

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

    // -----------------------------
    // Property Type helpers/models
    // -----------------------------

    private static class PropertyTypeOption {
        final int id;
        final String name;

        PropertyTypeOption(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static List<PropertyTypeOption> buildPropertyTypesSorted() {
        List<PropertyTypeOption> list = new ArrayList<>();

        // From proptypes.csv (Property ID, Name) sorted by Name
        list.add(new PropertyTypeOption(1, "Award / Trophy"));
        list.add(new PropertyTypeOption(2, "Binder(s) of Documents"));
        list.add(new PropertyTypeOption(3, "Broadcast Sheet"));
        list.add(new PropertyTypeOption(4, "Build Sheet"));
        list.add(new PropertyTypeOption(34, "Car Cover"));
        list.add(new PropertyTypeOption(6, "Certificate of Authenticity"));
        list.add(new PropertyTypeOption(40, "Cleaning Supplies"));
        list.add(new PropertyTypeOption(41, "Key/Key Fobs"));
        list.add(new PropertyTypeOption(8, "License Plate"));
        list.add(new PropertyTypeOption(37, "Magazine(s)"));
        list.add(new PropertyTypeOption(31, "Manual(s)"));
        list.add(new PropertyTypeOption(9, "Marti Report"));
        list.add(new PropertyTypeOption(24, "Misc Docs"));
        list.add(new PropertyTypeOption(35, "NCRS Docs"));
        list.add(new PropertyTypeOption(21, "No Property"));
        list.add(new PropertyTypeOption(22, "No Property - Block End"));
        list.add(new PropertyTypeOption(20, "Other"));
        list.add(new PropertyTypeOption(10, "Owner's Manual"));
        list.add(new PropertyTypeOption(36, "PHS Docs"));
        list.add(new PropertyTypeOption(29, "Photos"));
        list.add(new PropertyTypeOption(11, "Protect-O-Plate"));
        list.add(new PropertyTypeOption(12, "Registration"));
        list.add(new PropertyTypeOption(25, "Remote"));
        list.add(new PropertyTypeOption(13, "Service Records"));
        list.add(new PropertyTypeOption(26, "Spare / Misc Parts"));
        list.add(new PropertyTypeOption(14, "Stereo / Radio Manual"));
        list.add(new PropertyTypeOption(15, "Story Board"));
        list.add(new PropertyTypeOption(28, "Tank Sticker"));
        list.add(new PropertyTypeOption(33, "Tires"));
        list.add(new PropertyTypeOption(16, "Title"));
        list.add(new PropertyTypeOption(17, "Tool Kit"));
        list.add(new PropertyTypeOption(27, "Top"));
        list.add(new PropertyTypeOption(18, "Trophy"));
        list.add(new PropertyTypeOption(19, "Warranty Book"));
        list.add(new PropertyTypeOption(32, "Wheel Lock(s)"));
        list.add(new PropertyTypeOption(38, "Winch Controller"));
        list.add(new PropertyTypeOption(23, "Window Sticker"));

        Collections.sort(list, new Comparator<PropertyTypeOption>() {
            @Override
            public int compare(PropertyTypeOption a, PropertyTypeOption b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        return list;
    }

    private int resolvePropertyTypeIdByName(String name) {
        if (name == null) return -1;
        String n = name.trim();
        for (PropertyTypeOption opt : PROPERTY_TYPES) {
            if (opt.name.equalsIgnoreCase(n)) return opt.id;
        }
        return -1;
    }
}





