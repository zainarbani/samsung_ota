package io.github.thepbone.samsungfumo.syncml.commands;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.elements.Cred;
import io.github.thepbone.samsungfumo.syncml.elements.IXmlElement;
import io.github.thepbone.samsungfumo.syncml.elements.Meta;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;

import java.io.IOException;

public class Replace extends Cmd {
    private Cred cred;
    private Meta meta;

    public Cred getCred() { return cred; }
    public void setCred(Cred cred) { this.cred = cred; }
    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.REPLACE);
        super.write(writer);
        if (cred != null) cred.write(writer);
        if (meta != null) meta.write(writer);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object _param) {
        WbxmlError check = parser.parseCheckElement(WbxmlElement.REPLACE);
        if (check != WbxmlError.ERR_OK) throw new SyncMlParseException(check);

        WbxmlError error = parser.zeroBitTagCheck();
        if (error == WbxmlError.ZEROBIT_TAG) return this;
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
                case CMDID:
                    error = parser.parseElement(element);
                    setCmdID(Integer.parseInt(parser.currentInnerText));
                    break;
                case CRED:
                    cred = (Cred) new Cred().parse(parser);
                    break;
                case ITEM:
                    setItems(parser.parseItemlist(getItems()));
                    break;
                case META:
                    meta = (Meta) new Meta().parse(parser);
                    break;
                case NORESP:
                    setIsNoResp(parser.parseBlankElement(element).getValue());
                    break;
                default:
                    error = WbxmlError.UNKNOWN_ELEMENT;
                    break;
            }
        } while (error == WbxmlError.ERR_OK);

        throw new SyncMlParseException(error);
    }
}
