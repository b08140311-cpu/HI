package com.motd.sweetdownloader;

import android.content.SharedPreferences;

final class DownloadOptions {
    final int maxPages, minSize, workers;
    final boolean dedupe;
    final String imageFormat;
    DownloadOptions(SharedPreferences p) {
        maxPages = Math.max(1, Math.min(50, p.getInt("max_pages", 12)));
        minSize = Math.max(0, Math.min(10000, p.getInt("min_size", 300)));
        workers = Math.max(1, Math.min(4, p.getInt("workers", 2)));
        dedupe = p.getBoolean("dedupe", true);
        imageFormat = p.getString("image_format", "ORIGINAL");
    }
    String profile(MediaExtractor.Kind kind) {
        return kind == MediaExtractor.Kind.IMAGE ? imageFormat + ":" + minSize : "VIDEO";
    }
}
