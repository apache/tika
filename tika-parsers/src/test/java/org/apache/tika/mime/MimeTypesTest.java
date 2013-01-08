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

    public void testRegisteredMimes() throws MimeTypeException {
        String dummy = "text/xxxxx";
        assertEquals(text, types.getRegisteredMimeType("text/plain"));
        assertNull(types.getRegisteredMimeType(dummy));
        assertNotNull(types.forName(dummy));
        assertEquals(dummy, types.forName("text/xxxxx").getType().toString());
        assertEquals(dummy, types.getRegisteredMimeType("text/xxxxx").getType().toString());
        
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

}
