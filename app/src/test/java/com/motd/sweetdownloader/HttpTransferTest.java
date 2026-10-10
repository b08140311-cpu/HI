package com.motd.sweetdownloader;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.Dispatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class HttpTransferTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private MockWebServer server;
    private String base;
    @Before public void start() throws Exception { server = new MockWebServer(); server.start(); base = server.url("/").toString(); }
    @After public void stop() throws Exception { server.shutdown(); }
    private File partial(String name, String url) throws Exception {
        File file = folder.newFile(name); Files.write(file.toPath(), "abc".getBytes());
        Properties metadata = new Properties(); metadata.setProperty("url", url); metadata.setProperty("type", "video/mp4"); metadata.setProperty("validator", "\"v1\"");
        try (FileOutputStream out = new FileOutputStream(file.getPath() + ".meta")) { metadata.store(out, "test"); }
        return file;
    }
    private String contents(File file) throws Exception { return new String(Files.readAllBytes(file.toPath()), "UTF-8"); }
    @Test public void resumesOnlyAtTheRequestedOffset() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setBody("def"));
        File file = partial("range.part", base + "range");
        new HttpTransfer(() -> false).download(base + "range", base, file, 1000, null);
        RecordedRequest request = server.takeRequest();
        assertEquals("bytes=3-", request.getHeader("Range")); assertEquals("\"v1\"", request.getHeader("If-Range"));
        assertEquals("abcdef", contents(file)); assertEquals(1, server.getRequestCount());
    }
    @Test public void restartsWhenServerIgnoresRange() throws Exception {
        server.enqueue(new MockResponse().setBody("xyz"));
        File file = partial("full.part", base + "full");
        new HttpTransfer(() -> false).download(base + "full", base, file, 1000, null);
        assertEquals("xyz", contents(file));
    }
    @Test public void retriesTransientErrorsButNeverRetriesForbiddenResponses() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("retry")); server.enqueue(new MockResponse().setBody("ok"));
        new HttpTransfer(() -> false).download(base + "retry", base, folder.newFile("retry.part"), 1000, null);
        assertEquals(2, server.getRequestCount());
        server.enqueue(new MockResponse().setResponseCode(403));
        assertThrows(HttpTransfer.HttpFailure.class, () -> new HttpTransfer(() -> false).download(base + "forbidden", base, folder.newFile("bad.part"), 1000, null));
        assertEquals(3, server.getRequestCount());
    }
    @Test public void rejectsIncorrectRangesAndSizeLimits() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().equals("/wrong")) return new MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-2/3").setBody("def");
                return new MockResponse().setBody("0123456789");
            }
        });
        File file = partial("wrong.part", base + "wrong");
        assertThrows(java.io.IOException.class, () -> new HttpTransfer(() -> false).download(base + "wrong", base, file, 1000, null));
        assertEquals("abc", contents(file));
        assertThrows(java.io.IOException.class, () -> new HttpTransfer(() -> false).download(base + "large", base, folder.newFile("large.part"), 5, null));
    }
    @Test public void cancellationMakesNoNetworkRequest() throws Exception {
        assertThrows(InterruptedException.class, () -> new HttpTransfer(() -> true).download(base + "never", base, folder.newFile("cancel.part"), 1000, null));
        assertEquals(0, server.getRequestCount());
    }
}
