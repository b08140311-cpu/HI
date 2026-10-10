package com.motd.sweetdownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ordinary HTML discovery; no site-specific adult extraction or content classifiers. */
public final class MediaExtractor {
    public enum Kind { IMAGE, VIDEO }
    public static final class Media {
        public final String url, referer;
        public final Kind kind;
        public Media(String url, String referer, Kind kind) {
            this.url = url; this.referer = referer; this.kind = kind;
        }
    }
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*['\"]?([^)'\"\\s]+)['\"]?\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_URL = Pattern.compile("[\"']((?:https?://|//|/)[^\"'\\s]+)[\"']");
    private MediaExtractor() {}

    public static String normalize(String input) {
        try {
            URI uri = new URI(input.trim()).normalize();
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("請使用 http/https 網址，不要在網址內填入密碼");
            }
            String value = uri.toASCIIString();
            int hash = value.indexOf('#');
            return hash < 0 ? value : value.substring(0, hash);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("網址格式錯誤：" + input);
        }
    }

    public static List<String> parseInput(String input) {
        Set<String> urls = new LinkedHashSet<>();
        for (String line : input.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) urls.add(normalize(line));
        }
        if (urls.isEmpty()) throw new IllegalArgumentException("請輸入網址，每行一個");
        if (urls.size() > 50) throw new IllegalArgumentException("每次最多 50 個網址");
        return new ArrayList<>(urls);
    }

    public static Kind kind(String url, String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.startsWith("image/")) return Kind.IMAGE;
        if (type.startsWith("video/") || type.contains("mpegurl") || type.contains("dash+xml")) return Kind.VIDEO;
        String path;
        try { path = new URI(url).getPath().toLowerCase(Locale.ROOT); }
        catch (Exception e) { return null; }
        if (path.matches(".*\\.(jpg|jpeg|png|webp|gif|avif|bmp)$")) return Kind.IMAGE;
        if (path.matches(".*\\.(mp4|webm|mkv|mov|m4v|avi|m3u8|mpd|ts)$")) return Kind.VIDEO;
        return null;
    }

    public static boolean isStream(String url, String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".m3u8") || path.endsWith(".mpd") || t.contains("mpegurl") || t.contains("dash+xml");
    }

    public static boolean isYouTube(String url) {
        String host = URI.create(url).getHost().toLowerCase(Locale.ROOT);
        return host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com");
    }

    public static String publicPage(String url) {
        URI u = URI.create(url);
        String host = u.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals("t.me") && !host.equals("telegram.me")) return url;
        if (u.getPath().startsWith("/c/") || u.getPath().startsWith("/+")) {
            throw new IllegalArgumentException("私人 Telegram 連結需要登入；此版本只支援公開頁面");
        }
        if (!u.getPath().startsWith("/s/")) return "https://t.me/s" + u.getRawPath();
        return url;
    }

    public static Document document(String html, String base) {
        Document doc = Jsoup.parse(html, base);
        // A message link should not silently download other messages in its public preview.
        String path = URI.create(base).getPath();
        String host = URI.create(base).getHost();
        if (host.equals("t.me") && path.matches("/s/[^/]+/\\d+/?")) {
            String post = path.replaceFirst("/s/", "").replaceAll("/$", "");
            Element message = doc.selectFirst("[data-post=\"" + post + "\"]");
            if (message != null) {
                Document selected = Jsoup.parse(message.outerHtml(), base);
                selected.title(doc.title());
                return selected;
            }
            throw new IllegalArgumentException("公開頁面找不到該 Telegram 訊息，可能需要登入或已刪除");
        }
        return doc;
    }

    public static List<Media> extract(Document doc, String base) {
        LinkedHashMap<String, Media> result = new LinkedHashMap<>();
        String[] imageAttrs = {"data-original", "data-full", "data-full-src", "data-large", "data-zoom-image",
                "data-hires", "data-original-src", "data-master", "data-original-url", "zoomfile", "data-zoomfile",
                "data-src", "data-lazy-src", "data-lazy", "data-image", "data-url", "data-image-url", "src"};
        for (Element image : doc.select("img, picture source")) {
            for (String attr : imageAttrs) add(result, image.attr(attr), base, Kind.IMAGE);
            for (String attr : new String[]{"srcset", "data-srcset", "data-lazy-srcset"}) {
                String[] candidates = image.attr(attr).split(",");
                // Larger srcset candidates generally appear last. Fetch these first for visual dedupe.
                for (int i = candidates.length - 1; i >= 0; i--) {
                    String part = candidates[i].trim();
                    if (!part.isEmpty()) add(result, part.split("\\s+")[0], base, Kind.IMAGE);
                }
            }
        }
        for (Element video : doc.select("video, video source, source[type^=video/]")) {
            for (String attr : new String[]{"src", "data-src", "data-original"}) add(result, video.attr(attr), base, Kind.VIDEO);
            add(result, video.attr("poster"), base, Kind.IMAGE);
        }
        for (Element meta : doc.select("meta[content]")) {
            String key = (meta.hasAttr("property") ? meta.attr("property") :
                    meta.hasAttr("name") ? meta.attr("name") : meta.attr("itemprop")).toLowerCase(Locale.ROOT);
            if (key.matches("(og:image(:url|:secure_url)?|twitter:image(:src)?|image|thumbnailurl)"))
                add(result, meta.attr("content"), base, Kind.IMAGE);
            if (key.matches("(og:video(:url|:secure_url)?|twitter:player:stream|contenturl)"))
                add(result, meta.attr("content"), base, Kind.VIDEO);
        }
        for (Element link : doc.select("link[rel=preload]")) {
            if (link.attr("as").equals("image")) add(result, link.attr("href"), base, Kind.IMAGE);
            if (link.attr("as").equals("video")) add(result, link.attr("href"), base, Kind.VIDEO);
        }
        for (Element e : doc.getAllElements()) {
            for (String attr : new String[]{"href", "file", "data-file", "data-attachment", "data-download"}) {
                String raw = e.attr(attr);
                String resolved = resolve(base, raw);
                if (resolved != null) {
                    Kind k = kind(resolved, "");
                    if (k != null) add(result, resolved, base, k);
                }
            }
            Matcher css = CSS_URL.matcher(e.attr("style") + (e.tagName().equals("style") ? e.data() : ""));
            while (css.find()) add(result, css.group(1), base, Kind.IMAGE);
            if (e.tagName().equals("script")) {
                String data = e.data().replace("\\/", "/").replace("\\u002F", "/").replace("\\u002f", "/");
                Matcher quoted = QUOTED_URL.matcher(data);
                while (quoted.find()) {
                    String resolved = resolve(base, quoted.group(1));
                    Kind k = resolved == null ? null : kind(resolved, "");
                    if (k != null) add(result, resolved, base, k);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private static void add(LinkedHashMap<String, Media> result, String raw, String base, Kind kind) {
        String resolved = resolve(base, raw);
        if (resolved != null) result.putIfAbsent(kind + ":" + resolved, new Media(resolved, base, kind));
    }
    private static String resolve(String base, String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            if (raw.trim().startsWith("?")) return normalize(base.split("[?#]", 2)[0] + raw.trim());
            return normalize(new URL(new URL(base), raw.trim()).toString());
        }
        catch (Exception ignored) { return null; }
    }

    public static List<String> nextPages(Document doc, String base) {
        Set<String> pages = new LinkedHashSet<>();
        URI current = URI.create(base);
        for (Element link : doc.select("a[href],link[rel=next][href]")) {
            String marker = (link.text() + " " + link.attr("rel") + " " + link.className()).toLowerCase(Locale.ROOT);
            String url = resolve(base, link.attr("href"));
            if (url == null || url.equals(base)) continue;
            URI u = URI.create(url);
            if (!current.getHost().equalsIgnoreCase(u.getHost())) continue;
            boolean next = marker.contains("next") || marker.contains("下一") || marker.contains("下頁") || marker.contains("下页")
                    || marker.trim().equals("›") || marker.trim().equals(">") || marker.trim().equals("»");
            boolean numericPage = link.text().trim().matches("\\d+") &&
                    ((u.getQuery() != null && u.getQuery().matches("(?i)(.*&)?(page|paged|p)=\\d+(&.*)?"))
                            || u.getPath().matches(".*/page/\\d+/?"));
            if (next || numericPage) pages.add(url);
        }
        return new ArrayList<>(pages);
    }

    public static String title(Document doc, String fallback) {
        Element meta = doc.selectFirst("meta[property=og:title],meta[name=twitter:title]");
        String name = meta == null ? doc.title() : meta.attr("content");
        if (name.trim().isEmpty()) name = URI.create(fallback).getHost();
        return safeName(name);
    }
    public static String safeName(String name) {
        String clean = name.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_").trim().replaceAll("^[. ]+|[. ]+$", "");
        if (clean.isEmpty()) clean = "Sweet";
        return clean.substring(0, Math.min(clean.length(), 80));
    }
}
