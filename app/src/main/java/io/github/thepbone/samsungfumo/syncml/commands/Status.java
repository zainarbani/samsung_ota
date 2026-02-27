package io.github.thepbone.samsungfumo.syncml.commands;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.elements.Chal;
import io.github.thepbone.samsungfumo.syncml.elements.Cred;
import io.github.thepbone.samsungfumo.syncml.elements.IXmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;

import java.io.IOException;
import java.util.ArrayList;

public class Status extends Cmd {
    private Integer msgRef;
    private Integer cmdRef;
    private String cmd;
    private String targetRef;
    private String sourceRef;
    private Chal chal;
    private Cred cred;

    public Integer getMsgRef() { return msgRef; }
    public void setMsgRef(Integer msgRef) { this.msgRef = msgRef; }
    public Integer getCmdRef() { return cmdRef; }
    public void setCmdRef(Integer cmdRef) { this.cmdRef = cmdRef; }
    public String getCmd() { return cmd; }
    public void setCmd(String cmd) { this.cmd = cmd; }
    public String getTargetRef() { return targetRef; }
    public void setTargetRef(String targetRef) { this.targetRef = targetRef; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public Chal getChal() { return chal; }
    public void setChal(Chal chal) { this.chal = chal; }
    public Cred getCred() { return cred; }
    public void setCred(Cred cred) { this.cred = cred; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.STATUS);
        super.write(writer);
        if (msgRef != null) writer.writeElementString(WbxmlElement.MSGREF, String.valueOf(msgRef));
        if (cmdRef != null) writer.writeElementString(WbxmlElement.CMDREF, String.valueOf(cmdRef));
        if (cmd != null) writer.writeElementString(WbxmlElement.CMD, cmd);
        if (targetRef != null) writer.writeElementString(WbxmlElement.TARGETREF, targetRef);
        if (sourceRef != null) writer.writeElementString(WbxmlElement.SOURCEREF, sourceRef);
        if (chal != null) chal.write(writer);
        if (cred != null) cred.write(writer);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object _param) {
        WbxmlError check = parser.parseCheckElement(WbxmlElement.STATUS);
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
                case STATUS:
                    parser.parseReadElement();
                    break;
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
                case MSGREF:
                    error = parser.parseElement(element);
                    msgRef = Integer.parseInt(parser.currentInnerText);
                    break;
                case SOURCEREF:
                    sourceRef = parser.parseElelist(element, new ArrayList<>()).get(0);
                    break;
                case TARGETREF:
                    targetRef = parser.parseElelist(element, new ArrayList<>()).get(0);
                    break;
                case CRED:
                    cred = (Cred) new Cred().parse(parser);
                    break;
                case DATA:
                    error = parser.parseElement(element);
                    setData(parser.currentInnerText);
                    break;
                case CHAL:
                    chal = (Chal) new Chal().parse(parser);
                    break;
                case CMD:
                    error = parser.parseElement(element);
                    cmd = parser.currentInnerText;
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
