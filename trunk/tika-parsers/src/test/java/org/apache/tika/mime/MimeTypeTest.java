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

import junit.framework.TestCase;

public class MimeTypeTest extends TestCase {

    private MimeTypes types;
    private MimeType text;

    protected void setUp() throws MimeTypeException {
        types = new MimeTypes();
        text = types.forName("text/plain");
    }

    /** Test MimeType constructor */
    public void testConstrctor() {
        // Missing name
        try {
            new MimeType(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }
    }

    public void testIsValidName() {
        assertTrue(MimeType.isValid("application/octet-stream"));
        assertTrue(MimeType.isValid("text/plain"));
        assertTrue(MimeType.isValid("foo/bar"));
        assertTrue(MimeType.isValid("a/b"));

        assertFalse(MimeType.isValid("application"));
        assertFalse(MimeType.isValid("application/"));
        assertFalse(MimeType.isValid("/"));
        assertFalse(MimeType.isValid("/octet-stream"));
        assertFalse(MimeType.isValid("application//octet-stream"));
        assertFalse(MimeType.isValid("application/octet=stream"));
        assertFalse(MimeType.isValid("application/\u00f6ctet-stream"));
        assertFalse(MimeType.isValid("text/plain;"));
        assertFalse(MimeType.isValid("text/plain; charset=UTF-8"));
        try {
            MimeType.isValid(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }
    }

    /** Test MimeType setDescription() */
    public void testSetDescription() {
        try {
            text.setDescription(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }
    }

}
