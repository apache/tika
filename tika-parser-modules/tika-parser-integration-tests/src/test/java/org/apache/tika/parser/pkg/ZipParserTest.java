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

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

    private class GatherRelIDsDocumentExtractor implements EmbeddedDocumentExtractor {
        public Set<String> allRelIDs = new HashSet<String>();
        public boolean shouldParseEmbedded(Metadata metadata) {
            String relID = metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
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
    public void testQuineRecursiveParserWrapper() throws Exception {
        //received permission from author via dm
        //2019-07-25 to include
        //http://alf.nu/s/droste.zip in unit tests
        //Out of respect to the author, please maintain
        //the original file name
        getRecursiveMetadata("droste.zip");
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
