package io.github.thepbone.samsungfumo.syncml.enums;

public enum WbxmlCharset {
    UTF8(106);

    private final int value;
    WbxmlCharset(int value) { this.value = value; }
    public int getValue() { return value; }
    public static WbxmlCharset fromValue(int value) {
        for (WbxmlCharset charset : values()) if (charset.value == value) return charset;
        return null;
    }
}
