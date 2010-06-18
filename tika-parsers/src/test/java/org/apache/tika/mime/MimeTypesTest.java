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

import static org.apache.tika.mime.MediaType.OCTET_STREAM;
import static org.apache.tika.mime.MediaType.TEXT_PLAIN;

import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

public class MimeTypesTest extends TestCase {

    private MimeTypes types;

    private MediaTypeRegistry registry;

    private MimeType binary;

    private MimeType text;

    private MimeType html;

    protected void setUp() throws MimeTypeException {
        types = new MimeTypes();
        registry = types.getMediaTypeRegistry();
        binary = types.forName("application/octet-stream");
        text = types.forName("text/plain");
        types.addAlias(text, MediaType.parse("text/x-plain"));
        html = types.forName("text/html");
        types.setSuperType(html, TEXT_PLAIN);
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

    public void testSuperType() throws MimeTypeException {
        assertNull(registry.getSupertype(OCTET_STREAM));
        assertEquals(OCTET_STREAM, registry.getSupertype(TEXT_PLAIN));
        assertEquals(TEXT_PLAIN, registry.getSupertype(html.getType()));
   }

    public void testIsDescendantOf() {
        assertFalse(registry.isSpecializationOf(OCTET_STREAM, OCTET_STREAM));
        assertFalse(registry.isSpecializationOf(TEXT_PLAIN, TEXT_PLAIN));
        assertFalse(registry.isSpecializationOf(html.getType(), html.getType()));

        assertTrue(registry.isSpecializationOf(html.getType(), OCTET_STREAM));
        assertFalse(registry.isSpecializationOf(OCTET_STREAM, html.getType()));

        assertTrue(registry.isSpecializationOf(html.getType(), TEXT_PLAIN));
        assertFalse(registry.isSpecializationOf(TEXT_PLAIN, html.getType()));

        assertTrue(registry.isSpecializationOf(TEXT_PLAIN, OCTET_STREAM));
        assertFalse(registry.isSpecializationOf(OCTET_STREAM, TEXT_PLAIN));
    }

    public void testCompareTo() {
        assertTrue(binary.compareTo(binary) == 0);
        assertTrue(binary.compareTo(text) != 0);
        assertTrue(binary.compareTo(html) != 0);

        assertTrue(text.compareTo(binary) != 0);
        assertTrue(text.compareTo(text) == 0);
        assertTrue(text.compareTo(html) != 0);

        assertTrue(html.compareTo(binary) != 0);
        assertTrue(html.compareTo(text) != 0);
        assertTrue(html.compareTo(html) == 0);
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
