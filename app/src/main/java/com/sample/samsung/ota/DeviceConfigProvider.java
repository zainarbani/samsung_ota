package com.sample.samsung.ota;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class DeviceConfigProvider {
    private static final String DEFAULT_MCC = "901";
    private static final String DEFAULT_MNC = "00";
    private static final String FOTA_CLIENT_VERSION = "4.3.27";
    private static final String EXCEPTION_MCC_LIST = "001,002,999,@65,000";
    private static final String EXCEPTION_MNC_LIST = "@5";

    private DeviceConfigProvider() {
    }

    public static DeviceConfig read(Context context) {
        String model = readModelName();
        String customerCode = readSalesCode();
        String deviceId = readDeviceId(context);
        String simMcc = readSimMcc(context);
        String simMnc = readSimMnc(context);
        String netMcc = readNetworkMcc(context);
        String netMnc = readNetworkMnc(context);
        String firmwareVersion = readFirmwareVersion();
        String uniqueNumber = readUniqueNumber();
        String serial = readSerial();
        String carrierId = readSystemProperty("ro.boot.carrierid", customerCode);
        String fotaClientVersion = FOTA_CLIENT_VERSION;

        return new DeviceConfig(
                model,
                customerCode,
                deviceId,
                firmwareVersion,
                uniqueNumber,
                serial,
                simMcc,
                simMnc,
                netMcc,
                netMnc,
                carrierId,
                fotaClientVersion);
    }

    private static String readDeviceId(Context context) {
        String imei = getPrimaryImei(context);
        if (!imei.isEmpty()) {
            if (imei.length() == 14 && Character.isDigit(imei.charAt(0))) {
                imei = appendLuhnChecksum(imei);
            }
            return "IMEI:" + imei;
        }

        String meid = getMeid(context);
        if (!meid.isEmpty()) {
            if (meid.length() >= 15) {
                meid = meid.substring(0, 14);
            }
            return "MEID:" + meid;
        }

        String serial = readSerial();
        if (!serial.isEmpty()) {
            return "TWID:" + serial;
        }

        String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        return "TWID:" + (androidId == null ? "unknown" : androidId);
    }

    private static String appendLuhnChecksum(String input14) {
        int sum = 0;
        for (int i = 0; i < input14.length(); i++) {
            int value = input14.charAt(i) - '0';
            if (i % 2 == 1) {
                value *= 2;
                if (value >= 10) {
                    value = (value % 10) + 1;
                }
            }
            sum += value;
        }
        int mod = sum % 10;
        if (mod == 0) {
            return input14;
        }
        return input14 + (10 - mod);
    }

    private static String readSalesCode() {
        String code = readSystemProperty("ro.csc.sales_code", "");
        if (code.isEmpty()) {
            code = readSystemProperty("ril.sales_code", "BTU");
        }

        if ("MKT".equals(code) || "KTT".equals(code)) {
            return "KT";
        }
        if ("MLG".equals(code) || "LGT".equals(code)) {
            return "LG";
        }
        if ("MSK".equals(code)) {
            return "SKT";
        }
        if ("BST".equals(code) || "SPR".equals(code) || "VMU".equals(code) || "XAS".equals(code)) {
            return "SPR";
        }

        if ("TFG".equals(code)) {
            String productCode = readSystemProperty("ril.product_code", "");
            if (productCode.length() >= 3) {
                String suffix = productCode.substring(productCode.length() - 3);
                String movistar = "TMM/UFN/UFU/COB/CHT/SAM/VMT/TGU/SAL/NBS/PBS/EBE/CRM";
                if (movistar.contains(suffix)) {
                    return suffix;
                }
            }
        }
        return code;
    }

    private static String readFirmwareVersion() {
        String pda = readSystemProperty("ro.build.PDA", "");
        String csc = readSystemProperty("ril.official_cscver", "");
        String phone = readSystemProperty("ril.sw_ver", "");

        if (pda.isEmpty() && csc.isEmpty() && phone.isEmpty()) {
            String incremental = Build.VERSION.INCREMENTAL == null ? "" : Build.VERSION.INCREMENTAL;
            return incremental;
        }
        return pda + "/" + csc + "/" + phone;
    }

    private static String readModelName() {
        if (Build.MODEL != null && !Build.MODEL.isEmpty()) {
            return Build.MODEL;
        }
        String productModel = readSystemProperty("ro.product.model", "");
        int slash = productModel.indexOf('/');
        return slash > 0 ? productModel.substring(0, slash) : productModel;
    }

    private static String readSerial() {
        try {
            String serial = Build.getSerial();
            return serial == null ? "" : serial;
        } catch (SecurityException e) {
            return "";
        }
    }

    private static int getDataSubId() {
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        return subId > -1 ? subId : 0;
    }

    private static TelephonyManager getTelephonyManager(Context context, int subId) {
        try {
            TelephonyManager tm = context.getSystemService(TelephonyManager.class);
            if (tm == null) {
                return null;
            }
            return tm.createForSubscriptionId(subId);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getPrimaryImei(Context context) {
        try {
            TelephonyManager tm = context.getSystemService(TelephonyManager.class);
            if (tm == null) {
                return "";
            }
            String primary = tm.getPrimaryImei();
            return primary == null ? "" : primary;
        } catch (Exception e) {
            return "";
        }
    }

    private static String getMeid(Context context) {
        try {
            TelephonyManager tm = context.getSystemService(TelephonyManager.class);
            if (tm == null) {
                return "";
            }
            String meid = tm.getMeid(0);
            return meid == null ? "" : meid;
        } catch (Exception e) {
            return "";
        }
    }

    private static String readMccFromOperator(String operator) {
        if (operator == null || operator.length() < 3) {
            return DEFAULT_MCC;
        }
        String mcc = operator.substring(0, 3);
        return EXCEPTION_MCC_LIST.contains(mcc) ? DEFAULT_MCC : mcc;
    }

    private static String readMncFromOperator(String operator) {
        if (operator == null || operator.length() <= 3) {
            return DEFAULT_MNC;
        }
        String mnc = operator.substring(3);
        return EXCEPTION_MNC_LIST.equals(mnc) ? DEFAULT_MNC : mnc;
    }

    private static String readNetworkMcc(Context context) {
        try {
            TelephonyManager tm = getTelephonyManager(context, getDataSubId());
            if (tm == null) {
                return "";
            }
            return readMccFromOperator(tm.getNetworkOperator());
        } catch (Exception e) {
            return "";
        }
    }

    private static String readNetworkMnc(Context context) {
        try {
            TelephonyManager tm = getTelephonyManager(context, getDataSubId());
            if (tm == null) {
                return "";
            }
            return readMncFromOperator(tm.getNetworkOperator());
        } catch (Exception e) {
            return "";
        }
    }

    private static String readSimMcc(Context context) {
        try {
            TelephonyManager tm = getTelephonyManager(context, getDataSubId());
            if (tm == null || tm.getSimState() != TelephonyManager.SIM_STATE_READY) {
                return DEFAULT_MCC;
            }
            return readMccFromOperator(tm.getSimOperator());
        } catch (Exception e) {
            return DEFAULT_MCC;
        }
    }

    private static String readSimMnc(Context context) {
        try {
            TelephonyManager tm = getTelephonyManager(context, getDataSubId());
            if (tm == null || tm.getSimState() != TelephonyManager.SIM_STATE_READY) {
                return DEFAULT_MNC;
            }
            return readMncFromOperator(tm.getSimOperator());
        } catch (Exception e) {
            return DEFAULT_MNC;
        }
    }

    private static String readUniqueNumber() {
        String[] preferredPaths = new String[]{
                "sys/block/mmcblk0/device/unique_number",
                "sys/class/scsi_host/host0/unique_number",
                "/sys/class/sec/ufs/un",
                "/sys/class/sec/mmc/un"
        };
        for (String path : preferredPaths) {
            String value = readText(path).toUpperCase(Locale.US);
            if (!value.isEmpty()) {
                return value;
            }
        }

        String cid = readText("/sys/block/mmcblk0/device/cid");
        String memName = readText("/sys/block/mmcblk0/device/name");
        if (cid.isEmpty() || memName.isEmpty()) {
            return "000000000000000000";
        }
        try {
            String maker = cid.substring(0, 2);
            String part;
            if ("15".equals(maker)) {
                part = memName.substring(0, 2);
            } else if ("02".equals(maker) || "45".equals(maker)) {
                part = memName.substring(3, 5);
            } else if ("11".equals(maker) || "90".equals(maker)) {
                part = memName.substring(1, 3);
            } else if ("FE".equalsIgnoreCase(maker)) {
                part = memName.substring(4, 6);
            } else {
                part = "";
            }
            return ("c" + part + cid.substring(18, 30)).toUpperCase(Locale.US);
        } catch (Exception e) {
            return "000000000000000000";
        }
    }

    private static String readText(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return "";
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String readSystemProperty(String key, String fallback) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object value = sp.getMethod("get", String.class, String.class).invoke(null, key, fallback);
            if (value instanceof String) {
                return (String) value;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public static final class DeviceConfig {
        public final String modelName;
        public final String customerCode;
        public final String deviceUniqueId;
        public final String firmwareVersion;
        public final String uniqueNumber;
        public final String serialNumber;
        public final String simMcc;
        public final String simMnc;
        public final String netMcc;
        public final String netMnc;
        public final String carrierId;
        public final String fotaClientVersion;

        DeviceConfig(
                String modelName,
                String customerCode,
                String deviceUniqueId,
                String firmwareVersion,
                String uniqueNumber,
                String serialNumber,
                String simMcc,
                String simMnc,
                String netMcc,
                String netMnc,
                String carrierId,
                String fotaClientVersion) {
            this.modelName = emptyIfNull(modelName);
            this.customerCode = emptyIfNull(customerCode);
            this.deviceUniqueId = emptyIfNull(deviceUniqueId);
            this.firmwareVersion = emptyIfNull(firmwareVersion);
            this.uniqueNumber = emptyIfNull(uniqueNumber);
            this.serialNumber = emptyIfNull(serialNumber);
            this.simMcc = emptyIfNull(simMcc);
            this.simMnc = emptyIfNull(simMnc);
            this.netMcc = emptyIfNull(netMcc);
            this.netMnc = emptyIfNull(netMnc);
            this.carrierId = emptyIfNull(carrierId);
            this.fotaClientVersion = emptyIfNull(fotaClientVersion);
        }

        public DeviceConfig withFirmwareVersion(String firmwareVersion) {
            return new DeviceConfig(
                    this.modelName,
                    this.customerCode,
                    this.deviceUniqueId,
                    emptyIfNull(firmwareVersion),
                    this.uniqueNumber,
                    this.serialNumber,
                    this.simMcc,
                    this.simMnc,
                    this.netMcc,
                    this.netMnc,
                    this.carrierId,
                    this.fotaClientVersion);
        }

        private static String emptyIfNull(String value) {
            return value == null ? "" : value;
        }
    }
}
