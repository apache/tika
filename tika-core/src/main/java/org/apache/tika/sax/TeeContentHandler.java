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
package org.apache.tika.sax;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Content handler proxy that forwards the received SAX events to zero or
 * more underlying content handlers.
 */
public class TeeContentHandler extends DefaultHandler {

    private final ContentHandler[] handlers;

    public TeeContentHandler(ContentHandler... handlers) {
        this.handlers = handlers;
    }

    @Override
    public void startPrefixMapping(String prefix, String uri)
            throws SAXException {
        for (ContentHandler handler : handlers) {
            handler.startPrefixMapping(prefix, uri);
        }
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        for (ContentHandler handler : handlers) {
            handler.endPrefixMapping(prefix);
        }
    }

    @Override
    public void processingInstruction(String target, String data)
            throws SAXException {
        for (ContentHandler handler : handlers) {
            handler.processingInstruction(target, data);
        }
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        for (ContentHandler handler : handlers) {
            handler.setDocumentLocator(locator);
        }
    }

    @Override
    public void startDocument() throws SAXException {
        for (ContentHandler handler : handlers) {
            handler.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        for (ContentHandler handler : handlers) {
            handler.endDocument();
        }
    }

    @Override
    public void startElement(
            String uri, String localName, String name, Attributes atts)
            throws SAXException {
        for (ContentHandler handler : handlers) {
            handler.startElement(uri, localName, name, atts);
        }
    }

    @Override
    public void endElement(String uri, String localName, String name)
            throws SAXException {
        for (ContentHandler handler : handlers) {
            handler.endElement(uri, localName, name);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        for (ContentHandler handler : handlers) {
            handler.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        for (ContentHandler handler : handlers) {
            handler.ignorableWhitespace(ch, start, length);
        }
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        for (ContentHandler handler : handlers) {
            handler.skippedEntity(name);
        }
    }

}
