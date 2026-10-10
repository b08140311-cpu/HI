package com.motd.sweetdownloader;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;

public class SettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        DownloadOptions options = new DownloadOptions(getSharedPreferences("sweet_settings", MODE_PRIVATE));
        ScrollView scroll = new ScrollView(this); scroll.setBackground(SweetUi.pinkGradient(this));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(SweetUi.dp(this, 20), SweetUi.dp(this, 28), SweetUi.dp(this, 20), SweetUi.dp(this, 28));
        scroll.addView(root); root.addView(SweetUi.title(this, "下載設定", 26));
        EditText pages = number(root, "最多掃描頁數（1–50）", options.maxPages);
        EditText size = number(root, "圖片最短邊（0–10000 px，0 表示不限）", options.minSize);
        EditText workers = number(root, "同時圖片下載／影片片段數（1–4）", options.workers);
        root.addView(SweetUi.label(this, "圖片格式", 16, SweetUi.TEXT));
        Spinner format = new Spinner(this);
        format.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"原始格式（保留 GIF 動畫）", "JPG", "PNG"}));
        format.setSelection(options.imageFormat.equals("PNG") ? 2 : options.imageFormat.equals("JPG") ? 1 : 0);
        root.addView(format);
        CheckBox dedupe = new CheckBox(this); dedupe.setText("過濾外觀重複圖片"); dedupe.setChecked(options.dedupe); root.addView(dedupe);
        root.addView(SweetUi.label(this, "格式轉換會將 GIF 轉成單張圖片。\n影片保留原始串流；可合併時優先輸出 MP4。\n設定會在下次下載生效。", 13, SweetUi.MUTED));
        Button save = SweetUi.pill(this, "儲存", true); root.addView(save);
        save.setOnClickListener(v -> {
            Integer p = valid(pages, 1, 50), s = valid(size, 0, 10000), w = valid(workers, 1, 4);
            if (p == null || s == null || w == null) return;
            getSharedPreferences("sweet_settings", MODE_PRIVATE).edit().putInt("max_pages", p).putInt("min_size", s)
                    .putInt("workers", w).putBoolean("dedupe", dedupe.isChecked()).remove("people_only")
                    .putString("image_format", format.getSelectedItemPosition() == 2 ? "PNG" : format.getSelectedItemPosition() == 1 ? "JPG" : "ORIGINAL").apply();
            finish();
        });
        setContentView(scroll);
    }
    private EditText number(LinearLayout root, String label, int initial) {
        root.addView(SweetUi.label(this, label, 16, SweetUi.TEXT));
        EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setText(String.valueOf(initial));
        root.addView(input); return input;
    }
    private Integer valid(EditText input, int min, int max) {
        try { int value = Integer.parseInt(input.getText().toString()); if (value >= min && value <= max) return value; }
        catch (Exception ignored) { }
        input.setError("請輸入 " + min + "–" + max); return null;
    }
}
