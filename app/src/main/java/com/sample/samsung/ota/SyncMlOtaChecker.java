package com.sample.samsung.ota;

import io.github.thepbone.samsungfumo.syncml.SyncDocument;
import io.github.thepbone.samsungfumo.syncml.SyncMlParser;
import io.github.thepbone.samsungfumo.syncml.SyncMlWriter;
import io.github.thepbone.samsungfumo.syncml.commands.Alert;
import io.github.thepbone.samsungfumo.syncml.commands.Cmd;
import io.github.thepbone.samsungfumo.syncml.commands.Get;
import io.github.thepbone.samsungfumo.syncml.commands.Replace;
import io.github.thepbone.samsungfumo.syncml.commands.Status;
import io.github.thepbone.samsungfumo.syncml.elements.Cred;
import io.github.thepbone.samsungfumo.syncml.elements.Item;
import io.github.thepbone.samsungfumo.syncml.elements.Meta;
import io.github.thepbone.samsungfumo.syncml.elements.PcData;
import io.github.thepbone.samsungfumo.syncml.elements.Source;
import io.github.thepbone.samsungfumo.syncml.elements.SyncBody;
import io.github.thepbone.samsungfumo.syncml.elements.SyncHdr;
import io.github.thepbone.samsungfumo.syncml.elements.Target;
import io.github.thepbone.samsungfumo.syncml.enums.AlertTypes;
import io.github.thepbone.samsungfumo.utils.ArrayUtils;
import io.github.thepbone.samsungfumo.utils.Base64Utils;
import io.github.thepbone.samsungfumo.utils.SyncMlUtils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class SyncMlOtaChecker {
    private static final String SERVER_URL = "https://dms.ospserver.net/v1/device/magicsync/mdm";
    private static final String SERVER_ID = "x6g1q14r75";
    private static final String USER_AGENT_SUFFIX = " SyncML_DM Client";

    private final DeviceConfigProvider.DeviceConfig config;
    private final String clientName;
    private final String clientPassword;
    private String serverUrl = SERVER_URL;
    private SyncDocument lastResponse;
    private byte[] serverNonce = new byte[0];
    private int currentMessageId = 1;
    private final String sessionId;

    public SyncMlOtaChecker(android.content.Context context) {
        this(DeviceConfigProvider.read(context.getApplicationContext()));
    }

    public SyncMlOtaChecker(DeviceConfigProvider.DeviceConfig config) {
        this.config = config;
        this.clientName = config.deviceUniqueId;
        this.clientPassword = DmAuthHelper.generateClientPassword(this.clientName, SERVER_ID);
        LocalDateTime now = LocalDateTime.now();
        this.sessionId = Integer.toHexString(now.getMinute()) + Integer.toHexString(now.getSecond());
    }

    public SyncMlResult checkForUpdate() {
        try {
            SyncBody initialBody = buildInitialBody();
            SyncDocument initialResponse = sendSyncMl(initialBody);

            int attempts = 1;
            while (attempts < 5 && !SyncMlUtils.isAuthorizationAccepted(initialResponse.getSyncBody().getCmds())) {
                attempts++;
                initialResponse = sendSyncMl(initialBody);
            }
            if (!SyncMlUtils.isAuthorizationAccepted(initialResponse.getSyncBody().getCmds())) {
                return SyncMlResult.error("Authentication failed");
            }
            if (!SyncMlUtils.hasCommand(initialResponse.getSyncBody().getCmds(), Get.class)) {
                return SyncMlResult.error("Server did not request additional device info");
            }

            String[][] devInf = buildDevInfNodes();
            String[][] devDetail = buildDevDetailNodes();
            String[][] allNodes = ArrayUtils.concatArray(new String[][][]{devInf, devDetail});
            Cmd[] getResults = SyncMlUtils.buildGetResults(initialResponse, allNodes, 2);
            Cmd[] full = ArrayUtils.concatArray(new Cmd[][]{
                    {buildAuthenticationStatus(1)},
                    getResults
            });

            SyncBody followBody = new SyncBody();
            followBody.setCmds(full);
            SyncDocument updateResponse = sendSyncMl(followBody);

            int svcState = SyncMlUtils.extractSvcState(updateResponse.getSyncBody().getCmds());
            if (svcState == 260) {
                return SyncMlResult.error("No update (SvcState=260)");
            }
            if (svcState == 220) {
                return SyncMlResult.error("No suitable firmware (SvcState=220)");
            }

            String descriptorUrl = SyncMlUtils.findFirmwareUpdateUri(updateResponse);
            if (descriptorUrl == null || descriptorUrl.isBlank()) {
                return SyncMlResult.error("No descriptor URL in SyncML response");
            }

            FirmwareDescriptor descriptor = downloadDescriptor(descriptorUrl);
            if (descriptor == null) {
                return SyncMlResult.error("Descriptor download/parse failed");
            }
            return SyncMlResult.success(svcState, descriptorUrl, descriptor);
        } catch (Exception e) {
            return SyncMlResult.error("SyncML check failed: " + e.getMessage());
        }
    }

    private SyncDocument sendSyncMl(SyncBody body) throws Exception {
        SyncHdr header = buildHeader();
        SyncMlWriter writer = new SyncMlWriter();
        writer.beginDocument();
        writer.writeSyncHdr(header);
        writer.writeSyncBody(body);
        writer.endDocument();

        byte[] response = postWbxml(serverUrl, writer.getBytes());
        SyncDocument document = new SyncMlParser(response).parse();
        processServerResponse(document);
        this.lastResponse = document;
        return document;
    }

    private SyncHdr buildHeader() {
        Cred cred = null;
        if (lastResponse == null || !SyncMlUtils.isAuthorizationAccepted(lastResponse.getSyncBody().getCmds())) {
            byte[] nonce = DmAuthHelper.nextNonce(currentMessageId, serverNonce);
            String digest = DmAuthHelper.makeMd5Digest(clientName, clientPassword, nonce);
            cred = new Cred();
            Meta meta = new Meta();
            meta.setFormat("b64");
            meta.setType("syncml:auth-md5");
            cred.setMeta(meta);
            cred.setData(digest);
        }

        SyncHdr hdr = new SyncHdr();
        hdr.setSessionID(sessionId);
        hdr.setMsgID(currentMessageId);

        Target target = new Target();
        target.setLocURI(serverUrl);
        hdr.setTarget(target);

        Source src = new Source();
        src.setLocURI(clientName);
        src.setLocName(clientName);
        hdr.setSource(src);
        hdr.setCred(cred);

        Meta meta = new Meta();
        meta.setMaxMsgSize(5120);
        meta.setMaxObjSize(1048576);
        hdr.setMeta(meta);
        return hdr;
    }

    private void processServerResponse(SyncDocument document) {
        Cmd[] cmds = document.getSyncBody() != null ? document.getSyncBody().getCmds() : null;
        if (cmds != null) {
            for (Cmd cmd : cmds) {
                if (cmd instanceof Status) {
                    Status status = (Status) cmd;
                    if (status.getChal() != null
                            && status.getChal().getMeta() != null
                            && "syncml:auth-md5".equals(status.getChal().getMeta().getType())
                            && "b64".equals(status.getChal().getMeta().getFormat())
                            && status.getChal().getMeta().getNextNonce() != null) {
                        this.serverNonce = Base64Utils.decode(status.getChal().getMeta().getNextNonce());
                    }
                }
            }
        }

        if (document.getSyncHdr() != null && document.getSyncHdr().getRespURI() != null) {
            this.serverUrl = document.getSyncHdr().getRespURI();
        }
        currentMessageId++;
    }

    private byte[] postWbxml(String url, byte[] payload) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(25000);
        conn.setDoOutput(true);
        conn.setRequestProperty("User-Agent", "Samsung " + config.modelName + USER_AGENT_SUFFIX);
        conn.setRequestProperty("Accept", "application/vnd.syncml.dm+wbxml");
        conn.setRequestProperty("Content-Type", "application/vnd.syncml.dm+wbxml");
        conn.getOutputStream().write(payload);

        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            throw new IllegalStateException("SyncML server returned empty body: " + code);
        }
        byte[] data = readBytes(stream);
        conn.disconnect();
        if (code >= 400) {
            throw new IllegalStateException("SyncML HTTP " + code);
        }
        return data;
    }

    private Cmd buildAuthenticationStatus(int cmdId) {
        Status status = new Status();
        status.setCmdID(cmdId);
        status.setMsgRef(currentMessageId - 1);
        status.setCmdRef(0);
        status.setCmd("SyncHdr");
        status.setTargetRef(clientName);
        String srcUrl = serverUrl.contains("?")
                ? serverUrl.substring(0, serverUrl.lastIndexOf('?'))
                : serverUrl;
        status.setSourceRef(srcUrl);
        status.setData("212");
        return status;
    }

    private SyncBody buildInitialBody() {
        SyncBody body = new SyncBody();
        List<Cmd> cmds = new ArrayList<>();

        Alert clientInit = new Alert();
        clientInit.setCmdID(1);
        clientInit.setData(AlertTypes.CLIENT_INITIATED_MGMT);
        cmds.add(clientInit);

        Replace devInfo = new Replace();
        devInfo.setCmdID(2);
        devInfo.setItems(SyncMlUtils.buildItemList(buildDevInfNodes()));
        cmds.add(devInfo);

        Alert fumo = new Alert();
        fumo.setCmdID(3);
        fumo.setData(AlertTypes.GENERIC);
        Item item = new Item();
        Source source = new Source();
        source.setLocURI("./FUMO/DownloadAndUpdate");
        item.setSource(source);
        Meta meta = new Meta();
        meta.setFormat("chr");
        meta.setType("org.openmobilealliance.dm.firmwareupdate.devicerequest");
        item.setMeta(meta);
        PcData data = new PcData();
        data.setData("0");
        item.setData(data);
        fumo.setItems(new Item[]{item});
        cmds.add(fumo);

        body.setCmds(cmds.toArray(new Cmd[0]));
        return body;
    }

    private String[][] buildDevInfNodes() {
        return new String[][]{
                {"./DevInfo/Lang", java.util.Locale.getDefault().toLanguageTag()},
                {"./DevInfo/DmV", "1.2"},
                {"./DevInfo/Mod", config.modelName},
                {"./DevInfo/Man", "Samsung"},
                {"./DevInfo/DevId", config.deviceUniqueId},
                {"./DevInfo/Ext/TelephonyMcc", config.netMcc},
                {"./DevInfo/Ext/TelephonyMnc", config.netMnc},
                {"./DevInfo/Ext/SIMCardMcc", config.simMcc},
                {"./DevInfo/Ext/SIMCardMnc", config.simMnc},
                {"./DevInfo/Ext/FotaClientVer", config.fotaClientVersion},
                {"./DevInfo/Ext/DMClientVer", config.fotaClientVersion},
                {"./DevInfo/Ext/OmcCode", config.customerCode},
                {"./DevInfo/Ext/DevNetworkConnType", "WIFI"},
        };
    }

    private String[][] buildDevDetailNodes() {
        return new String[][]{
                {"./DevDetail/FwV", config.firmwareVersion}
        };
    }

    private FirmwareDescriptor downloadDescriptor(String descriptorUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(descriptorUrl).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "application/vnd.oma.dd+xml");
            conn.setRequestProperty("User-Agent", "Samsung " + config.modelName + USER_AGENT_SUFFIX);

            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null || code >= 400) {
                return null;
            }

            String xml = new String(readBytes(stream), StandardCharsets.UTF_8);
            conn.disconnect();
            return parseDescriptor(xml);
        } catch (Exception e) {
            return null;
        }
    }

    private static FirmwareDescriptor parseDescriptor(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            Node mediaNode = null;
            NodeList children = doc.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if ("media".equals(children.item(i).getNodeName())) {
                    mediaNode = children.item(i);
                    break;
                }
            }
            if (mediaNode == null) {
                return null;
            }

            String description = "";
            String objectUri = "";
            String installParam = "";
            int size = 0;
            NodeList mediaChildren = mediaNode.getChildNodes();
            for (int i = 0; i < mediaChildren.getLength(); i++) {
                Node child = mediaChildren.item(i);
                String name = child.getNodeName();
                if ("description".equals(name)) {
                    description = child.getTextContent();
                } else if ("objectURI".equals(name)) {
                    objectUri = child.getTextContent();
                } else if ("installParam".equals(name)) {
                    installParam = child.getTextContent();
                } else if ("size".equals(name)) {
                    try {
                        size = Integer.parseInt(child.getTextContent());
                    } catch (Exception ignored) {
                    }
                }
            }

            String md5 = null;
            String fw = null;
            for (String param : installParam.split(";")) {
                String[] pair = param.split("=");
                if (pair.length != 2) {
                    continue;
                }
                if ("MD5".equals(pair[0])) {
                    md5 = pair[1];
                } else if ("updateFwV".equals(pair[0])) {
                    fw = pair[1];
                }
            }

            return new FirmwareDescriptor(objectUri, description, size, md5, fw);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readBytes(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = input.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    public static class FirmwareDescriptor {
        public final String url;
        public final String description;
        public final int size;
        public final String md5;
        public final String updateFwV;

        public FirmwareDescriptor(String url, String description, int size, String md5, String updateFwV) {
            this.url = url == null ? "" : url;
            this.description = description == null ? "" : description;
            this.size = size;
            this.md5 = md5;
            this.updateFwV = updateFwV;
        }
    }

    public static class SyncMlResult {
        public final boolean ok;
        public final String message;
        public final int svcState;
        public final String descriptorUrl;
        public final FirmwareDescriptor descriptor;

        private SyncMlResult(boolean ok, String message, int svcState, String descriptorUrl, FirmwareDescriptor descriptor) {
            this.ok = ok;
            this.message = message;
            this.svcState = svcState;
            this.descriptorUrl = descriptorUrl;
            this.descriptor = descriptor;
        }

        static SyncMlResult success(int svcState, String descriptorUrl, FirmwareDescriptor descriptor) {
            return new SyncMlResult(true, "ok", svcState, descriptorUrl, descriptor);
        }

        static SyncMlResult error(String message) {
            return new SyncMlResult(false, message, -1, "", null);
        }
    }
}
