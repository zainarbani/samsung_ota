package com.sample.samsung.ota;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.DocumentsContract;
import androidx.core.app.NotificationCompat;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DownloadForegroundService extends Service {
    public static final String ACTION_START = "com.sample.samsung.ota.action.DOWNLOAD_START";
    public static final String ACTION_CANCEL = "com.sample.samsung.ota.action.DOWNLOAD_CANCEL";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_DEST_URI = "extra_dest_uri";

    public static final String BROADCAST_DOWNLOAD = "com.sample.samsung.ota.broadcast.DOWNLOAD";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_SAVED_PATH = "saved_path";
    public static final String EXTRA_URL_DONE = "url_done";
    public static final String STATE_RUNNING = "running";
    public static final String STATE_SUCCESS = "success";
    public static final String STATE_FAILED = "failed";
    public static final String STATE_CANCELED = "canceled";

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static volatile boolean running = false;
    private static volatile int progress = -1;
    private static final Object FINAL_STATE_LOCK = new Object();
    private static long lastFinalEventId = 0L;
    private static long consumedFinalEventId = 0L;
    private static String lastFinalState = "";
    private static String lastFinalUrlDone = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean cancelRequested = false;
    private volatile HttpURLConnection activeConnection;
    private Future<?> activeTask;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            requestCancel();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            return START_NOT_STICKY;
        }

        String url = intent.getStringExtra(EXTRA_URL);
        String destUri = intent.getStringExtra(EXTRA_DEST_URI);
        if (url == null || url.isBlank() || destUri == null || destUri.isBlank()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (activeTask != null && !activeTask.isDone()) {
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Downloading update package", -1));

        cancelRequested = false;
        running = true;
        progress = -1;
        clearFinalEvent();
        activeTask = executor.submit(() -> performDownload(url, Uri.parse(destUri)));
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        requestCancel();
        executor.shutdownNow();
        super.onDestroy();
    }

    public static boolean isDownloadRunning() {
        return running;
    }

    public static int getCurrentProgress() {
        return progress;
    }

    public static DownloadFinalEvent consumeLastFinalEvent() {
        synchronized (FINAL_STATE_LOCK) {
            if (lastFinalEventId == 0L || consumedFinalEventId == lastFinalEventId) {
                return null;
            }
            consumedFinalEventId = lastFinalEventId;
            return new DownloadFinalEvent(lastFinalState, lastFinalUrlDone);
        }
    }

    private void performDownload(String sourceUrl, Uri destinationUri) {
        HttpURLConnection conn = null;
        try {
            publishRunning(-1);
            conn = (HttpURLConnection) URI.create(sourceUrl).toURL().openConnection();
            activeConnection = conn;
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(300000);
            conn.setRequestProperty("User-Agent", "SAMSUNG-Android");
            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                publishFinal(STATE_FAILED, "Download HTTP: " + code, "", sourceUrl);
                return;
            }

            long totalBytes = conn.getContentLengthLong();
            long downloadedBytes = 0;
            int lastPercent = -1;
            try (InputStream in = conn.getInputStream();
                 OutputStream out = getContentResolver().openOutputStream(destinationUri, "w")) {
                if (out == null) {
                    publishFinal(STATE_FAILED, "Unable to open destination file", "", sourceUrl);
                    return;
                }

                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (cancelRequested) {
                        deletePartialDownload(destinationUri);
                        publishFinal(STATE_CANCELED, "Download canceled", "", sourceUrl);
                        return;
                    }
                    out.write(buf, 0, n);
                    downloadedBytes += n;
                    if (totalBytes > 0) {
                        int p = (int) ((downloadedBytes * 100L) / totalBytes);
                        if (p > 100) {
                            p = 100;
                        }
                        if (p != lastPercent) {
                            lastPercent = p;
                            publishRunning(p);
                        }
                    } else {
                        // When server doesn't provide length, show a capped moving progress.
                        int p = 5 + (int) (downloadedBytes / (512L * 1024L));
                        if (p > 95) {
                            p = 95;
                        }
                        if (p != lastPercent) {
                            lastPercent = p;
                            publishRunning(p);
                        }
                    }
                }
                out.flush();
            }

            publishFinal(STATE_SUCCESS, "", formatSavedLocation(destinationUri), sourceUrl);
        } catch (Exception e) {
            if (cancelRequested) {
                deletePartialDownload(destinationUri);
                publishFinal(STATE_CANCELED, "Download canceled", "", sourceUrl);
            } else {
                publishFinal(STATE_FAILED, e.getMessage() == null ? "Download error" : e.getMessage(), "", sourceUrl);
            }
        } finally {
            activeConnection = null;
            running = false;
            progress = -1;
            if (conn != null) {
                conn.disconnect();
            }
            stopForeground(STOP_FOREGROUND_REMOVE);
            pushTerminalNotification(lastFinalState);
            stopSelf();
        }
    }

    private void requestCancel() {
        cancelRequested = true;
        HttpURLConnection conn = activeConnection;
        if (conn != null) {
            conn.disconnect();
        }
        Future<?> task = activeTask;
        if (task != null) {
            task.cancel(true);
        }
    }

    private void publishRunning(int p) {
        progress = p;
        running = true;
        updateNotification(p);

        Intent i = new Intent(BROADCAST_DOWNLOAD);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_STATE, STATE_RUNNING);
        i.putExtra(EXTRA_PROGRESS, p);
        sendBroadcast(i);
    }

    private void publishFinal(String state, String message, String savedPath, String urlDone) {
        synchronized (FINAL_STATE_LOCK) {
            lastFinalEventId++;
            lastFinalState = state == null ? "" : state;
            lastFinalUrlDone = urlDone == null ? "" : urlDone;
        }

        Intent i = new Intent(BROADCAST_DOWNLOAD);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_STATE, state);
        i.putExtra(EXTRA_MESSAGE, message == null ? "" : message);
        i.putExtra(EXTRA_SAVED_PATH, savedPath == null ? "" : savedPath);
        i.putExtra(EXTRA_URL_DONE, urlDone == null ? "" : urlDone);
        sendBroadcast(i);
    }

    private static void clearFinalEvent() {
        synchronized (FINAL_STATE_LOCK) {
            lastFinalEventId = 0L;
            consumedFinalEventId = 0L;
            lastFinalState = "";
            lastFinalUrlDone = "";
        }
    }

    public static final class DownloadFinalEvent {
        public final String state;
        public final String urlDone;

        public DownloadFinalEvent(String state, String urlDone) {
            this.state = state == null ? "" : state;
            this.urlDone = urlDone == null ? "" : urlDone;
        }
    }

    private void updateNotification(int p) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification("Downloading update package", p));
        }
    }

    private void pushTerminalNotification(String finalState) {
        String title;
        int iconRes;
        if (STATE_SUCCESS.equals(finalState)) {
            title = "Download completed";
            iconRes = android.R.drawable.stat_sys_download_done;
        } else if (STATE_CANCELED.equals(finalState)) {
            title = "Download canceled";
            iconRes = android.R.drawable.stat_notify_error;
        } else if (STATE_FAILED.equals(finalState)) {
            title = "Download failed";
            iconRes = android.R.drawable.stat_notify_error;
        } else {
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(title, -2, iconRes));
        }
    }

    private Notification buildNotification(String title, int p) {
        return buildNotification(title, p, android.R.drawable.stat_sys_download);
    }

    private Notification buildNotification(String title, int p, int iconRes) {
        boolean terminal = p == -2;
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = new Intent(this, DownloadForegroundService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent cancelPendingIntent = PendingIntent.getService(
                this,
                11,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(false)
                .setOngoing(!terminal && (p < 0 || p < 100))
                .setOnlyAlertOnce(true)
                .setSilent(true);

        if (!terminal) {
            b.addAction(0, "Cancel", cancelPendingIntent);
        }

        if (terminal) {
            b.setProgress(0, 0, false);
            b.setContentText(title);
        } else if (p >= 0 && p <= 100) {
            b.setProgress(100, p, false);
            b.setContentText(p + "%");
        } else {
            int fallbackPercent = 5;
            b.setProgress(100, fallbackPercent, false);
            b.setContentText(fallbackPercent + "%");
        }

        return b.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Download service",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Foreground OTA download status");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private void deletePartialDownload(Uri destinationUri) {
        try {
            getContentResolver().delete(destinationUri, null, null);
        } catch (Exception ignored) {
        }
    }

    private String formatSavedLocation(Uri uri) {
        if (uri == null) {
            return "-";
        }
        try {
            if ("content".equalsIgnoreCase(uri.getScheme())
                    && "com.android.externalstorage.documents".equals(uri.getAuthority())) {
                String docId = DocumentsContract.getDocumentId(uri);
                if (docId != null) {
                    String[] parts = docId.split(":", 2);
                    if (parts.length == 2 && "primary".equalsIgnoreCase(parts[0])) {
                        return parts[1];
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return uri.toString();
    }
}
