package io.github.thepbone.samsungfumo.syncml.commands;

import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.elements.IXmlElement;
import io.github.thepbone.samsungfumo.syncml.elements.Item;
import io.github.thepbone.samsungfumo.syncml.enums.WbxmlElement;

public abstract class Cmd implements IXmlElement {
    private Integer cmdID;
    private String data;
    private Integer isNoResp;
    private Item[] items;

    public Integer getCmdID() { return cmdID; }
    public void setCmdID(Integer cmdID) { this.cmdID = cmdID; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public Integer getIsNoResp() { return isNoResp; }
    public void setIsNoResp(Integer isNoResp) { this.isNoResp = isNoResp; }
    public Item[] getItems() { return items; }
    public void setItems(Item[] items) { this.items = items; }

    @Override
    public void write(SyncMlWriter writer) {
        writer.writeElementString(WbxmlElement.CMDID, String.valueOf(cmdID));
        if (data != null) writer.writeElementString(WbxmlElement.DATA, data);
        if (isNoResp != null && isNoResp > 0) writer.writeSelfClosingElement(WbxmlElement.NORESP);
        if (items != null) {
            for (Item item : items) item.write(writer);
        }
    }
}
