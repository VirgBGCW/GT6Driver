package com.example.gt6driver.sync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;

public class GT6MediaSyncService extends Service {
    public static final String CHANNEL_ID = "gt6_sync";
    private FileObserver picObserver, movObserver;

    // Simple debounce for FileObserver -> Work enqueue
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable debouncedEnqueue = () -> GT6MediaSync.enqueueImmediate(getApplicationContext());

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(2001, buildNotification("Watching GT6 media..."));

        // Kick off a one-time immediate scan at startup
        GT6MediaSync.enqueueImmediate(getApplicationContext());

        // Start WorkManager content triggers (scoped-storage friendly)
        GT6MediaSync.enqueueContentTriggers(getApplicationContext());

        // Fallback: raw FileObserver (best-effort on pre-scoped/vendor devices)
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
        if (!dir.exists()) dir.mkdirs();

        final int mask = FileObserver.CREATE | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE;
        return new FileObserver(dir.getAbsolutePath(), mask) {
            @Override public void onEvent(int event, @Nullable String path) {
                // Debounce bursts of events into a single enqueue
                handler.removeCallbacks(debouncedEnqueue);
                handler.postDelayed(debouncedEnqueue, 1500);
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
        handler.removeCallbacksAndMessages(null);
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

