package io.github.thepbone.samsungfumo.syncml.elements;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;
import io.github.thepbone.samsungfumo.utils.Log;

public class Source implements IXmlElement {
    private String locURI;
    private String locName;

    public String getLocURI() { return locURI; }
    public void setLocURI(String locURI) { this.locURI = locURI; }
    public String getLocName() { return locName; }
    public void setLocName(String locName) { this.locName = locName; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.SOURCE);
        writer.writeElementString(WbxmlElement.LOCURI, locURI);
        if (locName != null) writer.writeElementString(WbxmlElement.LOCNAME, locName);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object param) {
        WbxmlError check = parser.parseCheckElement(WbxmlElement.SOURCE);
        if (check != WbxmlError.ERR_OK) throw new SyncMlParseException(check);

        WbxmlError zeroBit = parser.zeroBitTagCheck();
        if (zeroBit == WbxmlError.ZEROBIT_TAG) return this;
        if (zeroBit != WbxmlError.ERR_OK) throw new SyncMlParseException(zeroBit);

        WbxmlError parseElem = parser.parseElement(WbxmlElement.LOCURI);
        if (parseElem != WbxmlError.ERR_OK) throw new SyncMlParseException(parseElem);

        String str = parser.currentInnerText;
        if (parser.currentElement() == WbxmlElement.LOCNAME) {
            parser.skipElement();
        }

        WbxmlError checkEnd = parser.parseCheckElement(WbxmlElement.END);
        if (checkEnd != WbxmlError.ERR_OK) throw new SyncMlParseException(checkEnd);

        locURI = (str != null && !str.isEmpty()) ? str : null;
        return this;
    }
}
