package io.github.thepbone.samsungfumo.syncml;

import io.github.thepbone.samsungfumo.syncml.elements.SyncBody;
import io.github.thepbone.samsungfumo.syncml.elements.SyncHdr;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlCodepage;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;

import java.io.IOException;

public class SyncMlParser extends WbxmlDecoder {
    public SyncMlParser(byte[] wbxml) {
        super(wbxml);
    }

    public SyncDocument parse() {
        SyncDocument document = new SyncDocument();

        index = 0;
        parseStartdoc(this);
        WbxmlElement element = currentElement();

        if (element != WbxmlElement.SYNCML) {
            throw new RuntimeException("SyncMlParseException: " + WbxmlError.UNKNOWN_ELEMENT);
        }

        WbxmlError checkSyncMlElement = parseCheckElement(WbxmlElement.SYNCML);
        if (checkSyncMlElement != WbxmlError.ERR_OK) {
            throw new RuntimeException("SyncMlParseException: " + checkSyncMlElement);
        }

        WbxmlError error = zeroBitTagCheck();
        if (error == WbxmlError.ZEROBIT_TAG) {
            return document;
        }

        if (error != WbxmlError.ERR_OK) {
            throw new RuntimeException("SyncMlParseException: " + error);
        }

        do {
            element = currentElement();

            switch (element) {
                case END:
                    return document;
                case CODEPAGE:
                    parseReadElement();
                    try {
                        currentCodePage = WbxmlCodepage.fromValue(readByte());
                    } catch (IOException e) {
                        error = WbxmlError.FAIL;
                    }
                    break;
                case SYNCBODY:
                    try {
                        document.setSyncBody((SyncBody) new SyncBody().parse(this));
                    } catch (Exception ex) {
                        error = WbxmlError.FAIL;
                    }
                    break;
                case SYNCHDR:
                    try {
                        document.setSyncHdr((SyncHdr) new SyncHdr().parse(this));
                    } catch (Exception ex) {
                        error = WbxmlError.FAIL;
                    }
                    break;
                default:
                    error = WbxmlError.UNKNOWN_ELEMENT;
                    break;
            }
        } while (error == WbxmlError.ERR_OK);

        throw new RuntimeException("SyncMlParseException: " + error);
    }
}
