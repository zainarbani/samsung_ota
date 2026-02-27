package com.sample.samsung.ota;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.core.view.WindowInsetsControllerCompat;
import android.content.pm.PackageManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements ShizukuAttestationHelper.Callback {
    private static final String TAG = "MainActivity";
    private static final String ALIAS = "AttestationKey_com_wssyncmldm";
    private static final String DOWNLOAD_MIME_TYPE = "application/zip";
    private static final String DOWNLOAD_BUTTON_LABEL = "Download Update";
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private String defaultFwVersion = "";
    private String latestDownloadUrl = "";
    private String pendingDownloadUrl = "";
    private String downloadedUrl = "";
    private volatile boolean isDownloading = false;
    private volatile int currentDownloadProgress = -1;
    private Drawable downloadButtonDefaultBackground;
    private LayerDrawable downloadProgressBackground;

    private EditText fwVersionEditText;
    private Button checkUpdateButton;
    private TextView deviceNameTextView;
    private TextView currentFwTextView;
    private TextView cscTextView;
    private TextView androidVersionTextView;
    private TextView deviceIdTextView;
    private ImageButton themeToggleButton;
    private NestedScrollView logScrollView;
    private TextView resultTextView;
    private Button downloadButton;

    private IAttestationService attestationService;
    private ShizukuAttestationHelper shizukuHelper;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean downloadProgressSyncScheduled = false;
    private final Runnable downloadProgressSyncRunnable = new Runnable() {
        @Override
        public void run() {
            downloadProgressSyncScheduled = false;
            syncDownloadStateFromService();
            if (DownloadForegroundService.isDownloadRunning()) {
                scheduleDownloadProgressSync();
            }
        }
    };
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String state = intent.getStringExtra(DownloadForegroundService.EXTRA_STATE);
            if (DownloadForegroundService.STATE_RUNNING.equals(state)) {
                int p = intent.getIntExtra(DownloadForegroundService.EXTRA_PROGRESS, -1);
                setDownloadingProgress(p);
                scheduleDownloadProgressSync();
                return;
            }

            isDownloading = false;
            currentDownloadProgress = -1;

            if (DownloadForegroundService.STATE_SUCCESS.equals(state)) {
                String urlDone = intent.getStringExtra(DownloadForegroundService.EXTRA_URL_DONE);
                downloadedUrl = (urlDone == null || urlDone.isBlank()) ? "" : urlDone;
                return;
            }

            appendLog("failed");
            if (DownloadForegroundService.STATE_CANCELED.equals(state)) {
                appendLog("Download canceled");
            } else {
                String msg = safe(intent.getStringExtra(DownloadForegroundService.EXTRA_MESSAGE));
                appendLog(msg.equals("-") ? "Download failed" : "Download error: " + msg);
            }
            refreshActionButtons();
        }
    };
    private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(DOWNLOAD_MIME_TYPE),
            uri -> {
                if (uri == null) {
                    appendLog("Download canceled");
                    return;
                }
                String url = pendingDownloadUrl;
                if (url == null || url.isBlank()) {
                    appendLog("failed");
                    appendLog("Error: Download URL is empty");
                    return;
                }
                appendLog("Downloading update package...");
                setDownloadingProgress(-1);
                Intent startIntent = new Intent(this, DownloadForegroundService.class);
                startIntent.setAction(DownloadForegroundService.ACTION_START);
                startIntent.putExtra(DownloadForegroundService.EXTRA_URL, url);
                startIntent.putExtra(DownloadForegroundService.EXTRA_DEST_URI, uri.toString());
                ContextCompat.startForegroundService(this, startIntent);
            });
    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (!granted) {
                    appendLog("Notification permission denied; download notification may be hidden.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applySavedThemeMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        bindViews();
        initDeviceInfo();
        initActions();
        applySystemBarsTheme();
        ensureNotificationPermissionIfNeeded();

        shizukuHelper = new ShizukuAttestationHelper(this, this);
        shizukuHelper.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shizukuHelper != null) {
            shizukuHelper.stop();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(DownloadForegroundService.BROADCAST_DOWNLOAD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }
        syncDownloadStateFromService();
        if (DownloadForegroundService.isDownloadRunning()) {
            scheduleDownloadProgressSync();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        cancelDownloadProgressSync();
        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onStatus(String message) {
        appendLog(message);
    }

    @Override
    public void onServiceReady(IAttestationService service) {
        attestationService = service;
        refreshActionButtons();
    }

    @Override
    public void onServiceDisconnected() {
        attestationService = null;
        refreshActionButtons();
    }

    private void bindViews() {
        fwVersionEditText = findViewById(R.id.fwVersionEditText);
        checkUpdateButton = findViewById(R.id.checkUpdateButton);
        deviceNameTextView = findViewById(R.id.deviceNameTextView);
        currentFwTextView = findViewById(R.id.currentFwTextView);
        cscTextView = findViewById(R.id.cscTextView);
        androidVersionTextView = findViewById(R.id.androidVersionTextView);
        deviceIdTextView = findViewById(R.id.deviceIdTextView);
        themeToggleButton = findViewById(R.id.themeToggleButton);
        logScrollView = findViewById(R.id.logScrollView);
        resultTextView = findViewById(R.id.resultTextView);
        downloadButton = findViewById(R.id.downloadButton);
        downloadButtonDefaultBackground = downloadButton.getBackground();
        Drawable progressDrawable = ContextCompat.getDrawable(this, R.drawable.download_button_progress);
        if (progressDrawable instanceof LayerDrawable) {
            downloadProgressBackground = (LayerDrawable) progressDrawable.mutate();
        }
        downloadButton.setText(DOWNLOAD_BUTTON_LABEL);
    }

    private void initDeviceInfo() {
        DeviceConfigProvider.DeviceConfig cfg = DeviceConfigProvider.read(getApplicationContext());
        defaultFwVersion = cfg.firmwareVersion;
        fwVersionEditText.setText(defaultFwVersion);
        deviceNameTextView.setText(cfg.modelName);
        currentFwTextView.setText(cfg.firmwareVersion);
        cscTextView.setText(cfg.customerCode);
        androidVersionTextView.setText(MainUiHelper.buildAndroidVersionDisplay());
        deviceIdTextView.setText(MainUiHelper.buildDeviceIdDisplay(cfg.deviceUniqueId, cfg.serialNumber));
        checkUpdateButton.setEnabled(false);
        setDownloadState("", false);
        updateThemeToggleIcon();
    }

    private void initActions() {
        checkUpdateButton.setOnClickListener(v -> onCheckUpdateClicked());
        downloadButton.setOnClickListener(v -> onDownloadClicked());
        themeToggleButton.setOnClickListener(v -> {
            ThemeHelper.toggleTheme(this, isCurrentUiDarkMode());
            getDelegate().applyDayNight();
            updateThemeToggleIcon();
            applySystemBarsTheme();
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rebindUiAfterThemeChange();
        applySystemBarsTheme();
    }

    private void onCheckUpdateClicked() {
        if (attestationService == null) {
            resetLog();
            appendLog("Connecting to Shizuku...");
            appendLog("failed");
            return;
        }

        String selectedFw = fwVersionEditText.getText() == null
                ? defaultFwVersion
                : fwVersionEditText.getText().toString().trim();
        if (selectedFw.isEmpty()) {
            selectedFw = defaultFwVersion;
        }

        String finalSelectedFw = selectedFw;
        checkUpdateButton.setEnabled(false);
        setDownloadState("", false);
        resetLog();
        appendLog("Authorizing FOTA client...");
        IO_EXECUTOR.execute(() -> runOtaRegistration(finalSelectedFw));
    }

    private void onDownloadClicked() {
        if (isDownloading) {
            requestCancelDownload();
            return;
        }
        if (latestDownloadUrl == null || latestDownloadUrl.isBlank()) {
            return;
        }
        pendingDownloadUrl = latestDownloadUrl;
        String suggestedName = "update.zip";
        createDocumentLauncher.launch(suggestedName);
    }

    private void runOtaRegistration(String firmwareVersionOverride) {
        try {
            DeviceConfigProvider.DeviceConfig selectedConfig = DeviceConfigProvider
                    .read(getApplicationContext())
                    .withFirmwareVersion(firmwareVersionOverride);
            FotaClient client = new FotaClient(selectedConfig);

            FotaClient.FotaResult bootstrap = client.registerDeviceBootstrap();
            if (bootstrap.challenge == null || bootstrap.challenge.isBlank()) {
                appendLog("failed");
                appendRegistrationDetails("Bootstrap", bootstrap);
                return;
            }

            appendLog("success");
            appendLog("Checking for updates...");

            String activeChallenge = bootstrap.challenge;
            OtaAttemptResult attempt = doRegistrationAttempt(
                    client,
                    selectedConfig,
                    activeChallenge);

            if (attempt.fotaResult != null
                    && attempt.fotaResult.needsChallengeRetry()
                    && attempt.fotaResult.challenge != null
                    && !attempt.fotaResult.challenge.equals(activeChallenge)) {
                activeChallenge = attempt.fotaResult.challenge;
                attempt = doRegistrationAttempt(client, selectedConfig, activeChallenge);
            }

            renderResultSummary(attempt);
        } catch (Exception e) {
            Log.e(TAG, "OTA registration failed", e);
            appendLog("failed");
            appendLog("Error: " + safe(e.getMessage()));
        } finally {
            refreshActionButtons();
        }
    }

    private OtaAttemptResult doRegistrationAttempt(
            FotaClient client,
            DeviceConfigProvider.DeviceConfig selectedConfig,
            String challenge) throws Exception {
        List<String> certs = attestationService.getAttestationCerts(
                ALIAS,
                challenge.getBytes(StandardCharsets.UTF_8));
        if (certs == null || certs.size() < 3) {
            return OtaAttemptResult.error("Attestation returned insufficient certs: "
                    + (certs == null ? 0 : certs.size()));
        }

        String appCert = certs.get(0);
        String sakCert = certs.get(1);
        String rootCert = certs.get(certs.size() - 1);

        FotaClient.FotaResult result = client.registerDevice(challenge, appCert, sakCert, rootCert);

        SyncMlOtaChecker.SyncMlResult syncMlResult = null;
        if (result.statusCode >= 200 && result.statusCode < 300) {
            syncMlResult = new SyncMlOtaChecker(selectedConfig).checkForUpdate();
        }
        return OtaAttemptResult.of(result, syncMlResult);
    }

    private void renderResultSummary(OtaAttemptResult attempt) {
        if (attempt.error != null) {
            setDownloadState("", false);
            appendLog("failed");
            appendLog("Error: " + safe(attempt.error));
            return;
        }

        if (attempt.syncMlResult == null) {
            appendLog("failed");
            appendRegistrationDetails("Registration", attempt.fotaResult);
            setDownloadState("", false);
            return;
        }

        if (!attempt.syncMlResult.ok) {
            String msg = safe(attempt.syncMlResult.message);
            if (msg.startsWith("No update")
                    || msg.startsWith("No suitable firmware")) {
                appendLog("success");
                appendLog("");
                appendLog("No new update found");
                setDownloadState("", false);
                return;
            }
            appendLog("failed");
            appendLog("SyncML error: " + msg);
            setDownloadState("", false);
            return;
        }

        SyncMlOtaChecker.FirmwareDescriptor descriptor = attempt.syncMlResult.descriptor;
        if (descriptor == null) {
            appendLog("failed");
            appendLog("Error: Descriptor is empty");
            setDownloadState("", false);
            return;
        }

        appendLog("success");
        appendLog("");
        appendLog("New update found!");
        appendLog("");
        appendLog("FW version: " + safe(descriptor.updateFwV));
        appendLog("File size: " + formatSizeMb(descriptor.size));
        appendLog("File MD5: " + safe(descriptor.md5));
        appendLog("");
        appendLog("Changelogs:");
        appendLog(safe(descriptor.description));
        appendLog("");

        String preferredUrl = descriptor.url;
        if (preferredUrl == null || preferredUrl.isBlank()) {
            preferredUrl = attempt.syncMlResult.descriptorUrl;
        }
        setDownloadState(preferredUrl, preferredUrl != null && !preferredUrl.isBlank());
    }

    private void appendRegistrationDetails(String stage, FotaClient.FotaResult result) {
        if (result == null) {
            return;
        }
        appendLog(stage + " HTTP: " + result.statusCode);
        if (result.errorCode != null && !result.errorCode.isBlank()) {
            appendLog(stage + " code: " + result.errorCode);
        }
        if (result.errorMessage != null && !result.errorMessage.isBlank()) {
            appendLog(stage + " message: " + result.errorMessage);
        }
        if (result.challenge != null && !result.challenge.isBlank()) {
            appendLog(stage + " challenge: " + result.challenge);
        }
        if (result.body != null && !result.body.isBlank()) {
            appendLog(stage + " response:");
            appendLog(result.body);
        }
    }

    private String safe(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private void resetLog() {
        mainHandler.post(() -> resultTextView.setText(""));
    }

    private void appendLog(String text) {
        mainHandler.post(() -> {
            String existing = resultTextView.getText() == null ? "" : resultTextView.getText().toString();
            if (existing.isEmpty()) {
                resultTextView.setText(text);
            } else {
                resultTextView.setText(existing + "\n" + text);
            }
            logScrollView.post(() -> logScrollView.fullScroll(android.view.View.FOCUS_DOWN));
        });
    }

    private void setDownloadState(String url, boolean enabled) {
        latestDownloadUrl = url == null ? "" : url;
        boolean shouldEnable = enabled
                && !latestDownloadUrl.isBlank()
                && !latestDownloadUrl.equals(downloadedUrl);
        mainHandler.post(() -> {
            if (isDownloading) {
                return;
            }
            downloadButton.setText(DOWNLOAD_BUTTON_LABEL);
            if (downloadButtonDefaultBackground != null) {
                downloadButton.setBackground(downloadButtonDefaultBackground);
            }
            downloadButton.setEnabled(shouldEnable);
            downloadButton.setAlpha(shouldEnable ? 1.0f : 0.45f);
        });
    }

    private void requestCancelDownload() {
        Intent cancelIntent = new Intent(this, DownloadForegroundService.class);
        cancelIntent.setAction(DownloadForegroundService.ACTION_CANCEL);
        startService(cancelIntent);
    }

    private void setDownloadingProgress(int percent) {
        isDownloading = true;
        currentDownloadProgress = percent;
        setCheckUpdateEnabled(false);
        mainHandler.post(() -> {
            downloadButton.setEnabled(true);
            downloadButton.setText("Cancel Download");
            if (downloadProgressBackground != null) {
                int normalized = percent < 0 ? 5 : Math.max(0, Math.min(percent, 100));
                Drawable progress = downloadProgressBackground.findDrawableByLayerId(android.R.id.progress);
                if (progress != null) {
                    progress.setLevel(normalized * 100);
                }
                downloadButton.setBackground(downloadProgressBackground);
            }
            downloadButton.setAlpha(1.0f);
        });
    }

    private void rebindUiAfterThemeChange() {
        String logText = resultTextView != null && resultTextView.getText() != null
                ? resultTextView.getText().toString()
                : "";
        String fwInput = fwVersionEditText != null && fwVersionEditText.getText() != null
                ? fwVersionEditText.getText().toString()
                : "";

        setContentView(R.layout.main);
        bindViews();
        initActions();

        DeviceConfigProvider.DeviceConfig cfg = DeviceConfigProvider.read(getApplicationContext());
        defaultFwVersion = cfg.firmwareVersion;
        fwVersionEditText.setText(fwInput.isBlank() ? defaultFwVersion : fwInput);
        deviceNameTextView.setText(cfg.modelName);
        currentFwTextView.setText(cfg.firmwareVersion);
        cscTextView.setText(cfg.customerCode);
        androidVersionTextView.setText(MainUiHelper.buildAndroidVersionDisplay());
        deviceIdTextView.setText(MainUiHelper.buildDeviceIdDisplay(cfg.deviceUniqueId, cfg.serialNumber));

        resultTextView.setText(logText);
        boolean canDownload = !latestDownloadUrl.isBlank() && !latestDownloadUrl.equals(downloadedUrl);
        if (isDownloading) {
            setDownloadingProgress(currentDownloadProgress);
        } else {
            setDownloadState(latestDownloadUrl, canDownload);
        }
        setCheckUpdateEnabled(attestationService != null && !isDownloading);
        updateThemeToggleIcon();
    }

    private void applySystemBarsTheme() {
        boolean darkMode = isCurrentUiDarkMode();
        int barColor = ContextCompat.getColor(this, darkMode ? android.R.color.black : android.R.color.white);
        getWindow().setStatusBarColor(barColor);
        getWindow().setNavigationBarColor(barColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!darkMode);
        controller.setAppearanceLightNavigationBars(!darkMode);
    }

    private void syncDownloadStateFromService() {
        if (DownloadForegroundService.isDownloadRunning()) {
            setDownloadingProgress(DownloadForegroundService.getCurrentProgress());
        } else {
            isDownloading = false;
            currentDownloadProgress = -1;
            cancelDownloadProgressSync();
            DownloadForegroundService.DownloadFinalEvent finalEvent =
                    DownloadForegroundService.consumeLastFinalEvent();
            if (finalEvent != null
                    && DownloadForegroundService.STATE_SUCCESS.equals(finalEvent.state)) {
                downloadedUrl = finalEvent.urlDone == null ? "" : finalEvent.urlDone;
                appendLog("Download completed");
            }
            refreshActionButtons();
        }
    }

    private void scheduleDownloadProgressSync() {
        if (downloadProgressSyncScheduled) {
            return;
        }
        downloadProgressSyncScheduled = true;
        mainHandler.postDelayed(downloadProgressSyncRunnable, 300);
    }

    private void cancelDownloadProgressSync() {
        downloadProgressSyncScheduled = false;
        mainHandler.removeCallbacks(downloadProgressSyncRunnable);
    }

    private void refreshActionButtons() {
        setDownloadState(latestDownloadUrl, hasDownloadUrl());
        setCheckUpdateEnabled(attestationService != null && !isDownloading);
    }

    private boolean hasDownloadUrl() {
        return latestDownloadUrl != null && !latestDownloadUrl.isBlank();
    }

    private void ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private String formatSizeMb(int bytes) {
        if (bytes <= 0) {
            return "-";
        }
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.US, "%.2f MB", mb);
    }

    private void updateThemeToggleIcon() {
        if (isCurrentUiDarkMode()) {
            themeToggleButton.setImageResource(R.drawable.ic_theme_light);
        } else {
            themeToggleButton.setImageResource(R.drawable.ic_theme_dark);
        }
    }

    private void setCheckUpdateEnabled(boolean enabled) {
        mainHandler.post(() -> {
            checkUpdateButton.setEnabled(enabled);
            checkUpdateButton.setAlpha(enabled ? 1.0f : 0.45f);
        });
    }

    private boolean isCurrentUiDarkMode() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static class OtaAttemptResult {
        final FotaClient.FotaResult fotaResult;
        final SyncMlOtaChecker.SyncMlResult syncMlResult;
        final String error;

        private OtaAttemptResult(
                FotaClient.FotaResult fotaResult,
                SyncMlOtaChecker.SyncMlResult syncMlResult,
                String error) {
            this.fotaResult = fotaResult;
            this.syncMlResult = syncMlResult;
            this.error = error;
        }

        static OtaAttemptResult of(
                FotaClient.FotaResult fotaResult,
                SyncMlOtaChecker.SyncMlResult syncMlResult) {
            return new OtaAttemptResult(fotaResult, syncMlResult, null);
        }

        static OtaAttemptResult error(String error) {
            return new OtaAttemptResult(null, null, error);
        }
    }
}
