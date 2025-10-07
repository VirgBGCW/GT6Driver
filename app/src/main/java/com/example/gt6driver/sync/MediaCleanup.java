// app/src/main/java/com/example/gt6driver/sync/MediaCleanup.java
package com.example.gt6driver.sync;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.net.Uri;

import java.io.File;

public final class MediaCleanup {

    private MediaCleanup() {}

    /** Remove all media for this consignment under Pictures/gt6|GT6/{id} and Movies/gt6|GT6/{id}. */
    public static void removeLocalConsignmentMedia(Context ctx, String consignmentId) {
        if (consignmentId == null || consignmentId.trim().isEmpty()) return;

        // Always try both casings so you’re covered.
        String lower = "gt6/" + consignmentId;
        String upper = "GT6/" + consignmentId;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Q+ → delete via MediaStore rows by RELATIVE_PATH
            deleteByRelativePath(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_PICTURES + "/" + lower);
            deleteByRelativePath(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_PICTURES + "/" + upper);

            deleteByRelativePath(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_MOVIES + "/" + lower);
            deleteByRelativePath(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_MOVIES + "/" + upper);

            // Direct folder delete isn’t needed; once rows are gone the folder disappears (or is ignored).
        } else {
            // Pre-Q → delete filesystem then rely on media scan to reflect changes
            File picsLower  = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), lower);
            File picsUpper  = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), upper);
            File movieLower = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),   lower);
            File movieUpper = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),   upper);

            deleteFileTree(picsLower);
            deleteFileTree(picsUpper);
            deleteFileTree(movieLower);
            deleteFileTree(movieUpper);
        }
    }

    private static void deleteByRelativePath(Context ctx, Uri collection, String relativePathPrefix) {
        // RELATIVE_PATH stored by the system can be with/without trailing slash, so use LIKE
        String sel   = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] arg = new String[]{ relativePathPrefix + "%" };

        ContentResolver cr = ctx.getContentResolver();
        try (Cursor c = cr.query(collection, new String[]{ MediaStore.MediaColumns._ID }, sel, arg, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                Uri row = ContentUris.withAppendedId(collection, id);
                try { cr.delete(row, null, null); } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}
    }

    /** Recursively delete a directory tree on pre-Q. */
    private static void deleteFileTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteFileTree(k);
            }
        }
        try { // delete file or empty dir
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        } catch (Exception ignore) {}
    }
}
