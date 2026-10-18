/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft.ooxml.xwpf;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.openxml4j.opc.PackagePart;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.ooxml.OOXMLWordAndPowerPointTextHandler;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Scrapes from styles.xml each style's name, its basedOn parent and whether
 * its own run properties set bold or italics.
 */
public class XWPFStylesShim {

    /**
     * Empty singleton to be used when there is no style info
     */
    public static final XWPFStylesShim EMPTY_STYLES = new EmptyXWPFStyles();

    // bounds basedOn chains, including cyclic ones
    private static final int MAX_BASED_ON_DEPTH = 32;
    // Word itself caps style names at 253 characters
    private static final int MAX_STYLE_NAME_LENGTH = 255;

    private final Map<String, StyleInfo> styles = new HashMap<>();

    private XWPFStylesShim() {

    }

    public XWPFStylesShim(PackagePart part, ParseContext parseContext)
            throws IOException, TikaException, SAXException {

        try (InputStream is = part.getInputStream()) {
            onDocumentLoad(parseContext, is);
        }
    }

    private void onDocumentLoad(ParseContext parseContext, InputStream stream)
            throws TikaException, IOException, SAXException {
        XMLReaderUtils
                .parseSAX(stream, new StylesStripper(), parseContext);
    }

    /**
     * @param styleId
     * @return style's name or null if styleId is null or can't be found
     */
    public String getStyleName(String styleId) {
        if (styleId == null) {
            return null;
        }
        StyleInfo info = styles.get(styleId);
        return info == null ? null : info.name;
    }

    /**
     * @return whether the style, or the nearest style it is based on that says
     * either way, sets bold; false if none does
     */
    public boolean isBold(String styleId) {
        StyleInfo info = styles.get(styleId);
        for (int i = 0; info != null && i < MAX_BASED_ON_DEPTH; i++) {
            if (info.bold != null) {
                return info.bold;
            }
            info = info.basedOn == null ? null : styles.get(info.basedOn);
        }
        return false;
    }

    /**
     * @return whether the style, or the nearest style it is based on that says
     * either way, sets italics; false if none does
     */
    public boolean isItalics(String styleId) {
        StyleInfo info = styles.get(styleId);
        for (int i = 0; info != null && i < MAX_BASED_ON_DEPTH; i++) {
            if (info.italics != null) {
                return info.italics;
            }
            info = info.basedOn == null ? null : styles.get(info.basedOn);
        }
        return false;
    }

    private static class StyleInfo {
        String name;
        String basedOn;
        Boolean bold;
        Boolean italics;
    }

    private static class EmptyXWPFStyles extends XWPFStylesShim {

        @Override
        public String getStyleName(String styleId) {
            return null;
        }

        @Override
        public boolean isBold(String styleId) {
            return false;
        }

        @Override
        public boolean isItalics(String styleId) {
            return false;
        }
    }

    private class StylesStripper extends DefaultHandler {

        StyleInfo current = null;
        // depth below the current <w:style>; its own rPr is at 1, toggles at 2
        int depth = 0;
        boolean inStyleRPr = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            if (current != null) {
                depth++;
            }
            if (uri != null && !OOXMLWordAndPowerPointTextHandler.W_NS.equals(uri)) {
                return;
            }
            if ("style".equals(localName)) {
                String styleId =
                        atts.getValue(OOXMLWordAndPowerPointTextHandler.W_NS, "styleId");
                if (styleId != null) {
                    current = new StyleInfo();
                    styles.put(styleId, current);
                    depth = 0;
                }
            } else if (current == null) {
                return;
            } else if (depth == 1 && "name".equals(localName)) {
                String name = getVal(atts);
                current.name = name != null && name.length() > MAX_STYLE_NAME_LENGTH
                        ? name.substring(0, MAX_STYLE_NAME_LENGTH) : name;
            } else if (depth == 1 && "basedOn".equals(localName)) {
                current.basedOn = getVal(atts);
            } else if (depth == 1 && "rPr".equals(localName)) {
                inStyleRPr = true;
            } else if (depth == 2 && inStyleRPr && "b".equals(localName)) {
                current.bold = getOnOff(atts);
            } else if (depth == 2 && inStyleRPr && "i".equals(localName)) {
                current.italics = getOnOff(atts);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (current == null) {
                return;
            }
            if (depth == 0) {
                current = null;
                return;
            }
            if (depth == 1) {
                inStyleRPr = false;
            }
            depth--;
        }

        private String getVal(Attributes atts) {
            return atts.getValue(OOXMLWordAndPowerPointTextHandler.W_NS, "val");
        }

        private boolean getOnOff(Attributes atts) {
            String v = getVal(atts);
            return v == null || !("0".equals(v) || "false".equals(v) || "off".equals(v));
        }
    }

}
