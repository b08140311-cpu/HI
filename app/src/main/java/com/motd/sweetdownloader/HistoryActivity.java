package com.motd.sweetdownloader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends Activity {
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
        root.setPadding(SweetUi.dp(this, 18), SweetUi.dp(this, 26),
                SweetUi.dp(this, 18), SweetUi.dp(this, 100));
        scroll.addView(root);

        TextView title = SweetUi.title(this, "下載記錄", 25);
        root.addView(title);

        JSONArray arr = load();
        if (arr.length() == 0) {
            TextView empty = SweetUi.label(this, "目前還沒有下載記錄", 15, SweetUi.MUTED);
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 220));
            root.addView(empty, ep);
        } else {
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                addItem(root, o);
            }
        }

        LinearLayout nav = buildNav();
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 62));
        root.addView(nav, np);

        setContentView(scroll);
    }

    private JSONArray load() {
        try {
            return new JSONArray(getSharedPreferences("sweet_downloads", MODE_PRIVATE)
                    .getString("items", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void addItem(LinearLayout root, JSONObject o) {
        String folder = o.optString("folder", "Sweet");
        String source = o.optString("source", "");
        int saved = o.optInt("saved", 0);
        int pages = o.optInt("pages", 1);
        long time = o.optLong("time", 0);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(SweetUi.dp(this, 12), SweetUi.dp(this, 12),
                SweetUi.dp(this, 12), SweetUi.dp(this, 12));
        card.setBackground(SweetUi.rounded(SweetUi.WHITE_GLASS, 20, this));

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Uri first = firstUri(folder);
        if (first != null) thumb.setImageURI(first);
        else thumb.setBackground(SweetUi.rounded(SweetUi.PINK_SOFT, 14, this));
        card.addView(thumb, new LinearLayout.LayoutParams(
                SweetUi.dp(this, 72), SweetUi.dp(this, 72)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(SweetUi.dp(this, 12), 0, 0, 0);

        TextView name = SweetUi.label(this, cleanFolder(folder), 15, SweetUi.TEXT);
        name.setMaxLines(1);
        info.addView(name);

        TextView meta = SweetUi.label(this,
                saved + " 張 · " + pages + " 頁", 12, SweetUi.MUTED);
        info.addView(meta);

        if (time > 0) {
            String date = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                    .format(new Date(time));
            info.addView(SweetUi.label(this, date, 11, SweetUi.MUTED));
        }

        card.addView(info, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView check = SweetUi.label(this, "✓", 22, ColorCompat.green());
        card.addView(check);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(HistoryActivity.this, CollectionActivity.class);
            intent.putExtra("folder", folder);
            startActivity(intent);
        });

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, SweetUi.dp(this, 12), 0, 0);
        root.addView(card, cp);
    }

    private Uri firstUri(String folder) {
        try {
            JSONArray arr = new JSONArray(getSharedPreferences("sweet_gallery", MODE_PRIVATE)
                    .getString("items", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (folder.equals(o.optString("folder"))) {
                    String uri = o.optString("uri");
                    if (!uri.isEmpty()) return Uri.parse(uri);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String cleanFolder(String f) {
        return f.replaceFirst("_[0-9]{10,}$", "");
    }

    private LinearLayout buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(SweetUi.rounded(0xEEFFFFFF, 22, this));

        Button home = SweetUi.nav(this, "⌂\n首頁", false);
        Button history = SweetUi.nav(this, "▣\n下載記錄", true);
        Button gallery = SweetUi.nav(this, "▧\nGallery", false);

        nav.addView(home, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        nav.addView(history, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        nav.addView(gallery, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        home.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        gallery.setOnClickListener(v -> {
            startActivity(new Intent(this, GalleryActivity.class));
            finish();
        });
        return nav;
    }

    static class ColorCompat {
        static int green() { return android.graphics.Color.rgb(45, 170, 95); }
    }
}
