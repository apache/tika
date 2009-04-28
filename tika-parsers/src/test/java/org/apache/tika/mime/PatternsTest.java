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

import junit.framework.TestCase;

/**
 * Test case for {@link Patterns}.
 *
 * @version $Id$
 */
public class PatternsTest extends TestCase {

    private Patterns patterns;
    private MimeTypes types;
    private MimeType text;

    protected void setUp() throws MimeTypeException {
        patterns = new Patterns();
        types = new MimeTypes();
        text = types.forName("text/plain");
    }

    /** Test add() */
    public void testAdd() throws MimeTypeException {
        try {
            patterns.add(null, text);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }
        try {
            patterns.add("", null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }
        try {
            patterns.add(null, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }
    }

    /** Test matches() */
    public void testMatches() {
        try {
            patterns.matches(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected result
        }
    }
}
