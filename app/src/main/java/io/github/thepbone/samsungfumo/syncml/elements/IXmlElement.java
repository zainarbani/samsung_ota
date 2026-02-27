package io.github.thepbone.samsungfumo.syncml.elements;

import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;

public interface IXmlElement {
    void write(SyncMlWriter writer);
    IXmlElement parse(SyncMlParser parser, Object param);
    
    default IXmlElement parse(SyncMlParser parser) {
        return parse(parser, null);
    }
}
