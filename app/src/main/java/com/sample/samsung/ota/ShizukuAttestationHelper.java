package com.sample.samsung.ota;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;
import rikka.shizuku.Shizuku;

public class ShizukuAttestationHelper {
    public interface Callback {
        void onStatus(String message);
        void onServiceReady(IAttestationService service);
        void onServiceDisconnected();
    }

    private static final String TAG = "ShizukuHelper";

    private final Callback callback;
    private final Shizuku.UserServiceArgs userServiceArgs;
    private IAttestationService attestationService;
    private boolean listenersRegistered;
    private boolean bindingInProgress;
    private boolean isBound;

    private final Shizuku.OnRequestPermissionResultListener permissionListener = this::onRequestPermissionResult;
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::checkShizukuAndBind;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            attestationService = IAttestationService.Stub.asInterface(binder);
            bindingInProgress = false;
            isBound = true;
            callback.onStatus("success");
            callback.onServiceReady(attestationService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            attestationService = null;
            bindingInProgress = false;
            isBound = false;
            callback.onStatus("failed");
            callback.onStatus("Shizuku error: service disconnected");
            callback.onServiceDisconnected();
            checkShizukuAndBind();
        }
    };

    public ShizukuAttestationHelper(Context context, Callback callback) {
        this.callback = callback;
        this.userServiceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(context.getPackageName(), AttestationService.class.getName()))
                .daemon(true)
                .processNameSuffix("attestation_service")
                .debuggable(BuildConfig.DEBUG)
                .version(1);
    }

    public void start() {
        if (!listenersRegistered) {
            callback.onStatus("Connecting to Shizuku...");
            Shizuku.addRequestPermissionResultListener(permissionListener);
            Shizuku.addBinderReceivedListener(binderReceivedListener);
            listenersRegistered = true;
        }
        checkShizukuAndBind();
    }

    public void stop() {
        if (listenersRegistered) {
            Shizuku.removeRequestPermissionResultListener(permissionListener);
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            listenersRegistered = false;
        }
        try {
            if (isBound || bindingInProgress || attestationService != null) {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, false);
            }
        } catch (Exception ignored) {
        } finally {
            attestationService = null;
            bindingInProgress = false;
            isBound = false;
        }
    }

    public IAttestationService getService() {
        return attestationService;
    }

    private void checkShizukuAndBind() {
        if (!Shizuku.pingBinder()) {
            callback.onStatus("failed");
            callback.onStatus("Shizuku error: binder not available");
            return;
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bindService();
        } else {
            Shizuku.requestPermission(0);
        }
    }

    private void onRequestPermissionResult(int requestCode, int grantResult) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            bindService();
        } else {
            callback.onStatus("failed");
            callback.onStatus("Shizuku error: permission denied");
        }
    }

    private void bindService() {
        if (attestationService != null || bindingInProgress || isBound) {
            return;
        }
        try {
            bindingInProgress = true;
            Shizuku.bindUserService(userServiceArgs, serviceConnection);
        } catch (Exception e) {
            bindingInProgress = false;
            Log.e(TAG, "Failed to bind service", e);
            callback.onStatus("failed");
            callback.onStatus("Shizuku error: " + (e.getMessage() == null ? "bind exception" : e.getMessage()));
        }
    }
}
