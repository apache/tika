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
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class DWGParserTest extends TestCase {
    public void testDWG2000Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2000.dwg");
        testParserAlt(input);
    }

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

    public void testDWGMechParser() throws Exception {
        String[] types = new String[] {
              "6", "2004", "2004DX", "2005", "2006",
              "2007", "2008", "2009", "2010", "2011"
        };
        for (String type : types) {
           InputStream input = DWGParserTest.class.getResourceAsStream(
                   "/test-documents/testDWGmech"+type+".dwg");
           testParserAlt(input);
        }
    }

    private void testParser(InputStream input) throws Exception {
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(input, handler, metadata);

            assertEquals("image/vnd.dwg", metadata.get(Metadata.CONTENT_TYPE));

            assertEquals("The quick brown fox jumps over the lazy dog", 
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog",
                    metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("Gym class featuring a brown fox and lazy dog",
                    metadata.get(Metadata.SUBJECT));
            assertEquals("Nevin Nollop",
                    metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("Pangram, fox, dog",
                    metadata.get(TikaCoreProperties.KEYWORDS));
            assertEquals("Lorem ipsum",
                    metadata.get(TikaCoreProperties.COMMENTS).substring(0,11));
            assertEquals("http://www.alfresco.com",
                    metadata.get(TikaCoreProperties.RELATION));
            
            // Check some of the old style metadata too
            assertEquals("The quick brown fox jumps over the lazy dog", 
                  metadata.get(Metadata.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog",
                  metadata.get(Metadata.SUBJECT));

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
            
            assertNull(metadata.get(TikaCoreProperties.TITLE));
            assertNull(metadata.get(TikaCoreProperties.DESCRIPTION));
            assertNull(metadata.get(Metadata.SUBJECT));
            assertNull(metadata.get(TikaCoreProperties.CREATOR));
            assertNull(metadata.get(TikaCoreProperties.KEYWORDS));
            assertNull(metadata.get(TikaCoreProperties.COMMENTS));
            assertNull(metadata.get(TikaCoreProperties.RELATION));

            String content = handler.toString();
            assertTrue(content.contains(""));
        } finally {
            input.close();
        }
    }

    private void testParserAlt(InputStream input) throws Exception {
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(input, handler, metadata);

            assertEquals("image/vnd.dwg", metadata.get(Metadata.CONTENT_TYPE));

            assertEquals("Test Title", 
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Test Subject",
                    metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("Test Subject",
                    metadata.get(Metadata.SUBJECT));
            assertEquals("My Author",
                    metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("My keyword1, MyKeyword2",
                    metadata.get(TikaCoreProperties.KEYWORDS));
            assertEquals("This is a comment",
                    metadata.get(TikaCoreProperties.COMMENTS));
            assertEquals("bejanpol",
                    metadata.get(TikaCoreProperties.MODIFIER));
            assertEquals("bejanpol",
                    metadata.get(Metadata.LAST_AUTHOR));
            assertEquals("http://mycompany/drawings",
                    metadata.get(TikaCoreProperties.RELATION));
            assertEquals("MyCustomPropertyValue",
                  metadata.get("MyCustomProperty"));

            String content = handler.toString();
            assertTrue(content.contains("This is a comment"));
            assertTrue(content.contains("mycompany"));
        } finally {
            input.close();
        }
    }
}
