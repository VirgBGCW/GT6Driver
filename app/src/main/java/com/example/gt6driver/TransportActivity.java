package com.example.gt6driver;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
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
    private MaterialCheckBox cbFobs, cbNoKey;
    private TextInputLayout enterKeyCountLayout;
    private TextInputEditText enterKeyCountInput;
    private ImageButton btnKeyCamera;
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
    private boolean isChangingKeyState = false;

    // Fallbacks
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
            applyKeyUiToModel(intakeModel);

            // required for API
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
        cbFobs = findViewById(R.id.cbFobs);
        cbNoKey = findViewById(R.id.cbNoKey);
        enterKeyCountLayout = findViewById(R.id.enterKeyCountLayout);
        enterKeyCountInput = findViewById(R.id.enterKeyCountInput);
        btnKeyCamera = findViewById(R.id.btnKeyCamera);
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
        if (it.keyCheck != null) {
            Boolean hasKey = it.keyCheck.hasKey;
            Integer num = it.keyCheck.numberOfKeys;

            if (cbFobs != null && cbNoKey != null) {
                if (Boolean.TRUE.equals(hasKey)) {
                    cbFobs.setChecked(true);
                    cbNoKey.setChecked(false);
                    if (enterKeyCountInput != null && num != null) {
                        enterKeyCountInput.setText(String.valueOf(num));
                    }
                } else if (Boolean.FALSE.equals(hasKey)) {
                    cbFobs.setChecked(false);
                    cbNoKey.setChecked(true);
                    if (enterKeyCountInput != null) enterKeyCountInput.setText(null);
                }
            }

            if (hasKey != null) {
                setStatusIcon(keyIcon, true);
                keyDone = true;
                setKeyExpanded(false);
            } else {
                setStatusIcon(keyIcon, false);
                keyDone = false;
            }

            updateKeyCountEnabled();
        } else {
            setStatusIcon(keyIcon, false);
            keyDone = false;
        }
    }

    private void wireVinUi() {
        if (verifyVinHeader != null) verifyVinHeader.setOnClickListener(v -> setVinExpanded(!vinExpanded));

        if (btnVinMatch != null) {
            btnVinMatch.setOnClickListener(v -> {
                vinMatched = true;

                String curVin = currentVinForApi();
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

        if (btnKeyUpdate != null) {
            btnKeyUpdate.setOnClickListener(v -> {
                boolean hasFobs = cbFobs != null && cbFobs.isChecked();
                boolean noKey = cbNoKey != null && cbNoKey.isChecked();

                if (!hasFobs && !noKey) {
                    Toast.makeText(this, "Select FOB(s) or NO KEY.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (hasFobs) {
                    String count = safe(enterKeyCountInput);
                    if (TextUtils.isEmpty(count)) {
                        if (enterKeyCountInput != null) {
                            enterKeyCountInput.setError("Enter number of keys");
                            enterKeyCountInput.requestFocus();
                        }
                        return;
                    }
                    try {
                        int c = Integer.parseInt(count);
                        if (c <= 0) {
                            enterKeyCountInput.setError("Must be at least 1");
                            enterKeyCountInput.requestFocus();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        if (enterKeyCountInput != null) {
                            enterKeyCountInput.setError("Invalid number");
                            enterKeyCountInput.requestFocus();
                        }
                        return;
                    }
                }

                setStatusIcon(keyIcon, true);
                keyDone = true;
                setKeyExpanded(false);
                refreshConfirmEnabled();
                Toast.makeText(this, "Key check saved.", Toast.LENGTH_SHORT).show();
            });
        }

        if (cbFobs != null && cbNoKey != null) {
            CompoundButton.OnCheckedChangeListener keyMutualListener = (buttonView, isChecked) -> {
                if (isChangingKeyState) return;
                isChangingKeyState = true;

                if (buttonView == cbFobs && isChecked) cbNoKey.setChecked(false);
                else if (buttonView == cbNoKey && isChecked) cbFobs.setChecked(false);

                updateKeyCountEnabled();

                isChangingKeyState = false;
            };
            cbFobs.setOnCheckedChangeListener(keyMutualListener);
            cbNoKey.setOnCheckedChangeListener(keyMutualListener);
            updateKeyCountEnabled();
        }

        if (btnKeyCamera != null) {
            btnKeyCamera.setOnClickListener(v -> {
                Toast.makeText(this, "Key photo capture not wired on Transport screen yet.", Toast.LENGTH_SHORT).show();
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

        boolean hasFobs = cbFobs != null && cbFobs.isChecked();
        boolean noKey = cbNoKey != null && cbNoKey.isChecked();

        if (hasFobs) {
            model.keyCheck.hasKey = true;
            String keyCount = safe(enterKeyCountInput);
            try {
                model.keyCheck.numberOfKeys = TextUtils.isEmpty(keyCount) ? null : Integer.valueOf(keyCount);
            } catch (NumberFormatException ignore) {
                model.keyCheck.numberOfKeys = null;
            }
        } else if (noKey) {
            model.keyCheck.hasKey = false;
            model.keyCheck.numberOfKeys = null;
        }

        if (model.keyCheck.photoUrl == null) model.keyCheck.photoUrl = "";
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

    private void updateKeyCountEnabled() {
        boolean enable = cbFobs != null && cbFobs.isChecked();
        if (enterKeyCountLayout != null) {
            enterKeyCountLayout.setEnabled(enable);
            enterKeyCountLayout.setAlpha(enable ? 1f : 0.6f);
        }
        if (enterKeyCountInput != null) {
            enterKeyCountInput.setEnabled(enable);
            if (!enable) enterKeyCountInput.setText(null);
        }
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



