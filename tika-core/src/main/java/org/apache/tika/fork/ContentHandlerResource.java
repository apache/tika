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

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

class ContentHandlerResource implements ForkResource {

    private final ContentHandler handler;

    public ContentHandlerResource(ContentHandler handler) {
        this.handler = handler;
    }

    public Throwable process(DataInputStream input, DataOutputStream output)
            throws IOException {
        try {
            internalProcess(input);
            return null;
        } catch (SAXException e) {
            return e;
        }
    }

    private void internalProcess(DataInputStream input)
            throws IOException, SAXException {
        int type = input.readUnsignedByte();
        if (type == ContentHandlerProxy.START_DOCUMENT) {
            handler.startDocument();
        } else if (type == ContentHandlerProxy.END_DOCUMENT) {
            handler.endDocument();
        } else if (type == ContentHandlerProxy.START_PREFIX_MAPPING) {
            handler.startPrefixMapping(readString(input), readString(input));
        } else if (type == ContentHandlerProxy.END_PREFIX_MAPPING) {
            handler.endPrefixMapping(readString(input));
        } else if (type == ContentHandlerProxy.START_ELEMENT) {
            String uri = readString(input);
            String localName = readString(input);
            String qName = readString(input);
            AttributesImpl atts = null;
            int n = input.readInt();
            if (n >= 0) {
                atts = new AttributesImpl();
                for (int i = 0; i < n; i++) {
                    atts.addAttribute(
                            readString(input), readString(input),
                            readString(input), readString(input),
                            readString(input));
                }
            }
            handler.startElement(uri, localName, qName, atts);
        } else if (type == ContentHandlerProxy.END_ELEMENT) {
            String uri = readString(input);
            String localName = readString(input);
            String qName = readString(input);
            handler.endElement(uri, localName, qName);
        } else if (type == ContentHandlerProxy.CHARACTERS) {
            char[] ch = readCharacters(input);
            handler.characters(ch, 0, ch.length);
        } else if (type == ContentHandlerProxy.IGNORABLE_WHITESPACE) {
            char[] ch = readCharacters(input);
            handler.characters(ch, 0, ch.length);
        } else if (type == ContentHandlerProxy.PROCESSING_INSTRUCTION) {
            handler.processingInstruction(readString(input), readString(input));
        } else if (type == ContentHandlerProxy.SKIPPED_ENTITY) {
            handler.skippedEntity(readString(input));
        }
    }

    private String readString(DataInputStream input) throws IOException {
        if (input.readBoolean()) {
            return input.readUTF();
        } else {
            return null;
        }
    }

    private char[] readCharacters(DataInputStream input) throws IOException {
        int n = input.readInt();
        char[] ch = new char[n];
        for (int i = 0; i < n; i++) {
            ch[i] = input.readChar();
        }
        return ch;
    }

}
