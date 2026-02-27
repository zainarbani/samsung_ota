package com.sample.samsung.ota;

import android.util.Base64;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class FotaRequestSigner {
    private FotaRequestSigner() {
    }

    public static String buildOAuthHeader(
            String appId,
            String appSecret,
            String requestMethod,
            String requestUri,
            String requestBody,
            String timestamp) throws Exception {
        Map<String, String> oauth = new LinkedHashMap<>();
        oauth.put("oauth_consumer_key", appId);
        oauth.put("oauth_nonce", randomHex(10));
        oauth.put("oauth_signature_method", "HMAC-SHA1");
        oauth.put("oauth_timestamp", timestamp);
        oauth.put("oauth_version", "1.0");

        String signature = generateSignature(appSecret, requestMethod, requestUri, requestBody, oauth);
        oauth.put("oauth_signature", signature);

        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : oauth.entrySet()) {
            if (header.length() > 0) {
                header.append(",");
            }
            header.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return header.toString();
    }

    private static String generateSignature(
            String appSecret,
            String requestMethod,
            String requestUri,
            String requestBody,
            Map<String, String> oauth) throws Exception {
        StringBuilder baseString = new StringBuilder();
        baseString.append(requestMethod.toUpperCase());
        baseString.append('&');
        baseString.append(normalizeUrlWithOAuthSpec(requestUri));
        baseString.append('&');
        baseString.append(normalizeParameters(oauth));
        if (requestBody != null && !requestBody.isEmpty()) {
            baseString.append('&');
            baseString.append(requestBody);
        }

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.US_ASCII), "HmacSHA1"));
        byte[] digest = mac.doFinal(baseString.toString().getBytes(StandardCharsets.US_ASCII));
        return Base64.encodeToString(digest, Base64.NO_WRAP);
    }

    private static String normalizeUrlWithOAuthSpec(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme().toLowerCase();
        String authority = uri.getAuthority().toLowerCase();
        int port = uri.getPort();

        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            int colonIdx = authority.lastIndexOf(':');
            if (colonIdx >= 0) {
                authority = authority.substring(0, colonIdx);
            }
        }

        String path = uri.getRawPath() != null ? uri.getRawPath() : "";
        String query = uri.getRawQuery();
        String rawPath = path + (query != null ? "?" + query : "");
        if (rawPath.isEmpty()) {
            rawPath = "/";
        }
        return oauthEncode(scheme + "://" + authority + rawPath);
    }

    private static String normalizeParameters(Map<String, String> map) {
        StringBuilder params = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (params.length() > 0) {
                params.append('&');
            }
            params.append(entry.getKey()).append("=")
                    .append(entry.getValue().replace("\"", "").replace("&quot;", ""));
        }
        return oauthEncode(params.toString());
    }

    private static String oauthEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~")
                    .replace("%21", "!")
                    .replace("%27", "'")
                    .replace("%28", "(")
                    .replace("%29", ")");
        } catch (Exception e) {
            return "";
        }
    }

    private static String randomHex(int chars) {
        int bytesLen = (int) Math.ceil(chars / 2.0);
        byte[] bytes = new byte[bytesLen];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.substring(0, Math.min(chars, sb.length()));
    }
}
