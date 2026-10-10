# Sweet Downloader Android 2.0.2

Simple Android image and video downloader. No NudeNet, ML Kit, people/nudity classifiers, or adult-site-specific extraction is included.

## Features

- Paste up to 50 HTTP/HTTPS URLs, one per line, or share a link from another Android app. Choose images, videos, or both.
- Images from ordinary HTML, lazy-loading attributes, srcset, picture sources, metadata, inline CSS and quoted media URLs in scripts/JSON. Bounded same-host pagination.
- Direct MP4/WebM/MKV/MOV/AVI/TS files, HTML video/source tags and video metadata. Bundled yt-dlp, FFmpeg and QuickJS support YouTube, supported public video pages and unprotected HLS/DASH streams.
- Public Telegram page/message preview media. Specific message links are scoped to that message.
- Foreground notification, cancellation, streaming transfer, transient retries, validated Range responses and retained partial downloads. Repeating a URL skips existing saved files and reuses its album.
- Minimum image dimensions, optional visual deduplication, original/JPG/PNG output, and 1–4 concurrent image downloads/video fragments. Original mode preserves GIF animation; conversion produces a still image. Oversized conversions fail rather than silently reducing resolution.
- Gallery, system video playback, download history and individual failure reasons.

Images: `Pictures/Sweet/<album>/`. Videos: `Movies/Sweet/<album>/`. Android 10+ uses MediaStore pending writes with rollback. Android 7–9 requests storage permission. No server or API credential is needed.

## Limitations

This is a native Android implementation of the general download workflow, not a direct Tkinter/Playwright/Telethon port. It does not automate login, execute page JavaScript to discover images, download private Telegram content, decrypt DRM, or bypass access restrictions. Unsupported pages report failures. Website changes can affect YouTube extractors; **更新影片引擎** refreshes the bundled engine. Playlists are treated as a single video, as in the supplied desktop implementation.

Images are limited to 64 MiB and HTML pages to 8 MiB. Original image data remains unchanged while previews are bounded in memory. Background processes may be stopped by Android; reopen the app and start the same URL to continue. Resume depends on server support. A successfully downloaded codec may be unsupported by the phone's video player.

## Build and verify

Java 17, Gradle 8.9, Android platform 35 and build-tools 34.0.0. Dependencies resolve from Google Maven and Maven Central. This repository currently uses system Gradle, without a checked-in wrapper.

```sh
gradle --no-daemon --max-workers=2 assembleDebug testDebugUnitTest lintDebug
```

In the prepared Codex environment, first run `source /workspace/toolchains/activate-hi.sh` and `cd /workspace/HI`. Verify the APK with:

```sh
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Debug-signed universal APK for arm64-v8a, armeabi-v7a, x86_64 and x86; Android 7/API 24 or later.

For a smaller APK containing only a particular architecture, add `-PsweetAbi=arm64-v8a` (or `armeabi-v7a`, `x86_64`, `x86`) to `assembleDebug`. Without this option, the build includes all four architectures.

Tests exercise media discovery, Telegram message scoping, retry/range/cancellation behavior, bitmap conversion, duplicate filtering, repeat-download behavior and both legacy and MediaStore storage paths using local HTTP fixtures and Robolectric. Device playback and live YouTube/Telegram downloads must be verified on a phone; local tests do not establish live-site availability.

## Third-party software

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency sources and licenses. Preserve their license and corresponding-source requirements when redistributing the APK.
