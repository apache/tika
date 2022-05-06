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

import static org.apache.tika.TikaTest.assertContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.BodyContentHandler;

public class DWGParserTest extends TikaTest {

    @Test
    public void testDWG2000Parser() throws Exception {
        InputStream input =
                DWGParserTest.class.getResourceAsStream("/test-documents/testDWG2000.dwg");
        testParserAlt(input);
    }

    @Test
    public void testDWG2004Parser() throws Exception {
        InputStream input =
                DWGParserTest.class.getResourceAsStream("/test-documents/testDWG2004.dwg");
        testParser(input);
    }

    @Test
    public void testDWG2004ParserNoHeaderAddress() throws Exception {
        InputStream input = DWGParserTest.class
                .getResourceAsStream("/test-documents/testDWG2004_no_header.dwg");
        testParserNoHeader(input);
    }

    @Test
    public void testDWG2007Parser() throws Exception {
        InputStream input =
                DWGParserTest.class.getResourceAsStream("/test-documents/testDWG2007.dwg");
        testParser(input);
    }

    @Test
    public void testDWG2010Parser() throws Exception {
        InputStream input =
                DWGParserTest.class.getResourceAsStream("/test-documents/testDWG2010.dwg");
        testParser(input);
    }

    @Test
    public void testDWG2010CustomPropertiesParser() throws Exception {
        // Check that standard parsing works
        InputStream testInput = DWGParserTest.class
                .getResourceAsStream("/test-documents/testDWG2010_custom_props.dwg");
        testParser(testInput);

        // Check that custom properties with alternate padding work
        try (InputStream input = DWGParserTest.class
                .getResourceAsStream("/test-documents/testDWG2010_custom_props.dwg")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(input, handler, metadata, null);

            assertEquals("valueforcustomprop1",
                    metadata.get(DWGParser.DWG_CUSTOM_META_PREFIX + "customprop1"));
            assertEquals("valueforcustomprop2",
                    metadata.get(DWGParser.DWG_CUSTOM_META_PREFIX + "customprop2"));
        }
    }

    @Test
    public void testDWGMechParser() throws Exception {
        String[] types =
                new String[]{"6", "2004", "2004DX", "2005", "2006", "2007", "2008", "2009", "2010",
                        "2011"};
        for (String type : types) {
            InputStream input = DWGParserTest.class
                    .getResourceAsStream("/test-documents/testDWGmech" + type + ".dwg");
            testParserAlt(input);
        }
    }

    @SuppressWarnings("deprecation")
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
            assertEquals("Nevin Nollop", metadata.get(TikaCoreProperties.CREATOR));
            assertContains("Pangram, fox, dog",
                    Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));
            assertEquals("Lorem ipsum", metadata.get(TikaCoreProperties.COMMENTS).substring(0, 11));
            assertEquals("http://www.alfresco.com", metadata.get(TikaCoreProperties.RELATION));

            String content = handler.toString();
            assertContains("The quick brown fox jumps over the lazy dog", content);
            assertContains("Gym class", content);
            assertContains("www.alfresco.com", content);
        } finally {
            input.close();
        }
    }

    @SuppressWarnings("deprecation")
    private void testParserNoHeader(InputStream input) throws Exception {
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(input, handler, metadata);

            assertEquals("image/vnd.dwg", metadata.get(Metadata.CONTENT_TYPE));

            assertNull(metadata.get(TikaCoreProperties.TITLE));
            assertNull(metadata.get(TikaCoreProperties.DESCRIPTION));
            assertNull(metadata.get(TikaCoreProperties.CREATOR));
            assertNull(metadata.get(TikaCoreProperties.SUBJECT));
            assertNull(metadata.get(TikaCoreProperties.COMMENTS));
            assertNull(metadata.get(TikaCoreProperties.RELATION));

            String content = handler.toString();
            assertEquals("", content);
        } finally {
            input.close();
        }
    }

    @SuppressWarnings("deprecation")
    private void testParserAlt(InputStream input) throws Exception {
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(input, handler, metadata);

            assertEquals("image/vnd.dwg", metadata.get(Metadata.CONTENT_TYPE));

            assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Test Subject", metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("My Author", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("My keyword1, MyKeyword2", metadata.get(TikaCoreProperties.SUBJECT));
            assertEquals("This is a comment", metadata.get(TikaCoreProperties.COMMENTS));
            assertEquals("bejanpol", metadata.get(TikaCoreProperties.MODIFIER));
            assertEquals("http://mycompany/drawings", metadata.get(TikaCoreProperties.RELATION));
            assertEquals("MyCustomPropertyValue",
                    metadata.get(DWGParser.DWG_CUSTOM_META_PREFIX + "MyCustomProperty"));

            String content = handler.toString();
            assertContains("This is a comment", content);
            assertContains("mycompany", content);
        } finally {
            input.close();
        }
    }

    @Test
    public void testAC1027() throws Exception {
        Metadata metadata = getXML("testDWG-AC1027.dwg").metadata;
        assertEquals("hlu", metadata.get(TikaCoreProperties.MODIFIER));
    }

    @Test
    public void testAC1032() throws Exception {
        Metadata metadata = getXML("testDWG-AC1032.dwg").metadata;
        assertEquals("jlakshvi", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("CUSTOMER'S ADDRESS", metadata.get("dwg-custom:CUSTOMER'S ADDRESS"));
    }
}
