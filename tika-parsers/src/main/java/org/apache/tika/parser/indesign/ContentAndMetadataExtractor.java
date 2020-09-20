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
package org.apache.tika.parser.indesign;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extractor for InDesign Content and Metadata.
 */
class ContentAndMetadataExtractor {

    private final static Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

    /**
     * Extract the InDesign Story Content and emit to the <code>XHTMLContentHandler</code>.
     *
     * @param stream the document stream (input)
     * @param handler handler for the XHTML SAX events (output)
     * @param metadata document metadata (input and output)
     * @param context parse context
     * @throws IOException if the document stream could not be read
     * @throws SAXException if the SAX events could not be processed
     * @throws TikaException if the document could not be parsed
     */
    static void extract(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // Parse the content using inner content handler
        XMLReaderUtils.parseSAX(
                new CloseShieldInputStream(stream), new ContentAndMetadataHandler(handler, metadata), context
        );
    }

    /**
     * Content handler for InDesign Content and Metadata.
     */
    static class ContentAndMetadataHandler extends DefaultHandler {

        private final ContentHandler handler;
        private final Metadata metadata;
        private boolean inContent = false;

        ContentAndMetadataHandler(ContentHandler handler, Metadata metadata) {
            this.handler = handler;
            this.metadata = metadata;
        }

        public void startElement(
                String uri, String localName, String qName, Attributes attributes)
                throws SAXException {

            // Get Spread Metadata
            if ("Spread".equals(localName) || "MasterSpread".equals(localName)) {
                metadata.add("PageCount", attributes.getValue("PageCount"));
            }

            // Trigger processing of content from Spread or Stories
            if ("Content".equals(localName)) {
                inContent = true;
                handler.startElement(XHTMLContentHandler.XHTML, "p", "p", EMPTY_ATTRIBUTES);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (inContent) {
                handler.characters(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("Content".equals(localName)) {
                inContent = false;
                handler.endElement(XHTMLContentHandler.XHTML, "p", "p");
            }
        }
    }


}