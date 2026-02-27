package io.github.thepbone.samsungfumo.syncml;

import io.github.thepbone.samsungfumo.syncml.elements.SyncBody;
import io.github.thepbone.samsungfumo.syncml.elements.SyncHdr;
import io.github.thepbone.samsungfumo.syncml.enums.MetinfTag;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCharset;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;

public class SyncMlWriter extends WbxmlEncoder {
    public byte[] getBytes() {
        return memoryStream.toByteArray();
    }

    public WbxmlError beginDocument() {
        if (!startDocument(WbxmlCharset.UTF8, "-//SYNCML//DTD SyncML 1.2//EN", 0x1D)) {
            return WbxmlError.BUFFER_TOO_SMALL;
        }

        writeStartElement(WbxmlElement.SYNCML);
        return WbxmlError.ERR_OK;
    }

    public WbxmlError endDocument() {
        if (endElement()) {
            return WbxmlError.ERR_OK;
        }

        return WbxmlError.BUFFER_TOO_SMALL;
    }

    public void writeSyncHdr(SyncHdr syncHdr) {
        syncHdr.write(this);
    }

    public void writeSyncBody(SyncBody syncBody) {
        syncBody.write(this);
    }

    public WbxmlError switchCodepage(WbxmlCodepage page) {
        return addSwitchpage(page.getValue()) ? WbxmlError.ERR_OK : WbxmlError.BUFFER_TOO_SMALL;
    }

    public WbxmlError writeOpaqueString(String str) {
        if (!addOpaque(str.toCharArray(), str.length())) {
            return WbxmlError.BUFFER_TOO_SMALL;
        }

        return WbxmlError.ERR_OK;
    }

    public void writeElementString(int tag, String value) {
        writeStartElement(tag);
        writeString(value);
        writeEndElement();
    }

    public void writeElementString(WbxmlElement tag, String value) {
        writeElementString(tag.getValue(), value);
    }

    public void writeElementString(MetinfTag tag, String value) {
        writeElementString(tag.getValue(), value);
    }

    public WbxmlError writeStartElement(WbxmlElement tag) {
        return writeStartElement(tag.getValue());
    }

    public WbxmlError writeStartElement(MetinfTag tag) {
        return writeStartElement(tag.getValue());
    }

    public WbxmlError writeStartElement(int tag) {
        return !startElement(tag, true) ? WbxmlError.BUFFER_TOO_SMALL : WbxmlError.ERR_OK;
    }

    public WbxmlError writeString(String str) {
        return !addContent(str) ? WbxmlError.BUFFER_TOO_SMALL : WbxmlError.ERR_OK;
    }

    public WbxmlError writeEndElement() {
        return !endElement() ? WbxmlError.BUFFER_TOO_SMALL : WbxmlError.ERR_OK;
    }

    public WbxmlError writeSelfClosingElement(WbxmlElement tag) {
        return !startElement(tag.getValue(), false) ? WbxmlError.BUFFER_TOO_SMALL : WbxmlError.ERR_OK;
    }

    public WbxmlError writeSelfClosingElement(MetinfTag tag) {
        return !startElement(tag.getValue(), false) ? WbxmlError.BUFFER_TOO_SMALL : WbxmlError.ERR_OK;
    }
}
