package com.sample.samsung.ota;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public final class FotaCryptoUtils {
    private FotaCryptoUtils() {
    }

    public static String encryptNetworkBearer(String plainText) {
        try {
            String key = idmMealyMachine(5932, 16);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            return plainText;
        }
    }

    private static String idmMealyMachine(int seed, int size) {
        byte[] out = new byte[size];
        int[][] next = {
                {11, 0}, {0, 4}, {8, 15}, {11, 2}, {0, 3}, {9, 0}, {15, 0}, {0, 0},
                {5, 0}, {0, 0}, {0, 0}, {1, 6}, {0, 0}, {3, 13}, {0, 0}, {2, 13}
        };
        char[][] table = {
                {'s', '3'}, {'v', 'n'}, {'1', '9'}, {'m', '0'}, {'e', 'c'}, {'3', 'B'}, {'7', 'N'}, {'k', '2'},
                {'2', 'C'}, {'a', 'C'}, {'J', '2'}, {'y', 'l'}, {'8', 'd'}, {'1', '0'}, {'A', '^'}, {'7', '0'}
        };

        int state = 0;
        int value = seed;
        for (int i = 0; i < size; i++) {
            int bit = value & 1;
            value >>= 1;
            out[i] = (byte) table[state][bit];
            state = next[state][bit];
        }
        return new String(out, StandardCharsets.UTF_8);
    }
}
