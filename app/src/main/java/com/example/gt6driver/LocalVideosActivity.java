package com.example.gt6driver;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LocalVideosActivity extends AppCompatActivity {

    private RecyclerView rvVideos;
    private TextView tvEmpty;
    private final List<LocalVideoItem> videos = new ArrayList<>();
    private VideoListAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_videos);

        setTitle("Local Videos");

        rvVideos = findViewById(R.id.rvLocalVideos);
        tvEmpty = findViewById(R.id.tvEmptyVideos);

        rvVideos.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VideoListAdapter(this, videos, item -> {
            Intent intent = new Intent(LocalVideosActivity.this, VideoPlayerActivity.class);
            intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, item.contentUri.toString());
            intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, item.displayName);
            startActivity(intent);
        });
        rvVideos.setAdapter(adapter);

        loadVideos();
    }

    private void loadVideos() {
        videos.clear();

        String[] projection = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.RELATIVE_PATH,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION
        };

        String selection = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ? OR " +
                MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";
        String[] args = new String[]{"Movies/GT6/%", "Movies/gt6/%"};

        try (Cursor c = getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                null
        )) {
            if (c != null) {
                int idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int pathCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH);
                int modCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);
                int sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                int durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);

                while (c.moveToNext()) {
                    long id = c.getLong(idCol);
                    String name = c.getString(nameCol);
                    String relPath = c.getString(pathCol);
                    long dateModifiedSecs = c.getLong(modCol);
                    long size = c.getLong(sizeCol);
                    long durationMs = c.getLong(durCol);
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);

                    videos.add(new LocalVideoItem(
                            id,
                            name != null ? name : "video.mp4",
                            relPath != null ? relPath : "",
                            dateModifiedSecs * 1000L,
                            size,
                            durationMs,
                            contentUri
                    ));
                }
            }
        }

        Collections.sort(videos, (a, b) -> Long.compare(b.dateModifiedMs, a.dateModifiedMs));

        tvEmpty.setVisibility(videos.isEmpty() ? View.VISIBLE : View.GONE);
        rvVideos.setVisibility(videos.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private static class LocalVideoItem {
        final long id;
        final String displayName;
        final String relativePath;
        final long dateModifiedMs;
        final long sizeBytes;
        final long durationMs;
        final Uri contentUri;

        LocalVideoItem(long id, String displayName, String relativePath, long dateModifiedMs,
                       long sizeBytes, long durationMs, Uri contentUri) {
            this.id = id;
            this.displayName = displayName;
            this.relativePath = relativePath;
            this.dateModifiedMs = dateModifiedMs;
            this.sizeBytes = sizeBytes;
            this.durationMs = durationMs;
            this.contentUri = contentUri;
        }
    }

    private interface OnVideoClickListener {
        void onVideoClick(LocalVideoItem item);
    }

    private static class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.VH> {
        private final Context context;
        private final List<LocalVideoItem> items;
        private final OnVideoClickListener listener;
        private final DateFormat dateFormat;

        VideoListAdapter(Context context, List<LocalVideoItem> items, OnVideoClickListener listener) {
            this.context = context;
            this.items = items;
            this.listener = listener;
            this.dateFormat = android.text.format.DateFormat.getMediumDateFormat(context);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local_video, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            LocalVideoItem item = items.get(position);
            h.tvName.setText(item.displayName);
            h.tvMeta.setText(buildMeta(item));
            loadThumbnail(h.ivThumb, item);
            h.itemView.setOnClickListener(v -> listener.onVideoClick(item));
        }

        private String buildMeta(LocalVideoItem item) {
            String dateText = dateFormat.format(item.dateModifiedMs);
            return formatDuration(item.durationMs) + "  •  " + humanSize(item.sizeBytes) + "\n" +
                    item.relativePath + "  •  " + dateText;
        }

        private void loadThumbnail(ImageView imageView, LocalVideoItem item) {
            Bitmap bmp = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bmp = context.getContentResolver().loadThumbnail(item.contentUri, new Size(240, 135), null);
                } else {
                    bmp = MediaStore.Video.Thumbnails.getThumbnail(
                            context.getContentResolver(),
                            item.id,
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null
                    );
                }
            } catch (Exception ignored) {
            }
            imageView.setImageBitmap(bmp);
            if (bmp == null) {
                imageView.setImageResource(android.R.drawable.ic_media_play);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView ivThumb;
            final TextView tvName;
            final TextView tvMeta;

            VH(@NonNull View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.ivVideoThumb);
                tvName = itemView.findViewById(R.id.tvVideoName);
                tvMeta = itemView.findViewById(R.id.tvVideoMeta);
            }
        }

        private static String formatDuration(long durationMs) {
            long totalSeconds = Math.max(0L, durationMs / 1000L);
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;
            if (hours > 0L) {
                return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
            }
            return String.format(Locale.US, "%d:%02d", minutes, seconds);
        }

        private static String humanSize(long bytes) {
            if (bytes < 1024L) return bytes + " B";
            double kb = bytes / 1024.0;
            if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb);
            double mb = kb / 1024.0;
            if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb);
            double gb = mb / 1024.0;
            return String.format(Locale.US, "%.2f GB", gb);
        }
    }
}
