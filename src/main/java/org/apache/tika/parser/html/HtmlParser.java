/**
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
package org.apache.tika.parser.html;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.utils.Utils;
import org.cyberneko.html.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Simple HTML parser that extracts title.
 */
public class HtmlParser extends AbstractParser {

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        SAXParser parser = new SAXParser();
        parser.setContentHandler(
                new TitleExtractingContentHandler(handler, metadata));
        parser.parse(new InputSource(Utils.getUTF8Reader(
                new CloseShieldInputStream(stream), metadata)));
    }

    private static class TitleExtractingContentHandler extends
            ContentHandlerDecorator {

        private static final String TAG_TITLE = "TITLE";

        private static final String TAG_HEAD = "HEAD";

        private static final String TAG_HTML = "HTML";

        private Phase phase = Phase.START;

        private Metadata metadata;

        private StringBuilder title = new StringBuilder();

        private static enum Phase {
            START, HTML, HEAD, TITLE, IGNORE;
        }

        public TitleExtractingContentHandler(final ContentHandler handler,
                final Metadata metadata) {
            super(handler);
            this.metadata = metadata;
        }

        @Override
        public void startElement(String uri, String localName, String name,
                Attributes atts) throws SAXException {

            switch (phase) {
            case START:
                if (TAG_HTML.equals(localName)) {
                    phase = Phase.HTML;
                }
                break;
            case HTML:
                if (TAG_HEAD.equals(localName)) {
                    phase = Phase.HEAD;
                }
                break;
            case HEAD:
                if (TAG_TITLE.equals(localName)) {
                    phase = Phase.TITLE;
                }
                break;
            }
            super.startElement(uri, localName, name, atts);
        }

        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            switch (phase) {
            case TITLE:
                title.append(ch, start, length);
                break;
            }
            super.characters(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String name)
                throws SAXException {
            switch (phase) {
            case TITLE:
                if (TAG_TITLE.equals(localName)) {
                    phase = Phase.IGNORE;
                }
                break;
            }
            super.endElement(uri, localName, name);
        }

        @Override
        public void endDocument() throws SAXException {
            metadata.set(Metadata.TITLE, title.toString());
            super.endDocument();
        }
    }
}
