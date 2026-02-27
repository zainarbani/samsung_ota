package io.github.thepbone.samsungfumo.utils;

import java.util.function.BiConsumer;

public class Log {
    public enum Severity {
        VRB,
        DBG,
        INF,
        WRN,
        ERR
    }

    public static BiConsumer<Severity, String> onLogEvent;
    public static Severity minSeverity = Severity.INF;

    public static void V(String str) {
        writeLine(Severity.VRB, str);
    }

    public static void D(String str) {
        writeLine(Severity.DBG, str);
    }

    public static void I(String str) {
        writeLine(Severity.INF, str);
    }

    public static void W(String str) {
        writeLine(Severity.WRN, str);
    }

    public static void E(String str) {
        writeLine(Severity.ERR, str);
    }

    private static void writeLine(Severity sev, String msg) {
        if (sev.ordinal() < minSeverity.ordinal()) {
            return;
        }
        if (onLogEvent == null) {
            System.out.println("[" + sev + "] " + msg);
        } else {
            onLogEvent.accept(sev, msg);
        }
    }
}
