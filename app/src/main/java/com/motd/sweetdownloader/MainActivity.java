package com.motd.sweetdownloader;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import org.json.JSONArray;
import org.json.JSONObject;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int MAX_PAGES = 12;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/141 Mobile Safari/537.36";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText urlInput;
    private Button downloadButton;
    private TextView status;
    private ImageLabeler imageLabeler;
    private FaceDetector faceDetector;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        imageLabeler = com.google.mlkit.vision.label.ImageLabeling.getClient(
                ImageLabelerOptions.DEFAULT_OPTIONS);

        FaceDetectorOptions faceOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();
        faceDetector = FaceDetection.getClient(faceOptions);

        buildUi();

        if (Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (imageLabeler != null) imageLabeler.close();
        if (faceDetector != null) faceDetector.close();
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        int pink = Color.rgb(247, 168, 199);
        int dark = Color.rgb(123, 56, 88);
        int light = Color.rgb(255, 244, 248);

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(pink);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(48), dp(28), dp(88));

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
        status.setText("Ready · people only · auto pages");
        status.setTextColor(dark);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(20), 0, 0);
        root.addView(status, statusParams);

        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        shell.addView(root, rootParams);

        Button galleryButton = new Button(this);
        galleryButton.setText("GALLERY");
        galleryButton.setTextSize(11);
        galleryButton.setTextColor(dark);
        galleryButton.setAllCaps(false);
        galleryButton.setBackgroundColor(Color.argb(225, 255, 244, 248));
        FrameLayout.LayoutParams galleryParams =
                new FrameLayout.LayoutParams(dp(112), dp(48), Gravity.BOTTOM | Gravity.END);
        galleryParams.setMargins(0, 0, dp(18), dp(18));
        shell.addView(galleryButton, galleryParams);

        setContentView(shell);

        downloadButton.setOnClickListener(v -> startDownload());
        galleryButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, GalleryActivity.class)));
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
                            " people images · " + result.pages + " pages\nPictures/Sweet/" +
                            result.folder);
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
        Document firstDoc = fetchDocument(pageUrl);
        String resolved = firstDoc.location();
        String folder = detectTitle(firstDoc, resolved) + "_" + System.currentTimeMillis();

        List<String> pageQueue = new ArrayList<>();
        Set<String> seenPages = new LinkedHashSet<>();
        LinkedHashMap<String, String> allImages = new LinkedHashMap<>();

        pageQueue.add(resolved);
        seenPages.add(normalizePageUrl(resolved));

        int pageIndex = 0;
        while (pageIndex < pageQueue.size() && pageIndex < MAX_PAGES) {
            String currentUrl = pageQueue.get(pageIndex);
            Document doc = pageIndex == 0 ? firstDoc : fetchDocument(currentUrl);

            setStatus("Scanning page " + (pageIndex + 1) + "/" +
                    Math.min(MAX_PAGES, Math.max(pageQueue.size(), pageIndex + 1)));

            LinkedHashSet<String> pageImages = extractImages(doc, doc.location());
            for (String imageUrl : pageImages) {
                try {
                    URL parsed = new URL(imageUrl);
                    allImages.putIfAbsent(canonicalImageKey(parsed), imageUrl);
                } catch (Exception ignored) {
                }
            }

            if (pageQueue.size() < MAX_PAGES) {
                List<String> discovered = discoverPaginationLinks(
                        doc, doc.location(), resolved, MAX_PAGES - pageQueue.size());

                for (String next : discovered) {
                    String normalized = normalizePageUrl(next);
                    if (seenPages.add(normalized)) {
                        pageQueue.add(next);
                        if (pageQueue.size() >= MAX_PAGES) break;
                    }
                }
            }

            pageIndex++;
        }

        List<String> urls = new ArrayList<>(allImages.values());
        setStatus("Pages " + pageIndex + " · " + urls.size() + " candidates");

        List<long[]> seenHashes = new ArrayList<>();
        int saved = 0;
        int checked = 0;

        for (String imageUrl : urls) {
            checked++;
            try {
                byte[] data = fetchBytes(imageUrl, resolved);
                if (data.length < 12000) continue;

                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bitmap == null || Math.min(bitmap.getWidth(), bitmap.getHeight()) < 180) {
                    if (bitmap != null) bitmap.recycle();
                    continue;
                }

                long[] hash = perceptualHash(bitmap);
                if (isVisualDuplicate(hash, seenHashes)) {
                    bitmap.recycle();
                    continue;
                }

                setStatus("Checking people " + checked + "/" + urls.size());
                if (!containsPerson(bitmap)) {
                    bitmap.recycle();
                    continue;
                }

                seenHashes.add(hash);

                String ext = extension(imageUrl, data);
                String name = String.format(Locale.US, "%03d.%s", saved + 1, ext);
                Uri savedUri = saveToPictures(folder, name, data, ext);
                recordGalleryItem(folder, name, savedUri);

                saved++;
                bitmap.recycle();
                setStatus("Saved " + saved + " · page set " + pageIndex);
            } catch (Exception ignored) {
            }
        }

        return new DownloadResult(folder, saved, pageIndex);
    }

    private Document fetchDocument(String pageUrl) throws Exception {
        return Jsoup.connect(pageUrl)
                .userAgent(USER_AGENT)
                .referrer(pageUrl)
                .timeout(30000)
                .followRedirects(true)
                .get();
    }

    private List<String> discoverPaginationLinks(
            Document doc, String currentUrl, String rootUrl, int remaining) {
        List<String> found = new ArrayList<>();
        if (remaining <= 0) return found;

        try {
            URL root = new URL(rootUrl);
            String rootHost = root.getHost().toLowerCase(Locale.ROOT);
            String rootParent = parentPath(root.getPath());

            for (Element a : doc.select("a[href]")) {
                if (found.size() >= remaining) break;

                String raw = a.attr("href").trim();
                if (raw.isEmpty() || raw.startsWith("#") ||
                        raw.startsWith("javascript:") || raw.startsWith("mailto:")) {
                    continue;
                }

                URL candidate = new URL(new URL(currentUrl), raw);
                if (!candidate.getProtocol().equals("http") &&
                        !candidate.getProtocol().equals("https")) {
                    continue;
                }

                if (!candidate.getHost().toLowerCase(Locale.ROOT).equals(rootHost)) {
                    continue;
                }

                if (!parentPath(candidate.getPath()).equals(rootParent)) {
                    continue;
                }

                String text = a.text().trim().toLowerCase(Locale.ROOT);
                String rel = a.attr("rel").toLowerCase(Locale.ROOT);
                String cls = a.className().toLowerCase(Locale.ROOT);
                String href = candidate.toString().toLowerCase(Locale.ROOT);
                String query = candidate.getQuery() == null
                        ? "" : candidate.getQuery().toLowerCase(Locale.ROOT);
                String path = candidate.getPath().toLowerCase(Locale.ROOT);
                String marker = text + " " + rel + " " + cls;

                boolean looksPaged =
                        marker.contains("next") ||
                        marker.contains("pagination") ||
                        marker.contains("pager") ||
                        text.contains("下一") ||
                        text.contains("下頁") ||
                        text.contains("下页") ||
                        text.equals(">") ||
                        text.equals("›") ||
                        text.equals("»") ||
                        query.matches("(?i)(^|.*&)(page|paged|p)=\\d+(&.*|$)") ||
                        path.matches("(?i).*/page/\\d+/?$") ||
                        path.matches("(?i).*(?:[-_/])\\d+(?:\\.html?)?$");

                if (!looksPaged) continue;

                String normalized = normalizePageUrl(candidate.toString());
                if (normalized.equals(normalizePageUrl(currentUrl))) continue;

                boolean duplicate = false;
                for (String old : found) {
                    if (normalizePageUrl(old).equals(normalized)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) found.add(candidate.toString());
            }
        } catch (Exception ignored) {
        }

        return found;
    }

    private String parentPath(String path) {
        if (path == null || path.isEmpty()) return "/";
        int slash = path.lastIndexOf('/');
        if (slash < 0) return "/";
        return path.substring(0, slash + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizePageUrl(String value) {
        try {
            URL u = new URL(value);
            String protocol = u.getProtocol().toLowerCase(Locale.ROOT);
            String host = u.getHost().toLowerCase(Locale.ROOT);
            int port = u.getPort();

            StringBuilder out = new StringBuilder();
            out.append(protocol).append("://").append(host);
            if (port != -1 && port != u.getDefaultPort()) out.append(":").append(port);
            out.append(u.getPath().replaceAll("/+$", ""));

            if (u.getQuery() != null && !u.getQuery().isEmpty()) {
                out.append("?").append(u.getQuery());
            }
            return out.toString();
        } catch (Exception e) {
            return value;
        }
    }

    private boolean containsPerson(Bitmap bitmap) {
        try {
            InputImage input = InputImage.fromBitmap(bitmap, 0);

            List<Face> faces = Tasks.await(faceDetector.process(input));
            if (faces != null && !faces.isEmpty()) return true;

            List<ImageLabel> labels = Tasks.await(imageLabeler.process(input));
            if (labels != null) {
                Set<String> humanLabels = new LinkedHashSet<>(Arrays.asList(
                        "person", "people", "human", "portrait", "selfie", "face",
                        "man", "woman", "boy", "girl", "child", "baby"
                ));

                for (ImageLabel label : labels) {
                    String text = label.getText().toLowerCase(Locale.ROOT).trim();
                    if (label.getConfidence() >= 0.35f && humanLabels.contains(text)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private long[] perceptualHash(Bitmap source) {
        Bitmap scaled = Bitmap.createScaledBitmap(source, 16, 16, true);
        int[] pixels = new int[256];
        scaled.getPixels(pixels, 0, 16, 0, 0, 16, 16);
        if (scaled != source) scaled.recycle();

        long total = 0;
        int[] lum = new int[256];

        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            int y = (Color.red(c) * 299 +
                    Color.green(c) * 587 +
                    Color.blue(c) * 114) / 1000;
            lum[i] = y;
            total += y;
        }

        int avg = (int) (total / 256L);
        long[] bits = new long[4];

        for (int i = 0; i < 256; i++) {
            if (lum[i] >= avg) bits[i / 64] |= (1L << (i % 64));
        }
        return bits;
    }

    private boolean isVisualDuplicate(long[] hash, List<long[]> seen) {
        for (long[] old : seen) {
            int distance = 0;
            for (int i = 0; i < 4; i++) {
                distance += Long.bitCount(hash[i] ^ old[i]);
            }
            if (distance <= 14) return true;
        }
        return false;
    }

    private LinkedHashSet<String> extractImages(Document doc, String base) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();

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

        out.entrySet().removeIf(entry -> {
            String lower = entry.getValue().toLowerCase(Locale.ROOT);
            return lower.contains("favicon") ||
                    lower.contains("sprite") ||
                    lower.contains("/logo") ||
                    lower.contains("avatar") ||
                    lower.contains("emoji") ||
                    lower.contains("badge") ||
                    lower.contains("tracking") ||
                    lower.contains("pixel.");
        });

        return new LinkedHashSet<>(out.values());
    }

    private void addUrl(Map<String, String> out, String raw, String base) {
        if (raw == null) return;
        raw = raw.trim();

        if (raw.isEmpty() ||
                raw.startsWith("data:") ||
                raw.startsWith("blob:") ||
                raw.startsWith("javascript:")) {
            return;
        }

        try {
            URL url = new URL(new URL(base), raw);
            String value = url.toString();

            if (!value.startsWith("http://") && !value.startsWith("https://")) return;
            out.putIfAbsent(canonicalImageKey(url), value);
        } catch (Exception ignored) {
        }
    }

    private String canonicalImageKey(URL url) {
        StringBuilder key = new StringBuilder();

        key.append(url.getHost().toLowerCase(Locale.ROOT))
                .append(url.getPath().replaceAll("/+$", "").toLowerCase(Locale.ROOT));

        String query = url.getQuery();
        if (query == null || query.isEmpty()) return key.toString();

        Set<String> resizeKeys = new LinkedHashSet<>(Arrays.asList(
                "w", "width", "h", "height", "q", "quality", "format",
                "fit", "crop", "auto", "dpr", "resize"
        ));

        List<String> kept = new ArrayList<>();
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);

            if (!resizeKeys.contains(name)) kept.add(part);
        }

        if (!kept.isEmpty()) key.append("?").append(String.join("&", kept));
        return key.toString();
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
                .replaceAll("\\s+", " ")
                .trim();

        if (s.length() > 80) s = s.substring(0, 80);
        return s.isEmpty() ? "Sweet_Gallery" : s;
    }

    private byte[] fetchBytes(String url, String referer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
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

    private String extension(String url, byte[] data) {
        String lower = url.toLowerCase(Locale.ROOT);

        if (lower.contains(".png")) return "png";
        if (lower.contains(".webp")) return "webp";
        if (lower.contains(".gif")) return "gif";
        if (data.length > 4 && (data[0] & 255) == 0x89 && data[1] == 'P') return "png";

        return "jpg";
    }

    private Uri saveToPictures(String folder, String name, byte[] data, String ext)
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

            return uri;
        } else {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Sweet/" + folder);

            if (!dir.exists() && !dir.mkdirs()) {
                throw new Exception("Cannot create folder");
            }

            File file = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(data);
            }

            return Uri.fromFile(file);
        }
    }

    private void recordGalleryItem(String folder, String name, Uri uri) {
        try {
            SharedPreferences prefs = getSharedPreferences("sweet_gallery", MODE_PRIVATE);
            JSONArray array = new JSONArray(prefs.getString("items", "[]"));

            JSONObject item = new JSONObject();
            item.put("folder", folder);
            item.put("name", name);
            item.put("uri", uri.toString());
            item.put("time", System.currentTimeMillis());

            array.put(item);
            prefs.edit().putString("items", array.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void setStatus(String text) {
        runOnUiThread(() -> status.setText(text));
    }

    static class DownloadResult {
        final String folder;
        final int saved;
        final int pages;

        DownloadResult(String folder, int saved, int pages) {
            this.folder = folder;
            this.saved = saved;
            this.pages = pages;
        }
    }
}
