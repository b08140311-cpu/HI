圖片／影片下載版，Android 7.0（API 24）以上。

下載檔案：
- **SweetDownloader-2.0-arm64.apk**：一般 ARM64 Android 手機建議使用。
- **SweetDownloader-2.0-universal.apk**：通用版本，包含 ARM64、32 位 ARM、x86 和 x86_64。
- **SweetDownloader-2.0-source.zip**：此版本完整應用程式原始碼。
- **SHA256SUMS.txt**：檔案校驗碼。

功能：
- 網頁圖片、直接影片連結，以及 Android 版 yt-dlp／FFmpeg 影片引擎。
- 批次網址、背景下載通知、取消、重試、HTTP Range 續傳。
- 圖片尺寸設定、重複圖片過濾、原始／JPG／PNG 格式。
- 圖片與影片瀏覽、下載紀錄及失敗原因。
- 已移除 NudeNet、ML Kit、人物與裸露判斷。

發布流程會先通過單元測試、Android Lint 及 APK 簽章驗證，再上傳檔案。

限制：尚未在實體手機驗證 YouTube／Telegram 連線下載與影片播放。Telegram 只支援公開預覽頁面可取得的媒體；私人頻道、需登入的頁面與 DRM 內容不支援。網站變更可能影響影片引擎，程式內可使用「更新影片引擎」。

此版本為 Debug 簽署 APK。第三方元件來源與授權見原始碼中的 THIRD_PARTY_NOTICES.md。
