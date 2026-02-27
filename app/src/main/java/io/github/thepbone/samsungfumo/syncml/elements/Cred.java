package io.github.thepbone.samsungfumo.syncml.elements;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;

import java.io.IOException;

public class Cred implements IXmlElement {
    private Meta meta;
    private String data;

    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.CRED);
        if (meta != null) meta.write(writer);
        if (data != null) writer.writeElementString(WbxmlElement.DATA, data);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object _param) {
        WbxmlError error = parser.parseCheckElement(WbxmlElement.CRED);
        if (error != WbxmlError.ERR_OK) throw new SyncMlParseException(error);

        do {
            WbxmlElement element = parser.currentElement();
            switch (element) {
                case END:
                    parser.parseReadElement();
                    return this;
                case CODEPAGE:
                    parser.parseReadElement();
                    try {
                        parser.currentCodePage = WbxmlCodepage.fromValue(parser.readByte());
                    } catch (IOException e) {
                        error = WbxmlError.FAIL;
                    }
                    break;
                case DATA:
                    error = parser.parseElement(element);
                    data = parser.currentInnerText;
                    break;
                case META:
                    meta = (Meta) new Meta().parse(parser);
                    break;
                default:
                    error = WbxmlError.UNKNOWN_ELEMENT;
                    break;
            }
        } while (error == WbxmlError.ERR_OK);

        return this;
    }
}
