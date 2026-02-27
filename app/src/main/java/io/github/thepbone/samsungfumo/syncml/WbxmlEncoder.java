package io.github.thepbone.samsungfumo.syncml;

import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCharset;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class WbxmlEncoder {
    protected final ByteArrayOutputStream memoryStream = new ByteArrayOutputStream();

    public boolean startDocument(WbxmlCharset charset, String stringTable, int i3) {
        if (!appendByte(2) || !appendUInt32(0)) {
            return false;
        }

        return appendUInt32(0) && appendUInt32(charset.getValue()) && appendUInt32(i3) &&
                appendToBuffer(stringTable);
    }

    public boolean startElement(int tag, boolean z) {
        int i = tag;
        if (z) {
            i |= 64;
        }

        return appendByte(i);
    }

    public boolean endElement() {
        return appendByte(1);
    }

    public boolean addSwitchpage(int i) {
        return appendByte(0) && appendByte(i);
    }

    public boolean addContent(String str) {
        if (!appendByte(3) || !appendToBuffer(str)) {
            return false;
        }

        memoryStream.write(0);
        return true;
    }

    public boolean addOpaque(char[] cArr, int i) {
        if (!(appendByte(WbxmlElement.OPAQUE.getValue()) && appendUInt32(i))) {
            return false;
        }

        try {
            memoryStream.write(new String(cArr).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    public ByteArrayOutputStream getBuffer() {
        return memoryStream;
    }

    public boolean appendToBuffer(String str) {
        try {
            memoryStream.write(str.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean appendByte(int i) {
        memoryStream.write(i);
        return true;
    }

    public boolean appendUInt32(int i) {
        int i2;
        byte[] bArr = new byte[5];
        int i3 = 0;
        while (true) {
            i2 = i3 + 1;
            bArr[i3] = (byte) (i & 127);
            i >>= 7;
            if (i == 0) {
                break;
            }

            i3 = i2;
        }

        while (i2 > 1) {
            i2--;
            memoryStream.write((bArr[i2] & 0xFF) | 0x80);
        }

        memoryStream.write(bArr[0] & 0xFF);
        return true;
    }
}
