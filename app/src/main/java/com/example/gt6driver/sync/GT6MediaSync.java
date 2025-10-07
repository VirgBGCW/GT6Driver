package com.example.gt6driver.sync;

import android.content.Context;
import android.provider.MediaStore;

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

    // Prefs keys
    private static final String PREFS = "gt6";
    private static final String PREF_SAS = "azure_sas";

    // ✅ Fallback SAS (query string only; no leading '?')
    // Matches: https://stgt6driverappdev.blob.core.windows.net/driver?{SAS}
   // DEV  private static final String DEFAULT_SAS =
       //   "si=driver&spr=https&sv=2024-11-04&sr=c&sig=No06Fv7bY%2Bf8cWTMAZImASOqH%2FpRPS2v2raoKLBWUyM%3D";

    private static final String DEFAULT_SAS =
            "si=driver&spr=https&sv=2024-11-04&sr=c&sig=bkDZ74H2Fwmznej2B86lmh3eJXfQ9nI0csLwS8ixyN8%3D";

    /** Returns saved SAS or the DEFAULT_SAS if none saved. */
    private static String sas(Context ctx) {
        String s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_SAS, "");
        return (s == null || s.isEmpty()) ? DEFAULT_SAS : s;
    }

    /** Optional helper if you want to set/override SAS elsewhere (e.g., ConfigActivity). */
    public static void setSas(Context ctx, String value) {
        if (value != null && value.startsWith("?")) value = value.substring(1); // normalize
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_SAS, value == null ? "" : value)
                .apply();
    }

    public static void enqueueImmediate(Context ctx) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(MediaUploadWorker.class)
                .addTag("gt6_scan_now")
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(new Data.Builder()
                        .putString(MediaUploadWorker.KEY_SAS, sas(ctx))
                        .build())
                .build();
        WorkManager.getInstance(ctx).enqueue(req);
    }

    public static void enqueuePeriodic(Context ctx) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(MediaUploadWorker.class, 15, TimeUnit.MINUTES)
                .addTag("gt6_scan_periodic")
                .setInputData(new Data.Builder()
                        .putString(MediaUploadWorker.KEY_SAS, sas(ctx))
                        .build())
                .build();
        WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("gt6_scan_periodic", ExistingPeriodicWorkPolicy.KEEP, req);
    }

    /** React to new entries in MediaStore (scoped-storage friendly). */
    public static void enqueueContentTriggers(Context ctx) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                // Run when new media is added/updated in MediaStore
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
                // Coalesce rapid changes
                .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(30, TimeUnit.SECONDS)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MediaUploadWorker.class)
                .setConstraints(constraints)
                .addTag("gt6_content_triggered")
                .setInputData(new Data.Builder()
                        .putString(MediaUploadWorker.KEY_SAS, sas(ctx))
                        .build())
                .build();

        WorkManager.getInstance(ctx)
                .enqueueUniqueWork("gt6_content_watch", ExistingWorkPolicy.REPLACE, request);
    }
}
