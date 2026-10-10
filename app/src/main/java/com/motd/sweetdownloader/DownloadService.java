package com.motd.sweetdownloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {
    static final String CANCEL = "com.motd.sweetdownloader.CANCEL";
    static final String UPDATE = "com.motd.sweetdownloader.UPDATE";
    private static final String CHANNEL = "sweet_downloads";
    private static final int NOTIFICATION = 21;
    static volatile boolean running;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DownloadEngine engine;
    private PowerManager.WakeLock wakeLock;
    private long lastUi;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "媒體下載", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (CANCEL.equals(intent.getAction())) {
            if (engine != null) engine.cancel();
            else stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        running = true;
        Notification notification = notification("準備下載…", -1);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIFICATION, notification);
        PowerManager power = getSystemService(PowerManager.class);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sweet:download");
        wakeLock.acquire(6L * 60 * 60 * 1000);
        engine = new DownloadEngine(this, this::status);
        String mode = intent.getStringExtra("mode");
        final String selectedMode = "VIDEO".equals(mode) || "BOTH".equals(mode) ? mode : "IMAGE";
        status("準備下載…", -1);
        executor.execute(() -> {
            String outcome;
            try {
                if (UPDATE.equals(intent.getAction())) {
                    engine.updateVideoEngine(); outcome = "影片引擎已更新";
                } else {
                    List<String> urls = MediaExtractor.parseInput(intent.getStringExtra("urls") == null ? "" : intent.getStringExtra("urls"));
                    engine.run(urls, selectedMode);
                    outcome = "完成 · " + engine.totalImages + " 張圖片 · " + engine.totalVideos + " 部影片\n"
                            + "略過 " + engine.totalSkipped + " · 失敗 " + engine.totalFailures;
                    if (engine.totalFailures > 0) outcome += "\n請在下載紀錄查看失敗原因";
                }
            } catch (InterruptedException e) {
                outcome = "已取消 · 已儲存的檔案保留，重新下載相同網址可續傳";
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                outcome = "下載失敗：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            }
            running = false;
            status(outcome, 100);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        });
        return START_NOT_STICKY;
    }
    private synchronized void status(String message, int progress) {
        long now = System.currentTimeMillis();
        if (running && progress != -1 && now - lastUi < 250) return;
        lastUi = now;
        if (message.length() > 1600) message = message.substring(0, 1600);
        getSharedPreferences("sweet_job", MODE_PRIVATE).edit()
                .putString("message", message).putInt("progress", progress).apply();
        if (running) getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(message, progress));
    }
    private Notification notification(String text, int progress) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel = PendingIntent.getService(this, 1, new Intent(this, DownloadService.class).setAction(CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Sweet Downloader")
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(content)
                .setOnlyAlertOnce(true).setOngoing(true).setProgress(100, Math.max(0, progress), progress < 0)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancel).build()).build();
    }
    @Override public void onTimeout(int startId, int fgsType) {
        if (engine != null) engine.cancel();
        getSharedPreferences("sweet_job", MODE_PRIVATE).edit().putString("message", "背景下載超過系統時間限制，請重新開啟續傳").apply();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() {
        if (engine != null) engine.cancel();
        executor.shutdownNow();
        running = false;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
