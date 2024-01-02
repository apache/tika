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
package org.apache.tika.parser.epub;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.XMLReaderUtils;

public class EncryptionParser implements Parser {

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.EMPTY_SET;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        try {
            XMLReaderUtils.parseSAX(stream, new EncryptionHandler(), context);
        } catch (SAXException e) {
            if (e.getCause() instanceof EncryptedDocumentException) {
                throw (EncryptedDocumentException)e.getCause();
            }
        }
    }

    private class EncryptionHandler extends DefaultHandler {
        Set<String> encryptedItems = new HashSet<>();
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("CipherReference".equals(localName)) {
                String encryptedUri = XMLReaderUtils.getAttrValue("URI", attributes);
                encryptedItems.add(encryptedUri);
            }
        }

        @Override
        public void endDocument() throws SAXException {
            if (encryptedItems.size() > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append("EPUB contains encrypted items: ");
                int added = 0;
                for (String u : encryptedItems) {
                    if (sb.length() > 500) {
                        sb.append(" and others...");
                        break;
                    }
                    if (added++ > 0) {
                        sb.append(", ");
                    }
                    sb.append(u);
                }
                throw new SAXException(new EncryptedDocumentException(sb.toString()));
            }
        }
    }
}
