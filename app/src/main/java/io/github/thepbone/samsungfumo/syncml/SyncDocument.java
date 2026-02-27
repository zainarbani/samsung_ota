package io.github.thepbone.samsungfumo.syncml;

import io.github.thepbone.samsungfumo.syncml.elements.SyncBody;
import io.github.thepbone.samsungfumo.syncml.elements.SyncHdr;

public class SyncDocument {
    private SyncHdr syncHdr;
    private SyncBody syncBody;

    public SyncDocument() {}

    public SyncDocument(SyncHdr syncHdr, SyncBody syncBody) {
        this.syncHdr = syncHdr;
        this.syncBody = syncBody;
    }

    public SyncHdr getSyncHdr() { return syncHdr; }
    public void setSyncHdr(SyncHdr syncHdr) { this.syncHdr = syncHdr; }

    public SyncBody getSyncBody() { return syncBody; }
    public void setSyncBody(SyncBody syncBody) { this.syncBody = syncBody; }
}
