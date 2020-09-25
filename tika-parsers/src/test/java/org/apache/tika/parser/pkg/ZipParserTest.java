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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Test case for parsing zip files.
 */
public class ZipParserTest extends AbstractPkgTest {

    @Test
    public void testZipParsing() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = ZipParserTest.class.getResourceAsStream(
                "/test-documents/test-documents.zip")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/zip", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }

    /**
     * Tests that the ParseContext parser is correctly
     *  fired for all the embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();

        try (InputStream stream = ZipParserTest.class.getResourceAsStream(
                "/test-documents/test-documents.zip")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, trackingContext);
        }
       
       // Should have found all 9 documents
       assertEquals(9, tracker.filenames.size());
       assertEquals(9, tracker.mediatypes.size());
       assertEquals(9, tracker.modifiedAts.size());
       
       // Should have names and modified dates, but not content types, 
       //  as zip doesn't store the content types
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
       for(String crt : tracker.createdAts) {
           assertNull(crt);
       }
       for(String mod : tracker.modifiedAts) {
           assertNotNull(mod);
           assertTrue("Modified at " + mod, mod.startsWith("20"));
       }
    }

    /**
     * Test case for the ability of the ZIP parser to extract the name of
     * a ZIP entry even if the content of the entry is unreadable due to an
     * unsupported compression method.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-346">TIKA-346</a>
     */
    @Test
    public void testUnsupportedZipCompressionMethod() throws Exception {
        String content = new Tika().parseToString(
                ZipParserTest.class.getResourceAsStream(
                        "/test-documents/moby.zip"));
        assertContains("README", content);
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
    @Test
    public void testPlaceholders() throws Exception {
        String xml = getXML("testEmbedded.zip").xml;
        assertContains("<div class=\"embedded\" id=\"test1.txt\" />", xml);
        assertContains("<div class=\"embedded\" id=\"test2.txt\" />", xml);

        // Also make sure EMBEDDED_RELATIONSHIP_ID was
        // passed when parsing the embedded docs:
        ParseContext context = new ParseContext();
        GatherRelIDsDocumentExtractor relIDs = new GatherRelIDsDocumentExtractor();
        context.set(EmbeddedDocumentExtractor.class, relIDs);
        try (InputStream input = getResourceAsStream("/test-documents/testEmbedded.zip")) {
            AUTO_DETECT_PARSER.parse(input,
                    new BodyContentHandler(),
                    new Metadata(),
                    context);
        }

        assertTrue(relIDs.allRelIDs.contains("test1.txt"));
        assertTrue(relIDs.allRelIDs.contains("test2.txt"));
    }

    @Test // TIKA-936
    public void testCustomEncoding() throws Exception {
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        factory.setEntryEncoding("SJIS");
        trackingContext.set(ArchiveStreamFactory.class, factory);

        try (InputStream stream = TikaInputStream.get(Base64.decodeBase64(
                "UEsDBBQAAAAIAI+CvUCDo3+zIgAAACgAAAAOAAAAk/qWe4zqg4GDgi50"
                        + "eHRr2tj0qulsc2pzRHN609Gm7Y1OvFxNYLHJv6ZV97yCiQEAUEsBAh"
                        + "QLFAAAAAgAj4K9QIOjf7MiAAAAKAAAAA4AAAAAAAAAAAAgAAAAAAAA"
                        + "AJP6lnuM6oOBg4IudHh0UEsFBgAAAAABAAEAPAAAAE4AAAAAAA=="))) {
            AUTO_DETECT_PARSER.parse(
                    stream, new DefaultHandler(),
                    new Metadata(), trackingContext);
        }

        assertEquals(1, tracker.filenames.size());
        assertEquals(
                "\u65E5\u672C\u8A9E\u30E1\u30E2.txt",
                tracker.filenames.get(0));
    }

    @Test
    public void testZipEncrypted() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testZipEncrypted.zip");
        assertEquals(2, metadataList.size());
        String[] values = metadataList.get(0).getValues(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM);
        assertNotNull(values);
        assertEquals(1, values.length);
        assertContains("EncryptedDocumentException: stream (encrypted.txt) is encrypted", values[0]);


        assertContains("hello world", metadataList.get(1).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
    }

    @Test
    public void testKMZDetection() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testKMZ.kmz");
        assertEquals("application/vnd.google-earth.kmz", metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testJARDetection() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testJAR.jar");
        assertEquals("application/java-archive", metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testQuineRecursiveParserWrapper() throws Exception {
        //received permission from author via dm
        //2019-07-25 to include
        //http://alf.nu/s/droste.zip in unit tests
        //Out of respect to the author, please maintain
        //the original file name
        getRecursiveMetadata("droste.zip");
    }

    @Test(expected = TikaException.class)
    public void testQuine() throws Exception {
        getXML("droste.zip");
    }

    @Test
    public void testZipUsingStoredWithDataDescriptor() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = ZipParserTest.class.getResourceAsStream(
                "/test-documents/testZip_with_DataDescriptor.zip")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, trackingContext);

            assertEquals(5, tracker.filenames.size());
            assertEquals("en0", tracker.filenames.get(0));
            assertEquals("en1", tracker.filenames.get(1));
            assertEquals("en2", tracker.filenames.get(2));
            assertEquals("en3", tracker.filenames.get(3));
            assertEquals("en4", tracker.filenames.get(4));
            assertEquals(1, tracker.lastSeenStart[0]);
            assertEquals(2, tracker.lastSeenStart[1]);
            assertEquals(3, tracker.lastSeenStart[2]);
            assertEquals(4, tracker.lastSeenStart[3]);
        }
    }

    @Test
    public void testDataDescriptorWithEmptyEntry() throws Exception {

        //test that an empty first entry does not cause problems
        List<Metadata> results = getRecursiveMetadata("testZip_with_DataDescriptor2.zip");
        assertEquals(5, results.size());

        //mime is 0 bytes
        assertContains("InputStream must have > 0 bytes",
                results.get(1).get("X-TIKA:EXCEPTION:embedded_exception"));
        //source.xml is binary, not xml
        assertContains("TikaException: XML parse error",
                results.get(2).get("X-TIKA:EXCEPTION:embedded_exception"));
        //manifest.xml has malformed xml
        assertContains("TikaException: XML parse error",
                results.get(4).get("X-TIKA:EXCEPTION:embedded_exception"));
    }
}
