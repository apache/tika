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

    @Override
    public void startPrefixMapping(String prefix, String uri)
            throws SAXException {
        try {
            handler.startPrefixMapping(prefix, uri);
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        try {
            handler.endPrefixMapping(prefix);
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public void processingInstruction(String target, String data)
            throws SAXException {
        try {
            handler.processingInstruction(target, data);
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        handler.setDocumentLocator(locator);
    }

    @Override
    public void startDocument() throws SAXException {
        try {
            handler.startDocument();
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            handler.endDocument();
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public void startElement(
            String uri, String localName, String name, Attributes atts)
            throws SAXException {
        try {
            handler.startElement(uri, localName, name, atts);
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public void endElement(String uri, String localName, String name)
            throws SAXException {
        try {
            handler.endElement(uri, localName, name);
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        try {
            handler.characters(ch, start, length);
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        try {
            handler.ignorableWhitespace(ch, start, length);
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        try {
            handler.skippedEntity(name);
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public String toString() {
        return handler.toString();
    }

    /**
     * Handle any exceptions thrown by methods in this class. This method
     * provides a single place to implement custom exception handling. The
     * default behaviour is simply to re-throw the given exception, but
     * subclasses can also provide alternative ways of handling the situation.
     *
     * @param exception the exception that was thrown
     * @throws SAXException the exception (if any) thrown to the client
     */
    protected void handleException(SAXException exception) throws SAXException {
        throw exception;
    }

}
