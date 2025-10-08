// app/src/main/java/com/example/gt6driver/CheckInDetailsActivity.java
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
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.gt6driver.model.VehicleDetail;

import java.io.File;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.gt6driver.net.DriverTaskRepository;
import com.example.gt6driver.net.VehicleTaskIntake;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
//
import com.example.gt6driver.Nav;


public class CheckInDetailsActivity extends AppCompatActivity {
    // for statis AZURE STORATE
    private static final String BLOB_BASE = "https://stgt6driverappprod.blob.core.windows.net/driver/";
    // Keys to match what ActionActivity sends
    private static final String EXTRA_VEHICLE        = "vehicle";
    private static final String EXTRA_OPPORTUNITY_ID = "opportunityId";
    private VehicleDetail vehicle;

    private TextView panelLot, panelDesc, panelVin;
    private ImageView ivVehicleThumb;

    // VERIFY VIN
    private MaterialCardView verifyVinPanel;
    private View verifyVinHeader;
    private View verifyActions;
    private ImageView verifyIcon;
    private MaterialButton btnVinMatch, btnVinNoMatch;
    private Boolean vinMatched = null; // true = MATCH, false = DOES NOT MATCH, null = unset
    private View noMatchGroup;
    private TextInputLayout enterVinLayout;
    private TextInputEditText enterVinInput;
    private MaterialCheckBox cbVinNotified;
    private MaterialButton btnVinUpdate;
    private TextView verifyVinValue;
    private ImageButton btnCamera;
    private boolean vinExpanded = false;
    private boolean vinNoMatchMode = false;

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

    // KEY CHECK
    private MaterialCardView keyPanel;
    private View keyHeader;
    private View keyGroup;
    private ImageView keyIcon;
    private MaterialCheckBox cbFobs, cbNoKey;
    private TextInputLayout enterKeyCountLayout;
    private TextInputEditText enterKeyCountInput;
    private ImageButton btnKeyCamera;
    private MaterialButton btnKeyUpdate;
    private boolean keyExpanded = false;
    private boolean isChangingKeyState = false;

    // INTAKE VIDEO
    private MaterialCardView videoPanel;
    private View videoHeader;
    private View videoGroup;
    private ImageView videoIcon;
    private ImageView videoPromptIcon;
    private MaterialButton btnVideoAccept;
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
    private View qualityGroup, qualityDetailsGroup;
    private ImageView qualityIcon;
    private TextView qualityMessage;
    private MaterialButton btnQualityNoConcerns, btnQualityConcerns, btnQualityUpdate;
    private Boolean qualityHasConcerns = null;  // this is the field used to bind to API to toggle concerns flag
    private MaterialCheckBox cbExteriorDamage, cbInteriorDamage, cbTiresWheels, cbMechanical, cbServiceLights;
    private boolean qualityExpanded = false;

    // Camera/Permissions
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<Intent> recordVideoLauncher;
    private ActivityResultLauncher<String> requestWritePermissionLauncher;
    private boolean pendingVideoCapture = false;

    private Uri pendingVideoUri;
    private Uri lastCapturedVideoUri;

    private MaterialButton btnConfirm;

    private String lot, description, eventName, driver, mode, vin;
    private String shortDescription;
    private int eventId;

    private String thumbUrl;

    // Completion flags
    private boolean vinDone = false;
    private boolean mileageDone = false;
    private boolean keyDone = false;
    private boolean videoDone = false;
    private boolean descDone = false;
    private boolean qualityDone = false;

    // Full-res photo capture state
    private String pendingPhotoLabel = null; // "vin" | "mileage" | "keycheck"
    private Uri pendingPhotoUri = null;
    private ActivityResultLauncher<Intent> takePictureLauncher;

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
        panelVin  = findViewById(R.id.panelVin);
        ivVehicleThumb = findViewById(R.id.ivCheckInThumb);

        // VERIFY VIN
        verifyVinPanel = findViewById(R.id.verifyVinPanel);
        verifyVinHeader= findViewById(R.id.verifyVinHeader);
        verifyActions  = findViewById(R.id.verifyActions);
        verifyIcon     = findViewById(R.id.verifyIcon);
        btnVinMatch    = findViewById(R.id.btnVinMatch);
        btnVinNoMatch  = findViewById(R.id.btnVinNoMatch);
        verifyVinValue = findViewById(R.id.verifyVinValue);
        noMatchGroup   = findViewById(R.id.noMatchGroup);
        enterVinLayout = findViewById(R.id.enterVinLayout);
        enterVinInput  = findViewById(R.id.enterVinInput);
        cbVinNotified  = findViewById(R.id.cbVinNotified);
        btnVinUpdate   = findViewById(R.id.btnVinUpdate);
        btnCamera      = findViewById(R.id.btnCamera);

        // MILEAGE
        mileagePanel       = findViewById(R.id.mileagePanel);
        mileageHeader      = findViewById(R.id.mileageHeader);
        mileageGroup       = findViewById(R.id.mileageGroup);
        mileageIcon        = findViewById(R.id.mileageIcon);
        enterMileageLayout = findViewById(R.id.enterMileageLayout);
        enterMileageInput  = findViewById(R.id.enterMileageInput);
        btnMileageCamera   = findViewById(R.id.btnMileageCamera);
        btnMileageUpdate   = findViewById(R.id.btnMileageUpdate);

        // KEY CHECK
        keyPanel            = findViewById(R.id.keyPanel);
        keyHeader           = findViewById(R.id.keyHeader);
        keyGroup            = findViewById(R.id.keyGroup);
        keyIcon             = findViewById(R.id.keyIcon);
        cbFobs              = findViewById(R.id.cbFobs);
        cbNoKey             = findViewById(R.id.cbNoKey);
        enterKeyCountLayout = findViewById(R.id.enterKeyCountLayout);
        enterKeyCountInput  = findViewById(R.id.enterKeyCountInput);
        btnKeyCamera        = findViewById(R.id.btnKeyCamera);
        btnKeyUpdate        = findViewById(R.id.btnKeyUpdate);

        // INTAKE VIDEO
        videoPanel      = findViewById(R.id.videoPanel);
        videoHeader     = findViewById(R.id.videoHeader);
        videoGroup      = findViewById(R.id.videoGroup);
        videoIcon       = findViewById(R.id.videoIcon);
        videoPromptIcon = findViewById(R.id.videoPromptIcon);
        btnVideoAccept  = findViewById(R.id.btnVideoAccept);

        // DESCRIPTION
        descPanel        = findViewById(R.id.descPanel);
        descHeader       = findViewById(R.id.descHeader);
        descGroup        = findViewById(R.id.descGroup);
        descIcon         = findViewById(R.id.descIcon);
        descValue        = findViewById(R.id.descValue);
        btnDescCorrect   = findViewById(R.id.btnDescCorrect);
        btnDescIncorrect = findViewById(R.id.btnDescIncorrect);
        descIncorrectGroup = findViewById(R.id.descIncorrectGroup);
        cbIncorrectMileage = findViewById(R.id.cbIncorrectMileage);
        cbIncorrectVin     = findViewById(R.id.cbIncorrectVin);
        cbIncorrectDetails = findViewById(R.id.cbIncorrectDetails);
        cbSpellingErrors   = findViewById(R.id.cbSpellingErrors);
        btnDescUpdate      = findViewById(R.id.btnDescUpdate);

        // QUALITY
        qualityPanel        = findViewById(R.id.qualityPanel);
        qualityGroup        = findViewById(R.id.qualityGroup);
        qualityDetailsGroup = findViewById(R.id.qualityDetailsGroup);
        qualityIcon         = findViewById(R.id.qualityIcon);
        qualityMessage      = findViewById(R.id.qualityMessage);
        btnQualityNoConcerns= findViewById(R.id.btnQualityNoConcerns);
        btnQualityConcerns  = findViewById(R.id.btnQualityConcerns);
        btnQualityUpdate    = findViewById(R.id.btnQualityUpdate);
        cbExteriorDamage    = findViewById(R.id.cbExteriorDamage);
        cbInteriorDamage    = findViewById(R.id.cbInteriorDamage);
        cbTiresWheels       = findViewById(R.id.cbTiresWheels);
        cbMechanical        = findViewById(R.id.cbMechanical);
        cbServiceLights     = findViewById(R.id.cbServiceLights);

        // Confirm button
        btnConfirm = findViewById(R.id.btnConfirmIntake);
        btnConfirm.setEnabled(true);
        btnConfirm.setAlpha(1f);


// ===== Intent extras =====
        Intent in = getIntent();

// Preferred path: full vehicle model
        vehicle = in.getParcelableExtra(EXTRA_VEHICLE);

// Legacy fields (fallbacks if vehicle is null)
        lot         = in.getStringExtra(Nav.EXTRA_LOT);
        description = in.getStringExtra(Nav.EXTRA_DESC);
        shortDescription = in.getStringExtra("shortdesc"); // short desc
        eventName   = in.getStringExtra(Nav.EXTRA_EVENT_NAME);
        eventId     = in.getIntExtra(Nav.EXTRA_EVENT_ID, -1);
       // driver      = in.getStringExtra(Nav.EXTRA_DRIVER);
        if (driver == null || driver.trim().isEmpty()) {
            try {
                String fromSession = com.example.gt6driver.session.CurrentSelection.get().getDriverName();
                if (fromSession != null && !fromSession.trim().isEmpty()) driver = fromSession;
            } catch (Throwable ignored) {}
        }
        mode        = in.getStringExtra("mode"); // "check_in"
        vin         = in.getStringExtra(Nav.EXTRA_VIN);
        thumbUrl    = in.getStringExtra(Nav.EXTRA_THUMB);

// Opportunity Id (first from intent, else from vehicle)
        opportunityId = in.getStringExtra(EXTRA_OPPORTUNITY_ID);
        if ((opportunityId == null || opportunityId.trim().isEmpty()) && vehicle != null) {
            // Support both names on the model
            if (vehicle.opportunityId != null && !vehicle.opportunityId.isEmpty()) {
                opportunityId = vehicle.opportunityId;
            } else if (vehicle.crmopportunityid != null && !vehicle.crmopportunityid.isEmpty()) {
                opportunityId = vehicle.crmopportunityid;
            }
        }

// If we have a vehicle, prefer its values for header UI
        if (vehicle != null) {
            // lotnumber is String in your models
            String vLot     = vehicle.lotnumber != null ? vehicle.lotnumber : "";
            String vDesc  = safeStr(vehicle.marketingdescription);
            description   = vDesc;  // don't fallback to title/anything else
            String vVin     = vehicle.vin != null ? vehicle.vin : "";
            String vThumb   = !isEmpty(vehicle.thumbUrl) ? vehicle.thumbUrl
                    : (vehicle.tbuncpath != null ? vehicle.tbuncpath : "");

            // Use these as the working values the rest of the activity expects
            lot       = firstNonEmpty(vLot, lot);
            description = firstNonEmpty(vDesc, description);
            vin       = firstNonEmpty(vVin, vin);
            thumbUrl  = firstNonEmpty(vThumb, thumbUrl);
        }

// Vehicle header
        panelLot.setText("LOT # " + (lot != null ? lot : ""));
        panelDesc.setText(description != null ? description : "");
        if (panelVin != null) panelVin.setText("VIN: " + (vin != null ? vin : ""));

// Load vehicle thumbnail
        loadThumbIntoHeader();

        if (descValue != null) {
            descValue.setText(!TextUtils.isEmpty(shortDescription)
                    ? shortDescription
                    : "No description available.");
        }

        // Vehicle header
        panelLot.setText("LOT # " + (lot != null ? lot : ""));
        panelDesc.setText(description != null ? description : "");
        if (panelVin != null) panelVin.setText("VIN: " + (vin != null ? vin : ""));

        // Load vehicle thumbnail
        loadThumbIntoHeader();

        if (descValue != null) {
            descValue.setText(!TextUtils.isEmpty(description)
                    ? description
                    : "No description available.");
        }
        // ===== PHOTO launcher =====
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (pendingPhotoUri != null && pendingPhotoLabel != null) {
                            if (hasNonZeroSize(pendingPhotoUri)) {
                                try { getContentResolver().notifyChange(pendingPhotoUri, null); } catch (Exception ignored) {}

                                // ✅ NEW: map label -> blob URL and store on model
                                if (intakeModel == null) intakeModel = new VehicleTaskIntake();
                                String fileName;
                                switch (pendingPhotoLabel.toLowerCase()) {
                                    case "keycheck":
                                        fileName = "keycheck_intake.jpg";
                                        if (intakeModel.keyCheck == null) intakeModel.keyCheck = new VehicleTaskIntake.KeyCheck();
                                        intakeModel.keyCheck.photoUrl = mediaUrl(fileName);
                                        break;
                                    case "mileage":
                                        fileName = "mileage_intake.jpg";
                                        if (intakeModel.mileage == null) intakeModel.mileage = new VehicleTaskIntake.Mileage();
                                        intakeModel.mileage.photoUrl = mediaUrl(fileName);
                                        break;
                                    case "vin":
                                    default:
                                        fileName = "vin.jpg";
                                        if (intakeModel.vinVerify == null) intakeModel.vinVerify = new VehicleTaskIntake.VinVerify();
                                        intakeModel.vinVerify.photoUrl = mediaUrl(fileName);
                                        break;
                                }

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

        // ==== VIDEO launcher ====
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

                            // Copy the returned Uri into our deterministic MediaStore location
                            Uri target = createVideoUriForIntake(); // .../GT6/{consignmentId}/intake.mp4
                            if (target == null) {
                                Toast.makeText(this, "Unable to prepare storage for video.", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            if (!copyUri(source, target)) {
                                // Best-effort cleanup if copy failed
                                try { getContentResolver().delete(target, null, null); } catch (Exception ignored) {}
                                Toast.makeText(this, "Failed to save the video.", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            lastCapturedVideoUri = target;

                            try { getContentResolver().notifyChange(lastCapturedVideoUri, null); } catch (Exception ignored) {}

                            setStatusIcon(videoIcon, false);
                            videoDone = false;
                            setVideoExpanded(true);
                            Toast.makeText(this, "Video captured. Tap Accept to confirm.", Toast.LENGTH_SHORT).show();
                            refreshConfirmEnabled();

                        } else {
                            // user canceled from camera UI — nothing to do
                        }
                    } finally {
                        pendingVideoUri = null;      // not used in this flow, but keep state clean
                        pendingVideoCapture = false;
                    }
                }
        );


        // CAMERA permission
        requestCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        if (pendingVideoCapture) {
                            openVideoCamera();
                        } else {
                            if (pendingPhotoLabel == null) pendingPhotoLabel = "vin";
                            openCameraPhoto(pendingPhotoLabel);
                        }
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

        // VIN: camera button
        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> {
                vinNoMatchMode = true;
                setVinExpanded(true);
                hideKeyboard();
                ensureCameraForPhoto("vin");
            });
        }

        // VIN: panel toggle
        if (verifyVinHeader != null) verifyVinHeader.setOnClickListener(v -> toggleVinPanel());

        if (btnVinMatch != null) {
            btnVinMatch.setOnClickListener(v -> {
                vinMatched = true;

                // Ensure the UI shows the VIN we're about to persist
                String curVin = currentVinForApi();
                if (verifyVinValue != null) verifyVinValue.setText(curVin);

                // Persist into the model so the API call definitely gets this value
                if (intakeModel == null) intakeModel = new VehicleTaskIntake();
                if (intakeModel.vinVerify == null) intakeModel.vinVerify = new VehicleTaskIntake.VinVerify();
                intakeModel.vinVerify.newVin = curVin;          // <-- put current VIN into request body
                intakeModel.vinVerify.isMatched = true;         // explicit

                setStatusIcon(verifyIcon, true);
                vinNoMatchMode = false;
                vinDone = true;
                setVinExpanded(false);
                refreshConfirmEnabled();
            });
        }

        if (btnVinNoMatch != null) {
            btnVinNoMatch.setOnClickListener(v -> {
                vinMatched = false;
                setStatusIcon(verifyIcon, false);
                vinNoMatchMode = true;
                vinDone = false; // not done until UPDATE
                setVinExpanded(true);
                showKeyboardFor(enterVinInput);
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
                hideKeyboard();

                if (intakeModel == null) intakeModel = new VehicleTaskIntake();
                if (intakeModel.vinVerify == null) intakeModel.vinVerify = new VehicleTaskIntake.VinVerify();
                intakeModel.vinVerify.newVin = entered;
                intakeModel.vinVerify.isNotified = (cbVinNotified != null && cbVinNotified.isChecked());

                if (verifyVinValue != null) verifyVinValue.setText(entered);

                Toast.makeText(this, "VIN submitted: " + entered +
                                (cbVinNotified != null && cbVinNotified.isChecked() ? " • Specialist notified" : ""),
                        Toast.LENGTH_LONG).show();

                if (enterVinInput != null) enterVinInput.setText("");

                // After submitting a corrected VIN, the step is complete but it is still a mismatch.
                vinMatched = false;  // IMPORTANT: keep this false
                vinDone = true;      // step completed
                vinNoMatchMode = false;
                setStatusIcon(verifyIcon, true); // green = completed, not "matched"
                setVinExpanded(false);
                refreshConfirmEnabled();
            });
        }




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
                hideKeyboard();
                Toast.makeText(this, "Mileage saved: " + miles, Toast.LENGTH_SHORT).show();
                setStatusIcon(mileageIcon, true);
                mileageDone = true;
                setMileageExpanded(false);
                refreshConfirmEnabled();
            });
        }

        // KEY CHECK
        if (keyHeader != null) keyHeader.setOnClickListener(v -> toggleKeyPanel());
        if (btnKeyCamera != null) {
            btnKeyCamera.setOnClickListener(v -> {
                setKeyExpanded(true);
                hideKeyboard();
                ensureCameraForPhoto("keycheck");
            });
        }

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
                hideKeyboard();
                Toast.makeText(this, "Key check saved.", Toast.LENGTH_SHORT).show();
                setStatusIcon(keyIcon, true);
                keyDone = true;
                setKeyExpanded(false);
                refreshConfirmEnabled();
            });
        }

        if (cbFobs != null && cbNoKey != null) {
            CompoundButton.OnCheckedChangeListener keyMutualListener = (buttonView, isChecked) -> {
                if (isChangingKeyState) return;
                isChangingKeyState = true;

                if (buttonView == cbFobs && isChecked) {
                    cbNoKey.setChecked(false);
                } else if (buttonView == cbNoKey && isChecked) {
                    cbFobs.setChecked(false);
                }
                updateKeyCountEnabled();

                isChangingKeyState = false;
            };
            cbFobs.setOnCheckedChangeListener(keyMutualListener);
            cbNoKey.setOnCheckedChangeListener(keyMutualListener);
            updateKeyCountEnabled();
        }

        // VIDEO section
        if (videoHeader != null) videoHeader.setOnClickListener(v -> toggleVideoPanel());

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

                // ✅ NEW: persist Azure URL for the intake video
                if (intakeModel == null) intakeModel = new VehicleTaskIntake();
                if (intakeModel.video == null) intakeModel.video = new VehicleTaskIntake.VideoInfo();
                intakeModel.video.videoUrl = mediaUrl("intake.mp4");

                setStatusIcon(videoIcon, true);
                videoDone = true;
                Toast.makeText(this, "Intake video accepted.", Toast.LENGTH_SHORT).show();
                setVideoExpanded(false);
                refreshConfirmEnabled();
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
                descDone = true; // section completed
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

                // Persist into the working model FIRST (source of truth for API)
                VehicleTaskIntake.Description d = descModel();
                d.isCorrect = false;
                d.isIncorrectMileage  = incMileage;
                d.isInCorrectVin      = incVin;
                d.isIncorrectDetails  = incDetails;
                d.isIncorrectSpelling = incSpell;

                // UX wrap-up
                setStatusIcon(descIcon, true); // green = "section complete", not "correct"
                descDone = true;
                if (descIncorrectGroup != null) descIncorrectGroup.setVisibility(View.GONE);
                clearDescIncorrectChecks(); // safe to clear; we already persisted
                setDescExpanded(false);
                Toast.makeText(this, "Description update submitted.", Toast.LENGTH_SHORT).show();
                refreshConfirmEnabled();
            });
        }


        // QUALITY
        if (qualityPanel != null) {
            qualityPanel.setOnClickListener(v -> toggleQualityPanel());
        }
        if (btnQualityNoConcerns != null) {
            btnQualityNoConcerns.setOnClickListener(v -> {
                qualityHasConcerns = false;           // <-- explicit
                setStatusIcon(qualityIcon, true);
                qualityDone = true;
                setQualityExpanded(false);
                refreshConfirmEnabled();
            });
        }

        if (btnQualityConcerns != null) {
            btnQualityConcerns.setOnClickListener(v -> {
                qualityHasConcerns = true;            // <-- explicit
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

                qualityHasConcerns = true;            // still “concerns”
                setStatusIcon(qualityIcon, true);     // mark section ok
                qualityDone = true;                   // section complete
                setQualityExpanded(false);
                if (qualityDetailsGroup != null) qualityDetailsGroup.setVisibility(View.GONE);

                Toast.makeText(this, any ? "Quality concerns recorded."
                        : "No specific concerns checked.", Toast.LENGTH_SHORT).show();
                refreshConfirmEnabled();
            });
        }

        // CONFIRM -> PUT save
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                hideKeyboard();
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
                        btnConfirm.setEnabled(true);
                        btnConfirm.setAlpha(1f);
                    }
                    @Override public void onHttpError(int code, String message) {
                        Toast.makeText(CheckInDetailsActivity.this, "Save HTTP " + code + ": " + message, Toast.LENGTH_LONG).show();
                        btnConfirm.setEnabled(true);
                        btnConfirm.setAlpha(1f);
                    }
                });
            });
        }

        // Fire API load once UI is ready
        driverTaskRepo = new DriverTaskRepository();
        fetchIntakeAndBind();
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
    private static String firstNonEmpty(String a, String b) { return isEmpty(a) ? (b == null ? "" : b) : a; }

//
@androidx.annotation.Nullable
private Uri preferResultUri(@androidx.annotation.Nullable Intent data,
                            @androidx.annotation.Nullable Uri fallback) {
    return (data != null && data.getData() != null) ? data.getData() : fallback;
}

    private boolean awaitNonZeroSize(Uri uri) {
        for (int i = 0; i < 8; i++) { // ~2 seconds
            if (hasNonZeroSize(uri)) return true;
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        }
        return false;
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

    // ---------- Networking bind/load ----------

    private void fetchIntakeAndBind() {
        if (TextUtils.isEmpty(opportunityId)) {
            Toast.makeText(this, "Missing opportunityId", Toast.LENGTH_LONG).show();
            return;
        }
        setPanelsEnabled(false);
        driverTaskRepo.fetchIntake(opportunityId, new DriverTaskRepository.IntakeCallback() {
            @Override public void onSuccess(VehicleTaskIntake it) {
                intakeModel = (it != null ? it : new VehicleTaskIntake());
                if (it == null) {
                    Toast.makeText(CheckInDetailsActivity.this, "No intake data found.", Toast.LENGTH_SHORT).show();
                    setPanelsEnabled(true);
                    return;
                }
                bindIntakeToUi(it);
                setPanelsEnabled(true);
            }
            @Override public void onError(Throwable t) {
                Toast.makeText(CheckInDetailsActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                setPanelsEnabled(true);
            }
            @Override public void onHttpError(int code, String message) {
                Toast.makeText(CheckInDetailsActivity.this, "HTTP " + code + ": " + message, Toast.LENGTH_LONG).show();
                setPanelsEnabled(true);
            }
        });
    }
    private String getDisplayVin() {
        String fromModel = (intakeModel != null && intakeModel.vinVerify != null)
                ? safeStr(intakeModel.vinVerify.newVin) : "";
        if (!TextUtils.isEmpty(fromModel)) return fromModel;
        return vin != null ? vin : "";
    }
// Check to Make sure you actually took a photo

    private boolean hasVinPhoto() {
        return intakeModel != null &&
                intakeModel.vinVerify != null &&
                !TextUtils.isEmpty(intakeModel.vinVerify.photoUrl);
    }
    private boolean hasMileagePhoto() {
        return intakeModel != null &&
                intakeModel.mileage != null &&
                !TextUtils.isEmpty(intakeModel.mileage.photoUrl);
    }
    private boolean hasKeyPhoto() {
        return intakeModel != null &&
                intakeModel.keyCheck != null &&
                !TextUtils.isEmpty(intakeModel.keyCheck.photoUrl);
    }
//
    // Keep the persisted Description state in your working model
    private VehicleTaskIntake.Description descModel() {
        if (intakeModel == null) intakeModel = new VehicleTaskIntake();
        if (intakeModel.description == null) intakeModel.description = new VehicleTaskIntake.Description();
        return intakeModel.description;
    }

    private void bindIntakeToUi(VehicleTaskIntake it) {
        // VIN verify
        if (it.vinVerify != null) {
            vinMatched = it.vinVerify.isMatched;

            String newVin = safeStr(it.vinVerify.newVin);
            if (verifyVinValue != null) {
                String displayVin = !TextUtils.isEmpty(newVin) ? newVin : (vin != null ? vin : "");
                verifyVinValue.setText(displayVin);
            }
            if (cbVinNotified != null) {
                cbVinNotified.setChecked(Boolean.TRUE.equals(it.vinVerify.isNotified));
            }

            if (Boolean.TRUE.equals(vinMatched)) {
                // matched
                setStatusIcon(verifyIcon, true);
                vinDone = true;
                vinNoMatchMode = false;
                setVinExpanded(false);
            } else {
                // not matched (or null)
                if (!TextUtils.isEmpty(newVin)) {
                    // User (or server) provided a corrected VIN already → considered completed
                    setStatusIcon(verifyIcon, true);
                    vinDone = true;
                    vinNoMatchMode = true;
                    setVinExpanded(false);
                } else {
                    // needs user action
                    setStatusIcon(verifyIcon, false);
                    vinDone = false;
                    vinNoMatchMode = true;
                }
            }
        }
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
        }

        // Key check
        if (it.keyCheck != null) {
            Boolean hasKey = it.keyCheck.hasKey;
            Integer num    = it.keyCheck.numberOfKeys;

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
            }
            updateKeyCountEnabled();
        }

        // Description
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
                // Reasons already exist → consider the section complete
                setStatusIcon(descIcon, true);   // green = done
                descDone = true;
                setDescExpanded(false);
                if (descIncorrectGroup != null) descIncorrectGroup.setVisibility(View.GONE);

                // Optionally reflect server state in the checkboxes (even though hidden)
                if (cbIncorrectMileage != null) cbIncorrectMileage.setChecked(Boolean.TRUE.equals(it.description.isIncorrectMileage));
                if (cbIncorrectVin != null)     cbIncorrectVin.setChecked(Boolean.TRUE.equals(it.description.isInCorrectVin));
                if (cbSpellingErrors != null)   cbSpellingErrors.setChecked(Boolean.TRUE.equals(it.description.isIncorrectSpelling));
                if (cbIncorrectDetails != null) cbIncorrectDetails.setChecked(Boolean.TRUE.equals(it.description.isIncorrectDetails));
            } else {
                // No decision yet
                setStatusIcon(descIcon, false);
                descDone = false;
                if (descIncorrectGroup != null) descIncorrectGroup.setVisibility(View.VISIBLE);
                setDescExpanded(true);
            }
        }


// Quality
        if (it.quality != null) {
            qualityHasConcerns = it.quality.isConcerns;

            if (Boolean.FALSE.equals(it.quality.isConcerns)) {
                // No concerns
                setStatusIcon(qualityIcon, true);
                qualityDone = true;
                setQualityExpanded(false);
                if (qualityDetailsGroup != null) qualityDetailsGroup.setVisibility(View.GONE);
                clearQualityChecks(); // to match server state (no concerns)
            } else if (Boolean.TRUE.equals(it.quality.isConcerns)) {
                // Has concerns
                setStatusIcon(qualityIcon, false);
                // If server provided details, show them and mark NOT done until user confirms,
                // or mark done directly if you prefer. Here we mark done so Confirm can enable:
                qualityDone = true;
                setQualityExpanded(false);
                if (qualityDetailsGroup != null) qualityDetailsGroup.setVisibility(View.GONE);

                if (cbExteriorDamage != null) cbExteriorDamage.setChecked(Boolean.TRUE.equals(it.quality.isExteriorDamage));
                if (cbInteriorDamage != null) cbInteriorDamage.setChecked(Boolean.TRUE.equals(it.quality.isInteriorDamage));
                if (cbTiresWheels   != null) cbTiresWheels.setChecked(Boolean.TRUE.equals(it.quality.isTiresWheels));
                if (cbMechanical    != null) cbMechanical.setChecked(Boolean.TRUE.equals(it.quality.isMechanical));
                if (cbServiceLights != null) cbServiceLights.setChecked(Boolean.TRUE.equals(it.quality.isServiceLights));
            }
        }


        // Video
        if (it.video != null) {
            String url = safeStr(it.video.videoUrl);
            if (!TextUtils.isEmpty(url)) {
                setStatusIcon(videoIcon, true);
                videoDone = true;
            }
        }

        // (optional) it.checkInBy available if you want to display
        refreshConfirmEnabled();
    }

    // ---------- Build body for PUT ----------

    private VehicleTaskIntake buildIntakeFromUi() {
        VehicleTaskIntake body = (intakeModel != null ? intakeModel : new VehicleTaskIntake());
        body.opportunityId = opportunityId;

        // ---------------- VIN ----------------
        if (body.vinVerify == null) body.vinVerify = new VehicleTaskIntake.VinVerify();

        // Matched flag from UI
        body.vinVerify.isMatched = Boolean.TRUE.equals(vinMatched);

        // Always persist the VIN currently shown to the user (original or corrected)
        String currentVinShown = currentVinForApi(); // assumes helper from earlier message
        body.vinVerify.newVin = TextUtils.isEmpty(currentVinShown) ? "" : currentVinShown;

        // isNotified from checkbox
        body.vinVerify.isNotified = (cbVinNotified != null && cbVinNotified.isChecked());

        // Photo URL: only keep if we truly captured a VIN photo earlier; else blank
        if (TextUtils.isEmpty(body.vinVerify.photoUrl)) {
            // If intakeModel holds a captured vin photo, keep it; otherwise blank
            String capturedVinUrl = (intakeModel != null && intakeModel.vinVerify != null)
                    ? intakeModel.vinVerify.photoUrl : null;
            body.vinVerify.photoUrl = TextUtils.isEmpty(capturedVinUrl) ? "" : capturedVinUrl;
        }

        // ---------------- MILEAGE ----------------
        if (body.mileage == null) body.mileage = new VehicleTaskIntake.Mileage();

        String milesTxt = safe(enterMileageInput);
        if (!TextUtils.isEmpty(milesTxt)) {
            try {
                body.mileage.odometer = Integer.parseInt(milesTxt);
            } catch (NumberFormatException ignore) {
                // leave null if invalid
            }
        }

        // Photo URL: only if a mileage photo was truly captured; else blank
        if (TextUtils.isEmpty(body.mileage.photoUrl)) {
            String capturedMileageUrl = (intakeModel != null && intakeModel.mileage != null)
                    ? intakeModel.mileage.photoUrl : null;
            body.mileage.photoUrl = TextUtils.isEmpty(capturedMileageUrl) ? "" : capturedMileageUrl;
        }

        // ---------------- KEY CHECK ----------------
        if (body.keyCheck == null) body.keyCheck = new VehicleTaskIntake.KeyCheck();

        Boolean hasFobs = cbFobs != null && cbFobs.isChecked();
        Boolean noKey   = cbNoKey != null && cbNoKey.isChecked();
        if (Boolean.TRUE.equals(hasFobs)) {
            body.keyCheck.hasKey = true;
            String keyCount = safe(enterKeyCountInput);
            try {
                body.keyCheck.numberOfKeys = TextUtils.isEmpty(keyCount) ? null : Integer.valueOf(keyCount);
            } catch (NumberFormatException ignore) {
                body.keyCheck.numberOfKeys = null;
            }
        } else if (Boolean.TRUE.equals(noKey)) {
            body.keyCheck.hasKey = false;
            body.keyCheck.numberOfKeys = null;
        } // else leave as-is if neither selected

        // Photo URL: only if a key photo was truly captured; else blank
        if (TextUtils.isEmpty(body.keyCheck.photoUrl)) {
            String capturedKeyUrl = (intakeModel != null && intakeModel.keyCheck != null)
                    ? intakeModel.keyCheck.photoUrl : null;
            body.keyCheck.photoUrl = TextUtils.isEmpty(capturedKeyUrl) ? "" : capturedKeyUrl;
        }

        // ---------------- DESCRIPTION ----------------
// ---------------- DESCRIPTION ----------------
        if (body.description == null) body.description = new VehicleTaskIntake.Description();

        if (intakeModel != null && intakeModel.description != null) {
            VehicleTaskIntake.Description src = intakeModel.description;
            body.description.isCorrect           = Boolean.TRUE.equals(src.isCorrect);
            body.description.isIncorrectMileage  = Boolean.TRUE.equals(src.isIncorrectMileage);
            body.description.isInCorrectVin      = Boolean.TRUE.equals(src.isInCorrectVin);
            body.description.isIncorrectSpelling = Boolean.TRUE.equals(src.isIncorrectSpelling);
            body.description.isIncorrectDetails  = Boolean.TRUE.equals(src.isIncorrectDetails);
        } else {
            // Default if user never interacted
            body.description.isCorrect           = true;
            body.description.isIncorrectMileage  = false;
            body.description.isInCorrectVin      = false;
            body.description.isIncorrectSpelling = false;
            body.description.isIncorrectDetails  = false;
        }


        // ---------------- QUALITY ----------------
        if (body.quality == null) body.quality = new VehicleTaskIntake.Quality();
        if (Boolean.TRUE.equals(qualityHasConcerns)) {
            body.quality.isConcerns      = true;
            body.quality.isExteriorDamage= cbExteriorDamage != null && cbExteriorDamage.isChecked();
            body.quality.isInteriorDamage= cbInteriorDamage != null && cbInteriorDamage.isChecked();
            body.quality.isTiresWheels   = cbTiresWheels  != null && cbTiresWheels.isChecked();
            body.quality.isServiceLights = cbServiceLights!= null && cbServiceLights.isChecked();
            body.quality.isMechanical    = cbMechanical   != null && cbMechanical.isChecked();
        } else {
            body.quality.isConcerns      = false;
            body.quality.isExteriorDamage= false;
            body.quality.isInteriorDamage= false;
            body.quality.isTiresWheels   = false;
            body.quality.isServiceLights = false;
            body.quality.isMechanical    = false;
        }

        // ---------------- VIDEO ----------------
        if (body.video == null) body.video = new VehicleTaskIntake.VideoInfo();
        // Keep whatever was set when user accepted the intake video; otherwise blank
        if (TextUtils.isEmpty(body.video.videoUrl)) {
            String capturedVideoUrl = (intakeModel != null && intakeModel.video != null)
                    ? intakeModel.video.videoUrl : null;
            body.video.videoUrl = TextUtils.isEmpty(capturedVideoUrl) ? "" : capturedVideoUrl;
        }

        // ---------------- OPTIONAL ----------------
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

    private void toggleVinPanel() { setVinExpanded(!vinExpanded); }
    private void setVinExpanded(boolean expanded) {
        vinExpanded = expanded;
        if (verifyVinValue != null) {
            verifyVinValue.setText(getDisplayVin()); // <- use newVin if present
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
    /** Returns "GT6/{consignmentid}" or "GT6/unknown" if not available. */
    private String consignmentSubdir() {
        String id = "unknown";
        if (vehicle != null && vehicle.consignmentid != null) {
            id = String.valueOf(vehicle.consignmentid);
        }
        return "GT6/" + id;
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
    // Open Video Camera
    private void openVideoCamera() {
        // Do NOT pre-create a Uri and do NOT set EXTRA_OUTPUT.
        // This lets the OEM camera show the OK/CANCEL review UI.
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);

        // These extras can also suppress review on some devices—omit them for best chance of OK/CANCEL
        // intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 120);
        // intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 200 * 1024 * 1024L);

        if (intent.resolveActivity(getPackageManager()) != null) {
            recordVideoLauncher.launch(intent);
        } else {
            Toast.makeText(this, "No video recorder available.", Toast.LENGTH_SHORT).show();
        }
    }
// Read VIN from UI
    /** The VIN we are currently showing to the user (prefers corrected/new VIN if present). */
    private String currentVinForApi() {
        String ui = (verifyVinValue != null && verifyVinValue.getText() != null)
                ? verifyVinValue.getText().toString().trim()
                : "";
        if (!TextUtils.isEmpty(ui)) return ui;
        return (vin != null ? vin.trim() : "");
    }

    private String safe(TextInputEditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view == null) view = new View(this);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showKeyboardFor(View view) {
        if (view == null) return;
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private void refreshConfirmEnabled() {
        if (btnConfirm == null) return;
        // Always allow confirm; individual sections are optional
        btnConfirm.setEnabled(true);
        btnConfirm.setAlpha(1f);
    }


    private void setPanelsEnabled(boolean enabled) {
        float alpha = enabled ? 1f : 0.6f;
        if (verifyVinPanel != null) verifyVinPanel.setAlpha(alpha);
        if (mileagePanel  != null)  mileagePanel.setAlpha(alpha);
        if (keyPanel      != null)  keyPanel.setAlpha(alpha);
        if (videoPanel    != null)  videoPanel.setAlpha(alpha);
        if (descPanel     != null)  descPanel.setAlpha(alpha);
        if (qualityPanel  != null)  qualityPanel.setAlpha(alpha);

        setGroupEnabled(verifyVinPanel, enabled);
        setGroupEnabled(mileagePanel, enabled);
        setGroupEnabled(keyPanel, enabled);
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

    private Uri createVideoUriForIntake() {
        String fileName = "intake.mp4";
        String subdir   = consignmentSubdir(); // "GT6/{consignmentid}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + subdir);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1); // ← important
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



    // Photos -> Pictures/gt6/{lot}/intake

    // Photos -> Pictures/GT6/{consignmentid}/{label}.jpg   (label = "vin", "mileage", "keycheck")
// Photos -> Pictures/GT6/{consignmentid}/{name}.jpg
// where name is: vin.jpg, keycheck_intake.jpg, mileage_intake.jpg
    private Uri createIntakePhotoUri(String label) {
        // Map labels to the exact filenames you want
        String baseName;
        if ("keycheck".equalsIgnoreCase(label)) {
            baseName = "keycheck_intake";
        } else if ("mileage".equalsIgnoreCase(label)) {
            baseName = "mileage_intake";
        } else {
            // default: keep original label, e.g., "vin"
            baseName = (label == null || label.trim().isEmpty()) ? "photo" : label.trim();
        }

        String fileName = baseName + ".jpg";
        String subdir   = consignmentSubdir(); // "GT6/{consignmentid}"

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
        pendingPhotoUri = createIntakePhotoUri(label);
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
// resolve Driver Name for API
    private String resolveDriverName() {
        // Prefer what Lookup/Main passed via Intent
        String fromIntent = driver;
        if (fromIntent != null && !fromIntent.trim().isEmpty()) return fromIntent.trim();

        // Fallback to process-scoped selection (if you added the session helper)
        try {
            String fromSession = com.example.gt6driver.session.CurrentSelection.get().getDriverName();
            if (fromSession != null && !fromSession.trim().isEmpty()) return fromSession.trim();
        } catch (Throwable ignored) {}

        return "";
    }

    // Small utils
    private boolean anyTrue(Boolean... arr) {
        if (arr == null) return false;
        for (Boolean b : arr) if (Boolean.TRUE.equals(b)) return true;
        return false;
    }
    // returns just the numeric/string id (no GT6 prefix)
    private String consignmentIdStr() {
        if (vehicle != null && vehicle.consignmentid != null) {
            return String.valueOf(vehicle.consignmentid);
        }
        return "unknown";
    }

    private String mediaUrl(String fileName) {
        // driver/{consignmentId}/{fileName}
        return BLOB_BASE + consignmentIdStr() + "/" + fileName;
    }
    private String safeStr(String s) { return s == null ? "" : s; }
}
















