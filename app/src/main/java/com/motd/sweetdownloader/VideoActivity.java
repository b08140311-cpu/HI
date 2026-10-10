package com.motd.sweetdownloader;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

public class VideoActivity extends Activity {
    private VideoView video;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String value = getIntent().getStringExtra("uri");
        if (value == null) { finish(); return; }
        video = new VideoView(this);
        video.setBackgroundColor(android.graphics.Color.BLACK);
        MediaController controls = new MediaController(this); controls.setAnchorView(video);
        video.setMediaController(controls); setContentView(video); video.setVideoURI(Uri.parse(value));
        int position = state == null ? 0 : state.getInt("position", 0);
        video.setOnPreparedListener(player -> { video.seekTo(position); video.start(); });
        video.setOnErrorListener((player, what, extra) -> {
            Toast.makeText(this, "手機播放器不支援此影片編碼，原始檔案仍保留在 Movies/Sweet", Toast.LENGTH_LONG).show();
            finish(); return true;
        });
    }
    @Override protected void onPause() { if (video != null) video.pause(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (video != null) state.putInt("position", video.getCurrentPosition()); super.onSaveInstanceState(state);
    }
}
