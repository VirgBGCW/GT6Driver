// app/src/main/java/com/example/gt6driver/ReleaseVideoActivity.java
package com.example.gt6driver;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;
import android.provider.MediaStore;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

public class ReleaseVideoActivity extends AppCompatActivity {

    // Inputs
    public static final String EXTRA_CONSIGNMENT_ID = "consignmentId";
    public static final String EXTRA_ENABLE_AUDIO   = "enableAudio";

    // Outputs (match CheckOutDetailsActivity)
    public static final String EXTRA_RESULT_VIDEO_URI = "extra_video_uri";      // Parcelable Uri
    public static final String EXTRA_RESULT_CANCELED  = "extra_video_canceled"; // boolean

    private static final String TAG = "GT6-ReleaseVideo";

    private PreviewView previewView;
    private MaterialButton btnRecordStop;
    private ImageButton btnClose;

    private Executor mainExecutor;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;

    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;

    private boolean isRecording = false;

    private String consignmentId;
    private boolean enableAudio;

    private ActivityResultLauncher<String[]> permsLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_release_video);

        previewView = findViewById(R.id.releasePreviewView);
        btnRecordStop = findViewById(R.id.btnRecordStop);
        btnClose = findViewById(R.id.btnClose);

        // ✅ keep bottom button above gesture/nav bar in portrait + landscape
        applySystemBarInsets();

        mainExecutor = ContextCompat.getMainExecutor(this);

        consignmentId = getIntent().getStringExtra(EXTRA_CONSIGNMENT_ID);
        if (consignmentId == null || consignmentId.trim().isEmpty()) consignmentId = "unknown";

        enableAudio = getIntent().getBooleanExtra(EXTRA_ENABLE_AUDIO, true);

        permsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                res -> {
                    boolean camOk = Boolean.TRUE.equals(res.get(Manifest.permission.CAMERA));
                    boolean micOk = !enableAudio || Boolean.TRUE.equals(res.get(Manifest.permission.RECORD_AUDIO));
                    if (camOk && micOk) {
                        startCamera();
                    } else {
                        Toast.makeText(this, "Camera/Mic permission required.", Toast.LENGTH_SHORT).show();
                        Intent data = new Intent();
                        data.putExtra(EXTRA_RESULT_CANCELED, true);
                        setResult(RESULT_CANCELED, data);
                        finish();
                    }
                }
        );

        btnClose.setOnClickListener(v -> {
            if (isRecording) stopRecording();
            Intent data = new Intent();
            data.putExtra(EXTRA_RESULT_CANCELED, true);
            setResult(RESULT_CANCELED, data);
            finish();
        });

        btnRecordStop.setOnClickListener(v -> {
            if (!isRecording) startRecording();
            else stopRecording();
        });

        requestPermsAndStart();
    }

    @SuppressWarnings("SameParameterValue")
    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private void applySystemBarInsets() {
        final View root = findViewById(android.R.id.content);

        // Capture the original margins once, so we don't "stack" insets repeatedly.
        final ViewGroup.MarginLayoutParams recordLp =
                (ViewGroup.MarginLayoutParams) btnRecordStop.getLayoutParams();
        final int baseBottomMargin = recordLp.bottomMargin; // from XML

        final ViewGroup.MarginLayoutParams closeLp =
                (ViewGroup.MarginLayoutParams) btnClose.getLayoutParams();
        final int baseTopMargin = closeLp.topMargin;
        final int baseRightMargin = closeLp.rightMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Record button: lift above nav/gesture bar
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) btnRecordStop.getLayoutParams();
            lp.bottomMargin = baseBottomMargin + bars.bottom;
            btnRecordStop.setLayoutParams(lp);

            // Close button: avoid status bar / cutout
            ViewGroup.MarginLayoutParams cp =
                    (ViewGroup.MarginLayoutParams) btnClose.getLayoutParams();
            cp.topMargin = baseTopMargin + bars.top;
            cp.rightMargin = baseRightMargin + bars.right;
            btnClose.setLayoutParams(cp);

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    private void requestPermsAndStart() {
        boolean camGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        boolean micGranted = !enableAudio || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;

        if (camGranted && micGranted) {
            startCamera();
            return;
        }

        if (enableAudio) {
            permsLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        } else {
            permsLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera init failed", e);
                Toast.makeText(this, "Camera init failed.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }, mainExecutor);
    }

    private void bindUseCases() {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build();

        videoCapture = VideoCapture.withOutput(recorder);

        cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                videoCapture
        );
    }

    private void startRecording() {
        if (videoCapture == null) {
            Toast.makeText(this, "Camera not ready.", Toast.LENGTH_SHORT).show();
            return;
        }

        MediaStoreOutputOptions out = buildOutputOptions();

        // ✅ PendingRecording is a top-level class in androidx.camera.video
        PendingRecording pending = videoCapture.getOutput().prepareRecording(this, out);
        if (enableAudio) pending = pending.withAudioEnabled();

        activeRecording = pending.start(mainExecutor, event -> {
            if (event instanceof VideoRecordEvent.Start) {
                isRecording = true;
                btnRecordStop.setText("STOP");
            } else if (event instanceof VideoRecordEvent.Finalize) {
                isRecording = false;
                btnRecordStop.setText("RECORD");

                VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;

                if (fin.hasError()) {
                    Log.e(TAG, "Recording finalize error=" + fin.getError());
                    Toast.makeText(this, "Recording failed.", Toast.LENGTH_SHORT).show();
                    activeRecording = null;

                    Intent data = new Intent();
                    data.putExtra(EXTRA_RESULT_CANCELED, true);
                    setResult(RESULT_CANCELED, data);
                    finish();
                    return;
                }

                Uri savedUri = fin.getOutputResults().getOutputUri();
                activeRecording = null;

                // If MediaStore returned null, treat as failure/cancel.
                if (savedUri == null) {
                    Toast.makeText(this, "Video saved but no Uri returned.", Toast.LENGTH_SHORT).show();
                    Intent data = new Intent();
                    data.putExtra(EXTRA_RESULT_CANCELED, true);
                    setResult(RESULT_CANCELED, data);
                    finish();
                    return;
                }

                // Return the Uri as a Parcelable extra (matches CheckOutDetailsActivity)
                Intent data = new Intent();
                data.putExtra(EXTRA_RESULT_VIDEO_URI, savedUri);
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }

    private void stopRecording() {
        try {
            if (activeRecording != null) activeRecording.stop();
        } catch (Exception ignored) {}
    }

    private MediaStoreOutputOptions buildOutputOptions() {
        final String fileName = "release.mp4";
        final String relPath = Environment.DIRECTORY_MOVIES + "/GT6/" + consignmentId + "/";

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath);

        long nowMs = System.currentTimeMillis();
        cv.put(MediaStore.MediaColumns.DATE_ADDED, nowMs / 1000L);
        cv.put(MediaStore.MediaColumns.DATE_MODIFIED, nowMs / 1000L);

        return new MediaStoreOutputOptions.Builder(
                getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(cv).build();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording) stopRecording();
    }
}


