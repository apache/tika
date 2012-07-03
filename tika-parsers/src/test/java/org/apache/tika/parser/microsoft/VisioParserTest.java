/**
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
package org.apache.tika.parser.microsoft;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import junit.framework.TestCase;

public class VisioParserTest extends TestCase {

    public void testVisioParser() throws Exception {
        InputStream input = VisioParserTest.class.getResourceAsStream(
                "/test-documents/testVISIO.vsd");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            assertEquals(
                    "application/vnd.visio",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Hogwarts", metadata.get(TikaCoreProperties.CREATOR));
            String content = handler.toString();
            assertTrue(content.contains("Some random text, on a page"));
        } finally {
            input.close();
        }
    }

}
