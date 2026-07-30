package com.example.gt6driver.sync;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.gt6driver.StorageConfig;
import com.example.gt6driver.util.DeviceInfo;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class MediaUploadWorker extends Worker {
    private static final int FG_ID = 4442;
    private static final String FG_CHANNEL = "gt6_uploads";
    private static final String TAG = "GT6-Worker";

    public static final String KEY_SAS = "sasQuery";
    public static final String KEY_CONTAINER_URL = "containerUrl";
    public static final String KEY_RECURSIVE = "recursive";
    public static final String KEY_PREFIX = "prefix";

    public static final String PROGRESS_TOTAL = "progress_total";
    public static final String PROGRESS_UPLOADED = "progress_uploaded";
    public static final String PROGRESS_PERCENT = "progress_percent";

    private static final String DEFAULT_PREFIX = "";

    private static final long MIN_VIDEO_MS = 60_000L;

    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static Object lockFor(String key) { return LOCKS.computeIfAbsent(key, k -> new Object()); }

    public MediaUploadWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        setForegroundAsync(makeForegroundInfo("GT6: uploading media..."));

        final String sasInputParam       = getInputData().getString(KEY_SAS);
        final String containerInputParam = getInputData().getString(KEY_CONTAINER_URL);
        String prefix                    = getInputData().getString(KEY_PREFIX);

        final String sasPref       = GT6MediaSync.getSas(getApplicationContext());
        final String containerPref = GT6MediaSync.getContainerUrl(getApplicationContext());

        final String sasRaw = !TextUtils.isEmpty(sasInputParam) ? sasInputParam : sasPref;
        final String containerRaw = !TextUtils.isEmpty(containerInputParam) ? containerInputParam : containerPref;
        final String defaultContainerUrl = StorageConfig.driverContainerUrl();

        Log.i(TAG, "CFG SOURCE: inputUrl=" + containerInputParam + " prefUrl=" + containerPref +
                " defaultUrl=" + defaultContainerUrl);

        if (TextUtils.isEmpty(sasRaw)) {
            Log.e(TAG, "Missing SAS");
            return Result.failure(new Data.Builder().putString("error", "Missing SAS").build());
        }

        final String containerUrl = normalizeContainerUrl(
                TextUtils.isEmpty(containerRaw)
                        ? defaultContainerUrl
                        : containerRaw
        );

        prefix = trimSlashes(TextUtils.isEmpty(prefix) ? DEFAULT_PREFIX : prefix);

        try {
            Uri u = Uri.parse(containerUrl);
            String host = (u != null) ? u.getHost() : "<?>";
            Log.i(TAG, "Worker cfg: host=" + host +
                    " containerUrl=" + containerUrl +
                    " sasHints=" + (sasRaw.contains("si=") ? "storedPolicy" : "adhoc"));
        } catch (Exception e) {
            Log.e(TAG, "Worker cfg logging failed: " + e.getMessage());
        }

        final AzureUploader uploader = new AzureUploader(sasRaw);
        final ContentResolver cr = getApplicationContext().getContentResolver();

        int totalImages = countRows(cr, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false);
        int totalVideos = countRows(cr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true);
        ProgressState progressState = new ProgressState(totalImages + totalVideos);
        updateProgress(progressState);

        boolean okImages = processTable(
                cr,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/jpeg",
                uploader,
                containerUrl,
                prefix,
                false,
                progressState
        );

        boolean okVideos = processTable(
                cr,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "video/mp4",
                uploader,
                containerUrl,
                prefix,
                true,
                progressState
        );

        GT6MediaSync.enqueueContentTriggers(getApplicationContext());

        return (okImages && okVideos) ? Result.success() : Result.retry();
    }

    private boolean processTable(ContentResolver cr,
                                 Uri table,
                                 String defaultMime,
                                 AzureUploader uploader,
                                 String containerUrl,
                                 String prefix,
                                 boolean isVideo,
                                 ProgressState progressState) {

        String like1 = isVideo ? "Movies/GT6/%" : "Pictures/GT6/%";
        String like2 = isVideo ? "Movies/gt6/%" : "Pictures/gt6/%";
        String sel   = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR " +
                MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = new String[]{ like1, like2 };

        String[] projection = new String[] {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                "owner_package_name"
        };

        Log.i(TAG, (isVideo ? "[VIDEO]" : "[IMAGE]") +
                " scanning sel=(" + sel + ") args=[" + args[0] + "," + args[1] + "]");

        boolean allOk = true;

        try (Cursor c = cr.query(table, projection, sel, args,
                MediaStore.MediaColumns.DATE_ADDED + " DESC")) {

            if (c != null) {
                Log.i(TAG, (isVideo ? "[VIDEO]" : "[IMAGE]") +
                        " MediaStore query found " + c.getCount() + " rows.");

                while (c.moveToNext()) {
                    if (!uploadRow(cr, table, c, defaultMime, uploader, containerUrl, prefix, isVideo, progressState)) {
                        allOk = false;
                    }
                }
            } else {
                Log.w(TAG, (isVideo ? "[VIDEO]" : "[IMAGE]") + " query returned null cursor");
            }
        } catch (Exception e) {
            Log.e(TAG, "Primary query failed: " + e.getMessage());
            allOk = false;
        }

        return allOk;
    }

    private boolean uploadRow(ContentResolver cr,
                              Uri baseTable,
                              Cursor c,
                              String defaultMime,
                              AzureUploader uploader,
                              String containerUrl,
                              String prefix,
                              boolean isVideo,
                              ProgressState progressState) {

        long id        = c.getLong(0);
        String name    = safe(c.getString(1));
        String relPath = safe(c.getString(2));
        long size      = c.getLong(3);
        String mime    = safe(c.getString(4));
        if (TextUtils.isEmpty(mime)) mime = defaultMime;

        Uri itemUri = ContentUris.withAppendedId(baseTable, id);

        String consignmentId = parseConsignmentId(relPath);
        if (TextUtils.isEmpty(consignmentId)) {
            Log.w(TAG, (isVideo ? "[VIDEO]" : "[IMAGE]") +
                    " skip (no consignmentId) relPath=" + relPath + " name=" + name);
            progressState.incrementProcessed();
            updateProgress(progressState);
            return true;
        }

        if (TextUtils.isEmpty(name)) {
            name = isVideo ? "release.mp4" : "photo.jpg";
        }

        String blobPath = joinPath(prefix, consignmentId, name);

        Object lock = lockFor(blobPath);
        synchronized (lock) {

            // Validate video duration before upload
            if (isVideo) {
                long durationMs = getVideoDurationMs(cr, itemUri);
                Log.i(TAG, "[VIDEO] durationMs=" + durationMs + " name=" + name + " uri=" + itemUri);

                if (durationMs > 0 && durationMs < MIN_VIDEO_MS) {
                    Log.w(TAG, "[VIDEO] skip upload; too short (" + durationMs + " ms): " + itemUri);
                    progressState.incrementProcessed();
                    updateProgress(progressState);
                    return true;
                }
            }

            HashMap<String, String> meta = buildDefaultMeta(consignmentId, name, relPath);

            Uri sidecarUri = null;
            if (isVideo) {
                String base = baseNameNoExt(name);
                String sidecarName = base + ".meta.json";

                sidecarUri = findSidecarInDownloads(cr, sidecarName, consignmentId);
                if (sidecarUri != null) {
                    HashMap<String, String> sidecarMeta = readSidecarJson(cr, sidecarUri);
                    if (sidecarMeta != null && !sidecarMeta.isEmpty()) {
                        meta.putAll(sidecarMeta);
                        Log.i(TAG, "Sidecar found; metadata overridden from " + sidecarName);
                    } else {
                        Log.w(TAG, "Sidecar found but empty/unreadable: " + sidecarUri);
                    }
                } else {
                    Log.i(TAG, "No sidecar found for " + sidecarName + "; using defaults.");
                }
            }

            Log.i(TAG, "Uploading → " + containerUrl + "/" + blobPath +
                    " (mime=" + mime + ", size=" + size + ")");

            try (InputStream in = cr.openInputStream(itemUri)) {
                if (in == null) {
                    Log.w(TAG, "InputStream null for " + itemUri);
                    return true;
                }

                long contentLen = size > 0 ? size : guessLength(cr, itemUri);

                // Always upload latest version; do not skip just because blob exists
                uploader.putBlobAuto(containerUrl, blobPath, mime, contentLen, in, meta);

                tryDelete(getApplicationContext(), cr, itemUri);

                if (sidecarUri != null) tryDelete(getApplicationContext(), cr, sidecarUri);

                Log.i(TAG, "Uploaded; delete attempted: " + itemUri +
                        (sidecarUri != null ? (" + sidecar " + sidecarUri) : ""));
                progressState.incrementProcessed();
                updateProgress(progressState);
                return true;

            } catch (FileNotFoundException e) {
                Log.w(TAG, "File not found: " + itemUri);
                progressState.incrementProcessed();
                updateProgress(progressState);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Upload failed; will retry. " + e.getMessage());
                progressState.incrementProcessed();
                updateProgress(progressState);
                return false;
            }
        }
    }

    private int countRows(ContentResolver cr, Uri table, boolean isVideo) {
        String like1 = isVideo ? "Movies/GT6/%" : "Pictures/GT6/%";
        String like2 = isVideo ? "Movies/gt6/%" : "Pictures/gt6/%";
        String sel = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR " +
                MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = new String[]{like1, like2};

        try (Cursor c = cr.query(table, new String[]{MediaStore.MediaColumns._ID}, sel, args, null)) {
            return c != null ? c.getCount() : 0;
        } catch (Exception e) {
            Log.w(TAG, "Count query failed", e);
            return 0;
        }
    }

    private void updateProgress(ProgressState progressState) {
        int total = Math.max(0, progressState.total);
        int processed = Math.max(0, Math.min(progressState.processed, total));
        int percent = total <= 0 ? 0 : (int) Math.round((processed * 100.0) / total);
        setProgressAsync(new Data.Builder()
                .putInt(PROGRESS_TOTAL, total)
                .putInt(PROGRESS_UPLOADED, processed)
                .putInt(PROGRESS_PERCENT, percent)
                .build());
        setForegroundAsync(makeForegroundInfo(total > 0
                ? ("GT6: uploading media... " + percent + "% (" + processed + "/" + total + ")")
                : "GT6: uploading media..."));
    }

    private static class ProgressState {
        final int total;
        int processed;

        ProgressState(int total) {
            this.total = total;
            this.processed = 0;
        }

        void incrementProcessed() {
            processed++;
        }
    }

    private HashMap<String, String> buildDefaultMeta(String consignmentId, String filename, String relPath) {
        HashMap<String, String> meta = new HashMap<>();
        String nowUtc = java.time.Instant.now().toString();
        meta.put("createdat", nowUtc);
        meta.put("recordedat", nowUtc);
        meta.put("driver", "Upload Agent");
        meta.put("device", DeviceInfo.getDeviceName(getApplicationContext()));
        meta.put("consignmentid", consignmentId);
        meta.put("filename", filename);
        meta.put("relpath", relPath);
        return meta;
    }

    private Uri findSidecarInDownloads(ContentResolver cr, String sidecarName, String consignmentId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;

        Uri table = MediaStore.Downloads.EXTERNAL_CONTENT_URI;

        String[] proj = new String[] {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH
        };

        String relPath = android.os.Environment.DIRECTORY_DOWNLOADS + "/GT6/" + consignmentId + "/";
        String sel = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                MediaStore.MediaColumns.RELATIVE_PATH + "=?";

        try (Cursor c = cr.query(table, proj, sel, new String[]{sidecarName, relPath},
                MediaStore.MediaColumns.DATE_ADDED + " DESC")) {

            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                return ContentUris.withAppendedId(table, id);
            }
        } catch (Exception e) {
            Log.w(TAG, "Sidecar lookup failed: " + e.getMessage());
        }
        return null;
    }

    private HashMap<String, String> readSidecarJson(ContentResolver cr, Uri sidecarUri) {
        try (InputStream in = cr.openInputStream(sidecarUri)) {
            if (in == null) return null;

            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);

            JSONObject o = new JSONObject(sb.toString());
            HashMap<String, String> meta = new HashMap<>();

            putIfPresent(meta, "createdat", o.optString("createdAt", null));
            putIfPresent(meta, "createdat", o.optString("createdat", null));
            putIfPresent(meta, "recordedat", o.optString("recordedAt", null));
            putIfPresent(meta, "recordedat", o.optString("recordedat", null));
            putIfPresent(meta, "consignmentid", o.optString("consignmentId", null));
            putIfPresent(meta, "consignmentid", o.optString("consignmentid", null));
            putIfPresent(meta, "device", o.optString("device", null));
            putIfPresent(meta, "driver", o.optString("driver", null));
            putIfPresent(meta, "lot", o.optString("lot", null));

            return meta;

        } catch (Exception e) {
            Log.w(TAG, "Sidecar read failed: " + e.getMessage());
            return null;
        }
    }

    private static void putIfPresent(HashMap<String, String> meta, String key, String value) {
        if (meta == null) return;
        if (TextUtils.isEmpty(key)) return;
        if (value == null) return;
        String v = value.trim();
        if (v.isEmpty() || "null".equalsIgnoreCase(v)) return;
        meta.put(key.toLowerCase(Locale.US), v);
    }

    private static String baseNameNoExt(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(0, dot) : filename;
    }

    private long getVideoDurationMs(ContentResolver cr, Uri uri) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(getApplicationContext(), uri);
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

    private static boolean azureBlobExistsReflect(AzureUploader uploader, String containerUrl, String blobPath) throws Exception {
        try {
            Method m = uploader.getClass().getMethod("blobExists", String.class, String.class);
            Object res = m.invoke(uploader, containerUrl, blobPath);
            if (res instanceof Boolean) return (Boolean) res;
        } catch (NoSuchMethodException nsme) {}
        return false;
    }

    private static long guessLength(ContentResolver cr, Uri uri) {
        try (Cursor cur = cr.query(uri, new String[]{MediaStore.MediaColumns.SIZE}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                return cur.getLong(0);
            }
        } catch (Exception ignore) {}
        return -1;
    }

    private static void tryDelete(Context ctx, ContentResolver cr, Uri uri) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30 && ctx != null) {
                String myPkg = ctx.getPackageName();
                String owner = null;
                try (Cursor cur = cr.query(uri, new String[]{"owner_package_name"}, null, null, null)) {
                    if (cur != null && cur.moveToFirst()) owner = cur.getString(0);
                } catch (Exception ignored) {}

                if (owner != null && !myPkg.equals(owner)) {
                    Log.w(TAG, "Skip delete (not owner). owner=" + owner + " uri=" + uri);
                    return;
                }
            }

            int n = cr.delete(uri, null, null);
            if (n <= 0) {
                Log.w(TAG, "Delete returned 0 for " + uri);
            } else {
                Log.i(TAG, "Deleted local: rows=" + n + " uri=" + uri);
            }
        } catch (SecurityException se) {
            Log.w(TAG, "Delete security exception for " + uri + ": " + se.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "Delete failed for " + uri + ": " + e.getMessage());
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String parseConsignmentId(String rel) {
        if (TextUtils.isEmpty(rel)) return null;
        int idx = rel.indexOf("GT6/");
        if (idx < 0) idx = rel.indexOf("gt6/");
        if (idx < 0) return null;
        String tail = rel.substring(idx + 4);
        int slash = tail.indexOf('/');
        return (slash > 0) ? tail.substring(0, slash) : tail;
    }

    private static String trimSlashes(String s) {
        if (s == null) return "";
        return s.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private static String joinPath(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            String t = trimSlashes(p);
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(t);
        }
        return sb.toString();
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.w(TAG, "Worker onStopped() called. isStopped=" + isStopped());
    }

    private ForegroundInfo makeForegroundInfo(String text) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    FG_CHANNEL, "GT6 Uploads", android.app.NotificationManager.IMPORTANCE_LOW);
            NotificationManagerCompat.from(getApplicationContext()).createNotificationChannel(ch);
        }

        android.app.Notification n = new NotificationCompat.Builder(getApplicationContext(), FG_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("GT6 Media Sync")
                .setContentText(text)
                .setOngoing(true)
                .build();

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            return new ForegroundInfo(
                    FG_ID,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            return new ForegroundInfo(FG_ID, n);
        }
    }

    private static String normalizeContainerUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        return url.replaceAll("/+$", "");
    }
}




