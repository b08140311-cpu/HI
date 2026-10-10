package com.motd.sweetdownloader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GalleryActivity extends Activity {
    private LinearLayout content;
    private Button albumsTab;
    private Button allTab;

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
                SweetUi.dp(this, 18), SweetUi.dp(this, 32));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = SweetUi.title(this, "Gallery", 27);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView menu = SweetUi.label(this, "⋮", 25, SweetUi.TEXT);
        menu.setGravity(Gravity.CENTER);
        header.addView(menu, new LinearLayout.LayoutParams(
                SweetUi.dp(this, 42), SweetUi.dp(this, 42)));
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        albumsTab = SweetUi.pill(this, "合集", true);
        allTab = SweetUi.pill(this, "全部媒體", false);
        tabs.addView(albumsTab, new LinearLayout.LayoutParams(
                0, SweetUi.dp(this, 42), 1f));
        LinearLayout.LayoutParams atp = new LinearLayout.LayoutParams(
                0, SweetUi.dp(this, 42), 1f);
        atp.setMargins(SweetUi.dp(this, 10), 0, 0, 0);
        tabs.addView(allTab, atp);

        LinearLayout.LayoutParams tabsP = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 42));
        tabsP.setMargins(0, SweetUi.dp(this, 18), 0, SweetUi.dp(this, 18));
        root.addView(tabs, tabsP);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout nav = buildNav();
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 64));
        np.setMargins(0, SweetUi.dp(this, 20), 0, 0);
        root.addView(nav, np);

        albumsTab.setOnClickListener(v -> showAlbums());
        allTab.setOnClickListener(v -> showAll());
        showAlbums();

        setContentView(scroll);
    }

    private void setTab(boolean albums) {
        albumsTab.setBackground(SweetUi.rounded(
                albums ? SweetUi.PINK : SweetUi.PINK_SOFT, 28, this));
        albumsTab.setTextColor(albums ? Color.WHITE : SweetUi.PINK_DARK);
        allTab.setBackground(SweetUi.rounded(
                albums ? SweetUi.PINK_SOFT : SweetUi.PINK, 28, this));
        allTab.setTextColor(albums ? SweetUi.PINK_DARK : Color.WHITE);
    }

    private void showAlbums() {
        setTab(true);
        content.removeAllViews();

        LinkedHashMap<String, Album> groups = loadAlbums();
        if (groups.isEmpty()) {
            TextView empty = SweetUi.label(this, "尚未下載圖片或影片", 15, SweetUi.MUTED);
            empty.setGravity(Gravity.CENTER);
            content.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 200)));
            return;
        }

        for (Map.Entry<String, Album> entry : groups.entrySet()) {
            addAlbumCard(entry.getKey(), entry.getValue());
        }
    }

    private void addAlbumCard(String folder, Album album) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(SweetUi.dp(this, 10), SweetUi.dp(this, 10),
                SweetUi.dp(this, 10), SweetUi.dp(this, 10));
        card.setBackground(SweetUi.rounded(SweetUi.WHITE_GLASS, 22, this));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        MediaViews.thumbnail(cover, album.first);
        cover.setBackground(SweetUi.rounded(SweetUi.PINK_SOFT, 16, this));
        card.addView(cover, new LinearLayout.LayoutParams(
                SweetUi.dp(this, 88), SweetUi.dp(this, 88)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(SweetUi.dp(this, 14), 0, 0, 0);

        TextView name = SweetUi.label(this, cleanFolder(folder), 15, SweetUi.TEXT);
        name.setMaxLines(2);
        info.addView(name);

        info.addView(SweetUi.label(this,
                album.count + " 個媒體檔案", 12, SweetUi.MUTED));

        if (album.time > 0) {
            String date = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                    .format(new Date(album.time));
            info.addView(SweetUi.label(this, date, 11, SweetUi.MUTED));
        }

        card.addView(info, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = SweetUi.label(this, "›", 30, SweetUi.MUTED);
        card.addView(arrow);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, CollectionActivity.class);
            intent.putExtra("folder", folder);
            startActivity(intent);
        });

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, SweetUi.dp(this, 12));
        content.addView(card, cp);
    }

    private void showAll() {
        setTab(false);
        content.removeAllViews();

        List<Uri> items = loadAll();
        if (items.isEmpty()) {
            TextView empty = SweetUi.label(this, "尚未下載圖片或影片", 15, SweetUi.MUTED);
            empty.setGravity(Gravity.CENTER);
            content.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 200)));
            return;
        }

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);

        int width = getResources().getDisplayMetrics().widthPixels
                - SweetUi.dp(this, 36) - SweetUi.dp(this, 8);
        int cell = width / 3;

        for (Uri uri : items) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            MediaViews.thumbnail(image, uri);

            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = cell;
            gp.height = cell;
            gp.setMargins(SweetUi.dp(this, 2), SweetUi.dp(this, 2),
                    SweetUi.dp(this, 2), SweetUi.dp(this, 2));
            grid.addView(image, gp);

            image.setOnClickListener(v -> openFullscreen(uri));
        }

        content.addView(grid);
    }

    private LinkedHashMap<String, Album> loadAlbums() {
        LinkedHashMap<String, Album> groups = new LinkedHashMap<>();
        try {
            JSONArray arr = new JSONArray(getSharedPreferences("sweet_gallery", MODE_PRIVATE)
                    .getString("items", "[]"));

            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                String folder = o.optString("folder", "Sweet");
                String uri = o.optString("uri", "");
                if (uri.isEmpty()) continue;

                Album album = groups.get(folder);
                if (album == null) {
                    album = new Album(Uri.parse(uri), 0, o.optLong("time", 0));
                    groups.put(folder, album);
                }
                album.count++;
                album.time = Math.max(album.time, o.optLong("time", 0));
            }
        } catch (Exception ignored) {}
        return groups;
    }

    private List<Uri> loadAll() {
        List<Uri> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(getSharedPreferences("sweet_gallery", MODE_PRIVATE)
                    .getString("items", "[]"));

            for (int i = arr.length() - 1; i >= 0; i--) {
                String uri = arr.getJSONObject(i).optString("uri", "");
                if (!uri.isEmpty()) out.add(Uri.parse(uri));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void openFullscreen(Uri uri) {
        if (MediaViews.openVideo(this, uri)) return;
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        MediaViews.thumbnail(image, uri);
        image.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(image, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private LinearLayout buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(SweetUi.rounded(0xEEFFFFFF, 22, this));

        Button home = SweetUi.nav(this, "⌂\n首頁", false);
        Button history = SweetUi.nav(this, "▣\n下載記錄", false);
        Button gallery = SweetUi.nav(this, "▧\nGallery", true);

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
        history.setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
            finish();
        });

        return nav;
    }

    private String cleanFolder(String f) {
        return f.replaceFirst("_[0-9]{10,}$", "");
    }

    static class Album {
        final Uri first;
        int count;
        long time;

        Album(Uri first, int count, long time) {
            this.first = first;
            this.count = count;
            this.time = time;
        }
    }
}
