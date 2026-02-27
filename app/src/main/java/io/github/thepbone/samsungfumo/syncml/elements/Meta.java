package io.github.thepbone.samsungfumo.syncml.elements;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.enums.MetinfTag;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;
import io.github.thepbone.samsungfumo.utils.Log;

import java.io.IOException;

public class Meta implements IXmlElement {
    private String format;
    private String type;
    private Integer size;
    private String nextNonce;
    private Integer maxMsgSize;
    private Integer maxObjSize;

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getNextNonce() { return nextNonce; }
    public void setNextNonce(String nextNonce) { this.nextNonce = nextNonce; }
    public Integer getMaxMsgSize() { return maxMsgSize; }
    public void setMaxMsgSize(Integer maxMsgSize) { this.maxMsgSize = maxMsgSize; }
    public Integer getMaxObjSize() { return maxObjSize; }
    public void setMaxObjSize(Integer maxObjSize) { this.maxObjSize = maxObjSize; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.META);
        writer.switchCodepage(WbxmlCodepage.METINF);
        if (format != null) writer.writeElementString(MetinfTag.FORMAT, format);
        if (type != null) writer.writeElementString(MetinfTag.TYPE, type);
        if (size != null) writer.writeElementString(MetinfTag.SIZE, String.valueOf(size));
        if (nextNonce != null) writer.writeElementString(MetinfTag.NEXTNONCE, nextNonce);
        if (maxMsgSize != null) writer.writeElementString(MetinfTag.MAXMSGSIZE, String.valueOf(maxMsgSize));
        if (maxObjSize != null) writer.writeElementString(MetinfTag.MAXOBJSIZE, String.valueOf(maxObjSize));
        writer.switchCodepage(WbxmlCodepage.SYNCML);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object _param) {
        WbxmlError checkElementResult = parser.parseCheckElement(WbxmlElement.META);
        if (checkElementResult != WbxmlError.ERR_OK) throw new SyncMlParseException(checkElementResult);

        WbxmlError zeroBitTagCheck = parser.zeroBitTagCheck();
        if (zeroBitTagCheck == WbxmlError.ZEROBIT_TAG) return this;
        if (zeroBitTagCheck != WbxmlError.ERR_OK) throw new SyncMlParseException(zeroBitTagCheck);

        WbxmlElement element = parser.currentElement();
        if (element == WbxmlElement.END) {
            parser.parseReadElement();
            return this;
        }

        WbxmlError checkCodepage = parser.parseCheckElement(WbxmlElement.CODEPAGE);
        if (checkCodepage != WbxmlError.ERR_OK) throw new SyncMlParseException(checkCodepage);

        WbxmlError error = parser.parseCheckElement(WbxmlElement.END);
        if (error != WbxmlError.ERR_OK) throw new SyncMlParseException(error);

        parser.currentCodePage = WbxmlCodepage.METINF;
        do {
            element = parser.currentElement();
            switch (element) {
                case END:
                    parser.parseReadElement();
                    parser.meta = this;
                    return this;
                case CODEPAGE:
                    parser.parseReadElement();
                    try {
                        parser.currentCodePage = WbxmlCodepage.fromValue(parser.readByte());
                    } catch (IOException e) {
                        error = WbxmlError.FAIL;
                    }
                    break;
                default:
                    MetinfTag tag = MetinfTag.fromValue(element.getValue());
                    if (tag != null) {
                        switch (tag) {
                            case NEXTNONCE:
                                error = parser.parseElement(element);
                                nextNonce = parser.currentInnerText;
                                break;
                            case MAXMSGSIZE:
                                error = parser.parseElement(element);
                                maxMsgSize = Integer.parseInt(parser.currentInnerText);
                                break;
                            case SIZE:
                                error = parser.parseElement(element);
                                size = Integer.parseInt(parser.currentInnerText);
                                break;
                            case TYPE:
                                error = parser.parseElement(element);
                                type = parser.currentInnerText;
                                break;
                            case MAXOBJSIZE:
                                error = parser.parseElement(element);
                                maxObjSize = Integer.parseInt(parser.currentInnerText);
                                break;
                            case FORMAT:
                                error = parser.parseElement(element);
                                format = parser.currentInnerText;
                                break;
                            default:
                                Log.E("Parser: MetinfTag." + tag + " not implemented");
                                error = parser.parseElement(element);
                                break;
                        }
                    } else {
                        error = WbxmlError.UNKNOWN_ELEMENT;
                    }
                    break;
            }
        } while (error == WbxmlError.ERR_OK);

        throw new SyncMlParseException(error);
    }
}
