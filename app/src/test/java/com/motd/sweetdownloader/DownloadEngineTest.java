package com.motd.sweetdownloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.Dispatcher;
import okio.Buffer;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DownloadEngineTest {
    private Context context;
    private MockWebServer server;
    private String base;
    private byte[] png, video;
    private final AtomicInteger imageHits = new AtomicInteger();
    @Before public void start() throws Exception {
        context = RuntimeEnvironment.getApplication();
        for (String prefs : Arrays.asList("sweet_settings", "sweet_gallery", "sweet_downloads", "sweet_sessions"))
            context.getSharedPreferences(prefs, 0).edit().clear().commit();
        context.getSharedPreferences("sweet_settings", 0).edit().putInt("min_size", 0).putBoolean("dedupe", false).putInt("max_pages", 2).commit();
        Bitmap image = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888); image.eraseColor(android.graphics.Color.BLUE);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.PNG, 100, out); image.recycle(); png = out.toByteArray();
        video = new byte[]{0,0,0,24,'f','t','y','p','i','s','o','m',0,0,0,0,'i','s','o','m','m','p','4','2'};
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.startsWith("/image")) { imageHits.incrementAndGet(); return response("image/png", png); }
                if (path.equals("/clip.mp4")) return response("video/mp4", video);
                if (path.equals("/gallery")) return response("text/html", "<title>Test Gallery</title><img src='/image'><video src='/clip.mp4'></video><a rel='next' href='/page2'>Next</a>".getBytes());
                if (path.equals("/page2")) return response("text/html", "<img src='/image?second=1'>".getBytes());
                return new MockResponse().setResponseCode(403);
            }
        });
        server.start(); base = server.url("/").toString().replaceAll("/$", "");
    }
    private static MockResponse response(String type, byte[] bytes) {
        return new MockResponse().setHeader("Content-Type", type).setBody(new Buffer().write(bytes));
    }
    @After public void stop() throws Exception { server.shutdown(); }
    private JSONArray gallery() throws Exception { return new JSONArray(context.getSharedPreferences("sweet_gallery", 0).getString("items", "[]")); }
    private DownloadEngine engine() { return new DownloadEngine(context, (text, percent) -> { }); }
    @Test public void imagesAndVideosAreDownloadedAndPersistedWithoutAnyPeopleClassifier() throws Exception {
        DownloadEngine engine = engine(); engine.run(Collections.singletonList(base + "/gallery"), "BOTH");
        assertEquals(2, engine.totalImages); assertEquals(1, engine.totalVideos); assertEquals(0, engine.totalFailures);
        JSONArray items = gallery(); assertEquals(3, items.length());
        for (int i = 0; i < items.length(); i++) {
            File file = new File(Uri.parse(items.getJSONObject(i).getString("uri")).getPath());
            assertArrayEquals(items.getJSONObject(i).getString("kind").equals("VIDEO") ? video : png, Files.readAllBytes(file.toPath()));
        }
        assertEquals(2, new JSONArray(context.getSharedPreferences("sweet_downloads", 0).getString("items", "[]")).getJSONObject(0).getInt("pages"));
    }
    @Test public void repeatedDownloadsSkipSavedFilesAndKeepTheSameAlbum() throws Exception {
        engine().run(Collections.singletonList(base + "/gallery"), "IMAGE");
        String folder = gallery().getJSONObject(0).getString("folder"); int hits = imageHits.get();
        DownloadEngine second = engine(); second.run(Collections.singletonList(base + "/gallery"), "IMAGE");
        assertEquals(0, second.totalImages); assertEquals(2, second.totalSkipped); assertEquals(hits, imageHits.get());
        assertEquals(folder, gallery().getJSONObject(0).getString("folder")); assertEquals(2, gallery().length());
    }
    @Test public void dimensionAndVisualDuplicateFiltersWorkOnNonPersonImages() throws Exception {
        context.getSharedPreferences("sweet_settings", 0).edit().putBoolean("dedupe", true).commit();
        DownloadEngine engine = engine(); engine.run(Collections.singletonList(base + "/gallery"), "IMAGE");
        assertEquals(1, engine.totalImages); assertEquals(1, engine.totalSkipped);
        context.getSharedPreferences("sweet_settings", 0).edit().putInt("min_size", 100).commit();
        DownloadEngine smaller = engine(); smaller.run(Collections.singletonList(base + "/image"), "IMAGE");
        assertEquals(0, smaller.totalImages); assertEquals(1, smaller.totalSkipped);
    }
    @Test public void jpegConversionProducesAJpegRatherThanRenamingTheSource() throws Exception {
        context.getSharedPreferences("sweet_settings", 0).edit().putString("image_format", "JPG").commit();
        DownloadEngine engine = engine(); engine.run(Collections.singletonList(base + "/image"), "IMAGE");
        assertEquals(1, engine.totalImages);
        byte[] bytes = Files.readAllBytes(new File(Uri.parse(gallery().getJSONObject(0).getString("uri")).getPath()).toPath());
        assertEquals(0xff, bytes[0] & 255); assertEquals(0xd8, bytes[1] & 255);
        assertEquals("image/jpeg", gallery().getJSONObject(0).getString("mime"));
    }
    @Test public void forbiddenSourceIsRecordedAsAFailureInsteadOfSilentSuccess() throws Exception {
        DownloadEngine engine = engine(); engine.run(Collections.singletonList(base + "/denied"), "IMAGE");
        assertEquals(1, engine.totalFailures); assertEquals(0, gallery().length());
        JSONArray history = new JSONArray(context.getSharedPreferences("sweet_downloads", 0).getString("items", "[]"));
        assertEquals("失敗", history.getJSONObject(0).getString("outcome"));
        assertTrue(history.getJSONObject(0).getJSONArray("errors").getJSONObject(0).getString("message").contains("403"));
    }
    @Test public void shareIntentPrefillsTheSimplifiedInterface() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND)
                .putExtra(android.content.Intent.EXTRA_TEXT, "Example\nhttps://example.com/video.mp4").setType("text/plain");
        org.robolectric.android.controller.ActivityController<MainActivity> activity = Robolectric.buildActivity(MainActivity.class, intent).setup();
        android.widget.EditText urls = activity.get().findViewById(R.id.download_urls);
        assertEquals("https://example.com/video.mp4", urls.getText().toString());
        activity.pause().stop().destroy();
    }
}
