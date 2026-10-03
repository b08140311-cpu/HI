package com.motd.sweetdownloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CollectionActivity extends Activity {
    private String folder;
    private List<GalleryItem> items;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        folder = getIntent().getStringExtra("folder");
        if (folder == null) folder = "Sweet";
        items = loadFolder(folder);
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(SweetUi.pinkGradient(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(SweetUi.dp(this, 14), SweetUi.dp(this, 22),
                SweetUi.dp(this, 14), SweetUi.dp(this, 28));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button back = SweetUi.pill(this, "‹", false);
        header.addView(back, new LinearLayout.LayoutParams(
                SweetUi.dp(this, 46), SweetUi.dp(this, 42)));
        back.setOnClickListener(v -> finish());

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(SweetUi.dp(this, 10), 0, 0, 0);

        TextView title = SweetUi.title(this, cleanFolder(folder), 20);
        title.setMaxLines(1);
        titleBox.addView(title);

        TextView meta = SweetUi.label(this,
                items.size() + " 張圖片", 12, SweetUi.MUTED);
        titleBox.addView(meta);

        header.addView(titleBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button menu = SweetUi.pill(this, "⋮", false);
        header.addView(menu, new LinearLayout.LayoutParams(
                SweetUi.dp(this, 48), SweetUi.dp(this, 42)));
        menu.setOnClickListener(v -> showMenu());

        root.addView(header);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button lookbook = SweetUi.pill(this, "♡  LOOKBOOK", true);
        Button select = SweetUi.pill(this, "選單", false);

        actions.addView(lookbook, new LinearLayout.LayoutParams(
                0, SweetUi.dp(this, 46), 1f));
        LinearLayout.LayoutParams selp = new LinearLayout.LayoutParams(
                SweetUi.dp(this, 110), SweetUi.dp(this, 46));
        selp.setMargins(SweetUi.dp(this, 10), 0, 0, 0);
        actions.addView(select, selp);

        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 46));
        ap.setMargins(0, SweetUi.dp(this, 18), 0, SweetUi.dp(this, 14));
        root.addView(actions, ap);

        lookbook.setOnClickListener(v -> openLookbook());
        select.setOnClickListener(v -> showMenu());

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(false);

        int width = getResources().getDisplayMetrics().widthPixels
                - SweetUi.dp(this, 28) - SweetUi.dp(this, 8);
        int cell = width / 3;

        for (GalleryItem item : items) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageURI(item.uri);
            image.setBackground(SweetUi.rounded(SweetUi.PINK_SOFT, 14, this));

            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = cell;
            gp.height = cell;
            gp.setMargins(SweetUi.dp(this, 2), SweetUi.dp(this, 2),
                    SweetUi.dp(this, 2), SweetUi.dp(this, 2));
            grid.addView(image, gp);

            image.setOnClickListener(v -> openFullscreen(item.uri));
        }

        root.addView(grid);

        setContentView(scroll);
    }

    private void openLookbook() {
        Intent intent = new Intent(this, LookbookActivity.class);
        intent.putExtra("folder", folder);
        startActivity(intent);
    }

    private void showMenu() {
        String source = sourceForFolder(folder);
        String[] actions = new String[]{
                "查看 Lookbook",
                "複製來源網址",
                "刪除此合集"
        };

        new AlertDialog.Builder(this)
                .setTitle(cleanFolder(folder))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        openLookbook();
                    } else if (which == 1) {
                        if (source.isEmpty()) {
                            Toast.makeText(this, "這個舊合集沒有來源網址", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ClipboardManager cm = (ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("source", source));
                        Toast.makeText(this, "已複製網址", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        confirmDelete();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("刪除此合集？")
                .setMessage("會從 Sweet Gallery 索引移除，不會強制刪除手機相簿裡的原始圖片。")
                .setPositiveButton("刪除", (d, w) -> {
                    removeFolderFromIndex(folder);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void removeFolderFromIndex(String folder) {
        try {
            JSONArray arr = new JSONArray(getSharedPreferences("sweet_gallery", MODE_PRIVATE)
                    .getString("items", "[]"));
            JSONArray next = new JSONArray();

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!folder.equals(o.optString("folder", ""))) next.put(o);
            }

            getSharedPreferences("sweet_gallery", MODE_PRIVATE)
                    .edit().putString("items", next.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void openFullscreen(Uri uri) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageURI(uri);
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

    private List<GalleryItem> loadFolder(String folder) {
        List<GalleryItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(getSharedPreferences("sweet_gallery", MODE_PRIVATE)
                    .getString("items", "[]"));

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!folder.equals(o.optString("folder", ""))) continue;
                String uri = o.optString("uri", "");
                if (uri.isEmpty()) continue;

                out.add(new GalleryItem(
                        o.optString("name", ""),
                        Uri.parse(uri)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private String sourceForFolder(String folder) {
        try {
            JSONArray arr = new JSONArray(getSharedPreferences("sweet_downloads", MODE_PRIVATE)
                    .getString("items", "[]"));

            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                if (folder.equals(o.optString("folder", ""))) {
                    return o.optString("source", "");
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String cleanFolder(String f) {
        return f.replaceFirst("_[0-9]{10,}$", "");
    }

    static class GalleryItem {
        final String name;
        final Uri uri;

        GalleryItem(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }
}
