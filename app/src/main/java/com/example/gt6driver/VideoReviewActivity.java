package com.example.gt6driver;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.gt6driver.model.VehicleDetail;
import com.google.android.material.button.MaterialButton;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoReviewActivity extends AppCompatActivity {

    private static final String COMPRESSED_VIDEO_BASE =
            "https://stgt6driverappprod.blob.core.windows.net/compressed-files/";

    private TextView panelLot, panelDesc;
    private TextView panelVinValue;
    private TextView panelLocationName;
    private TextView panelRowCol;
    private ImageView panelImage;
    private MaterialButton btnCheckOut, btnCheckIn;

    private int eventId = -1;
    private String eventName = "";
    private String driver = "";
    private VehicleDetail vehicle;

    private String lotLegacy = "", descLegacy = "", vinLegacy = "", thumbLegacy = "";

    private String intakeVideoUrl = "";
    private String releaseVideoUrl = "";
    private boolean intakeVideoAvailable = false;
    private boolean releaseVideoAvailable = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService videoCheckExecutor = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_review);

        bindViews();
        readExtras(getIntent());
        populateHeader();
        prepareVideoButtons();
        wireButtons();
        checkVideoAvailability();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoCheckExecutor.shutdownNow();
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
                rowColText = defaulted(col, "-") + " - " + defaulted(row, "-");
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

    private void prepareVideoButtons() {
        intakeVideoUrl = firstNonEmpty(
                vehicle != null ? vehicle.intakevideo : null,
                compressedVideoUrl("intake")
        );
        releaseVideoUrl = firstNonEmpty(
                vehicle != null ? vehicle.releasevideo : null,
                compressedVideoUrl("release")
        );

        setVideoButtonState(btnCheckOut, false, "Checking intake video...");
        setVideoButtonState(btnCheckIn, false, "Checking release video...");
    }

    private void wireButtons() {
        btnCheckOut.setOnClickListener(v -> {
            if (!intakeVideoAvailable) {
                Toast.makeText(this, "Intake video not available.", Toast.LENGTH_SHORT).show();
                return;
            }
            playVideo("Intake Video", intakeVideoUrl);
        });

        btnCheckIn.setOnClickListener(v -> {
            if (!releaseVideoAvailable) {
                Toast.makeText(this, "Release video not available.", Toast.LENGTH_SHORT).show();
                return;
            }
            playVideo("Release Video", releaseVideoUrl);
        });
    }

    private void checkVideoAvailability() {
        checkVideoAvailability(intakeVideoUrl, true);
        checkVideoAvailability(releaseVideoUrl, false);
    }

    private void checkVideoAvailability(String url, boolean isIntake) {
        if (url == null || url.trim().isEmpty()) {
            updateVideoAvailability(isIntake, false);
            return;
        }

        videoCheckExecutor.execute(() -> {
            boolean exists = videoExists(url.trim());
            mainHandler.post(() -> updateVideoAvailability(isIntake, exists));
        });
    }

    private void updateVideoAvailability(boolean isIntake, boolean available) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (isIntake) {
            intakeVideoAvailable = available;
            setVideoButtonState(btnCheckOut, available,
                    available ? "INTAKE VIDEO" : "NO INTAKE VIDEO");
        } else {
            releaseVideoAvailable = available;
            setVideoButtonState(btnCheckIn, available,
                    available ? "RELEASE VIDEO" : "NO RELEASE VIDEO");
        }
    }

    private void setVideoButtonState(MaterialButton button, boolean enabled, String text) {
        if (button == null) {
            return;
        }
        button.setText(text);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1.0f : 0.45f);
        button.setClickable(enabled);
        button.setFocusable(enabled);
    }

    private boolean videoExists(String videoUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(videoUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 400) {
                return true;
            }

            // Some servers do not support HEAD correctly. Try a very small GET before deciding missing.
            if (responseCode == HttpURLConnection.HTTP_BAD_METHOD
                    || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                return videoExistsWithGet(videoUrl);
            }
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return false;
    }

    private boolean videoExistsWithGet(String videoUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(videoUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Range", "bytes=0-1");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_PARTIAL
                    || (responseCode >= 200 && responseCode < 400);
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void playVideo(String title, String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, title + " URL not found.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, title);
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, url.trim());
        startActivity(intent);
    }

    private String compressedVideoUrl(String baseNameNoExt) {
        String consignmentId = consignmentIdStr();
        if (consignmentId.isEmpty() || "unknown".equalsIgnoreCase(consignmentId)) {
            return "";
        }
        return COMPRESSED_VIDEO_BASE + consignmentId + "/" + baseNameNoExt + "_c.mp4";
    }

    private String consignmentIdStr() {
        if (vehicle != null && vehicle.consignmentid != null) {
            return String.valueOf(vehicle.consignmentid);
        }
        return "unknown";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String firstNonEmpty(String preferred, String fallback) {
        String p = safe(preferred);
        return p.isEmpty() ? safe(fallback) : p;
    }

    private static String defaulted(String s, String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s;
    }
}
