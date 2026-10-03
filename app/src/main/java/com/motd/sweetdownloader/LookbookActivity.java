package com.motd.sweetdownloader;

import android.app.Activity;
import android.graphics.Color;
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

import java.util.ArrayList;
import java.util.List;

public class LookbookActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String folder = getIntent().getStringExtra("folder");
        if (folder == null) folder = "Sweet";
        render(folder);
    }

    private void render(String folder) {
        int bg = Color.rgb(12, 10, 14);
        int text = Color.rgb(250, 244, 247);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, SweetUi.dp(this, 16), 0, SweetUi.dp(this, 30));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(SweetUi.dp(this, 10), 0, SweetUi.dp(this, 12), 0);

        Button close = new Button(this);
        close.setText("×");
        close.setTextSize(26);
        close.setTextColor(Color.WHITE);
        close.setBackgroundColor(Color.TRANSPARENT);
        header.addView(close, new LinearLayout.LayoutParams(
                SweetUi.dp(this, 52), SweetUi.dp(this, 48)));
        close.setOnClickListener(v -> finish());

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);

        TextView title = SweetUi.label(this, "LOOKBOOK", 18, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        titleBox.addView(title);

        List<Uri> items = loadFolder(folder);
        TextView count = SweetUi.label(this, items.size() + " 張圖片", 11,
                Color.rgb(205, 192, 199));
        count.setGravity(Gravity.CENTER);
        titleBox.addView(count);

        header.addView(titleBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView spacer = new TextView(this);
        header.addView(spacer, new LinearLayout.LayoutParams(
                SweetUi.dp(this, 52), SweetUi.dp(this, 48)));

        root.addView(header);

        for (int i = 0; i < items.size(); i++) {
            Uri uri = items.get(i);

            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.BLACK);
            image.setImageURI(uri);
            image.setOnClickListener(v -> openFullscreen(uri));

            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            ip.setMargins(SweetUi.dp(this, 10), SweetUi.dp(this, 10),
                    SweetUi.dp(this, 10), 0);
            root.addView(image, ip);

            TextView index = SweetUi.label(this,
                    (i + 1) + " / " + items.size(), 10, Color.rgb(170, 158, 164));
            index.setGravity(Gravity.END);
            index.setPadding(0, SweetUi.dp(this, 4), SweetUi.dp(this, 14),
                    SweetUi.dp(this, 4));
            root.addView(index);
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
        } catch (Exception ignored) {}
        return out;
    }

    private void openFullscreen(Uri uri) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageURI(uri);
        shell.addView(image, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);

        TextView download = SweetUi.label(this, "⇩\n下載", 11, Color.WHITE);
        download.setGravity(Gravity.CENTER);
        TextView favorite = SweetUi.label(this, "♡\n加入收藏", 11, Color.WHITE);
        favorite.setGravity(Gravity.CENTER);
        TextView close = SweetUi.label(this, "×\n關閉", 11, Color.WHITE);
        close.setGravity(Gravity.CENTER);

        actions.addView(download, new LinearLayout.LayoutParams(
                0, SweetUi.dp(this, 72), 1f));
        actions.addView(favorite, new LinearLayout.LayoutParams(
                0, SweetUi.dp(this, 72), 1f));
        actions.addView(close, new LinearLayout.LayoutParams(
                0, SweetUi.dp(this, 72), 1f));

        close.setOnClickListener(v -> dialog.dismiss());
        shell.addView(actions);

        dialog.setContentView(shell);
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
}
