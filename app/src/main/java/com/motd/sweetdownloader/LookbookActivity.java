package com.motd.sweetdownloader;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class LookbookActivity extends Activity {

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String folder = getIntent().getStringExtra("folder");
        if (folder == null) folder = "Sweet";
        render(folder);
    }

    private void render(String folder) {
        int bg = Color.rgb(18, 15, 24);
        int pink = Color.rgb(255, 143, 197);
        int text = Color.rgb(255, 232, 243);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(22), 0, dp(36));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("LOOKBOOK");
        title.setTextSize(22);
        title.setTextColor(pink);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText(folder);
        subtitle.setTextSize(11);
        subtitle.setTextColor(text);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(dp(18), dp(4), dp(18), dp(18));
        root.addView(subtitle, subParams);

        List<Uri> items = loadFolder(folder);

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No images in this collection");
            empty.setTextColor(text);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
        } else {
            for (int i = 0; i < items.size(); i++) {
                final Uri uri = items.get(i);

                TextView index = new TextView(this);
                index.setText(String.format("%02d / %02d", i + 1, items.size()));
                index.setTextColor(Color.argb(170, 255, 232, 243));
                index.setTextSize(10);
                index.setPadding(dp(18), 0, 0, dp(5));
                root.addView(index, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                ImageView image = new ImageView(this);
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setBackground(new ColorDrawable(Color.BLACK));
                image.setImageURI(uri);
                image.setOnClickListener(v -> openFullscreen(uri));

                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                imageParams.setMargins(0, 0, 0, dp(18));
                root.addView(image, imageParams);
            }
        }

        setContentView(scroll);
    }

    private List<Uri> loadFolder(String folder) {
        List<Uri> out = new ArrayList<>();
        try {
            String raw = getSharedPreferences("sweet_gallery", MODE_PRIVATE)
                    .getString("items", "[]");
            JSONArray arr = new JSONArray(raw);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!folder.equals(o.optString("folder", ""))) continue;
                String uri = o.optString("uri", "");
                if (!uri.isEmpty()) out.add(Uri.parse(uri));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void openFullscreen(Uri uri) {
        final android.app.Dialog dialog = new android.app.Dialog(this);
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
}
