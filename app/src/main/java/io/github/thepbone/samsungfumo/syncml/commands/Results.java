package io.github.thepbone.samsungfumo.syncml.commands;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.elements.IXmlElement;
import io.github.thepbone.samsungfumo.syncml.elements.Meta;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;

import java.io.IOException;

public class Results extends Cmd {
    private Integer msgRef;
    private Integer cmdRef;
    private String sourceRef;
    private String targetRef;
    private Meta meta;

    public Integer getMsgRef() { return msgRef; }
    public void setMsgRef(Integer msgRef) { this.msgRef = msgRef; }
    public Integer getCmdRef() { return cmdRef; }
    public void setCmdRef(Integer cmdRef) { this.cmdRef = cmdRef; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getTargetRef() { return targetRef; }
    public void setTargetRef(String targetRef) { this.targetRef = targetRef; }
    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.RESULTS);
        super.write(writer);
        if (msgRef != null) writer.writeElementString(WbxmlElement.MSGREF, String.valueOf(msgRef));
        if (cmdRef != null) writer.writeElementString(WbxmlElement.CMDREF, String.valueOf(cmdRef));
        if (sourceRef != null) writer.writeElementString(WbxmlElement.SOURCEREF, sourceRef);
        if (targetRef != null) writer.writeElementString(WbxmlElement.TARGETREF, targetRef);
        if (meta != null) meta.write(writer);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object _param) {
        WbxmlError check = parser.parseCheckElement(WbxmlElement.RESULTS);
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
                case ITEM:
                    setItems(parser.parseItemlist(getItems()));
                    break;
                case META:
                    meta = (Meta) new Meta().parse(parser);
                    break;
                case MSGREF:
                    error = parser.parseElement(element);
                    msgRef = Integer.parseInt(parser.currentInnerText);
                    break;
                case SOURCEREF:
                    error = parser.parseElement(element);
                    sourceRef = parser.currentInnerText;
                    break;
                case TARGETREF:
                    error = parser.parseElement(element);
                    targetRef = parser.currentInnerText;
                    break;
                case CMDID:
                    error = parser.parseElement(element);
                    setCmdID(Integer.parseInt(parser.currentInnerText));
                    break;
                case CMDREF:
                    error = parser.parseElement(element);
                    cmdRef = Integer.parseInt(parser.currentInnerText);
                    break;
                default:
                    error = WbxmlError.UNKNOWN_ELEMENT;
                    break;
            }
        } while (error == WbxmlError.ERR_OK);

        throw new SyncMlParseException(error);
    }
}
