package com.example.gt6driver;

import android.content.ContentResolver;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gt6driver.sync.GT6MediaSync;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URI = "video_uri";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_FORCE_LANDSCAPE = "force_landscape";
    public static final String EXTRA_RECORDED_AT = "recorded_at";

    private static final int OVERLAY_SHOW_AFTER_MS = 5_000;
    private static final int OVERLAY_HIDE_AFTER_MS = 35_000;

    private VideoView videoView;
    private TextView recordedAtOverlay;
    private String videoUriString = "";
    private String recordedAtDisplay = "";

    private final Handler overlayHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor();
    private boolean overlayTickerRunning = false;

    private final Runnable overlayTicker = new Runnable() {
        @Override public void run() {
            updateRecordedAtOverlayVisibility();
            if (overlayTickerRunning) {
                overlayHandler.postDelayed(this, 500L);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra(EXTRA_FORCE_LANDSCAPE, false)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        setContentView(R.layout.activity_video_player);

        videoView = findViewById(R.id.videoView);
        recordedAtOverlay = findViewById(R.id.recordedAtOverlay);

        String title = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);
        if (title != null && !title.trim().isEmpty()) {
            setTitle(title);
        } else {
            setTitle("Video Player");
        }

        videoUriString = safe(getIntent().getStringExtra(EXTRA_VIDEO_URI));
        if (videoUriString.isEmpty()) {
            Toast.makeText(this, "Video not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        recordedAtDisplay = formatRecordedAt(getIntent().getStringExtra(EXTRA_RECORDED_AT));
        if (!recordedAtDisplay.isEmpty()) {
            setRecordedAtOverlayText(recordedAtDisplay);
        }
        loadRecordedAtIfNeeded(videoUriString);

        Uri videoUri = Uri.parse(videoUriString);
        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);
        videoView.setVideoURI(videoUri);
        videoView.setOnPreparedListener(mp -> {
            mp.setOnInfoListener((mediaPlayer, what, extra) -> false);
            videoView.start();
            startOverlayTicker();
        });
        videoView.setOnCompletionListener(mp -> hideRecordedAtOverlay());
        videoView.setOnErrorListener((MediaPlayer mp, int what, int extra) -> {
            Toast.makeText(this, "Unable to play this video.", Toast.LENGTH_SHORT).show();
            return false;
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
        hideRecordedAtOverlay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopOverlayTicker();
        metadataExecutor.shutdownNow();
    }

    private void loadRecordedAtIfNeeded(String uriString) {
        if (!recordedAtDisplay.isEmpty()) return;

        metadataExecutor.execute(() -> {
            String rawRecordedAt = resolveRecordedAt(uriString);
            String display = formatRecordedAt(rawRecordedAt);
            if (!display.isEmpty()) {
                overlayHandler.post(() -> {
                    recordedAtDisplay = display;
                    setRecordedAtOverlayText(display);
                    updateRecordedAtOverlayVisibility();
                });
            }
        });
    }

    private String resolveRecordedAt(String uriString) {
        if (TextUtils.isEmpty(uriString)) return "";

        Uri uri = Uri.parse(uriString);
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            return recordedAtFromMediaStore(uri);
        }

        String remoteRecordedAt = recordedAtFromRemoteHeaders(uriString);
        if (!remoteRecordedAt.isEmpty()) return remoteRecordedAt;

        String originalBlobUrl = originalBlobUrlForCompressedVideo(uriString);
        if (!originalBlobUrl.isEmpty()) {
            return recordedAtFromRemoteHeaders(appendDriverSas(originalBlobUrl));
        }

        return "";
    }

    private String recordedAtFromRemoteHeaders(String uriString) {
        String recordedAt = readRecordedAtHeader(uriString, "HEAD");
        if (!recordedAt.isEmpty()) return recordedAt;
        return readRecordedAtHeader(uriString, "GET");
    }

    private String readRecordedAtHeader(String uriString, String method) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(uriString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            if ("GET".equals(method)) {
                connection.setRequestProperty("Range", "bytes=0-1");
            }
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 400) {
                return firstNonEmpty(
                        connection.getHeaderField("x-ms-meta-recordedat"),
                        connection.getHeaderField("x-ms-meta-recordedAt"),
                        connection.getHeaderField("x-ms-meta-recorded-at"),
                        connection.getHeaderField("x-ms-meta-createdat"),
                        connection.getHeaderField("x-ms-meta-createdAt"),
                        connection.getHeaderField("x-ms-meta-created-at"),
                        connection.getHeaderField("Last-Modified")
                );
            }
        } catch (Exception ignored) {
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return "";
    }

    private String recordedAtFromMediaStore(Uri uri) {
        ContentResolver resolver = getContentResolver();
        String[] projection = new String[] {
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED
        };

        try (Cursor c = resolver.query(uri, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                long dateTakenMs = getLong(c, MediaStore.Video.Media.DATE_TAKEN);
                if (dateTakenMs > 0L) return String.valueOf(dateTakenMs);

                long dateAddedSecs = getLong(c, MediaStore.Video.Media.DATE_ADDED);
                if (dateAddedSecs > 0L) return String.valueOf(dateAddedSecs * 1000L);

                long dateModifiedSecs = getLong(c, MediaStore.Video.Media.DATE_MODIFIED);
                if (dateModifiedSecs > 0L) return String.valueOf(dateModifiedSecs * 1000L);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static long getLong(Cursor c, String columnName) {
        int index = c.getColumnIndex(columnName);
        if (index < 0 || c.isNull(index)) return 0L;
        return c.getLong(index);
    }

    private String originalBlobUrlForCompressedVideo(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            String path = uri.getPath();
            if (TextUtils.isEmpty(path) || !path.contains("/compressed-files/")) {
                return "";
            }

            String suffix = path.substring(path.indexOf("/compressed-files/") + "/compressed-files/".length());
            if (suffix.endsWith("/intake_c.mp4")) {
                suffix = suffix.substring(0, suffix.length() - "/intake_c.mp4".length()) + "/intake.mp4";
            } else if (suffix.endsWith("/release_c.mp4")) {
                suffix = suffix.substring(0, suffix.length() - "/release_c.mp4".length()) + "/release.mp4";
            } else {
                return "";
            }

            return uri.buildUpon()
                    .encodedPath("/driver/" + suffix)
                    .query(null)
                    .fragment(null)
                    .build()
                    .toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String appendDriverSas(String uriString) {
        String sas = safe(GT6MediaSync.getSas(this));
        if (sas.isEmpty()) return uriString;
        if (sas.startsWith("?")) sas = sas.substring(1);
        return uriString + (uriString.contains("?") ? "&" : "?") + sas;
    }

    private void startOverlayTicker() {
        if (overlayTickerRunning) return;
        overlayTickerRunning = true;
        overlayHandler.post(overlayTicker);
    }

    private void stopOverlayTicker() {
        overlayTickerRunning = false;
        overlayHandler.removeCallbacks(overlayTicker);
    }

    private void updateRecordedAtOverlayVisibility() {
        if (recordedAtOverlay == null) return;

        if (recordedAtDisplay.isEmpty() || videoView == null) {
            hideRecordedAtOverlay();
            return;
        }

        int positionMs = videoView.getCurrentPosition();
        if (positionMs >= OVERLAY_SHOW_AFTER_MS && positionMs < OVERLAY_HIDE_AFTER_MS) {
            recordedAtOverlay.setVisibility(TextView.VISIBLE);
        } else {
            hideRecordedAtOverlay();
        }
    }

    private void hideRecordedAtOverlay() {
        if (recordedAtOverlay != null) {
            recordedAtOverlay.setVisibility(TextView.GONE);
        }
    }

    private void setRecordedAtOverlayText(String display) {
        if (recordedAtOverlay != null && !TextUtils.isEmpty(display)) {
            recordedAtOverlay.setText("Recorded: " + display);
        }
    }

    private static String formatRecordedAt(String raw) {
        String value = safe(raw);
        if (value.isEmpty()) return "";

        Date parsed = parseRecordedAt(value);
        if (parsed == null) return "";

        SimpleDateFormat out = new SimpleDateFormat("MM/dd/yyyy h:mma", Locale.US);
        return out.format(parsed).toLowerCase(Locale.US);
    }

    private static Date parseRecordedAt(String value) {
        if (value.matches("^\\d{10,}$")) {
            try {
                long epoch = Long.parseLong(value);
                if (epoch < 10_000_000_000L) epoch *= 1000L;
                return new Date(epoch);
            } catch (NumberFormatException ignored) {
            }
        }

        String normalized = value;
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1) + "+0000";
        } else if (normalized.matches(".*[+-]\\d{2}:\\d{2}$")) {
            normalized = normalized.substring(0, normalized.length() - 3)
                    + normalized.substring(normalized.length() - 2);
        }

        Date isoDate = parseWithUtc("yyyy-MM-dd'T'HH:mm:ss.SSSZ", normalized);
        if (isoDate != null) return isoDate;

        isoDate = parseWithUtc("yyyy-MM-dd'T'HH:mm:ssZ", normalized);
        if (isoDate != null) return isoDate;

        isoDate = parseWithUtc("EEE, dd MMM yyyy HH:mm:ss zzz", value);
        if (isoDate != null) return isoDate;

        return parseWithLocal("MM/dd/yyyy h:mma", value.toUpperCase(Locale.US));
    }

    private static Date parseWithUtc(String pattern, String value) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setLenient(false);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return parseFully(f, value);
    }

    private static Date parseWithLocal(String pattern, String value) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setLenient(false);
        return parseFully(f, value);
    }

    private static Date parseFully(SimpleDateFormat f, String value) {
        ParsePosition pos = new ParsePosition(0);
        Date date = f.parse(value, pos);
        return (date != null && pos.getIndex() == value.length()) ? date : null;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String safeValue = safe(value);
            if (!safeValue.isEmpty()) return safeValue;
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
