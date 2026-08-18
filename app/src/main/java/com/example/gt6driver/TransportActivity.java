package com.example.gt6driver;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.gt6driver.model.VehicleDetail;
import com.example.gt6driver.net.DriverTaskRepository;
import com.example.gt6driver.net.VehicleTaskIntake;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class TransportActivity extends AppCompatActivity {

    private static final String EXTRA_OPPORTUNITY_ID = "opportunityId";
    private static final String EXTRA_VEHICLE = "vehicle";

    // Vehicle panel views
    private ImageView ivCheckInThumb;
    private TextView panelLot;
    private TextView panelDesc;
    private TextView panelVin;

    // VIN verify UI
    private MaterialCardView verifyVinPanel;
    private View verifyVinHeader;
    private View verifyActions;
    private ImageView verifyIcon;
    private MaterialButton btnVinMatch, btnVinNoMatch;
    private TextView verifyVinValue;
    private View noMatchGroup;
    private TextInputLayout enterVinLayout;
    private TextInputEditText enterVinInput;
    private MaterialCheckBox cbVinNotified;
    private MaterialButton btnVinUpdate;
    private ImageButton btnCamera; // optional (still in your xml)

    // Key check UI
    private MaterialCardView keyPanel;
    private View keyHeader;
    private View keyGroup;
    private ImageView keyIcon;
    private MaterialCheckBox cbNoKey;
    private TextInputLayout enterKeyCountLayout, enterFobCountLayout, enterRemoteCountLayout;
    private TextInputEditText enterKeyCountInput, enterFobCountInput, enterRemoteCountInput;
    private MaterialButton btnKeyUpdate;

    // Confirm
    private MaterialButton btnConfirmOnsite;

    // Models / networking
    private VehicleDetail vehicle;
    private DriverTaskRepository driverTaskRepo;
    private VehicleTaskIntake intakeModel;
    private String opportunityId;

    // State
    private Boolean vinMatched = null;
    private boolean vinDone = false;
    private boolean keyDone = false;
    private boolean vinExpanded = false;
    private boolean vinNoMatchMode = false;
    private boolean keyExpanded = false;

    // Fallbacks
    private String lotLegacy = "";
    private String descLegacy = "";
    private String vinLegacy = "";
    private String thumbLegacy = "";
    private String driver = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transport);

        bindViews();
        readExtras(getIntent());
        populateVehicleHeader();

        btnConfirmOnsite.setEnabled(false);
        btnConfirmOnsite.setAlpha(0.5f);

        driverTaskRepo = new DriverTaskRepository();
        fetchIntakeAndBind();

        wireVinUi();
        wireKeyUi();

        btnConfirmOnsite.setOnClickListener(v -> {
            if (TextUtils.isEmpty(opportunityId)) {
                Toast.makeText(this, "Missing opportunityId", Toast.LENGTH_LONG).show();
                return;
            }
            if (intakeModel == null) {
                Toast.makeText(this, "Transport data not loaded yet.", Toast.LENGTH_SHORT).show();
                return;
            }

            applyVinUiToModel(intakeModel);
            if (!isValidVinLength(intakeModel.vinVerify.newVin)) {
                Toast.makeText(
                        this,
                        "VIN must be 0 or 4-17 characters. Tap NO MATCH and enter the corrected VIN.",
                        Toast.LENGTH_LONG
                ).show();
                setVinExpanded(true);
                return;
            }

            applyKeyUiToModel(intakeModel);
            applyDriverToModel(intakeModel);
            if (TextUtils.isEmpty(intakeModel.checkInBy)) {
                Toast.makeText(this, "Missing driver name; cannot save transport.", Toast.LENGTH_LONG).show();
                return;
            }

            // Path carries opportunityId on PUT; repository strips it from the request body.
            if (TextUtils.isEmpty(intakeModel.opportunityId)) {
                intakeModel.opportunityId = opportunityId;
            }

            btnConfirmOnsite.setEnabled(false);
            btnConfirmOnsite.setAlpha(0.5f);

            driverTaskRepo.saveIntake(opportunityId, intakeModel, new DriverTaskRepository.SaveCallback() {
                @Override public void onSaved() {
                    Toast.makeText(TransportActivity.this, "Transport saved", Toast.LENGTH_LONG).show();
                    finish();
                }
                @Override public void onError(Throwable t) {
                    Toast.makeText(TransportActivity.this, "Save failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    refreshConfirmEnabled();
                }
                @Override public void onHttpError(int code, String message) {
                    Toast.makeText(TransportActivity.this, "Save HTTP " + code + ": " + message, Toast.LENGTH_LONG).show();
                    refreshConfirmEnabled();
                }
            });
        });
    }

    private void bindViews() {
        ivCheckInThumb = findViewById(R.id.ivCheckInThumb);
        panelLot = findViewById(R.id.panelLot);
        panelDesc = findViewById(R.id.panelDesc);
        panelVin = findViewById(R.id.panelVin);

        verifyVinPanel = findViewById(R.id.verifyVinPanel);
        verifyVinHeader = findViewById(R.id.verifyVinHeader);
        verifyActions = findViewById(R.id.verifyActions);
        verifyIcon = findViewById(R.id.verifyIcon);
        btnVinMatch = findViewById(R.id.btnVinMatch);
        btnVinNoMatch = findViewById(R.id.btnVinNoMatch);
        verifyVinValue = findViewById(R.id.verifyVinValue);
        noMatchGroup = findViewById(R.id.noMatchGroup);
        enterVinLayout = findViewById(R.id.enterVinLayout);
        enterVinInput = findViewById(R.id.enterVinInput);
        cbVinNotified = findViewById(R.id.cbVinNotified);
        btnVinUpdate = findViewById(R.id.btnVinUpdate);
        btnCamera = findViewById(R.id.btnCamera);

        keyPanel = findViewById(R.id.keyPanel);
        keyHeader = findViewById(R.id.keyHeader);
        keyGroup = findViewById(R.id.keyGroup);
        keyIcon = findViewById(R.id.keyIcon);
        cbNoKey = findViewById(R.id.cbNoKey);
        enterKeyCountLayout = findViewById(R.id.enterKeyCountLayout);
        enterKeyCountInput = findViewById(R.id.enterKeyCountInput);
        enterFobCountLayout = findViewById(R.id.enterFobCountLayout);
        enterFobCountInput = findViewById(R.id.enterFobCountInput);
        enterRemoteCountLayout = findViewById(R.id.enterRemoteCountLayout);
        enterRemoteCountInput = findViewById(R.id.enterRemoteCountInput);
        btnKeyUpdate = findViewById(R.id.btnKeyUpdate);

        btnConfirmOnsite = findViewById(R.id.btnConfirmOnsite);
    }

    private void readExtras(Intent intent) {
        vehicle = intent.getParcelableExtra(EXTRA_VEHICLE);

        opportunityId = intent.getStringExtra(EXTRA_OPPORTUNITY_ID);
        if ((TextUtils.isEmpty(opportunityId)) && vehicle != null) {
            if (!TextUtils.isEmpty(vehicle.opportunityId)) opportunityId = vehicle.opportunityId;
            else if (!TextUtils.isEmpty(vehicle.crmopportunityid)) opportunityId = vehicle.crmopportunityid;
        }

        lotLegacy = safe(intent.getStringExtra(Nav.EXTRA_LOT));
        descLegacy = safe(intent.getStringExtra(Nav.EXTRA_DESC));
        vinLegacy = safe(intent.getStringExtra(Nav.EXTRA_VIN));
        thumbLegacy = safe(intent.getStringExtra(Nav.EXTRA_THUMB));
        driver = safe(intent.getStringExtra(Nav.EXTRA_DRIVER));
    }

    private void populateVehicleHeader() {
        String lot = (vehicle != null && vehicle.lotnumber != null)
                ? "LOT # " + vehicle.lotnumber
                : (lotLegacy.isEmpty() ? "" : "LOT # " + lotLegacy);

        String desc = (vehicle != null && !TextUtils.isEmpty(vehicle.title))
                ? vehicle.title
                : (vehicle != null ? safe(vehicle.marketingdescription) : descLegacy);

        String vin = (vehicle != null) ? safe(vehicle.vin) : vinLegacy;
        String thumbUrl = (vehicle != null) ? safe(vehicle.thumbUrl) : thumbLegacy;

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

    private void fetchIntakeAndBind() {
        if (TextUtils.isEmpty(opportunityId)) {
            Toast.makeText(this, "Missing opportunityId", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setPanelsEnabled(false);

        driverTaskRepo.fetchIntake(opportunityId, new DriverTaskRepository.IntakeCallback() {
            @Override public void onSuccess(VehicleTaskIntake it) {
                intakeModel = (it != null ? it : new VehicleTaskIntake());
                intakeModel.opportunityId = opportunityId;

                bindIntakeToTransportUi(intakeModel);
                setPanelsEnabled(true);
                updateKeyInputEnabled();
                refreshConfirmEnabled();
            }

            @Override public void onError(Throwable t) {
                Toast.makeText(TransportActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }

            @Override public void onHttpError(int code, String message) {
                Toast.makeText(TransportActivity.this, "HTTP " + code + ": " + message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void bindIntakeToTransportUi(VehicleTaskIntake it) {
        // ---- VIN ----
        if (it.vinVerify != null) {
            vinMatched = it.vinVerify.isMatched;
            String displayVin = !TextUtils.isEmpty(safe(it.vinVerify.newVin))
                    ? safe(it.vinVerify.newVin)
                    : safe(panelVin != null ? panelVin.getText().toString() : vinLegacy);

            if (verifyVinValue != null) verifyVinValue.setText(displayVin);

            if (cbVinNotified != null) {
                cbVinNotified.setChecked(Boolean.TRUE.equals(it.vinVerify.isNotified));
            }

            if (Boolean.TRUE.equals(it.vinVerify.isMatched)) {
                setStatusIcon(verifyIcon, true);
                vinDone = true;
                vinNoMatchMode = false;
                setVinExpanded(false);
            } else if (!TextUtils.isEmpty(safe(it.vinVerify.newVin))) {
                // submitted a new VIN (no-match path)
                setStatusIcon(verifyIcon, true);
                vinDone = true;
                vinNoMatchMode = true;
                setVinExpanded(false);
            } else {
                setStatusIcon(verifyIcon, false);
                vinDone = false;
                vinNoMatchMode = true;
            }
        } else {
            setStatusIcon(verifyIcon, false);
            vinDone = false;
        }

        // ---- KEY ----
        if (it.keyCheck != null
                || it.fobCheck != null
                || it.remoteControlCheck != null
                || it.noKeysArePresent != null) {
            Boolean hasKey = (it.keyCheck != null) ? it.keyCheck.hasKey : null;
            int keyCount = intOrZero((it.keyCheck != null) ? it.keyCheck.numberOfKeys : null);
            int fobCount = intOrZero((it.fobCheck != null) ? it.fobCheck.numberOfFOBs : null);
            int remoteCount = intOrZero((it.remoteControlCheck != null)
                    ? it.remoteControlCheck.numberOfRemoteControls
                    : null);
            boolean noKeys = Boolean.TRUE.equals(it.noKeysArePresent)
                    || Boolean.FALSE.equals(hasKey);

            if (noKeys) {
                keyCount = 0;
            }

            setCountText(enterKeyCountInput, keyCount);
            setCountText(enterFobCountInput, fobCount);
            setCountText(enterRemoteCountInput, remoteCount);
            if (cbNoKey != null) cbNoKey.setChecked(noKeys);

            if (hasKey != null
                    || it.noKeysArePresent != null
                    || it.fobCheck != null
                    || it.remoteControlCheck != null) {
                setStatusIcon(keyIcon, true);
                keyDone = true;
                setKeyExpanded(false);
            } else {
                setStatusIcon(keyIcon, false);
                keyDone = false;
            }

            updateKeyInputEnabled();
        } else {
            setCountText(enterKeyCountInput, 0);
            setCountText(enterFobCountInput, 0);
            setCountText(enterRemoteCountInput, 0);
            if (cbNoKey != null) cbNoKey.setChecked(false);
            setStatusIcon(keyIcon, false);
            keyDone = false;
            updateKeyInputEnabled();
        }
    }

    private void wireVinUi() {
        if (verifyVinHeader != null) verifyVinHeader.setOnClickListener(v -> setVinExpanded(!vinExpanded));

        if (btnVinMatch != null) {
            btnVinMatch.setOnClickListener(v -> {
                String curVin = currentVinForApi();
                vinMatched = true;

                if (verifyVinValue != null) verifyVinValue.setText(curVin);

                vinNoMatchMode = false;
                vinDone = true;

                setStatusIcon(verifyIcon, true);
                setVinExpanded(false);
                refreshConfirmEnabled();
            });
        }

        if (btnVinNoMatch != null) {
            btnVinNoMatch.setOnClickListener(v -> {
                vinMatched = false;
                vinNoMatchMode = true;
                vinDone = false;

                setStatusIcon(verifyIcon, false);
                setVinExpanded(true);
                refreshConfirmEnabled();
            });
        }

        if (btnVinUpdate != null) {
            btnVinUpdate.setOnClickListener(v -> {
                String entered = safe(enterVinInput);
                if (TextUtils.isEmpty(entered)) {
                    if (enterVinInput != null) {
                        enterVinInput.setError("VIN is required");
                        enterVinInput.requestFocus();
                    }
                    return;
                }
                if (!isValidVinLength(entered)) {
                    if (enterVinInput != null) {
                        enterVinInput.setError("VIN must be 0 or 4-17 characters");
                        enterVinInput.requestFocus();
                    }
                    return;
                }

                if (verifyVinValue != null) verifyVinValue.setText(entered);

                Toast.makeText(this, "VIN submitted: " + entered, Toast.LENGTH_SHORT).show();

                if (enterVinInput != null) enterVinInput.setText("");

                vinMatched = false; // no-match flow
                vinNoMatchMode = false;
                vinDone = true;

                setStatusIcon(verifyIcon, true);
                setVinExpanded(false);
                refreshConfirmEnabled();
            });
        }

        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> {
                // If you want photo capture here later, hook it up.
                Toast.makeText(this, "VIN photo capture not wired on Transport screen yet.", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void wireKeyUi() {
        if (keyHeader != null) keyHeader.setOnClickListener(v -> setKeyExpanded(!keyExpanded));

        setDefaultCountTextIfEmpty(enterKeyCountInput);
        setDefaultCountTextIfEmpty(enterFobCountInput);
        setDefaultCountTextIfEmpty(enterRemoteCountInput);
        updateKeyInputEnabled();

        if (btnKeyUpdate != null) {
            btnKeyUpdate.setOnClickListener(v -> {
                boolean noKey = cbNoKey != null && cbNoKey.isChecked();

                if (noKey) {
                    resetPhysicalKeyCountToZero();
                } else if (!validateCountInput(enterKeyCountInput, "Number of keys")) {
                    return;
                }

                if (!validateCountInput(enterFobCountInput, "Number of FOB(s)")
                        || !validateCountInput(enterRemoteCountInput, "Number of remote(s)")) {
                    return;
                }

                if (intakeModel == null) intakeModel = new VehicleTaskIntake();
                applyKeyUiToModel(intakeModel);

                setStatusIcon(keyIcon, true);
                keyDone = true;
                setKeyExpanded(false);
                refreshConfirmEnabled();
                Toast.makeText(this, "Key check saved.", Toast.LENGTH_SHORT).show();
            });
        }

        if (cbNoKey != null) {
            cbNoKey.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) resetPhysicalKeyCountToZero();
                updateKeyInputEnabled();
            });
        }
    }

    private void applyVinUiToModel(VehicleTaskIntake model) {
        if (model.vinVerify == null) model.vinVerify = new VehicleTaskIntake.VinVerify();

        model.vinVerify.isMatched = Boolean.TRUE.equals(vinMatched);

        String currentVinShown = currentVinForApi();
        model.vinVerify.newVin = TextUtils.isEmpty(currentVinShown) ? "" : currentVinShown;

        model.vinVerify.isNotified = (cbVinNotified != null && cbVinNotified.isChecked());

        // photoUrl stays as-is unless you wire camera in
        if (model.vinVerify.photoUrl == null) model.vinVerify.photoUrl = "";
    }

    private void applyKeyUiToModel(VehicleTaskIntake model) {
        if (model.keyCheck == null) model.keyCheck = new VehicleTaskIntake.KeyCheck();
        if (model.fobCheck == null) model.fobCheck = new VehicleTaskIntake.FobCheck();
        if (model.remoteControlCheck == null) {
            model.remoteControlCheck = new VehicleTaskIntake.RemoteControlCheck();
        }

        boolean noKey = cbNoKey != null && cbNoKey.isChecked();

        int keyCount = noKey ? 0 : readCountOrZero(enterKeyCountInput);
        int fobCount = readCountOrZero(enterFobCountInput);
        int remoteCount = readCountOrZero(enterRemoteCountInput);

        model.keyCheck.hasKey = !noKey;
        model.keyCheck.numberOfKeys = keyCount;
        model.fobCheck.numberOfFOBs = fobCount;
        model.remoteControlCheck.numberOfRemoteControls = remoteCount;
        model.noKeysArePresent = noKey;

        if (model.keyCheck.photoUrl == null) model.keyCheck.photoUrl = "";
    }

    private void applyDriverToModel(VehicleTaskIntake model) {
        String driverName = resolveDriverName();
        if (!TextUtils.isEmpty(driverName)) {
            model.checkInBy = driverName;
        } else if (model.checkInBy == null) {
            model.checkInBy = "";
        }
    }

    private void refreshConfirmEnabled() {
        boolean ok = vinDone && keyDone;
        btnConfirmOnsite.setEnabled(ok);
        btnConfirmOnsite.setAlpha(ok ? 1f : 0.5f);
    }

    private void setVinExpanded(boolean expanded) {
        vinExpanded = expanded;
        if (verifyVinValue != null) verifyVinValue.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (verifyActions != null) verifyActions.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (noMatchGroup != null) noMatchGroup.setVisibility(expanded && vinNoMatchMode ? View.VISIBLE : View.GONE);
    }

    private void setKeyExpanded(boolean expanded) {
        keyExpanded = expanded;
        if (keyGroup != null) keyGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private void updateKeyInputEnabled() {
        boolean enablePhysicalKeys = cbNoKey == null || !cbNoKey.isChecked();
        setCountInputEnabled(enterKeyCountLayout, enterKeyCountInput, enablePhysicalKeys);
        setCountInputEnabled(enterFobCountLayout, enterFobCountInput, true);
        setCountInputEnabled(enterRemoteCountLayout, enterRemoteCountInput, true);
    }

    private void setCountInputEnabled(TextInputLayout layout, TextInputEditText input, boolean enabled) {
        if (layout != null) {
            layout.setEnabled(enabled);
            layout.setAlpha(enabled ? 1f : 0.85f);
        }
        if (input != null) {
            input.setEnabled(enabled);
        }
    }

    private void resetPhysicalKeyCountToZero() {
        setCountText(enterKeyCountInput, 0);
    }

    private boolean validateCountInput(TextInputEditText input, String label) {
        if (input == null) return true;

        String raw = safe(input);
        if (TextUtils.isEmpty(raw)) {
            input.setText("0");
            input.setError(null);
            return true;
        }

        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                input.setError(label + " must be 0 or greater");
                input.requestFocus();
                return false;
            }
            input.setError(null);
            return true;
        } catch (NumberFormatException e) {
            input.setError("Invalid " + label.toLowerCase());
            input.requestFocus();
            return false;
        }
    }

    private int readCountOrZero(TextInputEditText input) {
        String raw = safe(input);
        if (TextUtils.isEmpty(raw)) return 0;
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setDefaultCountTextIfEmpty(TextInputEditText input) {
        if (input != null && TextUtils.isEmpty(safe(input))) {
            input.setText("0");
        }
    }

    private void setCountText(TextInputEditText input, int count) {
        if (input != null) {
            input.setText(String.valueOf(Math.max(0, count)));
        }
    }

    private int intOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private boolean isValidVinLength(String vin) {
        String value = safe(vin);
        return "0".equals(value) || (value.length() >= 4 && value.length() <= 17);
    }

    private String resolveDriverName() {
        if (!TextUtils.isEmpty(driver)) return driver.trim();

        try {
            String fromSession = com.example.gt6driver.session.CurrentSelection.get().getDriverName();
            if (!TextUtils.isEmpty(fromSession)) return fromSession.trim();
        } catch (Throwable ignored) {}

        return "";
    }

    private void setStatusIcon(ImageView icon, boolean ok) {
        if (icon == null) return;
        icon.setBackground(null);
        if (ok) {
            icon.setImageResource(R.drawable.ic_check_circle_24);
            icon.setColorFilter(Color.parseColor("#2E7D32"));
        } else {
            icon.setImageResource(R.drawable.ic_close_24);
            icon.setColorFilter(Color.parseColor("#D32F2F"));
        }
    }

    private void setPanelsEnabled(boolean enabled) {
        float alpha = enabled ? 1f : 0.6f;

        if (verifyVinPanel != null) verifyVinPanel.setAlpha(alpha);
        if (keyPanel != null) keyPanel.setAlpha(alpha);

        setGroupEnabled(verifyVinPanel, enabled);
        setGroupEnabled(keyPanel, enabled);
    }

    private void setGroupEnabled(View v, boolean enabled) {
        if (v == null) return;
        v.setEnabled(enabled);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setGroupEnabled(vg.getChildAt(i), enabled);
            }
        }
    }

    private String currentVinForApi() {
        String ui = (verifyVinValue != null && verifyVinValue.getText() != null)
                ? verifyVinValue.getText().toString().trim()
                : "";
        if (!TextUtils.isEmpty(ui)) return ui;

        // fall back to header vin text
        String headerVin = (panelVin != null && panelVin.getText() != null)
                ? panelVin.getText().toString().trim()
                : "";
        if (!TextUtils.isEmpty(headerVin)) return headerVin;

        return vinLegacy;
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
    private String safe(TextInputEditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }
}



