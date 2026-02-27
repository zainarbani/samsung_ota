package io.github.thepbone.samsungfumo.syncml;

import io.github.thepbone.samsungfumo.syncml.elements.Item;
import io.github.thepbone.samsungfumo.syncml.elements.Meta;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;
import io.github.thepbone.samsungfumo.utils.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WbxmlDecoder {
    public String currentInnerText = "";
    public WbxmlCodepage currentCodePage = WbxmlCodepage.SYNCML;
    public int index = 0;
    public Meta meta;

    private String stringT = "";
    private String stringTable;
    private int publicUid;
    private final byte[] buffer;

    protected WbxmlDecoder(byte[] buffer) {
        this.buffer = buffer;
    }

    public WbxmlElement parseReadElement() {
        try {
            return WbxmlElement.fromValue(readByte() & 63 & 127);
        } catch (IOException e) {
            Log.E("WbxmlDecoder.parseReadElement: Failed to read byte: " + e.toString());
            return WbxmlElement.UNSET;
        }
    }

    public Item[] parseItemlist(Item[] xDmList) {
        List<Item> list = new ArrayList<>();
        if (xDmList != null) {
            for (Item item : xDmList) list.add(item);
        }

        while (true) {
            WbxmlElement i = currentElement();
            if (i != WbxmlElement.ITEM) {
                return list.toArray(new Item[0]);
            }

            Item item = new Item();
            item.parse((SyncMlParser) this);
            list.add(item);
        }
    }

    public String parseContent() {
        try {
            int b = readByte();
            WbxmlElement nextByte = WbxmlElement.fromValue(b);
            if (nextByte == WbxmlElement.STR_I) {
                return parseStrI();
            }
            if (nextByte == WbxmlElement.STR_T) {
                return parseStrT();
            }
            if (nextByte == WbxmlElement.OPAQUE) {
                return parseExtension(nextByte);
            }

            index--;
            skipElement();
        } catch (IOException e) {
            Log.E("WbxmlDecoder.parseContent: " + e);
        }
        return null;
    }

    public List<String> parseElelist(WbxmlElement listElementType, List<String> xDmList) {
        while (true) {
            WbxmlElement currentElement = currentElement();
            if (currentElement != listElementType) {
                return xDmList;
            }
            if (parseElement(listElementType) != WbxmlError.ERR_OK) {
                return null;
            }
            xDmList.add(currentInnerText);
        }
    }

    public WbxmlError parseElement(WbxmlElement i) {
        currentInnerText = "";
        WbxmlError checkResult = parseCheckElement(i);
        if (checkResult != WbxmlError.ERR_OK) {
            return checkResult;
        }

        WbxmlError zeroBitResult = zeroBitTagCheck();
        if (zeroBitResult == WbxmlError.ZEROBIT_TAG) {
            return WbxmlError.ERR_OK;
        }
        if (zeroBitResult != WbxmlError.ERR_OK) {
            return zeroBitResult;
        }

        WbxmlError skipLiteralResult = skipLiteralElement();
        if (skipLiteralResult != WbxmlError.ERR_OK) {
            return skipLiteralResult;
        }

        while (true) {
            String content = parseContent();
            currentInnerText += (content != null ? content : "");

            try {
                if (peekByte() != 131) {
                    break;
                }
                readByte();
                currentInnerText += parseStrT();
                if (peekByte() == 1) {
                    break;
                }
            } catch (IOException e) {
                break;
            }
        }

        return parseCheckElement(WbxmlElement.END);
    }

    public WbxmlError parseBlankElement(WbxmlElement element) {
        boolean z = (buffer[index] & 64) != 0;
        WbxmlError checkResult = parseCheckElement(element);
        if (checkResult != WbxmlError.ERR_OK) {
            return checkResult;
        }

        if (!z) {
            return WbxmlError.ERR_OK;
        }
        
        return parseCheckElement(WbxmlElement.END);
    }

    public WbxmlElement currentElement() {
        if (index >= buffer.length) return WbxmlElement.UNSET;
        int i = buffer[index] & 0xFF;
        return WbxmlElement.fromValue(i & 63 & 127);
    }

    public WbxmlError parseCheckElement(WbxmlElement element) {
        WbxmlElement next = parseReadElement();
        if (element == next) {
            return WbxmlError.ERR_OK;
        }
        Log.E("WbxmlDecoder.parseCheckElement: Unknown element; " + element + " does not match with " + next + "!");
        return WbxmlError.UNKNOWN_ELEMENT;
    }

    public WbxmlError zeroBitTagCheck() {
        if (index <= 0) return WbxmlError.FAIL;
        int i3 = buffer[index - 1] & 0xFF;
        int i4 = i3 & 63 & 127;
        if (i4 < 5 || i4 > 60 || (i3 & 64) != 0) {
            return WbxmlError.ERR_OK;
        } else {
            Log.E("WbxmlDecoder.zeroBitTagCheck: Encountered a zero bit tag");
            return WbxmlError.ZEROBIT_TAG;
        }
    }

    public void skipElement() {
        int i = 0;
        while (true) {
            WbxmlElement currentElement = currentElement();
            if (currentElement == WbxmlElement.CODEPAGE) {
                try {
                    readByte();
                    readByte();
                } catch (IOException ignored) {}
            } else if (currentElement == WbxmlElement.END) {
                try {
                    readByte();
                } catch (IOException ignored) {}
                i--;
                if (i <= 0) break;
            } else {
                if (currentElement != WbxmlElement.STR_I && currentElement != WbxmlElement.STR_T) {
                    if (currentElement != WbxmlElement.OPAQUE) {
                        try {
                            readByte();
                        } catch (IOException ignored) {}
                        i++;
                    }
                }
                parseContent();
            }
        }

        while (currentElement() == WbxmlElement.CODEPAGE) {
            try {
                readByte();
                readByte();
            } catch (IOException ignored) {}
        }
    }

    public WbxmlError skipLiteralElement() {
        if (currentElement() != WbxmlElement.LITERAL) {
            return WbxmlError.ERR_OK;
        }
        try {
            while (readByte() != WbxmlElement.END.getValue());
        } catch (IOException e) {
            return WbxmlError.FAIL;
        }
        return WbxmlError.ERR_OK;
    }

    public void parseStartdoc(WbxmlDecoder xDmParser) {
        try {
            readByte();
            xDmParser.publicUid = readUInt32();
            if (xDmParser.publicUid == 0) {
                readUInt32();
            }
            readUInt32();
            xDmParser.stringTable = parseStringTable();
            stringT = xDmParser.stringTable;
        } catch (IOException e) {
            Log.E("WbxmlDecoder.parseStartDoc: " + e.toString());
        }
    }

    public String parseStrT() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            int uint1 = readUInt32();
            while (uint1 < stringT.length() && stringT.charAt(uint1) != 0) {
                baos.write(stringT.charAt(uint1));
                uint1++;
            }
        } catch (IOException e) {
            Log.E("WbxmlDecoder.parseStrT" + e.toString());
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    public String parseStrI() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            while (true) {
                int b = readByte();
                if (b == 0) {
                    return new String(baos.toByteArray(), StandardCharsets.UTF_8);
                }
                baos.write(b);
            }
        } catch (IOException e) {
            Log.E("WbxmlDecoder.parseStrI: " + e);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public String parseExtension(WbxmlElement element) {
        if (element == WbxmlElement.OPAQUE) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                int nextUint32 = readUInt32();
                for (int i2 = 0; i2 < nextUint32; i2++) {
                    baos.write(readByte());
                }
                return new String(baos.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                Log.E("WbxmlDecoder.parseExtension: " + e.toString());
            }
        }
        return null;
    }

    public String parseStringTable() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            int uint1 = readUInt32();
            for (int i = 0; i < uint1; i++) {
                baos.write(readByte());
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.E("WbxmlDecoder.parseStringTable: " + e.toString());
            return null;
        }
    }

    public int readUInt32() throws IOException {
        int i = 0;
        for (int i2 = 0; i2 < 5; i2++) {
            int byte1 = readByte();
            i = (i << 7) | (byte1 & 127);
            if ((byte1 & 128) == 0) {
                return i;
            }
        }
        return 0;
    }

    public int readByte() throws IOException {
        if (index >= buffer.length) {
            throw new IOException("Unexpected EOF ReadByte");
        }
        return buffer[index++] & 0xFF;
    }

    private int peekByte() throws IOException {
        if (index >= buffer.length) {
            throw new IOException("Unexpected EOF PeekByte");
        }
        return buffer[index] & 0xFF;
    }
}
