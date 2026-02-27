package com.sample.samsung.ota;

import io.github.thepbone.samsungfumo.secure.DesCrypt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class DmAuthHelper {
    private static final byte[] DICT = {
            1, 15, 5, 11, 19, 28, 23, 47, 35, 44, 2, 14, 6, 10, 18, 13, 22, 26, 32, 47,
            3, 13, 7, 9, 17, 30, 21, 25, 33, 45, 4, 12, 8, 63, 16, 31, 20, 24, 34, 46
    };
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private DmAuthHelper() {
    }

    public static String generateClientPassword(String clientName, String serverId) {
        try {
            char[] token = new char[64];
            int colonIdx = clientName.indexOf(':');
            String right = colonIdx >= 0 ? clientName.substring(colonIdx + 1) : clientName;

            int idx = 0;
            for (int i = 0; i < right.length(); i++) {
                char c = right.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    token[idx++] = c;
                }
            }
            if (idx == 0) {
                return "";
            }

            long v1 = 0;
            long v2 = 0;
            for (int i = 0; i < idx - 1; i++) {
                v1 += (long) token[i] * DICT[i];
                v2 += (long) token[i] * token[(idx - i) - 1] * DICT[i];
            }
            String devPwdKey = String.valueOf(v1) + v2;
            byte[] input = (serverId + devPwdKey + clientName).getBytes(StandardCharsets.UTF_8);

            MessageDigest md = MessageDigest.getInstance("MD5");
            char[] mdHex = encodeHex(md.digest(input));
            byte[] nameBytes = clientName.getBytes(StandardCharsets.UTF_8);
            input[0] = nameBytes[nameBytes.length - 2];
            input[1] = nameBytes[nameBytes.length - 1];

            String seed = "" + mdHex[1] + mdHex[4] + mdHex[5] + mdHex[7] +
                    new DesCrypt().generate(clientName, input);

            String out = seed;
            for (int i = 0; i < 3; i++) {
                out = adpShuffle(out);
            }
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    public static String makeMd5Digest(String clientName, String clientPassword, byte[] nonce) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] h1 = md.digest((clientName + ":" + clientPassword).getBytes(StandardCharsets.UTF_8));
            String h1b64 = Base64.getEncoder().encodeToString(h1) + ":";
            byte[] h1Bytes = h1b64.getBytes(StandardCharsets.UTF_8);

            byte[] merged = new byte[h1Bytes.length + nonce.length];
            System.arraycopy(h1Bytes, 0, merged, 0, h1Bytes.length);
            System.arraycopy(nonce, 0, merged, h1Bytes.length, nonce.length);
            byte[] h2 = md.digest(merged);
            return Base64.getEncoder().encodeToString(h2);
        } catch (Exception e) {
            return "";
        }
    }

    public static byte[] nextNonce(int currentMessageId, byte[] serverNonce) {
        if (currentMessageId == 1 || serverNonce == null || serverNonce.length == 0) {
            String factory = Base64.getEncoder().encodeToString(
                    (new SecureRandom().nextInt() + "SSNextNonce").getBytes(StandardCharsets.UTF_8));
            return Base64.getDecoder().decode(factory);
        }
        return serverNonce;
    }

    private static char[] encodeHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        int i = 0;
        for (byte b : bytes) {
            out[i++] = HEX[b & 0x0F];
            out[i++] = HEX[(b >> 4) & 0x0F];
        }
        return out;
    }

    private static String adpShuffle(String input) {
        StringBuilder sb = new StringBuilder(input);
        int len = sb.length();
        int odd = len % 2;
        int half = len / 2;
        if (odd != 0) {
            half++;
        }
        while (half < len) {
            char ch = sb.charAt(half);
            sb.deleteCharAt(half);
            int dst = len - half;
            if (odd == 0) {
                dst -= 1;
            }
            sb.insert(dst, ch);
            half++;
        }
        return sb.toString();
    }
}
