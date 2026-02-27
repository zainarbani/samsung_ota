package io.github.thepbone.samsungfumo.syncml.elements;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;
import io.github.thepbone.samsungfumo.utils.Log;

public class Target implements IXmlElement {
    private String locURI;

    public String getLocURI() { return locURI; }
    public void setLocURI(String locURI) { this.locURI = locURI; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.TARGET);
        writer.writeElementString(WbxmlElement.LOCURI, locURI);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object param) {
        WbxmlError check = parser.parseCheckElement(WbxmlElement.TARGET);
        if (check != WbxmlError.ERR_OK) throw new SyncMlParseException(check);

        WbxmlError zeroBit = parser.zeroBitTagCheck();
        if (zeroBit == WbxmlError.ZEROBIT_TAG) return this;
        if (zeroBit != WbxmlError.ERR_OK) throw new SyncMlParseException(zeroBit);

        WbxmlError parseElem = parser.parseElement(WbxmlElement.LOCURI);
        if (parseElem != WbxmlError.ERR_OK) throw new SyncMlParseException(parseElem);

        String locUriStr = parser.currentInnerText;
        if (parser.currentElement() == WbxmlElement.LOCNAME) {
            parser.skipElement();
        }

        WbxmlError checkEnd = parser.parseCheckElement(WbxmlElement.END);
        if (checkEnd != WbxmlError.ERR_OK) throw new SyncMlParseException(checkEnd);

        locURI = (locUriStr != null && !locUriStr.isEmpty()) ? locUriStr : null;
        return this;
    }
}
