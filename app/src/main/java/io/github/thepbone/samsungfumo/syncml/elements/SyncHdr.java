package io.github.thepbone.samsungfumo.syncml.elements;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;
import io.github.thepbone.samsungfumo.utils.Log;

import java.io.IOException;

public class SyncHdr implements IXmlElement {
    private String verDTD = "1.2";
    private String verProto = "DM/1.2";
    private String sessionID;
    private Integer msgID;
    private Target target;
    private Source source;
    private String respURI;
    private Integer isNoResp;
    private Cred cred;
    private Meta meta;

    public String getVerDTD() { return verDTD; }
    public void setVerDTD(String verDTD) { this.verDTD = verDTD; }
    public String getVerProto() { return verProto; }
    public void setVerProto(String verProto) { this.verProto = verProto; }
    public String getSessionID() { return sessionID; }
    public void setSessionID(String sessionID) { this.sessionID = sessionID; }
    public Integer getMsgID() { return msgID; }
    public void setMsgID(Integer msgID) { this.msgID = msgID; }
    public Target getTarget() { return target; }
    public void setTarget(Target target) { this.target = target; }
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    public String getRespURI() { return respURI; }
    public void setRespURI(String respURI) { this.respURI = respURI; }
    public Integer getIsNoResp() { return isNoResp; }
    public void setIsNoResp(Integer isNoResp) { this.isNoResp = isNoResp; }
    public Cred getCred() { return cred; }
    public void setCred(Cred cred) { this.cred = cred; }
    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.SYNCHDR);
        writer.writeElementString(WbxmlElement.VERDTD, verDTD);
        writer.writeElementString(WbxmlElement.VERPROTO, verProto);
        writer.writeElementString(WbxmlElement.SESSIONID, sessionID);
        writer.writeElementString(WbxmlElement.MSGID, String.valueOf(msgID));
        if (respURI != null) writer.writeElementString(WbxmlElement.RESPURI, respURI);
        if (isNoResp != null && isNoResp > 0) writer.writeSelfClosingElement(WbxmlElement.NORESP);
        if (target != null) target.write(writer);
        if (source != null) source.write(writer);
        if (cred != null) cred.write(writer);
        if (meta != null) meta.write(writer);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object _param) {
        WbxmlError check = parser.parseCheckElement(WbxmlElement.SYNCHDR);
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
                case CRED:
                    cred = (Cred) new Cred().parse(parser);
                    break;
                case NORESP:
                    isNoResp = parser.parseBlankElement(element).getValue();
                    break;
                case RESPURI:
                    error = parser.parseElement(element);
                    respURI = parser.currentInnerText;
                    break;
                case SESSIONID:
                    error = parser.parseElement(element);
                    sessionID = parser.currentInnerText;
                    break;
                case SOURCE:
                    source = (Source) new Source().parse(parser);
                    break;
                case TARGET:
                    target = (Target) new Target().parse(parser);
                    break;
                case META:
                    meta = (Meta) new Meta().parse(parser);
                    break;
                case MSGID:
                    error = parser.parseElement(element);
                    msgID = Integer.parseInt(parser.currentInnerText);
                    break;
                case VERDTD:
                    error = parser.parseElement(element);
                    verDTD = parser.currentInnerText;
                    break;
                case VERPROTO:
                    error = parser.parseElement(element);
                    verProto = parser.currentInnerText;
                    break;
                default:
                    error = WbxmlError.UNKNOWN_ELEMENT;
                    break;
            }
        } while (error == WbxmlError.ERR_OK);

        throw new SyncMlParseException(error);
    }
}
