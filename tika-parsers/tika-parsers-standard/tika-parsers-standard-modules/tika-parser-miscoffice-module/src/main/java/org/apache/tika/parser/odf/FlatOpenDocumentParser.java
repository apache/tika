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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

public class FlatOpenDocumentParser extends AbstractParser {

    static final MediaType FLAT_OD =
            MediaType.application("vnd.oasis.opendocument.tika.flat.document");
    static final MediaType FLAT_ODT = MediaType.application("vnd.oasis.opendocument.flat.text");
    static final MediaType FLAT_ODP =
            MediaType.application("vnd.oasis.opendocument.flat.presentation");
    static final MediaType FLAT_ODS =
            MediaType.application("vnd.oasis.opendocument.flat.spreadsheet");
    static final MediaType ODT = MediaType.application("vnd.oasis.opendocument.text");
    static final MediaType ODP = MediaType.application("vnd.oasis.opendocument.presentation");
    static final MediaType ODS = MediaType.application("vnd.oasis.opendocument.spreadsheet");
    private static final long serialVersionUID = -8739250869531737584L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(FLAT_OD, FLAT_ODT, FLAT_ODP, FLAT_ODS)));

    private boolean extractMacros = false;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        final XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        xhtml.startDocument();
        try {
            ContentHandler fodHandler = getContentHandler(xhtml, metadata, context);
            XMLReaderUtils.parseSAX(CloseShieldInputStream.wrap(stream),
                    new EmbeddedContentHandler(fodHandler), context);
            //can only detect subtype (text/pres/sheet) during parse.
            //update it here.
            MediaType detected = ((FlatOpenDocumentParserHandler) fodHandler).getDetectedType();
            if (detected != null) {
                metadata.set(Metadata.CONTENT_TYPE, detected.toString());
            }
        } finally {
            xhtml.endDocument();
        }
    }

    @Field
    public void setExtractMacros(boolean extractMacros) {
        this.extractMacros = extractMacros;
    }

    public boolean isExtractMacros() {
        return extractMacros;
    }
    private ContentHandler getContentHandler(ContentHandler handler, Metadata metadata,
                                             ParseContext context) {
        return new FlatOpenDocumentParserHandler(handler, metadata, context, extractMacros);
    }

    private static class FlatOpenDocumentParserHandler extends ContentHandlerDecorator {
        private static final String META = "meta";
        private static final String BODY = "body";
        private static final String SCRIPTS = "scripts";
        private static final String DOCUMENT = "document";


        private final ContentHandler defaultHandler = new DefaultHandler();

        private final ContentHandler bodyHandler;
        private final ContentHandler metadataHandler;
        private final ContentHandler macroHandler;
        private final boolean extractMacros;
        private ContentHandler currentHandler = defaultHandler;
        private MediaType detectedType = null;

        private FlatOpenDocumentParserHandler(ContentHandler baseHandler, Metadata metadata,
                                              ParseContext parseContext, boolean extractMacros) {
            this.extractMacros = extractMacros;

            this.bodyHandler = new OpenDocumentBodyHandler(new NSNormalizerContentHandler(baseHandler),
                            parseContext);

            this.metadataHandler = new NSNormalizerContentHandler(
                    OpenDocumentMetaParser.getContentHandler(metadata, parseContext));

            if (extractMacros) {
                this.macroHandler = new FlatOpenDocumentMacroHandler(baseHandler, parseContext);
            } else {
                this.macroHandler = null;
            }
        }

        MediaType getDetectedType() {
            return detectedType;
        }

        @Override
        public void startElement(String namespaceURI, String localName, String qName,
                                 Attributes attrs) throws SAXException {

            if (META.equals(localName)) {
                currentHandler = metadataHandler;
            } else if (BODY.equals(localName)) {
                currentHandler = bodyHandler;
            } else if (extractMacros && SCRIPTS.equals(localName)) {
                currentHandler = macroHandler;
            }

            //trust the mimetype element if it exists for the subtype
            if (DOCUMENT.equals(localName)) {
                String mime = XMLReaderUtils.getAttrValue("mimetype", attrs);
                if (mime != null) {
                    if (mime.equals(ODT.toString())) {
                        detectedType = FLAT_ODT;
                    } else if (mime.equals(ODP.toString())) {
                        detectedType = FLAT_ODP;
                    } else if (mime.equals(ODS.toString())) {
                        detectedType = FLAT_ODS;
                    }
                }
            }
            currentHandler.startElement(namespaceURI, localName, qName, attrs);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            currentHandler.characters(ch, start, length);
        }

        @Override
        public void endElement(String namespaceURI, String localName, String qName)
                throws SAXException {
            if (META.equals(localName)) {
                currentHandler = defaultHandler;
            } else if (BODY.equals(localName)) {
                currentHandler = defaultHandler;
            } else if (extractMacros && SCRIPTS.equals(localName)) {
                currentHandler = defaultHandler;
            }
            currentHandler.endElement(namespaceURI, localName, qName);
        }
    }
}
