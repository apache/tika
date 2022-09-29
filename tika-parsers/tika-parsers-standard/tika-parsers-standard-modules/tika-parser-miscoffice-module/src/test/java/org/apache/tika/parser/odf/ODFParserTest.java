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
package org.apache.tika.parser.odf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

public class ODFParserTest extends TikaTest {
    /**
     * For now, allow us to run some tests against both
     * the old and the new parser
     */
    private Parser[] getParsers() {
        return new Parser[]{new OpenDocumentParser()};
    }


    @Test
    public void testOO3() throws Exception {
        for (Parser parser : getParsers()) {
            try (InputStream input = getResourceAsStream("/test-documents/testODFwithOOo3.odt")) {
                Metadata metadata = new Metadata();
                ContentHandler handler = new BodyContentHandler();
                parser.parse(input, handler, metadata, new ParseContext());

                assertEquals("application/vnd.oasis.opendocument.text",
                        metadata.get(Metadata.CONTENT_TYPE));

                String content = handler.toString();
                assertContains("Tika is part of the Lucene project.", content);
                assertContains("Solr", content);
                assertContains("one embedded", content);
                assertContains("Rectangle Title", content);
                assertContains("a blue background and dark border", content);
            }
        }
    }

    @Test
    public void testOO2() throws Exception {
        for (Parser parser : getParsers()) {
            try (InputStream input = getResourceAsStream("/test-documents/testOpenOffice2.odt")) {
                Metadata metadata = new Metadata();
                ContentHandler handler = new BodyContentHandler();
                parser.parse(input, handler, metadata, new ParseContext());

                assertEquals("application/vnd.oasis.opendocument.text",
                        metadata.get(Metadata.CONTENT_TYPE));
                assertEquals("en-US", metadata.get(TikaCoreProperties.LANGUAGE));
                assertEquals("PT1M7S", metadata.get(OfficeOpenXMLExtended.TOTAL_TIME));
                assertEquals("NeoOffice/2.2$Unix OpenOffice.org_project/680m18$Build-9161",
                        metadata.get("generator"));

                // Check date metadata, both old-style and new-style
                assertEquals("2007-09-14T11:07:10", metadata.get(TikaCoreProperties.MODIFIED));
                assertEquals("2007-09-14T11:06:08", metadata.get(TikaCoreProperties.CREATED));

                // Check the document statistics
                assertEquals("1", metadata.get(Office.PAGE_COUNT));
                assertEquals("1", metadata.get(Office.PARAGRAPH_COUNT));
                assertEquals("14", metadata.get(Office.WORD_COUNT));
                assertEquals("78", metadata.get(Office.CHARACTER_COUNT));
                assertEquals("0", metadata.get(Office.TABLE_COUNT));
                assertEquals("0", metadata.get(Office.OBJECT_COUNT));
                assertEquals("0", metadata.get(Office.IMAGE_COUNT));

                // Custom metadata tags present but without values
                assertEquals(null, metadata.get("custom:Info 1"));
                assertEquals(null, metadata.get("custom:Info 2"));
                assertEquals(null, metadata.get("custom:Info 3"));
                assertEquals(null, metadata.get("custom:Info 4"));

                assertEquals("1.0", metadata.get(OpenDocumentMetaParser.ODF_VERSION_KEY));

                String content = handler.toString();
                assertTrue(content.contains("This is a sample Open Office document," +
                        " written in NeoOffice 2.2.1 for the Mac."));
            }
        }
    }

    /**
     * Similar to {@link #testOO2()}, but using a different
     * OO2 file with different metadata in it
     */
    @Test
    public void testOO2Metadata() throws Exception {
        try (InputStream input = getResourceAsStream("/test-documents/testOpenOffice2.odf")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OpenDocumentParser().parse(input, handler, metadata);

            assertEquals("application/vnd.oasis.opendocument.formula",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals(null, metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("2006-01-27T11:55:22", metadata.get(TikaCoreProperties.CREATED));
            assertEquals("The quick brown fox jumps over the lazy dog",
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog",
                    metadata.get(OfficeOpenXMLCore.SUBJECT));
            assertContains("Gym class featuring a brown fox and lazy dog",
                    Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));
            assertEquals("PT0S", metadata.get(OfficeOpenXMLExtended.TOTAL_TIME));
            assertEquals("1", metadata.get("editing-cycles"));
            assertEquals("OpenOffice.org/2.2$Win32 OpenOffice.org_project/680m14$Build-9134",
                    metadata.get("generator"));
            assertEquals("Pangram, fox, dog", metadata.get(TikaCoreProperties.SUBJECT));

            // User defined metadata
            assertEquals("Text 1", metadata.get("custom:Info 1"));
            assertEquals("2", metadata.get("custom:Info 2"));
            assertEquals("false", metadata.get("custom:Info 3"));
            assertEquals("true", metadata.get("custom:Info 4"));

            // No statistics present
            assertEquals(null, metadata.get(Office.PAGE_COUNT));
            assertEquals(null, metadata.get(Office.PARAGRAPH_COUNT));
            assertEquals(null, metadata.get(Office.WORD_COUNT));
            assertEquals(null, metadata.get(Office.CHARACTER_COUNT));
            assertEquals(null, metadata.get(Office.TABLE_COUNT));
            assertEquals(null, metadata.get(Office.OBJECT_COUNT));
            assertEquals(null, metadata.get(Office.IMAGE_COUNT));
            assertEquals(null, metadata.get("nbTab"));
            assertEquals(null, metadata.get("nbObject"));
            assertEquals(null, metadata.get("nbImg"));
            assertEquals(null, metadata.get("nbPage"));
            assertEquals(null, metadata.get("nbPara"));
            assertEquals(null, metadata.get("nbWord"));
            assertEquals(null, metadata.get("nbCharacter"));
            assertEquals("1.0", metadata.get(OpenDocumentMetaParser.ODF_VERSION_KEY));


            // Note - contents of maths files not currently supported
            String content = handler.toString().trim();
            assertEquals("", content.trim());
        }
    }

    /**
     * Similar to {@link #testOO2()} )}, but using an OO3 file
     */
    @Test
    public void testOO3Metadata() throws Exception {
        try (InputStream input = getResourceAsStream("/test-documents/testODFwithOOo3.odt")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OpenDocumentParser().parse(input, handler, metadata);

            assertEquals("application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("2009-10-05T21:22:38", metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("2009-10-05T19:04:01", metadata.get(TikaCoreProperties.CREATED));
            assertEquals("2009-10-05T19:04:01", metadata.get(TikaCoreProperties.CREATED));
            assertEquals("Apache Tika", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Test document", metadata.get(OfficeOpenXMLCore.SUBJECT));
            assertContains("Test document",
                    Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));
            assertEquals("A rather complex document", metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("Bart Hanssens", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("2", metadata.get("editing-cycles"));
            assertEquals("PT02H03M24S", metadata.get(OfficeOpenXMLExtended.TOTAL_TIME));
            assertEquals("OpenOffice.org/3.1$Unix OpenOffice.org_project/310m19$Build-9420",
                    metadata.get("generator"));
            assertEquals("Apache, Lucene, Tika", metadata.get(TikaCoreProperties.SUBJECT));

            // User defined metadata
            assertEquals("Bart Hanssens", metadata.get("custom:Editor"));
            assertEquals(null, metadata.get("custom:Info 2"));
            assertEquals(null, metadata.get("custom:Info 3"));
            assertEquals(null, metadata.get("custom:Info 4"));

            // Check the document statistics
            assertEquals("2", metadata.get(Office.PAGE_COUNT));
            assertEquals("13", metadata.get(Office.PARAGRAPH_COUNT));
            assertEquals("54", metadata.get(Office.WORD_COUNT));
            assertEquals("351", metadata.get(Office.CHARACTER_COUNT));
            assertEquals("0", metadata.get(Office.TABLE_COUNT));
            assertEquals("2", metadata.get(Office.OBJECT_COUNT));
            assertEquals("0", metadata.get(Office.IMAGE_COUNT));
            assertEquals("1.1", metadata.get(OpenDocumentMetaParser.ODF_VERSION_KEY));

            String content = handler.toString();
            assertTrue(content.contains("Apache Tika Tika is part of the Lucene project."));
        }
    }

    @Test
    public void testODPMasterFooter() throws Exception {
        try (InputStream input = getResourceAsStream("/test-documents/testMasterFooter.odp")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            AUTO_DETECT_PARSER.parse(input, handler, metadata, new ParseContext());

            String content = handler.toString();
            assertContains("Master footer is here", content);
        }
    }

    @Test
    public void testODTFooter() throws Exception {
        try (InputStream input = getResourceAsStream("/test-documents/testFooter.odt")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            AUTO_DETECT_PARSER.parse(input, handler, metadata, new ParseContext());

            String content = handler.toString();
            assertContains("Here is some text...", content);
            assertContains("Here is some text on page 2", content);
            assertContains("Here is footer text", content);
        }
    }

    @Test
    public void testODSFooter() throws Exception {
        try (InputStream input = getResourceAsStream("/test-documents/testFooter.ods")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            AUTO_DETECT_PARSER.parse(input, handler, metadata, new ParseContext());

            String content = handler.toString();
            assertContains("Here is a footer in the center area", content);
        }
    }

    @Test
    public void testFromFile() throws Exception {
        try (TikaInputStream tis = TikaInputStream
                .get(getResourceAsUrl("/test-documents/testODFwithOOo3.odt"))) {
            assertEquals(true, tis.hasFile());
            OpenDocumentParser parser = new OpenDocumentParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            parser.parse(tis, handler, metadata, new ParseContext());

            assertEquals("application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("1.1", metadata.get(OpenDocumentMetaParser.ODF_VERSION_KEY));

            String content = handler.toString();
            assertContains("Tika is part of the Lucene project.", content);
        }
    }

    @Test
    public void testNPEFromFile() throws Exception {
        OpenDocumentParser parser = new OpenDocumentParser();
        try (TikaInputStream tis = TikaInputStream
                .get(getResourceAsUrl("/test-documents/testNPEOpenDocument.odt"))) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            parser.parse(tis, handler, metadata, new ParseContext());

            assertEquals("application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));

            String content = handler.toString();
            assertContains("primero hay que generar un par de claves", content);
        }
    }

    // TIKA-1063: Test basic style support.
    @Test
    public void testODTStyles() throws Exception {
        String xml = getXML("testStyles.odt").xml;
        assertContains("This <i>is</i> <b>just</b> a <u>test</u>", xml);
        assertContains("<p>And <b>another <i>test</i> is</b> here.</p>", xml);
        assertContains("<ol>\t<li><p>One</p>", xml);
        assertContains("</ol>", xml);
        assertContains("<ul>\t<li><p>First</p>", xml);
        assertContains("</ul>", xml);
    }

    //TIKA-1600: Test that null pointer doesn't break parsing.
    @Test
    public void testNullStylesInODTFooter() throws Exception {
        Parser parser = new OpenDocumentParser();
        try (InputStream input = getResourceAsStream("/test-documents/testODT-TIKA-6000.odt")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            parser.parse(input, handler, metadata, getNonRecursingParseContext());

            assertEquals("application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));

            String content = handler.toString();

            assertContains("Utilisation de ce document", content);
            assertContains("Copyright and License", content);
            assertContains("Changer la langue", content);
            assertContains("La page d’accueil permet de faire une recherche simple", content);
        }
    }

    @Test  //TIKA-1916
    public void testMissingMeta() throws Exception {
        String xml = getXML("testODTNoMeta.odt").xml;
        assertContains("Test text", xml);
    }

    @Test //TIKA-2242
    public void testParagraphLevelFontStyles() throws Exception {
        String xml = getXML("testODTStyles2.odt", getNonRecursingParseContext()).xml;
        //test text span font-style properties
        assertContains("<p><b>name</b>, advocaat", xml);
        //test paragraph's font-style properties
        assertContains("<p><b>Publicatie Onbekwaamverklaring", xml);
    }

    @Test //TIKA-2242
    public void testAnnotationsAndPDepthGt1() throws Exception {
        //not allowed in html: <p> <annotation> <p> this is an annotation </p> </annotation> </p>
        String xml = getXML("testODTStyles3.odt").xml;
        assertContains(
                "<p><b>WOUTERS Rolf</b><p class=\"annotation\"> Beschermde persoon is " +
                        "overleden </p>",
                xml);
    }

    @Test
    public void testEmbedded() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testODTEmbedded.odt");
        assertEquals(3, metadataList.size());
    }

    @Test
    public void testEmbeddedImageAndLink() throws Exception {
        String xml = getXML("testODTEmbeddedImageLink.odt").xml;
        assertContains("<a href=\"https://tika.apache.org/\">" +
                "<img src=\"embedded:Pictures/10000201000001240000006457F5B1D1243E0671.png\" />" +
                "<span>Visit Tika</span></a>", xml);
    }

    @Test
    public void testInvalidFromStream() throws Exception {
        try (InputStream is = getResourceAsUrl("/test-documents/testODTnotaZipFile.odt")
                .openStream()) {
            OpenDocumentParser parser = new OpenDocumentParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            assertThrows(IOException.class, () -> {
                parser.parse(is, handler, metadata, new ParseContext());
            });
        }
    }

    @Test
    public void testInvalidFromFile() throws Exception {
        try (TikaInputStream is = TikaInputStream
                .get(getResourceAsUrl("/test-documents/testODTnotaZipFile.odt"))) {
            OpenDocumentParser parser = new OpenDocumentParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            assertThrows(IOException.class, () -> {
                parser.parse(is, handler, metadata, new ParseContext());
            });
        }
    }

    @Test
    public void testEncryptedODTFile() throws Exception {
        //the password to this file is "tika"
        Path p =
                Paths.get(
                        ODFParserTest.class.getResource(
                                "/test-documents/testODTEncrypted.odt").toURI());
        assertThrows(EncryptedDocumentException.class, () -> {
            getRecursiveMetadata(p, false);
        });
    }

    //this, of course, should throw an EncryptedDocumentException
    //but the file can't be read by Java's ZipInputStream or
    //by commons compress, unless you enable descriptors.
    //https://issues.apache.org/jira/browse/ODFTOOLKIT-402
    @Test
    public void testEncryptedODTStream() throws Exception {
        try (InputStream is = ODFParserTest.class.getResourceAsStream(
                "/test-documents/testODTEncrypted.odt")) {
            assertThrows(TikaException.class, () -> {
                getRecursiveMetadata(is, false);
            });
        }
    }

    private ParseContext getNonRecursingParseContext() {
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, new EmptyParser());
        return parseContext;
    }

    @Test
    public void testMultiThreaded() throws Exception {
        int numThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Integer> executorCompletionService =
                new ExecutorCompletionService<>(executorService);

        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(() -> {
                for (int i1 = 0; i1 < 10; i1++) {
                    List<Metadata> metadataList = getRecursiveMetadata("testODTEmbedded.odt");
                    assertEquals(3, metadataList.size());
                    assertEquals("THUMBNAIL",
                            metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
                }
                return 1;
            });
        }

        try {
            int finished = 0;
            while (finished < numThreads) {
                Future<Integer> future = executorCompletionService.take();
                future.get();
                finished++;
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    public void testODTXHTMLIsParseable() throws Exception {
        //for all OpenDocument files, make sure that the
        //output from the parse is parseable xhtml
        int filesTested = 0;
        for (Path p : getAllTestFiles()) {
            String fileName = p.getFileName().toString();
            if (fileName.endsWith(".odt") || fileName.endsWith("odp") || fileName.endsWith("odf") ||
                    fileName.endsWith(".ods")) {

                XMLResult xmlResult = null;
                try (InputStream is = TikaInputStream.get(p)) {
                    xmlResult = getXML(is, AUTO_DETECT_PARSER, new Metadata());
                } catch (Exception e) {
                    continue;
                }
                try {
                    //just make sure this doesn't throw any exceptions
                    XMLReaderUtils.parseSAX(new ByteArrayInputStream(xmlResult.xml.getBytes(StandardCharsets.UTF_8)),
                            new DefaultHandler(), new ParseContext());
                    filesTested++;
                } catch (Exception e) {
                    fail(p.getFileName().toString(), e);
                }
            }
        }
        assertTrue(filesTested > 10);
    }

    @Test
    public void testVersions() throws Exception {
        //test at least that all files from
        // https://github.com/openpreserve/format-corpus/tree/master/office-examples/LibreOffice7-ODF-1.3
        //pass as 1.3.  Note that we don't currently parse base files, so skip that one.
        for (String name : new String[]{
                //"LibreOfficeBase_odb_1.3.odb",
                "LibreOfficeCalc_ods_1.3.ods",
                "LibreOfficeDraw_odg_1.3.odg",
                "LibreOfficeImpress_odp_1.3.odp",
                "LibreOfficeWriter_odt_1.3.odt",
        }) {
            List<Metadata> metadataList = getRecursiveMetadata("/versions/" + name);
            Metadata metadata = metadataList.get(0);
            assertEquals("1.3", metadata.get(OpenDocumentMetaParser.ODF_VERSION_KEY), "failed on " + name);
        }
    }
}
