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
package org.apache.tika.sax;

import java.io.IOException;

import junit.framework.TestCase;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.NullInputStream;
import org.apache.tika.io.TikaInputStream;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Tests for the {@link SecureContentHandler} class.
 */
public class SecureContentHandlerTest extends TestCase {

    private static final int MANY_BYTES = 2000000;

    private TikaInputStream stream;

    private SecureContentHandler handler;

    protected void setUp() {
        stream = TikaInputStream.get(new NullInputStream(MANY_BYTES));
        handler = new SecureContentHandler(new DefaultHandler(), stream);
    }

    public void testZeroCharactersPerByte() throws IOException {
        try {
            char[] ch = new char[] { 'x' };
            for (int i = 0; i < MANY_BYTES; i++) {
                stream.read();
            }
            handler.characters(ch, 0, 1);
        } catch (SAXException e) {
            fail("Unexpected SAXException");
        }
    }

    public void testOneCharacterPerByte() throws IOException {
        try {
            char[] ch = new char[1];
            for (int i = 0; i < MANY_BYTES; i++) {
                stream.read();
                handler.characters(ch, 0, ch.length);
            }
        } catch (SAXException e) {
            fail("Unexpected SAXException");
        }
    }

    public void testTenCharactersPerByte() throws IOException {
        try {
            char[] ch = new char[10];
            for (int i = 0; i < MANY_BYTES; i++) {
                stream.read();
                handler.characters(ch, 0, ch.length);
            }
        } catch (SAXException e) {
            fail("Unexpected SAXException");
        }
    }

    public void testManyCharactersPerByte() throws IOException {
        try {
            char[] ch = new char[1000];
            for (int i = 0; i < MANY_BYTES; i++) {
                stream.read();
                handler.characters(ch, 0, ch.length);
            }
            fail("Expected SAXException not thrown");
        } catch (SAXException e) {
            // expected
        }
    }

    public void testSomeCharactersWithoutInput() throws IOException {
        try {
            char[] ch = new char[100];
            for (int i = 0; i < 100; i++) {
                handler.characters(ch, 0, ch.length);
            }
        } catch (SAXException e) {
            fail("Unexpected SAXException");
        }
    }

    public void testManyCharactersWithoutInput() throws IOException {
        try {
            char[] ch = new char[100];
            for (int i = 0; i < 20000; i++) {
                handler.characters(ch, 0, ch.length);
            }
            fail("Expected SAXException not thrown");
        } catch (SAXException e) {
            // expected
        }
    }

    public void testNestedElements() throws SAXException {
        for (int i = 1; i < handler.getMaximumDepth(); i++) {
            handler.startElement("", "x", "x", new AttributesImpl());
        }
        try {
            handler.startElement("", "x", "x", new AttributesImpl());
            fail("Nested XML element limit exceeded");
        } catch (SAXException e) {
            try {
                handler.throwIfCauseOf(e);
                throw e;
            } catch (TikaException expected) {
            }
        }
    }

    public void testNestedEntries() throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "class", "class", "CDATA", "package-entry");
        for (int i = 1; i < handler.getMaximumPackageEntryDepth(); i++) {
            handler.startElement("", "div", "div", atts);
        }
        try {
            handler.startElement("", "div", "div", atts);
            fail("Nested XML element limit exceeded");
        } catch (SAXException e) {
            try {
                handler.throwIfCauseOf(e);
                throw e;
            } catch (TikaException expected) {
            }
        }
    }

}
