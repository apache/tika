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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing tar files.
 */
public class TarParserTest extends AbstractPkgTest {

    public void testTarParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = TarParserTest.class.getResourceAsStream(
                "/test-documents/test-documents.tar");
        try {
            parser.parse(stream, handler, metadata, recursingContext);
        } finally {
            stream.close();
        }

        assertEquals("application/x-tar", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertTrue(content.contains("test-documents/testEXCEL.xls"));
        assertTrue(content.contains("Sample Excel Worksheet"));
        assertTrue(content.contains("test-documents/testHTML.html"));
        assertTrue(content.contains("Test Indexation Html"));
        assertTrue(content.contains("test-documents/testOpenOffice2.odt"));
        assertTrue(content.contains("This is a sample Open Office document"));
        assertTrue(content.contains("test-documents/testPDF.pdf"));
        assertTrue(content.contains("Apache Tika"));
        assertTrue(content.contains("test-documents/testPPT.ppt"));
        assertTrue(content.contains("Sample Powerpoint Slide"));
        assertTrue(content.contains("test-documents/testRTF.rtf"));
        assertTrue(content.contains("indexation Word"));
        assertTrue(content.contains("test-documents/testTXT.txt"));
        assertTrue(content.contains("Test d'indexation de Txt"));
        assertTrue(content.contains("test-documents/testWORD.doc"));
        assertTrue(content.contains("This is a sample Microsoft Word Document"));
        assertTrue(content.contains("test-documents/testXML.xml"));
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
               "/test-documents/test-documents.tar");
       try {
           parser.parse(stream, handler, metadata, trackingContext);
       } finally {
           stream.close();
       }
       
       // Should have found all 9 documents, but not the directory
       assertEquals(9, tracker.filenames.size());
       assertEquals(9, tracker.mediatypes.size());
       
       // Should have names but not content types, as tar doesn't
       //  store the content types
       assertEquals("test-documents/testEXCEL.xls", tracker.filenames.get(0));
       assertEquals("test-documents/testHTML.html", tracker.filenames.get(1));
       assertEquals("test-documents/testOpenOffice2.odt", tracker.filenames.get(2));
       assertEquals("test-documents/testPDF.pdf", tracker.filenames.get(3));
       assertEquals("test-documents/testPPT.ppt", tracker.filenames.get(4));
       assertEquals("test-documents/testRTF.rtf", tracker.filenames.get(5));
       assertEquals("test-documents/testTXT.txt", tracker.filenames.get(6));
       assertEquals("test-documents/testWORD.doc", tracker.filenames.get(7));
       assertEquals("test-documents/testXML.xml", tracker.filenames.get(8));
       
       for(String type : tracker.mediatypes) {
          assertNull(type);
       }
    }
}
