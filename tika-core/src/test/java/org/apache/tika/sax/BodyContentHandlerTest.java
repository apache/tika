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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;

/**
 * Test cases for the {@link BodyContentHandler} class.
 */
public class BodyContentHandlerTest extends TestCase {

    /**
     * Test that the conversion to an {@link OutputStream} doesn't leave
     * characters unflushed in an internal buffer.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-179">TIKA-179</a>
     */
    public void testOutputStream() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                new BodyContentHandler(buffer), new Metadata());
        xhtml.startDocument();
        xhtml.element("p", "Test text");
        xhtml.endDocument();

        assertEquals("Test text\n", buffer.toString());
    }

}
