package io.github.thepbone.samsungfumo.exceptions;

import io.github.thepbone.samsungfumo.syncml.enums.WbxmlError;

public class SyncMlParseException extends RuntimeException {
    private final WbxmlError wbXmlError;

    public SyncMlParseException(WbxmlError error) {
        super("SyncML parse error: " + error);
        this.wbXmlError = error;
    }

    public WbxmlError getWbXmlError() {
        return wbXmlError;
    }
}
