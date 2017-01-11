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

package org.apache.tika.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Junit test class for Tika {@link Parser}s.
 */
public class TestParsers extends TikaTest {

    private TikaConfig tc;

    private Tika tika;

    @Before
    public void setUp() throws Exception {
        tc = TikaConfig.getDefaultConfig();
        tika = new Tika(tc);
    }

    @Test
    public void testWORDExtraction() throws Exception {

        Path tmpFile = getTestDocumentAsTempFile("testWORD.doc");
        Parser parser = tika.getParser();
        Metadata metadata = new Metadata();
        try (InputStream stream = Files.newInputStream(tmpFile)) {
            parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        } finally {
            Files.delete(tmpFile);
        }
        assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testEXCELExtraction() throws Exception {
        final String expected = "Numbers and their Squares";
        Path tmpFile = getTestDocumentAsTempFile("testEXCEL.xls");
        try {
            String s1 = tika.parseToString(tmpFile);
            assertTrue("Text does not contain '" + expected + "'", s1
                    .contains(expected));
            Parser parser = tika.getParser();
            Metadata metadata = new Metadata();
            try (InputStream stream = Files.newInputStream(tmpFile)) {
                parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
            }
            assertEquals("Simple Excel document", metadata.get(TikaCoreProperties.TITLE));
        } finally {
            Files.delete(tmpFile);
        }
    }

    @Test
    public void testOptionalHyphen() throws Exception {
        String[] extensions =
                new String[] { "ppt", "pptx", "doc", "docx", "rtf", "pdf"};
        for (String extension : extensions) {
            Path tmpFile = getTestDocumentAsTempFile("testOptionalHyphen." + extension);
            String content = null;
            try {
                content = tika.parseToString(tmpFile);
            } finally {
                Files.delete(tmpFile);
            }
            assertTrue("optional hyphen was not handled for '" + extension + "' file type: " + content,
                       content.contains("optionalhyphen") ||
                       content.contains("optional\u00adhyphen") ||   // soft hyphen
                       content.contains("optional\u200bhyphen") ||   // zero width space
                       content.contains("optional\u2027"));          // hyphenation point
            
        }
    }

    @Test
    public void testComment() throws Exception {
        final String[] extensions = new String[] {"ppt", "pptx", "doc", 
            "docx", "xls", "xlsx", "pdf", "rtf"};
        for(String extension : extensions) {
            verifyComment(extension, "testComment");
        }
    }

    @Test
    public void testEmbeddedPDFInPPTX() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_embeddedPDF.pptx");
        Metadata pdfMetadata1 = metadataList.get(4);
        assertContains("Apache Tika", pdfMetadata1.get(RecursiveParserWrapper.TIKA_CONTENT));
        Metadata pdfMetadata2 = metadataList.get(5);
        assertContains("Hello World", pdfMetadata2.get(RecursiveParserWrapper.TIKA_CONTENT));
    }

    @Test
    public void testEmbeddedPDFInXLSX() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_embeddedPDF.xlsx");
        Metadata pdfMetadata = metadataList.get(1);
        assertContains("Hello World", pdfMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));
    }

    @Test
    public void testEmbeddedPDFInPPTXViaSAX() throws Exception {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXPptxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_embeddedPDF.pptx", parseContext);
        Metadata pdfMetadata1 = metadataList.get(4);
        assertContains("Apache Tika", pdfMetadata1.get(RecursiveParserWrapper.TIKA_CONTENT));
        Metadata pdfMetadata2 = metadataList.get(5);
        assertContains("Hello World", pdfMetadata2.get(RecursiveParserWrapper.TIKA_CONTENT));
    }

    private void verifyComment(String extension, String fileName) throws Exception {
        TemporaryResources tmp = new TemporaryResources();

        String content = null;
        Path tmpFile = null;
        try {
            tmpFile = getTestDocumentAsTempFile(fileName + "." + extension);
            content = tika.parseToString(tmpFile);
        } finally {
            if (tmpFile != null) {
                Files.delete(tmpFile);
            }
        }
        assertTrue(extension + ": content=" + content + " did not extract text",
                content.contains("Here is some text"));
        assertTrue(extension + ": content=" + content + " did not extract comment",
                content.contains("Here is a comment"));
    }
}
