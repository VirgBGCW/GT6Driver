package com.example.gt6driver;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
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
import com.example.gt6driver.model.PropertyItemCheckInType;
import com.example.gt6driver.model.PropertyItem;
import com.example.gt6driver.model.VehicleDetail;
import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.LookupService;
import com.example.gt6driver.net.OpportunityApi;
import com.example.gt6driver.net.PropertyCreateRequest;
import com.example.gt6driver.ui.PropertyAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.example.gt6driver.net.PropertyCheckinTypeUpdateRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PropertyActivity extends AppCompatActivity {

    private static final String LOG_TAG = "PropertyActivity";
    private static final String HTTP_LOG_TAG = "GT6DriverHTTP";
    private static final String PROPERTY_STATUS_AWAITING_ARRIVAL =
            PropertyItemCheckInType.AWAITING_ARRIVAL.getApiValue();
    private static final String PROPERTY_STATUS_AWAITING_ARRIVAL_LEGACY =
            PropertyItemCheckInType.AWAITING_ARRIVAL_LEGACY_API_VALUE;
    private static final String PROPERTY_STATUS_ARRIVED =
            PropertyItemCheckInType.ARRIVED.getApiValue();
    private static final String PROPERTY_STATUS_LEFT_IN_CAR =
            PropertyItemCheckInType.LEFT_IN_CAR_ALTERNATE.getApiValue();
    private static final String PROPERTY_STATUS_REMOVED =
            PropertyItemCheckInType.REMOVED.getApiValue();

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
    private MaterialButton btnPrintPropertyForm;

    // Inputs/state
    private VehicleDetail vehicle;
    private int eventId = -1;
    private String eventName = "";
    private String driver = "";

    private String opportunityId = "";
    private final List<PropertyTypeCatalog.Option> propertyTypes = new ArrayList<>();
    private Call<JsonElement> propertyTypesCall;
    private boolean propertyTypesLoaded = false;

    private interface PropertyLoadCallback {
        void onLoaded(@NonNull List<PropertyItem> items);
    }

    // Legacy fallbacks
    private String lotLegacy = "", descLegacy = "", vinLegacy = "", thumbLegacy = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } catch (Throwable ignored) {}

        setContentView(R.layout.activity_property);
        ApiClient.configure(this);

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
        loadPropertyTypesFromApi();

        if (btnCloseProperty != null) {
            btnCloseProperty.setOnClickListener(v -> finish());
        }

        if (btnAddProperty != null) {
            btnAddProperty.setEnabled(false);
            btnAddProperty.setAlpha(0.6f);
            btnAddProperty.setOnClickListener(v -> showAddPropertyDialog());
        }

        if (btnPrintPropertyForm != null) {
            btnPrintPropertyForm.setOnClickListener(v -> printPropertyForm());
        }
    }

    private void bindViews() {
        panelImage = findViewById(R.id.panelImage);
        panelLot = findViewById(R.id.panelLot);
        panelDesc = findViewById(R.id.panelDesc);

        panelVinValue = safeFindText(R.id.panelVinValue);
        panelVin = safeFindText(R.id.panelVin);

        panelLocationName = safeFindText(R.id.panelLocationName);
        panelRowCol = safeFindText(R.id.panelRowCol);

        rvProperty = findViewById(R.id.rvProperty);

        btnCloseProperty = findViewById(R.id.btnCloseProperty);
        btnAddProperty = findViewById(R.id.btnAddProperty);
        btnPrintPropertyForm = findViewById(R.id.btnPrintPropertyForm);

        propertyEmptyState = findViewById(R.id.propertyEmptyState);
    }

    private TextView safeFindText(int id) {
        try {
            View v = findViewById(id);
            if (v instanceof TextView) return (TextView) v;
        } catch (Throwable ignored) {}
        return null;
    }

    @Override
    protected void onDestroy() {
        if (propertyTypesCall != null && !propertyTypesCall.isCanceled()) {
            propertyTypesCall.cancel();
        }
        super.onDestroy();
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
        adapter = new PropertyAdapter(this::updatePropertyCheckInType);
        rvProperty.setLayoutManager(new LinearLayoutManager(this));
        rvProperty.setAdapter(adapter);
        rvProperty.setItemAnimator(null);
    }

    private void loadPropertyTypesFromApi() {
        propertyTypesLoaded = false;
        updateAddPropertyEnabled(false);

        LookupService svc = ApiClient.getMemberApi().create(LookupService.class);
        propertyTypesCall = svc.getLookupCodes("PropertyType");

        try {
            Log.i(HTTP_LOG_TAG, propertyTypesCall.request().method() + " " + propertyTypesCall.request().url());
        } catch (Throwable ignored) {}

        propertyTypesCall.enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    propertyTypesLoaded = false;
                    updateAddPropertyEnabled(false);
                    Toast.makeText(
                            PropertyActivity.this,
                            "Failed to load property types (" + response.code() + ")",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                List<PropertyTypeCatalog.Option> fetched = parsePropertyTypes(response.body());
                if (fetched.isEmpty()) {
                    propertyTypesLoaded = false;
                    updateAddPropertyEnabled(false);
                    Toast.makeText(PropertyActivity.this, "No property types found.", Toast.LENGTH_LONG).show();
                    return;
                }

                propertyTypes.clear();
                propertyTypes.addAll(fetched);
                propertyTypesLoaded = true;
                updateAddPropertyEnabled(true);
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                if (call.isCanceled()) return;
                propertyTypesLoaded = false;
                updateAddPropertyEnabled(false);
                Log.e(HTTP_LOG_TAG, "Property type lookup network error", t);
                Toast.makeText(PropertyActivity.this, "Network error loading property types", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateAddPropertyEnabled(boolean enabled) {
        if (btnAddProperty == null) return;
        btnAddProperty.setEnabled(enabled);
        btnAddProperty.setAlpha(enabled ? 1f : 0.6f);
    }

    private List<PropertyTypeCatalog.Option> parsePropertyTypes(JsonElement body) {
        List<PropertyTypeCatalog.Option> fetched = new ArrayList<>();
        if (body == null || !body.isJsonArray()) return fetched;

        for (JsonElement topEl : body.getAsJsonArray()) {
            if (topEl == null || !topEl.isJsonObject()) continue;
            JsonObject topObj = topEl.getAsJsonObject();
            JsonElement lookupsEl = topObj.get("lookups");
            if (lookupsEl == null || !lookupsEl.isJsonArray()) continue;

            for (JsonElement lookupEl : lookupsEl.getAsJsonArray()) {
                if (lookupEl == null || !lookupEl.isJsonObject()) continue;
                JsonObject lookup = lookupEl.getAsJsonObject();

                Integer id = getIntOrNull(lookup, "id");
                String name = getStringOrNull(lookup, "name");

                if (id != null && id > 0 && name != null && !name.trim().isEmpty()) {
                    fetched.add(new PropertyTypeCatalog.Option(id, name.trim()));
                }
            }
        }

        Collections.sort(fetched, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return fetched;
    }

    @Nullable
    private Integer getIntOrNull(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private String getStringOrNull(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private int resolveLoadedPropertyTypeIdByName(String name) {
        String target = safe(name);
        for (PropertyTypeCatalog.Option option : propertyTypes) {
            if (option.name.equalsIgnoreCase(target)) {
                return option.id;
            }
        }
        return -1;
    }

    private void updatePropertyCheckInType(@NonNull PropertyItem item, int position, @NonNull String newCheckInType) {
        String propertyId = item.getPropertyIdForApi();
        if (propertyId.isEmpty()) {
            Toast.makeText(this, "Unable to update property status", Toast.LENGTH_LONG).show();
            adapter.notifyItemChanged(position);
            return;
        }

        String previousSelectionType = currentPropertyCheckInType(item);
        String targetSelectionType = normalizePropertyCheckInType(newCheckInType);
        if (targetSelectionType.equals(previousSelectionType)) return;

        String previousCheckInType = item.checkInType;
        Boolean previousIsLeftInCar = item.isLeftInCar;
        boolean isLeftInCar = PROPERTY_STATUS_LEFT_IN_CAR.equals(targetSelectionType);
        String apiCheckInType = isLeftInCar ? PROPERTY_STATUS_ARRIVED : targetSelectionType;

        item.checkInType = apiCheckInType;
        item.isLeftInCar = isLeftInCar;
        item.isUpdatingCheckInType = true;
        adapter.notifyItemChanged(position);

        OpportunityApi api = ApiClient.getMemberApi().create(OpportunityApi.class);
        PropertyCheckinTypeUpdateRequest body = new PropertyCheckinTypeUpdateRequest(apiCheckInType, isLeftInCar);
        Call<Void> call = api.updateConsignmentPropertyCheckinType(propertyId, body);

        try {
            String payloadJson = new com.google.gson.Gson().toJson(body);
            Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url());
            Log.i(HTTP_LOG_TAG, "Property PUT context: propertyId=" + propertyId
                    + ", previousSelectionType=" + previousSelectionType
                    + ", targetSelectionType=" + targetSelectionType
                    + ", apiCheckInType=" + apiCheckInType);
            Log.i(HTTP_LOG_TAG, "Property PUT payload JSON: " + payloadJson);
        } catch (Throwable ignored) {}

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                item.isUpdatingCheckInType = false;

                if (response.isSuccessful()) {
                    item.checkInType = apiCheckInType;
                    item.isLeftInCar = isLeftInCar;
                    adapter.notifyItemChanged(position);
                    Toast.makeText(PropertyActivity.this, "Property status updated", Toast.LENGTH_SHORT).show();
                } else {
                    item.checkInType = previousCheckInType;
                    item.isLeftInCar = previousIsLeftInCar;
                    adapter.notifyItemChanged(position);

                    String errorText = readErrorBody(response);
                    Log.e(HTTP_LOG_TAG, "Property PUT failed (" + response.code() + "): " + errorText);
                    Toast.makeText(
                            PropertyActivity.this,
                            "Failed to update property status (" + response.code() + ")",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                item.isUpdatingCheckInType = false;
                item.checkInType = previousCheckInType;
                item.isLeftInCar = previousIsLeftInCar;
                adapter.notifyItemChanged(position);

                Log.e(HTTP_LOG_TAG, "Property PUT network error", t);
                Toast.makeText(
                        PropertyActivity.this,
                        "Network error updating property status",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
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
        loadProperty(oppId, null);
    }

    private void loadProperty(String oppId, @Nullable PropertyLoadCallback callback) {
        Log.d(HTTP_LOG_TAG, "GET /Opportunity/" + oppId + "/Property");

        OpportunityApi api = ApiClient.getMemberApi().create(OpportunityApi.class);
        Call<List<PropertyItem>> call = api.getOpportunityProperty(oppId);

        try {
            Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url());
        } catch (Throwable ignored) {}

        call.enqueue(new Callback<List<PropertyItem>>() {
            @Override
            public void onResponse(Call<List<PropertyItem>> call, Response<List<PropertyItem>> response) {
                if (response.isSuccessful()) {
                    List<PropertyItem> list = response.body();

                    if (list == null || list.isEmpty()) {
                        adapter.setItems(Collections.emptyList());
                        rvProperty.setVisibility(View.GONE);
                        if (propertyEmptyState != null) propertyEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        adapter.setItems(list);
                        rvProperty.setVisibility(View.VISIBLE);
                        if (propertyEmptyState != null) propertyEmptyState.setVisibility(View.GONE);
                    }

                    if (callback != null) {
                        callback.onLoaded(list == null ? Collections.emptyList() : list);
                    }

                } else if (response.code() == 404) {
                    adapter.setItems(Collections.emptyList());
                    rvProperty.setVisibility(View.GONE);
                    if (propertyEmptyState != null) propertyEmptyState.setVisibility(View.VISIBLE);

                    if (callback != null) {
                        callback.onLoaded(Collections.emptyList());
                    }

                } else {
                    String errorText = readErrorBody(response);
                    Log.e(HTTP_LOG_TAG, "Failed to load property (" + response.code() + "): " + errorText);

                    Toast.makeText(
                            PropertyActivity.this,
                            "Failed to load property (" + response.code() + ")",
                            Toast.LENGTH_LONG
                    ).show();

                    if (callback != null) {
                        callback.onLoaded(Collections.emptyList());
                    }
                }
            }

            @Override
            public void onFailure(Call<List<PropertyItem>> call, Throwable t) {
                Log.e(HTTP_LOG_TAG, "Property GET network error", t);
                Toast.makeText(
                        PropertyActivity.this,
                        "Network error loading property",
                        Toast.LENGTH_LONG
                ).show();

                if (callback != null) {
                    callback.onLoaded(Collections.emptyList());
                }
            }
        });
    }

    private void printPropertyForm() {
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        if (printManager == null) {
            Toast.makeText(this, "Print service is not available.", Toast.LENGTH_LONG).show();
            return;
        }

        List<PropertyItem> items = adapter != null
                ? adapter.getItemsSnapshot()
                : Collections.emptyList();

        PropertyPrintDocumentAdapter.Data data = buildPropertyPrintData(items);
        String lot = data.lotNumber.isEmpty() ? "Vehicle" : ("Lot " + data.lotNumber);
        String jobName = "GT6 Property Check-In - " + lot;

        PrintAttributes attributes = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();

        printManager.print(jobName, new PropertyPrintDocumentAdapter(this, data), attributes);
    }

    private PropertyPrintDocumentAdapter.Data buildPropertyPrintData(List<PropertyItem> items) {
        PropertyPrintDocumentAdapter.Data data = new PropertyPrintDocumentAdapter.Data();

        data.eventName = eventName.isEmpty() ? "GT6" : eventName;
        data.year = (vehicle != null && vehicle.year != null) ? String.valueOf(vehicle.year) : "";

        String make = vehicle != null ? safe(vehicle.make) : "";
        String model = vehicle != null ? safe(vehicle.model) : "";
        data.makeModel = (make + " " + model).trim();
        if (data.makeModel.isEmpty()) {
            data.makeModel = vehicle != null
                    ? coalesce(safe(vehicle.title), safe(vehicle.marketingdescription))
                    : descLegacy;
        }

        data.color = vehicle != null ? safe(vehicle.exteriorcolor) : "";
        data.lotNumber = vehicle != null && vehicle.lotnumber != null
                ? safe(vehicle.lotnumber)
                : lotLegacy;
        data.dateText = new SimpleDateFormat("MMMM d, yyyy", Locale.US).format(new Date());
        data.parkingLocation = buildParkingLocationText();
        data.initials = initialsFor(driver);
        data.items = items != null ? new ArrayList<>(items) : Collections.emptyList();
        return data;
    }

    private String buildParkingLocationText() {
        if (vehicle == null) return "";

        String location = safe(vehicle.tentid);
        String row = safe(vehicle.row);
        String col = safe(vehicle.col);
        String rowCol = "";
        if (!row.isEmpty() || !col.isEmpty()) {
            rowCol = defaulted(col, "-") + " - " + defaulted(row, "-");
        }
        return coalesce((location + " " + rowCol).trim(), location, rowCol);
    }

    private String initialsFor(String name) {
        String clean = safe(name).trim();
        if (clean.isEmpty()) return "";

        StringBuilder initials = new StringBuilder();
        String[] parts = clean.split("\\s+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() >= 3) break;
        }
        return initials.toString();
    }

    private String normalizePropertyCheckInType(String value) {
        if (value == null) return PROPERTY_STATUS_AWAITING_ARRIVAL;

        String normalized = value.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");

        if (normalized.equalsIgnoreCase(PROPERTY_STATUS_AWAITING_ARRIVAL)
                || normalized.equalsIgnoreCase(PROPERTY_STATUS_AWAITING_ARRIVAL_LEGACY)
                || normalized.equalsIgnoreCase("Awaiting")) {
            return PROPERTY_STATUS_AWAITING_ARRIVAL;
        }
        if (normalized.equalsIgnoreCase(PROPERTY_STATUS_ARRIVED)
                || normalized.equalsIgnoreCase("Arrived")
                || normalized.equalsIgnoreCase("Removed")) {
            return PROPERTY_STATUS_ARRIVED;
        }
        if (normalized.equalsIgnoreCase(PROPERTY_STATUS_REMOVED)) {
            return PROPERTY_STATUS_ARRIVED;
        }
        if (normalized.equalsIgnoreCase(PROPERTY_STATUS_LEFT_IN_CAR)
                || normalized.equalsIgnoreCase(PropertyItemCheckInType.LEFT_IN_CAR.getApiValue())
                || normalized.equalsIgnoreCase(PropertyItemCheckInType.LEFT_IN_CAR_ALTERNATE.getApiValue())
                || normalized.equalsIgnoreCase("LeftInCar")
                || normalized.equalsIgnoreCase("LeftInVehicle")) {
            return PROPERTY_STATUS_LEFT_IN_CAR;
        }
        return PROPERTY_STATUS_AWAITING_ARRIVAL;
    }

    private String currentPropertyCheckInType(PropertyItem item) {
        if (item != null && Boolean.TRUE.equals(item.isLeftInCar)) {
            return PROPERTY_STATUS_LEFT_IN_CAR;
        }
        return normalizePropertyCheckInType(item == null ? null : item.checkInType);
    }

    private void showAddPropertyDialog() {
        if (!propertyTypesLoaded || propertyTypes.isEmpty()) {
            Toast.makeText(this, "Property types are still loading.", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_property, null, false);

        TextInputLayout tilType = view.findViewById(R.id.tilPropertyType);
        MaterialAutoCompleteTextView actType = view.findViewById(R.id.actPropertyType);

        TextInputLayout tilDescription = view.findViewById(R.id.tilDescription);
        TextInputEditText etDescription = view.findViewById(R.id.etDescription);

        TextInputLayout tilNotes = view.findViewById(R.id.tilNotes);
        TextInputEditText etNotes = view.findViewById(R.id.etNotes);

        MaterialCheckBox chkLeftInVehicle = view.findViewById(R.id.chkLeftInVehicle);
        MaterialButton btnQtyMinus = view.findViewById(R.id.btnQtyMinus);
        MaterialButton btnQtyPlus = view.findViewById(R.id.btnQtyPlus);
        TextView tvQtyValue = view.findViewById(R.id.tvQtyValue);

        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
        MaterialButton btnAdd = view.findViewById(R.id.btnAdd);

        final int[] qty = new int[] {1};
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

        List<String> typeNames = new ArrayList<>();
        for (PropertyTypeCatalog.Option opt : propertyTypes) {
            typeNames.add(opt.name);
        }

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

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> {
                String typeName = (actType.getText() == null) ? "" : actType.getText().toString().trim();
                String description = (etDescription.getText() == null) ? "" : etDescription.getText().toString().trim();
                String notes = (etNotes.getText() == null) ? "" : etNotes.getText().toString().trim();
                boolean leftInVehicle = chkLeftInVehicle != null && chkLeftInVehicle.isChecked();

                boolean ok = true;

                if (typeName.isEmpty()) {
                    tilType.setError("Select a type");
                    ok = false;
                } else {
                    tilType.setError(null);
                }

                if (description.isEmpty()) {
                    tilDescription.setError("Description is required");
                    ok = false;
                } else {
                    tilDescription.setError(null);
                }

                if (notes.length() > 80) {
                    tilNotes.setError("Max 80 characters");
                    ok = false;
                } else {
                    tilNotes.setError(null);
                }

                int propertyTypeId = resolveLoadedPropertyTypeIdByName(typeName);
                if (propertyTypeId <= 0) {
                    tilType.setError("Invalid type");
                    ok = false;
                }

                if (!ok) return;

                btnAdd.setEnabled(false);
                btnAdd.setAlpha(0.6f);
                if (btnCancel != null) {
                    btnCancel.setEnabled(false);
                    btnCancel.setAlpha(0.6f);
                }

                createProperty(
                        propertyTypeId,
                        description,
                        notes,
                        qty[0],
                        leftInVehicle,
                        dialog::dismiss,
                        () -> {
                            btnAdd.setEnabled(true);
                            btnAdd.setAlpha(1f);
                            if (btnCancel != null) {
                                btnCancel.setEnabled(true);
                                btnCancel.setAlpha(1f);
                            }
                        }
                );
            });
        }

        dialog.show();
    }

    private void createProperty(int propertyItemTypeId,
                                String propertyDescription,
                                String notes,
                                int quantity,
                                boolean isLeftInCar,
                                Runnable onSuccessDismiss,
                                Runnable onFailureReenable) {

        OpportunityApi api = ApiClient.getMemberApi().create(OpportunityApi.class);
        Set<String> existingPropertyIds = currentPropertyIds();

        PropertyCreateRequest body = new PropertyCreateRequest(
                propertyItemTypeId,
                propertyDescription,
                notes,
                quantity,
                isLeftInCar
        );

        Call<Void> call = api.addOpportunityProperty(opportunityId.trim(), body);

        try {
            Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url());
            Log.d(
                    HTTP_LOG_TAG,
                    "POST body: {propertyItemTypeId=" + propertyItemTypeId
                            + ", propertyDescription='" + propertyDescription + "'"
                            + ", notes='" + notes + "'"
                            + ", quantity=" + quantity
                            + ", isLeftInCar=" + isLeftInCar
                            + "}"
            );
        } catch (Throwable ignored) {}

        Toast.makeText(this, "Adding property...", Toast.LENGTH_SHORT).show();

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    if (propertyEmptyState != null) propertyEmptyState.setVisibility(View.GONE);
                    rvProperty.setVisibility(View.VISIBLE);

                    if (isLeftInCar) {
                        loadProperty(opportunityId.trim(), items -> {
                            int addedPosition = findAddedPropertyPosition(
                                    items,
                                    existingPropertyIds,
                                    propertyItemTypeId,
                                    propertyDescription,
                                    notes,
                                    quantity
                            );
                            if (addedPosition >= 0 && addedPosition < items.size()) {
                                updateAddedPropertyLeftInCarStatus(
                                        items.get(addedPosition),
                                        addedPosition,
                                        onSuccessDismiss
                                );
                            } else {
                                Log.e(HTTP_LOG_TAG, "Unable to find added property for Left In Car status update");
                                Toast.makeText(
                                        PropertyActivity.this,
                                        "Property added; unable to set In Car status",
                                        Toast.LENGTH_LONG
                                ).show();
                                if (onSuccessDismiss != null) onSuccessDismiss.run();
                            }
                        });
                    } else {
                        Toast.makeText(PropertyActivity.this, "Property added", Toast.LENGTH_SHORT).show();
                        loadProperty(opportunityId.trim());
                        if (onSuccessDismiss != null) onSuccessDismiss.run();
                    }
                } else {
                    String errorText = readErrorBody(response);
                    Log.e(HTTP_LOG_TAG, "Add failed (" + response.code() + "): " + errorText);

                    Toast.makeText(
                            PropertyActivity.this,
                            "Add failed (" + response.code() + ")",
                            Toast.LENGTH_LONG
                    ).show();

                    if (onFailureReenable != null) onFailureReenable.run();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(HTTP_LOG_TAG, "Property POST network error", t);
                Toast.makeText(
                        PropertyActivity.this,
                        "Network error adding property",
                        Toast.LENGTH_LONG
                ).show();

                if (onFailureReenable != null) onFailureReenable.run();
            }
        });
    }

    private void updateAddedPropertyLeftInCarStatus(@NonNull PropertyItem item,
                                                    int position,
                                                    @Nullable Runnable onComplete) {
        String propertyId = item.getPropertyIdForApi();
        if (propertyId.isEmpty()) {
            Log.e(HTTP_LOG_TAG, "Added property missing propertyId for Left In Car status update");
            Toast.makeText(
                    PropertyActivity.this,
                    "Property added; unable to set In Car status",
                    Toast.LENGTH_LONG
            ).show();
            if (onComplete != null) onComplete.run();
            return;
        }

        item.checkInType = PROPERTY_STATUS_ARRIVED;
        item.isLeftInCar = true;
        item.isUpdatingCheckInType = true;
        adapter.notifyItemChanged(position);

        OpportunityApi api = ApiClient.getMemberApi().create(OpportunityApi.class);
        PropertyCheckinTypeUpdateRequest body =
                new PropertyCheckinTypeUpdateRequest(PROPERTY_STATUS_ARRIVED, true);
        Call<Void> call = api.updateConsignmentPropertyCheckinType(propertyId, body);

        try {
            String payloadJson = new com.google.gson.Gson().toJson(body);
            Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url());
            Log.i(HTTP_LOG_TAG, "Added property Left In Car PUT context: propertyId=" + propertyId
                    + ", apiCheckInType=" + PROPERTY_STATUS_ARRIVED
                    + ", isLeftInCar=true");
            Log.i(HTTP_LOG_TAG, "Added property Left In Car PUT payload JSON: " + payloadJson);
        } catch (Throwable ignored) {}

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                item.isUpdatingCheckInType = false;

                if (response.isSuccessful()) {
                    item.checkInType = PROPERTY_STATUS_ARRIVED;
                    item.isLeftInCar = true;
                    adapter.notifyItemChanged(position);
                    Toast.makeText(PropertyActivity.this, "Property added", Toast.LENGTH_SHORT).show();
                } else {
                    String errorText = readErrorBody(response);
                    Log.e(HTTP_LOG_TAG, "Added property Left In Car PUT failed ("
                            + response.code() + "): " + errorText);
                    Toast.makeText(
                            PropertyActivity.this,
                            "Property added; In Car status update failed (" + response.code() + ")",
                            Toast.LENGTH_LONG
                    ).show();
                    loadProperty(opportunityId.trim());
                }

                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                item.isUpdatingCheckInType = false;
                Log.e(HTTP_LOG_TAG, "Added property Left In Car PUT network error", t);
                Toast.makeText(
                        PropertyActivity.this,
                        "Property added; network error setting In Car status",
                        Toast.LENGTH_LONG
                ).show();
                loadProperty(opportunityId.trim());
                if (onComplete != null) onComplete.run();
            }
        });
    }

    private Set<String> currentPropertyIds() {
        Set<String> ids = new HashSet<>();
        if (adapter == null) return ids;

        for (PropertyItem item : adapter.getItemsSnapshot()) {
            String id = item == null ? "" : item.getPropertyIdForApi();
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private int findAddedPropertyPosition(@NonNull List<PropertyItem> items,
                                          @NonNull Set<String> existingPropertyIds,
                                          int propertyItemTypeId,
                                          String propertyDescription,
                                          String notes,
                                          int quantity) {
        int newestUnknownProperty = -1;
        int matchingProperty = -1;

        for (int i = items.size() - 1; i >= 0; i--) {
            PropertyItem item = items.get(i);
            String id = item == null ? "" : item.getPropertyIdForApi();
            boolean newId = !id.isEmpty() && !existingPropertyIds.contains(id);
            boolean matches = matchesAddedProperty(
                    item,
                    propertyItemTypeId,
                    propertyDescription,
                    notes,
                    quantity
            );

            if (newId && matches) return i;
            if (newId && newestUnknownProperty < 0) newestUnknownProperty = i;
            if (matches && matchingProperty < 0) matchingProperty = i;
        }

        return newestUnknownProperty >= 0 ? newestUnknownProperty : matchingProperty;
    }

    private boolean matchesAddedProperty(@Nullable PropertyItem item,
                                         int propertyItemTypeId,
                                         String propertyDescription,
                                         String notes,
                                         int quantity) {
        if (item == null) return false;

        boolean typeMatches = asInt(item.propertyItemTypeId) == propertyItemTypeId;
        boolean descriptionMatches = safe(item.propertyDescription).trim()
                .equals(propertyDescription == null ? "" : propertyDescription.trim());
        boolean notesMatches = safe(item.notes).trim().equals(notes == null ? "" : notes.trim());
        boolean quantityMatches = item.quantity == null || item.quantity == quantity;

        return typeMatches && descriptionMatches && notesMatches && quantityMatches;
    }

    private int asInt(Object value) {
        if (value == null) return -1;
        if (value instanceof Number) return ((Number) value).intValue();

        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String readErrorBody(Response<?> response) {
        try {
            if (response != null && response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            Log.e(HTTP_LOG_TAG, "Unable to read error body", e);
        }
        return "";
    }

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
        for (String s : vals) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return "";
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    private String defaulted(String v, String d) {
        return (v != null && !v.trim().isEmpty()) ? v : d;
    }

}






