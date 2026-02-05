// app/src/main/java/com/example/gt6driver/CheckInDetailsActivity.java
package com.example.gt6driver;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.io.File;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.gt6driver.model.VehicleDetail;
import com.example.gt6driver.net.DriverTaskRepository;
import com.example.gt6driver.net.VehicleTaskIntake;
import com.example.gt6driver.util.DeviceInfo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class CheckInDetailsActivity extends AppCompatActivity {

    // ==== Intake Video (uses IntakeVideoActivity) ====
    private MaterialButton btnVideoRecord;
    private MaterialButton btnVideoAccept;
    private Uri lastCapturedVideoUri;

    // for static AZURE STORAGE
    private static final String BLOB_BASE = "https://stgt6driverappprod.blob.core.windows.net/driver/";
    private static final String COMPRESSED_BASE = "https://stgt6driverappprod.blob.core.windows.net/compressed-files/";

    // Keys to match what ActionActivity sends
    private static final String EXTRA_VEHICLE        = "vehicle";
    private static final String EXTRA_OPPORTUNITY_ID = "opportunityId";

    private static final String TAG = "GT6Intake";

    private VehicleDetail vehicle;

    private TextView panelLot, panelDesc, panelVin;
    private ImageView ivVehicleThumb;

    // MILEAGE
    private MaterialCardView mileagePanel;
    private View mileageHeader;
    private View mileageGroup;
    private ImageView mileageIcon;
    private TextInputLayout enterMileageLayout;
    private TextInputEditText enterMileageInput;
    private ImageButton btnMileageCamera;
    private MaterialButton btnMileageUpdate;
    private boolean mileageExpanded = false;

    // INTAKE VIDEO
    private MaterialCardView videoPanel;
    private View videoHeader;
    private View videoGroup;
    private ImageView videoIcon;
    private boolean videoExpanded = false;

    // DESCRIPTION
    private MaterialCardView descPanel;
    private View descHeader;
    private View descGroup;
    private ImageView descIcon;
    private TextView descValue;
    private MaterialButton btnDescCorrect, btnDescIncorrect;
    private View descIncorrectGroup;
    private MaterialCheckBox cbIncorrectMileage, cbIncorrectVin, cbIncorrectDetails, cbSpellingErrors;
    private MaterialButton btnDescUpdate;
    private boolean descExpanded = false;

    // QUALITY CONCERNS
    private MaterialCardView qualityPanel;
    private View qualityHeader;
    private View qualityGroup, qualityDetailsGroup;
    private ImageView qualityIcon;
    private TextView qualityMessage;
    private MaterialButton btnQualityNoConcerns, btnQualityConcerns, btnQualityUpdate;
    private Boolean qualityHasConcerns = null;
    private MaterialCheckBox cbExteriorDamage, cbInteriorDamage, cbTiresWheels, cbMechanical, cbServiceLights;
    private boolean qualityExpanded = false;

    // Camera/Permissions
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<String> requestWritePermissionLauncher;
    private ActivityResultLauncher<String> requestReadImagesPermissionLauncher;

    // Full-res photo capture state (NOW MILEAGE ONLY)
    private String pendingPhotoLabel = null; // "mileage"
    private Uri pendingPhotoUri = null;
    private ActivityResultLauncher<Intent> takePictureLauncher;

    // Video result (IntakeVideoActivity)
    private ActivityResultLauncher<Intent> recordVideoLauncher;

    private MaterialButton btnConfirm;

    private String lot, description, eventName, driver, mode, vin;
    private String shortDescription;
    private int eventId;
    private String thumbUrl;

    // Completion flags (VIN/KEY removed)
    private boolean mileageDone = false;
    private boolean videoDone = false;
    private boolean descDone = false;
    private boolean qualityDone = false;

    // ✅ NEW: Must have loaded intake from API before we allow PUT
    private boolean intakeLoaded = false;

    // Networking
    private DriverTaskRepository driverTaskRepo;
    private String opportunityId; // REQUIRED via Intent

    // Keep latest model to PUT back
    private VehicleTaskIntake intakeModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_in_details);

        // Vehicle panel
        panelLot = findViewById(R.id.panelLot);
        panelDesc = findViewById(R.id.panelDesc);
        panelVin = findViewById(R.id.panelVin);
        ivVehicleThumb = findViewById(R.id.ivCheckInThumb);

        // MILEAGE
        mileagePanel = findViewById(R.id.mileagePanel);
        mileageHeader = findViewById(R.id.mileageHeader);
        mileageGroup = findViewById(R.id.mileageGroup);
        mileageIcon = findViewById(R.id.mileageIcon);
        enterMileageLayout = findViewById(R.id.enterMileageLayout);
        enterMileageInput = findViewById(R.id.enterMileageInput);
        btnMileageCamera = findViewById(R.id.btnMileageCamera);
        btnMileageUpdate = findViewById(R.id.btnMileageUpdate);

        // INTAKE VIDEO (buttons only; recording happens in IntakeVideoActivity)
        videoPanel = findViewById(R.id.videoPanel);
        videoHeader = findViewById(R.id.videoHeader);
        videoGroup = findViewById(R.id.videoGroup);
        videoIcon = findViewById(R.id.videoIcon);
        btnVideoRecord = findViewById(R.id.btnVideoRecord);
        btnVideoAccept = findViewById(R.id.btnVideoAccept);

        // DESCRIPTION
        descPanel = findViewById(R.id.descPanel);
        descHeader = findViewById(R.id.descHeader);
        descGroup = findViewById(R.id.descGroup);
        descIcon = findViewById(R.id.descIcon);
        descValue = findViewById(R.id.descValue);
        btnDescCorrect = findViewById(R.id.btnDescCorrect);
        btnDescIncorrect = findViewById(R.id.btnDescIncorrect);
        descIncorrectGroup = findViewById(R.id.descIncorrectGroup);
        cbIncorrectMileage = findViewById(R.id.cbIncorrectMileage);
        cbIncorrectVin = findViewById(R.id.cbIncorrectVin);
        cbIncorrectDetails = findViewById(R.id.cbIncorrectDetails);
        cbSpellingErrors = findViewById(R.id.cbSpellingErrors);
        btnDescUpdate = findViewById(R.id.btnDescUpdate);

        // QUALITY
        qualityPanel = findViewById(R.id.qualityPanel);
        qualityHeader = findViewById(R.id.qualityHeader);
        qualityGroup = findViewById(R.id.qualityGroup);
        qualityDetailsGroup = findViewById(R.id.qualityDetailsGroup);
        qualityIcon = findViewById(R.id.qualityIcon);
        qualityMessage = findViewById(R.id.qualityMessage);
        btnQualityNoConcerns = findViewById(R.id.btnQualityNoConcerns);
        btnQualityConcerns = findViewById(R.id.btnQualityConcerns);
        btnQualityUpdate = findViewById(R.id.btnQualityUpdate);
        cbExteriorDamage = findViewById(R.id.cbExteriorDamage);
        cbInteriorDamage = findViewById(R.id.cbInteriorDamage);
        cbTiresWheels = findViewById(R.id.cbTiresWheels);
        cbMechanical = findViewById(R.id.cbMechanical);
        cbServiceLights = findViewById(R.id.cbServiceLights);

        // Back Button confirm
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                new MaterialAlertDialogBuilder(CheckInDetailsActivity.this)
                        .setTitle("Cancel Intake")
                        .setMessage("Are you sure you want to cancel?")
                        .setNegativeButton("NO", (dialog, which) -> dialog.dismiss())
                        .setPositiveButton("YES", (dialog, which) -> {
                            dialog.dismiss();
                            finish();
                        })
                        .show();
            }
        });

        // Confirm button (START DISABLED until required panels done)
        btnConfirm = findViewById(R.id.btnConfirmIntake);
        setConfirmEnabled(false);

        // ===== Intent extras =====
        Intent in = getIntent();

        // Preferred path: full vehicle model
        vehicle = in.getParcelableExtra(EXTRA_VEHICLE);

        // Legacy fields (fallbacks if vehicle is null)
        lot = in.getStringExtra(Nav.EXTRA_LOT);
        description = in.getStringExtra(Nav.EXTRA_DESC);
        shortDescription = in.getStringExtra("shortdesc");
        eventName = in.getStringExtra(Nav.EXTRA_EVENT_NAME);
        eventId = in.getIntExtra(Nav.EXTRA_EVENT_ID, -1);

        if (driver == null || driver.trim().isEmpty()) {
            try {
                String fromSession = com.example.gt6driver.session.CurrentSelection.get().getDriverName();
                if (fromSession != null && !fromSession.trim().isEmpty()) driver = fromSession;
            } catch (Throwable ignored) {}
        }

        mode = in.getStringExtra("mode");
        vin = in.getStringExtra(Nav.EXTRA_VIN);
        thumbUrl = in.getStringExtra(Nav.EXTRA_THUMB);

        // Opportunity Id (first from intent, else from vehicle)
        opportunityId = in.getStringExtra(EXTRA_OPPORTUNITY_ID);
        if ((opportunityId == null || opportunityId.trim().isEmpty()) && vehicle != null) {
            if (vehicle.opportunityId != null && !vehicle.opportunityId.isEmpty()) {
                opportunityId = vehicle.opportunityId;
            } else if (vehicle.crmopportunityid != null && !vehicle.crmopportunityid.isEmpty()) {
                opportunityId = vehicle.crmopportunityid;
            }
        }

        // If we have a vehicle, prefer its values for header UI
        if (vehicle != null) {
            String vLot = vehicle.lotnumber != null ? vehicle.lotnumber : "";
            String vDesc = safeStr(vehicle.marketingdescription);
            description = vDesc;
            String vVin = vehicle.vin != null ? vehicle.vin : "";
            String vThumb = !isEmpty(vehicle.thumbUrl) ? vehicle.thumbUrl
                    : (vehicle.tbuncpath != null ? vehicle.tbuncpath : "");

            lot = firstNonEmpty(vLot, lot);
            description = firstNonEmpty(vDesc, description);
            vin = firstNonEmpty(vVin, vin);
            thumbUrl = firstNonEmpty(vThumb, thumbUrl);
        }

        // Vehicle header
        panelLot.setText("LOT # " + (lot != null ? lot : ""));
        panelDesc.setText(description != null ? description : "");
        if (panelVin != null) panelVin.setText("VIN: " + (vin != null ? vin : ""));

        // Load vehicle thumbnail
        loadThumbIntoHeader();

        if (descValue != null) {
            descValue.setText(!TextUtils.isEmpty(description) ? description : "No description available.");
        }

        // ===== PHOTO launcher (MILEAGE ONLY) =====
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        if (result.getResultCode() != RESULT_OK) {
                            if (pendingPhotoUri != null && !awaitNonZeroSize(pendingPhotoUri)) {
                                try { getContentResolver().delete(pendingPhotoUri, null, null); } catch (Exception ignore) {}
                            }
                            pendingPhotoLabel = null;
                            pendingPhotoUri = null;
                            return;
                        }

                        final long now = System.currentTimeMillis();

                        Uri requested = pendingPhotoUri;
                        Uri returned  = preferResultUri(result.getData(), null);

                        android.graphics.Bitmap bmpFromExtras = null;
                        if (result.getData() != null && result.getData().getExtras() != null) {
                            Object extra = result.getData().getExtras().get("data");
                            if (extra instanceof android.graphics.Bitmap) bmpFromExtras = (android.graphics.Bitmap) extra;
                        }

                        // Case A: camera wrote to our requested row
                        if (requested != null && awaitNonZeroSize(requested)) {
                            try { getContentResolver().notifyChange(requested, null); } catch (Exception ignore) {}
                            finalizePhotoAndBind(requested, pendingPhotoLabel);
                            return;
                        }

                        // Case B: need to write data ourselves to requested row
                        Uri dest = (requested != null) ? requested : createIntakePhotoUri(pendingPhotoLabel);
                        if (dest == null) {
                            Toast.makeText(this, "Failed to create GT6 photo row.", Toast.LENGTH_SHORT).show();
                            pendingPhotoLabel = null;
                            pendingPhotoUri = null;
                            return;
                        }

                        boolean ok;
                        if (bmpFromExtras != null) {
                            ok = saveBitmapToUriAsJpeg(bmpFromExtras, dest, 92);
                        } else {
                            if (returned == null) {
                                returned = findLatestCapturedImage(now - 5 * 60_000L);
                            }
                            ok = (returned != null) && copyUri(returned, dest);
                        }

                        if (!ok || !awaitNonZeroSize(dest)) {
                            try { getContentResolver().delete(dest, null, null); } catch (Exception ignore) {}
                            Toast.makeText(this, "Photo save/copy failed.", Toast.LENGTH_SHORT).show();
                            pendingPhotoLabel = null;
                            pendingPhotoUri = null;
                            return;
                        }

                        try { getContentResolver().notifyChange(dest, null); } catch (Exception ignore) {}
                        finalizePhotoAndBind(dest, pendingPhotoLabel);

                    } finally {
                        pendingPhotoLabel = null;
                        pendingPhotoUri = null;
                    }
                }
        );

        // ===== VIDEO launcher (IntakeVideoActivity) =====
        recordVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK) return;

                    Intent data = result.getData();
                    Uri finalDest = null;

                    if (data != null) {
                        try {
                            finalDest = data.getParcelableExtra(IntakeVideoActivity.EXTRA_RESULT_VIDEO_URI);
                        } catch (Throwable ignored) {}
                        if (finalDest == null) finalDest = data.getData();
                    }

                    if (finalDest == null) {
                        Toast.makeText(this, "No video returned.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    lastCapturedVideoUri = finalDest;
                    verifyDestAndReport(finalDest, "Video");

                    // Sidecar
                    String createdAtUtc = isoUtcNow();
                    String deviceName = DeviceInfo.getDeviceName(this);
                    String driverName = resolveDriverName();
                    if (TextUtils.isEmpty(driverName)) driverName = "Upload Agent";

                    boolean sidecarOk = writeSidecarJsonToDownload(
                            "intake",
                            createdAtUtc,
                            consignmentIdStr(),
                            deviceName,
                            driverName,
                            (lot != null ? lot : "")
                    );
                    Log.i(TAG, "Sidecar write attempted for intake.meta.json ok=" + sidecarOk);

                    // Auto-accept intake video
                    acceptIntakeVideoIfPresent();

                    // Kick uploader immediately
                    com.example.gt6driver.sync.GT6MediaSync.enqueueImmediate(this);
                }
        );

        // ===== Permission launchers =====
        requestReadImagesPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openCameraPhoto(pendingPhotoLabel != null ? pendingPhotoLabel : "mileage");
                    } else {
                        Toast.makeText(this, "Photos permission is required.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        if (pendingPhotoLabel == null) pendingPhotoLabel = "mileage";
                        openCameraPhoto(pendingPhotoLabel);
                    } else {
                        Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        requestWritePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openCameraPhoto(pendingPhotoLabel != null ? pendingPhotoLabel : "mileage");
                    } else {
                        Toast.makeText(this, "Storage permission is required to save photos.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // ===== Click listeners =====

        // MILEAGE
        if (mileageHeader != null) mileageHeader.setOnClickListener(v -> toggleMileagePanel());

        if (btnMileageCamera != null) {
            btnMileageCamera.setOnClickListener(v -> {
                setMileageExpanded(true);
                hideKeyboard();
                ensureCameraForPhoto("mileage");
            });
        }

        if (btnMileageUpdate != null) {
            btnMileageUpdate.setOnClickListener(v -> {
                String miles = safe(enterMileageInput);
                if (TextUtils.isEmpty(miles)) {
                    if (enterMileageInput != null) {
                        enterMileageInput.setError("Mileage is required");
                        enterMileageInput.requestFocus();
                    }
                    return;
                }

                // Persist into model so server gets it
                // NOTE: we still allow local edits before intakeLoaded, but CONFIRM won't allow PUT until loaded.
                if (intakeModel == null) intakeModel = new VehicleTaskIntake();
                if (intakeModel.mileage == null) intakeModel.mileage = new VehicleTaskIntake.Mileage();
                try { intakeModel.mileage.odometer = Integer.parseInt(miles); } catch (NumberFormatException ignored) {}

                hideKeyboard();
                Toast.makeText(this, "Mileage saved: " + miles, Toast.LENGTH_SHORT).show();
                setStatusIcon(mileageIcon, true);
                mileageDone = true;
                setMileageExpanded(false);
                refreshConfirmEnabled();
            });
        }

        // VIDEO section
        if (videoHeader != null) videoHeader.setOnClickListener(v -> toggleVideoPanel());

        if (btnVideoRecord != null) {
            btnVideoRecord.setText("RECORD");
            btnVideoRecord.setOnClickListener(v -> {
                setVideoExpanded(true);
                hideKeyboard();

                Intent intent = new Intent(CheckInDetailsActivity.this, IntakeVideoActivity.class);
                intent.putExtra(IntakeVideoActivity.EXTRA_CONSIGNMENT_ID, consignmentIdStr());
                intent.putExtra(IntakeVideoActivity.EXTRA_ENABLE_AUDIO, true);

                recordVideoLauncher.launch(intent);
            });
        }

        if (btnVideoAccept != null) {
            btnVideoAccept.setText("RECORD VIDEO");
            btnVideoAccept.setEnabled(true);

            btnVideoAccept.setOnClickListener(v -> {
                // If already accepted, do nothing
                if (intakeModel != null && intakeModel.video != null
                        && !TextUtils.isEmpty(safeStr(intakeModel.video.videoUrl))) {
                    return;
                }

                setVideoExpanded(true);
                hideKeyboard();

                Intent intent = new Intent(CheckInDetailsActivity.this, IntakeVideoActivity.class);
                intent.putExtra(IntakeVideoActivity.EXTRA_CONSIGNMENT_ID, consignmentIdStr());
                intent.putExtra(IntakeVideoActivity.EXTRA_ENABLE_AUDIO, true);
                recordVideoLauncher.launch(intent);
            });
        }

        // DESCRIPTION
        if (descHeader != null) descHeader.setOnClickListener(v -> toggleDescPanel());

        if (btnDescCorrect != null) {
            btnDescCorrect.setOnClickListener(v -> {
                VehicleTaskIntake.Description d = descModel();
                d.isCorrect = true;
                d.isIncorrectMileage  = false;
                d.isInCorrectVin      = false;
                d.isIncorrectSpelling = false;
                d.isIncorrectDetails  = false;

                setStatusIcon(descIcon, true);
                descDone = true;
                setDescExpanded(false);
                Toast.makeText(this, "Description confirmed correct.", Toast.LENGTH_SHORT).show();
                refreshConfirmEnabled();
            });
        }

        if (btnDescIncorrect != null) {
            btnDescIncorrect.setOnClickListener(v -> {
                setStatusIcon(descIcon, false);
                descDone = false;
                setDescExpanded(true);
                if (descIncorrectGroup != null) descIncorrectGroup.setVisibility(View.VISIBLE);
                refreshConfirmEnabled();
            });
        }

        if (btnDescUpdate != null) {
            btnDescUpdate.setOnClickListener(v -> {
                boolean incMileage = cbIncorrectMileage != null && cbIncorrectMileage.isChecked();
                boolean incVin     = cbIncorrectVin     != null && cbIncorrectVin.isChecked();
                boolean incDetails = cbIncorrectDetails != null && cbIncorrectDetails.isChecked();
                boolean incSpell   = cbSpellingErrors   != null && cbSpellingErrors.isChecked();

                VehicleTaskIntake.Description d = descModel();
                d.isCorrect = false;
                d.isIncorrectMileage  = incMileage;
                d.isInCorrectVin      = incVin;
                d.isIncorrectDetails  = incDetails;
                d.isIncorrectSpelling = incSpell;

                setStatusIcon(descIcon, true);
                descDone = true;
                if (descIncorrectGroup != null) descIncorrectGroup.setVisibility(View.GONE);
                clearDescIncorrectChecks();
                setDescExpanded(false);
                Toast.makeText(this, "Description update submitted.", Toast.LENGTH_SHORT).show();
                refreshConfirmEnabled();
            });
        }

        // QUALITY
        if (qualityHeader != null) {
            qualityHeader.setOnClickListener(v -> toggleQualityPanel());
        } else if (qualityPanel != null) {
            qualityPanel.setOnClickListener(v -> toggleQualityPanel());
        }

        if (btnQualityNoConcerns != null) {
            btnQualityNoConcerns.setOnClickListener(v -> {
                qualityHasConcerns = false;

                if (intakeModel == null) intakeModel = new VehicleTaskIntake();
                if (intakeModel.quality == null) intakeModel.quality = new VehicleTaskIntake.Quality();
                intakeModel.quality.isConcerns = false;
                intakeModel.quality.isExteriorDamage = false;
                intakeModel.quality.isInteriorDamage = false;
                intakeModel.quality.isTiresWheels = false;
                intakeModel.quality.isServiceLights = false;
                intakeModel.quality.isMechanical = false;

                setStatusIcon(qualityIcon, true);
                qualityDone = true;
                setQualityExpanded(false);
                if (qualityDetailsGroup != null) qualityDetailsGroup.setVisibility(View.GONE);
                clearQualityChecks();
                refreshConfirmEnabled();
            });
        }

        if (btnQualityConcerns != null) {
            btnQualityConcerns.setOnClickListener(v -> {
                qualityHasConcerns = true;
                setStatusIcon(qualityIcon, false);
                qualityDone = false;
                setQualityExpanded(true);
                if (qualityDetailsGroup != null) {
                    qualityDetailsGroup.setVisibility(View.VISIBLE);
                }
                refreshConfirmEnabled();
            });
        }

        if (btnQualityUpdate != null) {
            btnQualityUpdate.setOnClickListener(v -> {
                boolean any =
                        (cbExteriorDamage != null && cbExteriorDamage.isChecked()) ||
                                (cbInteriorDamage != null && cbInteriorDamage.isChecked()) ||
                                (cbTiresWheels  != null && cbTiresWheels.isChecked()) ||
                                (cbMechanical   != null && cbMechanical.isChecked()) ||
                                (cbServiceLights!= null && cbServiceLights.isChecked());

                qualityHasConcerns = true;

                if (intakeModel == null) intakeModel = new VehicleTaskIntake();
                if (intakeModel.quality == null) intakeModel.quality = new VehicleTaskIntake.Quality();
                intakeModel.quality.isConcerns       = true;
                intakeModel.quality.isExteriorDamage = cbExteriorDamage != null && cbExteriorDamage.isChecked();
                intakeModel.quality.isInteriorDamage = cbInteriorDamage != null && cbInteriorDamage.isChecked();
                intakeModel.quality.isTiresWheels    = cbTiresWheels  != null && cbTiresWheels.isChecked();
                intakeModel.quality.isServiceLights  = cbServiceLights!= null && cbServiceLights.isChecked();
                intakeModel.quality.isMechanical     = cbMechanical   != null && cbMechanical.isChecked();

                setStatusIcon(qualityIcon, true);
                qualityDone = true;
                setQualityExpanded(false);
                if (qualityDetailsGroup != null) qualityDetailsGroup.setVisibility(View.GONE);

                Toast.makeText(this, any ? "Quality concerns recorded." : "No specific concerns checked.", Toast.LENGTH_SHORT).show();
                refreshConfirmEnabled();
            });
        }

        // CONFIRM -> PUT save
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                hideKeyboard();

                // ✅ NEW: make the failure mode obvious (GET must have completed successfully)
                if (!intakeLoaded || intakeModel == null) {
                    Toast.makeText(this, "Intake record not loaded yet. Try again.", Toast.LENGTH_LONG).show();
                    refreshConfirmEnabled();
                    return;
                }

                if (!isConfirmReady()) {
                    Toast.makeText(this, "Complete Mileage, Description, Quality, and Intake Video first.", Toast.LENGTH_LONG).show();
                    refreshConfirmEnabled();
                    return;
                }

                if (TextUtils.isEmpty(opportunityId)) {
                    Toast.makeText(this, "Missing opportunityId", Toast.LENGTH_LONG).show();
                    return;
                }

                VehicleTaskIntake body = buildIntakeFromUi();

                btnConfirm.setEnabled(false);
                btnConfirm.setAlpha(0.5f);

                Toast.makeText(this, "Saving intake...", Toast.LENGTH_SHORT).show();

                driverTaskRepo.saveIntake(opportunityId, body, new DriverTaskRepository.SaveCallback() {
                    @Override public void onSaved() {
                        Toast.makeText(CheckInDetailsActivity.this, "INTAKE saved", Toast.LENGTH_LONG).show();
                        Intent back = new Intent(CheckInDetailsActivity.this, LookupActivity.class);
                        back.putExtra("mode", mode);
                        back.putExtra("eventName", eventName);
                        back.putExtra("eventId", eventId);
                        back.putExtra("driver", driver);
                        back.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(back);
                        finish();
                    }
                    @Override public void onError(Throwable t) {
                        Toast.makeText(CheckInDetailsActivity.this, "Save failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        refreshConfirmEnabled();
                    }
                    @Override public void onHttpError(int code, String message) {
                        Toast.makeText(CheckInDetailsActivity.this, "Save HTTP " + code + ": " + message, Toast.LENGTH_LONG).show();
                        refreshConfirmEnabled();
                    }
                });
            });
        }

        // Fire API load once UI is ready
        driverTaskRepo = new DriverTaskRepository();
        fetchIntakeAndBind();
    }

    // ---------- Confirm gating ----------

    private boolean isConfirmReady() {
        // ✅ NEW: require intakeLoaded so we don't PUT a blank model and wipe other fields (e.g., Transport)
        return intakeLoaded && mileageDone && descDone && qualityDone && videoDone;
    }

    private void setConfirmEnabled(boolean enabled) {
        if (btnConfirm == null) return;
        btnConfirm.setEnabled(enabled);
        btnConfirm.setAlpha(enabled ? 1f : 0.5f);
    }

    private void refreshConfirmEnabled() {
        setConfirmEnabled(isConfirmReady());
    }

    // ---------- Networking bind/load ----------

    private void fetchIntakeAndBind() {
        if (TextUtils.isEmpty(opportunityId)) {
            Toast.makeText(this, "Missing opportunityId", Toast.LENGTH_LONG).show();
            return;
        }

        // ✅ NEW: reset loaded state until GET succeeds
        intakeLoaded = false;
        refreshConfirmEnabled();

        setPanelsEnabled(false);
        driverTaskRepo.fetchIntake(opportunityId, new DriverTaskRepository.IntakeCallback() {
            @Override public void onSuccess(VehicleTaskIntake it) {
                setPanelsEnabled(true);

                if (it == null) {
                    // ✅ IMPORTANT: do NOT create a blank object and allow PUT
                    intakeModel = new VehicleTaskIntake();
                    intakeLoaded = false;
                    Toast.makeText(CheckInDetailsActivity.this, "No intake data found.", Toast.LENGTH_SHORT).show();
                    refreshConfirmEnabled();
                    return;
                }

                intakeModel = it;
                intakeLoaded = true;

                bindIntakeToUi(it);
                refreshConfirmEnabled();
            }

            @Override public void onError(Throwable t) {
                setPanelsEnabled(true);
                intakeLoaded = false;
                Toast.makeText(CheckInDetailsActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                refreshConfirmEnabled();
            }

            @Override public void onHttpError(int code, String message) {
                setPanelsEnabled(true);
                intakeLoaded = false;
                Toast.makeText(CheckInDetailsActivity.this, "HTTP " + code + ": " + message, Toast.LENGTH_LONG).show();
                refreshConfirmEnabled();
            }
        });
    }

    // Keep the persisted Description state in your working model
    private VehicleTaskIntake.Description descModel() {
        if (intakeModel == null) intakeModel = new VehicleTaskIntake();
        if (intakeModel.description == null) intakeModel.description = new VehicleTaskIntake.Description();
        return intakeModel.description;
    }

    private void bindIntakeToUi(VehicleTaskIntake it) {
        // Mileage
        if (it.mileage != null) {
            Integer odo = it.mileage.odometer;
            if (enterMileageInput != null && odo != null) {
                enterMileageInput.setText(String.valueOf(odo));
            }
            if (odo != null) {
                setStatusIcon(mileageIcon, true);
                mileageDone = true;
            }
        } else {
            setStatusIcon(mileageIcon, false);
            mileageDone = false;
        }

        // Description
        if (it.description != null) {
            boolean anyReasons = anyTrue(
                    it.description.isIncorrectMileage,
                    it.description.isInCorrectVin,
                    it.description.isIncorrectSpelling,
                    it.description.isIncorrectDetails
            );

            if (Boolean.TRUE.equals(it.description.isCorrect)) {
                setStatusIcon(descIcon, true);
                descDone = true;
                setDescExpanded(false);
                if (descIncorrectGroup != null) descIncorrectGroup.setVisibility(View.GONE);
                clearDescIncorrectChecks();
            } else if (anyReasons) {
                setStatusIcon(descIcon, true);
                descDone = true;
                setDescExpanded(false);
                if (descIncorrectGroup != null) descIncorrectGroup.setVisibility(View.GONE);

                if (cbIncorrectMileage != null) cbIncorrectMileage.setChecked(Boolean.TRUE.equals(it.description.isIncorrectMileage));
                if (cbIncorrectVin != null)     cbIncorrectVin.setChecked(Boolean.TRUE.equals(it.description.isInCorrectVin));
                if (cbSpellingErrors != null)   cbSpellingErrors.setChecked(Boolean.TRUE.equals(it.description.isIncorrectSpelling));
                if (cbIncorrectDetails != null) cbIncorrectDetails.setChecked(Boolean.TRUE.equals(it.description.isIncorrectDetails));
            } else {
                setStatusIcon(descIcon, false);
                descDone = false;
                if (descIncorrectGroup != null) descIncorrectGroup.setVisibility(View.VISIBLE);
                setDescExpanded(true);
            }
        } else {
            setStatusIcon(descIcon, false);
            descDone = false;
        }

        // Quality
        if (it.quality != null) {
            qualityHasConcerns = it.quality.isConcerns;

            if (Boolean.FALSE.equals(it.quality.isConcerns)) {
                setStatusIcon(qualityIcon, true);
                qualityDone = true;
                setQualityExpanded(false);
                if (qualityDetailsGroup != null) qualityDetailsGroup.setVisibility(View.GONE);
                clearQualityChecks();
            } else if (Boolean.TRUE.equals(it.quality.isConcerns)) {
                // Consider quality "done" if server has a state for it
                setStatusIcon(qualityIcon, true);
                qualityDone = true;
                setQualityExpanded(false);
                if (qualityDetailsGroup != null) qualityDetailsGroup.setVisibility(View.GONE);

                if (cbExteriorDamage != null) cbExteriorDamage.setChecked(Boolean.TRUE.equals(it.quality.isExteriorDamage));
                if (cbInteriorDamage != null) cbInteriorDamage.setChecked(Boolean.TRUE.equals(it.quality.isInteriorDamage));
                if (cbTiresWheels   != null) cbTiresWheels.setChecked(Boolean.TRUE.equals(it.quality.isTiresWheels));
                if (cbMechanical    != null) cbMechanical.setChecked(Boolean.TRUE.equals(it.quality.isMechanical));
                if (cbServiceLights != null) cbServiceLights.setChecked(Boolean.TRUE.equals(it.quality.isServiceLights));
            } else {
                setStatusIcon(qualityIcon, false);
                qualityDone = false;
            }
        } else {
            setStatusIcon(qualityIcon, false);
            qualityDone = false;
        }

        // Video (server state)
        String acceptedUrl = (it.video != null) ? safeStr(it.video.videoUrl) : "";
        if (!TextUtils.isEmpty(acceptedUrl)) {
            setStatusIcon(videoIcon, true);
            videoDone = true;

            if (btnVideoAccept != null) {
                btnVideoAccept.setEnabled(false);
                btnVideoAccept.setAlpha(0.5f);
                btnVideoAccept.setText("ACCEPTED");
            }
            if (btnVideoRecord != null) {
                btnVideoRecord.setEnabled(false);
                btnVideoRecord.setAlpha(0.5f);
            }
        } else {
            setStatusIcon(videoIcon, false);
            videoDone = false;

            if (btnVideoAccept != null) {
                btnVideoAccept.setEnabled(true);
                btnVideoAccept.setAlpha(1f);
                btnVideoAccept.setText("RECORD VIDEO");
            }
            if (btnVideoRecord != null) {
                btnVideoRecord.setEnabled(true);
                btnVideoRecord.setAlpha(1f);
                btnVideoRecord.setText("RECORD");
            }
        }

        refreshConfirmEnabled();
    }

    // ---------- Build body for PUT ----------

    private VehicleTaskIntake buildIntakeFromUi() {
        // ✅ IMPORTANT: always start from the latest GET model to preserve fields TransportActivity owns
        VehicleTaskIntake body = intakeModel;
        body.opportunityId = opportunityId;

        // ---------------- MILEAGE ----------------
        if (body.mileage == null) body.mileage = new VehicleTaskIntake.Mileage();
        String milesTxt = safe(enterMileageInput);
        if (!TextUtils.isEmpty(milesTxt)) {
            try { body.mileage.odometer = Integer.parseInt(milesTxt); }
            catch (NumberFormatException ignore) {}
        }
        // Preserve photoUrl if already captured into intakeModel.mileage.photoUrl

        // ---------------- DESCRIPTION ----------------
        if (body.description == null) body.description = new VehicleTaskIntake.Description();
        if (intakeModel != null && intakeModel.description != null) {
            VehicleTaskIntake.Description src = intakeModel.description;
            body.description.isCorrect           = Boolean.TRUE.equals(src.isCorrect);
            body.description.isIncorrectMileage  = Boolean.TRUE.equals(src.isIncorrectMileage);
            body.description.isInCorrectVin      = Boolean.TRUE.equals(src.isInCorrectVin);
            body.description.isIncorrectSpelling = Boolean.TRUE.equals(src.isIncorrectSpelling);
            body.description.isIncorrectDetails  = Boolean.TRUE.equals(src.isIncorrectDetails);
        }

        // ---------------- QUALITY ----------------
        if (body.quality == null) body.quality = new VehicleTaskIntake.Quality();
        if (Boolean.TRUE.equals(qualityHasConcerns)) {
            body.quality.isConcerns       = true;
            body.quality.isExteriorDamage = cbExteriorDamage != null && cbExteriorDamage.isChecked();
            body.quality.isInteriorDamage = cbInteriorDamage != null && cbInteriorDamage.isChecked();
            body.quality.isTiresWheels    = cbTiresWheels  != null && cbTiresWheels.isChecked();
            body.quality.isServiceLights  = cbServiceLights!= null && cbServiceLights.isChecked();
            body.quality.isMechanical     = cbMechanical   != null && cbMechanical.isChecked();
        } else {
            body.quality.isConcerns       = false;
            body.quality.isExteriorDamage = false;
            body.quality.isInteriorDamage = false;
            body.quality.isTiresWheels    = false;
            body.quality.isServiceLights  = false;
            body.quality.isMechanical     = false;
        }

        // ---------------- VIDEO ----------------
        if (body.video == null) body.video = new VehicleTaskIntake.VideoInfo();
        if (TextUtils.isEmpty(body.video.videoUrl)) {
            String capturedVideoUrl = (intakeModel != null && intakeModel.video != null)
                    ? intakeModel.video.videoUrl : null;
            body.video.videoUrl = TextUtils.isEmpty(capturedVideoUrl) ? "" : capturedVideoUrl;
        }

        // ---------------- CHECK-IN BY (required for API) ----------------
        String checkInBy = resolveDriverName();
        body.checkInBy = (checkInBy == null) ? "" : checkInBy;

        return body;
    }

    // ---------- UI helpers ----------

    private void loadThumbIntoHeader() {
        if (ivVehicleThumb == null) return;
        if (thumbUrl != null && !thumbUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(thumbUrl)
                    .transform(
                            new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                            new com.bumptech.glide.load.resource.bitmap.RoundedCorners(16)
                    )
                    .placeholder(android.R.color.darker_gray)
                    .error(android.R.color.darker_gray)
                    .into(ivVehicleThumb);
        } else {
            ivVehicleThumb.setImageResource(android.R.color.darker_gray);
        }
    }

    private void toggleMileagePanel() { setMileageExpanded(!mileageExpanded); }
    private void setMileageExpanded(boolean expanded) {
        mileageExpanded = expanded;
        if (mileageGroup != null) mileageGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private void toggleVideoPanel() { setVideoExpanded(!videoExpanded); }
    private void setVideoExpanded(boolean expanded) {
        videoExpanded = expanded;
        if (videoGroup != null) videoGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private void toggleDescPanel() { setDescExpanded(!descExpanded); }
    private void setDescExpanded(boolean expanded) {
        descExpanded = expanded;
        if (descGroup != null) descGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private void toggleQualityPanel() { setQualityExpanded(!qualityExpanded); }
    private void setQualityExpanded(boolean expanded) {
        qualityExpanded = expanded;
        if (qualityGroup != null) qualityGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (!expanded && qualityDetailsGroup != null) {
            qualityDetailsGroup.setVisibility(View.GONE);
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

    private void clearDescIncorrectChecks() {
        if (cbIncorrectMileage != null)  cbIncorrectMileage.setChecked(false);
        if (cbIncorrectVin != null)      cbIncorrectVin.setChecked(false);
        if (cbIncorrectDetails != null)  cbIncorrectDetails.setChecked(false);
        if (cbSpellingErrors != null)    cbSpellingErrors.setChecked(false);
    }

    private void clearQualityChecks() {
        if (cbExteriorDamage != null) cbExteriorDamage.setChecked(false);
        if (cbInteriorDamage != null) cbInteriorDamage.setChecked(false);
        if (cbTiresWheels  != null) cbTiresWheels.setChecked(false);
        if (cbMechanical   != null) cbMechanical.setChecked(false);
        if (cbServiceLights!= null) cbServiceLights.setChecked(false);
    }

    // ---------- Permissions / Camera ----------

    private void ensureCameraForPhoto(String label) {
        // mileage only
        pendingPhotoLabel = "mileage";

        boolean cameraOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (!cameraOk) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean readImgOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
            if (!readImgOk) {
                requestReadImagesPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
                return;
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            boolean writeOk = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!writeOk) {
                requestWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
        }

        openCameraPhoto("mileage");
    }

    // Single canonical version of openCameraPhoto (mileage only)
    private void openCameraPhoto(String label) {
        pendingPhotoLabel = "mileage";

        Uri dest = createPhotoUriForExternalCapture("mileage");
        if (dest == null) {
            Toast.makeText(this, "Failed to create photo destination.", Toast.LENGTH_SHORT).show();
            pendingPhotoLabel = null;
            return;
        }
        pendingPhotoUri = dest;

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, dest);
        intent.setClipData(android.content.ClipData.newRawUri("image", dest));
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        grantOutputUriToCamera(intent, dest);

        if (intent.resolveActivity(getPackageManager()) != null) {
            takePictureLauncher.launch(intent);
        } else {
            Toast.makeText(this, "No camera app available.", Toast.LENGTH_SHORT).show();
            pendingPhotoLabel = null;
            pendingPhotoUri = null;
        }
    }

    private void grantOutputUriToCamera(Intent intent, Uri uri) {
        if (uri == null) return;
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            grantUriPermission(ri.activityInfo.packageName, uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    // ---------- Media helpers ----------

    private boolean awaitNonZeroSize(Uri uri) {
        for (int i = 0; i < 8; i++) { // ~2 seconds
            if (hasNonZeroSize(uri)) return true;
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    private boolean hasNonZeroSize(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.SIZE}, null, null, null);
            if (c != null && c.moveToFirst()) {
                return c.getLong(0) > 0;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return false;
    }

    @androidx.annotation.Nullable
    private Uri preferResultUri(@androidx.annotation.Nullable Intent data,
                                @androidx.annotation.Nullable Uri fallback) {
        return (data != null && data.getData() != null) ? data.getData() : fallback;
    }

    private boolean copyUri(Uri from, Uri to) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
                getContentResolver().update(to, cv, null, null);
            }

            try (java.io.InputStream in = getContentResolver().openInputStream(from);
                 java.io.OutputStream out = getContentResolver().openOutputStream(to, "w")) {
                if (in == null || out == null) return false;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(to, done, null, null);
            }

            return hasNonZeroSize(to);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean saveBitmapToUriAsJpeg(android.graphics.Bitmap bmp, Uri dest, int quality) {
        try (java.io.OutputStream out = getContentResolver().openOutputStream(dest, "w")) {
            if (out == null) return false;
            boolean ok = bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out);
            out.flush();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    private Uri createIntakePhotoUri(String label) {
        // mileage only
        final String fileName = "mileage_intake.jpg";
        final String relPath = Environment.DIRECTORY_PICTURES + "/GT6/" + consignmentIdStr();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        } else {
            File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File dir = new File(pictures, "GT6/" + consignmentIdStr());
            if (!dir.exists() && !dir.mkdirs()) return null;
            File f = new File(dir, fileName);
            try {
                if (!f.exists()) f.createNewFile();
                return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private Uri createPhotoUriForExternalCapture(String label) {
        // mileage only
        final String fileName = "mileage_intake.jpg";
        final String relPath  = Environment.DIRECTORY_PICTURES + "/GT6/" + consignmentIdStr();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath);
            return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        } else {
            File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File dir = new File(pictures, "GT6/" + consignmentIdStr());
            if (!dir.exists() && !dir.mkdirs()) return null;
            File f = new File(dir, fileName);
            try {
                if (!f.exists()) f.createNewFile();
                return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private void finalizePhotoAndBind(Uri dest, String label) {
        if (intakeModel == null) intakeModel = new VehicleTaskIntake();

        // mileage only
        String fileName = "mileage_intake.jpg";
        if (intakeModel.mileage == null) intakeModel.mileage = new VehicleTaskIntake.Mileage();
        intakeModel.mileage.photoUrl = mediaUrl(fileName);

        verifyDestAndReport(dest, "Image");
    }

    // ---------- Video helpers ----------

    // ✅ Auto-accept intake video so user can’t lose it by forgetting to tap Accept
    private void acceptIntakeVideoIfPresent() {
        if (lastCapturedVideoUri == null) return;

        if (intakeModel == null) intakeModel = new VehicleTaskIntake();
        if (intakeModel.video == null) intakeModel.video = new VehicleTaskIntake.VideoInfo();

        // Store the URL your API expects (your existing naming convention)
        intakeModel.video.videoUrl = compressedVideoUrl("intake.mp4");

        videoDone = true;
        setStatusIcon(videoIcon, true);

        if (btnVideoAccept != null) {
            btnVideoAccept.setEnabled(false);
            btnVideoAccept.setAlpha(0.5f);
            btnVideoAccept.setText("ACCEPTED");
        }
        if (btnVideoRecord != null) {
            btnVideoRecord.setEnabled(false);
            btnVideoRecord.setAlpha(0.5f);
        }

        com.example.gt6driver.sync.GT6MediaSync.enqueueImmediate(this);
        refreshConfirmEnabled();
    }

    // Compressed Video Helper
    private String compressedVideoUrl(String originalFileName) {
        int dot = originalFileName.lastIndexOf('.');
        String name = (dot > 0) ? originalFileName.substring(0, dot) : originalFileName;
        String ext  = (dot > 0) ? originalFileName.substring(dot) : "";
        return COMPRESSED_BASE + consignmentIdStr() + "/" + name + "_c" + ext;
    }

    // ---------- Sidecar + misc helpers ----------

    private String isoUtcNow() {
        long now = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return java.time.Instant.ofEpochMilli(now).toString();
        }
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date(now));
    }

    private boolean writeSidecarJsonToDownload(
            String baseNameNoExt,
            String createdAtUtc,
            String consignmentId,
            String tablet,
            String driver,
            String lot
    ) {
        final String sidecarName = baseNameNoExt + ".meta.json";

        try {
            final String relPath = Environment.DIRECTORY_DOWNLOADS + "/GT6/" + consignmentId + "/";

            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;

            // Delete existing to avoid "(1)"
            try {
                String sel = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                        MediaStore.MediaColumns.RELATIVE_PATH + "=?";
                getContentResolver().delete(collection, sel, new String[]{ sidecarName, relPath });
            } catch (Exception e) {
                Log.w(TAG, "Sidecar(Download): delete existing failed", e);
            }

            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, sidecarName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            }

            Uri jsonUri = getContentResolver().insert(collection, cv);
            if (jsonUri == null) return false;

            String json =
                    "{"
                            + "\"createdAt\":\"" + createdAtUtc + "\","
                            + "\"consignmentId\":\"" + consignmentId + "\","
                            + "\"tablet\":\"" + tablet + "\","
                            + "\"driver\":\"" + driver + "\","
                            + "\"lot\":\"" + lot + "\""
                            + "}";

            try (java.io.OutputStream out = getContentResolver().openOutputStream(jsonUri, "w")) {
                if (out == null) {
                    try { getContentResolver().delete(jsonUri, null, null); } catch (Exception ignored) {}
                    return false;
                }
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(jsonUri, done, null, null);
            }

            long size = 0;
            try (Cursor c = getContentResolver().query(
                    jsonUri, new String[]{ MediaStore.MediaColumns.SIZE },
                    null, null, null
            )) {
                if (c != null && c.moveToFirst()) size = c.getLong(0);
            }

            return size > 0;

        } catch (Exception e) {
            Log.e(TAG, "Sidecar(Download): write failed", e);
            return false;
        }
    }

    private void verifyDestAndReport(Uri dest, String mediaKind) {
        Cursor c = null;
        String name = "unknown";
        String rel = "";
        try {
            String[] cols = {
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.SIZE
            };
            c = getContentResolver().query(dest, cols, null, null, null);
            if (c != null && c.moveToFirst()) {
                name = safeStr(c.getString(0));
                rel  = safeStr(c.getString(1));
                long sz = c.getLong(2);
                Log.i("GT6-COPY", mediaKind + " saved → RELATIVE_PATH=" + rel + " name=" + name + " size=" + sz);
            }
        } catch (Exception e) {
            Log.e("GT6-COPY", "Query dest failed: " + dest, e);
        } finally {
            if (c != null) c.close();
        }
        String where = (rel.isEmpty() ? "(no RELATIVE_PATH)" : (rel + name));
        Toast.makeText(this, mediaKind + " saved: " + where, Toast.LENGTH_LONG).show();
    }

    private boolean anyTrue(Boolean... arr) {
        if (arr == null) return false;
        for (Boolean b : arr) if (Boolean.TRUE.equals(b)) return true;
        return false;
    }

    private String resolveDriverName() {
        String fromIntent = driver;
        if (fromIntent != null && !fromIntent.trim().isEmpty()) return fromIntent.trim();

        try {
            String fromSession = com.example.gt6driver.session.CurrentSelection.get().getDriverName();
            if (fromSession != null && !fromSession.trim().isEmpty()) return fromSession.trim();
        } catch (Throwable ignored) {}

        return "";
    }

    private String consignmentIdStr() {
        if (vehicle != null && vehicle.consignmentid != null) {
            return String.valueOf(vehicle.consignmentid);
        }
        return "unknown";
    }

    private String mediaUrl(String fileName) {
        return BLOB_BASE + consignmentIdStr() + "/" + fileName;
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
    private static String firstNonEmpty(String a, String b) { return isEmpty(a) ? (b == null ? "" : b) : a; }
    private String safeStr(String s) { return s == null ? "" : s; }

    private String safe(TextInputEditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view == null) view = new View(this);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void setPanelsEnabled(boolean enabled) {
        float alpha = enabled ? 1f : 0.6f;

        if (mileagePanel  != null)  mileagePanel.setAlpha(alpha);
        if (videoPanel    != null)  videoPanel.setAlpha(alpha);
        if (descPanel     != null)  descPanel.setAlpha(alpha);
        if (qualityPanel  != null)  qualityPanel.setAlpha(alpha);

        setGroupEnabled(mileagePanel, enabled);
        setGroupEnabled(videoPanel, enabled);
        setGroupEnabled(descPanel, enabled);
        setGroupEnabled(qualityPanel, enabled);
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

    @androidx.annotation.Nullable
    private Uri findLatestCapturedImage(long notBeforeMillis) {
        Cursor c = null;
        try {
            String[] cols = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED };
            String order = MediaStore.Images.Media.DATE_ADDED + " DESC";
            c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, null, null, order);
            if (c != null && c.moveToFirst()) {
                long dateAddedSec = c.getLong(1);
                long whenMs = dateAddedSec * 1000L;
                if (whenMs >= notBeforeMillis) {
                    long id = c.getLong(0);
                    return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }
}
