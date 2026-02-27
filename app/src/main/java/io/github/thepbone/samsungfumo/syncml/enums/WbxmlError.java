package io.github.thepbone.samsungfumo.syncml.enums;

public enum WbxmlError {
    ERR_OK(0),
    FAIL(1),
    UNKNOWN_ELEMENT(2),
    MEMORY_NOT_ENOUGH(3),
    IMPLICIT_BUFFER_END(4),
    BUFFER_TOO_SMALL(5),
    INVALID_PARAMETER(6),
    UNKNOWN(7),
    ZEROBIT_TAG(8),
    NOT_IMPLEMENTED(100);

    private final int value;
    WbxmlError(int value) { this.value = value; }
    public int getValue() { return value; }
    public static WbxmlError fromValue(int value) {
        for (WbxmlError error : values()) if (error.value == value) return error;
        return null;
    }
}
