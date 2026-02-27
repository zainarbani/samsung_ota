package io.github.thepbone.samsungfumo.syncml.elements;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;

public class Chal implements IXmlElement {
    private Meta meta;

    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.CHAL);
        if (meta != null) meta.write(writer);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object _param) {
        WbxmlError check = parser.parseCheckElement(WbxmlElement.CHAL);
        if (check != WbxmlError.ERR_OK) throw new SyncMlParseException(check);

        WbxmlError zeroBit = parser.zeroBitTagCheck();
        if (zeroBit == WbxmlError.ZEROBIT_TAG) return this;
        if (zeroBit != WbxmlError.ERR_OK) throw new SyncMlParseException(zeroBit);

        meta = (Meta) new Meta().parse(parser);
        WbxmlError checkEnd = parser.parseCheckElement(WbxmlElement.END);
        if (checkEnd != WbxmlError.ERR_OK) throw new SyncMlParseException(checkEnd);

        return this;
    }
}
