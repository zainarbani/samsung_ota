package com.sample.samsung.ota;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public final class FotaXmlUtils {
    private FotaXmlUtils() {
    }

    public static String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static String readTag(String xml, String tagName) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            if (document.getElementsByTagName(tagName).getLength() == 0) {
                return null;
            }
            return document.getElementsByTagName(tagName).item(0).getTextContent();
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> parseUpgradeValues(String xml) {
        List<String> versions = new ArrayList<>();
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList values = document.getElementsByTagName("value");
            for (int i = 0; i < values.getLength(); i++) {
                String value = values.item(i).getTextContent();
                if (value != null && !value.isBlank()) {
                    versions.add(value.trim());
                }
            }
        } catch (Exception ignored) {
        }
        return versions;
    }
}
