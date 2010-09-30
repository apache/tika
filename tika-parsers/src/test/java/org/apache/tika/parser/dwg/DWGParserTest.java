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
package org.apache.tika.parser.dwg;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class DWGParserTest extends TestCase {

    public void testDWG2004Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2004.dwg");
        testParser(input);
    }

    public void testDWG2004ParserNoHeaderAddress() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2004_no_header.dwg");
        testParserNoHeader(input);
    }

    public void testDWG2007Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2007.dwg");
        testParser(input);
    }

    public void testDWG2010Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2010.dwg");
        testParser(input);
    }

    private void testParser(InputStream input) throws Exception {
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(input, handler, metadata);

            assertEquals("image/vnd.dwg", metadata.get(Metadata.CONTENT_TYPE));

            assertEquals("The quick brown fox jumps over the lazy dog", 
                    metadata.get(Metadata.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog",
                    metadata.get(Metadata.SUBJECT));
            assertEquals("Nevin Nollop",
                    metadata.get(Metadata.AUTHOR));
            assertEquals("Pangram, fox, dog",
                    metadata.get(Metadata.KEYWORDS));
            assertEquals("Lorem ipsum",
                    metadata.get(Metadata.COMMENTS).substring(0,11));
            assertEquals("http://www.alfresco.com",
                    metadata.get(Metadata.RELATION));

            String content = handler.toString();
            assertTrue(content.contains("The quick brown fox jumps over the lazy dog"));
            assertTrue(content.contains("Gym class"));
            assertTrue(content.contains("www.alfresco.com"));
        } finally {
            input.close();
        }
    }

    private void testParserNoHeader(InputStream input) throws Exception {
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(input, handler, metadata);

            assertEquals("image/vnd.dwg", metadata.get(Metadata.CONTENT_TYPE));
            
            assertNull(metadata.get(Metadata.TITLE));
            assertNull(metadata.get(Metadata.SUBJECT));
            assertNull(metadata.get(Metadata.AUTHOR));
            assertNull(metadata.get(Metadata.KEYWORDS));
            assertNull(metadata.get(Metadata.COMMENTS));
            assertNull(metadata.get(Metadata.RELATION));

            String content = handler.toString();
            assertTrue(content.contains(""));
        } finally {
            input.close();
        }
    }
}
