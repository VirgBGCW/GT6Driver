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
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.gt6driver.model.ConsignmentKeyPayload;
import com.example.gt6driver.model.VehicleDetail;
import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.net.DriverTaskApi;
import com.example.gt6driver.net.DriverTaskRepository;
import com.example.gt6driver.net.ReleasePayload;
import com.example.gt6driver.util.DeviceInfo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CheckOutDetailsActivity extends AppCompatActivity {

    private static final String TAG = "GT6Release";

    private static final String BLOB_BASE = "https://stgt6driverappprod.blob.core.windows.net/";
    private static final String COMPRESSED_VIDEO_BASE =
            "https://stgt6driverappprod.blob.core.windows.net/compressed-files/";

    private static final String EXTRA_VEHICLE        = "vehicle";
    private static final String EXTRA_OPPORTUNITY_ID = "opportunityId";

    // Runtime permissions
    private static final String PERM_CAMERA = Manifest.permission.CAMERA;

    // For compatibility with older builds of ReleaseVideoActivity (now "no-URI" by design)
    private static final String EXTRA_RESULT_CANCELED  = "extra_video_canceled"; // boolean (optional)

    private VehicleDetail vehicle;
    private DriverTaskRepository repo;
    private ReleasePayload releaseModel;

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
    private MaterialButton btnVideoAccept; // launches ReleaseVideoActivity
    private boolean videoExpanded = false;

    // Launchers
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<Intent> takePictureLauncher;
    private ActivityResultLauncher<Intent> releaseVideoLauncher;

    private String pendingPhotoLabel = null; // "vin"|"mileage"|"keycheck"|"owner"
    private Uri pendingPhotoUri = null;

    // "No URI" video flow: we no longer rely on a returned Uri
    // (kept for optional debugging if older builds still return it)
    private Uri lastCapturedVideoUri = null;

    // Confirm
    private MaterialButton btnConfirm;

    // Context from previous screen
    private String lot, description, eventName, driver, mode, vin, thumbUrl;
    private int eventId;
    private String opportunityId;

    // Completion flags
    private boolean vinDone = false;
    private boolean keyDone = false;
    private boolean videoDone = false;

    private ImageButton btnKeyCamera;

    // Permission routing: camera permission is used for both PHOTO and VIDEO
    private enum PendingCameraAction { NONE, PHOTO, VIDEO }
    private PendingCameraAction pendingCameraAction = PendingCameraAction.NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_out_details);

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

        // ===== Read extras =====
        Intent in = getIntent();
        vehicle = in.getParcelableExtra(EXTRA_VEHICLE);

        lot         = in.getStringExtra(Nav.EXTRA_LOT);
        description = in.getStringExtra(Nav.EXTRA_DESC);
        eventName   = in.getStringExtra(Nav.EXTRA_EVENT_NAME);
        eventId     = in.getIntExtra(Nav.EXTRA_EVENT_ID, -1);
        driver      = in.getStringExtra("driver");
        if (driver == null || driver.trim().isEmpty()) {
            try {
                String fromSession = com.example.gt6driver.session.CurrentSelection.get().getDriverName();
                if (fromSession != null && !fromSession.trim().isEmpty()) driver = fromSession;
            } catch (Throwable ignored) {}
        }

        mode        = in.getStringExtra("mode");
        vin         = in.getStringExtra(Nav.EXTRA_VIN);
        thumbUrl    = in.getStringExtra(Nav.EXTRA_THUMB);

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

        // ==== PHOTO launcher ====
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (pendingPhotoUri != null && pendingPhotoLabel != null) {
                            if (hasNonZeroSize(pendingPhotoUri)) {
                                try { getContentResolver().notifyChange(pendingPhotoUri, null); } catch (Exception ignored) {}

                                if (releaseModel == null) releaseModel = new ReleasePayload();
                                switch (pendingPhotoLabel.toLowerCase()) {
                                    case "keycheck":
                                        if (releaseModel.keyCheck == null) releaseModel.keyCheck = new ReleasePayload.KeyCheck();
                                        releaseModel.keyCheck.photoUrl = mediaUrl("keycheck_release.jpg");
                                        break;
                                    case "owner":
                                        if (releaseModel.ownerVerification == null) releaseModel.ownerVerification = new ReleasePayload.OwnerVerification();
                                        releaseModel.ownerVerification.licensePhotoUrl = mediaUrl("owner_release.jpg");
                                        break;
                                    default:
                                        break;
                                }

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
                    pendingCameraAction = PendingCameraAction.NONE;
                }
        );

        // CAMERA permission (photos + launching ReleaseVideoActivity)
        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        pendingCameraAction = PendingCameraAction.NONE;
                        Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // route to the correct pending action
                    if (pendingCameraAction == PendingCameraAction.VIDEO) {
                        pendingCameraAction = PendingCameraAction.NONE;
                        launchReleaseVideoActivity();
                        return;
                    }

                    if (pendingCameraAction == PendingCameraAction.PHOTO && pendingPhotoLabel != null) {
                        String label = pendingPhotoLabel;
                        pendingCameraAction = PendingCameraAction.NONE;
                        openCameraPhoto(label);
                    } else {
                        pendingCameraAction = PendingCameraAction.NONE;
                    }
                }
        );

        // VIDEO launcher: "no URI" expected
        releaseVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK) {
                        Intent data = result.getData();
                        boolean canceled = data != null && data.getBooleanExtra(EXTRA_RESULT_CANCELED, false);
                        if (canceled) Toast.makeText(this, "Video canceled.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Some older builds might still return a Uri; keep for logging only.
                    Intent data = result.getData();
                    lastCapturedVideoUri = null;
                    if (data != null) {
                        try { lastCapturedVideoUri = data.getData(); } catch (Throwable ignored) {}
                        if (lastCapturedVideoUri == null && data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                            try { lastCapturedVideoUri = data.getClipData().getItemAt(0).getUri(); } catch (Throwable ignored) {}
                        }
                    }

                    // ✅ Mark video complete immediately (no-URI flow)
                    videoDone = true;
                    setStatusIcon(videoIcon, true);

                    // Write the blob URL into the payload (compress/uploader flow still uses this)
                    acceptReleaseVideoAfterRecord();

                    // Sidecar JSON in Downloads
                    boolean sidecarOk = writeSidecarJsonToDownload(
                            "release",
                            isoUtcNow(),
                            consignmentIdStr(),
                            DeviceInfo.getDeviceName(this),
                            resolveDriverName(),
                            (lot != null ? lot : "")
                    );
                    Log.i(TAG, "Sidecar write ok=" + sidecarOk);

                    // Kick uploader
                    com.example.gt6driver.sync.GT6MediaSync.enqueueImmediate(this);

                    refreshConfirmEnabled();
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
                ensureCameraForPhoto("keycheck");
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
            if (!isChecked) return;
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

        // VIDEO: use ReleaseVideoActivity
        if (videoPanel != null) videoPanel.setOnClickListener(v -> toggleVideoPanel());
        if (videoPromptIcon != null) {
            videoPromptIcon.setOnClickListener(v -> {
                setVideoExpanded(true);
                hideKeyboard();
                ensureCameraThenLaunchVideo();
            });
        }

        if (btnVideoAccept != null) {
            btnVideoAccept.setText("RECORD VIDEO");
            btnVideoAccept.setOnClickListener(v -> {
                setVideoExpanded(true);
                hideKeyboard();
                ensureCameraThenLaunchVideo();
            });
        }

        // CONFIRM
        btnConfirm.setOnClickListener(v -> {
            hideKeyboard();
            setConfirmBusy(true);

            postConsignmentReleasedUpdate();

            ReleasePayload p = buildReleaseFromUi();

            repo.releaseVehicleTask(opportunityId, p, new DriverTaskRepository.SaveCallback() {
                @Override public void onSaved() {
                    setConfirmBusy(false);
                    Toast.makeText(CheckOutDetailsActivity.this, "Release saved!", Toast.LENGTH_LONG).show();
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

        // Prefill from server
        repo.fetchRelease(opportunityId, new DriverTaskRepository.ReleaseCallback() {
            @Override public void onSuccess(ReleasePayload r) {
                if (r != null) {
                    releaseModel = r;
                    applyReleaseToUi(r);
                }
            }
            @Override public void onError(Throwable t) {}
            @Override public void onHttpError(int code, String message) {}
        });

        refreshConfirmEnabled();
    }

    // ====== Video launch helpers (no-URI) ======

    private void ensureCameraThenLaunchVideo() {
        boolean cameraOk = ContextCompat.checkSelfPermission(this, PERM_CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        if (!cameraOk) {
            pendingCameraAction = PendingCameraAction.VIDEO;
            requestCameraPermissionLauncher.launch(PERM_CAMERA);
            return;
        }
        launchReleaseVideoActivity();
    }

    private void launchReleaseVideoActivity() {
        try {
            Intent intent = new Intent(this, ReleaseVideoActivity.class);

            // Pass context if your ReleaseVideoActivity uses it (safe even if it ignores extras)
            intent.putExtra(EXTRA_OPPORTUNITY_ID, opportunityId);
            intent.putExtra("consignmentId", consignmentIdStr());
            intent.putExtra("lot", lot != null ? lot : "");
            intent.putExtra("driver", resolveDriverName());
            intent.putExtra("mode", mode != null ? mode : "");

            releaseVideoLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch ReleaseVideoActivity", e);
            Toast.makeText(this, "Unable to open video recorder.", Toast.LENGTH_SHORT).show();
        }
    }

    // ===================== Existing helpers (unchanged) =====================

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

            try {
                String sel = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                        MediaStore.MediaColumns.RELATIVE_PATH + "=?";
                int d = getContentResolver().delete(collection, sel, new String[]{ sidecarName, relPath });
                Log.i(TAG, "Sidecar(Download): deleted existing rows d=" + d);
            } catch (Exception e) {
                Log.w(TAG, "Sidecar(Download): delete existing failed", e);
            }

            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, sidecarName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri jsonUri = getContentResolver().insert(collection, cv);
            if (jsonUri == null) {
                Log.e(TAG, "Sidecar(Download): insert returned null relPath=" + relPath);
                return false;
            }

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
                    Log.e(TAG, "Sidecar(Download): openOutputStream null " + jsonUri);
                    try { getContentResolver().delete(jsonUri, null, null); } catch (Exception ignored) {}
                    return false;
                }
                out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.flush();
            }

            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(jsonUri, done, null, null);

            long size = 0;
            try (Cursor c = getContentResolver().query(
                    jsonUri, new String[]{ MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME },
                    null, null, null
            )) {
                if (c != null && c.moveToFirst()) {
                    size = c.getLong(0);
                    Log.i(TAG, "Sidecar(Download): wrote " + c.getString(1) + c.getString(2) +
                            " size=" + size + " uri=" + jsonUri);
                }
            }

            return size > 0;

        } catch (Exception e) {
            Log.e(TAG, "Sidecar(Download): write failed", e);
            return false;
        }
    }

    private String isoUtcNow() {
        long now = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return java.time.Instant.ofEpochMilli(now).toString();
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date(now));
    }

    // ✅ no-URI accept logic used after returning from ReleaseVideoActivity
    private void acceptReleaseVideoAfterRecord() {
        if (releaseModel == null) releaseModel = new ReleasePayload();
        if (releaseModel.video == null) releaseModel.video = new ReleasePayload.Video();

        // Your uploader/compressor flow still uses this URL
        releaseModel.video.videoUrl = compressedMp4Url("release");

        setStatusIcon(videoIcon, true);
        videoDone = true;

        Toast.makeText(this, "Release video saved.", Toast.LENGTH_SHORT).show();
        setVideoExpanded(false);
    }

    // ===== Build payload =====
    private ReleasePayload buildReleaseFromUi() {
        ReleasePayload p = (releaseModel != null ? releaseModel : new ReleasePayload());

        p.opportunityId = opportunityId;
        p.releasedBy = resolveDriverName();

        if (p.gateRelease == null) p.gateRelease = new ReleasePayload.GateRelease();
        p.gateRelease.gateReleaseTicket = gateRelease;

        if (p.vinVerify == null) p.vinVerify = new ReleasePayload.VinVerify();
        p.vinVerify.isMatched = vinDone && !vinNoMatchMode;

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

        if (TextUtils.isEmpty(p.keyCheck.photoUrl)) p.keyCheck.photoUrl = "";

        if (p.ownerVerification == null) p.ownerVerification = new ReleasePayload.OwnerVerification();

        boolean isOwner = cbOwner != null && cbOwner.isChecked();
        boolean isRel   = cbReliable != null && cbReliable.isChecked();
        boolean isTfx   = cbTFX != null && cbTFX.isChecked();
        boolean isOther = cbOther != null && cbOther.isChecked();

        p.ownerVerification.isOwner    = isOwner;
        p.ownerVerification.isReliable = isRel;
        p.ownerVerification.isTfx      = isTfx;
        p.ownerVerification.isOther    = isOther;

        p.ownerVerification.otherTransportName = (isOther ? readOtherParty() : null);

        if (TextUtils.isEmpty(p.ownerVerification.licensePhotoUrl)) {
            p.ownerVerification.licensePhotoUrl = "";
        }

        if (p.video == null) p.video = new ReleasePayload.Video();
        if (TextUtils.isEmpty(p.video.videoUrl)) p.video.videoUrl = "";

        return p;
    }

    // NOTE: applyReleaseToUi(...) unchanged from your original (keep yours as-is)
    private void applyReleaseToUi(ReleasePayload r) {
        // keep your original method body here (unchanged)
        // (omitted in this snippet for brevity)
    }

    private String consignmentSubdir() {
        String id = consignmentIdStr();
        return "GT6/" + id;
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

    private String compressedMp4Url(String baseNameNoExt) {
        return COMPRESSED_VIDEO_BASE + consignmentIdStr() + "/" + baseNameNoExt + "_c.mp4";
    }

    private void ensureCameraForPhoto(String label) {
        if (ContextCompat.checkSelfPermission(this, PERM_CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCameraPhoto(label);
        } else {
            pendingPhotoLabel = label;
            pendingCameraAction = PendingCameraAction.PHOTO;
            requestCameraPermissionLauncher.launch(PERM_CAMERA);
        }
    }

    private Uri createReleasePhotoUri(String label) {
        String fileName;
        if ("keycheck".equalsIgnoreCase(label)) fileName = "keycheck_release.jpg";
        else if ("mileage".equalsIgnoreCase(label)) fileName = "mileage_release.jpg";
        else if ("owner".equalsIgnoreCase(label)) fileName = "owner_release.jpg";
        else fileName = (label == null || label.trim().isEmpty()) ? "photo_release.jpg" : (label + "_release.jpg");

        String subdir = consignmentSubdir();

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + subdir);
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
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

    private String resolveDriverName() {
        if (driver != null && !driver.trim().isEmpty()) return driver.trim();
        try {
            String fromSession = com.example.gt6driver.session.CurrentSelection.get().getDriverName();
            if (fromSession != null && !fromSession.trim().isEmpty()) return fromSession.trim();
        } catch (Throwable ignored) {}
        return "";
    }

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
        boolean allDone = vinDone && keyDone && ownerDone && videoDone;
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

    private String readOtherParty() {
        return (enterTowInput != null && enterTowInput.getText() != null)
                ? enterTowInput.getText().toString().trim() : "";
    }

    private String computeReleaseTo() {
        if (cbOwner != null && cbOwner.isChecked())    return "Owner";
        if (cbReliable != null && cbReliable.isChecked()) return "Reliable";
        if (cbTFX != null && cbTFX.isChecked())        return "TFX";
        if (cbOther != null && cbOther.isChecked())    return "Other";
        return "Unknown";
    }

    // ===================== Consignment Key "Released" update =====================

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
            retrofit2.Retrofit retrofit = ApiClient.getMemberApi();
            final DriverTaskApi api = retrofit.create(DriverTaskApi.class);

            String baseUrl = retrofit.baseUrl().toString();
            String endpointPath = "api/v1/Opportunity/Consignment/" + opportunityId + "/Key";
            String fullUrl = baseUrl + endpointPath;
            String payloadJson = new Gson().toJson(body);
            Log.i(TAG, "PUT " + fullUrl);
            Log.i(TAG, "Payload: " + payloadJson);

            api.updateConsignmentKey(opportunityId, body).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> resp) {
                    Log.i(TAG, "Response: HTTP " + resp.code());
                    if (!resp.isSuccessful()) {
                        Toast.makeText(CheckOutDetailsActivity.this,
                                "Consignment key update failed (" + resp.code() + ")", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onFailure(Call<Void> call, Throwable t) {
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










