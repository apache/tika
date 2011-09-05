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

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import junit.framework.TestCase;

/**
 * Unit tests for the {@link SafeContentHandler} class.
 */
public class SafeContentHandlerTest extends TestCase {

    private ContentHandler output;

    private ContentHandler safe;

    protected void setUp() {
        output = new WriteOutContentHandler();
        safe = new SafeContentHandler(output);
    }

    public void testEmptyInput() throws SAXException {
        safe.characters(new char[0], 0, 0);
        safe.ignorableWhitespace(new char[0], 0, 0);
        assertEquals("", output.toString());
    }

    public void testNormalCharacters() throws SAXException {
        safe.characters("abc".toCharArray(), 0, 3);
        assertEquals("abc", output.toString());
    }

    public void testNormalWhitespace() throws SAXException {
        safe.ignorableWhitespace("abc".toCharArray(), 0, 3);
        assertEquals("abc", output.toString());
    }

    public void testInvalidCharacters() throws SAXException {
        safe.characters("ab\u0007".toCharArray(), 0, 3);
        safe.characters("a\u000Bc".toCharArray(), 0, 3);
        safe.characters("\u0019bc".toCharArray(), 0, 3);
        assertEquals("ab\ufffda\ufffdc\ufffdbc", output.toString());
    }

    public void testInvalidWhitespace() throws SAXException {
        safe.ignorableWhitespace("ab\u0000".toCharArray(), 0, 3);
        safe.ignorableWhitespace("a\u0001c".toCharArray(), 0, 3);
        safe.ignorableWhitespace("\u0002bc".toCharArray(), 0, 3);
        assertEquals("ab\ufffda\ufffdc\ufffdbc", output.toString());
    }

    public void testInvalidSurrogates() throws SAXException {
        safe.ignorableWhitespace("\udb00\ubfff".toCharArray(), 0, 2);
        assertEquals("\ufffd\ubfff", output.toString());
    }

}
