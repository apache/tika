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
package org.apache.tika.parser.pkg;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.Tika;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing zip files.
 */
public class ZipParserTest extends AbstractPkgTest {

    public void testZipParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = ZipParserTest.class.getResourceAsStream(
                "/test-documents/test-documents.zip");
        try {
            parser.parse(stream, handler, metadata, recursingContext);
        } finally {
            stream.close();
        }

        assertEquals("application/zip", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertTrue(content.contains("testEXCEL.xls"));
        assertTrue(content.contains("Sample Excel Worksheet"));
        assertTrue(content.contains("testHTML.html"));
        assertTrue(content.contains("Test Indexation Html"));
        assertTrue(content.contains("testOpenOffice2.odt"));
        assertTrue(content.contains("This is a sample Open Office document"));
        assertTrue(content.contains("testPDF.pdf"));
        assertTrue(content.contains("Apache Tika"));
        assertTrue(content.contains("testPPT.ppt"));
        assertTrue(content.contains("Sample Powerpoint Slide"));
        assertTrue(content.contains("testRTF.rtf"));
        assertTrue(content.contains("indexation Word"));
        assertTrue(content.contains("testTXT.txt"));
        assertTrue(content.contains("Test d'indexation de Txt"));
        assertTrue(content.contains("testWORD.doc"));
        assertTrue(content.contains("This is a sample Microsoft Word Document"));
        assertTrue(content.contains("testXML.xml"));
        assertTrue(content.contains("Rida Benjelloun"));
    }

    /**
     * Tests that the ParseContext parser is correctly
     *  fired for all the embedded entries.
     */
    public void testEmbedded() throws Exception {
       Parser parser = new AutoDetectParser(); // Should auto-detect!
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();

       InputStream stream = ZipParserTest.class.getResourceAsStream(
               "/test-documents/test-documents.zip");
       try {
           parser.parse(stream, handler, metadata, trackingContext);
       } finally {
           stream.close();
       }
       
       // Should have found all 9 documents
       assertEquals(9, tracker.filenames.size());
       assertEquals(9, tracker.mediatypes.size());
       
       // Should have names but not content types, as zip doesn't
       //  store the content types
       assertEquals("testEXCEL.xls", tracker.filenames.get(0));
       assertEquals("testHTML.html", tracker.filenames.get(1));
       assertEquals("testOpenOffice2.odt", tracker.filenames.get(2));
       assertEquals("testPDF.pdf", tracker.filenames.get(3));
       assertEquals("testPPT.ppt", tracker.filenames.get(4));
       assertEquals("testRTF.rtf", tracker.filenames.get(5));
       assertEquals("testTXT.txt", tracker.filenames.get(6));
       assertEquals("testWORD.doc", tracker.filenames.get(7));
       assertEquals("testXML.xml", tracker.filenames.get(8));
       
       for(String type : tracker.mediatypes) {
          assertNull(type);
       }
    }

    /**
     * Test case for the ability of the ZIP parser to extract the name of
     * a ZIP entry even if the content of the entry is unreadable due to an
     * unsupported compression method.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-346">TIKA-346</a>
     */
    public void testUnsupportedZipCompressionMethod() throws Exception {
        String content = new Tika().parseToString(
                ZipParserTest.class.getResourceAsStream(
                        "/test-documents/moby.zip"));
        assertTrue(content.contains("README"));
    }

    private class GatherRelIDsDocumentExtractor implements EmbeddedDocumentExtractor {
        public Set<String> allRelIDs = new HashSet<String>();
        public boolean shouldParseEmbedded(Metadata metadata) {      
            String relID = metadata.get(Metadata.EMBEDDED_RELATIONSHIP_ID);
            if (relID != null) {
                allRelIDs.add(relID);
            }
            return false;
        }

        public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean outputHtml) {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    // TIKA-1036
    public void testPlaceholders() throws Exception {
        String xml = getXML("testEmbedded.zip").xml;
        assertContains("<div class=\"embedded\" id=\"test1.txt\"/>", xml);
        assertContains("<div class=\"embedded\" id=\"test2.txt\"/>", xml);

        // Also make sure EMBEDDED_RELATIONSHIP_ID was
        // passed when parsing the embedded docs:
        Parser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        GatherRelIDsDocumentExtractor relIDs = new GatherRelIDsDocumentExtractor();
        context.set(EmbeddedDocumentExtractor.class, relIDs);
        InputStream input = getResourceAsStream("/test-documents/testEmbedded.zip");
        try {
          parser.parse(input,
                       new BodyContentHandler(),
                       new Metadata(),
                       context);
        } finally {
            input.close();
        }

        assertTrue(relIDs.allRelIDs.contains("test1.txt"));
        assertTrue(relIDs.allRelIDs.contains("test2.txt"));
    }
}
