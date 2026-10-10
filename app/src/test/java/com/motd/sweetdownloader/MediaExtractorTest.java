package com.motd.sweetdownloader;

import org.junit.Test;
import org.jsoup.Jsoup;
import java.util.List;
import static org.junit.Assert.*;

public class MediaExtractorTest {
    @Test public void findsOrdinaryImagesAndVideosWithoutConflatingSourceTags() {
        String html = "<picture><source srcset='/small.jpg 200w, /large.jpg 1200w'></picture>"
                + "<img data-original='/full.png?token=secret' src='/small.jpg'><video poster='/poster.webp'><source src='/clip.mp4'></video>"
                + "<meta property='og:video' content='/embed'><div style=\"background-image:url('/style.png')\"></div>"
                + "<script>{\"image\":\"https:\\/\\/example.com\\/json.jpg\"}</script>";
        List<MediaExtractor.Media> media = MediaExtractor.extract(Jsoup.parse(html, "https://example.com/post"), "https://example.com/post");
        assertTrue(media.stream().anyMatch(m -> m.kind == MediaExtractor.Kind.VIDEO && m.url.endsWith("clip.mp4")));
        assertFalse(media.stream().anyMatch(m -> m.kind == MediaExtractor.Kind.IMAGE && m.url.endsWith("clip.mp4")));
        assertTrue(media.stream().anyMatch(m -> m.url.endsWith("style.png")));
        assertTrue(media.stream().anyMatch(m -> m.url.endsWith("json.jpg")));
        assertTrue(media.stream().anyMatch(m -> m.url.contains("token=secret")));
    }
    @Test public void preservesCaseAndSignedQueries() {
        List<MediaExtractor.Media> media = MediaExtractor.extract(Jsoup.parse("<img src='/A.jpg?signature=1'><img src='/a.jpg?signature=2'>", "https://example.com/"), "https://example.com/");
        assertEquals(2, media.size());
    }
    @Test public void paginationDoesNotCrawlOtherSitesOrUnrelatedLinks() {
        String base = "https://example.com/album?page=1";
        List<String> pages = MediaExtractor.nextPages(Jsoup.parse("<a rel='next' href='?page=2'>Next</a><a href='/about'>About</a><a rel='next' href='https://other.example/a'>Next</a>", base), base);
        assertEquals(1, pages.size()); assertTrue(pages.get(0).endsWith("page=2"));
    }
    @Test public void batchValidationRejectsUnsupportedProtocols() {
        assertEquals(1, MediaExtractor.parseInput("https://example.com/a\nhttps://example.com/a#top").size());
        assertThrows(IllegalArgumentException.class, () -> MediaExtractor.parseInput("file:///tmp/a"));
        assertThrows(IllegalArgumentException.class, () -> MediaExtractor.parseInput("https://user:password@example.com/a"));
    }
    @Test public void telegramMessageScopeAndPrivateLinksAreExplicit() {
        String page = MediaExtractor.publicPage("https://t.me/channel/123");
        assertEquals("https://t.me/s/channel/123", page);
        String html = "<div data-post='channel/122'><img src='/wrong.jpg'></div><div data-post='channel/123'><img src='/right.jpg'></div>";
        List<MediaExtractor.Media> media = MediaExtractor.extract(MediaExtractor.document(html, page), page);
        assertEquals(1, media.size()); assertTrue(media.get(0).url.endsWith("right.jpg"));
        assertThrows(IllegalArgumentException.class, () -> MediaExtractor.publicPage("https://t.me/c/1234/5"));
        assertThrows(IllegalArgumentException.class, () -> MediaExtractor.document("<html>unavailable</html>", page));
    }
}
