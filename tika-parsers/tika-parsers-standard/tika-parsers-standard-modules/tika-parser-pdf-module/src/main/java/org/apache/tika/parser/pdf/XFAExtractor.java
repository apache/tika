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
package org.apache.tika.parser.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.TaggedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * This class offers an initial capability to
 * scrape text containing elements out of XFA, and
 * it tries to link fields with values.
 * <p>
 * Some areas for improvement:
 * <ol>
 *     <li>handle metadata stored in &lt;desc&gt; section (govdocs1: 754282.pdf, 982106.pdf)</li>
 *     <li>handle pdf metadata (access permissions, etc.) in &lt;pdf&gt; element</li>
 *     <li>extract different types of uris as metadata</li>
 *     <li>add extraction of &lt;image&gt; data (govdocs1: 754282.pdf)</li>
 *     <li>add computation of traversal order for fields</li>
 *     <li>figure out when text extracted from xfa fields is duplicative of that
 *     extracted from the rest of the pdf...and do this efficiently and quickly</li>
 *     <li>avoid duplication with &lt;speak&gt; and &lt;tooltip&gt; elements</li>
 * </ol>
 */
class XFAExtractor {

    private static final String XFA_TEMPLATE_NS_PREFIX = "http://www.xfa.org/schema/xfa-template";
    private static final String XFA_DATA_NS = "http://www.xfa.org/schema/xfa-data/1.0/";
    private static final Set<String> TEXT_ELEMENTS =
            Set.of("speak", "text", "contents-richtext", "toolTip", "exData");
    private static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

    /**
     * @throws TikaException if the XFA stream itself is not well-formed XML; the
     *                       caller may fall back to the AcroForm fields
     * @throws SAXException  thrown by {@code xhtml}, propagated unchanged
     */
    void extract(InputStream xfaIs, XHTMLContentHandler xhtml, Metadata m, ParseContext context)
            throws IOException, SAXException, TikaException {
        //the div must be closed even when the XFA is malformed, or the caller's
        //AcroForm fallback nests under it and </body> can't balance
        xhtml.startElement("div", "class", "xfa_content");
        try {
            //tagging separates exceptions raised downstream from XML parse errors
            TaggedContentHandler out = new TaggedContentHandler(xhtml);
            XFAHandler handler = new XFAHandler(out);
            try {
                XMLReaderUtils.parseSAX(xfaIs, handler, context);
            } catch (SAXException e) {
                out.throwIfCauseOf(e);
                throw new TikaException("XML error in XFA: " + e.getMessage(), e);
            }

            if (handler.fields.isEmpty()) {
                return;
            }
            xhtml.startElement("div", "class", "xfa_form");
            xhtml.startElement("ol");
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, XFAField> e : handler.fields.entrySet()) {
                String fieldName = e.getKey();
                XFAField field = e.getValue();
                String displayFieldName =
                        (field.toolTip == null || field.toolTip.isBlank()) ? fieldName :
                                field.toolTip;
                List<String> fieldValues = handler.values.getOrDefault(fieldName,
                        Collections.emptyList());
                if (fieldValues.isEmpty()) {
                    fieldValues = Collections.singletonList("");
                }
                for (String fieldValue : fieldValues) {
                    AttributesImpl attrs = new AttributesImpl();
                    attrs.addAttribute("", "fieldName", "fieldName", "CDATA", fieldName);

                    sb.append(displayFieldName).append(": ");
                    if (fieldValue != null) {
                        sb.append(fieldValue);
                    }

                    xhtml.startElement("li", attrs);
                    xhtml.characters(sb.toString());
                    xhtml.endElement("li");
                    sb.setLength(0);
                }
            }
            xhtml.endElement("ol");
            xhtml.endElement("div");
        } finally {
            xhtml.endElement("div");
        }
    }

    private static boolean isTemplateField(String uri, String localName) {
        return "field".equals(localName) && uri.startsWith(XFA_TEMPLATE_NS_PREFIX);
    }

    private static boolean isXfaData(String uri, String localName) {
        return "data".equals(localName) && XFA_DATA_NS.equals(uri);
    }

    /**
     * Streams paragraphs from text-bearing elements as they are seen and caches
     * template fields and xfa:data values for the merged dump at the end.
     */
    private static class XFAHandler extends DefaultHandler {

        private enum State {
            TOP, TEXT, DATA, FIELD, FIELD_TOOLTIP
        }

        //values keyed by the local name of the data element that carried them
        final Map<String, List<String>> values = new LinkedHashMap<>();
        //insertion order is dump order
        final Map<String, XFAField> fields = new LinkedHashMap<>();

        private final ContentHandler out;
        private final StringBuilder buffer = new StringBuilder();
        private State state = State.TOP;
        //element whose end returns from TEXT or FIELD_TOOLTIP
        private String endUri;
        private String endLocalName;
        private String fieldName;
        private String toolTip;
        private String pdfObjRef;

        XFAHandler(ContentHandler out) {
            this.out = out;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            switch (state) {
                case TOP:
                    if (isTemplateField(uri, localName)) {
                        state = State.FIELD;
                        fieldName = firstAttributeValue(atts, "name");
                        toolTip = "";
                        pdfObjRef = "";
                    } else if (isXfaData(uri, localName)) {
                        state = State.DATA;
                        buffer.setLength(0);
                    } else if (TEXT_ELEMENTS.contains(localName)) {
                        startScrape(State.TEXT, uri, localName);
                    }
                    break;
                case FIELD:
                    if ("toolTip".equals(localName)) {
                        startScrape(State.FIELD_TOOLTIP, uri, localName);
                    }
                    break;
                default:
                    break;
            }
        }

        private void startScrape(State scrapeState, String uri, String localName) {
            state = scrapeState;
            endUri = uri;
            endLocalName = localName;
            buffer.setLength(0);
        }

        private boolean isScrapeEnd(String uri, String localName) {
            return endLocalName.equals(localName) && endUri.equals(uri);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (state == State.TEXT || state == State.DATA || state == State.FIELD_TOOLTIP) {
                buffer.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            switch (state) {
                case TEXT:
                    if (isScrapeEnd(uri, localName)) {
                        if (!buffer.toString().isBlank()) {
                            paragraph();
                        }
                        buffer.setLength(0);
                        state = State.TOP;
                    } else if ("p".equals(localName)) {
                        paragraph();
                        buffer.setLength(0);
                    }
                    break;
                case DATA:
                    //text is attributed to whichever element ends next
                    if (buffer.length() > 0) {
                        values.computeIfAbsent(localName, k -> new ArrayList<>())
                                .add(buffer.toString());
                        buffer.setLength(0);
                    }
                    if (isXfaData(uri, localName)) {
                        state = State.TOP;
                    }
                    break;
                case FIELD:
                    if (isTemplateField(uri, localName)) {
                        fields.put(fieldName, new XFAField(fieldName, toolTip, pdfObjRef));
                        state = State.TOP;
                    }
                    break;
                case FIELD_TOOLTIP:
                    if (isScrapeEnd(uri, localName)) {
                        toolTip = buffer.toString();
                        buffer.setLength(0);
                        state = State.FIELD;
                    } else if ("p".equals(localName)) {
                        buffer.append('\n');
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void processingInstruction(String target, String data) {
            if (state == State.FIELD && "PDF_OBJR".equals(target)) {
                pdfObjRef = data;
            }
        }

        private void paragraph() throws SAXException {
            if (buffer.length() == 0) {
                return;
            }
            char[] chars = new char[buffer.length()];
            buffer.getChars(0, chars.length, chars, 0);
            out.startElement(XHTMLContentHandler.XHTML, "p", "p", EMPTY_ATTRIBUTES);
            out.characters(chars, 0, chars.length);
            out.endElement(XHTMLContentHandler.XHTML, "p", "p");
        }

        private static String firstAttributeValue(Attributes atts, String name) {
            for (int i = 0; i < atts.getLength(); i++) {
                if (name.equals(atts.getLocalName(i))) {
                    return atts.getValue(i);
                }
            }
            return "";
        }
    }

    static class XFAField {
        final String fieldName;
        final String toolTip;
        final String pdfObjRef;

        XFAField(String fieldName, String toolTip, String pdfObjRef) {
            this.fieldName = fieldName;
            this.toolTip = toolTip;
            this.pdfObjRef = pdfObjRef;
        }

        @Override
        public String toString() {
            return "XFAField{" + "fieldName='" + fieldName + '\'' + ", toolTip='" + toolTip + '\'' +
                    ", pdfObjRef='" + pdfObjRef + '\'' + '}';
        }
    }
}
