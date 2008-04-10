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
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import junit.framework.TestCase;

public class WordParserTest extends TestCase {

    public void testWordParser() throws Exception {
        InputStream input = WordParserTest.class.getResourceAsStream(
                "/test-documents/testWORD.doc");
        try {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata);

            assertEquals(
                    "application/msword",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Sample Word Document", metadata.get(Metadata.TITLE));
            assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
            assertTrue(handler.toString().contains("Sample Word Document"));
        } finally {
            input.close();
        }
    }

}
