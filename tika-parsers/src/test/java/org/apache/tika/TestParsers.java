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
package org.apache.tika;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import junit.framework.TestCase;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.ParseUtils;
import org.apache.tika.utils.Utils;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Junit test class for Tika {@link Parser}s.
 */
public class TestParsers extends TestCase {

    private TikaConfig tc;

    private File testFilesBaseDir;

    public void setUp() throws Exception {
        /*
         * FIXME the old mechanism does not work anymore when running the tests
         * with Maven - need a resource-based one, but this means more changes
         * to classes which rely on filenames.
         *
         * String sep = File.separator; StringTokenizer st = new
         * StringTokenizer(System.getProperty( "java.class.path"),
         * File.pathSeparator);
         *
         * classDir = new File(st.nextToken());
         *
         * config = classDir.getParent() + sep + "config" + sep + "config.xml";
         *
         * String log4j = classDir.getParent() + sep + "Config" + sep + "log4j" +
         * sep + "log4j.properties";
         */

        testFilesBaseDir = new File("src/test/resources/test-documents");

        tc = TikaConfig.getDefaultConfig();
    }

    public void testPDFExtraction() throws Exception {
        File file = getTestFile("testPDF.pdf");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/pdf");
        String s3 = ParseUtils.getStringContent(file, TikaConfig
                .getDefaultConfig());
        assertEquals(s1, s2);
        assertEquals(s1, s3);
    }

    public void testTXTExtraction() throws Exception {
        File file = getTestFile("testTXT.txt");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "text/plain");
        assertEquals(s1, s2);
    }

    public void testRTFExtraction() throws Exception {
        File file = getTestFile("testRTF.rtf");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/rtf");
        assertEquals(s1, s2);
    }

    public void testXMLExtraction() throws Exception {
        File file = getTestFile("testXML.xml");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/xml");
        assertEquals(s1, s2);
    }

    public void testPPTExtraction() throws Exception {
        File file = getTestFile("testPPT.ppt");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(
                file, tc, "application/vnd.ms-powerpoint");
        assertEquals(s1, s2);
        Parser parser = tc.getParser("application/vnd.ms-powerpoint");
        Metadata metadata = new Metadata();
        InputStream stream = new FileInputStream(file);
        try {
            parser.parse(stream, new DefaultHandler(), metadata);
        } finally {
            stream.close();
        }
        assertEquals("Sample Powerpoint Slide", metadata.get(Metadata.TITLE));
    }

    public void testWORDxtraction() throws Exception {
        File file = getTestFile("testWORD.doc");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/msword");
        assertEquals(s1, s2);
        Parser parser = tc.getParser("application/msword");
        Metadata metadata = new Metadata();
        InputStream stream = new FileInputStream(file);
        try {
            parser.parse(stream, new DefaultHandler(), metadata);
        } finally {
            stream.close();
        }
        assertEquals("Sample Word Document", metadata.get(Metadata.TITLE));
    }

    public void testEXCELExtraction() throws Exception {
        final String expected = "Numbers and their Squares";
        File file = getTestFile("testEXCEL.xls");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/vnd.ms-excel");
        assertEquals(s1, s2);
        assertTrue("Text does not contain '" + expected + "'", s1
                .contains(expected));
        Parser parser = tc.getParser("application/vnd.ms-excel");
        Metadata metadata = new Metadata();
        InputStream stream = new FileInputStream(file);
        try {
            parser.parse(stream, new DefaultHandler(), metadata);
        } finally {
            stream.close();
        }
        assertEquals("Simple Excel document", metadata.get(Metadata.TITLE));
    }

    public void testOOExtraction() throws Exception {
        File file = getTestFile("testOpenOffice2.odt");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc,
        "application/vnd.oasis.opendocument.text");
        assertEquals(s1, s2);
    }

    public void testOutlookExtraction() throws Exception {
        File file = getTestFile("test-outlook.msg");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc,
        "application/vnd.ms-outlook");
        assertEquals(s1, s2);
    }

    public void testHTMLExtraction() throws Exception {
        File file = getTestFile("testHTML.html");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "text/html");
        assertEquals(s1, s2);

        Parser parser = tc.getParser("text/html");
        assertNotNull(parser);
    }

    public void testZipFileExtraction() throws Exception {
        File file = getTestFile("test-documents.zip");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/zip");
        assertEquals(s1, s2);

        Parser parser = tc.getParser("application/zip");
        assertNotNull(parser);
    }

    public void testMP3Extraction() throws Exception {
        File file = getTestFile("testMP3.mp3");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "audio/mpeg");
        assertEquals(s1, s2);

        Parser parser = tc.getParser("audio/mpeg");
        assertNotNull(parser);
    }

    public void testZipExtraction() throws Exception {
        File zip = getTestFile("test-documents.zip");
        List<Parser> parsers = ParseUtils.getParsersFromZip(zip, tc);
        List<File> zipFiles = Utils.unzip(new FileInputStream(zip));
        for (int i = 0; i < parsers.size(); i++) {
            Parser zipEntryParser = parsers.get(i);
            assertNotNull(zipEntryParser);
            for (int j = 0; j < zipFiles.size(); j++) {
                /* FIXME: Doesn't work with the new Parser interface
                ParserConfig config = tc.getParserConfig(
                        zipEntryParser.getMimeType());
                Map<String, Content> contents = config.getContents();
                assertNotNull(contents);
                InputStream stream = new FileInputStream(zipFiles.get(j));
                try {
                    zipEntryParser.getContents(stream, contents);
                    assertNotNull(contents.get("fullText"));
                } finally {
                    stream.close();
                }
                */
            }
        }
    }

    private File getTestFile(String filename) {
        return new File(testFilesBaseDir, filename);
    }

}
