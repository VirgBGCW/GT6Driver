package com.example.gt6driver.sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;

public class GT6MediaSyncService extends Service {
    public static final String CHANNEL_ID = "gt6_sync";
    private FileObserver picObserver, movObserver;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(2001, buildNotification("Watching GT6 media..."));

        // Start WorkManager content triggers (best for scoped storage)
        GT6MediaSync.enqueueContentTriggers(getApplicationContext());

        // Fallback: FileObserver on physical paths (pre-scoped or vendor devices)
        File pics = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GT6");
        File movs = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "GT6");
        picObserver = observerFor(pics);
        movObserver = observerFor(movs);
        if (picObserver != null) picObserver.startWatching();
        if (movObserver != null) movObserver.startWatching();

        // Periodic “belt-and-suspenders” scan
        GT6MediaSync.enqueuePeriodic(getApplicationContext());
    }

    private FileObserver observerFor(File dir) {
        if (dir == null) return null;
        // If the folder doesn't exist yet, create it (safe no-op if it already exists)
        if (!dir.exists()) dir.mkdirs();

        final int mask = FileObserver.CREATE | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE;
        return new FileObserver(dir.getAbsolutePath(), mask) {
            @Override public void onEvent(int event, @Nullable String path) {
                // Any new/finished file -> prompt a scan now
                GT6MediaSync.enqueueImmediate(getApplicationContext());
            }
        };
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // keep running
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        if (picObserver != null) picObserver.stopWatching();
        if (movObserver != null) movObserver.stopWatching();
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "GT6 Sync", NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Uploads GT6 photos/videos to Azure and cleans up");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("GT6 Media Sync")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }
}
