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
package org.apache.tika.parser.microsoft.ooxml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Generic SAX handler that collects raw XML content by ID from OOXML part files.
 * Works with any part that contains wrapper elements with {@code w:id} attributes
 * containing body content (paragraphs, tables, formatting, etc.).
 * <p>
 * Used for:
 * <ul>
 *   <li>footnotes.xml — wrapper element "footnote"</li>
 *   <li>endnotes.xml — wrapper element "endnote"</li>
 *   <li>comments.xml — wrapper element "comment"</li>
 * </ul>
 * <p>
 * IDs "0" and "-1" are skipped (these are separator/continuation elements in
 * footnotes/endnotes).
 */
class OOXMLPartContentCollector extends DefaultHandler {

    private static final String W_NS =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private final Set<String> wrapperElementNames;
    private final Set<String> skipIds;
    private final Map<String, byte[]> contentMap = new HashMap<>();
    private final Map<String, String> namespaceMappings = new HashMap<>();

    private String currentId = null;
    private ByteArrayOutputStream buffer = null;
    private int depth = 0;

    /**
     * @param wrapperElementNames local names of wrapper elements to collect
     *                            (e.g., "footnote", "endnote", "comment")
     */
    OOXMLPartContentCollector(Set<String> wrapperElementNames) {
        this(wrapperElementNames, Set.of("0", "-1"));
    }

    /**
     * @param wrapperElementNames local names of wrapper elements to collect
     * @param skipIds             IDs to skip (e.g., "0", "-1" for footnote
     *                            separator/continuation elements)
     */
    OOXMLPartContentCollector(Set<String> wrapperElementNames, Set<String> skipIds) {
        this.wrapperElementNames = wrapperElementNames;
        this.skipIds = skipIds;
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) {
        namespaceMappings.put(prefix, uri);
    }

    Map<String, byte[]> getContentMap() {
        return contentMap;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes atts) throws SAXException {
        if (currentId != null) {
            depth++;
            appendStartTag(localName, qName, atts);
            return;
        }

        if (wrapperElementNames.contains(localName)) {
            String id = atts.getValue(W_NS, "id");
            if (id != null && !skipIds.contains(id)) {
                currentId = id;
                buffer = new ByteArrayOutputStream();
                // Don't write wrapper open tag yet — inline xmlns declarations
                // (e.g., xmlns:a on nested elements) haven't been captured via
                // startPrefixMapping. Defer to endElement when all are known.
                depth = 0;
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (currentId == null) {
            return;
        }

        if (depth == 0) {
            // Build the wrapper now — all startPrefixMapping calls from nested
            // elements have been captured, so inline xmlns declarations are included.
            byte[] wrapperOpen = buildWrapperOpenTag().getBytes(StandardCharsets.UTF_8);
            byte[] content = buffer.toByteArray();
            ByteArrayOutputStream combined =
                    new ByteArrayOutputStream(wrapperOpen.length + content.length + 16);
            combined.write(wrapperOpen, 0, wrapperOpen.length);
            combined.write(content, 0, content.length);
            writeString(combined, "</w:body>");
            contentMap.put(currentId, combined.toByteArray());
            currentId = null;
            buffer = null;
            return;
        }

        depth--;
        if (qName != null && !qName.isEmpty()) {
            writeString("</" + qName + ">");
        } else {
            writeString("</" + localName + ">");
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (currentId != null) {
            writeString(escape(new String(ch, start, length)));
        }
    }

    private String buildWrapperOpenTag() {
        StringBuilder sb = new StringBuilder("<w:body");
        // include all namespace declarations from the source document
        for (Map.Entry<String, String> entry : namespaceMappings.entrySet()) {
            String prefix = entry.getKey();
            String nsUri = entry.getValue();
            if (prefix == null || prefix.isEmpty()) {
                sb.append(" xmlns=\"").append(escape(nsUri)).append("\"");
            } else {
                sb.append(" xmlns:").append(prefix).append("=\"")
                        .append(escape(nsUri)).append("\"");
            }
        }
        // ensure w namespace is present
        if (!namespaceMappings.containsKey("w")) {
            sb.append(" xmlns:w=\"").append(W_NS).append("\"");
        }
        sb.append(">");
        return sb.toString();
    }

    private void appendStartTag(String localName, String qName, Attributes atts) {
        String tagName = (qName != null && !qName.isEmpty()) ? qName : localName;
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(tagName);
        for (int i = 0; i < atts.getLength(); i++) {
            String attName = atts.getQName(i);
            if (attName == null || attName.isEmpty()) {
                attName = atts.getLocalName(i);
            }
            sb.append(' ').append(attName).append("=\"");
            sb.append(escape(atts.getValue(i)));
            sb.append('"');
        }
        sb.append('>');
        writeString(sb.toString());
    }

    private void writeString(String s) {
        writeString(buffer, s);
    }

    private static void writeString(ByteArrayOutputStream target, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        target.write(bytes, 0, bytes.length);
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String replacement = null;
            switch (c) {
                case '&':
                    replacement = "&amp;";
                    break;
                case '<':
                    replacement = "&lt;";
                    break;
                case '>':
                    replacement = "&gt;";
                    break;
                case '"':
                    replacement = "&quot;";
                    break;
                default:
                    if (sb != null) {
                        sb.append(c);
                    }
                    continue;
            }
            if (sb == null) {
                sb = new StringBuilder(s.length() + 16);
                sb.append(s, 0, i);
            }
            sb.append(replacement);
        }
        return sb != null ? sb.toString() : s;
    }
}
