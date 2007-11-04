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

public class MimeTypesTest extends TestCase {

    private MimeTypes types;

    protected void setUp() {
        types = new MimeTypes();
    }

    public void testForName() throws MimeTypeException {
        assertNotNull(types.forName("text/plain"));
        assertEquals("text/plain", types.forName("text/plain").getName());
        assertEquals("text/plain", types.forName("TEXT/PLAIN").getName());

        try {
            types.forName("invalid");
            fail("MimeTypeException not thrown on invalid type name");
        } catch (MimeTypeException e) {
            // expected
        }
    }

    public void addAlias() throws MimeTypeException {
        types.addAlias(types.forName("text/plain"), "foo/bar");
        assertNotNull(types.forName("foo/bar"));
        assertEquals("text/plain", types.forName("foo/bar").getName());

        try {
            types.addAlias(types.forName("text/plain"), "invalid");
            fail("MimeTypeException not thrown on invalid alias name");
        } catch (MimeTypeException e) {
            // expected
        }
    }
}
