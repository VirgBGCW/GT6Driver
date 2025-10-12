package com.example.gt6driver.sync;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MediaUploadWorker extends Worker {
    private static final int FG_ID = 4442;
    private static final String FG_CHANNEL = "gt6_uploads";
    private static final String TAG = "GT6-Worker";

    // === Keys expected from GT6MediaSync ===
    public static final String KEY_SAS = "sasQuery";                // SAS query (with or without leading '?')
    public static final String KEY_CONTAINER_URL = "containerUrl";  // e.g. https://acct.blob.core.windows.net/driver
    public static final String KEY_RECURSIVE = "recursive";         // (unused here, reserved)
    public static final String KEY_PREFIX = "prefix";               // optional blob path prefix (e.g., event/date)

    // === Defaults if inputs AND prefs are missing (last-ditch) ===
    private static final String DEFAULT_CONTAINER_BASE = "https://stgt6driverappprod.blob.core.windows.net";
    private static final String DEFAULT_CONTAINER_NAME = "driver";
    private static final String DEFAULT_PREFIX = "";

    // Per-blob in-process lock to prevent concurrent duplicate uploads
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static Object lockFor(String key) { return LOCKS.computeIfAbsent(key, k -> new Object()); }

    public MediaUploadWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Foreground (Android 14+ needs a type)
        setForegroundAsync(makeForegroundInfo("GT6: uploading media..."));

        // ---- Read inputs; if missing, fall back to GT6MediaSync (prefs); if still missing, use hard defaults ----
        final String sasInputParam       = getInputData().getString(KEY_SAS);
        final String containerInputParam = getInputData().getString(KEY_CONTAINER_URL);
        String prefix                    = getInputData().getString(KEY_PREFIX);

        // Also fetch prefs to help debug where values came from
        final String sasPref       = GT6MediaSync.getSas(getApplicationContext());
        final String containerPref = GT6MediaSync.getContainerUrl(getApplicationContext());

        // Choose effective values (inputs > prefs > defaults)
        final String sasRaw = !TextUtils.isEmpty(sasInputParam) ? sasInputParam : sasPref;
        final String containerRaw = !TextUtils.isEmpty(containerInputParam) ? containerInputParam : containerPref;

        Log.i(TAG, "CFG SOURCE: inputUrl=" + containerInputParam + " prefUrl=" + containerPref +
                " defaultUrl=" + (DEFAULT_CONTAINER_BASE + "/" + DEFAULT_CONTAINER_NAME));

        // Validate SAS (must exist from either input or prefs)
        if (TextUtils.isEmpty(sasRaw)) {
            Log.e(TAG, "Missing SAS");
            return Result.failure(new Data.Builder().putString("error", "Missing SAS").build());
        }

        // Normalize container URL (fallback to hard default only if prefs missing)
        final String containerUrl = normalizeContainerUrl(
                TextUtils.isEmpty(containerRaw)
                        ? (DEFAULT_CONTAINER_BASE + "/" + DEFAULT_CONTAINER_NAME)
                        : containerRaw
        );

        // Normalize prefix (may be empty)
        prefix = trimSlashes(TextUtils.isEmpty(prefix) ? DEFAULT_PREFIX : prefix);

        // Log the resolved config (do NOT print full SAS)
        try {
            Uri u = Uri.parse(containerUrl);
            String host = (u != null) ? u.getHost() : "<?>";
            Log.i(TAG, "Worker cfg: host=" + host +
                    " containerUrl=" + containerUrl +
                    " sasHints=" + (sasRaw.contains("si=") ? "storedPolicy" : "adhoc"));
        } catch (Exception e) {
            Log.e(TAG, "Worker cfg logging failed: " + e.getMessage());
        }

        // Build uploader (constructor will prepend '?' if missing)
        final AzureUploader uploader = new AzureUploader(sasRaw);
        final ContentResolver cr = getApplicationContext().getContentResolver();

        // Track consignmentIds we uploaded, so we can remove folders afterwards
        Set<String> touchedConsignments = new HashSet<>();

        boolean okImages = processTable(
                cr,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/jpeg",
                uploader,
                containerUrl,
                prefix,
                /*isVideo=*/false,
                touchedConsignments
        );

        boolean okVideos = processTable(
                cr,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "video/mp4",
                uploader,
                containerUrl,
                prefix,
                /*isVideo=*/true,
                touchedConsignments
        );

        // Only remove local folders when all uploads succeeded this pass
        if (okImages && okVideos) {
            for (String id : touchedConsignments) {
                try {
                    MediaCleanup.removeLocalConsignmentMedia(getApplicationContext(), id);
                    Log.i(TAG, "Local consignment media removed for id=" + id);
                } catch (Exception e) {
                    Log.w(TAG, "Cleanup failed for id=" + id + ": " + e.getMessage());
                }
            }
        }
// Re-arm content triggers so new captures continue to auto-upload
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
                                 Set<String> touchedConsignments) {

        // Primary query (your original folders)
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
                // API 30+; harmless on older (query will just omit)
                "owner_package_name"
        };

        Log.i(TAG, (isVideo ? "[VIDEO]" : "[IMAGE]") +
                " scanning sel=(" + sel + ") args=[" + args[0] + "," + args[1] + "]");

        int totalFound = 0;
        boolean allOk = true;

        try (Cursor c = cr.query(table, projection, sel, args,
                MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (c != null) {
                totalFound = c.getCount();
                Log.i(TAG, (isVideo ? "[VIDEO]" : "[IMAGE]") +
                        " MediaStore query found " + totalFound + " rows.");
                while (c.moveToNext()) {
                    if (!uploadRow(cr, table, c, defaultMime, uploader, containerUrl, prefix, isVideo, touchedConsignments)) {
                        allOk = false; // trigger retry
                    }
                }
            } else {
                Log.w(TAG, (isVideo ? "[VIDEO]" : "[IMAGE]") + " query returned null cursor");
            }
        } catch (Exception e) {
            Log.e(TAG, "Primary query failed: " + e.getMessage());
            allOk = false;
        }

        // If nothing matched, dump the most recent 10 rows for visibility (NO 'LIMIT' in sortOrder)
        if (totalFound == 0) {
            try (Cursor dbg = cr.query(
                    table,
                    new String[] {
                            MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.RELATIVE_PATH
                    },
                    null, null,
                    MediaStore.MediaColumns.DATE_ADDED + " DESC"
            )) {
                if (dbg != null) {
                    int lim = 0;
                    while (dbg.moveToNext() && lim++ < 10) {
                        Log.i(TAG, (isVideo ? "[VIDEO]" : "[IMAGE]") + " DEBUG row: " +
                                "name=" + dbg.getString(1) +
                                " relPath=" + dbg.getString(2));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "DEBUG probe failed: " + e.getMessage());
            }
        }

        // Fallback: query Files under Movies/GT6 when primary had 0 videos
        if (isVideo && totalFound == 0) {
            Uri files = MediaStore.Files.getContentUri("external");
            String filesSel =
                    "(" + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR " +
                            MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?)" +
                            " AND " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=" +
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            String[] filesArgs = new String[]{ "Movies/GT6/%", "Movies/gt6/%" };

            String[] filesProj = new String[] {
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE
            };

            try (Cursor c = cr.query(files, filesProj, filesSel, filesArgs,
                    MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
                if (c != null) {
                    Log.i(TAG, "[VIDEO][FALLBACK] Files matched: " + c.getCount());
                    while (c.moveToNext()) {
                        if (!uploadRow(cr, files, c, "video/mp4", uploader, containerUrl, prefix, true, touchedConsignments)) {
                            allOk = false;
                        }
                    }
                } else {
                    Log.w(TAG, "[VIDEO][FALLBACK] Files query returned null cursor");
                }
            } catch (Exception e) {
                Log.e(TAG, "[VIDEO][FALLBACK] Files query failed: " + e.getMessage());
                allOk = false;
            }
        }

        // Optional: very broad probe to see any GT6 anywhere
        if (totalFound == 0) {
            String broadSel = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            String[] broadArgs = new String[] { "%/GT6/%" };
            try (Cursor c = cr.query(table, projection, broadSel, broadArgs,
                    MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
                if (c != null) {
                    Log.i(TAG, (isVideo ? "[VIDEO]" : "[IMAGE]") +
                            " broad probe matched: " + c.getCount());
                    int lim = 0;
                    while (c.moveToNext() && lim++ < 10) {
                        Log.i(TAG, "  → name=" + c.getString(1) +
                                " relPath=" + c.getString(2));
                    }
                }
            } catch (Exception ignore) {}
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
                              Set<String> touchedConsignments) {

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
            return true; // not a failure, just skip
        }

        if (TextUtils.isEmpty(name)) {
            name = isVideo ? "release.mp4" : "photo.jpg";
        }

        // Build blob path (no extra "driver" here; containerUrl already points at container)
        String blobPath = joinPath(prefix, consignmentId, name);

        // ---- ensure only one worker handles this blob at a time ----
        Object lock = lockFor(blobPath);
        synchronized (lock) {
            Log.i(TAG, "Uploading → " + containerUrl + "/" + blobPath +
                    " (mime=" + mime + ", size=" + size + ")");

            // Skip if it already exists in Azure (prevents re-uploads).
            // This uses reflection so the code compiles even if AzureUploader doesn't have blobExists()
            try {
                if (azureBlobExistsReflect(uploader, containerUrl, blobPath)) {
                    Log.i(TAG, "Skip upload; blob already exists: " + containerUrl + "/" + blobPath);
                    tryDelete(getApplicationContext(), cr, itemUri); // attempt cleanup anyway
                    touchedConsignments.add(consignmentId);
                    return true;
                }
            } catch (Exception checkEx) {
                Log.w(TAG, "HEAD check failed (will try upload anyway): " + checkEx.getMessage());
            }

            try (InputStream in = cr.openInputStream(itemUri)) {
                if (in == null) {
                    Log.w(TAG, "InputStream null for " + itemUri);
                    return true; // skip
                }

                long contentLen = size > 0 ? size : guessLength(cr, itemUri);
                uploader.putBlobAuto(containerUrl, blobPath, mime, contentLen, in); // handles >256MB

                // Delete after success (skip if not owner on Android 11+)
                tryDelete(getApplicationContext(), cr, itemUri);

                Log.i(TAG, "Uploaded and deleted: " + itemUri);
                touchedConsignments.add(consignmentId);
                return true;

            } catch (FileNotFoundException e) {
                Log.w(TAG, "File not found: " + itemUri);
                return true; // skip
            } catch (IOException e) {
                Log.e(TAG, "Upload failed; will retry. " + e.getMessage());
                return false; // cause retry
            } finally {
                // optional: release the key to keep map from growing forever
                // LOCKS.remove(blobPath);
            }
        }
    }

    // Try to call AzureUploader.blobExists(String, String) via reflection if available.
    private static boolean azureBlobExistsReflect(AzureUploader uploader, String containerUrl, String blobPath) throws Exception {
        try {
            Method m = uploader.getClass().getMethod("blobExists", String.class, String.class);
            Object res = m.invoke(uploader, containerUrl, blobPath);
            if (res instanceof Boolean) return (Boolean) res;
        } catch (NoSuchMethodException nsme) {
            // silently ignore if method not present (older AzureUploader)
        }
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
            // On Android 11+ only the owner app can delete silently.
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
            }
        } catch (SecurityException se) {
            Log.w(TAG, "Delete security exception for " + uri + ": " + se.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "Delete failed for " + uri + ": " + e.getMessage());
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /** Finds consignmentId in RELATIVE_PATH like "Pictures/GT6/158515/" or "Movies/gt6/158515/". */
    private static String parseConsignmentId(String rel) {
        if (TextUtils.isEmpty(rel)) return null;
        int idx = rel.indexOf("GT6/");
        if (idx < 0) idx = rel.indexOf("gt6/");
        if (idx < 0) return null;
        String tail = rel.substring(idx + 4);
        int slash = tail.indexOf('/');
        return (slash > 0) ? tail.substring(0, slash) : tail;
    }

    // ---------- helpers to keep blob names tidy ----------
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
        // If on WorkManager 2.9+, you can inspect reason:
        // int reason = getStopReason();
        // Log.w(TAG, "Stop reason = " + reason);
    }

    private ForegroundInfo makeForegroundInfo(String text) {
        // Create/update the channel (safe to call repeatedly)
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

        // API 29+: include the FGS type (dataSync)
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



