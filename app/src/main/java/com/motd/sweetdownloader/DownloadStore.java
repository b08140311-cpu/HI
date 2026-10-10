package com.motd.sweetdownloader;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;

final class DownloadStore {
    private final Context context;
    DownloadStore(Context context) { this.context = context; }

    synchronized String folder(String source, String title) throws IOException {
        android.content.SharedPreferences prefs = context.getSharedPreferences("sweet_sessions", Context.MODE_PRIVATE);
        String key = HttpTransfer.id(source);
        String old = prefs.getString(key, null);
        if (old != null) return old;
        String name = MediaExtractor.safeName(title) + "_" + System.currentTimeMillis();
        if (!prefs.edit().putString(key, name).commit()) throw new IOException("無法儲存下載紀錄");
        return name;
    }
    synchronized boolean alreadySaved(String url, String profile) {
        JSONArray items = read("sweet_gallery");
        for (int i = items.length() - 1; i >= 0; i--) {
            JSONObject o = items.optJSONObject(i);
            if (o == null || !url.equals(o.optString("download_url")) || !profile.equals(o.optString("profile"))) continue;
            try {
                Uri uri = Uri.parse(o.getString("uri"));
                if ("file".equals(uri.getScheme())) { if (new File(uri.getPath()).isFile()) return true; }
                else try (android.content.res.AssetFileDescriptor fd = context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
                    if (fd != null) return true;
                }
            } catch (Exception ignored) { /* Deleted files should be downloaded again. */ }
        }
        return false;
    }
    synchronized void media(String folder, String name, Uri uri, String source, String url,
                            String profile, MediaExtractor.Kind kind, String mime) throws Exception {
        JSONArray arr = read("sweet_gallery");
        JSONObject item = new JSONObject();
        item.put("folder", folder).put("name", name).put("uri", uri.toString()).put("source", source)
                .put("download_url", url).put("profile", profile).put("kind", kind.name())
                .put("mime", mime).put("time", System.currentTimeMillis());
        arr.put(item);
        write("sweet_gallery", arr);
    }
    synchronized void summary(String folder, String source, int images, int videos, int pages,
                              int failed, int skipped, String outcome, JSONArray errors) throws Exception {
        JSONArray arr = read("sweet_downloads");
        JSONObject item = new JSONObject();
        item.put("folder", folder).put("source", source).put("saved", images + videos).put("images", images)
                .put("videos", videos).put("pages", pages).put("failed", failed).put("skipped", skipped)
                .put("outcome", outcome).put("errors", errors).put("time", System.currentTimeMillis());
        arr.put(item);
        write("sweet_downloads", arr);
    }
    private JSONArray read(String prefs) {
        try { return new JSONArray(context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString("items", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    private void write(String prefs, JSONArray items) throws IOException {
        if (!context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putString("items", items.toString()).commit())
            throw new IOException("無法儲存下載紀錄");
    }
}
