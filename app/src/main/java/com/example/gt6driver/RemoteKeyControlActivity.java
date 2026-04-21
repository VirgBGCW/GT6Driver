package com.example.gt6driver;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.gt6driver.data.DriverDirectory;
import com.example.gt6driver.model.ConsignmentKeyPayload;
import com.example.gt6driver.model.VehicleDetail;
import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.DriverTaskApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RemoteKeyControlActivity extends AppCompatActivity {

    private static final String LOG_TAG = "GT6Driver";
    private static final String HTTP_LOG_TAG = "GT6DriverHTTP";

    // Header
    private TextView panelLot, panelDesc;
    private TextView panelVinValue;
    private TextView panelLocationName;
    private TextView panelRowCol;
    private ImageView panelImage;

    // Buttons
    private MaterialButton btnCheckOut, btnCheckIn;

    // Context/state
    private int eventId = -1;
    private String eventName = "";
    private String driver = "";
    private String crmId = "";

    // Vehicle
    private VehicleDetail vehicle;

    // legacy fallback
    private String lotLegacy = "", descLegacy = "", vinLegacy = "", thumbLegacy = "";

    // dialog working state
    private String selectedDriverNumber = null;
    private String selectedDriverName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_key_control);

        bindViews();
        readExtras(getIntent());

        crmId = coalesce(
                safe(getIntent().getStringExtra(Nav.EXTRA_OPPORTUNITY_ID)),
                safe(getIntent().getStringExtra("crmId")),
                (vehicle != null ? safe(vehicle.opportunityId) : ""),
                (vehicle != null ? safe(vehicle.crmopportunityid) : "")
        );

        Log.d(LOG_TAG, "Resolved crmId/opportunityId = " + crmId);

        populateHeader();
        wireButtons();
    }

    private void bindViews() {
        panelLot = findViewById(R.id.panelLot);
        panelDesc = findViewById(R.id.panelDesc);
        panelVinValue = findViewById(R.id.panelVinValue);
        panelLocationName = findViewById(R.id.panelLocationName);
        panelRowCol = findViewById(R.id.panelRowCol);
        panelImage = findViewById(R.id.panelImage);

        btnCheckOut = findViewById(R.id.btnCheckOut);
        btnCheckIn = findViewById(R.id.btnCheckIn);
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

    private void populateHeader() {
        String lotStr = (vehicle != null && vehicle.lotnumber != null)
                ? String.valueOf(vehicle.lotnumber) : lotLegacy;

        String title = (vehicle != null && vehicle.title != null && !vehicle.title.isEmpty())
                ? vehicle.title
                : (vehicle != null ? safe(vehicle.marketingdescription) : descLegacy);

        String vinStr = (vehicle != null) ? safe(vehicle.vin) : vinLegacy;
        String thumbUrl = (vehicle != null) ? safe(vehicle.thumbUrl) : thumbLegacy;

        panelLot.setText(lotStr.isEmpty() ? "" : ("LOT # " + lotStr));
        panelDesc.setText(title);
        panelVinValue.setText(vinStr);

        String locationName = (vehicle != null) ? safe(vehicle.tentid) : "";
        if (panelLocationName != null) {
            panelLocationName.setText(defaulted(locationName, ""));
        }

        String row = (vehicle != null) ? safe(vehicle.row) : "";
        String col = (vehicle != null) ? safe(vehicle.col) : "";
        if (panelRowCol != null) {
            String rowColText = "";
            if (!row.isEmpty() || !col.isEmpty()) {
                rowColText = defaulted(col, "—") + " - " + defaulted(row, "—");
            }
            panelRowCol.setText(rowColText);
        }

        float density = getResources().getDisplayMetrics().density;
        int radiusPx = (int) (12 * density);

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

    private void wireButtons() {
        btnCheckOut.setOnClickListener(v -> showAssignDriverDialog());
        btnCheckIn.setOnClickListener(v -> postDriverCheckInHardcoded());
    }

    private String resolveOpportunityId() {
        String id = safe(crmId);
        if (!id.isEmpty()) return id;

        if (vehicle != null) {
            String vOpp = safe(vehicle.opportunityId);
            if (!vOpp.isEmpty()) return vOpp;
            String vCrm = safe(vehicle.crmopportunityid);
            if (!vCrm.isEmpty()) return vCrm;
        }

        return coalesce(
                safe(getIntent().getStringExtra(Nav.EXTRA_OPPORTUNITY_ID)),
                safe(getIntent().getStringExtra("opportunityId")),
                safe(getIntent().getStringExtra("crmId"))
        );
    }

    private void postDriverCheckInHardcoded() {
        final String opportunityId = resolveOpportunityId();
        Log.d(HTTP_LOG_TAG, "postDriverCheckInHardcoded() using opportunityId=" + opportunityId);

        if (opportunityId == null || opportunityId.trim().isEmpty()) {
            Toast.makeText(this, "Missing Opportunity ID; cannot check in.", Toast.LENGTH_LONG).show();
            Log.w(HTTP_LOG_TAG, "postDriverCheckInHardcoded: missing opportunityId");
            showFullScreenResult(false, "FAILED");
            return;
        }

        ConsignmentKeyPayload payload = new ConsignmentKeyPayload(
                "CheckedIn",
                "BarrettJackson",
                "Barrett-Jackson",
                "Remote Check-In"
        );

        DriverTaskApi api = ApiClient.getMemberApi().create(DriverTaskApi.class);
        Call<Void> call = api.updateConsignmentKey(opportunityId, payload);

        try {
            Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url());
            Log.i(HTTP_LOG_TAG, "Body: " + new com.google.gson.Gson().toJson(payload));
        } catch (Throwable t) {
            Log.w(HTTP_LOG_TAG, "Logging failed", t);
        }

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.i(HTTP_LOG_TAG, "DriverCheckIn response code=" + response.code());
                if (response.isSuccessful()) {
                    showFullScreenResult(true, "MOVED TO BARRETT-JACKSON");
                } else {
                    int code = response.code();
                    String errBody = null;
                    try {
                        errBody = response.errorBody() != null ? response.errorBody().string() : null;
                    } catch (Exception ignored) {}

                    Log.w(HTTP_LOG_TAG, "DriverCheckIn failed. code=" + code + ", body=" + errBody);

                    if (code == 404) {
                        showFullScreenResult(false, "KEY RECORD NOT FOUND");
                    } else {
                        showFullScreenResult(false, "FAILED");
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(HTTP_LOG_TAG, "DriverCheckIn network error", t);
                showFullScreenResult(false, "FAILED");
            }
        });
    }

    private void postConsignmentKeyUpdate(@NonNull String reason) {
        final String opportunityId = resolveOpportunityId();
        Log.d(HTTP_LOG_TAG, "postConsignmentKeyUpdate() using opportunityId=" + opportunityId + ", reason=" + reason);

        if (opportunityId == null || opportunityId.trim().isEmpty()) {
            Toast.makeText(this, "Missing Opportunity ID; cannot post key update.", Toast.LENGTH_LONG).show();
            Log.w(HTTP_LOG_TAG, "postConsignmentKeyUpdate: missing opportunityId");
            showFullScreenResult(false, "FAILED");
            return;
        }

        if (selectedDriverName == null || selectedDriverName.trim().isEmpty()) {
            Toast.makeText(this, "Missing driver name; cannot post key update.", Toast.LENGTH_LONG).show();
            Log.w(HTTP_LOG_TAG, "postConsignmentKeyUpdate: missing driver name");
            showFullScreenResult(false, "FAILED");
            return;
        }

        ConsignmentKeyPayload payload = new ConsignmentKeyPayload(
                "CheckedOut",
                "Driver",
                selectedDriverName,
                "Heading to Block"
        );

        DriverTaskApi api = ApiClient.getMemberApi().create(DriverTaskApi.class);
        Call<Void> call = api.updateConsignmentKey(opportunityId, payload);

        try {
            Log.i(HTTP_LOG_TAG, call.request().method() + " " + call.request().url());
            String json = new com.google.gson.Gson().toJson(payload);
            Log.i(HTTP_LOG_TAG, "Body: " + json);
        } catch (Throwable t) {
            Log.w(HTTP_LOG_TAG, "Logging failed", t);
        }

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.i(HTTP_LOG_TAG, "Response code=" + response.code());
                if (response.isSuccessful()) {
                    showFullScreenResult(true, "ASSIGNED TO " + selectedDriverName.toUpperCase());
                } else {
                    int code = response.code();
                    String errBody = null;
                    try {
                        errBody = response.errorBody() != null ? response.errorBody().string() : null;
                    } catch (Exception ignored) {}

                    Log.w(HTTP_LOG_TAG, "Consignment update failed. code=" + code + ", body=" + errBody);

                    if (code == 404) {
                        showFullScreenResult(false, "KEY RECORD NOT FOUND");
                    } else {
                        showFullScreenResult(false, "FAILED");
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(HTTP_LOG_TAG, "Network error updating consignment", t);
                showFullScreenResult(false, "FAILED");
            }
        });
    }

    private void showAssignDriverDialog() {
        try {
            DriverDirectory.initFromCache(this);
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to initialize DriverDirectory from cache", e);
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_assign_driver, null, false);

        final TextInputLayout tilDriver = view.findViewById(R.id.tilDriver);
        final TextInputEditText etDriver = view.findViewById(R.id.etDriver);

        final MaterialButton btnDriver = view.findViewById(R.id.btnDriverNfc);
        final MaterialButton btnCancel = view.findViewById(R.id.btnDriverCancel);
        final MaterialButton[] pig = new MaterialButton[1];
        pig[0] = view.findViewById(R.id.btnDriverPigPen);

        if (pig[0] == null) {
            MaterialButton created = new MaterialButton(this);
            created.setId(View.generateViewId());
            created.setText("PIGPEN");
            created.setTextColor(getResources().getColor(android.R.color.white));
            created.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#424242")));
            created.setCornerRadius((int) (12 * getResources().getDisplayMetrics().density));

            ViewGroup row = (ViewGroup) btnDriver.getParent();
            if (row != null) {
                int insertIndex = row.indexOfChild(btnCancel);
                if (insertIndex < 0) insertIndex = row.getChildCount();
                row.addView(created, insertIndex,
                        new ViewGroup.MarginLayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            pig[0] = created;
        }

        final TextView tvDriverDisplay = new TextView(this);
        tvDriverDisplay.setText("");
        tvDriverDisplay.setTextColor(Color.WHITE);
        tvDriverDisplay.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        tvDriverDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvDriverDisplay.setPadding(0, dp(10), 0, 0);
        tvDriverDisplay.setVisibility(View.GONE);

        if (view instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) view;
            int tilIndex = root.indexOfChild(tilDriver);
            if (tilIndex >= 0) {
                root.addView(tvDriverDisplay, tilIndex + 1,
                        new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));
            } else {
                root.addView(tvDriverDisplay,
                        new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));
            }
        }

        tilDriver.setHelperText(null);

        final AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(true)
                .create();
        dlg.show();

        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            );
        }

        btnDriver.setText("DRIVER");
        btnCancel.setText("CANCEL");
        pig[0].setText("PIGPEN");

        btnDriver.setEnabled(false);
        pig[0].setEnabled(false);
        tilDriver.setError(null);

        selectedDriverNumber = null;
        selectedDriverName = null;

        etDriver.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectedDriverNumber = (s == null) ? null : s.toString().trim();
                selectedDriverName = null;

                tilDriver.setError(null);
                boolean ok = false;

                tvDriverDisplay.setVisibility(View.GONE);
                tvDriverDisplay.setText("");

                if (s != null && s.length() > 0 && android.text.TextUtils.isDigitsOnly(s)) {
                    try {
                        int num = Integer.parseInt(selectedDriverNumber);
                        String name = DriverDirectory.nameFor(num);

                        if (name != null && !name.isEmpty()) {
                            selectedDriverName = name;
                            ok = true;

                            tvDriverDisplay.setText("Driver: " + name);
                            tvDriverDisplay.setTextColor(Color.parseColor("#2E7D32"));
                            tvDriverDisplay.setVisibility(View.VISIBLE);
                        } else {
                            tvDriverDisplay.setText("Unknown driver number");
                            tvDriverDisplay.setTextColor(Color.parseColor("#C62828"));
                            tvDriverDisplay.setVisibility(View.VISIBLE);
                        }
                    } catch (NumberFormatException ignored) {}
                }

                btnDriver.setEnabled(ok);
                pig[0].setEnabled(ok);
            }
        });

        etDriver.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE && btnDriver.isEnabled()) {
                btnDriver.performClick();
                return true;
            }
            return false;
        });

        btnCancel.setOnClickListener(v -> dlg.dismiss());

        btnDriver.setOnClickListener(v -> {
            if (selectedDriverNumber == null || selectedDriverName == null) {
                tilDriver.setError(getString(R.string.driver_number_error));
                return;
            }
            dlg.dismiss();
            postConsignmentKeyUpdate("Moving to Driver");
        });

        pig[0].setOnClickListener(v -> {
            if (selectedDriverNumber == null || selectedDriverName == null) {
                tilDriver.setError(getString(R.string.driver_number_error));
                return;
            }
            dlg.dismiss();
            postConsignmentKeyUpdate("Moving to PIGPEN");
        });
    }

    private void showFullScreenResult(boolean success, String text) {
        runOnUiThread(() -> {
            final ViewGroup decor = (ViewGroup) getWindow().getDecorView();

            final FrameLayout overlay = new FrameLayout(this);
            overlay.setClickable(true);
            overlay.setFocusable(true);
            overlay.setFocusableInTouchMode(true);

            final int bg = success ? Color.parseColor("#1B5E20") : Color.parseColor("#B71C1C");
            overlay.setBackgroundColor(bg);

            final TextView tv = new TextView(this);
            tv.setText(text == null ? "" : text);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(24), dp(24), dp(24), dp(24));
            tv.setAllCaps(true);

            FrameLayout.LayoutParams lpMsg = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            lpMsg.gravity = Gravity.CENTER;
            overlay.addView(tv, lpMsg);

            decor.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            overlay.setAlpha(0f);
            overlay.animate().alpha(1f).setDuration(200).start();

            View.OnClickListener dismiss = v -> {
                overlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                    try { decor.removeView(overlay); } catch (Exception ignored) {}
                }).start();
            };
            overlay.setOnClickListener(dismiss);
            overlay.postDelayed(() -> {
                if (overlay.getParent() != null) dismiss.onClick(overlay);
            }, 2200);
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String defaulted(String v, String d) {
        return (v != null && !v.trim().isEmpty()) ? v : d;
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    private static String coalesce(String... vals) {
        for (String s : vals) {
            if (s != null && !s.trim().isEmpty()) return s;
        }
        return "";
    }
}
