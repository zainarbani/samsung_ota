package io.github.thepbone.samsungfumo.utils;

import java.lang.reflect.Array;
import java.util.Arrays;

public class ArrayUtils {
    @SuppressWarnings("unchecked")
    public static <T> T[] arrayPush(T[] table, T value) {
        T[] newTable = Arrays.copyOf(table, table.length + 1);
        newTable[table.length] = value;
        return newTable;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] concatArray(T[][] arrays) {
        int length = 0;
        for (T[] array : arrays) {
            length += array.length;
        }

        if (arrays.length == 0) {
            return null;
        }

        T[] result = (T[]) Array.newInstance(arrays[0].getClass().getComponentType(), length);
        int currentPos = 0;
        for (T[] array : arrays) {
            System.arraycopy(array, 0, result, currentPos, array.length);
            currentPos += array.length;
        }

        return result;
    }
}
