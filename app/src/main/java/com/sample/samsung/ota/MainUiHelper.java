package com.sample.samsung.ota;

import android.os.Build;

public final class MainUiHelper {
    private MainUiHelper() {
    }

    public static String buildDeviceIdDisplay(String deviceUniqueId, String serialNumber) {
        String id = deviceUniqueId == null ? "" : deviceUniqueId.trim();
        String sn = serialNumber == null ? "" : serialNumber.trim();
        if (id.isEmpty()) {
            return sn;
        }
        if (sn.isEmpty()) {
            return id;
        }
        return id + " / " + sn;
    }

    public static String buildAndroidVersionDisplay() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }
}
