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

/**
 * Content handler decorator that forwards the received SAX events to two
 * underlying content handlers.
 */
public class TeeContentHandler extends ContentHandlerDecorator {

    private final ContentHandler branch;

    public TeeContentHandler(ContentHandler handler, ContentHandler branch) {
        super(handler);
        this.branch = branch;
    }

    public void startPrefixMapping(String prefix, String uri)
            throws SAXException {
        super.startPrefixMapping(prefix, uri);
        branch.startPrefixMapping(prefix, uri);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        super.endPrefixMapping(prefix);
        branch.endPrefixMapping(prefix);
    }

    public void processingInstruction(String target, String data)
            throws SAXException {
        super.processingInstruction(target, data);
        branch.processingInstruction(target, data);
    }

    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        branch.setDocumentLocator(locator);
    }

    public void startDocument() throws SAXException {
        super.startDocument();
        branch.startDocument();
    }

    public void endDocument() throws SAXException {
        super.endDocument();
        branch.endDocument();
    }

    public void startElement(String uri, String localName, String name,
            Attributes atts) throws SAXException {
        super.startElement(uri, localName, name, atts);
        branch.startElement(uri, localName, name, atts);
    }

    public void endElement(String uri, String localName, String name)
            throws SAXException {
        super.endElement(uri, localName, name);
        branch.endElement(uri, localName, name);
    }

    public void characters(char[] ch, int start, int length)
            throws SAXException {
        super.characters(ch, start, length);
        branch.characters(ch, start, length);
    }

    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        super.ignorableWhitespace(ch, start, length);
        branch.ignorableWhitespace(ch, start, length);
    }

    public void skippedEntity(String name) throws SAXException {
        super.skippedEntity(name);
        branch.skippedEntity(name);
    }

}
