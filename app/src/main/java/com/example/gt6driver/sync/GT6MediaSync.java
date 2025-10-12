// app/src/main/java/com/example/gt6driver/sync/GT6MediaSync.java
package com.example.gt6driver.sync;

import android.content.Context;
import android.provider.MediaStore;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class GT6MediaSync {

    private static final String TAG = "GT6-Sync";

    // Prefs
    private static final String PREFS = "gt6";
    private static final String PREF_SAS = "azure_sas";
    private static final String PREF_CONTAINER_URL = "azure_container_url";

    // Keys passed to the Worker
    public static final String KEY_SAS = MediaUploadWorker.KEY_SAS;
    public static final String KEY_CONTAINER_URL = MediaUploadWorker.KEY_CONTAINER_URL;
    public static final String KEY_RECURSIVE = MediaUploadWorker.KEY_RECURSIVE;
    public static final String KEY_PREFIX = MediaUploadWorker.KEY_PREFIX;

    // Defaults (keep for fallback; prefer setting via setSas/setContainerUrl at runtime)
    private static final String DEFAULT_SAS =
            "si=driver&spr=https&sv=2024-11-04&sr=c&sig=bkDZ74H2Fwmznej2B86lmh3eJXfQ9nI0csLwS8ixyN8%3D";
    private static final String DEFAULT_CONTAINER_URL =
            "https://stgt6driverappprod.blob.core.windows.net/driver";

    /** Returns saved SAS or default. (Public so other classes can reuse.) */
    public static String getSas(Context ctx) {
        String s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_SAS, "");
        s = (s == null || s.isEmpty()) ? DEFAULT_SAS : s;
        // Helpful once in logs (never log the full sig in production!)
        Log.i(TAG, "getSas() len=" + s.length());
        return s;
    }

    /** Returns saved container URL or default. (Public so other classes can reuse.) */
    public static String getContainerUrl(Context ctx) {
        String u = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_CONTAINER_URL, "");
        u = (u == null || u.isEmpty()) ? DEFAULT_CONTAINER_URL : u.trim();
        Log.i(TAG, "getContainerUrl() -> " + u);
        return u;
    }

    /** Save/override SAS (normalize by stripping any leading '?'). */
    public static void setSas(Context ctx, String value) {
        if (value != null && value.startsWith("?")) value = value.substring(1);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PREF_SAS, value == null ? "" : value).apply();
        Log.i(TAG, "setSas() updated (len=" + (value == null ? 0 : value.length()) + ")");
    }

    /** Save/override Container URL (no trailing '/'; worker can normalize). */
    public static void setContainerUrl(Context ctx, String value) {
        String v = (value == null) ? "" : value.trim();
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PREF_CONTAINER_URL, v).apply();
        Log.i(TAG, "setContainerUrl() -> " + v);
    }

    /** Helper to build input data shared by all enqueue methods. */
    private static Data buildInput(Context ctx, boolean recursive, String prefix) {
        Data d = new Data.Builder()
                .putString(KEY_SAS, getSas(ctx))                     // no leading '?' needed
                .putString(KEY_CONTAINER_URL, getContainerUrl(ctx))  // e.g. https://acct.blob.core.windows.net/driver
                .putBoolean(KEY_RECURSIVE, recursive)
                .putString(KEY_PREFIX, prefix == null ? "" : prefix)
                .build();
        Log.i(TAG, "buildInput() prefix=" + (prefix == null ? "" : prefix));
        return d;
    }

    // ----------------- Public enqueue APIs -----------------

    /** Fire a one-off sync now. */
    public static void enqueueImmediate(Context ctx) {
        Constraints net = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(MediaUploadWorker.class)
                .addTag("gt6_scan_now")
                .setConstraints(net)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        java.time.Duration.ofSeconds(30)
                )
                .setInputData(buildInput(ctx, false, ""))
                .build();

        // Replace any existing scan instead of appending to a chain
        WorkManager.getInstance(ctx)
                .enqueueUniqueWork("gt6_scan_serial", ExistingWorkPolicy.APPEND, req);
    }

    /** Run every 15 minutes. */
    public static void enqueuePeriodic(Context ctx) {
        Constraints net = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(MediaUploadWorker.class, 15, TimeUnit.MINUTES)
                .addTag("gt6_scan_periodic")
                .setConstraints(net)
                .setInputData(buildInput(ctx, false, ""))
                .build();

        Log.i(TAG, "enqueuePeriodic() id=" + req.getId());
        WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("gt6_scan_periodic", ExistingPeriodicWorkPolicy.KEEP, req);
    }

    /** React to media changes via MediaStore (fires when user captures new media). */
    public static void enqueueContentTriggers(Context ctx) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(30, TimeUnit.SECONDS)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MediaUploadWorker.class)
                .setConstraints(constraints)
                .addTag("gt6_content_triggered")
                .setInputData(buildInput(ctx, false, ""))
                .build();

        Log.i(TAG, "enqueueContentTriggers() id=" + request.getId());
        WorkManager.getInstance(ctx)
                .enqueueUniqueWork("gt6_content_watch", ExistingWorkPolicy.REPLACE, request);
    }

    // ----------------- Quality-of-life helpers -----------------

    /** Clear all existing jobs and re-enqueue everything with the current SAS/URL. */
    public static void resetAndReenqueueAll(Context ctx) {
        WorkManager wm = WorkManager.getInstance(ctx);
        wm.cancelAllWork();
        wm.pruneWork();
        Log.i(TAG, "resetAndReenqueueAll(): cancelled & pruned all work; re-enqueueing");
        enqueuePeriodic(ctx);
        enqueueContentTriggers(ctx);
        enqueueImmediate(ctx);
    }

    /**
     * Call this right after changing container URL or SAS to avoid stale jobs running with old config.
     * Example:
     *   GT6MediaSync.setContainerUrl(ctx, prodUrl);
     *   GT6MediaSync.setSas(ctx, prodSas);
     *   GT6MediaSync.applyConfigAndReenqueue(ctx);
     */
    public static void applyConfigAndReenqueue(Context ctx) {
        resetAndReenqueueAll(ctx);
    }
}



