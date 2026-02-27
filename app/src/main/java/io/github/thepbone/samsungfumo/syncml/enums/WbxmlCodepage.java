package io.github.thepbone.samsungfumo.syncml.enums;

public enum WbxmlCodepage {
    SYNCML(0),
    METINF(1),
    DEVINF(2);

    private final int value;
    WbxmlCodepage(int value) { this.value = value; }
    public int getValue() { return value; }
    public static WbxmlCodepage fromValue(int value) {
        for (WbxmlCodepage cp : values()) if (cp.value == value) return cp;
        return null;
    }
}
