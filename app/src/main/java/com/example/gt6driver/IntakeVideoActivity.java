package com.example.gt6driver;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

public class IntakeVideoActivity extends AppCompatActivity {

    public static final String EXTRA_CONSIGNMENT_ID = "consignmentId";
    public static final String EXTRA_ENABLE_AUDIO   = "enableAudio";

    public static final String EXTRA_RESULT_VIDEO_URI = "extra_video_uri";
    public static final String EXTRA_RESULT_CANCELED  = "extra_video_canceled";

    private static final String TAG = "GT6-IntakeVideo";
    private static final long MIN_VALID_VIDEO_MS = 60_000L;

    private static final int PAUSE_COLOR_NORMAL = Color.parseColor("#455A64");
    private static final int PAUSE_COLOR_PAUSED = Color.parseColor("#FBC02D");

    private PreviewView previewView;
    private MaterialButton btnRecordStart;
    private MaterialButton btnPauseResume;
    private MaterialButton btnStopRecording;
    private MaterialButton btnPausedResumeFull;
    private LinearLayout recordingControlsRow;
    private View bottomControlsContainer;
    private ImageButton btnClose;

    private Executor mainExecutor;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;

    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;

    private boolean isRecording = false;
    private boolean isPaused = false;

    private ObjectAnimator pausedBlinkAnimator;

    private String consignmentId;
    private boolean enableAudio;

    private ActivityResultLauncher<String[]> permsLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_release_video);

        previewView   = findViewById(R.id.releasePreviewView);
        bottomControlsContainer = findViewById(R.id.bottomControlsContainer);
        btnRecordStart = findViewById(R.id.btnRecordStart);
        recordingControlsRow = findViewById(R.id.recordingControlsRow);
        btnPauseResume = findViewById(R.id.btnPauseResume);
        btnStopRecording = findViewById(R.id.btnStopRecording);
        btnPausedResumeFull = findViewById(R.id.btnPausedResumeFull);
        btnClose      = findViewById(R.id.btnClose);

        applySystemBarInsets();
        mainExecutor = ContextCompat.getMainExecutor(this);

        consignmentId = getIntent().getStringExtra(EXTRA_CONSIGNMENT_ID);
        if (consignmentId == null || consignmentId.trim().isEmpty()) consignmentId = "unknown";

        enableAudio = getIntent().getBooleanExtra(EXTRA_ENABLE_AUDIO, true);

        setPauseEnabled(false);
        updateRecordingControls();

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
            if (isRecording) {
                stopRecording();
                return;
            }
            Intent data = new Intent();
            data.putExtra(EXTRA_RESULT_CANCELED, true);
            setResult(RESULT_CANCELED, data);
            finish();
        });

        btnRecordStart.setOnClickListener(v -> startRecording());

        btnStopRecording.setOnClickListener(v -> stopRecording());

        View.OnClickListener pauseResumeClick = v -> {
            if (activeRecording == null || !isRecording) return;

            try {
                if (!isPaused) {
                    activeRecording.pause();
                } else {
                    activeRecording.resume();
                }
            } catch (Throwable t) {
                Log.w(TAG, "Pause/resume failed", t);
                Toast.makeText(this, "Pause/resume not supported on this device.", Toast.LENGTH_SHORT).show();
            }
        };

        btnPauseResume.setOnClickListener(pauseResumeClick);
        btnPausedResumeFull.setOnClickListener(pauseResumeClick);

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

        final ViewGroup.MarginLayoutParams controlsLp =
                (ViewGroup.MarginLayoutParams) bottomControlsContainer.getLayoutParams();
        final int baseBottomMargin = controlsLp.bottomMargin;

        final ViewGroup.MarginLayoutParams closeLp =
                (ViewGroup.MarginLayoutParams) btnClose.getLayoutParams();
        final int baseTopMargin = closeLp.topMargin;
        final int baseRightMargin = closeLp.rightMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) bottomControlsContainer.getLayoutParams();
            lp.bottomMargin = baseBottomMargin + bars.bottom;
            bottomControlsContainer.setLayoutParams(lp);

            ViewGroup.MarginLayoutParams cp =
                    (ViewGroup.MarginLayoutParams) btnClose.getLayoutParams();
            cp.topMargin = baseTopMargin + bars.top;
            cp.rightMargin = baseRightMargin + bars.right;
            btnClose.setLayoutParams(cp);

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    private void updateRecordingControls() {
        if (!isRecording) {
            btnRecordStart.setVisibility(View.VISIBLE);
            recordingControlsRow.setVisibility(View.GONE);
            btnPausedResumeFull.setVisibility(View.GONE);
            btnPauseResume.setEnabled(false);
            btnPauseResume.setAlpha(0.5f);
            return;
        }

        btnRecordStart.setVisibility(View.GONE);
        if (isPaused) {
            recordingControlsRow.setVisibility(View.GONE);
            btnPausedResumeFull.setVisibility(View.VISIBLE);
        } else {
            recordingControlsRow.setVisibility(View.VISIBLE);
            btnPausedResumeFull.setVisibility(View.GONE);
        }

        btnPauseResume.setEnabled(true);
        btnPauseResume.setAlpha(1f);
    }

    private void setPauseEnabled(boolean enabled) {
        btnPauseResume.setEnabled(enabled);
        btnPauseResume.setAlpha(enabled ? 1f : 0.5f);
        btnPausedResumeFull.setEnabled(enabled);
        btnPausedResumeFull.setAlpha(enabled ? 1f : 0.5f);

        if (!enabled) {
            stopPausedIndicator();
            btnPauseResume.setText("PAUSE");
            btnPauseResume.setBackgroundTintList(ColorStateList.valueOf(PAUSE_COLOR_NORMAL));
            btnPausedResumeFull.setText("PAUSED - PRESS TO RESUME");
            btnPausedResumeFull.setBackgroundTintList(ColorStateList.valueOf(PAUSE_COLOR_PAUSED));
            btnPausedResumeFull.setTextColor(Color.BLACK);
            updateRecordingControls();
        }
    }

    private void showPausedIndicator() {
        btnPausedResumeFull.setText("PAUSED - PRESS TO RESUME");
        btnPausedResumeFull.setBackgroundTintList(ColorStateList.valueOf(PAUSE_COLOR_PAUSED));
        btnPausedResumeFull.setTextColor(Color.BLACK);
        updateRecordingControls();

        if (pausedBlinkAnimator != null) {
            pausedBlinkAnimator.cancel();
        }

        pausedBlinkAnimator = ObjectAnimator.ofFloat(btnPausedResumeFull, "alpha", 1f, 0.35f, 1f);
        pausedBlinkAnimator.setDuration(700);
        pausedBlinkAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pausedBlinkAnimator.start();
    }

    private void stopPausedIndicator() {
        if (pausedBlinkAnimator != null) {
            pausedBlinkAnimator.cancel();
            pausedBlinkAnimator = null;
        }

        btnPauseResume.setAlpha(1f);
        btnPauseResume.setText("PAUSE");
        btnPauseResume.setTextColor(Color.WHITE);
        btnPauseResume.setBackgroundTintList(ColorStateList.valueOf(PAUSE_COLOR_NORMAL));
        btnPausedResumeFull.setAlpha(1f);
        btnPausedResumeFull.setText("PAUSED - PRESS TO RESUME");
        btnPausedResumeFull.setTextColor(Color.BLACK);
        btnPausedResumeFull.setBackgroundTintList(ColorStateList.valueOf(PAUSE_COLOR_PAUSED));
        updateRecordingControls();
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

        PendingRecording pending = videoCapture.getOutput().prepareRecording(this, out);
        if (enableAudio) pending = pending.withAudioEnabled();

        activeRecording = pending.start(mainExecutor, event -> {
            if (event instanceof VideoRecordEvent.Start) {
                isRecording = true;
                isPaused = false;
                setPauseEnabled(true);
                stopPausedIndicator();
                updateRecordingControls();
                btnClose.setEnabled(false);

            } else if (event instanceof VideoRecordEvent.Pause) {
                isPaused = true;
                showPausedIndicator();

            } else if (event instanceof VideoRecordEvent.Resume) {
                isPaused = false;
                stopPausedIndicator();

            } else if (event instanceof VideoRecordEvent.Finalize) {
                isRecording = false;
                isPaused = false;
                setPauseEnabled(false);
                stopPausedIndicator();
                updateRecordingControls();
                btnClose.setEnabled(true);

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

                if (savedUri == null) {
                    Toast.makeText(this, "Video saved but no Uri returned.", Toast.LENGTH_SHORT).show();
                    Intent data = new Intent();
                    data.putExtra(EXTRA_RESULT_CANCELED, true);
                    setResult(RESULT_CANCELED, data);
                    finish();
                    return;
                }

                long durationMs = getVideoDurationMs(savedUri);
                Log.i(TAG, "Saved intake video durationMs=" + durationMs + " uri=" + savedUri);

                if (durationMs > 0 && durationMs < MIN_VALID_VIDEO_MS) {
                    deleteUriQuietly(savedUri);
                    showTooShortVideoDialog(durationMs);
                    return;
                }

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
        final String fileName = "intake.mp4";
        final String relPath = Environment.DIRECTORY_MOVIES + "/GT6/" + consignmentId + "/";

        deleteExistingVideoRow(fileName, relPath);

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

    private void deleteExistingVideoRow(String fileName, String relPath) {
        try {
            String sel = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + "=?";
            int deleted = getContentResolver().delete(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    sel,
                    new String[]{fileName, relPath}
            );
            Log.i(TAG, "Deleted existing intake rows count=" + deleted);
        } catch (Exception e) {
            Log.w(TAG, "Failed deleting existing intake video row", e);
        }
    }

    private long getVideoDurationMs(Uri uri) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this, uri);
            String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (dur == null || dur.trim().isEmpty()) return -1L;
            return Long.parseLong(dur);
        } catch (Exception e) {
            Log.w(TAG, "Could not read video duration for uri=" + uri, e);
            return -1L;
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }

    private void deleteUriQuietly(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().delete(uri, null, null);
        } catch (Exception e) {
            Log.w(TAG, "Failed deleting short video uri=" + uri, e);
        }
    }

    private void showTooShortVideoDialog(long durationMs) {
        long seconds = Math.max(1L, durationMs / 1000L);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Video Too Short")
                .setMessage("The saved intake video was only " + seconds + " seconds. Please record again. Intake video must be longer than 1 minute.")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop called. recording=" + isRecording);
    }
}