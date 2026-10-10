package com.motd.sweetdownloader;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MediaWriterTest {
    private Context context;
    private FakeMediaProvider provider;
    private File image;
    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("sweet_settings", 0).edit().clear().putInt("min_size", 0).commit();
        provider = new FakeMediaProvider(context.getCacheDir());
        ShadowContentResolver.registerProviderInternal("media", provider);
        image = new File(context.getCacheDir(), "fixture.png");
        Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(image)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        bitmap.recycle();
    }
    private MediaWriter writer(HttpTransfer.Cancel cancel) { return new MediaWriter(context, new DownloadOptions(context.getSharedPreferences("sweet_settings", 0)), cancel); }
    @Test public void scopedStoragePublishesOnlyAfterACompleteCopy() throws Exception {
        MediaWriter.Saved saved = writer(() -> false).image(image, "image/png", "album", "https://example.com/image");
        assertEquals("content", saved.uri.getScheme());
        assertEquals("Pictures/Sweet/album", provider.inserted.getAsString(MediaStore.MediaColumns.RELATIVE_PATH));
        assertEquals(Integer.valueOf(1), provider.inserted.getAsInteger(MediaStore.MediaColumns.IS_PENDING));
        assertEquals(Integer.valueOf(0), provider.updated.getAsInteger(MediaStore.MediaColumns.IS_PENDING));
        assertEquals(image.length(), provider.files.get(saved.uri).length()); assertEquals(0, provider.deleted);
    }
    @Test public void failedWritesRemoveThePendingMediaRow() {
        provider.failOpen = true;
        assertThrows(java.io.IOException.class, () -> writer(() -> false).image(image, "image/png", "album", "https://example.com/image"));
        assertEquals(1, provider.deleted); assertTrue(provider.files.isEmpty()); assertNull(provider.updated);
    }
    @Test public void cancellationAlsoRollsBackUnfinishedMedia() {
        assertThrows(InterruptedException.class, () -> writer(() -> true).image(image, "image/png", "album", "https://example.com/image"));
        assertEquals(1, provider.deleted); assertTrue(provider.files.isEmpty()); assertNull(provider.updated);
    }
    @Test public void htmlDisguisedAsVideoIsNotPublished() throws Exception {
        File invalid = new File(context.getCacheDir(), "invalid.mp4");
        try (FileOutputStream out = new FileOutputStream(invalid)) { out.write("<html>Sign in</html>".getBytes()); }
        assertThrows(java.io.IOException.class, () -> writer(() -> false).video(invalid, "video/mp4", "album", "https://example.com/video.mp4"));
        assertTrue(provider.files.isEmpty());
    }
    private static final class FakeMediaProvider extends ContentProvider {
        final Map<Uri, File> files = new HashMap<>();
        final File root;
        ContentValues inserted, updated;
        int deleted;
        boolean failOpen;
        FakeMediaProvider(File root) { this.root = root; }
        @Override public boolean onCreate() { return true; }
        @Override public Uri insert(Uri uri, ContentValues values) {
            inserted = new ContentValues(values); Uri next = Uri.withAppendedPath(uri, "1");
            File target = new File(root, "published.png"); files.put(next, target);
            try {
                // Robolectric's resolver uses registered streams rather than calling openFile.
                java.io.OutputStream sink = failOpen ? new java.io.OutputStream() {
                    @Override public void write(int value) throws java.io.IOException { throw new java.io.IOException("simulated storage failure"); }
                } : new FileOutputStream(target);
                org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication().getContentResolver()).registerOutputStream(next, sink);
            } catch (FileNotFoundException e) { throw new IllegalStateException(e); }
            return next;
        }
        @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            if (failOpen) throw new FileNotFoundException("simulated storage failure");
            return ParcelFileDescriptor.open(files.get(uri), ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        @Override public int update(Uri uri, ContentValues values, String where, String[] args) { updated = new ContentValues(values); return 1; }
        @Override public int delete(Uri uri, String where, String[] args) { File file = files.remove(uri); if (file != null) file.delete(); deleted++; return 1; }
        @Override public String getType(Uri uri) { return "image/png"; }
        @Override public Cursor query(Uri uri, String[] projection, String where, String[] args, String sort) { return null; }
    }
}
