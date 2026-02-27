package io.github.thepbone.samsungfumo.syncml.enums;

public enum PcDataType {
    STRING(0),
    OPAQUE(1),
    EXTENSION(2);

    private final int value;
    PcDataType(int value) { this.value = value; }
    public int getValue() { return value; }
    public static PcDataType fromValue(int value) {
        for (PcDataType type : values()) if (type.value == value) return type;
        return null;
    }
}
