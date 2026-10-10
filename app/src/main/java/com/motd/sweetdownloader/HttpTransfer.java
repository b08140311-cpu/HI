package com.motd.sweetdownloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Set;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Streaming HTTP with bounded memory, verified ranges, transient retries and persistent partials. */
public final class HttpTransfer {
    public interface Progress { void update(long bytes, long total); }
    public interface Cancel { boolean cancelled(); }
    public static final class Result {
        public final File file;
        public final String type, url;
        public Result(File file, String type, String url) { this.file = file; this.type = type; this.url = url; }
    }
    public static final class HttpFailure extends IOException {
        public final int status;
        HttpFailure(int status) { super("HTTP " + status); this.status = status; }
    }
    private static final Pattern RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+|\\*)");
    private final Cancel cancel;
    private final Set<HttpURLConnection> active = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public HttpTransfer(Cancel cancel) { this.cancel = cancel; }

    public void close() { for (HttpURLConnection c : active) c.disconnect(); }
    private void check() throws InterruptedException {
        if (cancel.cancelled() || Thread.currentThread().isInterrupted()) throw new InterruptedException("已取消");
    }
    private HttpURLConnection connection(String url, String referer) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/141 Mobile Safari/537.36");
        c.setRequestProperty("Accept-Encoding", "identity");
        if (referer != null && !referer.isEmpty()) c.setRequestProperty("Referer", referer);
        active.add(c);
        return c;
    }
    public Result inspect(String url) throws Exception {
        check();
        HttpURLConnection c = connection(url, url);
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new HttpFailure(code);
            return new Result(null, mime(c.getContentType()), c.getURL().toString());
        } finally { active.remove(c); c.disconnect(); }
    }
    public Result download(String url, String referer, File partial, long limit, Progress progress) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            check();
            if (attempt > 0) Thread.sleep(attempt == 1 ? 800 : 1800);
            try { return attempt(url, referer, partial, limit, progress); }
            catch (InterruptedException e) { throw e; }
            catch (HttpFailure e) {
                if (!(e.status == 408 || e.status == 425 || e.status == 429 || e.status == 500
                        || e.status == 502 || e.status == 503 || e.status == 504)) throw e;
                last = e;
            } catch (IOException e) { last = e; }
        }
        check();
        throw last;
    }
    private Result attempt(String url, String referer, File partial, long limit, Progress progress) throws Exception {
        File parent = partial.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("無法建立暫存目錄");
        File sidecar = new File(partial.getPath() + ".meta");
        Properties metadata = new Properties();
        if (sidecar.exists()) try (InputStream in = new FileInputStream(sidecar)) { metadata.load(in); }
        long offset = partial.exists() && url.equals(metadata.getProperty("url")) ? partial.length() : 0;
        HttpURLConnection c = connection(url, referer);
        if (offset > 0) {
            c.setRequestProperty("Range", "bytes=" + offset + "-");
            String validator = metadata.getProperty("validator", "");
            if (!validator.isEmpty()) c.setRequestProperty("If-Range", validator);
        }
        try {
            check();
            int code = c.getResponseCode();
            if (code == 416 && offset > 0 && ("bytes */" + offset).equals(c.getHeaderField("Content-Range"))) {
                return new Result(partial, metadata.getProperty("type", "application/octet-stream"), url);
            }
            if (code < 200 || code >= 300) throw new HttpFailure(code);
            long total = c.getContentLengthLong();
            boolean append = false;
            if (code == 206) {
                String range = c.getHeaderField("Content-Range");
                Matcher m = RANGE.matcher(range == null ? "" : range);
                if (!m.matches() || Long.parseLong(m.group(1)) != offset || Long.parseLong(m.group(2)) < offset) {
                    throw new IOException("伺服器回傳錯誤的續傳範圍");
                }
                total = m.group(3).equals("*") ? (total < 0 ? -1 : offset + total) : Long.parseLong(m.group(3));
                append = offset > 0;
            } else { offset = 0; }
            if (total > limit) throw new IOException("檔案超過此類型的大小上限");
            String type = mime(c.getContentType());
            metadata.setProperty("url", url); metadata.setProperty("type", type);
            String validator = c.getHeaderField("ETag");
            if (validator == null || validator.startsWith("W/")) validator = c.getHeaderField("Last-Modified");
            metadata.setProperty("validator", validator == null ? "" : validator);
            try (FileOutputStream meta = new FileOutputStream(sidecar)) { metadata.store(meta, "Download resume metadata"); }
            long count = offset;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(partial, append)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    check(); count += n;
                    if (count > limit) throw new IOException("檔案超過此類型的大小上限");
                    out.write(buffer, 0, n);
                    if (progress != null) progress.update(count, total);
                }
            }
            if (count == 0 || (total >= 0 && count != total)) throw new IOException("下載不完整，可再次下載續傳");
            return new Result(partial, type, c.getURL().toString());
        } finally { active.remove(c); c.disconnect(); }
    }
    public static String mime(String value) {
        if (value == null || value.isEmpty()) return "application/octet-stream";
        return value.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
    }
    public static String id(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte v : digest) b.append(String.format(java.util.Locale.US, "%02x", v & 255));
            return b.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static void remove(Result r) {
        r.file.delete(); new File(r.file.getPath() + ".meta").delete();
    }
}
