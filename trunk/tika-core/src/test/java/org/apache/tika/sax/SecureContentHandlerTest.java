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

import org.apache.tika.io.CountingInputStream;
import org.apache.tika.io.NullInputStream;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import junit.framework.TestCase;

/**
 * Tests for the {@link SecureContentHandler} class.
 */
public class SecureContentHandlerTest extends TestCase {

    private static final int MANY_BYTES = 2000000;

    private CountingInputStream stream;

    private SecureContentHandler handler;

    protected void setUp() {
        stream = new CountingInputStream(new NullInputStream(MANY_BYTES));
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

}
