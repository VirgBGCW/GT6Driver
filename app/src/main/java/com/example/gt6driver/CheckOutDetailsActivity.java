
// app/src/main/java/com/example/gt6driver/CheckOutDetailsActivity.java
package com.example.gt6driver;

import android.Manifest;
import android.content.ClipData;
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
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import com.google.gson.Gson;
import retrofit2.Retrofit;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.gt6driver.model.ConsignmentKeyPayload;
import com.example.gt6driver.model.VehicleDetail;
import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.DriverTaskApi;
import com.example.gt6driver.net.DriverTaskRepository;
import com.example.gt6driver.net.ReleasePayload;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

// Common Nav extras
import com.example.gt6driver.Nav;

import java.io.File;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CheckOutDetailsActivity extends AppCompatActivity {

    // ===== Consistent with CheckIn =====
    //
    private static final String TAG = "GT6Release";
    private static final String BLOB_BASE = "https://stgt6driverappprod.blob.core.windows.net/";
    private static final String EXTRA_VEHICLE        = "vehicle";
    private static final String EXTRA_OPPORTUNITY_ID = "opportunityId";

    // Model + repo
    private VehicleDetail vehicle;
    private DriverTaskRepository repo;
    private ReleasePayload releaseModel; // keep a working model like CheckIn’s intakeModel

    // Header
    private TextView panelLot, panelDesc, panelVin;
    private ImageView ivVehicleThumb;

    // VERIFY VIN
    private MaterialCardView verifyVinPanel;
    private View verifyActions;
    private ImageView verifyIcon;
    private MaterialButton btnVinMatch, btnVinNoMatch;
    private View noMatchGroup;
    private TextView verifyVinValue;
    private TextView vinWarningMessage;
    private boolean vinExpanded = false;
    private boolean vinNoMatchMode = false;

    // MILEAGE
    private MaterialCardView mileagePanel;
    private View mileageGroup;
    private ImageView mileageIcon;
    private TextInputLayout enterMileageLayout;
    private TextInputEditText enterMileageInput;
    private MaterialButton btnMileageUpdate;
    private boolean mileageExpanded = false;

    // KEY CHECK
    private MaterialCardView keyPanel;
    private View keyGroup;
    private ImageView keyIcon;
    private MaterialCheckBox cbFobs, cbNoKey;
    private TextInputLayout enterKeyCountLayout;
    private TextInputEditText enterKeyCountInput;
    private MaterialButton btnKeyUpdate;
    private boolean keyExpanded = false;
    private boolean isChangingKeyState = false;

    // OWNER VERIFICATION
    private MaterialCardView ownerPanel;
    private View ownerGroup;
    private ImageView ownerIcon, ownerLicenseCamera, ownerPhoto;
    private MaterialCheckBox cbOwner, cbReliable, cbTFX, cbOther;
    private TextInputLayout enterTowLayout;
    private TextInputEditText enterTowInput;
    private MaterialButton btnOwnerUpdate;
    private boolean ownerExpanded = false;
    private boolean ownerDone = false;
    private boolean isChangingOwnerState = false;

    // GATE RELEASE
    private MaterialCardView gatePanel;
    private View gateGroup;
    private ImageView gateIcon;
    private MaterialButton btnGateYes, btnGateNo;
    private TextView gateWarning;
    private boolean gateExpanded = false;
    private boolean gateRelease = false;

    // RELEASE VIDEO
    private MaterialCardView videoPanel;
    private View videoGroup;
    private ImageView videoIcon;
    private ImageView videoPromptIcon;
    private MaterialButton btnVideoAccept;
    private boolean videoExpanded = false;

    // Camera & permissions + full-res capture state
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<String> requestWritePermissionLauncher; // for API <= 28 (video)
    private ActivityResultLauncher<Intent> takePictureLauncher;
    private ActivityResultLauncher<Intent> recordVideoLauncher;
    private boolean pendingVideoCapture = false;

    private String pendingPhotoLabel = null; // "vin"|"mileage"|"keycheck"|"owner"
    private Uri pendingPhotoUri = null;
    private Uri pendingVideoUri = null;
    private Uri lastCapturedVideoUri = null;

    // Confirm
    private MaterialButton btnConfirm;

    // Context from previous screen
    private String lot, description, eventName, driver, mode, vin, thumbUrl;
    private int eventId;
    private String opportunityId;

    // Completion flags
    private boolean vinDone = false;
    private boolean mileageDone = false;
    private boolean keyDone = false;
    private boolean videoDone = false;

    private ImageButton btnMileageCamera, btnKeyCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_out_details);

        // repo
        repo = new DriverTaskRepository();
        releaseModel = new ReleasePayload();

        // Bind header views
        panelLot = findViewById(R.id.panelLot);
        panelDesc = findViewById(R.id.panelDesc);
        panelVin  = findViewById(R.id.panelVin);
        ivVehicleThumb = findViewById(R.id.ivCheckOutThumb);

        // VIN
        verifyVinPanel   = findViewById(R.id.verifyVinPanel);
        verifyActions    = findViewById(R.id.verifyActions);
        verifyIcon       = findViewById(R.id.verifyIcon);
        btnVinMatch      = findViewById(R.id.btnVinMatch);
        btnVinNoMatch    = findViewById(R.id.btnVinNoMatch);
        noMatchGroup     = findViewById(R.id.noMatchGroup);
        verifyVinValue   = findViewById(R.id.verifyVinValue);
        vinWarningMessage= findViewById(R.id.vinWarningMessage);

        // Mileage
        mileagePanel       = findViewById(R.id.mileagePanel);
        mileageGroup       = findViewById(R.id.mileageGroup);
        mileageIcon        = findViewById(R.id.mileageIcon);
        enterMileageLayout = findViewById(R.id.enterMileageLayout);
        enterMileageInput  = findViewById(R.id.enterMileageInput);
        btnMileageUpdate   = findViewById(R.id.btnMileageUpdate);
        btnMileageCamera   = findViewById(R.id.btnMileageCamera);

        // Key
        keyPanel            = findViewById(R.id.keyPanel);
        keyGroup            = findViewById(R.id.keyGroup);
        keyIcon             = findViewById(R.id.keyIcon);
        cbFobs              = findViewById(R.id.cbFobs);
        cbNoKey             = findViewById(R.id.cbNoKey);
        enterKeyCountLayout = findViewById(R.id.enterKeyCountLayout);
        enterKeyCountInput  = findViewById(R.id.enterKeyCountInput);
        btnKeyUpdate        = findViewById(R.id.btnKeyUpdate);
        btnKeyCamera        = findViewById(R.id.btnKeyCamera);

        // Owner
        ownerPanel         = findViewById(R.id.ownerPanel);
        ownerGroup         = findViewById(R.id.ownerGroup);
        ownerIcon          = findViewById(R.id.ownerIcon);
        ownerPhoto         = findViewById(R.id.ownerPhoto);
        ownerLicenseCamera = findViewById(R.id.ownerLicenseCamera);
        cbOwner            = findViewById(R.id.cbOwner);
        cbReliable         = findViewById(R.id.cbReliable);
        cbTFX              = findViewById(R.id.cbTFX);
        cbOther            = findViewById(R.id.cbOther);
        enterTowLayout     = findViewById(R.id.enterTowLayout);
        enterTowInput      = findViewById(R.id.enterTowInput);
        btnOwnerUpdate     = findViewById(R.id.btnOwnerUpdate);

        // Gate
        gatePanel   = findViewById(R.id.gatePanel);
        gateGroup   = findViewById(R.id.gateGroup);
        gateIcon    = findViewById(R.id.gateIcon);
        btnGateYes  = findViewById(R.id.btnGateYes);
        btnGateNo   = findViewById(R.id.btnGateNo);
        gateWarning = findViewById(R.id.gateWarning);

        // Video
        videoPanel      = findViewById(R.id.videoPanel);
        videoGroup      = findViewById(R.id.videoGroup);
        videoIcon       = findViewById(R.id.videoIcon);
        videoPromptIcon = findViewById(R.id.videoPromptIcon);
        btnVideoAccept  = findViewById(R.id.btnVideoAccept);

        // Confirm
        btnConfirm = findViewById(R.id.btnConfirmRelease);
        btnConfirm.setEnabled(false);
        btnConfirm.setAlpha(0.5f);

        // ===== Read extras like CheckIn =====
        Intent in = getIntent();

        // Preferred: parcel
        vehicle = in.getParcelableExtra(EXTRA_VEHICLE);

        // Legacy fallbacks (Nav keys)
        lot         = in.getStringExtra(Nav.EXTRA_LOT);
        description = in.getStringExtra(Nav.EXTRA_DESC);
        eventName   = in.getStringExtra(Nav.EXTRA_EVENT_NAME);
        eventId     = in.getIntExtra(Nav.EXTRA_EVENT_ID, -1);
        if (driver == null || driver.trim().isEmpty()) {
            try {
                String fromSession = com.example.gt6driver.session.CurrentSelection.get().getDriverName();
                if (fromSession != null && !fromSession.trim().isEmpty()) driver = fromSession;
            } catch (Throwable ignored) {}
        }

        mode        = in.getStringExtra("mode"); // "check_out"
        vin         = in.getStringExtra(Nav.EXTRA_VIN);
        thumbUrl    = in.getStringExtra(Nav.EXTRA_THUMB);

        // Opportunity Id (intent first, else from vehicle)
        opportunityId = in.getStringExtra(EXTRA_OPPORTUNITY_ID);
        if ((opportunityId == null || opportunityId.trim().isEmpty()) && vehicle != null) {
            if (!isEmpty(vehicle.opportunityId)) {
                opportunityId = vehicle.opportunityId;
            } else if (!isEmpty(vehicle.crmopportunityid)) {
                opportunityId = vehicle.crmopportunityid;
            }
        }
        if (isEmpty(opportunityId)) {
            Toast.makeText(this, "Missing opportunity id", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // If we have a vehicle, prefer its values for header UI (same as CheckIn)
        if (vehicle != null) {
            String vLot   = (vehicle.lotnumber != null ? vehicle.lotnumber : "");
            String vDesc  = !isEmpty(vehicle.title) ? vehicle.title
                    : (vehicle.marketingdescription != null ? vehicle.marketingdescription : "");
            String vVin   = (vehicle.vin != null ? vehicle.vin : "");
            String vThumb = !isEmpty(vehicle.thumbUrl) ? vehicle.thumbUrl
                    : (vehicle.tbuncpath != null ? vehicle.tbuncpath : "");

            lot         = firstNonEmpty(vLot, lot);
            description = firstNonEmpty(vDesc, description);
            vin         = firstNonEmpty(vVin, vin);
            thumbUrl    = firstNonEmpty(vThumb, thumbUrl);
        }

        // Header UI
        panelLot.setText("LOT # " + (lot != null ? lot : ""));
        panelDesc.setText(description != null ? description : "");
        if (panelVin != null) panelVin.setText("VIN: " + (vin != null ? vin : ""));
        loadThumbIntoHeader();

        // ==== PHOTO launcher (like CheckIn) ====
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (pendingPhotoUri != null && pendingPhotoLabel != null) {
                            if (hasNonZeroSize(pendingPhotoUri)) {
                                try { getContentResolver().notifyChange(pendingPhotoUri, null); } catch (Exception ignored) {}

                                // Map label -> RELEASE photo URLs on working model
                                if (releaseModel == null) releaseModel = new ReleasePayload();
                                switch (pendingPhotoLabel.toLowerCase()) {
                                    case "keycheck":
                                        if (releaseModel.keyCheck == null) releaseModel.keyCheck = new ReleasePayload.KeyCheck();
                                        releaseModel.keyCheck.photoUrl = mediaUrl("keycheck_release.jpg");
                                        break;
                                    case "mileage":
                                        if (releaseModel.mileage == null) releaseModel.mileage = new ReleasePayload.Mileage();
                                        releaseModel.mileage.photoUrl = mediaUrl("mileage_release.jpg");
                                        break;
                                    case "owner":
                                        if (releaseModel.ownerVerification == null) releaseModel.ownerVerification = new ReleasePayload.OwnerVerification();
                                        releaseModel.ownerVerification.licensePhotoUrl = mediaUrl("owner_release.jpg");
                                        break;
                                    default:
                                        break;
                                }

                                // Kick the uploader
                                com.example.gt6driver.sync.GT6MediaSync.enqueueImmediate(this);

                                Toast.makeText(
                                        this,
                                        (pendingPhotoLabel.toUpperCase() + " photo saved:\n" + pendingPhotoUri),
                                        Toast.LENGTH_SHORT
                                ).show();

                            } else {
                                try { getContentResolver().delete(pendingPhotoUri, null, null); } catch (Exception ignored) {}
                                Toast.makeText(this, "Camera failed to save the photo. Please try again.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "Photo captured.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (pendingPhotoUri != null) {
                            try { getContentResolver().delete(pendingPhotoUri, null, null); } catch (Exception ignored) {}
                        }
                    }
                    pendingPhotoLabel = null;
                    pendingPhotoUri = null;
                }
        );

        // ==== VIDEO launcher (like CheckIn) ====
        recordVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        if (result.getResultCode() == RESULT_OK) {
                            Uri source = (result.getData() != null) ? result.getData().getData() : null;
                            if (source == null) {
                                Toast.makeText(this, "Video saved but no Uri returned.", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // Copy into our deterministic location: .../Movies/GT6/{consignmentId}/release.mp4
                            Uri target = createVideoUriForRelease();
                            if (target == null) {
                                Toast.makeText(this, "Unable to prepare storage for video.", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            if (!copyUri(source, target)) {
                                try { getContentResolver().delete(target, null, null); } catch (Exception ignored) {}
                                Toast.makeText(this, "Failed to save the video.", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            lastCapturedVideoUri = target;
                            try { getContentResolver().notifyChange(lastCapturedVideoUri, null); } catch (Exception ignored) {}

                            setStatusIcon(videoIcon, false);
                            videoDone = false;
                            setVideoExpanded(true);

                            // If you want to kick your uploader immediately:
                            com.example.gt6driver.sync.GT6MediaSync.enqueueImmediate(this);

                            Toast.makeText(this, "Video captured. Tap Accept to confirm.", Toast.LENGTH_SHORT).show();
                            refreshConfirmEnabled();
                        } else {
                            // user cancelled; nothing to do
                        }
                    } finally {
                        pendingVideoUri = null;
                        pendingVideoCapture = false;
                    }
                }
        );

//

        // CAMERA permission
        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        if (pendingVideoCapture) openVideoCamera();
                        else {
                            if (pendingPhotoLabel == null) pendingPhotoLabel = "owner";
                            openCameraPhoto(pendingPhotoLabel);
                        }
                    } else {
                        Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // WRITE permission (API <= 28)
        requestWritePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openVideoCamera();
                    } else {
                        Toast.makeText(this, "Storage permission is required to save video.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // VIN
        if (verifyVinPanel != null) verifyVinPanel.setOnClickListener(v -> toggleVinPanel());
        if (btnVinMatch != null) {
            btnVinMatch.setOnClickListener(v -> {
                vinNoMatchMode = false;
                setStatusIcon(verifyIcon, true);
                vinDone = true;
                if (noMatchGroup != null) noMatchGroup.setVisibility(View.GONE);
                setVinExpanded(false);
                refreshConfirmEnabled();
            });
        }
        if (btnVinNoMatch != null) {
            btnVinNoMatch.setOnClickListener(v -> {
                vinNoMatchMode = true;
                setStatusIcon(verifyIcon, false);
                vinDone = false;
                if (noMatchGroup != null) noMatchGroup.setVisibility(View.VISIBLE);
                setVinExpanded(true);
                refreshConfirmEnabled();
            });
        }

        // MILEAGE
        if (mileagePanel != null) mileagePanel.setOnClickListener(v -> toggleMileagePanel());
        if (btnMileageUpdate != null) {
            btnMileageUpdate.setOnClickListener(v -> {
                String miles = safe(enterMileageInput);
                if (TextUtils.isEmpty(miles)) {
                    enterMileageInput.setError("Mileage is required");
                    enterMileageInput.requestFocus();
                    return;
                }
                setStatusIcon(mileageIcon, true);
                mileageDone = true;
                setMileageExpanded(false);
                refreshConfirmEnabled();
            });
        }
        if (btnMileageCamera != null) {
            btnMileageCamera.setOnClickListener(v -> {
                setMileageExpanded(true);
                hideKeyboard();
                ensureCameraForPhoto("mileage");
            });
        }

        // KEY CHECK
        if (keyPanel != null) keyPanel.setOnClickListener(v -> toggleKeyPanel());
        if (btnKeyUpdate != null) {
            btnKeyUpdate.setOnClickListener(v -> {
                boolean hasFobs = cbFobs != null && cbFobs.isChecked();
                boolean noKey   = cbNoKey != null && cbNoKey.isChecked();
                if (!hasFobs && !noKey) {
                    Toast.makeText(this, "Select FOB(s) or NO KEY.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (hasFobs) {
                    String count = safe(enterKeyCountInput);
                    try {
                        int c = Integer.parseInt(count);
                        if (c <= 0) {
                            enterKeyCountInput.setError("Must be at least 1");
                            enterKeyCountInput.requestFocus();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        enterKeyCountInput.setError("Invalid number");
                        enterKeyCountInput.requestFocus();
                        return;
                    }
                }
                setStatusIcon(keyIcon, true);
                keyDone = true;
                setKeyExpanded(false);
                refreshConfirmEnabled();
            });
        }
        if (btnKeyCamera != null) {
            btnKeyCamera.setOnClickListener(v -> {
                setKeyExpanded(true);
                hideKeyboard();
                ensureCameraForPhoto("keycheck"); // maps to keycheck_release.jpg
            });
        }

        if (cbFobs != null && cbNoKey != null) {
            CompoundButton.OnCheckedChangeListener keyMutualListener = (btn, isChecked) -> {
                if (isChangingKeyState) return;
                isChangingKeyState = true;
                if (btn == cbFobs && isChecked) cbNoKey.setChecked(false);
                else if (btn == cbNoKey && isChecked) cbFobs.setChecked(false);
                updateKeyCountEnabled();
                isChangingKeyState = false;
            };
            cbFobs.setOnCheckedChangeListener(keyMutualListener);
            cbNoKey.setOnCheckedChangeListener(keyMutualListener);
            updateKeyCountEnabled();
        }

        // OWNER (mutual exclusive)
        if (ownerPanel != null) ownerPanel.setOnClickListener(v -> toggleOwnerPanel());

        CompoundButton.OnCheckedChangeListener ownerExclusiveListener = (btn, isChecked) -> {
            if (!isChecked) return; // ignore unchecks caused by exclusivity logic
            if (btn == cbOwner)    selectOwnerOption(cbOwner);
            if (btn == cbReliable) selectOwnerOption(cbReliable);
            if (btn == cbTFX)      selectOwnerOption(cbTFX);
            if (btn == cbOther)    selectOwnerOption(cbOther);
        };

        if (cbOwner    != null) cbOwner.setOnCheckedChangeListener(ownerExclusiveListener);
        if (cbReliable != null) cbReliable.setOnCheckedChangeListener(ownerExclusiveListener);
        if (cbTFX      != null) cbTFX.setOnCheckedChangeListener(ownerExclusiveListener);
        if (cbOther    != null) cbOther.setOnCheckedChangeListener(ownerExclusiveListener);

        if (ownerLicenseCamera != null) {
            ownerLicenseCamera.setOnClickListener(v -> {
                setOwnerExpanded(true);
                hideKeyboard();
                ensureCameraForPhoto("owner");
            });
        }

        // Owner "Update" button validation
        if (btnOwnerUpdate != null) {
            btnOwnerUpdate.setOnClickListener(v -> {
                boolean selOwner = cbOwner != null && cbOwner.isChecked();
                boolean selRel   = cbReliable != null && cbReliable.isChecked();
                boolean selTfx   = cbTFX != null && cbTFX.isChecked();
                boolean selOther = cbOther != null && cbOther.isChecked();

                if (!selOwner && !selRel && !selTfx && !selOther) {
                    Toast.makeText(this, "Select who is picking up the vehicle.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (selOther) {
                    String txt = readOtherParty();
                    if (TextUtils.isEmpty(txt)) {
                        enterTowInput.setError("Enter name for Other");
                        enterTowInput.requestFocus();
                        return;
                    }
                }
                ownerDone = true;
                setStatusIcon(ownerIcon, true);
                setOwnerExpanded(false);
                refreshConfirmEnabled();
            });
        }

        // GATE
        if (gatePanel != null) gatePanel.setOnClickListener(v -> toggleGatePanel());
        if (btnGateYes != null) {
            btnGateYes.setOnClickListener(v -> {
                gateRelease = true;
                setStatusIcon(gateIcon, true);
                if (gateWarning != null) gateWarning.setVisibility(View.GONE);
                setGateExpanded(false);
            });
        }
        if (btnGateNo != null) {
            btnGateNo.setOnClickListener(v -> {
                gateRelease = false;
                setStatusIcon(gateIcon, false);
                if (gateWarning != null) gateWarning.setVisibility(View.VISIBLE);
            });
        }

        // VIDEO
        if (videoPanel != null) videoPanel.setOnClickListener(v -> toggleVideoPanel());
        if (videoPromptIcon != null) {
            videoPromptIcon.setOnClickListener(v -> {
                setVideoExpanded(true);
                hideKeyboard();
                ensureCameraForVideo();
            });
        }
        if (btnVideoAccept != null) {
            btnVideoAccept.setOnClickListener(v -> {
                if (lastCapturedVideoUri == null) {
                    Toast.makeText(this, "No video to accept. Please record first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (releaseModel == null) releaseModel = new ReleasePayload();
                if (releaseModel.video == null) releaseModel.video = new ReleasePayload.Video();
                releaseModel.video.videoUrl = mediaUrl("release.mp4"); // deterministic
                setStatusIcon(videoIcon, true);
                videoDone = true;
                Toast.makeText(this, "Release video accepted.", Toast.LENGTH_SHORT).show();
                setVideoExpanded(false);
                refreshConfirmEnabled();
            });
        }

        // CONFIRM → build payload and call VehicleTask /Release
        btnConfirm.setOnClickListener(v -> {
            hideKeyboard();
            setConfirmBusy(true);

            // 🔔 Consignment/Key "Released" update (non-blocking)
            postConsignmentReleasedUpdate();

            ReleasePayload p = buildReleaseFromUi(); // merge UI + releaseModel URLs

            repo.releaseVehicleTask(opportunityId, p, new DriverTaskRepository.SaveCallback() {
                @Override public void onSaved() {
                    setConfirmBusy(false);
                    Toast.makeText(CheckOutDetailsActivity.this, "Release saved!", Toast.LENGTH_LONG).show();
                    // Kick one more scan to be safe
                    com.example.gt6driver.sync.GT6MediaSync.enqueueImmediate(CheckOutDetailsActivity.this);
                    navigateBackToLookup();
                }
                @Override public void onError(Throwable t) {
                    setConfirmBusy(false);
                    Toast.makeText(CheckOutDetailsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
                @Override public void onHttpError(int code, String message) {
                    setConfirmBusy(false);
                    Toast.makeText(CheckOutDetailsActivity.this,
                            "Release failed (" + code + "): " + message, Toast.LENGTH_LONG).show();
                }
            });
        });

        // Prefill from server if present
        repo.fetchRelease(opportunityId, new DriverTaskRepository.ReleaseCallback() {
            @Override public void onSuccess(ReleasePayload r) {
                if (r != null) {
                    releaseModel = r; // keep it so we don’t lose server-provided URLs/values
                    applyReleaseToUi(r);
                }
            }
            @Override public void onError(Throwable t) { /* log / toast as needed */ }
            @Override public void onHttpError(int code, String message) { /* log */ }
        });

        refreshConfirmEnabled();
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

// ===== Build payload (no auto-filling of photo URLs) =====
    private ReleasePayload buildReleaseFromUi() {
        // Start from the working model so any captured URLs already set are preserved
        ReleasePayload p = (releaseModel != null ? releaseModel : new ReleasePayload());

        p.opportunityId = opportunityId;
        p.releasedBy = resolveDriverName();

        // ---- Gate ----
        if (p.gateRelease == null) p.gateRelease = new ReleasePayload.GateRelease();
        p.gateRelease.gateReleaseTicket = gateRelease;

        // ---- VIN Verify ----
        if (p.vinVerify == null) p.vinVerify = new ReleasePayload.VinVerify();
        // Your UI uses vinDone + vinNoMatchMode
        p.vinVerify.isMatched = vinDone && !vinNoMatchMode;

        // ---- Mileage ----
        if (p.mileage == null) p.mileage = new ReleasePayload.Mileage();

        String milesTxt = safe(enterMileageInput);
        if (!TextUtils.isEmpty(milesTxt)) {
            try {
                p.mileage.odometer = Integer.parseInt(milesTxt.replaceAll("[^0-9]", ""));
            } catch (Exception ignore) {
                // leave as-is/null if invalid
            }
        }

        // Only include a mileage photo URL if one was actually captured earlier
        if (TextUtils.isEmpty(p.mileage.photoUrl)) {
            p.mileage.photoUrl = "";  // blank when no captured photo
        }

        // ---- Key Check ----
        if (p.keyCheck == null) p.keyCheck = new ReleasePayload.KeyCheck();

        boolean hasFobs = cbFobs != null && cbFobs.isChecked();
        boolean noKey   = cbNoKey != null && cbNoKey.isChecked();

        p.keyCheck.hasKey = hasFobs && !noKey;
        if (hasFobs) {
            String count = safe(enterKeyCountInput);
            try {
                p.keyCheck.numberOfKeys = TextUtils.isEmpty(count) ? null : Integer.valueOf(count);
            } catch (NumberFormatException ignore) {
                p.keyCheck.numberOfKeys = null;
            }
        } else {
            p.keyCheck.numberOfKeys = null;
        }

        // Only include a key photo URL if one was actually captured earlier
        if (TextUtils.isEmpty(p.keyCheck.photoUrl)) {
            p.keyCheck.photoUrl = "";  // blank when no captured photo
        }

        // ---- Owner Verification ----
        if (p.ownerVerification == null) p.ownerVerification = new ReleasePayload.OwnerVerification();

        boolean isOwner = cbOwner != null && cbOwner.isChecked();
        boolean isRel   = cbReliable != null && cbReliable.isChecked();
        boolean isTfx   = cbTFX != null && cbTFX.isChecked();
        boolean isOther = cbOther != null && cbOther.isChecked();

        p.ownerVerification.isOwner    = isOwner;
        p.ownerVerification.isReliable = isRel;
        p.ownerVerification.isTfx      = isTfx;
        p.ownerVerification.isOther    = isOther;

        p.ownerVerification.otherTransportName =
                (isOther ? readOtherParty() : null);

        // Only include the owner license photo URL if one was actually captured earlier
        if (TextUtils.isEmpty(p.ownerVerification.licensePhotoUrl)) {
            p.ownerVerification.licensePhotoUrl = "";  // blank when no captured photo
        }

        // ---- Video ----
        if (p.video == null) p.video = new ReleasePayload.Video();
        // Only include a video URL if user accepted a captured video earlier
        if (TextUtils.isEmpty(p.video.videoUrl)) {
            p.video.videoUrl = "";  // blank when not recorded/accepted
        }

        return p;
    }


    // ====== Bind from server (kept, just sanity tweaks) ======
    private void applyReleaseToUi(ReleasePayload r) {
        if (r == null) return;

        // Gate
        gateRelease = (r.gateRelease != null && Boolean.TRUE.equals(r.gateRelease.gateReleaseTicket));

        setStatusIcon(gateIcon, gateRelease);
        if (gateWarning != null) gateWarning.setVisibility(gateRelease ? View.GONE : View.VISIBLE);
        if (btnGateYes != null) btnGateYes.setSelected(gateRelease);
        if (btnGateNo  != null) btnGateNo.setSelected(!gateRelease);

        // VIN verify
        if (r.vinVerify != null) {
            boolean matched = Boolean.TRUE.equals(r.vinVerify.isMatched);
            vinDone = matched;
            vinNoMatchMode = !matched;
            setStatusIcon(verifyIcon, matched);
            setVinExpanded(false);
        }

        // Mileage
        if (r.mileage != null) {
            if (r.mileage.odometer != null && enterMileageInput != null) {
                enterMileageInput.setText(String.valueOf(r.mileage.odometer));
                setStatusIcon(mileageIcon, true);
                mileageDone = true;
            }
            setMileageExpanded(false);
        }

        // Key check
        if (r.keyCheck != null) {
            boolean hasKey = Boolean.TRUE.equals(r.keyCheck.hasKey);
            Integer cnt = r.keyCheck.numberOfKeys;

            isChangingKeyState = true;
            if (cbFobs  != null) cbFobs.setChecked(hasKey);
            if (cbNoKey != null) cbNoKey.setChecked(!hasKey);
            isChangingKeyState = false;

            updateKeyCountEnabled();
            if (hasKey && enterKeyCountInput != null && cnt != null && cnt > 0) {
                enterKeyCountInput.setText(String.valueOf(cnt));
            }
            keyDone = hasKey || (cbNoKey != null && cbNoKey.isChecked());
            setStatusIcon(keyIcon, keyDone);
            setKeyExpanded(false);
        }

        // Owner verification (respect any server-provided state)
        if (r.ownerVerification != null) {
            boolean isOwner = Boolean.TRUE.equals(r.ownerVerification.isOwner);
            boolean isRel   = Boolean.TRUE.equals(r.ownerVerification.isReliable);
            boolean isTfx   = Boolean.TRUE.equals(r.ownerVerification.isTfx);
            boolean isOther = Boolean.TRUE.equals(r.ownerVerification.isOther);

            isChangingOwnerState = true;
            if (cbOwner    != null) cbOwner.setChecked(isOwner);
            if (cbReliable != null) cbReliable.setChecked(isRel);
            if (cbTFX      != null) cbTFX.setChecked(isTfx);
            if (cbOther    != null) cbOther.setChecked(isOther);
            isChangingOwnerState = false;

            updateTowNameEnabled(isOther);
            if (isOther && enterTowInput != null) {
                enterTowInput.setText(safeStr(r.ownerVerification.otherTransportName));
            }

            ownerDone = isOwner || isRel || isTfx || isOther;
            setStatusIcon(ownerIcon, ownerDone);
            setOwnerExpanded(false);
        }

        // Video
        if (r.video != null && r.video.videoUrl != null) {
            try { lastCapturedVideoUri = Uri.parse(r.video.videoUrl); } catch (Exception ignored) {}
            videoDone = true;
            setStatusIcon(videoIcon, true);
            setVideoExpanded(false);
        }

        refreshConfirmEnabled();
    }

    // ===== Storage helpers (mirror CheckIn) =====
    /** "GT6/{consignmentid}" (match CheckIn casing) */
    private String consignmentSubdir() {
        String id = consignmentIdStr();
        return "GT6/" + id;
    }

    // returns just the numeric/string id (no GT6 prefix)
    private String consignmentIdStr() {
        if (vehicle != null && vehicle.consignmentid != null) {
            return String.valueOf(vehicle.consignmentid);
        }
        return "unknown";
    }

    private String mediaUrl(String fileName) {
        return BLOB_BASE + consignmentIdStr() + "/" + fileName;
    }

    private void ensureCameraForPhoto(String label) {
        pendingVideoCapture = false;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCameraPhoto(label);
        } else {
            pendingPhotoLabel = label;
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void ensureCameraForVideo() {
        pendingVideoCapture = true;

        boolean cameraOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (!cameraOk) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            boolean writeOk = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            if (!writeOk) {
                requestWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
        }

        openVideoCamera();
    }

    private Uri createReleasePhotoUri(String label) {
        String fileName;
        if ("keycheck".equalsIgnoreCase(label)) fileName = "keycheck_release.jpg";
        else if ("mileage".equalsIgnoreCase(label)) fileName = "mileage_release.jpg";
        else if ("owner".equalsIgnoreCase(label)) fileName = "owner_release.jpg";
        else fileName = (label == null || label.trim().isEmpty()) ? "photo_release.jpg" : (label + "_release.jpg");

        String subdir = consignmentSubdir(); // "GT6/{consignmentid}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + subdir);
            return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        } else {
            File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File dir = new File(pictures, subdir);
            if (!dir.exists() && !dir.mkdirs()) return null;

            File file = new File(dir, fileName);
            try {
                return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private void openCameraPhoto(String label) {
        pendingPhotoLabel = label;
        pendingPhotoUri = createReleasePhotoUri(label);
        if (pendingPhotoUri == null) {
            Toast.makeText(this, "Unable to prepare storage for photo.", Toast.LENGTH_SHORT).show();
            pendingPhotoLabel = null;
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);

        intent.setClipData(ClipData.newRawUri("output", pendingPhotoUri));
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            grantUriPermission(
                    ri.activityInfo.packageName,
                    pendingPhotoUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        }

        if (intent.resolveActivity(getPackageManager()) != null) {
            takePictureLauncher.launch(intent);
        } else {
            Toast.makeText(this, "No camera app available.", Toast.LENGTH_SHORT).show();
            try { getContentResolver().delete(pendingPhotoUri, null, null); } catch (Exception ignored) {}
            pendingPhotoLabel = null;
            pendingPhotoUri = null;
        }
    }

    private Uri createVideoUriForRelease() {
        String fileName = "release.mp4";
        String subdir   = consignmentSubdir(); // "GT6/{consignmentid}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + subdir);
            return getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
        } else {
            File movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            File dir = new File(movies, subdir);
            if (!dir.exists() && !dir.mkdirs()) return null;

            File file = new File(dir, fileName);
            try {
                return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
//
    private String resolveDriverName() {
        if (driver != null && !driver.trim().isEmpty()) return driver.trim();
        try {
            String fromSession = com.example.gt6driver.session.CurrentSelection.get().getDriverName();
            if (fromSession != null && !fromSession.trim().isEmpty()) return fromSession.trim();
        } catch (Throwable ignored) {}
        return "";
    }

    private void openVideoCamera() {
        // No EXTRA_OUTPUT → OEM camera shows OK/CANCEL review UI
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);

        // These can suppress review UI on some devices—omit for reliability:
        // intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 120);
        // intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 200 * 1024 * 1024L);

        if (intent.resolveActivity(getPackageManager()) != null) {
            recordVideoLauncher.launch(intent);
        } else {
            Toast.makeText(this, "No video recorder available.", Toast.LENGTH_SHORT).show();
        }
    }

    // ===== UI helpers =====
    private void loadThumbIntoHeader() {
        if (ivVehicleThumb == null) return;
        if (!isEmpty(thumbUrl)) {
            Glide.with(this)
                    .load(thumbUrl)
                    .transform(new CenterCrop(), new RoundedCorners(16))
                    .placeholder(android.R.color.darker_gray)
                    .error(android.R.color.darker_gray)
                    .into(ivVehicleThumb);
        } else {
            ivVehicleThumb.setImageResource(android.R.color.darker_gray);
        }
    }

    private void toggleVinPanel() { setVinExpanded(!vinExpanded); }
    private void setVinExpanded(boolean expanded) {
        vinExpanded = expanded;
        if (verifyVinValue != null) {
            verifyVinValue.setText(vin != null ? vin : "");
            verifyVinValue.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (verifyActions != null) verifyActions.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (noMatchGroup != null) noMatchGroup.setVisibility(expanded && vinNoMatchMode ? View.VISIBLE : View.GONE);
    }

    private void toggleMileagePanel() { setMileageExpanded(!mileageExpanded); }
    private void setMileageExpanded(boolean expanded) {
        mileageExpanded = expanded;
        if (mileageGroup != null) mileageGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private void toggleKeyPanel() { setKeyExpanded(!keyExpanded); }
    private void setKeyExpanded(boolean expanded) {
        keyExpanded = expanded;
        if (keyGroup != null) keyGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private void toggleVideoPanel() { setVideoExpanded(!videoExpanded); }
    private void setVideoExpanded(boolean expanded) {
        videoExpanded = expanded;
        if (videoGroup != null) videoGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private void toggleOwnerPanel() { setOwnerExpanded(!ownerExpanded); }
    private void setOwnerExpanded(boolean expanded) {
        ownerExpanded = expanded;
        if (ownerGroup != null) ownerGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private void toggleGatePanel() { setGateExpanded(!gateExpanded); }
    private void setGateExpanded(boolean expanded) {
        gateExpanded = expanded;
        if (gateGroup != null) gateGroup.setVisibility(expanded ? View.VISIBLE : View.GONE);
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

    private void updateTowNameEnabled(boolean enable) {
        if (enterTowLayout != null) {
            enterTowLayout.setEnabled(enable);
            enterTowLayout.setAlpha(enable ? 1f : 0.6f);
        }
        if (enterTowInput != null) {
            enterTowInput.setEnabled(enable);
            if (!enable) enterTowInput.setText(null);
        }
    }

    private void refreshConfirmEnabled() {
        boolean allDone = vinDone && mileageDone && keyDone && ownerDone && videoDone;
        if (btnConfirm != null) {
            btnConfirm.setEnabled(allDone);
            btnConfirm.setAlpha(allDone ? 1f : 0.5f);
        }
    }

    private void setConfirmBusy(boolean busy) {
        if (btnConfirm != null) {
            btnConfirm.setEnabled(!busy);
            btnConfirm.setAlpha(busy ? 0.5f : 1f);
        }
    }

    private void navigateBackToLookup() {
        Intent back = new Intent(CheckOutDetailsActivity.this, LookupActivity.class);
        back.putExtra("mode", mode);
        back.putExtra("eventName", eventName);
        back.putExtra("eventId", eventId);
        back.putExtra("driver", driver);
        back.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(back);
        finish();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view == null) view = new View(this);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private String safe(TextInputEditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }

    private String safeStr(String s) { return s == null ? "" : s; }
    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
    private static String firstNonEmpty(String a, String b) { return isEmpty(a) ? (b == null ? "" : b) : a; }

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

    // ===================== Owner Verification exclusivity + helpers =====================

    /** Enforce mutual exclusivity for Owner Verification checkboxes */
    private void selectOwnerOption(MaterialCheckBox selected) {
        if (isChangingOwnerState) return;
        isChangingOwnerState = true;

        boolean selOwner = (selected == cbOwner);
        boolean selRel   = (selected == cbReliable);
        boolean selTfx   = (selected == cbTFX);
        boolean selOther = (selected == cbOther);

        if (cbOwner    != null) cbOwner.setChecked(selOwner);
        if (cbReliable != null) cbReliable.setChecked(selRel);
        if (cbTFX      != null) cbTFX.setChecked(selTfx);
        if (cbOther    != null) cbOther.setChecked(selOther);

        updateTowNameEnabled(selOther);
        isChangingOwnerState = false;

        ownerDone = selOwner || selRel || selTfx || selOther;
        setStatusIcon(ownerIcon, ownerDone);
        refreshConfirmEnabled();
    }

    /** Read the free-text value safely (used when OTHER is selected) */
    private String readOtherParty() {
        return (enterTowInput != null && enterTowInput.getText() != null)
                ? enterTowInput.getText().toString().trim() : "";
    }

    /** Map Owner Verification selection to a releaseTo label */
    private String computeReleaseTo() {
        if (cbOwner != null && cbOwner.isChecked())    return "Owner";
        if (cbReliable != null && cbReliable.isChecked()) return "Reliable";
        if (cbTFX != null && cbTFX.isChecked())        return "TFX";
        if (cbOther != null && cbOther.isChecked())    return "Other";
        return "Unknown";
    }

    // ===================== Consignment Key "Released" update =====================

// ===================== Consignment Key "Released" update =====================
    /** Fire-and-forget call to Consignment/Key API per requirements, with logging */
    private void postConsignmentReleasedUpdate() {
        if (isEmpty(opportunityId)) return;

        String releaseTo = computeReleaseTo();
        String responsible = (cbOther != null && cbOther.isChecked()) ? readOtherParty() : "";

        if ("Other".equals(releaseTo) && TextUtils.isEmpty(responsible)) {
            Toast.makeText(this, "Enter name for Other (responsible party).", Toast.LENGTH_SHORT).show();
            return;
        }

        ConsignmentKeyPayload body = new ConsignmentKeyPayload(
                "Released",
                releaseTo,
                responsible,
                "Taking Vehicle Home"
        );

        try {
            // Build client & service once
            retrofit2.Retrofit retrofit = ApiClient.getMemberApi();
            final DriverTaskApi api = retrofit.create(DriverTaskApi.class);

            // Optional: log the URL we’re effectively hitting
            String baseUrl = retrofit.baseUrl().toString();
            String endpointPath = "api/v1/Opportunity/Consignment/" + opportunityId + "/Key";
            String fullUrl = baseUrl + endpointPath;
            String payloadJson = new com.google.gson.Gson().toJson(body);
            Log.i(TAG, "PUT " + fullUrl);
            Log.i(TAG, "Payload: " + payloadJson);

            api.updateConsignmentKey(opportunityId, body).enqueue(new retrofit2.Callback<Void>() {
                @Override public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> resp) {
                    Log.i(TAG, "Response: HTTP " + resp.code());
                    if (!resp.isSuccessful()) {
                        Toast.makeText(CheckOutDetailsActivity.this,
                                "Consignment key update failed (" + resp.code() + ")", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                    Log.e(TAG, "Network failure calling Consignment Key API", t);
                    Toast.makeText(CheckOutDetailsActivity.this,
                            "Network error updating consignment key", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error building or calling Consignment Key API", e);
            Toast.makeText(this, "Error calling Consignment Key API", Toast.LENGTH_SHORT).show();
        }
    }
}










