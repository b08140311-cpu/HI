package com.motd.sweetdownloader;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText urlInput;
    private Button downloadButton;
    private TextView status;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        if (Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        int pink = Color.rgb(247, 168, 199);
        int dark = Color.rgb(123, 56, 88);
        int light = Color.rgb(255, 244, 248);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(48), dp(28), dp(28));
        root.setBackgroundColor(pink);

        ImageView heart = new ImageView(this);
        heart.setImageResource(R.drawable.heart);
        root.addView(heart, new LinearLayout.LayoutParams(dp(120), dp(120)));

        TextView title = new TextView(this);
        title.setText("Sweet");
        title.setTextSize(28);
        title.setTextColor(light);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(8), 0, dp(34));
        root.addView(title, titleParams);

        urlInput = new EditText(this);
        urlInput.setHint("Paste URL");
        urlInput.setSingleLine(true);
        urlInput.setTextSize(15);
        urlInput.setTextColor(dark);
        urlInput.setHintTextColor(Color.argb(140, 123, 56, 88));
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setBackgroundColor(Color.argb(190, 255, 255, 255));
        urlInput.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        inputParams.setMargins(0, 0, 0, dp(16));
        root.addView(urlInput, inputParams);

        downloadButton = new Button(this);
        downloadButton.setText("DOWNLOAD");
        downloadButton.setTextColor(dark);
        downloadButton.setTextSize(14);
        downloadButton.setAllCaps(false);
        downloadButton.setBackgroundColor(Color.rgb(255, 214, 231));
        root.addView(downloadButton,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        status = new TextView(this);
        status.setText("Ready");
        status.setTextColor(dark);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(20), 0, 0);
        root.addView(status, statusParams);

        setContentView(root);
        downloadButton.setOnClickListener(v -> startDownload());
    }

    private void startDownload() {
        String pageUrl = urlInput.getText().toString().trim();
        if (!pageUrl.startsWith("http://") && !pageUrl.startsWith("https://")) {
            Toast.makeText(this, "Please enter a valid http/https URL", Toast.LENGTH_SHORT).show();
            return;
        }

        downloadButton.setEnabled(false);
        setStatus("Scanning...");

        executor.execute(() -> {
            try {
                DownloadResult result = scanAndDownload(pageUrl);
                runOnUiThread(() -> {
                    status.setText("Downloaded " + result.saved +
                            " images\nPictures/Sweet/" + result.folder);
                    Toast.makeText(this, "Download complete", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Error: " +
                        (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            } finally {
                runOnUiThread(() -> downloadButton.setEnabled(true));
            }
        });
    }

    private DownloadResult scanAndDownload(String pageUrl) throws Exception {
        Document doc = Jsoup.connect(pageUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/141 Mobile Safari/537.36")
                .referrer(pageUrl)
                .timeout(30000)
                .followRedirects(true)
                .get();

        String resolved = doc.location();
        String folder = detectTitle(doc, resolved) + "_" + System.currentTimeMillis();
        LinkedHashSet<String> urls = extractImages(doc, resolved);
        setStatus("Found " + urls.size() + " candidates");

        Set<String> signatures = new LinkedHashSet<>();
        int saved = 0;
        int index = 1;

        for (String imageUrl : urls) {
            try {
                byte[] data = fetchBytes(imageUrl, resolved);
                if (data.length < 12000) continue;

                String sig = data.length + ":" + quickHash(data);
                if (!signatures.add(sig)) continue;

                String ext = extension(imageUrl, data);
                saveToPictures(folder, String.format(Locale.US, "%03d.%s", index++, ext),
                        data, ext);
                saved++;
                setStatus("Downloading " + saved + "/" + urls.size());
            } catch (Exception ignored) {
            }
        }
        return new DownloadResult(folder, saved);
    }

    private LinkedHashSet<String> extractImages(Document doc, String base) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String[] attrs = {
                "src", "data-src", "data-original", "data-lazy-src", "data-image",
                "data-url", "data-full", "data-full-src", "data-large", "data-zoom-image",
                "data-hires", "data-original-src", "data-master", "data-image-url",
                "data-preview", "poster", "file", "zoomfile", "data-file",
                "data-zoomfile", "data-original-url", "data-attachment", "data-download"
        };

        for (Element e : doc.select("img,source,video")) {
            for (String attr : attrs) addUrl(out, e.attr(attr), base);
            for (String attr : new String[]{"srcset", "data-srcset", "data-lazy-srcset"}) {
                String value = e.attr(attr);
                if (!value.isEmpty()) {
                    for (String part : value.split(",")) {
                        String[] bits = part.trim().split("\\s+");
                        if (bits.length > 0) addUrl(out, bits[0], base);
                    }
                }
            }
        }

        for (Element e : doc.select(
                "meta[property=og:image],meta[property=og:image:url]," +
                "meta[property=og:image:secure_url],meta[name=twitter:image]," +
                "meta[name=twitter:image:src],meta[itemprop=image]")) {
            addUrl(out, e.attr("content"), base);
        }

        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            String lower = href.toLowerCase(Locale.ROOT);
            if (lower.matches(".*\\.(jpg|jpeg|png|webp|gif|avif|bmp)(\\?.*)?$")) {
                addUrl(out, href, base);
            }
        }

        out.removeIf(u -> {
            String lower = u.toLowerCase(Locale.ROOT);
            return lower.contains("favicon") || lower.contains("sprite") ||
                    lower.contains("/logo") || lower.contains("avatar") ||
                    lower.contains("emoji") || lower.contains("badge") ||
                    lower.contains("tracking") || lower.contains("pixel.");
        });

        return out;
    }

    private void addUrl(Set<String> out, String raw, String base) {
        if (raw == null) return;
        raw = raw.trim();
        if (raw.isEmpty() || raw.startsWith("data:") || raw.startsWith("blob:") ||
                raw.startsWith("javascript:")) return;
        try {
            URL url = new URL(new URL(base), raw);
            String value = url.toString();
            if (value.startsWith("http://") || value.startsWith("https://")) out.add(value);
        } catch (Exception ignored) {
        }
    }

    private String detectTitle(Document doc, String pageUrl) {
        Element og = doc.selectFirst("meta[property=og:title],meta[name=twitter:title]");
        if (og != null && !og.attr("content").trim().isEmpty()) {
            return sanitize(og.attr("content"));
        }

        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().trim().isEmpty()) return sanitize(h1.text());

        if (!doc.title().trim().isEmpty()) return sanitize(doc.title());

        try {
            return sanitize(new URL(pageUrl).getHost().replace("www.", ""));
        } catch (Exception e) {
            return "Sweet_Gallery";
        }
    }

    private String sanitize(String s) {
        if (s == null) return "Sweet_Gallery";
        s = s.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", " ")
                .replaceAll("\\s+", " ").trim();
        if (s.length() > 80) s = s.substring(0, 80);
        return s.isEmpty() ? "Sweet_Gallery" : s;
    }

    private byte[] fetchBytes(String url, String referer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/141 Mobile Safari/537.36");
        connection.setRequestProperty("Referer", referer);

        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = input.read(buffer)) != -1) {
                output.write(buffer, 0, n);
                if (output.size() > 35 * 1024 * 1024) break;
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private long quickHash(byte[] data) {
        long h = 1125899906842597L;
        int step = Math.max(1, data.length / 2048);
        for (int i = 0; i < data.length; i += step) h = 31 * h + (data[i] & 255);
        return h;
    }

    private String extension(String url, byte[] data) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".png")) return "png";
        if (lower.contains(".webp")) return "webp";
        if (lower.contains(".gif")) return "gif";
        if (data.length > 4 && (data[0] & 255) == 0x89 && data[1] == 'P') return "png";
        return "jpg";
    }

    private void saveToPictures(String folder, String name, byte[] data, String ext)
            throws Exception {
        String mime = ext.equals("png") ? "image/png" :
                ext.equals("webp") ? "image/webp" :
                        ext.equals("gif") ? "image/gif" : "image/jpeg";

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, mime);
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Sweet/" + folder);

            Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Cannot create image file");

            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("Cannot open output");
                out.write(data);
            }
        } else {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Sweet/" + folder);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create folder");
            try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
                out.write(data);
            }
        }
    }

    private void setStatus(String text) {
        runOnUiThread(() -> status.setText(text));
    }

    static class DownloadResult {
        final String folder;
        final int saved;

        DownloadResult(String folder, int saved) {
            this.folder = folder;
            this.saved = saved;
        }
    }
}
