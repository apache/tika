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
 * Decorator base class for the {@link ContentHandler} interface. This class
 * simply delegates all SAX events calls to an underlying decorated handler
 * instance. Subclasses can provide extra decoration by overriding one or more
 * of the SAX event methods.
 */
public class ContentHandlerDecorator extends DefaultHandler {

    /**
     * Decorated SAX event handler.
     */
    private final ContentHandler handler;

    /**
     * Creates a decorator for the given SAX event handler.
     *
     * @param handler SAX event handler to be decorated
     */
    public ContentHandlerDecorator(ContentHandler handler) {
        this.handler = handler;
    }

    public void startPrefixMapping(String prefix, String uri)
            throws SAXException {
        handler.startPrefixMapping(prefix, uri);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        handler.endPrefixMapping(prefix);
    }

    public void processingInstruction(String target, String data)
            throws SAXException {
        handler.processingInstruction(target, data);
    }

    public void setDocumentLocator(Locator locator) {
        handler.setDocumentLocator(locator);
    }

    public void startDocument() throws SAXException {
        handler.startDocument();
    }

    public void endDocument() throws SAXException {
        handler.endDocument();
    }

    public void startElement(String uri, String localName, String name,
            Attributes atts) throws SAXException {
        handler.startElement(uri, localName, name, atts);
    }

    public void endElement(String uri, String localName, String name)
            throws SAXException {
        handler.endElement(uri, localName, name);
    }

    public void characters(char[] ch, int start, int length)
            throws SAXException {
        handler.characters(ch, start, length);
    }

    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        handler.ignorableWhitespace(ch, start, length);
    }

    public void skippedEntity(String name) throws SAXException {
        handler.skippedEntity(name);
    }

    public String toString() {
        return handler.toString();
    }

}
