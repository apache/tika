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
package org.apache.tika.parser.pdf;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.parser.xml.XMLProfiler;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class PDFParserTest extends TikaTest {
    public static Level PDFBOX_LOG_LEVEL = Level.INFO;

    private static Boolean hasTesseract = null;

    public static boolean canRunOCR() {
        if (hasTesseract != null) {
            return hasTesseract;
        }
        hasTesseract = new TesseractOCRParser().hasTesseract(new TesseractOCRConfig());
        return hasTesseract;
    }

    @BeforeClass
    public static void setup() {
        //remember default logging level, but turn off for PDFParserTest
        PDFBOX_LOG_LEVEL = Logger.getLogger("org.apache.pdfbox").getLevel();
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.OFF);
    }

    @AfterClass
    public static void tearDown() {
        //return to regular logging level
        Logger.getLogger("org.apache.pdfbox").setLevel(PDFBOX_LOG_LEVEL);
    }
    public static final MediaType TYPE_TEXT = MediaType.TEXT_PLAIN;
    public static final MediaType TYPE_EMF = MediaType.image("emf");
    public static final MediaType TYPE_PDF = MediaType.application("pdf");
    public static final MediaType TYPE_DOCX = MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document");
    public static final MediaType TYPE_DOC = MediaType.application("msword");


    @Test
    public void testXMLProfiler() throws Exception {
        //test that the xml profiler is not triggered by default
        List<Metadata> metadataList = getRecursiveMetadata("testPDF_XFA_govdocs1_258578.pdf");
        assertEquals(1, metadataList.size());

        //test that it is triggered when added to the default parser
        //via the config, tesseract should skip this file because it is too large
        InputStream is = getClass().getResourceAsStream("/org/apache/tika/parser/pdf/tika-xml-profiler-config.xml");
        assertNotNull(is);
        TikaConfig tikaConfig = new TikaConfig(is);
        Parser p = new AutoDetectParser(tikaConfig);

        metadataList = getRecursiveMetadata("testPDF_XFA_govdocs1_258578.pdf", p);
        assertEquals(3, metadataList.size());

        int xmlProfilers = 0;
        for (Metadata metadata : metadataList) {
            String[] parsedBy = metadata.getValues("X-Parsed-By");
            for (int i = 0; i < parsedBy.length; i++) {
                if (parsedBy[i].equals(XMLProfiler.class.getCanonicalName())) {
                    xmlProfilers++;
                }
            }
        }

        assertEquals(2, xmlProfilers);

        //check xmp first
        String[] uris = metadataList.get(1).getValues(XMLProfiler.ENTITY_URIS);
        String[] localNames = metadataList.get(1).getValues(XMLProfiler.ENTITY_LOCAL_NAMES);
        assertEquals(8, uris.length);
        assertEquals(uris.length, localNames.length);
        assertEquals("adobe:ns:meta/", uris[0]);
        assertEquals("CreateDate CreatorTool MetadataDate ModifyDate Thumbnails", localNames[2]);
        assertEquals("x:xmpmeta", metadataList.get(1).get(XMLProfiler.ROOT_ENTITY));

        //check xfa
        uris = metadataList.get(2).getValues(XMLProfiler.ENTITY_URIS);
        localNames = metadataList.get(2).getValues(XMLProfiler.ENTITY_LOCAL_NAMES);
        assertEquals(8, uris.length);
        assertEquals(uris.length, localNames.length);
        assertEquals("http://ns.adobe.com/xdp/", uris[1]);
        assertEquals("field form instanceManager subform value", localNames[5]);
        assertEquals("xdp:xdp", metadataList.get(2).get(XMLProfiler.ROOT_ENTITY));
    }

    @Test //TIKA-1374
    public void testOSSpecificEmbeddedFileExtraction() throws Exception {
        List<Metadata> metadatas = getRecursiveMetadata("testPDF_multiFormatEmbFiles.pdf");
        assertEquals("metadata size", 5, metadatas.size());

        assertEquals("file name", "Test.txt", metadatas.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertContains("os specific", metadatas.get(1).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
        assertEquals("file name", "TestMac.txt", metadatas.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertContains("mac embedded", metadatas.get(2).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
        assertEquals("file name", "TestDos.txt", metadatas.get(3).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertContains("dos embedded", metadatas.get(3).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
        assertEquals("file name", "TestUnix.txt", metadatas.get(4).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertContains("unix embedded", metadatas.get(4).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));

    }

    //TIKA-1124
    @Test
    public void testEmbeddedPDFEmbeddingAnotherDocument() throws Exception {
       /* format of test doc:
         docx/
            pdf/
               docx
       */

        String content = getXML("testPDFEmbeddingAndEmbedded.docx").xml;
        int outerHaystack = content.indexOf("Outer_haystack");
        int pdfHaystack = content.indexOf("pdf_haystack");
        int needle = content.indexOf("Needle");
        assertTrue(outerHaystack > -1);
        assertTrue(pdfHaystack > -1);
        assertTrue(needle > -1);
        assertTrue(needle > pdfHaystack && pdfHaystack > outerHaystack);

        TrackingHandler tracker = new TrackingHandler();

        ContainerExtractor ex = new ParserContainerExtractor();
        try (TikaInputStream tis =
                     TikaInputStream.get(getResourceAsStream("/test-documents/testPDFEmbeddingAndEmbedded.docx"))) {
            ex.extract(tis, ex, tracker);
        }

        assertEquals(3, tracker.filenames.size());
        assertEquals(3, tracker.mediaTypes.size());
        assertEquals("image1.emf", tracker.filenames.get(0));
        assertNull(tracker.filenames.get(1));
        assertEquals("Test.docx", tracker.filenames.get(2));
        assertEquals(TYPE_EMF, tracker.mediaTypes.get(0));
        assertEquals(TYPE_PDF, tracker.mediaTypes.get(1));
        assertEquals(TYPE_DOCX, tracker.mediaTypes.get(2));
    }

    @Test // TIKA-1228, TIKA-1268
    public void testEmbeddedFilesInChildren() throws Exception {
        String xml = getXML("testPDF_childAttachments.pdf").xml;
        //"regressiveness" exists only in Unit10.doc not in the container pdf document
        assertTrue(xml.contains("regressiveness"));

        RecursiveParserWrapper p = new RecursiveParserWrapper(AUTO_DETECT_PARSER);
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);
        context.set(org.apache.tika.parser.pdf.PDFParserConfig.class, config);
        context.set(org.apache.tika.parser.Parser.class, p);

        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE,-1));
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/testPDF_childAttachments.pdf"))) {
            p.parse(tis, handler, new Metadata(), context);
        }

        List<Metadata> metadatas = handler.getMetadataList();

        assertEquals(5, metadatas.size());
        assertNull(metadatas.get(0).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("image0.jpg", metadatas.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("Press Quality(1).joboptions", metadatas.get(3).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("Unit10.doc", metadatas.get(4).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(MediaType.image("jpeg").toString(), metadatas.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals(MediaType.image("tiff").toString(), metadatas.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("text/plain; charset=ISO-8859-1", metadatas.get(3).get(Metadata.CONTENT_TYPE));
        assertEquals(TYPE_DOC.toString(), metadatas.get(4).get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testEmbeddedDocsWithOCROnly() throws Exception {
        assumeTrue("can run OCR", canRunOCR());

        for (PDFParserConfig.OCR_STRATEGY strategy : PDFParserConfig.OCR_STRATEGY.values()) {
            PDFParserConfig config = new PDFParserConfig();
            config.setOcrStrategy(strategy);
            ParseContext context = new ParseContext();
            context.set(PDFParserConfig.class, config);
            //make sure everything works with regular xml _and_ with recursive
            XMLResult xmlResult = getXML("testPDFEmbeddingAndEmbedded.docx", context);
            //can get dehaystack depending on version of tesseract and/or preprocessing
            if (xmlResult.xml.contains("pdf_haystack") || xmlResult.xml.contains("dehaystack")) {
                //great
            } else {
                fail("couldn't find pdf_haystack or its variants");
            }
            assertContains("Haystack", xmlResult.xml);
            assertContains("Needle", xmlResult.xml);
            if (! strategy.equals(PDFParserConfig.OCR_STRATEGY.NO_OCR)) {
                // Tesseract may see the t in haystack as a ! some times...
                //or it might see dehayslack...
                //TODO: figure out how to make this test less hacky
                String div = "<div class=\"ocr\">";
                if (xmlResult.xml.contains(div+"pdf_hays!ack")) {
                } else if (xmlResult.xml.contains(div+"pdf_haystack")) {
                } else if (xmlResult.xml.contains(div+"dehayslack")) {
                } else {
                    fail("couldn't find acceptable variants of haystack");
                }
            } else {
                assertNotContained("<div class=\"ocr\">pdf_haystack", xmlResult.xml);
            }
            assertEquals(4, getRecursiveMetadata("testPDFEmbeddingAndEmbedded.docx", context).size());
        }

    }


    @Test
    public void testFileInAnnotationExtractedIfNoContents() throws Exception {
        //TIKA-2845
        List<Metadata> contents = getRecursiveMetadata("testPDFFileEmbInAnnotation_noContents.pdf");
        assertEquals(2, contents.size());
        assertContains("This is a Excel", contents.get(1).get(RecursiveParserWrapperHandler.TIKA_CONTENT));
    }

    @Test
    public void testEmbeddedFilesInAnnotations() throws Exception {
        String xml = getXML("testPDFFileEmbInAnnotation.pdf").xml;

        assertTrue(xml.contains("This is a Excel"));
    }
}
