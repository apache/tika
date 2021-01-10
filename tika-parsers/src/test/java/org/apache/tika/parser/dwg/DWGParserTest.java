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

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Assert;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import java.io.File;
import java.io.InputStream;

import static org.apache.tika.TikaTest.assertContains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DWGParserTest {

    @Test
    public void testDWG2000Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2000.dwg");
        testParserAlt(input);
    }

    @Test
    public void testDWG2004Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2004.dwg");
        testParser(input);
    }

    @Test
    public void testDWG2004ParserNoHeaderAddress() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2004_no_header.dwg");
        testParserNoHeader(input);
    }

    @Test
    public void testDWG2007Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2007.dwg");
        testParser(input);
    }

    @Test
    public void testDWG2010Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2010.dwg");
        testParser(input);
    }

    @Test
    public void testDWG2010CustomPropertiesParser() throws Exception {
        // Check that standard parsing works
        InputStream testInput = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2010_custom_props.dwg");
        testParser(testInput);

        // Check that custom properties with alternate padding work
        try (InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2010_custom_props.dwg")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(input, handler, metadata, new ParseContext());

            assertEquals("valueforcustomprop1",
                    metadata.get("customprop1"));
            assertEquals("valueforcustomprop2",
                    metadata.get("customprop2"));
        }
    }

    @Test
    public void testDWG2013Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2013.dwg");
        testParser(input);
    }

    @Test
    public void testDWG2017Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2017.dwg");
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        DWGParser dwgParser = new DWGParser();
        File dwgread = new File("/usr/local/bin/dwgread");
        if (dwgread.exists()) {
            dwgParser.getDwgConfig().setDwgReadExecutable(dwgread.getAbsolutePath());
        }
        dwgParser.parse(input, handler, metadata, new ParseContext());
        if (dwgread.exists()) {
            Assert.assertEquals("2018-08-08T21:33:45.965072215Z", metadata.get(TikaCoreProperties.CREATED));
            Assert.assertEquals("2019-05-29T16:52:10.559992790Z", metadata.get(TikaCoreProperties.MODIFIED));
            Assert.assertEquals("AC1027", metadata.get("version"));
            Assert.assertEquals("31", metadata.get("dwg_version"));
            Assert.assertEquals("125", metadata.get("maint_version"));
            Assert.assertEquals("31", metadata.get("app_dwg_version"));
            Assert.assertEquals("125", metadata.get("app_maint_version"));
            String text = handler.toString();
            Assert.assertFalse(text.isEmpty());
            Assert.assertTrue(StringUtils.contains(text, "CONTROLADOR ESPECFICO DA APLICAO, CONFIGURVEL, UTILIZADO EM VENTILO-CONVECTORES, (FCU), VAV, etc."));
        }
    }

    @Test
    public void testDWG2017Parser3() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2017-2.dwg");
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        File dwgread = new File("/usr/local/bin/dwgread");
        DWGParser dwgParser = new DWGParser();
        if (dwgread.exists()) {
            dwgParser.getDwgConfig().setDwgReadExecutable(dwgread.getAbsolutePath());
        }
        dwgParser.parse(input, handler, metadata, new ParseContext());
        if (dwgread.exists()) {
            Assert.assertEquals("2018-08-08T21:33:45.965072215Z", metadata.get(TikaCoreProperties.CREATED));
            Assert.assertEquals("2019-02-23T03:23:36.096000373Z", metadata.get(TikaCoreProperties.MODIFIED));
            Assert.assertEquals("AC1027", metadata.get("version"));
            Assert.assertEquals("31", metadata.get("dwg_version"));
            Assert.assertEquals("125", metadata.get("maint_version"));
            Assert.assertEquals("31", metadata.get("app_dwg_version"));
            Assert.assertEquals("125", metadata.get("app_maint_version"));

            String text = handler.toString();
            Assert.assertFalse(text.isEmpty());
            Assert.assertTrue(StringUtils.contains(text, "VARIABLE SPEED ELEVATOR SHAFT PRESSURIZATION FAN"));
        }
    }

    @Test
    public void testDWG2018Parser() throws Exception {
        InputStream input = DWGParserTest.class.getResourceAsStream(
                "/test-documents/testDWG2018.dwg");
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        DWGParser dwgParser = new DWGParser();
        File dwgread = new File("/usr/local/bin/dwgread");
        if (dwgread.exists()) {
            dwgParser.getDwgConfig().setDwgReadExecutable(dwgread.getAbsolutePath());
        }
        dwgParser.parse(input, handler, metadata, new ParseContext());
        if (dwgread.exists()) {
            Assert.assertEquals("AC1032", metadata.get("version"));
            Assert.assertEquals("2014-05-17T15:36:15.287617743Z", metadata.get(TikaCoreProperties.CREATED));
            Assert.assertEquals("2018-04-09T22:12:41.316476762Z", metadata.get(TikaCoreProperties.MODIFIED));
            Assert.assertEquals("33", metadata.get("dwg_version"));
            Assert.assertEquals("4", metadata.get("maint_version"));
            Assert.assertEquals("0", metadata.get("app_dwg_version"));
            Assert.assertEquals("0", metadata.get("app_maint_version"));
            String text = handler.toString();
            Assert.assertFalse(text.isEmpty());
            Assert.assertTrue(StringUtils.contains(text, "This a multiline text to check if libredwg is able to read this in the current file format version"));
        }
    }

    @Test
    public void testDWGMechParser() throws Exception {
        String[] types = new String[]{
                "6", "2004", "2004DX", "2005", "2006",
                "2007", "2008", "2009", "2010", "2011"
        };
        for (String type : types) {
            InputStream input = DWGParserTest.class.getResourceAsStream(
                    "/test-documents/testDWGmech" + type + ".dwg");
            testParserAlt(input);
        }
    }

    @SuppressWarnings("deprecation")
    private void testParser(InputStream input) throws Exception {
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(input, handler, metadata, new ParseContext());

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
                    metadata.get(TikaCoreProperties.COMMENTS).substring(0, 11));
            assertEquals("http://www.alfresco.com",
                    metadata.get(TikaCoreProperties.RELATION));

            // Check some of the old style metadata too
            assertEquals("The quick brown fox jumps over the lazy dog",
                    metadata.get(Metadata.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog",
                    metadata.get(Metadata.SUBJECT));

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
            new DWGParser().parse(input, handler, metadata, new ParseContext());

            assertEquals("image/vnd.dwg", metadata.get(Metadata.CONTENT_TYPE));

            assertNull(metadata.get(TikaCoreProperties.TITLE));
            assertNull(metadata.get(TikaCoreProperties.DESCRIPTION));
            assertNull(metadata.get(Metadata.SUBJECT));
            assertNull(metadata.get(TikaCoreProperties.CREATOR));
            assertNull(metadata.get(TikaCoreProperties.KEYWORDS));
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
            new DWGParser().parse(input, handler, metadata, new ParseContext());

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
            assertContains("This is a comment", content);
            assertContains("mycompany", content);
        } finally {
            input.close();
        }
    }
}
