package io.github.thepbone.samsungfumo.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Base64Utils {
    public static String encode(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    public static String encode(byte[] bArr) {
        return Base64.getEncoder().encodeToString(bArr);
    }

    public static byte[] decode(String str) {
        return Base64.getDecoder().decode(str);
    }

    public static byte[] decode(byte[] bArr) {
        return Base64.getDecoder().decode(new String(bArr, StandardCharsets.UTF_8));
    }
}
