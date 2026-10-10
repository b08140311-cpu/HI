package com.motd.sweetdownloader;

import android.Manifest;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText urls;
    private RadioGroup modes;
    private Button download, cancel;
    private TextView status;
    private ProgressBar progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Intent pending;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            boolean active = DownloadService.running;
            download.setEnabled(!active); cancel.setEnabled(active);
            android.content.SharedPreferences prefs = getSharedPreferences("sweet_job", MODE_PRIVATE);
            status.setText(prefs.getString("message", "準備完成"));
            int value = prefs.getInt("progress", -1);
            progress.setIndeterminate(active && value < 0);
            progress.setProgress(Math.max(0, value));
            handler.postDelayed(this, 500);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(SweetUi.pinkGradient(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(SweetUi.dp(this, 20), SweetUi.dp(this, 32), SweetUi.dp(this, 20), SweetUi.dp(this, 30));
        scroll.addView(root);
        root.addView(SweetUi.title(this, "Sweet Downloader", 28));
        TextView subtitle = SweetUi.label(this, "圖片與影片下載", 16, SweetUi.MUTED);
        root.addView(subtitle);
        urls = new EditText(this); urls.setId(R.id.download_urls);
        urls.setHint("貼上網址，每行一個\n網頁圖片、影片直連、YouTube、公開 Telegram");
        urls.setTextSize(14); urls.setMinLines(4); urls.setMaxLines(8);
        urls.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        urls.setGravity(android.view.Gravity.TOP);
        root.addView(urls, spaced(130));
        Button paste = SweetUi.pill(this, "貼上網址", false);
        root.addView(paste, spaced(46));
        paste.setOnClickListener(v -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0)
                urls.setText(clipboard.getPrimaryClip().getItemAt(0).coerceToText(this));
        });
        modes = new RadioGroup(this); modes.setOrientation(RadioGroup.HORIZONTAL); modes.setId(R.id.download_modes);
        String[] labels = {"圖片", "影片", "全部"};
        for (int i = 0; i < labels.length; i++) {
            RadioButton radio = new RadioButton(this); radio.setId(new int[]{R.id.mode_images, R.id.mode_videos, R.id.mode_both}[i]); radio.setText(labels[i]);
            modes.addView(radio, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        modes.check(R.id.mode_images); root.addView(modes, spaced(52));
        download = SweetUi.pill(this, "開始下載", true); root.addView(download, spaced(54));
        cancel = SweetUi.pill(this, "取消下載", false); root.addView(cancel, spaced(48));
        download.setOnClickListener(v -> begin(false));
        cancel.setOnClickListener(v -> {
            startService(new Intent(this, DownloadService.class).setAction(DownloadService.CANCEL));
            status.setText("正在取消…"); cancel.setEnabled(false);
        });
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(progress, spaced(12));
        status = SweetUi.label(this, "準備完成", 14, SweetUi.TEXT); root.addView(status, spaced(-2));
        Button settings = SweetUi.pill(this, "下載設定", false); root.addView(settings, spaced(48));
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        Button history = SweetUi.pill(this, "下載紀錄與失敗原因", false); root.addView(history, spaced(48));
        history.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        Button gallery = SweetUi.pill(this, "已下載的圖片與影片", false); root.addView(gallery, spaced(48));
        gallery.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        Button update = SweetUi.pill(this, "更新影片引擎", false); root.addView(update, spaced(48));
        update.setOnClickListener(v -> begin(true));
        root.addView(SweetUi.label(this, "圖片：Pictures/Sweet\n影片：Movies/Sweet\n重新下載相同網址可續傳並略過已儲存檔案。\n私人 Telegram、登入頁面與 DRM 影片不支援。", 12, SweetUi.MUTED), spaced(-2));
        setContentView(scroll);
        if (state != null) { urls.setText(state.getString("urls", "")); modes.check(state.getInt("mode", R.id.mode_images)); }
        else acceptShare(getIntent());
    }
    private LinearLayout.LayoutParams spaced(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                height < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : SweetUi.dp(this, height));
        params.setMargins(0, SweetUi.dp(this, 12), 0, 0); return params;
    }
    private void begin(boolean update) {
        if (DownloadService.running) { Toast.makeText(this, "已有下載執行中", Toast.LENGTH_SHORT).show(); return; }
        if (!update) {
            try { MediaExtractor.parseInput(urls.getText().toString()); }
            catch (Exception e) { urls.setError(e.getMessage()); return; }
        }
        pending = new Intent(this, DownloadService.class);
        if (update) pending.setAction(DownloadService.UPDATE);
        else pending.putExtra("urls", urls.getText().toString()).putExtra("mode",
                modes.getCheckedRadioButtonId() == R.id.mode_videos ? "VIDEO" : modes.getCheckedRadioButtonId() == R.id.mode_both ? "BOTH" : "IMAGE");
        if (!update && Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100); return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101); return;
        }
        launch();
    }
    private void launch() {
        if (pending == null) return;
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(pending); else startService(pending);
        pending = null; download.setEnabled(false); status.setText("準備下載…");
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 100 && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)) {
            pending = null; status.setText("需要儲存權限才能下載到相簿"); return;
        }
        if (request == 100 || request == 101) launch();
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("urls", urls.getText().toString()); state.putInt("mode", modes.getCheckedRadioButtonId());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); acceptShare(intent); }
    private void acceptShare(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("https?://[^\\s<>\"]+").matcher(text);
                StringBuilder found = new StringBuilder();
                while (matcher.find()) { if (found.length() > 0) found.append('\n'); found.append(matcher.group()); }
                urls.setText(found.length() > 0 ? found.toString() : text);
            }
        }
    }
}
