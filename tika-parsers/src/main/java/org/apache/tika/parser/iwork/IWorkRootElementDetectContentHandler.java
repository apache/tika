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
package org.apache.tika.parser.iwork;

import org.apache.tika.metadata.Metadata;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * A handler that detects based on the root element which encapsulated handler to use.
 *
 * If during parsing no handler can be matched to the rootElement an exception is thrown.
 */
class IWorkRootElementDetectContentHandler extends DefaultHandler {

    private final Map<String, DefaultHandler> handlers = new HashMap<String, DefaultHandler>();
    private final Map<String, String> contentTypes = new HashMap<String, String>();

    private DefaultHandler chosenHandler;
    private final Metadata metadata;

    IWorkRootElementDetectContentHandler(Metadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Adds a handler to this auto detect parser that handles parsing when the specified rootElement is encountered.
     *
     * @param rootElement The root element the identifies the specified handler
     * @param handler The handler that does the actual parsing of the auto detected content
     * @param contentType The content type that belongs to the auto detected content
     */
    public void addHandler(String rootElement, DefaultHandler handler, String contentType) {
        handlers.put(rootElement, handler);
        contentTypes.put(rootElement, contentType);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (chosenHandler != null) {
            chosenHandler.startElement(uri, localName, qName, attributes);
            return;
        }

        DefaultHandler candidateHandler = handlers.get(qName);
        if (candidateHandler != null) {
            chosenHandler = candidateHandler;
            if (metadata.get(Metadata.CONTENT_TYPE) == null) {
                metadata.add(Metadata.CONTENT_TYPE, contentTypes.get(qName));
            }
            chosenHandler.startElement(uri, localName, qName, attributes);
        } else {
            throw new RuntimeException("Could not find handler to parse document based on root element");
        }
    }

    @Override
    public void endDocument() throws SAXException {
        chosenHandler.endDocument();
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        chosenHandler.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        chosenHandler.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        chosenHandler.ignorableWhitespace(ch, start, length);
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        chosenHandler.processingInstruction(target, data);
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        chosenHandler.skippedEntity(name);
    }

    @Override
    public void warning(SAXParseException e) throws SAXException {
        chosenHandler.warning(e);
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        chosenHandler.error(e);
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        chosenHandler.fatalError(e);
    }
}
