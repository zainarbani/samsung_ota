package io.github.thepbone.samsungfumo.syncml.enums;

import java.util.HashMap;
import java.util.Map;

public enum WbxmlElement {
    UNSET(-1),
    LITERAL_A(132),
    LITERAL_AC(196),
    LITERAL_C(68),
    STR_T(131),
    CODEPAGE(0),
    END(1),
    ENTITY(2),
    STR_I(3),
    LITERAL(4),
    ADD(5),
    ALERT(6),
    ARCHIVE(7),
    ATOMIC(8),
    CHAL(9),
    CMD(10),
    CMDID(11),
    CMDREF(12),
    COPY(13),
    CRED(14),
    DATA(15),
    DELETE(16),
    EXEC(17),
    FINAL(18),
    GET(19),
    ITEM(20),
    LANG(21),
    LOCNAME(22),
    LOCURI(23),
    MAP(24),
    MAPITEM(25),
    META(26),
    MSGID(27),
    MSGREF(28),
    NORESP(29),
    NORESULTS(30),
    PUT(31),
    REPLACE(32),
    RESPURI(33),
    RESULTS(34),
    SEARCH(35),
    SEQUENCE(36),
    SESSIONID(37),
    SFTDEL(38),
    SOURCE(39),
    SOURCEREF(40),
    STATUS(41),
    SYNC(42),
    SYNCBODY(43),
    SYNCHDR(44),
    SYNCML(45),
    TARGET(46),
    TARGETREF(47),
    NULL(48),
    VERDTD(49),
    VERPROTO(50),
    NUMBEROFCHANGES(51),
    MOREDATA(52),
    FIELD(53),
    FILTER(54),
    RECORD(55),
    FILTERTYPE(56),
    SOURCEPARENT(57),
    TARGETPARENT(58),
    MOVE(59),
    CORRELATOR(60),
    OPAQUE(195),
    EXT_I_0(64),
    EXT_I_1(65),
    EXT_I_2(66),
    EXT_0(192),
    EXT_1(193),
    EXT_2(194),
    EXT_T_0(128),
    EXT_T_1(129),
    EXT_T_2(130);

    private final int value;
    private static final Map<Integer, WbxmlElement> BY_VALUE = new HashMap<>();

    static {
        for (WbxmlElement e : values()) {
            BY_VALUE.put(e.value, e);
        }
    }

    WbxmlElement(int value) { this.value = value; }
    public int getValue() { return value; }
    public static WbxmlElement fromValue(int value) {
        return BY_VALUE.get(value);
    }
}
