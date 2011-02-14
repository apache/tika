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
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.Stack;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ElementMappingContentHandler;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.sax.ElementMappingContentHandler.TargetElement;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parser for ODF <code>content.xml</code> files.
 */
public class OpenDocumentContentParser implements Parser {

    public static final String TEXT_NS =
        "urn:oasis:names:tc:opendocument:xmlns:text:1.0";

    public static final String TABLE_NS =
        "urn:oasis:names:tc:opendocument:xmlns:table:1.0";

    public static final String OFFICE_NS =
        "urn:oasis:names:tc:opendocument:xmlns:office:1.0";

    public static final String SVG_NS =
        "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0";

    public static final String PRESENTATION_NS =
        "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0";

    public static final String DRAW_NS =
        "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0";

    public static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    protected static final char[] TAB = new char[] { '\t' };

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
                new QName(TEXT_NS, "list"),
                new TargetElement(XHTML, "ul"));
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
        
        final HashMap<QName,QName> aAttsMapping =
            new HashMap<QName,QName>();
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
        final HashMap<QName,QName> tableCellAttsMapping =
            new HashMap<QName,QName>();
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
        final XHTMLContentHandler xhtml =
            new XHTMLContentHandler(handler,metadata);
        DefaultHandler dh = new ElementMappingContentHandler(xhtml, MAPPINGS) {

            private final BitSet textNodeStack = new BitSet();

            private int nodeDepth = 0;

            private int completelyFiltered = 0;

            private Stack<String> headingStack = new Stack<String>();

            @Override
            public void characters(char[] ch, int start, int length)
                    throws SAXException {
                // only forward content of tags from text:-namespace
                if (completelyFiltered == 0 && nodeDepth > 0
                        && textNodeStack.get(nodeDepth - 1)) {
                    super.characters(ch,start,length);
                }
            }

            // helper for checking tags which need complete filtering
            // (with sub-tags)
            private boolean needsCompleteFiltering(
                    String namespaceURI, String localName) {
                if (TEXT_NS.equals(namespaceURI)) {
                    return localName.endsWith("-template")
                        || localName.endsWith("-style");
                } else if (TABLE_NS.equals(namespaceURI)) {
                    return "covered-table-cell".equals(localName);
                } else {
                    return false;
                }
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
                if (TEXT_NS.equals(namespaceURI)) {
                    return true;
                }
                if (SVG_NS.equals(namespaceURI)) {
                    return "title".equals(localName) ||
                            "desc".equals(localName);
                }
                return false;
            }

            @Override
            public void startElement(
                    String namespaceURI, String localName, String qName,
                    Attributes atts) throws SAXException {
                // keep track of current node type. If it is a text node,
                // a bit at the current depth ist set in textNodeStack.
                // characters() checks the top bit to determine, if the
                // actual node is a text node to print out nodeDepth contains
                // the depth of the current node and also marks top of stack.
                assert nodeDepth >= 0;

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
                    // to xhtml handler
                    if (TEXT_NS.equals(namespaceURI) && "h".equals(localName)) {
                        xhtml.startElement(headingStack.push(
                                getXHTMLHeaderTagName(atts)));
                    } else {
                        super.startElement(
                                namespaceURI, localName, qName, atts);
                    }
                }
            }

            @Override
            public void endElement(
                    String namespaceURI, String localName, String qName)
                    throws SAXException {
                // call next handler if no filtering
                if (completelyFiltered == 0) {
                    // special handling of text:h, that are directly passed
                    // to xhtml handler
                    if (TEXT_NS.equals(namespaceURI) && "h".equals(localName)) {
                        xhtml.endElement(headingStack.pop());
                    } else {
                        super.endElement(namespaceURI,localName,qName);
                    }

                    // special handling of tabulators
                    if (TEXT_NS.equals(namespaceURI)
                            && ("tab-stop".equals(localName)
                                    || "tab".equals(localName))) {
                        this.characters(TAB, 0, TAB.length);
                    }
                }

                // revert filter for *all* content of some tags
                if (needsCompleteFiltering(namespaceURI,localName)) {
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

        };

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setValidating(false);
            factory.setNamespaceAware(true);
            try {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (SAXNotRecognizedException e){
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

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

}
