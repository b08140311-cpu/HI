package com.motd.sweetdownloader;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MediaWriter {
    static final class Saved {
        final Uri uri; final String name, mime;
        Saved(Uri uri, String name, String mime) { this.uri = uri; this.name = name; this.mime = mime; }
    }
    private final Context context;
    private final DownloadOptions options;
    private final HttpTransfer.Cancel cancel;
    private final List<long[]> hashes = new ArrayList<>();
    MediaWriter(Context context, DownloadOptions options, HttpTransfer.Cancel cancel) {
        this.context = context; this.options = options; this.cancel = cancel;
    }
    synchronized Saved image(File file, String type, String folder, String url) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("不是可解碼的圖片");
        if (Math.min(bounds.outWidth, bounds.outHeight) < options.minSize) return null;
        // Bound bitmap memory, but keep the original file unchanged in ORIGINAL mode.
        BitmapFactory.Options sample = new BitmapFactory.Options();
        while ((long) (bounds.outWidth / Math.max(1, sample.inSampleSize)) *
                (bounds.outHeight / Math.max(1, sample.inSampleSize)) > 16000000L) {
            sample.inSampleSize = Math.max(1, sample.inSampleSize) * 2;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), sample);
        if (bitmap == null) throw new IOException("無法解碼圖片");
        try {
            long[] hash = hash(bitmap);
            if (options.dedupe) for (long[] old : hashes) {
                int distance = 0;
                for (int i = 0; i < 4; i++) distance += Long.bitCount(old[i] ^ hash[i]);
                if (distance <= 10) return null;
            }
            String mime = bounds.outMimeType == null ? type : bounds.outMimeType;
            File output = file;
            File converted = null;
            try {
                if (!options.imageFormat.equals("ORIGINAL")) {
                    // Never silently downscale a requested format conversion.
                    if (sample.inSampleSize > 1) throw new IOException("圖片太大，請選原始格式以保留完整畫質");
                    converted = new File(context.getCacheDir(), "converted-" + HttpTransfer.id(url));
                    mime = options.imageFormat.equals("PNG") ? "image/png" : "image/jpeg";
                    try (OutputStream out = new FileOutputStream(converted)) {
                        Bitmap target = bitmap;
                        if (mime.equals("image/jpeg") && bitmap.hasAlpha()) {
                            target = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                            android.graphics.Canvas canvas = new android.graphics.Canvas(target);
                            canvas.drawColor(android.graphics.Color.WHITE);
                            canvas.drawBitmap(bitmap, 0, 0, null);
                        }
                        try {
                            if (!target.compress(mime.equals("image/png") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 94, out))
                                throw new IOException("圖片格式轉換失敗");
                        } finally { if (target != bitmap) target.recycle(); }
                    }
                    output = converted;
                }
                Saved saved = publish(output, folder, fileName(url, mime), mime, MediaExtractor.Kind.IMAGE);
                if (options.dedupe) hashes.add(hash);
                return saved;
            } finally { if (converted != null) converted.delete(); }
        } finally { bitmap.recycle(); }
    }
    Saved video(File file, String type, String folder, String url) throws Exception {
        String mime = type;
        byte[] header = new byte[16];
        int n;
        try (InputStream in = new FileInputStream(file)) { n = in.read(header); }
        if (n >= 8 && (word(header, 4).equals("ftyp") || word(header, 4).equals("moov") || word(header, 4).equals("mdat"))) {
            mime = "video/mp4";
        } else if (n >= 4 && (header[0] & 255) == 0x1a && (header[1] & 255) == 0x45
                && (header[2] & 255) == 0xdf && (header[3] & 255) == 0xa3) {
            mime = type.equals("video/x-matroska") || URI.create(url).getPath().toLowerCase(Locale.ROOT).endsWith(".mkv") ? "video/x-matroska" : "video/webm";
        } else if (n >= 12 && word(header, 0).equals("RIFF") && word(header, 8).equals("AVI ")) {
            mime = "video/x-msvideo";
        } else if (n >= 1 && (header[0] & 255) == 0x47 && file.length() >= 188) {
            mime = "video/mp2t";
        } else { throw new IOException("不是支援的影片檔案，可能是登入頁或錯誤回應"); }
        return publish(file, folder, fileName(url, mime), mime, MediaExtractor.Kind.VIDEO);
    }
    private static String word(byte[] data, int offset) { return new String(data, offset, 4, java.nio.charset.StandardCharsets.US_ASCII); }
    private Saved publish(File file, String folder, String name, String mime, MediaExtractor.Kind kind) throws Exception {
        boolean video = kind == MediaExtractor.Kind.VIDEO;
        String directory = video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, directory + "/Sweet/" + folder);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri collection = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Uri uri = context.getContentResolver().insert(collection, values);
            if (uri == null) throw new IOException("無法建立媒體檔案");
            try {
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("無法寫入手機儲存空間");
                    copy(file, out);
                }
                ContentValues complete = new ContentValues(); complete.put(MediaStore.MediaColumns.IS_PENDING, 0);
                if (context.getContentResolver().update(uri, complete, null, null) != 1) throw new IOException("無法完成媒體儲存");
                return new Saved(uri, name, mime);
            } catch (Exception e) { context.getContentResolver().delete(uri, null, null); throw e; }
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(directory), "Sweet/" + folder);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("無法建立儲存目錄，請允許儲存權限");
        File target = new File(dir, name);
        for (int i = 2; target.exists(); i++) target = new File(dir, i + "_" + name);
        try (OutputStream out = new FileOutputStream(target)) { copy(file, out); }
        catch (Exception e) { target.delete(); throw e; }
        MediaScannerConnection.scanFile(context, new String[]{target.getPath()}, new String[]{mime}, null);
        return new Saved(Uri.fromFile(target), target.getName(), mime);
    }
    void delete(Saved saved) {
        if ("file".equals(saved.uri.getScheme())) new File(saved.uri.getPath()).delete();
        else context.getContentResolver().delete(saved.uri, null, null);
    }
    private void copy(File file, OutputStream out) throws Exception {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) {
                if (cancel.cancelled() || Thread.currentThread().isInterrupted()) throw new InterruptedException("已取消");
                out.write(buffer, 0, n);
            }
        }
    }
    static String fileName(String url, String mime) {
        String path = URI.create(url).getPath();
        String source = path.substring(path.lastIndexOf('/') + 1).replaceAll("\\.[^.]+$", "");
        source = MediaExtractor.safeName(source);
        String ext;
        switch (mime) {
            case "image/jpeg": ext = "jpg"; break;
            case "image/png": ext = "png"; break;
            case "image/gif": ext = "gif"; break;
            case "image/webp": ext = "webp"; break;
            case "image/avif": ext = "avif"; break;
            case "image/bmp": ext = "bmp"; break;
            case "image/heif": ext = "heif"; break;
            case "image/heic": ext = "heic"; break;
            case "image/vnd.wap.wbmp": ext = "wbmp"; break;
            case "image/x-icon": ext = "ico"; break;
            case "video/webm": ext = "webm"; break;
            case "video/x-matroska": ext = "mkv"; break;
            case "video/x-msvideo": ext = "avi"; break;
            case "video/mp2t": ext = "ts"; break;
            case "video/mp4": ext = "mp4"; break;
            default: ext = "bin";
        }
        return source + "_" + HttpTransfer.id(url).substring(0, 8) + "." + ext;
    }
    private static long[] hash(Bitmap bitmap) {
        Bitmap thumb = Bitmap.createScaledBitmap(bitmap, 16, 16, true);
        int[] px = new int[256]; thumb.getPixels(px, 0, 16, 0, 0, 16, 16);
        if (thumb != bitmap) thumb.recycle();
        int[] gray = new int[256]; long sum = 0;
        for (int i = 0; i < 256; i++) {
            int c = px[i]; gray[i] = ((c >> 16 & 255) * 299 + (c >> 8 & 255) * 587 + (c & 255) * 114) / 1000;
            sum += gray[i];
        }
        long[] bits = new long[4];
        for (int i = 0; i < 256; i++) if (gray[i] >= sum / 256) bits[i / 64] |= 1L << (i % 64);
        return bits;
    }
}
