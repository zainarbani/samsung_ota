package io.github.thepbone.samsungfumo.syncml.elements;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.enums.MetinfTag;
import io.github.thepbone.samsungfumo.syncml.enums.PcDataType;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;
import io.github.thepbone.samsungfumo.utils.Log;

import java.io.IOException;

public class PcData implements IXmlElement {
    private String data;
    private PcDataType type = PcDataType.STRING;

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public PcDataType getType() { return type; }
    public void setType(PcDataType type) { this.type = type; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeElementString(WbxmlElement.DATA, data);
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object param) {
        if (param == null) {
            throw new SyncMlParseException(WbxmlError.INVALID_PARAMETER);
        }

        WbxmlError checkElementResult = parser.parseCheckElement((WbxmlElement) param);
        if (checkElementResult != WbxmlError.ERR_OK) {
            throw new SyncMlParseException(checkElementResult);
        }

        WbxmlError zeroBitTagCheck = parser.zeroBitTagCheck();
        if (zeroBitTagCheck == WbxmlError.ZEROBIT_TAG) {
            return this;
        }

        if (zeroBitTagCheck != WbxmlError.ERR_OK) {
            Log.E("not WBXML_ERR_OK");
            throw new SyncMlParseException(zeroBitTagCheck);
        }

        try {
            int b = parser.readByte();
            WbxmlElement element = WbxmlElement.fromValue(b);
            switch (element) {
                case STR_I:
                    setStringPcdata(parser.parseStrI());
                    break;
                case STR_T:
                    setStringPcdata(parser.parseStrT());
                    break;
                case OPAQUE: {
                    String parsed = parser.parseExtension(element);
                    type = PcDataType.OPAQUE;
                    setStringPcdata(parsed);
                    break;
                }
                case CODEPAGE: {
                    parser.currentCodePage = WbxmlCodepage.fromValue(parser.readByte());
                    WbxmlElement subElement = parser.currentElement();
                    do {
                        switch (parser.currentCodePage) {
                            case METINF:
                                if (subElement.getValue() == MetinfTag.ANCHOR.getValue()) {
                                    Log.E("Parser: Pcdata.Anchor not implemented");
                                    throw new SyncMlParseException(WbxmlError.NOT_IMPLEMENTED);
                                }
                                if (subElement.getValue() == MetinfTag.MEM.getValue()) {
                                    Log.E("Parser: Pcdata.Mem not implemented");
                                    throw new SyncMlParseException(WbxmlError.NOT_IMPLEMENTED);
                                }
                                break;
                        }

                        if (subElement.getValue() == 0) {
                            parser.parseReadElement();
                            parser.parseReadElement();
                        }

                        subElement = parser.currentElement();
                    } while (subElement != WbxmlElement.END);

                    break;
                }
                default:
                    parser.index -= 1;
                    parser.skipElement();
                    type = PcDataType.EXTENSION;
                    data = null;
                    break;
            }
        } catch (IOException e) {
            throw new SyncMlParseException(WbxmlError.FAIL);
        }

        WbxmlError checkResult = parser.parseCheckElement(WbxmlElement.END);
        if (checkResult != WbxmlError.ERR_OK) {
            throw new SyncMlParseException(checkResult);
        }

        return this;
    }

    private void setStringPcdata(String str) {
        this.type = PcDataType.STRING;
        this.data = str;
    }
}
