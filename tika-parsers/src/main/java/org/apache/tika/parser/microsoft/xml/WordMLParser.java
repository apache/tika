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

import javax.xml.namespace.QName;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parses wordml 2003 format word files.  These are single xml files
 * that predate ooxml.
 *
 * @see {@url https://en.wikipedia.org/wiki/Microsoft_Office_XML_formats}
 */
public class WordMLParser extends AbstractXML2003Parser {


    //map between wordml and xhtml entities
    private final static Map<String, String> WORDML_TO_XHTML =
            new ConcurrentHashMap<>();

    //ignore all characters within these elements
    private final static Set<QName> IGNORE_CHARACTERS =
            Collections.newSetFromMap(new ConcurrentHashMap<QName, Boolean>());

    private static final MediaType MEDIA_TYPE = MediaType.application("vnd.ms-wordml");
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    MEDIA_TYPE)));

    static {
        WORDML_TO_XHTML.put(P, P);
        WORDML_TO_XHTML.put("tbl", TABLE);
        WORDML_TO_XHTML.put(TR, TR);
        WORDML_TO_XHTML.put("tc", TD);//not a typo -- table cell -> tc

        IGNORE_CHARACTERS.add(new QName(WORD_ML_URL, HLINK));
        IGNORE_CHARACTERS.add(new QName(WORD_ML_URL, PICT));
        IGNORE_CHARACTERS.add(new QName(WORD_ML_URL, BIN_DATA));
        IGNORE_CHARACTERS.add(new QName(MS_OFFICE_PROPERTIES_URN,
                DOCUMENT_PROPERTIES));
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    protected ContentHandler getContentHandler(ContentHandler ch,
                                        Metadata metadata, ParseContext context) {
        EmbeddedDocumentExtractor ex = context.get(EmbeddedDocumentExtractor.class);

        if (ex == null) {
            ex = new ParsingEmbeddedDocumentExtractor(context);
        }

        return new TeeContentHandler(
                super.getContentHandler(ch, metadata, context),
                new WordMLHandler(ch),
                new HyperlinkHandler(ch,
                        WORD_ML_URL),
                new PictHandler(ch, ex));
    }

    @Override
    public void setContentType(Metadata metadata) {
        metadata.set(Metadata.CONTENT_TYPE, MEDIA_TYPE.toString());
    }

    private class WordMLHandler extends DefaultHandler {
        private final ContentHandler handler;
        private boolean ignoreCharacters;
        private boolean inBody = false;

        //use inP to keep track of whether the handler is
        //in a paragraph or not. <p><p></p></p> was allowed
        //in wordml. Use this boolean to prevent <p> within <p>
        private boolean inP;

        public WordMLHandler(ContentHandler handler) {
            this.handler = handler;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs)
                throws SAXException {
            localName = localName.toLowerCase(Locale.US);
            if (WORD_ML_URL.equals(uri)) {
                if (BODY.equals(localName)) {
                    inBody = true;
                    return;
                }
                String html = WORDML_TO_XHTML.get(localName);
                if (html != null) {
                    if (P.equals(localName)) {
                        //close p if already in a p to prevent nested <p>
                        if (inP) {
                            handler.endElement(XHTMLContentHandler.XHTML, P, P);
                        }
                        inP = true;
                    }
                    handler.startElement(XHTMLContentHandler.XHTML, html, html, EMPTY_ATTRS);
                    if (html.equals(TABLE)) {
                        handler.startElement(XHTMLContentHandler.XHTML, TBODY, TBODY, EMPTY_ATTRS);
                    }
                }

            }
            if (IGNORE_CHARACTERS.contains(new QName(uri, localName))) {
                ignoreCharacters = true;
            }
        }

        @Override
        public void characters(char[] str , int offset, int len) throws SAXException {
            if (!ignoreCharacters && inBody) {
                handler.characters(str, offset, len);
            }
        }
        
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (WORD_ML_URL.equals(uri)) {
                //for now, don't bother checking for end of body...if there's any text
                //after the close of body, we should extract it
                localName = localName.toLowerCase(Locale.US);
                String html = WORDML_TO_XHTML.get(localName);
                if (html != null) {
                    if (html.equals(TABLE)) {
                        handler.endElement(XHTMLContentHandler.XHTML, TBODY, TBODY);
                    }
                    if (P.equals(html) && !inP) {
                        //start p if not already in one to prevent non-matching <p>
                        handler.startElement(XHTMLContentHandler.XHTML, P, P, EMPTY_ATTRS);
                    }
                    handler.endElement(XHTMLContentHandler.XHTML, html, html);

                    if (P.equals(html)) {
                        inP = false;
                    }
                }
            }
            if (IGNORE_CHARACTERS.contains(new QName(uri, localName))) {
                ignoreCharacters = false;
            }

        }
    }

    private class PictHandler extends DefaultHandler {
        final StringBuilder buffer = new StringBuilder();
        final ContentHandler handler;
        EmbeddedDocumentExtractor embeddedDocumentExtractor;
        boolean inPict = false;
        boolean inBin = false;
        String pictName = null;
        final Base64 base64 = new Base64();

        public PictHandler(ContentHandler handler, EmbeddedDocumentExtractor embeddedDocumentExtractor) {
            this.handler = handler;
            this.embeddedDocumentExtractor = embeddedDocumentExtractor;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs)
                throws SAXException {
            if (WORD_ML_URL.equals(uri)) {
                if (PICT.equals(localName)) {
                    inPict = true;
                } else if (BIN_DATA.equals(localName)) {
                    inBin = true;
                    pictName = attrs.getValue(WORD_ML_URL, NAME_ATTR);
                    if (pictName != null) {
                        pictName = pictName.replaceFirst("wordml://", "");
                    }
                }
            }
        }

        @Override
        public void characters(char[] str , int offset, int len) throws SAXException {
            if (inBin) {
                buffer.append(str, offset, len);
            } else if (inPict){
                handler.characters(str, offset, len);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (!WORD_ML_URL.equals(uri)) {
                return;
            }
            if (PICT.equals(localName)) {
                inPict = false;
                AttributesImpl attrs = new AttributesImpl();
                if (pictName != null) {
                    attrs.addAttribute(XHTMLContentHandler.XHTML,
                            HREF, HREF, CDATA, pictName);
                }
                handler.startElement(XHTMLContentHandler.XHTML,
                        IMG, IMG, attrs);
                handler.endElement(
                        XHTMLContentHandler.XHTML, IMG, IMG);
            } else if (BIN_DATA.equals(localName)) {
                inBin = false;
                byte[] bytes = base64.decode(buffer.toString());
                if (bytes == null) {
                    return;
                }
                try (TikaInputStream is = TikaInputStream.get(bytes)) {
                    Metadata metadata = new Metadata();
                    if (pictName != null) {
                        metadata.set(Metadata.RESOURCE_NAME_KEY, pictName);
                    }
                    if (embeddedDocumentExtractor.shouldParseEmbedded(metadata)) {
                        embeddedDocumentExtractor.parseEmbedded(is,
                                handler, metadata, false);
                    }
                } catch (IOException e) {
                    //log
                }
                buffer.setLength(0);
            }
        }
    }
}
