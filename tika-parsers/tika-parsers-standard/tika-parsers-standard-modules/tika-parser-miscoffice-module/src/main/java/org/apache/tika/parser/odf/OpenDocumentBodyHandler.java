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

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import javax.xml.namespace.QName;

import org.apache.commons.codec.binary.Base64;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ElementMappingContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

/*
    Handler for the body element or odt flat files and content.xml of
    traditional compressed odt files
 */
class OpenDocumentBodyHandler extends ElementMappingContentHandler {

    public static final String TEXT_NS = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    public static final String TABLE_NS = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    public static final String STYLE_NS = "urn:oasis:names:tc:opendocument:xmlns:style:1.0";
    public static final String FORMATTING_OBJECTS_NS =
            "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0";
    public static final String OFFICE_NS = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    public static final String SVG_NS = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0";
    public static final String PRESENTATION_NS =
            "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0";
    public static final String DRAW_NS = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0";
    public static final String XLINK_NS = "http://www.w3.org/1999/xlink";
    protected static final char[] TAB = new char[]{'\t'};
    private static final String BINARY_DATA = "binary-data";
    private static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();
    private static final NullListStyle NULL_LIST_STYLE = new NullListStyle();

    /**
     * Mappings between ODF tag names and XHTML tag names
     * (including attributes). All other tag names/attributes are ignored
     * and left out from event stream.
     */
    private static final HashMap<QName, TargetElement> MAPPINGS =
            new HashMap<>();
    private static final char[] SPACE = new char[]{' '};
    private static final String CLASS = "class";
    private static final Attributes ANNOTATION_ATTRIBUTES = buildAttributes(CLASS, "annotation");
    private static final Attributes NOTE_ATTRIBUTES = buildAttributes(CLASS, "note");
    private static final Attributes NOTES_ATTRIBUTES = buildAttributes(CLASS, "notes");

    static {
        // general mappings of text:-tags
        MAPPINGS.put(new QName(TEXT_NS, "p"), new TargetElement(XHTML, "p"));
        // text:h-tags are mapped specifically in startElement/endElement
        MAPPINGS.put(new QName(TEXT_NS, "line-break"), new TargetElement(XHTML, "br"));
        MAPPINGS.put(new QName(TEXT_NS, "list-item"), new TargetElement(XHTML, "li"));
        MAPPINGS.put(new QName(TEXT_NS, "note"), new TargetElement(XHTML, "span"));
        MAPPINGS.put(new QName(OFFICE_NS, "annotation"), new TargetElement(XHTML,
                "span"));
        MAPPINGS.put(new QName(PRESENTATION_NS, "notes"), new TargetElement(XHTML,
                "span"));
        MAPPINGS.put(new QName(DRAW_NS, "object"), new TargetElement(XHTML,
                "object"));
        MAPPINGS.put(new QName(DRAW_NS, "text-box"), new TargetElement(XHTML, "div"));
        MAPPINGS.put(new QName(SVG_NS, "title"), new TargetElement(XHTML, "span"));
        MAPPINGS.put(new QName(SVG_NS, "desc"), new TargetElement(XHTML, "span"));
        MAPPINGS.put(new QName(TEXT_NS, "span"), new TargetElement(XHTML, "span"));

        final HashMap<QName, QName> aAttsMapping = new HashMap<>();
        aAttsMapping.put(new QName(XLINK_NS, "href"), new QName("href"));
        aAttsMapping.put(new QName(XLINK_NS, "title"), new QName("title"));
        MAPPINGS.put(new QName(TEXT_NS, "a"), new TargetElement(XHTML, "a",
                aAttsMapping));
        MAPPINGS.put(new QName(DRAW_NS, "a"), new TargetElement(XHTML, "a",
                aAttsMapping));

        // create HTML tables from table:-tags
        MAPPINGS.put(new QName(TABLE_NS, "table"), new TargetElement(XHTML, "table"));
        // repeating of rows is ignored; for columns, see below!
        MAPPINGS.put(new QName(TABLE_NS, "table-row"), new TargetElement(XHTML, "tr"));
        // special mapping for rowspan/colspan attributes
        final HashMap<QName, QName> tableCellAttsMapping = new HashMap<>();
        tableCellAttsMapping
                .put(new QName(TABLE_NS, "number-columns-spanned"), new QName("colspan"));
        tableCellAttsMapping.put(new QName(TABLE_NS, "number-rows-spanned"), new QName("rowspan"));
        /* TODO: The following is not correct, the cell should be repeated not spanned!
         * Code generates a HTML cell, spanning all repeated columns, to make the cell look correct.
         * Problems may occur when both spanning and repeating is given, which is not allowed by
         *  spec.
         * Cell spanning instead of repeating  is not a problem, because OpenOffice uses it
         * only for empty cells.
         */
        tableCellAttsMapping
                .put(new QName(TABLE_NS, "number-columns-repeated"), new QName("colspan"));
        MAPPINGS.put(new QName(TABLE_NS, "table-cell"),
                new TargetElement(XHTML, "td", tableCellAttsMapping));
    }

    private final ContentHandler handler;
    private final ParseContext parseContext;
    private final BitSet textNodeStack = new BitSet();
    //have we written the start style tags
    //yet for the current text style
    boolean hasWrittenStartStyleTags = false;
    //if we're in a binary-data tag
    boolean inBinaryData = false;
    private EmbeddedDocumentExtractor embeddedDocumentExtractor;
    private StringBuilder base64BinaryDataBuffer = new StringBuilder();
    private int nodeDepth = 0;
    private int completelyFiltered = 0;
    private Stack<String> headingStack = new Stack<>();
    private Map<String, TextStyle> paragraphTextStyleMap = new HashMap<>();
    private Map<String, TextStyle> textStyleMap = new HashMap<>();
    private Map<String, ListStyle> listStyleMap = new HashMap<>();
    private String currParagraphStyleName; //paragraph style name
    private TextStyle currTextStyle; //this is the text style for particular spans/paragraphs
    private String currTextStyleName;
    private Stack<ListStyle> listStyleStack = new Stack<>();
    private ListStyle listStyle;
    // True if we are currently in the named style:
    private boolean curUnderlined;
    private boolean curBold;
    private boolean curItalic;
    private int pDepth = 0;
    OpenDocumentBodyHandler(ContentHandler handler, ParseContext parseContext) {
        super(handler, MAPPINGS);
        this.handler = handler;
        this.parseContext = parseContext;
    }

    private static Attributes buildAttributes(String key, String value) {
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", key, key, "CDATA", value);
        return attrs;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inBinaryData) {
            base64BinaryDataBuffer.append(ch, start, length);
            return;
        }
        // only forward content of tags from text:-namespace
        if (completelyFiltered == 0 && nodeDepth > 0 && textNodeStack.get(nodeDepth - 1)) {
            if (!hasWrittenStartStyleTags) {
                updateStyleTags();
                hasWrittenStartStyleTags = true;
            }
            super.characters(ch, start, length);
        }
    }

    // helper for checking tags which need complete filtering
    // (with sub-tags)
    private boolean needsCompleteFiltering(String namespaceURI, String localName) {
        if (TEXT_NS.equals(namespaceURI)) {
            return localName.endsWith("-template") || localName.endsWith("-style");
        }
        return TABLE_NS.equals(namespaceURI) && "covered-table-cell".equals(localName);
    }
            //<p> can appear inside comments and other things that are already inside <p>
    //we need to track our pDepth and only output <p> if we're at the main level

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
        if (TEXT_NS.equals(namespaceURI) && !localName.equals("page-number") &&
                !localName.equals("page-count")) {
            return true;
        }
        if (SVG_NS.equals(namespaceURI)) {
            return "title".equals(localName) || "desc".equals(localName);
        }
        return false;
    }

    private void startList(String name) throws SAXException {
        ListStyle style = null;
        if (name == null || ! listStyleMap.containsKey(name)) {
            style = NULL_LIST_STYLE;
        } else {
            style = listStyleMap.get(name);
        }
        String elementName = style.getTag();
        listStyleStack.push(style);
        handler.startElement(XHTML, elementName, elementName, EMPTY_ATTRIBUTES);
    }

    private void endList() throws SAXException {
        String elementName = "ul";
        if (! listStyleStack.isEmpty()) {
            ListStyle style = listStyleStack.pop();
            elementName = style != null ? style.getTag() : "ul";
        }
        handler.endElement(XHTML, elementName, elementName);
    }

    private void startSpan(String name) throws SAXException {
        if (name == null) {
            return;
        }
        currTextStyle = textStyleMap.get(name);
        hasWrittenStartStyleTags = false;
    }

    private void startParagraph(String styleName) throws SAXException {
        if (pDepth == 0) {
            handler.startElement(XHTML, "p", "p", EMPTY_ATTRIBUTES);
            if (styleName != null) {
                currTextStyle = paragraphTextStyleMap.get(styleName);
            }
            hasWrittenStartStyleTags = false;
        } else {
            handler.characters(SPACE, 0, SPACE.length);
        }
        pDepth++;
    }

    private void endParagraph() throws SAXException {
        closeStyleTags();
        if (pDepth == 1) {
            handler.endElement(XHTML, "p", "p");
        } else {
            handler.characters(SPACE, 0, SPACE.length);
        }
        pDepth--;

    }

    private void updateStyleTags() throws SAXException {

        if (currTextStyle == null) {
            closeStyleTags();
            return;
        }
        if (currTextStyle.bold != curBold) {
            // Enforce nesting -- must close s and i tags
            if (curUnderlined) {
                handler.endElement(XHTML, "u", "u");
                curUnderlined = false;
            }
            if (curItalic) {
                handler.endElement(XHTML, "i", "i");
                curItalic = false;
            }
            if (currTextStyle.bold) {
                handler.startElement(XHTML, "b", "b", EMPTY_ATTRIBUTES);
            } else {
                handler.endElement(XHTML, "b", "b");
            }
            curBold = currTextStyle.bold;
        }

        if (currTextStyle.italic != curItalic) {
            // Enforce nesting -- must close s tag
            if (curUnderlined) {
                handler.endElement(XHTML, "u", "u");
                curUnderlined = false;
            }
            if (currTextStyle.italic) {
                handler.startElement(XHTML, "i", "i", EMPTY_ATTRIBUTES);
            } else {
                handler.endElement(XHTML, "i", "i");
            }
            curItalic = currTextStyle.italic;
        }

        if (currTextStyle.underlined != curUnderlined) {
            if (currTextStyle.underlined) {
                handler.startElement(XHTML, "u", "u", EMPTY_ATTRIBUTES);
            } else {
                handler.endElement(XHTML, "u", "u");
            }
            curUnderlined = currTextStyle.underlined;
        }
    }

    private void endSpan() throws SAXException {
        updateStyleTags();
    }

    private void closeStyleTags() throws SAXException {
        // Close any still open style tags
        if (curUnderlined) {
            handler.endElement(XHTML, "u", "u");
            curUnderlined = false;
        }
        if (curItalic) {
            handler.endElement(XHTML, "i", "i");
            curItalic = false;
        }
        if (curBold) {
            handler.endElement(XHTML, "b", "b");
            curBold = false;
        }
        currTextStyle = null;
        hasWrittenStartStyleTags = false;
    }

    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes attrs)
            throws SAXException {

        if (DRAW_NS.equals(namespaceURI) && "image".equals(localName)) {
            String link = attrs.getValue(XLINK_NS, "href");
            AttributesImpl attr = new AttributesImpl();
            if (!StringUtils.isEmpty(link)) {
                attr.addAttribute("", "src", "src", "CDATA", "embedded:" + link);
            }
            handler.startElement(XHTMLContentHandler.XHTML, "img", "img", attr);
            handler.endElement(XHTMLContentHandler.XHTML, "img", "img");
        }

        if (BINARY_DATA.equals(localName)) {
            inBinaryData = true;
            return;
        }
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
                currTextStyle = new TextStyle();
                currTextStyleName = attrs.getValue(STYLE_NS, "name");
            } else if ("paragraph".equals(family)) {
                currTextStyle = new TextStyle();
                currParagraphStyleName = attrs.getValue(STYLE_NS, "name");
            }
        } else if (TEXT_NS.equals(namespaceURI) && "list-style".equals(localName)) {
            listStyle = new ListStyle();
            String name = attrs.getValue(STYLE_NS, "name");
            listStyleMap.put(name, listStyle);
        } else if (currTextStyle != null && STYLE_NS.equals(namespaceURI) &&
                "text-properties".equals(localName)) {
            String fontStyle = attrs.getValue(FORMATTING_OBJECTS_NS, "font-style");
            if ("italic".equals(fontStyle) || "oblique".equals(fontStyle)) {
                currTextStyle.italic = true;
            }
            String fontWeight = attrs.getValue(FORMATTING_OBJECTS_NS, "font-weight");
            if ("bold".equals(fontWeight) || "bolder".equals(fontWeight) ||
                    (fontWeight != null && Character.isDigit(fontWeight.charAt(0)) &&
                     Integer.parseInt(fontWeight) > 500)) {
                currTextStyle.bold = true;
            }
            String underlineStyle = attrs.getValue(STYLE_NS, "text-underline-style");
            if (underlineStyle != null && !underlineStyle.equals("none")) {
                currTextStyle.underlined = true;
            }
        } else if (listStyle != null && TEXT_NS.equals(namespaceURI)) {
            if ("list-level-style-bullet".equals(localName)) {
                listStyle.ordered = false;
            } else if ("list-level-style-number".equals(localName)) {
                listStyle.ordered = true;
            }
        }

        textNodeStack.set(nodeDepth++, isTextNode(namespaceURI, localName));
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
            } else if (TEXT_NS.equals(namespaceURI) && "p".equals(localName)) {
                startParagraph(attrs.getValue(TEXT_NS, "style-name"));
            } else if (TEXT_NS.equals(namespaceURI) && "s".equals(localName)) {
                handler.characters(SPACE, 0, 1);
            } else if ("annotation".equals(localName)) {
                closeStyleTags();
                handler.startElement(XHTML, "p", "p", ANNOTATION_ATTRIBUTES);
            } else if ("note".equals(localName)) {
                closeStyleTags();
                handler.startElement(XHTML, "p", "p", NOTE_ATTRIBUTES);
            } else if ("notes".equals(localName)) {
                closeStyleTags();
                handler.startElement(XHTML, "p", "p", NOTES_ATTRIBUTES);
            } else {
                super.startElement(namespaceURI, localName, qName, attrs);
            }
        }
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {
        if (BINARY_DATA.equals(localName)) {
            inBinaryData = false;
            try {
                processBinaryData();
            } catch (IOException e) {
                throw new SAXException(e);
            }
            return;
        }
        if (STYLE_NS.equals(namespaceURI) && "style".equals(localName)) {
            if (currTextStyle != null && currTextStyleName != null) {
                textStyleMap.put(currTextStyleName, currTextStyle);
                currTextStyleName = null;
                currTextStyle = null;
            } else if (currTextStyle != null && currParagraphStyleName != null) {
                paragraphTextStyleMap.put(currParagraphStyleName, currTextStyle);
                currParagraphStyleName = null;
                currTextStyle = null;
            }
        } else if (TEXT_NS.equals(namespaceURI) && "list-style".equals(localName)) {
            listStyle = null;
        }

        // call next handler if no filtering
        if (completelyFiltered == 0) {
            // special handling of text:h, that are directly passed
            // to incoming handler
            if (TEXT_NS.equals(namespaceURI) && "h".equals(localName)) {
                final String el = headingStack.pop();
                closeStyleTags();
                handler.endElement(namespaceURI, el, el);
            } else if (TEXT_NS.equals(namespaceURI) && "list".equals(localName)) {
                endList();
            } else if (TEXT_NS.equals(namespaceURI) && "span".equals(localName)) {
                currTextStyle = null;
                hasWrittenStartStyleTags = false;
            } else if (TEXT_NS.equals(namespaceURI) && "p".equals(localName)) {
                endParagraph();
            } else if ("annotation".equals(localName) || "note".equals(localName) ||
                    "notes".equals(localName)) {
                closeStyleTags();
                handler.endElement(namespaceURI, "p", "p");
            } else if ("a".equals(localName)) {
                closeStyleTags();
                super.endElement(namespaceURI, localName, qName);
            } else {
                super.endElement(namespaceURI, localName, qName);
            }

            // special handling of tabulators
            if (TEXT_NS.equals(namespaceURI) &&
                    ("tab-stop".equals(localName) || "tab".equals(localName))) {
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

    private void processBinaryData() throws IOException, SAXException {

        //TODO: figure out whether we're in an inline image or a regular
        //attachment and add that info to the embedded metadata

        byte[] bytes = Base64.decodeBase64(base64BinaryDataBuffer.toString());
        //clear state before parsing
        base64BinaryDataBuffer.setLength(0);
        inBinaryData = false;

        if (embeddedDocumentExtractor == null) {
            embeddedDocumentExtractor =
                    EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);
        }
        Metadata embeddedMetadata = new Metadata();
        if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
            try (InputStream is = TikaInputStream.get(bytes)) {
                embeddedDocumentExtractor.parseEmbedded(is, handler, embeddedMetadata, true);
            }
        }
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) {
        // remove prefix mappings as they should not occur in XHTML
    }

    @Override
    public void endPrefixMapping(String prefix) {
        // remove prefix mappings as they should not occur in XHTML
    }

    private interface Style {
    }

    private static class TextStyle implements Style {
        public boolean italic;
        public boolean bold;
        public boolean underlined;

        @Override
        public String toString() {
            return "TextStyle{" + "italic=" + italic + ", bold=" + bold + ", underlined=" +
                    underlined + '}';
        }
    }

    private static class ListStyle implements Style {
        public boolean ordered;
        public String getTag() {
            return ordered ? "ol" : "ul";
        }
    }

    private static class NullListStyle extends ListStyle {
        NullListStyle() {
            ordered = false;
        }

        @Override
        public String getTag() {
            return "ul";
        }
    }


}
