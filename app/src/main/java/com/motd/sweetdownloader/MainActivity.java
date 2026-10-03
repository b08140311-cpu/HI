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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/141 Mobile Safari/537.36";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText urlInput;
    private Button downloadButton;
    private TextView status;
    private LinearLayout progressCard;
    private ProgressBar progressBar;
    private TextView autoPagesSubtitle;
    private TextView peopleSubtitle;
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
    protected void onResume() {
        super.onResume();
        refreshSettingLabels();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (imageLabeler != null) imageLabeler.close();
        if (faceDetector != null) faceDetector.close();
    }

    private void buildUi() {
        FrameLayout shell = new FrameLayout(this);
        shell.setBackground(SweetUi.pinkGradient(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(SweetUi.dp(this, 22), SweetUi.dp(this, 34),
                SweetUi.dp(this, 22), SweetUi.dp(this, 110));
        scroll.addView(root);

        TextView title = SweetUi.title(this, "Sweet\nDownloader", 34);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.START);
        title.setShadowLayer(7f, 0f, 2f, Color.argb(90, 160, 70, 110));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView heart = new ImageView(this);
        heart.setImageResource(R.drawable.heart);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                SweetUi.dp(this, 128), SweetUi.dp(this, 128));
        hp.setMargins(0, SweetUi.dp(this, 6), 0, SweetUi.dp(this, 14));
        root.addView(heart, hp);

        urlInput = new EditText(this);
        urlInput.setHint("https://example.com/xxx");
        urlInput.setSingleLine(true);
        urlInput.setTextSize(14);
        urlInput.setTextColor(SweetUi.TEXT);
        urlInput.setHintTextColor(SweetUi.MUTED);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setPadding(SweetUi.dp(this, 16), 0, SweetUi.dp(this, 16), 0);
        urlInput.setBackground(SweetUi.rounded(0xEEFFFFFF, 25, this));
        root.addView(urlInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 54)));

        downloadButton = SweetUi.pill(this, "⇩  下載", true);
        LinearLayout.LayoutParams dbp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 56));
        dbp.setMargins(0, SweetUi.dp(this, 12), 0, SweetUi.dp(this, 16));
        root.addView(downloadButton, dbp);
        downloadButton.setOnClickListener(v -> startDownload());

        autoPagesSubtitle = addFeatureCard(root, "◎", "自動分頁",
                "最多 12 頁", v -> startActivity(new Intent(this, SettingsActivity.class)));

        peopleSubtitle = addFeatureCard(root, "●", "只抓有人圖片",
                "人臉 / 人物偵測", v -> startActivity(new Intent(this, SettingsActivity.class)));

        addFeatureCard(root, "⚙", "下載設定",
                "重複・畫質・頁數", v -> startActivity(new Intent(this, SettingsActivity.class)));

        progressCard = new LinearLayout(this);
        progressCard.setOrientation(LinearLayout.VERTICAL);
        progressCard.setPadding(SweetUi.dp(this, 16), SweetUi.dp(this, 14),
                SweetUi.dp(this, 16), SweetUi.dp(this, 14));
        progressCard.setBackground(SweetUi.rounded(0xEEFFF7FA, 22, this));
        progressCard.setVisibility(View.GONE);

        TextView pTitle = SweetUi.label(this, "正在下載…", 17, SweetUi.TEXT);
        pTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        progressCard.addView(pTitle);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams pbp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 8));
        pbp.setMargins(0, SweetUi.dp(this, 12), 0, SweetUi.dp(this, 10));
        progressCard.addView(progressBar, pbp);

        status = SweetUi.label(this, "準備完成", 12, SweetUi.MUTED);
        progressCard.addView(status);

        LinearLayout.LayoutParams pcp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pcp.setMargins(0, SweetUi.dp(this, 14), 0, 0);
        root.addView(progressCard, pcp);

        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        shell.addView(scroll, sp);

        LinearLayout nav = buildNav();
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 70),
                Gravity.BOTTOM);
        np.setMargins(SweetUi.dp(this, 12), 0, SweetUi.dp(this, 12),
                SweetUi.dp(this, 10));
        shell.addView(nav, np);

        setContentView(shell);
        refreshSettingLabels();
    }

    private TextView addFeatureCard(LinearLayout root, String icon, String title,
                                    String subtitle, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(SweetUi.dp(this, 16), SweetUi.dp(this, 10),
                SweetUi.dp(this, 16), SweetUi.dp(this, 10));
        card.setBackground(SweetUi.rounded(0xCFFFFFFF, 22, this));

        TextView iconView = SweetUi.label(this, icon, 22, Color.BLACK);
        iconView.setGravity(Gravity.CENTER);
        card.addView(iconView, new LinearLayout.LayoutParams(
                SweetUi.dp(this, 48), SweetUi.dp(this, 48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(SweetUi.dp(this, 8), 0, 0, 0);

        TextView t = SweetUi.label(this, title, 15, SweetUi.TEXT);
        t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        labels.addView(t);

        TextView sub = SweetUi.label(this, subtitle, 11, SweetUi.MUTED);
        labels.addView(sub);

        card.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = SweetUi.label(this, "›", 24, SweetUi.MUTED);
        card.addView(arrow);

        card.setOnClickListener(click);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SweetUi.dp(this, 74));
        cp.setMargins(0, 0, 0, SweetUi.dp(this, 10));
        root.addView(card, cp);
        return sub;
    }

    private LinearLayout buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(SweetUi.rounded(0xF4FFFFFF, 24, this));
        nav.setElevation(SweetUi.dp(this, 4));

        Button home = SweetUi.nav(this, "⌂\n首頁", true);
        Button history = SweetUi.nav(this, "▣\n下載記錄", false);
        Button gallery = SweetUi.nav(this, "▧\nGallery", false);

        nav.addView(home, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        nav.addView(history, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        nav.addView(gallery, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        history.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        gallery.setOnClickListener(v ->
                startActivity(new Intent(this, GalleryActivity.class)));

        return nav;
    }

    private void refreshSettingLabels() {
        if (autoPagesSubtitle == null || peopleSubtitle == null) return;
        SharedPreferences p = getSharedPreferences("sweet_settings", MODE_PRIVATE);
        int max = p.getInt("max_pages", 12);
        boolean people = p.getBoolean("people_only", true);
        autoPagesSubtitle.setText("最多 " + max + " 頁");
        peopleSubtitle.setText(people ? "人臉 / 人物偵測" : "已關閉人物過濾");
    }

    private int getMaxPages() {
        return getSharedPreferences("sweet_settings", MODE_PRIVATE)
                .getInt("max_pages", 12);
    }

    private boolean peopleOnly() {
        return getSharedPreferences("sweet_settings", MODE_PRIVATE)
                .getBoolean("people_only", true);
    }

    private boolean dedupeEnabled() {
        return getSharedPreferences("sweet_settings", MODE_PRIVATE)
                .getBoolean("dedupe", true);
    }

    private void startDownload() {
        String pageUrl = urlInput.getText().toString().trim();
        if (!pageUrl.startsWith("http://") && !pageUrl.startsWith("https://")) {
            Toast.makeText(this, "請輸入有效的 http/https 網址", Toast.LENGTH_SHORT).show();
            return;
        }

        downloadButton.setEnabled(false);
        progressCard.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        setStatus("正在掃描網站…");

        executor.execute(() -> {
            try {
                DownloadResult result = scanAndDownload(pageUrl);
                recordDownloadSummary(result.folder, result.source, result.saved, result.pages);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    status.setText("完成 · " + result.saved + " 張圖片 · " +
                            result.pages + " 頁\n" + cleanFolder(result.folder));
                    Toast.makeText(this, "下載完成", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    status.setText("Error: " +
                            (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                });
            } finally {
                runOnUiThread(() -> downloadButton.setEnabled(true));
            }
        });
    }

    private DownloadResult scanAndDownload(String pageUrl) throws Exception {
        int maxPages = getMaxPages();
        boolean filterPeople = peopleOnly();
        boolean useDedupe = dedupeEnabled();

        Document firstDoc = fetchDocument(pageUrl);
        String resolved = firstDoc.location();
        String folder = detectTitle(firstDoc, resolved) + "_" + System.currentTimeMillis();

        List<String> pageQueue = new ArrayList<>();
        Set<String> seenPages = new LinkedHashSet<>();
        LinkedHashMap<String, String> allImages = new LinkedHashMap<>();

        pageQueue.add(resolved);
        seenPages.add(normalizePageUrl(resolved));

        int pageIndex = 0;
        while (pageIndex < pageQueue.size() && pageIndex < maxPages) {
            String currentUrl = pageQueue.get(pageIndex);
            Document doc = pageIndex == 0 ? firstDoc : fetchDocument(currentUrl);

            setStatus("Scanning page " + (pageIndex + 1) + "/" +
                    Math.min(maxPages, Math.max(pageQueue.size(), pageIndex + 1)));

            LinkedHashSet<String> pageImages = extractImages(doc, doc.location());
            for (String imageUrl : pageImages) {
                try {
                    URL parsed = new URL(imageUrl);
                    allImages.putIfAbsent(canonicalImageKey(parsed), imageUrl);
                } catch (Exception ignored) {}
            }

            if (pageQueue.size() < maxPages) {
                List<String> discovered = discoverPaginationLinks(
                        doc, doc.location(), resolved, maxPages - pageQueue.size());

                for (String next : discovered) {
                    String normalized = normalizePageUrl(next);
                    if (seenPages.add(normalized)) {
                        pageQueue.add(next);
                        if (pageQueue.size() >= maxPages) break;
                    }
                }
            }

            pageIndex++;
        }

        List<String> urls = new ArrayList<>(allImages.values());
        setStatus("偵測到 " + pageIndex + " 個分頁 · " + urls.size() + " 張候選圖片");

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
                if (useDedupe && isVisualDuplicate(hash, seenHashes)) {
                    bitmap.recycle();
                    continue;
                }

                if (filterPeople) {
                    setStatus("圖片過濾 " + checked + "/" + urls.size() +
                            " · 已保留 " + saved);
                    if (!containsPerson(bitmap)) {
                        bitmap.recycle();
                        continue;
                    }
                } else {
                    setStatus("下載中 " + checked + "/" + urls.size());
                }

                if (useDedupe) seenHashes.add(hash);

                String ext = extension(imageUrl, data);
                String name = String.format(Locale.US, "%03d.%s", saved + 1, ext);
                Uri savedUri = saveToPictures(folder, name, data, ext);
                recordGalleryItem(folder, name, savedUri, resolved);

                saved++;
                bitmap.recycle();
            } catch (Exception ignored) {}
        }

        return new DownloadResult(folder, resolved, saved, pageIndex);
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
                        raw.startsWith("javascript:") || raw.startsWith("mailto:")) continue;

                URL candidate = new URL(new URL(currentUrl), raw);

                if (!candidate.getProtocol().equals("http") &&
                        !candidate.getProtocol().equals("https")) continue;

                if (!candidate.getHost().toLowerCase(Locale.ROOT).equals(rootHost)) continue;
                if (!parentPath(candidate.getPath()).equals(rootParent)) continue;

                String text = a.text().trim().toLowerCase(Locale.ROOT);
                String rel = a.attr("rel").toLowerCase(Locale.ROOT);
                String cls = a.className().toLowerCase(Locale.ROOT);
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
        } catch (Exception ignored) {}

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
                    if (label.getConfidence() >= 0.35f && humanLabels.contains(text)) return true;
                }
            }
        } catch (Exception ignored) {}
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
            for (int i = 0; i < 4; i++) distance += Long.bitCount(hash[i] ^ old[i]);
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
                raw.startsWith("javascript:")) return;

        try {
            URL url = new URL(new URL(base), raw);
            String value = url.toString();

            if (!value.startsWith("http://") && !value.startsWith("https://")) return;
            out.putIfAbsent(canonicalImageKey(url), value);
        } catch (Exception ignored) {}
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

            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create folder");

            File file = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(data);
            }

            return Uri.fromFile(file);
        }
    }

    private void recordGalleryItem(String folder, String name, Uri uri, String source) {
        try {
            SharedPreferences prefs = getSharedPreferences("sweet_gallery", MODE_PRIVATE);
            JSONArray array = new JSONArray(prefs.getString("items", "[]"));

            JSONObject item = new JSONObject();
            item.put("folder", folder);
            item.put("name", name);
            item.put("uri", uri.toString());
            item.put("source", source);
            item.put("time", System.currentTimeMillis());

            array.put(item);
            prefs.edit().putString("items", array.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void recordDownloadSummary(String folder, String source, int saved, int pages) {
        try {
            SharedPreferences prefs = getSharedPreferences("sweet_downloads", MODE_PRIVATE);
            JSONArray array = new JSONArray(prefs.getString("items", "[]"));

            JSONObject item = new JSONObject();
            item.put("folder", folder);
            item.put("source", source);
            item.put("saved", saved);
            item.put("pages", pages);
            item.put("time", System.currentTimeMillis());

            array.put(item);
            prefs.edit().putString("items", array.toString()).apply();
        } catch (Exception ignored) {}
    }

    private String cleanFolder(String f) {
        return f.replaceFirst("_[0-9]{10,}$", "");
    }

    private void setStatus(String text) {
        runOnUiThread(() -> {
            progressCard.setVisibility(View.VISIBLE);
            status.setText(text);
        });
    }

    static class DownloadResult {
        final String folder;
        final String source;
        final int saved;
        final int pages;

        DownloadResult(String folder, String source, int saved, int pages) {
            this.folder = folder;
            this.source = source;
            this.saved = saved;
            this.pages = pages;
        }
    }
}
