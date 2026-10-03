package com.motd.sweetdownloader;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GalleryActivity extends Activity {
    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    private void render() {
        int pink = Color.rgb(247, 168, 199);
        int dark = Color.rgb(123, 56, 88);
        int light = Color.rgb(255, 244, 248);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(pink);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(30));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Gallery");
        title.setTextSize(28);
        title.setTextColor(light);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("Sweet downloads");
        subtitle.setTextSize(12);
        subtitle.setTextColor(dark);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(0, dp(2), 0, dp(20));
        root.addView(subtitle, subParams);

        LinkedHashMap<String, List<GalleryItem>> groups = loadItems();
        if (groups.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No downloaded images yet");
            empty.setTextColor(dark);
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(160));
            root.addView(empty, p);
        } else {
            for (Map.Entry<String, List<GalleryItem>> entry : groups.entrySet()) {
                addCollection(root, entry.getKey(), entry.getValue(), dark);
            }
        }

        setContentView(scroll);
    }

    private LinkedHashMap<String, List<GalleryItem>> loadItems() {
        LinkedHashMap<String, List<GalleryItem>> groups = new LinkedHashMap<>();
        try {
            String raw = getSharedPreferences("sweet_gallery", MODE_PRIVATE)
                    .getString("items", "[]");
            JSONArray arr = new JSONArray(raw);

            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                String folder = o.optString("folder", "Sweet");
                String uri = o.optString("uri", "");
                String name = o.optString("name", "");
                if (uri.isEmpty()) continue;

                List<GalleryItem> list = groups.get(folder);
                if (list == null) {
                    list = new ArrayList<>();
                    groups.put(folder, list);
                }
                list.add(new GalleryItem(name, Uri.parse(uri)));
            }
        } catch (Exception ignored) {
        }
        return groups;
    }

    private void addCollection(LinearLayout root, String folder, List<GalleryItem> items, int dark) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundColor(Color.argb(185, 255, 244, 248));

        TextView name = new TextView(this);
        name.setText(folder + "  ·  " + items.size());
        name.setTextColor(dark);
        name.setTextSize(14);
        card.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(true);

        for (GalleryItem item : items) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.argb(80, 255, 255, 255));
            image.setImageURI(item.uri);

            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = dp(100);
            gp.height = dp(100);
            gp.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(image, gp);

            image.setOnClickListener(v -> openPreview(item.uri));
        }

        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.setMargins(0, dp(10), 0, 0);
        card.addView(grid, gridParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(14));
        root.addView(card, cardParams);
    }

    private void openPreview(Uri uri) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageURI(uri);
        image.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(image, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
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
