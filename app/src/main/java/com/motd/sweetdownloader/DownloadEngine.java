package com.motd.sweetdownloader;

import android.content.Context;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;
import kotlin.Unit;
import org.jsoup.nodes.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class DownloadEngine implements HttpTransfer.Cancel {
    interface Listener { void status(String text, int progress); }
    private final Context context;
    private final DownloadOptions options;
    private final DownloadStore store;
    private final Listener listener;
    private final HttpTransfer http;
    private final ExecutorService pool;
    private final File cache;
    private volatile boolean cancelled;
    private volatile String nativeProcess;
    private volatile long lastProgress;
    int totalImages, totalVideos, totalFailures, totalSkipped;

    DownloadEngine(Context context, Listener listener) {
        this.context = context; this.listener = listener;
        options = new DownloadOptions(context.getSharedPreferences("sweet_settings", Context.MODE_PRIVATE));
        store = new DownloadStore(context);
        cache = new File(context.getFilesDir(), "downloads");
        http = new HttpTransfer(this);
        pool = Executors.newFixedThreadPool(options.workers);
    }
    @Override public boolean cancelled() { return cancelled; }
    void cancel() {
        cancelled = true; http.close(); pool.shutdownNow();
        String id = nativeProcess;
        if (id != null) YoutubeDL.getInstance().destroyProcessById(id);
    }
    private void check() throws InterruptedException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException("已取消");
    }
    void run(List<String> sources, String mode) throws Exception {
        try {
            for (int i = 0; i < sources.size(); i++) {
                check();
                listener.status("網址 " + (i + 1) + "/" + sources.size() + " · 掃描中…", -1);
                downloadSource(sources.get(i), mode);
            }
        } finally { pool.shutdownNow(); http.close(); }
    }
    private void downloadSource(String source, String mode) throws Exception {
        Stats stats = new Stats();
        MediaWriter writer = new MediaWriter(context, options, this);
        String outcome = "完成";
        try {
            if (!mode.equals("IMAGE") && MediaExtractor.isYouTube(source)) {
                stats.folder = store.folder(source, "YouTube");
                videoWithExtractor(source, source, source, writer, stats);
                return;
            }
            String page = MediaExtractor.publicPage(source);
            HttpTransfer.Result inspected = http.inspect(page);
            MediaExtractor.Kind direct = MediaExtractor.kind(inspected.url, inspected.type);
            if (direct != null && !inspected.type.equals("text/html")) {
                stats.folder = store.folder(source, java.net.URI.create(source).getHost());
                if (accept(mode, direct)) downloadMedia(new MediaExtractor.Media(inspected.url, source, direct), source, writer, stats);
                else throw new IOException("此網址的媒體類型與下載模式不符");
                return;
            }
            List<String> pages = new ArrayList<>(); pages.add(inspected.url);
            Set<String> seenPages = new LinkedHashSet<>(); seenPages.add(inspected.url);
            LinkedHashMap<String, MediaExtractor.Media> candidates = new LinkedHashMap<>();
            for (int i = 0; i < pages.size() && i < options.maxPages; i++) {
                check();
                String current = pages.get(i);
                listener.status("掃描第 " + (i + 1) + " 頁 · 最多 " + options.maxPages + " 頁", -1);
                try {
                    HttpTransfer.Result html = http.download(current, source,
                            new File(cache, "page-" + HttpTransfer.id(current)), 8L * 1024 * 1024, null);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (FileInputStream in = new FileInputStream(html.file)) {
                        byte[] buffer = new byte[8192]; int n;
                        while ((n = in.read(buffer)) != -1) { check(); bytes.write(buffer, 0, n); }
                    }
                    String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                    Document doc = MediaExtractor.document(text, html.url);
                    HttpTransfer.remove(html);
                    stats.pages++;
                    if (stats.folder == null) stats.folder = store.folder(source, MediaExtractor.title(doc, html.url));
                    for (MediaExtractor.Media media : MediaExtractor.extract(doc, html.url)) {
                        if (accept(mode, media.kind)) candidates.putIfAbsent(media.kind + ":" + media.url, media);
                        if (candidates.size() >= 2000) break;
                    }
                    for (String next : MediaExtractor.nextPages(doc, html.url)) {
                        if (pages.size() >= options.maxPages) break;
                        if (seenPages.add(next)) pages.add(next);
                    }
                } catch (InterruptedException e) { throw e; }
                catch (Exception e) {
                    if (i == 0) throw e;
                    stats.error(current, e);
                }
            }
            List<Future<?>> images = new ArrayList<>();
            int count = candidates.size();
            int checked = 0;
            for (MediaExtractor.Media media : candidates.values()) {
                check();
                final int index = ++checked;
                if (media.kind == MediaExtractor.Kind.IMAGE) {
                    images.add(pool.submit(() -> {
                        try {
                            check();
                            downloadMedia(media, source, writer, stats);
                            listener.status("圖片處理 " + index + "/" + count + " · " + stats.counts(), -1);
                        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        catch (Exception e) { stats.error(media.url, e); }
                    }));
                } else {
                    try { downloadMedia(media, source, writer, stats); }
                    catch (InterruptedException e) { throw e; }
                    catch (Exception e) { stats.error(media.url, e); }
                }
            }
            for (Future<?> image : images) { check(); image.get(); }
            if (count == 0 && !mode.equals("IMAGE")) videoWithExtractor(source, source, source, writer, stats);
            if (count == 0 && mode.equals("IMAGE")) throw new IOException("頁面沒有可下載的圖片；動態頁面或登入內容可能無法取得");
        } catch (InterruptedException e) { outcome = "已取消"; throw e; }
        catch (Exception e) { check(); stats.error(source, e); outcome = "失敗"; }
        finally {
            // Wait for in-flight image writes before committing the summary, including cancellation.
            if (cancelled || Thread.currentThread().isInterrupted()) {
                outcome = "已取消";
                pool.shutdownNow();
                boolean interrupted = Thread.interrupted();
                pool.awaitTermination(35, java.util.concurrent.TimeUnit.SECONDS);
                if (interrupted) Thread.currentThread().interrupt();
            }
            if (stats.folder == null) stats.folder = store.folder(source, java.net.URI.create(source).getHost());
            if (stats.failed > 0 && outcome.equals("完成")) outcome = "部分失敗";
            store.summary(stats.folder, source, stats.images, stats.videos, stats.pages, stats.failed, stats.skipped, outcome, stats.errors);
            totalImages += stats.images; totalVideos += stats.videos;
            totalFailures += stats.failed; totalSkipped += stats.skipped;
        }
    }
    private static boolean accept(String mode, MediaExtractor.Kind kind) {
        return mode.equals("BOTH") || mode.equals(kind.name());
    }
    private void downloadMedia(MediaExtractor.Media media, String source, MediaWriter writer, Stats stats) throws Exception {
        check();
        String profile = options.profile(media.kind);
        if (store.alreadySaved(media.url, profile)) { stats.skipped(); return; }
        if (media.kind == MediaExtractor.Kind.VIDEO && MediaExtractor.isStream(media.url, "")) {
            videoWithExtractor(media.url, media.referer, source, writer, stats); return;
        }
        File partial = new File(cache, HttpTransfer.id(media.url) + ".part");
        long start = System.nanoTime();
        long initial = partial.exists() ? partial.length() : 0;
        HttpTransfer.Result result = http.download(media.url, media.referer, partial,
                media.kind == MediaExtractor.Kind.IMAGE ? 64L * 1024 * 1024 : Long.MAX_VALUE,
                (bytes, total) -> {
                    if (media.kind != MediaExtractor.Kind.VIDEO) return;
                    long now = System.currentTimeMillis();
                    if (now - lastProgress < 500) return;
                    lastProgress = now;
                    double seconds = Math.max(0.01, (System.nanoTime() - start) / 1e9);
                    double speed = Math.max(0, bytes - initial) / seconds;
                    String eta = total > 0 && speed > 0 ? " · 約 " + (int) ((total - bytes) / speed) + " 秒" : "";
                    listener.status(String.format(Locale.TAIWAN, "影片 %.1f MB · %.2f MB/s%s", bytes / 1048576.0, speed / 1048576.0, eta),
                            total > 0 ? (int) (100 * bytes / total) : -1);
                });
        if (media.kind == MediaExtractor.Kind.VIDEO && MediaExtractor.isStream(result.url, result.type)) {
            HttpTransfer.remove(result);
            videoWithExtractor(media.url, media.referer, source, writer, stats); return;
        }
        check();
        MediaWriter.Saved saved = media.kind == MediaExtractor.Kind.IMAGE
                ? writer.image(result.file, result.type, stats.folder, media.url)
                : writer.video(result.file, result.type, stats.folder, media.url);
        if (saved == null) { stats.skipped(); HttpTransfer.remove(result); return; }
        try { store.media(stats.folder, saved.name, saved.uri, source, media.url, profile, media.kind, saved.mime); }
        catch (Exception e) { writer.delete(saved); throw e; }
        stats.saved(media.kind); HttpTransfer.remove(result);
    }
    private void initVideo() throws Exception {
        listener.status("準備影片引擎…首次執行需要解壓縮", -1);
        YoutubeDL.getInstance().init(context); FFmpeg.getInstance().init(context); check();
    }
    void updateVideoEngine() throws Exception {
        initVideo();
        listener.status("更新影片引擎…", -1);
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE);
        listener.status("影片引擎已更新", 100);
    }
    private void videoWithExtractor(String url, String referer, String source, MediaWriter writer, Stats stats) throws Exception {
        if (store.alreadySaved(url, "VIDEO")) { stats.skipped(); return; }
        initVideo();
        File dir = new File(cache, "video-" + HttpTransfer.id(url));
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("無法建立影片暫存目錄");
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--no-playlist"); request.addOption("--continue");
        request.addOption("--no-mtime"); request.addOption("--no-overwrites");
        request.addOption("--socket-timeout", 30); request.addOption("--retries", 3);
        request.addOption("--fragment-retries", 3); request.addOption("--concurrent-fragments", options.workers);
        request.addOption("--referer", referer);
        request.addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/best");
        request.addOption("--merge-output-format", "mp4");
        request.addOption("-o", new File(dir, "%(title).80s [%(id)s].%(ext)s").getPath());
        nativeProcess = "sweet-" + HttpTransfer.id(url);
        try {
            check();
            YoutubeDL.getInstance().execute(request, nativeProcess, false, (progress, eta, line) -> {
                if (!cancelled) listener.status(String.format(Locale.TAIWAN, "影片 %.1f%% · 約 %d 秒\n%s", progress, eta, line), Math.round(progress));
                return Unit.INSTANCE;
            });
            check();
            File newest = null;
            File[] files = dir.listFiles();
            if (files != null) for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|webm|mov|m4v)$") &&
                        (newest == null || file.lastModified() > newest.lastModified())) newest = file;
            }
            if (newest == null) throw new IOException("影片引擎沒有產生完整影片");
            String mime = newest.getName().toLowerCase(Locale.ROOT).endsWith(".mkv") ? "video/x-matroska" : "video/mp4";
            MediaWriter.Saved saved = writer.video(newest, mime, stats.folder, url);
            try { store.media(stats.folder, saved.name, saved.uri, source, url, "VIDEO", MediaExtractor.Kind.VIDEO, saved.mime); }
            catch (Exception e) { writer.delete(saved); throw e; }
            stats.saved(MediaExtractor.Kind.VIDEO); newest.delete();
        } catch (Exception e) { check(); throw e; }
        finally { nativeProcess = null; }
    }
    private static final class Stats {
        String folder;
        int images, videos, pages, failed, skipped;
        final JSONArray errors = new JSONArray();
        synchronized void saved(MediaExtractor.Kind kind) { if (kind == MediaExtractor.Kind.IMAGE) images++; else videos++; }
        synchronized void skipped() { skipped++; }
        synchronized String counts() { return images + " 張圖片 · " + videos + " 部影片"; }
        synchronized void error(String url, Exception e) {
            failed++;
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (message.length() > 1200) message = message.substring(0, 1200);
            try { errors.put(new JSONObject().put("url", url).put("message", message)); }
            catch (Exception ignored) { /* Error text is best effort; the failure counter is retained. */ }
        }
    }
}
