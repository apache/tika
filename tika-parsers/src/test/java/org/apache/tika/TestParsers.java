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
package org.apache.tika;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.ParseUtils;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Junit test class for Tika {@link Parser}s.
 */
public class TestParsers extends TikaTest {

    private TikaConfig tc;

    public void setUp() throws Exception {
        tc = TikaConfig.getDefaultConfig();
    }

    public void testPDFExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testPDF.pdf");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/pdf");
        String s3 = ParseUtils.getStringContent(file, TikaConfig
                .getDefaultConfig());
        assertEquals(s1, s2);
        assertEquals(s1, s3);
    }

    public void testTXTExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testTXT.txt");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "text/plain");
        assertEquals(s1, s2);
    }

    public void testXMLExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testXML.xml");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/xml");
        assertEquals(s1, s2);
    }

    public void testPPTExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testPPT.ppt");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc,
                "application/vnd.ms-powerpoint");
        assertEquals(s1, s2);
        Parser parser =
            tc.getParser(MediaType.parse("application/vnd.ms-powerpoint"));
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
        File file = getResourceAsFile("/test-documents/testWORD.doc");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/msword");
        assertEquals(s1, s2);
        Parser parser = tc.getParser(MediaType.parse("application/msword"));
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
        File file = getResourceAsFile("/test-documents/testEXCEL.xls");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc,
                "application/vnd.ms-excel");
        assertEquals(s1, s2);
        assertTrue("Text does not contain '" + expected + "'", s1
                .contains(expected));
        Parser parser =
            tc.getParser(MediaType.parse("application/vnd.ms-excel"));
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
        File file = getResourceAsFile("/test-documents/testOpenOffice2.odt");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc,
                "application/vnd.oasis.opendocument.text");
        assertEquals(s1, s2);
    }

    public void testOutlookExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/test-outlook.msg");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc,
                "application/vnd.ms-outlook");
        assertEquals(s1, s2);
    }

    public void testHTMLExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testHTML.html");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "text/html");
        assertEquals(s1, s2);

        Parser parser = tc.getParser(MediaType.parse("text/html"));
        assertNotNull(parser);
    }

    public void testZipFileExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/test-documents.zip");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/zip");
        assertEquals(s1, s2);

        Parser parser = tc.getParser(MediaType.parse("application/zip"));
        assertNotNull(parser);
    }

    public void testMP3Extraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testMP3id3v1.mp3");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "audio/mpeg");
        assertEquals(s1, s2);

        Parser parser = tc.getParser(MediaType.parse("audio/mpeg"));
        assertNotNull(parser);
    }

    private void verifyComment(String extension, String fileName) throws Exception {
        File file = getResourceAsFile("/test-documents/" + fileName + "." + extension);
        String content = ParseUtils.getStringContent(file, tc);
        assertTrue(extension + ": content=" + content + " did not extract text",
                   content.contains("Here is some text"));
        assertTrue(extension + ": content=" + content + " did not extract comment",
                   content.contains("Here is a comment"));
    }

    public void testComment() throws Exception {
        // TIKA-717: re-enable ppt once we fix it
        //final String[] extensions = new String[] {"ppt", "pptx", "doc", "docx", "pdf", "rtf"};
        final String[] extensions = new String[] {"pptx", "doc", "docx", "pdf", "rtf"};
        for(String extension : extensions) {
            verifyComment(extension, "testComment");
            // TIKA-717: re-enable once we fix this:
            //if (extension.equals("pdf")) {
            //verifyComment(extension, "testComment2");
            //}
        }
    }
}
