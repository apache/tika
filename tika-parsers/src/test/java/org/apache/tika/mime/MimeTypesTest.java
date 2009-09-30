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
package org.apache.tika.mime;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;

import junit.framework.TestCase;

public class MimeTypesTest extends TestCase {

    private MimeTypes types;

    private MimeType binary;

    private MimeType text;

    private MimeType html;

    protected void setUp() throws MimeTypeException {
        types = new MimeTypes();
        binary = types.forName("application/octet-stream");
        text = types.forName("text/plain");
        text.addAlias("text/x-plain");
        html = types.forName("text/html");
        html.setSuperType(text);
    }

    public void testForName() throws MimeTypeException {
        assertEquals(text, types.forName("text/plain"));
        assertEquals(text, types.forName("TEXT/PLAIN"));

        try {
            types.forName("invalid");
            fail("MimeTypeException not thrown on invalid type name");
        } catch (MimeTypeException e) {
            // expected
        }
    }

    public void testAddAlias() throws MimeTypeException {
        assertEquals(text, types.forName("text/x-plain"));
        try {
            text.addAlias("invalid");
            fail("MimeTypeException not thrown on invalid alias name");
        } catch (MimeTypeException e) {
            // expected
        }
    }

    public void testSuperType() throws MimeTypeException {
        assertNull(binary.getSuperType());
        assertEquals(binary, text.getSuperType());
        assertEquals(text, html.getSuperType());
   }

    public void testSubTypes() {
        assertEquals(2, binary.getSubTypes().size());
        Iterator<MimeType> iter = binary.getSubTypes().iterator();
        String[] typeNames = new String[2];
        typeNames[0] = iter.next().getName();
        typeNames[1] = iter.next().getName();
        Arrays.sort(typeNames);
        assertEquals("application/xml", typeNames[0]);
        assertEquals("text/plain", typeNames[1]);

        assertEquals(1, text.getSubTypes().size());
        assertEquals(
                "text/html",
                text.getSubTypes().iterator().next().getName());
        
        assertEquals(0, html.getSubTypes().size());
    }

    public void testIsDescendantOf() {
        assertFalse(binary.isDescendantOf(binary));
        assertFalse(text.isDescendantOf(text));
        assertFalse(html.isDescendantOf(html));

        assertTrue(text.isDescendantOf(binary));
        assertFalse(binary.isDescendantOf(text));
        
        assertTrue(html.isDescendantOf(binary));
        assertFalse(binary.isDescendantOf(html));

        assertTrue(html.isDescendantOf(text));
        assertFalse(text.isDescendantOf(html));

        try {
            binary.isDescendantOf(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }
    }

    public void testCompareTo() {
        assertTrue(binary.compareTo(binary) == 0);
        assertTrue(binary.compareTo(text) < 0);
        assertTrue(binary.compareTo(html) < 0);

        assertTrue(text.compareTo(binary) > 0);
        assertTrue(text.compareTo(text) == 0);
        assertTrue(text.compareTo(html) < 0);

        assertTrue(html.compareTo(binary) > 0);
        assertTrue(html.compareTo(text) > 0);
        assertTrue(html.compareTo(html) == 0);

        try {
            binary.compareTo(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }
    }

    /** Test getMimeType(byte[]) */
    public void testGetMimeType_byteArray() {
        try {
            types.getMimeType((byte[])null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }

        // Plain text detection
        assertText(new byte[] { (byte) 0xFF, (byte) 0xFE });
        assertText(new byte[] { (byte) 0xFF, (byte) 0xFE });
        assertText(new byte[] { (byte) 0xEF, (byte) 0xFB, (byte) 0xBF });
        assertText(new byte[] { 'a', 'b', 'c' });
        assertText(new byte[] { '\t', '\r', '\n', 0x0C, 0x1B });
        assertNotText(new byte[] { '\t', '\r', '\n', 0x0E, 0x1C });
    }

    private void assertText(byte[] prefix) {
        assertMagic("text/plain", prefix);
    }

    private void assertNotText(byte[] prefix) {
        assertMagic("application/octet-stream", prefix);
    }

    private void assertMagic(String expected, byte[] prefix) {
        MimeType type = types.getMimeType(prefix);
        assertNotNull(type);
        assertEquals(expected, type.getName());
    }

    /** Test getMimeType(InputStream) */
    public void testGetMimeType_InputStream() throws IOException {
        try {
            types.getMimeType((InputStream)null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }
    }

}
