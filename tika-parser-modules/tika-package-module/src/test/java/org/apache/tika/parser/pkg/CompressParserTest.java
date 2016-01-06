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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing compress (.Z) files.
 */
public class CompressParserTest extends AbstractPkgTest {
    @Test
    public void testCompressParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = TarParserTest.class.getResourceAsStream(
                "/test-documents/test-documents.tar.Z");
        try {
            parser.parse(stream, handler, metadata, recursingContext);
        } finally {
            stream.close();
        }

        assertEquals("application/x-compress", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("test-documents/testEXCEL.xls", content);
        assertContains("test-documents/testHTML.html", content);
        assertContains("test-documents/testOpenOffice2.odt", content);
        assertContains("test-documents/testPDF.pdf", content);
        assertContains("test-documents/testPPT.ppt", content);
        assertContains("test-documents/testRTF.rtf", content);
        assertContains("test-documents/testTXT.txt", content);
        assertContains("test-documents/testWORD.doc", content);
        assertContains("test-documents/testXML.xml", content);
    }

    /**
     * Tests that the ParseContext parser is correctly
     *  fired for all the embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
       Parser parser = new AutoDetectParser(); // Should auto-detect!
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();

       InputStream stream = ZipParserTest.class.getResourceAsStream(
               "/test-documents/test-documents.tar.Z");
       try {
           parser.parse(stream, handler, metadata, trackingContext);
       } finally {
           stream.close();
       }
       
       // Should find a single entry, for the (compressed) tar file
       assertEquals(1, tracker.filenames.size());
       assertEquals(1, tracker.mediatypes.size());
       assertEquals(1, tracker.modifiedAts.size());
       
       assertEquals(null, tracker.filenames.get(0));
       assertEquals(null, tracker.mediatypes.get(0));
       assertEquals(null, tracker.modifiedAts.get(0));

       // Tar file starts with the directory name
       assertEquals("test-documents/", new String(tracker.lastSeenStart, 0, 15, US_ASCII));
    }
}