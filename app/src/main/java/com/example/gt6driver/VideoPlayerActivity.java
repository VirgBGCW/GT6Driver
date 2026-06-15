package com.example.gt6driver;

import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URI = "video_uri";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_FORCE_LANDSCAPE = "force_landscape";

    private VideoView videoView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra(EXTRA_FORCE_LANDSCAPE, false)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        setContentView(R.layout.activity_video_player);

        videoView = findViewById(R.id.videoView);

        String title = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);
        if (title != null && !title.trim().isEmpty()) {
            setTitle(title);
        } else {
            setTitle("Video Player");
        }

        String uriString = getIntent().getStringExtra(EXTRA_VIDEO_URI);
        if (uriString == null || uriString.trim().isEmpty()) {
            Toast.makeText(this, "Video not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Uri videoUri = Uri.parse(uriString);
        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);
        videoView.setVideoURI(videoUri);
        videoView.setOnPreparedListener(mp -> {
            mp.setOnInfoListener((mediaPlayer, what, extra) -> false);
            videoView.start();
        });
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
    }
}
