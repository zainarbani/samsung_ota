package io.github.thepbone.samsungfumo.syncml.elements;

import io.github.thepbone.samsungfumo.exceptions.SyncMlParseException;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.commands.*;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;
import io.github.thepbone.samsungfumo.utils.ArrayUtils;
import io.github.thepbone.samsungfumo.utils.Log;

import java.io.IOException;

public class SyncBody implements IXmlElement {
    private Cmd[] cmds;
    private Meta meta;
    private boolean isFinal = true;

    public Cmd[] getCmds() { return cmds; }
    public void setCmds(Cmd[] cmds) { this.cmds = cmds; }
    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }
    public boolean isFinal() { return isFinal; }
    public void setFinal(boolean isFinal) { this.isFinal = isFinal; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeStartElement(WbxmlElement.SYNCBODY);
        if (cmds != null) {
            for (Cmd cmd : cmds) cmd.write(writer);
        }
        if (isFinal) writer.writeSelfClosingElement(WbxmlElement.FINAL);
        writer.writeEndElement();
    }

    @Override
    public IXmlElement parse(SyncMlParser parser, Object _param) {
        WbxmlError check = parser.parseCheckElement(WbxmlElement.SYNCBODY);
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
                case ALERT:
                    cmds = ArrayUtils.arrayPush(cmds != null ? cmds : new Cmd[0], (Cmd) new Alert().parse(parser));
                    break;
                case EXEC:
                    cmds = ArrayUtils.arrayPush(cmds != null ? cmds : new Cmd[0], (Cmd) new Exec().parse(parser));
                    break;
                case GET:
                    cmds = ArrayUtils.arrayPush(cmds != null ? cmds : new Cmd[0], (Cmd) new Get().parse(parser));
                    break;
                case REPLACE:
                    cmds = ArrayUtils.arrayPush(cmds != null ? cmds : new Cmd[0], (Cmd) new Replace().parse(parser));
                    break;
                case RESULTS:
                    cmds = ArrayUtils.arrayPush(cmds != null ? cmds : new Cmd[0], (Cmd) new Results().parse(parser));
                    break;
                case STATUS:
                    cmds = ArrayUtils.arrayPush(cmds != null ? cmds : new Cmd[0], (Cmd) new Status().parse(parser));
                    break;
                case FINAL:
                    parser.parseBlankElement(element);
                    break;
                default:
                    Log.E("Parser: Element " + element + " not implemented in SyncBody");
                    error = WbxmlError.UNKNOWN;
                    break;
            }
        } while (error == WbxmlError.ERR_OK);

        throw new SyncMlParseException(error);
    }
}
