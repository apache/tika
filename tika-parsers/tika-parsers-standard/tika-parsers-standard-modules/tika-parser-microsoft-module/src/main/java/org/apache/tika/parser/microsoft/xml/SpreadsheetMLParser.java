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
package org.apache.tika.parser.microsoft.xml;


import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parses wordml 2003 format Excel files.  These are single xml files
 * that predate ooxml.
 * <p>
 * See <a href="https://en.wikipedia.org/wiki/Microsoft_Office_XML_formats">https://en.wikipedia.org/wiki/Microsoft_Office_XML_formats</a>
 */
public class SpreadsheetMLParser extends AbstractXML2003Parser {

    final static String CELL = "cell";
    final static String DATA = "data";
    final static String ROW = "row";
    final static String WORKSHEET = "worksheet";

    private static final MediaType MEDIA_TYPE = MediaType.application("vnd.ms-spreadsheetml");
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MEDIA_TYPE);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    protected ContentHandler getContentHandler(ContentHandler ch, Metadata metadata,
                                               ParseContext context) {

        return new TeeContentHandler(super.getContentHandler(ch, metadata, context),
                new ExcelMLHandler(ch));
    }

    @Override
    public void setContentType(Metadata metadata) {
        metadata.set(Metadata.CONTENT_TYPE, MEDIA_TYPE.toString());
    }

    private static class ExcelMLHandler extends DefaultHandler {
        final ContentHandler handler;
        StringBuilder buffer = new StringBuilder();
        String href = null;
        boolean inData = false;
        private boolean inBody = false;

        public ExcelMLHandler(ContentHandler handler) {
            this.handler = handler;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs)
                throws SAXException {
            localName = localName.toLowerCase(Locale.US);

            if (MS_SPREADSHEET_URN.equals(uri)) {
                switch (localName) {
                    case BODY:
                        inBody = true;
                        break;
                    case TABLE:
                        handler.startElement(XHTMLContentHandler.XHTML, TABLE, TABLE, EMPTY_ATTRS);
                        handler.startElement(XHTMLContentHandler.XHTML, TBODY, TBODY, EMPTY_ATTRS);
                        break;
                    case WORKSHEET:
                        String worksheetName = attrs.getValue(MS_SPREADSHEET_URN, "Name");
                        AttributesImpl xhtmlAttrs = new AttributesImpl();
                        if (worksheetName != null) {
                            xhtmlAttrs.addAttribute(XHTMLContentHandler.XHTML, NAME_ATTR, NAME_ATTR,
                                    CDATA, worksheetName);
                        }
                        handler.startElement(XHTMLContentHandler.XHTML, DIV, DIV, xhtmlAttrs);
                        break;
                    case ROW:
                        handler.startElement(XHTMLContentHandler.XHTML, TR, TR, EMPTY_ATTRS);
                        break;
                    case CELL:
                        href = attrs.getValue(MS_SPREADSHEET_URN, "HRef");
                        handler.startElement(XHTMLContentHandler.XHTML, TD, TD, EMPTY_ATTRS);
                        break;
                    case DATA:
                        inData = true;
                        break;
                }
            }
        }

        @Override
        public void characters(char[] str, int offset, int len) throws SAXException {
            if (inData) {
                buffer.append(str, offset, len);
            } else if (inBody) {
                handler.characters(str, offset, len);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            localName = localName.toLowerCase(Locale.US);
            if (MS_SPREADSHEET_URN.equals(uri)) {
                switch (localName) {
                    case TABLE:
                        handler.endElement(XHTMLContentHandler.XHTML, TBODY, TBODY);
                        handler.endElement(XHTMLContentHandler.XHTML, TABLE, TABLE);

                        break;
                    case WORKSHEET:
                        handler.endElement(XHTMLContentHandler.XHTML, DIV, DIV);
                        break;
                    case ROW:
                        handler.endElement(XHTMLContentHandler.XHTML, TR, TR);
                        break;
                    case CELL:
                        handler.endElement(XHTMLContentHandler.XHTML, TD, TD);
                        break;
                    case DATA:
                        if (href != null) {
                            AttributesImpl attrs = new AttributesImpl();
                            attrs.addAttribute(XHTMLContentHandler.XHTML, HREF, HREF, CDATA, href);
                            handler.startElement(XHTMLContentHandler.XHTML, A, A, attrs);
                        }
                        String b = buffer.toString();
                        if (b == null) {
                            b = "";
                        }
                        char[] chars = b.trim().toCharArray();
                        handler.characters(chars, 0, chars.length);
                        if (href != null) {
                            handler.endElement(XHTMLContentHandler.XHTML, A, A);
                        }
                        buffer.setLength(0);
                        inData = false;
                        href = null;
                        break;
                }
            }

        }
    }
}
