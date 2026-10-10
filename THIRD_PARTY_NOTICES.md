# Third-party components

- **youtubedl-android library/common/ffmpeg 0.18.1**, yausername and contributors: https://github.com/yausername/youtubedl-android. GPL-3.0. Source and bundled native package build instructions are in the upstream repository and Maven Central source artifacts.
- **yt-dlp**: https://github.com/yt-dlp/yt-dlp; Unlicense for the main project, plus included dependency licenses. Android builds: https://github.com/xibr/ytdlp-lazy. See upstream licenses and notices for the bundled build.
- **FFmpeg and bundled native dependencies**: https://ffmpeg.org/ and https://github.com/yausername/youtubedl-android/blob/master/BUILD_FFMPEG.md. Applicable LGPL/GPL and codec licenses depend on the upstream build configuration.
- **QuickJS**: https://bellard.org/quickjs/; MIT.
- **jsoup 1.18.3**: https://jsoup.org/; MIT.
- **Kotlin, AndroidX, Jackson and Commons IO**: respective upstream Apache-2.0 notices in their artifacts.

Robolectric, JUnit and MockWebServer are test-only dependencies, not shipped in the APK.

The accompanying app source archive contains this app's modified source. This source list does not replace dependency license texts or corresponding-source requirements; redistribution also requires the exact applicable third-party source, build scripts, configuration and notices.
