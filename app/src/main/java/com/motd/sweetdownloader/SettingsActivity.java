package com.motd.sweetdownloader;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private EditText maxPages;
    private Switch peopleOnly;
    private Switch dedupe;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(SweetUi.pinkGradient(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(SweetUi.dp(this, 20), SweetUi.dp(this, 24),
                SweetUi.dp(this, 20), SweetUi.dp(this, 36));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = SweetUi.pill(this, "‹", false);
        header.addView(back, new LinearLayout.LayoutParams(
                SweetUi.dp(this, 48), SweetUi.dp(this, 42)));
        TextView title = SweetUi.title(this, "設定", 24);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMargins(SweetUi.dp(this, 12), 0, 0, 0);
        header.addView(title, tp);
        root.addView(header);
        back.setOnClickListener(v -> finish());

        TextView section = SweetUi.label(this, "下載設定", 13, SweetUi.MUTED);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, SweetUi.dp(this, 26), 0, SweetUi.dp(this, 10));
        root.addView(section, sp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(SweetUi.dp(this, 16), SweetUi.dp(this, 12),
                SweetUi.dp(this, 16), SweetUi.dp(this, 12));
        card.setBackground(SweetUi.rounded(SweetUi.WHITE_GLASS, 22, this));
        root.addView(card);

        int savedMax = getSharedPreferences("sweet_settings", MODE_PRIVATE)
                .getInt("max_pages", 12);
        boolean savedPeople = getSharedPreferences("sweet_settings", MODE_PRIVATE)
                .getBoolean("people_only", true);
        boolean savedDedupe = getSharedPreferences("sweet_settings", MODE_PRIVATE)
                .getBoolean("dedupe", true);

        maxPages = new EditText(this);
        maxPages.setText(String.valueOf(savedMax));
        maxPages.setInputType(InputType.TYPE_CLASS_NUMBER);
        maxPages.setSingleLine(true);
        maxPages.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        addRow(card, "最大分頁數", maxPages);

        peopleOnly = new Switch(this);
        peopleOnly.setChecked(savedPeople);
        addRow(card, "只下載有人圖片", peopleOnly);

        dedupe = new Switch(this);
        dedupe.setChecked(savedDedupe);
        addRow(card, "重複圖片過濾", dedupe);

        TextView parallel = SweetUi.label(this, "同時下載數", 15, SweetUi.TEXT);
        TextView parallelValue = SweetUi.label(this, "1", 15, SweetUi.PINK_DARK);
        LinearLayout row = row();
        row.addView(parallel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(parallelValue);
        card.addView(row);

        TextView storageTitle = SweetUi.label(this, "儲存位置", 13, SweetUi.MUTED);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stp.setMargins(0, SweetUi.dp(this, 22), 0, SweetUi.dp(this, 8));
        root.addView(storageTitle, stp);

        TextView storage = SweetUi.label(this,
                "/Pictures/SweetDownloader", 14, SweetUi.PINK_DARK);
        storage.setPadding(SweetUi.dp(this, 16), SweetUi.dp(this, 16),
                SweetUi.dp(this, 16), SweetUi.dp(this, 16));
        storage.setBackground(SweetUi.rounded(SweetUi.WHITE_GLASS, 18, this));
        root.addView(storage);

        Button save = SweetUi.pill(this, "儲存設定", true);
        LinearLayout.LayoutParams savep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 54));
        savep.setMargins(0, SweetUi.dp(this, 24), 0, 0);
        root.addView(save, savep);

        save.setOnClickListener(v -> {
            int n = 12;
            try {
                n = Integer.parseInt(maxPages.getText().toString().trim());
            } catch (Exception ignored) {}
            if (n < 1) n = 1;
            if (n > 50) n = 50;

            getSharedPreferences("sweet_settings", MODE_PRIVATE).edit()
                    .putInt("max_pages", n)
                    .putBoolean("people_only", peopleOnly.isChecked())
                    .putBoolean("dedupe", dedupe.isChecked())
                    .apply();
            finish();
        });

        setContentView(scroll);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, SweetUi.dp(this, 10), 0, SweetUi.dp(this, 10));
        return row;
    }

    private void addRow(LinearLayout card, String title, android.view.View control) {
        LinearLayout row = row();
        TextView label = SweetUi.label(this, title, 15, SweetUi.TEXT);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(control, new LinearLayout.LayoutParams(
                SweetUi.dp(this, 120), SweetUi.dp(this, 48)));
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
