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
package org.apache.tika.fork;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

class ContentHandlerProxy implements ContentHandler, ForkProxy {

    public static final int START_DOCUMENT         =  1;
    public static final int END_DOCUMENT           =  2;
    public static final int START_PREFIX_MAPPING   =  3;
    public static final int END_PREFIX_MAPPING     =  4;
    public static final int START_ELEMENT          =  5;
    public static final int END_ELEMENT            =  6;
    public static final int CHARACTERS             =  7;
    public static final int IGNORABLE_WHITESPACE   =  8;
    public static final int PROCESSING_INSTRUCTION =  9;
    public static final int SKIPPED_ENTITY         = 10;

    /** Serial version UID */
    private static final long serialVersionUID = 737511106054617524L;

    private final int resource;

    private transient DataOutputStream output;

    public ContentHandlerProxy(int resource) {
        this.resource = resource;
    }

    public void init(DataInputStream input, DataOutputStream output) {
        this.output = output;
    }

    private void sendRequest(int type) throws SAXException {
        try {
            output.writeByte(ForkServer.RESOURCE);
            output.writeByte(resource);
            output.writeByte(type);
        } catch (IOException e) {
            throw new SAXException("Unexpected fork proxy problem", e);
        }
    }

    private void sendString(String string) throws SAXException {
        try {
            if (string != null) {
                output.writeBoolean(true);
                output.writeUTF(string);
            } else {
                output.writeBoolean(false);
            }
        } catch (IOException e) {
            throw new SAXException("Unexpected fork proxy problem", e);
        }
    }

    private void sendCharacters(char[] ch, int start, int length)
            throws SAXException {
        try {
            output.writeInt(length);
            for (int i = 0; i < length; i++) {
                output.writeChar(ch[start + i]);
            }
        } catch (IOException e) {
            throw new SAXException("Unexpected fork proxy problem", e);
        }
    }

    private void doneSending() throws SAXException {
        try {
            output.flush();
        } catch (IOException e) {
            throw new SAXException("Unexpected fork proxy problem", e);
        }
    }

    public void setDocumentLocator(Locator locator) {
        // skip
    }

    public void startDocument() throws SAXException {
        sendRequest(START_DOCUMENT);
        doneSending();
    }

    public void endDocument() throws SAXException {
        sendRequest(END_DOCUMENT);
        doneSending();
    }

    public void startPrefixMapping(String prefix, String uri)
            throws SAXException {
        sendRequest(START_PREFIX_MAPPING);
        sendString(prefix);
        sendString(uri);
        doneSending();
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        sendRequest(END_PREFIX_MAPPING);
        sendString(prefix);
        doneSending();
    }

    public void startElement(
            String uri, String localName, String qName, Attributes atts)
            throws SAXException {
        sendRequest(START_ELEMENT);
        sendString(uri);
        sendString(localName);
        sendString(qName);
        int n = -1;
        if (atts != null) {
            n = atts.getLength();
        }
        try {
            output.writeInt(n);
        } catch (IOException e) {
            throw new SAXException("Unexpected fork proxy problem", e);
        }
        for (int i = 0; i < n; i++) {
            sendString(atts.getURI(i));
            sendString(atts.getLocalName(i));
            sendString(atts.getQName(i));
            sendString(atts.getType(i));
            sendString(atts.getValue(i));
        }
        doneSending();
    }

    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        sendRequest(END_ELEMENT);
        sendString(uri);
        sendString(localName);
        sendString(qName);
        doneSending();
    }

    public void characters(char[] ch, int start, int length)
            throws SAXException {
        sendRequest(CHARACTERS);
        sendCharacters(ch, start, length);
        doneSending();
    }

    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        sendRequest(IGNORABLE_WHITESPACE);
        sendCharacters(ch, start, length);
        doneSending();
    }

    public void processingInstruction(String target, String data)
            throws SAXException {
        sendRequest(PROCESSING_INSTRUCTION);
        sendString(target);
        sendString(data);
        doneSending();
    }

    public void skippedEntity(String name) throws SAXException {
        sendRequest(SKIPPED_ENTITY);
        sendString(name);
        doneSending();
    }

}
