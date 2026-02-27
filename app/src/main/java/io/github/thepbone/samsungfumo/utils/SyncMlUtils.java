package io.github.thepbone.samsungfumo.utils;

import io.github.thepbone.samsungfumo.syncml.SyncDocument;
import io.github.thepbone.samsungfumo.syncml.commands.*;
import io.github.thepbone.samsungfumo.syncml.elements.Item;
import io.github.thepbone.samsungfumo.syncml.elements.Meta;
import io.github.thepbone.samsungfumo.syncml.elements.PcData;
import io.github.thepbone.samsungfumo.syncml.elements.Source;
import io.github.thepbone.samsungfumo.syncml.enums.AlertTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class SyncMlUtils {
    public static boolean isAuthorizationAccepted(Cmd[] cmds) {
        if (cmds != null && cmds.length > 0) {
            for (Cmd cmd : cmds) {
                if (cmd instanceof Status) {
                    Status status = (Status) cmd;
                    if ("SyncHdr".equals(status.getCmd()) && ("212".equals(status.getData()) || "200".equals(status.getData()))) {
                        return true;
                    }
                }
            }
        } else {
            Log.W("SyncMlUtils.isAuthorizationAccepted: Unknown state: No commands answered by server");
        }
        return false;
    }

    public static boolean hasCommand(Cmd[] cmds, Class<? extends Cmd> clazz) {
        if (cmds == null) return false;
        for (Cmd cmd : cmds) {
            if (clazz.isInstance(cmd)) return true;
        }
        return false;
    }

    public static boolean hasServerAborted(Cmd[] cmds) {
        if (cmds == null) return false;
        for (Cmd cmd : cmds) {
            if (cmd instanceof Alert && AlertTypes.SESSION_ABORT.equals(cmd.getData())) {
                return true;
            }
        }
        return false;
    }

    public static Cmd[] buildGetResults(SyncDocument doc, String[][] dataSource, int cmdIdStart) {
        if (doc == null || doc.getSyncHdr() == null) {
            Log.E("SyncMlUtils.buildGetResults: Null header");
            return new Cmd[0];
        }

        Cmd[] cmds = doc.getSyncBody().getCmds();
        if (cmds == null || !hasCommand(cmds, Get.class)) {
            Log.W("SyncMlUtils.buildGetResults: Cannot build GET response. No GET commands supplied.");
            return new Cmd[0];
        }

        List<Cmd> newCmds = new ArrayList<>();
        int currentNewCmdId = cmdIdStart;
        for (Cmd cmd : cmds) {
            if (!(cmd instanceof Get)) continue;
            Get get = (Get) cmd;

            if (get.getItems() == null || get.getItems().length != 1) {
                Log.W("SyncMlUtils.buildGetResults: GET request (" + get.getCmdID() + ") contained " + (get.getItems() != null ? get.getItems().length : 0) + " instead of 1 target");
                continue;
            }

            String locUri = get.getItems()[0].getTarget().getLocURI();
            if (locUri == null) {
                Log.W("SyncMlUtils.buildGetResults: GET request (" + get.getCmdID() + ") contained an item without a target");
                continue;
            }

            String valueResult = null;
            for (String[] pair : dataSource) {
                if (pair[0].equals(locUri)) {
                    valueResult = pair[1];
                    break;
                }
            }

            if (valueResult == null) {
                Log.W("SyncMlUtils.buildGetResults: Unable to find value for path '" + locUri + "' (GET request " + get.getCmdID() + ")");
                continue;
            }

            Status status = new Status();
            status.setCmdRef(get.getCmdID());
            status.setMsgRef(doc.getSyncHdr().getMsgID());
            status.setCmdID(currentNewCmdId++);
            status.setCmd("Get");
            status.setTargetRef(locUri);
            status.setData("200");

            Results result = new Results();
            result.setCmdRef(get.getCmdID());
            result.setMsgRef(doc.getSyncHdr().getMsgID());
            result.setCmdID(currentNewCmdId++);
            
            Item item = new Item();
            Source src = new Source();
            src.setLocURI(locUri);
            item.setSource(src);
            
            Meta meta = new Meta();
            meta.setFormat("chr");
            meta.setType("text/plain");
            meta.setSize(valueResult.length());
            item.setMeta(meta);
            
            PcData data = new PcData();
            data.setData(valueResult);
            item.setData(data);
            
            result.setItems(new Item[]{item});

            newCmds.add(status);
            newCmds.add(result);
        }

        return newCmds.toArray(new Cmd[0]);
    }

    public static Item[] buildItemList(String[][] dataSource) {
        List<Item> itemList = new ArrayList<>();
        for (String[] pair : dataSource) {
            Item item = new Item();
            Source src = new Source();
            src.setLocURI(pair[0]);
            item.setSource(src);
            PcData data = new PcData();
            data.setData(pair[1]);
            item.setData(data);
            itemList.add(item);
        }
        return itemList.toArray(new Item[0]);
    }

    public static Item findItemByTargetUri(SyncDocument doc, Class<? extends Cmd> clazz, String targetUri) {
        Cmd[] cmds = (doc != null && doc.getSyncBody() != null) ? doc.getSyncBody().getCmds() : null;
        if (cmds == null) return null;

        for (Cmd cmd : cmds) {
            if (!clazz.isInstance(cmd)) continue;
            if (cmd.getItems() == null) continue;

            for (Item item : cmd.getItems()) {
                String locUri = (item.getTarget() != null) ? item.getTarget().getLocURI() : null;
                if (targetUri.equals(locUri)) return item;
            }
        }
        return null;
    }

    public static String findFirmwareUpdateUri(SyncDocument doc) {
        Item item = findItemByTargetUri(doc, Replace.class, "./FUMO/DownloadAndUpdate/PkgURL");
        if (item == null || item.getData() == null || item.getData().getData() == null) return null;
        
        String uri = item.getData().getData().replaceAll("[\\s\\t]+", "");
        if (uri.isEmpty()) return null;
        return uri;
    }

    public static int extractSvcState(Cmd[] cmds) {
        if (cmds == null) return -1;
        for (Cmd cmd : cmds) {
            if (cmd instanceof Replace) {
                Replace replace = (Replace) cmd;
                if (replace.getItems() != null) {
                    for (Item item : replace.getItems()) {
                        if ("./FUMO/Ext/SvcState".equals(item.getTarget() != null ? item.getTarget().getLocURI() : null)) {
                            try {
                                return Integer.parseInt(item.getData().getData());
                            } catch (Exception e) {
                                return -1;
                            }
                        }
                    }
                }
            }
        }
        return -1;
    }

    public static Cmd[] buildSuccessResponses(SyncDocument doc, String[][] responseMap, int cmdIdStart) {
        if (doc == null || doc.getSyncHdr() == null) {
            Log.E("SyncMlUtils.buildSuccessResponses: Null header");
            return new Cmd[0];
        }

        Cmd[] cmds = doc.getSyncBody().getCmds();
        if (cmds == null) return new Cmd[0];

        List<Cmd> newCmds = new ArrayList<>();
        int currentNewCmdId = cmdIdStart;
        for (Cmd cmd : cmds) {
            if (cmd instanceof Status && "SyncHdr".equals(((Status) cmd).getCmd())) {
                continue;
            }

            if (cmd.getItems() == null || cmd.getItems().length != 1) {
                Log.W("SyncMlUtils.buildSuccessResponses: Request (" + cmd.getCmdID() + ") contained " + (cmd.getItems() != null ? cmd.getItems().length : 0) + " instead of 1 target");
                continue;
            }

            String locUri = cmd.getItems()[0].getTarget() != null ? cmd.getItems()[0].getTarget().getLocURI() : null;
            if (locUri == null) {
                Log.W("SyncMlUtils.buildSuccessResponses: Request (" + cmd.getCmdID() + ") contained an item without a target");
                continue;
            }

            String responseCode = "200";
            if (responseMap != null) {
                for (String[] pair : responseMap) {
                    if (pair[0].equals(cmd.getClass().getSimpleName())) {
                        responseCode = pair[1];
                        break;
                    }
                }
            }

            Status status = new Status();
            status.setCmdRef(cmd.getCmdID());
            status.setMsgRef(doc.getSyncHdr().getMsgID());
            status.setCmdID(currentNewCmdId++);
            status.setCmd(cmd.getClass().getSimpleName());
            status.setTargetRef(locUri);
            status.setData(responseCode);

            newCmds.add(status);
        }

        return newCmds.toArray(new Cmd[0]);
    }
}
