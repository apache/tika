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
package org.apache.tika.parser.odf;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ElementMappingContentHandler;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.sax.ElementMappingContentHandler.TargetElement;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

/**
 * Parser for ODF <code>content.xml</code> files.
 */
public class OpenDocumentContentParser extends AbstractParser {
    private interface Style {
    }

    private static class TextStyle implements Style {
        public boolean italic;
        public boolean bold;
        public boolean underlined;
    }

    private static class ListStyle implements Style {
        public boolean ordered;

        public String getTag() {
            return ordered ? "ol" : "ul";
        }
    }

    private static final class OpenDocumentElementMappingContentHandler extends
            ElementMappingContentHandler {
        private final ContentHandler handler;
        private final BitSet textNodeStack = new BitSet();
        private int nodeDepth = 0;
        private int completelyFiltered = 0;
        private Stack<String> headingStack = new Stack<String>();
        private Map<String, TextStyle> textStyleMap = new HashMap<String, TextStyle>();
        private Map<String, ListStyle> listStyleMap = new HashMap<String, ListStyle>();
        private TextStyle textStyle;
        private TextStyle lastTextStyle;
        private Stack<ListStyle> listStyleStack = new Stack<ListStyle>();
        private ListStyle listStyle;

        private OpenDocumentElementMappingContentHandler(ContentHandler handler,
                                                         Map<QName, TargetElement> mappings) {
            super(handler, mappings);
            this.handler = handler;
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            // only forward content of tags from text:-namespace
            if (completelyFiltered == 0 && nodeDepth > 0
                    && textNodeStack.get(nodeDepth - 1)) {
                lazyEndSpan();
                super.characters(ch, start, length);
            }
        }

        // helper for checking tags which need complete filtering
        // (with sub-tags)
        private boolean needsCompleteFiltering(
                String namespaceURI, String localName) {
            if (TEXT_NS.equals(namespaceURI)) {
                return localName.endsWith("-template")
                        || localName.endsWith("-style");
            }
            return TABLE_NS.equals(namespaceURI) && "covered-table-cell".equals(localName);
        }

        // map the heading level to <hX> HTML tags
        private String getXHTMLHeaderTagName(Attributes atts) {
            String depthStr = atts.getValue(TEXT_NS, "outline-level");
            if (depthStr == null) {
                return "h1";
            }

            int depth = Integer.parseInt(depthStr);
            if (depth >= 6) {
                return "h6";
            } else if (depth <= 1) {
                return "h1";
            } else {
                return "h" + depth;
            }
        }

        /**
         * Check if a node is a text node
         */
        private boolean isTextNode(String namespaceURI, String localName) {
            if (TEXT_NS.equals(namespaceURI) && !localName.equals("page-number") && !localName.equals("page-count")) {
                return true;
            }
            if (SVG_NS.equals(namespaceURI)) {
                return "title".equals(localName) ||
                        "desc".equals(localName);
            }
            return false;
        }

        private void startList(String name) throws SAXException {
            String elementName = "ul";
            if (name != null) {
                ListStyle style = listStyleMap.get(name);
                elementName = style != null ? style.getTag() : "ul";
                listStyleStack.push(style);
            }
            handler.startElement(XHTML, elementName, elementName, EMPTY_ATTRIBUTES);
        }

        private void endList() throws SAXException {
            String elementName = "ul";
            if (!listStyleStack.isEmpty()) {
                ListStyle style = listStyleStack.pop();
                elementName = style != null ? style.getTag() : "ul";
            }
            handler.endElement(XHTML, elementName, elementName);
        }

        private void startSpan(String name) throws SAXException {
            if (name == null) {
                return;
            }

            TextStyle style = textStyleMap.get(name);
            if (style == null) {
                return;
            }

            // End tags that refer to no longer valid styles
            if (!style.underlined && lastTextStyle != null && lastTextStyle.underlined) {
                handler.endElement(XHTML, "u", "u");
            }
            if (!style.italic && lastTextStyle != null && lastTextStyle.italic) {
                handler.endElement(XHTML, "i", "i");
            }
            if (!style.bold && lastTextStyle != null && lastTextStyle.bold) {
                handler.endElement(XHTML, "b", "b");
            }

            // Start tags for new styles
            if (style.bold && (lastTextStyle == null || !lastTextStyle.bold)) {
                handler.startElement(XHTML, "b", "b", EMPTY_ATTRIBUTES);
            }
            if (style.italic && (lastTextStyle == null || !lastTextStyle.italic)) {
                handler.startElement(XHTML, "i", "i", EMPTY_ATTRIBUTES);
            }
            if (style.underlined && (lastTextStyle == null || !lastTextStyle.underlined)) {
                handler.startElement(XHTML, "u", "u", EMPTY_ATTRIBUTES);
            }

            textStyle = style;
            lastTextStyle = null;
        }

        private void endSpan() throws SAXException {
            lastTextStyle = textStyle;
            textStyle = null;
        }

        private void lazyEndSpan() throws SAXException {
            if (lastTextStyle == null) {
                return;
            }

            if (lastTextStyle.underlined) {
                handler.endElement(XHTML, "u", "u");
            }
            if (lastTextStyle.italic) {
                handler.endElement(XHTML, "i", "i");
            }
            if (lastTextStyle.bold) {
                handler.endElement(XHTML, "b", "b");
            }

            lastTextStyle = null;
        }

        @Override
        public void startElement(
                String namespaceURI, String localName, String qName,
                Attributes attrs) throws SAXException {
            // keep track of current node type. If it is a text node,
            // a bit at the current depth its set in textNodeStack.
            // characters() checks the top bit to determine, if the
            // actual node is a text node to print out nodeDepth contains
            // the depth of the current node and also marks top of stack.
            assert nodeDepth >= 0;

            // Set styles
            if (STYLE_NS.equals(namespaceURI) && "style".equals(localName)) {
                String family = attrs.getValue(STYLE_NS, "family");
                if ("text".equals(family)) {
                    textStyle = new TextStyle();
                    String name = attrs.getValue(STYLE_NS, "name");
                    textStyleMap.put(name, textStyle);
                }
            } else if (TEXT_NS.equals(namespaceURI) && "list-style".equals(localName)) {
                listStyle = new ListStyle();
                String name = attrs.getValue(STYLE_NS, "name");
                listStyleMap.put(name, listStyle);
            } else if (textStyle != null && STYLE_NS.equals(namespaceURI)
                    && "text-properties".equals(localName)) {
                String fontStyle = attrs.getValue(FORMATTING_OBJECTS_NS, "font-style");
                if ("italic".equals(fontStyle) || "oblique".equals(fontStyle)) {
                    textStyle.italic = true;
                }
                String fontWeight = attrs.getValue(FORMATTING_OBJECTS_NS, "font-weight");
                if ("bold".equals(fontWeight) || "bolder".equals(fontWeight)
                        || (fontWeight != null && Character.isDigit(fontWeight.charAt(0))
                        && Integer.valueOf(fontWeight) > 500)) {
                    textStyle.bold = true;
                }
                String underlineStyle = attrs.getValue(STYLE_NS, "text-underline-style");
                if (underlineStyle != null) {
                    textStyle.underlined = true;
                }
            } else if (listStyle != null && TEXT_NS.equals(namespaceURI)) {
                if ("list-level-style-bullet".equals(localName)) {
                    listStyle.ordered = false;
                } else if ("list-level-style-number".equals(localName)) {
                    listStyle.ordered = true;
                }
            }

            textNodeStack.set(nodeDepth++,
                    isTextNode(namespaceURI, localName));
            // filter *all* content of some tags
            assert completelyFiltered >= 0;

            if (needsCompleteFiltering(namespaceURI, localName)) {
                completelyFiltered++;
            }
            // call next handler if no filtering
            if (completelyFiltered == 0) {
                // special handling of text:h, that are directly passed
                // to incoming handler
                if (TEXT_NS.equals(namespaceURI) && "h".equals(localName)) {
                    final String el = headingStack.push(getXHTMLHeaderTagName(attrs));
                    handler.startElement(XHTMLContentHandler.XHTML, el, el, EMPTY_ATTRIBUTES);
                } else if (TEXT_NS.equals(namespaceURI) && "list".equals(localName)) {
                    startList(attrs.getValue(TEXT_NS, "style-name"));
                } else if (TEXT_NS.equals(namespaceURI) && "span".equals(localName)) {
                    startSpan(attrs.getValue(TEXT_NS, "style-name"));
                } else {
                    super.startElement(namespaceURI, localName, qName, attrs);
                }
            }
        }

        @Override
        public void endElement(
                String namespaceURI, String localName, String qName)
                throws SAXException {
            if (STYLE_NS.equals(namespaceURI) && "style".equals(localName)) {
                textStyle = null;
            } else if (TEXT_NS.equals(namespaceURI) && "list-style".equals(localName)) {
                listStyle = null;
            }

            // call next handler if no filtering
            if (completelyFiltered == 0) {
                // special handling of text:h, that are directly passed
                // to incoming handler
                if (TEXT_NS.equals(namespaceURI) && "h".equals(localName)) {
                    final String el = headingStack.pop();
                    handler.endElement(XHTMLContentHandler.XHTML, el, el);
                } else if (TEXT_NS.equals(namespaceURI) && "list".equals(localName)) {
                    endList();
                } else if (TEXT_NS.equals(namespaceURI) && "span".equals(localName)) {
                    endSpan();
                } else {
                    if (TEXT_NS.equals(namespaceURI) && "p".equals(localName)) {
                        lazyEndSpan();
                    }
                    super.endElement(namespaceURI, localName, qName);
                }

                // special handling of tabulators
                if (TEXT_NS.equals(namespaceURI)
                        && ("tab-stop".equals(localName)
                        || "tab".equals(localName))) {
                    this.characters(TAB, 0, TAB.length);
                }
            }

            // revert filter for *all* content of some tags
            if (needsCompleteFiltering(namespaceURI, localName)) {
                completelyFiltered--;
            }
            assert completelyFiltered >= 0;

            // reduce current node depth
            nodeDepth--;
            assert nodeDepth >= 0;
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) {
            // remove prefix mappings as they should not occur in XHTML
        }

        @Override
        public void endPrefixMapping(String prefix) {
            // remove prefix mappings as they should not occur in XHTML
        }
    }

    public static final String TEXT_NS =
            "urn:oasis:names:tc:opendocument:xmlns:text:1.0";

    public static final String TABLE_NS =
            "urn:oasis:names:tc:opendocument:xmlns:table:1.0";

    public static final String STYLE_NS =
            "urn:oasis:names:tc:opendocument:xmlns:style:1.0";

    public static final String FORMATTING_OBJECTS_NS =
            "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0";

    public static final String OFFICE_NS =
            "urn:oasis:names:tc:opendocument:xmlns:office:1.0";

    public static final String SVG_NS =
            "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0";

    public static final String PRESENTATION_NS =
            "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0";

    public static final String DRAW_NS =
            "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0";

    public static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    protected static final char[] TAB = new char[]{'\t'};

    private static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

    /**
     * Mappings between ODF tag names and XHTML tag names
     * (including attributes). All other tag names/attributes are ignored
     * and left out from event stream.
     */
    private static final HashMap<QName, TargetElement> MAPPINGS =
            new HashMap<QName, TargetElement>();

    static {
        // general mappings of text:-tags
        MAPPINGS.put(
                new QName(TEXT_NS, "p"),
                new TargetElement(XHTML, "p"));
        // text:h-tags are mapped specifically in startElement/endElement
        MAPPINGS.put(
                new QName(TEXT_NS, "line-break"),
                new TargetElement(XHTML, "br"));
        MAPPINGS.put(
                new QName(TEXT_NS, "list-item"),
                new TargetElement(XHTML, "li"));
        MAPPINGS.put(
                new QName(TEXT_NS, "note"),
                new TargetElement(XHTML, "div"));
        MAPPINGS.put(
                new QName(OFFICE_NS, "annotation"),
                new TargetElement(XHTML, "div"));
        MAPPINGS.put(
                new QName(PRESENTATION_NS, "notes"),
                new TargetElement(XHTML, "div"));
        MAPPINGS.put(
                new QName(DRAW_NS, "object"),
                new TargetElement(XHTML, "object"));
        MAPPINGS.put(
                new QName(DRAW_NS, "text-box"),
                new TargetElement(XHTML, "div"));
        MAPPINGS.put(
                new QName(SVG_NS, "title"),
                new TargetElement(XHTML, "span"));
        MAPPINGS.put(
                new QName(SVG_NS, "desc"),
                new TargetElement(XHTML, "span"));
        MAPPINGS.put(
                new QName(TEXT_NS, "span"),
                new TargetElement(XHTML, "span"));

        final HashMap<QName, QName> aAttsMapping =
                new HashMap<QName, QName>();
        aAttsMapping.put(
                new QName(XLINK_NS, "href"),
                new QName("href"));
        aAttsMapping.put(
                new QName(XLINK_NS, "title"),
                new QName("title"));
        MAPPINGS.put(
                new QName(TEXT_NS, "a"),
                new TargetElement(XHTML, "a", aAttsMapping));

        // create HTML tables from table:-tags
        MAPPINGS.put(
                new QName(TABLE_NS, "table"),
                new TargetElement(XHTML, "table"));
        // repeating of rows is ignored; for columns, see below!
        MAPPINGS.put(
                new QName(TABLE_NS, "table-row"),
                new TargetElement(XHTML, "tr"));
        // special mapping for rowspan/colspan attributes
        final HashMap<QName, QName> tableCellAttsMapping =
                new HashMap<QName, QName>();
        tableCellAttsMapping.put(
                new QName(TABLE_NS, "number-columns-spanned"),
                new QName("colspan"));
        tableCellAttsMapping.put(
                new QName(TABLE_NS, "number-rows-spanned"),
                new QName("rowspan"));
        /* TODO: The following is not correct, the cell should be repeated not spanned!
         * Code generates a HTML cell, spanning all repeated columns, to make the cell look correct.
         * Problems may occur when both spanning and repeating is given, which is not allowed by spec.
         * Cell spanning instead of repeating  is not a problem, because OpenOffice uses it
         * only for empty cells.
         */
        tableCellAttsMapping.put(
                new QName(TABLE_NS, "number-columns-repeated"),
                new QName("colspan"));
        MAPPINGS.put(
                new QName(TABLE_NS, "table-cell"),
                new TargetElement(XHTML, "td", tableCellAttsMapping));
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.emptySet(); // not a top-level parser
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        parseInternal(stream,
                new XHTMLContentHandler(handler, metadata),
                metadata, context);
    }

    void parseInternal(
            InputStream stream, final ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        DefaultHandler dh = new OpenDocumentElementMappingContentHandler(handler, MAPPINGS);

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setValidating(false);
            factory.setNamespaceAware(true);
            try {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (SAXNotRecognizedException e) {
                // TIKA-329: Some XML parsers do not support the secure-processing
                // feature, even though it's required by JAXP in Java 5. Ignoring
                // the exception is fine here, deployments without this feature
                // are inherently vulnerable to XML denial-of-service attacks.
            }
            SAXParser parser = factory.newSAXParser();
            parser.parse(
                    new CloseShieldInputStream(stream),
                    new OfflineContentHandler(
                            new NSNormalizerContentHandler(dh)));
        } catch (ParserConfigurationException e) {
            throw new TikaException("XML parser configuration error", e);
        }
    }

}
