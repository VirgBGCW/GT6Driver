package com.example.gt6driver.sync;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class MediaUploadWorker extends Worker {

    public static final String KEY_SAS = "sas"; // SAS query string (no leading '?')
    private static final String TAG = "GT6-Worker";

    // Azure destination
    private static final String CONTAINER_URL = "https://stgt6driverappprod.blob.core.windows.net";
    // Keys to match what ActionActivity sends
    private static final String CONTAINER_NAME = "driver";  // container must exist (lowercase)
    private static final String PREFIX = "";                // no extra folder before consignmentId

    public MediaUploadWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String sas = getInputData().getString(KEY_SAS);
        if (TextUtils.isEmpty(sas)) {
            Log.e(TAG, "Missing SAS");
            return Result.failure(new Data.Builder().putString("error", "Missing SAS").build());
        }

        AzureUploader uploader = new AzureUploader(sas);
        ContentResolver cr = getApplicationContext().getContentResolver();

        // Track consignmentIds we uploaded, so we can remove folders afterwards
        Set<String> touchedConsignments = new HashSet<>();

        boolean okImages = processTable(
                cr,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/jpeg",
                uploader,
                /*isVideo=*/false,
                touchedConsignments
        );

        boolean okVideos = processTable(
                cr,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "video/mp4",
                uploader,
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

        // Re-arm content triggers so we're always listening
        try {
            GT6MediaSync.enqueueContentTriggers(getApplicationContext());
        } catch (Exception ignore) {}

        return (okImages && okVideos) ? Result.success() : Result.retry();
    }

    private boolean processTable(ContentResolver cr,
                                 Uri table,
                                 String defaultMime,
                                 AzureUploader uploader,
                                 boolean isVideo,
                                 Set<String> touchedConsignments) {

        // Scan both casings and both base directories (Pictures/..., Movies/...)
        String picturesLikeUpper = "Pictures/GT6/%";
        String picturesLikeLower = "Pictures/gt6/%";
        String moviesLikeUpper   = "Movies/GT6/%";
        String moviesLikeLower   = "Movies/gt6/%";

        final String sel;
        final String[] args;
        if (isVideo) {
            sel  = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR " +
                    MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            args = new String[]{ moviesLikeUpper, moviesLikeLower };
        } else {
            sel  = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR " +
                    MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            args = new String[]{ picturesLikeUpper, picturesLikeLower };
        }

        String[] projection = new String[] {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE
        };

        try (Cursor c = cr.query(table, projection, sel, args, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (c == null) return true;

            while (c.moveToNext()) {
                long id        = c.getLong(0);
                String name    = safe(c.getString(1));
                String relPath = safe(c.getString(2));
                long size      = c.getLong(3);
                String mime    = safe(c.getString(4));
                if (TextUtils.isEmpty(mime)) mime = defaultMime;

                Uri itemUri = ContentUris.withAppendedId(table, id);

                // consignmentId is the path segment after GT6/ or gt6/
                String consignmentId = parseConsignmentId(relPath);
                if (TextUtils.isEmpty(consignmentId)) {
                    Log.w(TAG, "Skip item with unknown consignmentId. relPath=" + relPath);
                    continue;
                }

                if (TextUtils.isEmpty(name)) {
                    name = isVideo ? "release.mp4" : "photo.jpg"; // safe fallback
                }

                // driver/{consignmentId}/{file}
                String blobPath = joinPath(PREFIX, consignmentId, name);

                Log.i(TAG, "Uploading → " + CONTAINER_URL + "/" + CONTAINER_NAME + "/" + blobPath
                        + "  (mime=" + mime + ", size=" + size + ")");

                try (InputStream in = cr.openInputStream(itemUri)) {
                    if (in == null) {
                        Log.w(TAG, "InputStream null for " + itemUri);
                        continue;
                    }

                    long contentLen = size > 0 ? size : guessLength(cr, itemUri);

                    uploader.putBlob(CONTAINER_URL + "/" + CONTAINER_NAME, blobPath, mime, contentLen, in);

                    // Uploaded OK → delete local row
                    tryDelete(cr, itemUri);
                    Log.i(TAG, "Uploaded and deleted local: " + itemUri);

                    touchedConsignments.add(consignmentId);

                } catch (FileNotFoundException e) {
                    Log.w(TAG, "File not found: " + itemUri);
                } catch (IOException e) {
                    Log.e(TAG, "Upload failed; will retry. " + e.getMessage());
                    return false; // trigger retry
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Query failed: " + e.getMessage());
            return false;
        }

        return true;
    }

    private static long guessLength(ContentResolver cr, Uri uri) {
        try (Cursor cur = cr.query(uri, new String[]{MediaStore.MediaColumns.SIZE}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                return cur.getLong(0);
            }
        } catch (Exception ignore) {}
        return -1;
    }

    private static void tryDelete(ContentResolver cr, Uri uri) {
        try {
            cr.delete(uri, null, null);
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
}

