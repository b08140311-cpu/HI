package com.motd.sweetdownloader;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MediaViews {
    private static final ExecutorService previews = Executors.newFixedThreadPool(2);
    static boolean isVideo(Context context, Uri uri) {
        try {
            JSONArray arr = new JSONArray(context.getSharedPreferences("sweet_gallery", Context.MODE_PRIVATE).getString("items", "[]"));
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && uri.toString().equals(o.optString("uri"))) return "VIDEO".equals(o.optString("kind"));
            }
        } catch (Exception ignored) { }
        return false;
    }
    static void thumbnail(ImageView view, Uri uri) {
        if (isVideo(view.getContext(), uri)) { view.setImageResource(android.R.drawable.ic_media_play); return; }
        view.setTag(uri.toString());
        previews.execute(() -> {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inJustDecodeBounds = true;
                try (InputStream in = view.getContext().getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, opts); }
                opts.inJustDecodeBounds = false; opts.inSampleSize = 1;
                while (Math.max(opts.outWidth, opts.outHeight) / opts.inSampleSize > 1200) opts.inSampleSize *= 2;
                Bitmap bitmap;
                try (InputStream in = view.getContext().getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in, null, opts); }
                view.post(() -> {
                    if (bitmap != null && uri.toString().equals(view.getTag())) view.setImageBitmap(bitmap);
                    else if (bitmap != null) bitmap.recycle();
                });
            } catch (Exception ignored) { view.post(() -> view.setImageResource(android.R.drawable.ic_menu_report_image)); }
        });
    }
    static boolean openVideo(Context context, Uri uri) {
        if (!isVideo(context, uri)) return false;
        context.startActivity(new Intent(context, VideoActivity.class).putExtra("uri", uri.toString())); return true;
    }
}
