package io.github.thepbone.samsungfumo.syncml.enums;

public enum MetinfTag {
    ANCHOR(5),
    EMI(6),
    FORMAT(7),
    FREEID(8),
    FREEMEM(9),
    LAST(10),
    MARK(11),
    MAXMSGSIZE(12),
    MAXOBJSIZE(21),
    MEM(13),
    METINF(14),
    NEXT(15),
    NEXTNONCE(16),
    SHAREDMEM(17),
    SIZE(18),
    TYPE(19),
    VERSION(20);

    private final int value;

    MetinfTag(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static MetinfTag fromValue(int value) {
        for (MetinfTag tag : values()) {
            if (tag.value == value) return tag;
        }
        return null;
    }
}
