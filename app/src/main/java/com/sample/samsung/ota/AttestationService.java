package com.sample.samsung.ota;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AttestationService extends IAttestationService.Stub {
    private static final String TAG = "AttestationService";

    public AttestationService() {
        prepareEnvironment();
    }

    private void prepareEnvironment() {
        try {
            if (Looper.getMainLooper() == null) {
                Looper.prepareMainLooper();
            }

            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Method initMainline = activityThreadClass.getDeclaredMethod("initializeMainlineModules");
                    initMainline.setAccessible(true);
                    initMainline.invoke(null);
                } catch (Exception ignored) {}
            }

            try {
                Class<?> providerClass;
                if (Build.VERSION.SDK_INT >= 31) {
                    providerClass = Class.forName("android.security.keystore2.AndroidKeyStoreProvider");
                } else {
                    providerClass = Class.forName("android.security.keystore.AndroidKeyStoreProvider");
                }
                Method installMethod = providerClass.getDeclaredMethod("install");
                installMethod.invoke(null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to install KeyStore provider", e);
            }

            Method systemMainMethod = activityThreadClass.getDeclaredMethod("systemMain");
            Object activityThread = systemMainMethod.invoke(null);

            Method getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext");
            Context systemContext = (Context) getSystemContextMethod.invoke(activityThread);

            int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
            Context context = systemContext.createPackageContext("android", flags);

            Field mPackageInfoField = context.getClass().getDeclaredField("mPackageInfo");
            mPackageInfoField.setAccessible(true);
            Object loadedApk = mPackageInfoField.get(context);

            Method makeApplicationMethod = loadedApk.getClass().getDeclaredMethod("makeApplication", boolean.class, Class.forName("android.app.Instrumentation"));
            Application app = (Application) makeApplicationMethod.invoke(loadedApk, true, null);

            Field mInitialApplicationField = activityThreadClass.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(activityThread, app);

            Log.d(TAG, "Environment prepared successfully with system application context");
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare environment", e);
        }
    }

    @Override
    public List<String> getAttestationCerts(String alias, byte[] challenge) throws RemoteException {
        AttestationHandler handler = new AttestationHandler(alias, challenge);
        List<byte[]> certs = handler.runAttestation().getCertificates();
        
        List<String> result = new ArrayList<>();
        if (certs != null) {
            for (byte[] cert : certs) {
                result.add(Base64.encodeToString(cert, Base64.NO_WRAP));
            }
        }
        return result;
    }

    public void destroy() {
        System.exit(0);
    }
}
