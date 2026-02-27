package com.sample.samsung.ota;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FotaClient {
    private static final String TAG = "FotaClient";

    private static final String FOTA_TIME_URL = "https://fota-apis.samsungdm.com/auth/time";
    private static final String FOTA_DEVICE_URL = "https://www.ospserver.net/device/fumo/device";
    private static final String FOTA_FW_URL = "https://fota-cloud-dn.ospserver.net/firmware";
    private static final String TIME_KEY = "j5p7ll8g33";
    private static final String TIME_SECRET = "5763D0052DC1462E13751F753384E9A9";
    private static final String REGI_KEY = "2cbmvps5z4";
    private static final String REGI_SECRET = "AF87056C54E8BFD81142D235F4F8E552";

    private final DeviceConfigProvider.DeviceConfig config;

    public FotaClient(Context context) {
        this(DeviceConfigProvider.read(context.getApplicationContext()));
    }

    public FotaClient(DeviceConfigProvider.DeviceConfig config) {
        this.config = config;
    }

    public String getConfigSummary() {
        return "model=" + config.modelName
                + ", csc=" + config.customerCode
                + ", deviceId=" + config.deviceUniqueId
                + ", fw=" + config.firmwareVersion
                + ", sim=" + config.simMcc + "/" + config.simMnc
                + ", net=" + config.netMcc + "/" + config.netMnc
                + ", carrierId=" + config.carrierId;
    }

    public FotaResult registerDeviceBootstrap() {
        return registerDevice("0", null, null, null, null);
    }

    public FotaResult registerDevice(String challenge, String appCert, String sakCert, String rootCert) {
        return registerDevice("1", challenge, appCert, sakCert, rootCert);
    }

    public FotaResult registerDevice(
            String authenticateType,
            String challenge,
            String appCert,
            String sakCert,
            String rootCert) {
        try {
            String serverTimeMs = getServerTime();
            if (serverTimeMs == null) {
                return FotaResult.error("Failed to fetch server time");
            }

            String payload = buildRegisterPayload(authenticateType, challenge, appCert, sakCert, rootCert);
            String authHeader = FotaRequestSigner.buildOAuthHeader(
                    REGI_KEY, REGI_SECRET, "POST", FOTA_DEVICE_URL, payload, serverTimeMs);

            HttpResponse response = doRequest(
                    "POST",
                    FOTA_DEVICE_URL,
                    baseHeaders(authHeader),
                    payload.getBytes(StandardCharsets.UTF_8));

            return FotaResult.fromResponse(response.statusCode, response.body);
        } catch (Exception e) {
            return FotaResult.error("Request failure: " + e.getMessage());
        }
    }

    public FirmwareFeedResult fetchFirmwareFeed() {
        try {
            String encryptedNb = FotaCryptoUtils.encryptNetworkBearer("WIFI");
            String rmtime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(LocalDateTime.now(ZoneOffset.UTC));

            String url = FOTA_FW_URL
                    + "/" + config.customerCode + "/"
                    + config.modelName.toUpperCase(Locale.US) + "/version.xml"
                    + "?px-nb=" + URLEncoder.encode(encryptedNb, StandardCharsets.UTF_8.toString())
                    + "&pn-rmtime=" + URLEncoder.encode(rmtime, StandardCharsets.UTF_8.toString());

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", "SAMSUNG-Android");
            headers.put("Accept", "*/*");
            headers.put("Accept-Encoding", "identity");

            HttpResponse response = doRequest("GET", url, headers, null);
            if (response.statusCode < 200 || response.statusCode >= 300) {
                return FirmwareFeedResult.error(response.statusCode, response.body);
            }

            return FirmwareFeedResult.success(response.body, FotaXmlUtils.parseUpgradeValues(response.body));
        } catch (Exception e) {
            return FirmwareFeedResult.error(-1, "Firmware feed fetch failed: " + e.getMessage());
        }
    }

    private String getServerTime() {
        try {
            String oauthTimestamp = String.valueOf(System.currentTimeMillis());
            String authHeader = FotaRequestSigner.buildOAuthHeader(
                    TIME_KEY, TIME_SECRET, "GET", FOTA_TIME_URL, null, oauthTimestamp);
            HttpResponse response = doRequest("GET", FOTA_TIME_URL, baseHeaders(authHeader), null);
            if (response.statusCode < 200 || response.statusCode >= 300) {
                Log.e(TAG, "getServerTime failed: " + response.statusCode + " " + response.body);
                return null;
            }
            return FotaXmlUtils.readTag(response.body, "currentServerTime");
        } catch (Exception e) {
            Log.e(TAG, "getServerTime exception", e);
            return null;
        }
    }

    private Map<String, String> baseHeaders(String authHeader) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "SAMSUNG-Android");
        headers.put("Accept", "*, */*");
        headers.put("Accept-Encoding", "identity");
        headers.put("X-Sec-Dm-DeviceModel", config.modelName);
        headers.put("x-osp-version", "v1");
        headers.put("X-Sec-Dm-CustomerCode", config.customerCode);
        headers.put("Content-Type", "text/xml");
        headers.put("Authorization", authHeader);
        return headers;
    }

    private HttpResponse doRequest(String method, String url, Map<String, String> headers, byte[] body)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoInput(true);
        conn.setUseCaches(false);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }

        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
        }

        int statusCode = conn.getResponseCode();
        InputStream stream = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String responseBody = "";
        if (stream != null) {
            responseBody = readAll(stream);
        }
        conn.disconnect();
        return new HttpResponse(statusCode, responseBody);
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private String buildRegisterPayload(
            String authenticateType,
            String challenge,
            String appCert,
            String sakCert,
            String rootCert) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("deviceUniqueID", config.deviceUniqueId);
        fields.put("deviceModelID", config.modelName);
        fields.put("customerCode", config.customerCode);
        fields.put("firmwareVersion", config.firmwareVersion);
        fields.put("devicePhysicalAddressText", config.deviceUniqueId);
        fields.put("uniqueNumber", config.uniqueNumber);
        fields.put("deviceSerialNumber", config.serialNumber);
        fields.put("deviceTypeCode", "PHONE DEVICE");
        fields.put("deviceName", config.modelName);
        fields.put("mobileCountryCode", config.simMcc);
        fields.put("mobileNetworkCode", config.simMnc);
        fields.put("mobileCountryCodeByTelephony", config.netMcc);
        fields.put("mobileNetworkCodeByTelephony", config.netMnc);
        fields.put("terms", "Y");
        fields.put("termsVersion", "3.0");
        fields.put("fotaClientVer", config.fotaClientVersion);
        fields.put("carrierID", config.carrierId.isBlank() ? config.customerCode : config.carrierId);
        fields.put("authenticateType", authenticateType);
        if (challenge != null && !challenge.isBlank()) {
            fields.put("challenge", challenge);
        }
        if (appCert != null && !appCert.isBlank()) {
            fields.put("appCert", appCert);
        }
        if (sakCert != null && !sakCert.isBlank()) {
            fields.put("sakCert", sakCert);
        }
        if (rootCert != null && !rootCert.isBlank()) {
            fields.put("rootCert", rootCert);
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<FumoDeviceVO>");
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            xml.append("<").append(entry.getKey()).append(">");
            xml.append(FotaXmlUtils.escapeXml(entry.getValue()));
            xml.append("</").append(entry.getKey()).append(">");
        }
        xml.append("</FumoDeviceVO>");
        return xml.toString();
    }

    private static class HttpResponse {
        final int statusCode;
        final String body;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }
    }

    public static class FotaResult {
        public final int statusCode;
        public final String body;
        public final String errorCode;
        public final String errorMessage;
        public final String challenge;

        private FotaResult(int statusCode, String body, String errorCode, String errorMessage, String challenge) {
            this.statusCode = statusCode;
            this.body = body;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.challenge = challenge;
        }

        static FotaResult fromResponse(int statusCode, String body) {
            String code = FotaXmlUtils.readTag(body, "code");
            String message = FotaXmlUtils.readTag(body, "message");
            String challenge = FotaXmlUtils.readTag(body, "challenge");
            return new FotaResult(statusCode, body, code, message, challenge);
        }

        static FotaResult error(String message) {
            return new FotaResult(-1, "", "LOCAL_ERROR", message, null);
        }

        boolean needsChallengeRetry() {
            return statusCode == 406
                    && "FUD_3062".equals(errorCode)
                    && challenge != null
                    && !challenge.isEmpty();
        }
    }

    public static class FirmwareFeedResult {
        public final int statusCode;
        public final String body;
        public final List<String> versions;
        public final String error;

        private FirmwareFeedResult(int statusCode, String body, List<String> versions, String error) {
            this.statusCode = statusCode;
            this.body = body;
            this.versions = versions;
            this.error = error;
        }

        static FirmwareFeedResult success(String body, List<String> versions) {
            return new FirmwareFeedResult(200, body, versions, null);
        }

        static FirmwareFeedResult error(int statusCode, String error) {
            return new FirmwareFeedResult(statusCode, "", new ArrayList<>(), error);
        }
    }
}
