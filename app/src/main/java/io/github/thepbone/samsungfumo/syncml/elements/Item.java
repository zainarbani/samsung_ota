package io.github.thepbone.samsungfumo.syncml.elements;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;
import io.github.thepbone.samsungfumo.utils.Log;

import java.io.IOException;

public class Item implements IXmlElement {
    private Source source;
    private Target target;
    private PcData data;
    private Meta meta;
    private Integer moreData;

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    public Target getTarget() { return target; }
    public void setTarget(Target target) { this.target = target; }
    public PcData getData() { return data; }
    public void setData(PcData data) { this.data = data; }
    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }
    public Integer getMoreData() { return moreData; }
    public void setMoreData(Integer moreData) { this.moreData = moreData; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.ITEM);
        if (source != null) source.write(writer);
        if (target != null) target.write(writer);
        if (meta != null) meta.write(writer);
        if (data != null) data.write(writer);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object _param) {
        WbxmlError check = parser.parseCheckElement(WbxmlElement.ITEM);
        if (check != WbxmlError.ERR_OK) throw new SyncMlParseException(check);

        WbxmlError zeroBit = parser.zeroBitTagCheck();
        if (zeroBit == WbxmlError.ZEROBIT_TAG) return this;
        if (zeroBit != WbxmlError.ERR_OK) throw new SyncMlParseException(zeroBit);

        WbxmlError error = WbxmlError.ERR_OK;
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
                    data = (PcData) new PcData().parse(parser, element);
                    break;
                case META:
                    meta = (Meta) new Meta().parse(parser);
                    break;
                case SOURCE:
                    source = (Source) new Source().parse(parser);
                    break;
                case TARGET:
                    target = (Target) new Target().parse(parser);
                    break;
                case MOREDATA:
                    moreData = parser.parseBlankElement(element).getValue();
                    break;
                default:
                    error = WbxmlError.UNKNOWN_ELEMENT;
                    break;
            }
        } while (error == WbxmlError.ERR_OK);

        throw new SyncMlParseException(error);
    }
}
