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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.TikaLoaderHelper;
import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.image.ImageMetadataExtractor;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.parser.xml.XMLProfiler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;
import org.apache.tika.serialization.serdes.ParseContextSerializer;
import org.apache.tika.utils.StringUtils;

public class PDFParserTest extends TikaTest {
    public static final MediaType TYPE_TEXT = MediaType.TEXT_PLAIN;
    public static final MediaType TYPE_EMF = MediaType.image("emf");
    public static final MediaType TYPE_PDF = MediaType.application("pdf");
    public static final MediaType TYPE_DOCX =
            MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document");
    public static final MediaType TYPE_DOC = MediaType.application("msword");
    public static Level PDFBOX_LOG_LEVEL = Level.INFO;
    private static Boolean hasTesseract = null;

    private static Boolean hasMuPDF = null;

    public static boolean canRunOCR() throws TikaConfigException {
        if (hasTesseract != null) {
            return hasTesseract;
        }
        hasTesseract = new TesseractOCRParser().hasTesseract();
        return hasTesseract;
    }

    public static boolean hasMuPDF() throws TikaConfigException {
        if (hasMuPDF != null) {
            return hasMuPDF;
        }
        hasMuPDF = ExternalParser.check(new String[]{"mutool", "-v"});
        return hasMuPDF;
    }

    @BeforeAll
    public static void setup() {
        //remember default logging level, but turn off for PDFParserTest
        PDFBOX_LOG_LEVEL = Logger.getLogger("org.apache.pdfbox").getLevel();
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.OFF);
    }

    @AfterAll
    public static void tearDown() {
        //return to regular logging level
        Logger.getLogger("org.apache.pdfbox").setLevel(PDFBOX_LOG_LEVEL);
    }

    private static ParseContext NO_OCR() {
        PDFParserConfig config = new PDFParserConfig();
        config.setOcrStrategy(OcrConfig.Strategy.NO_OCR);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        return context;
    }

    @Test
    public void testXMLProfiler() throws Exception {
        //test that the xml profiler is not triggered by default
        List<Metadata> metadataList =
                getRecursiveMetadata("testPDF_XFA_govdocs1_258578.pdf", NO_OCR());
        assertEquals(1, metadataList.size());

        Parser p = TikaLoaderHelper.getLoader("tika-xml-profiler-config.json").loadAutoDetectParser();

        metadataList = getRecursiveMetadata("testPDF_XFA_govdocs1_258578.pdf", p);
        assertEquals(3, metadataList.size());

        int xmlProfilers = 0;
        for (Metadata metadata : metadataList) {
            String[] parsedBy = metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY);
            for (String s : parsedBy) {
                if (s.equals(XMLProfiler.class.getCanonicalName())) {
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
        List<Metadata> metadatas =
                getRecursiveMetadata("testPDF_multiFormatEmbFiles.pdf", NO_OCR());
        assertEquals(5, metadatas.size(), "metadata size");

        assertEquals("Test.txt",
                metadatas.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertContains("os specific", metadatas.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("TestMac.txt",
                metadatas.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertContains("mac embedded", metadatas.get(2).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("TestDos.txt",
                metadatas.get(3).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertContains("dos embedded", metadatas.get(3).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("TestUnix.txt",
                metadatas.get(4).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertContains("unix embedded", metadatas.get(4).get(TikaCoreProperties.TIKA_CONTENT));

    }

    //TIKA-1124
    @Test
    public void testEmbeddedPDFEmbeddingAnotherDocument() throws Exception {
       /* format of test doc:
         docx/
            pdf/
               docx
       */

        String content = getXML("testPDFEmbeddingAndEmbedded.docx", NO_OCR()).xml;
        int outerHaystack = content.indexOf("Outer_haystack");
        int pdfHaystack = content.indexOf("pdf_haystack");
        int needle = content.indexOf("Needle");
        assertTrue(outerHaystack > -1);
        assertTrue(pdfHaystack > -1);
        assertTrue(needle > -1);
        assertTrue(needle > pdfHaystack && pdfHaystack > outerHaystack);

        TrackingHandler tracker = new TrackingHandler();
        ParseContext context = new ParseContext();
        ContainerExtractor ex = new ParserContainerExtractor();
        try (TikaInputStream tis = TikaInputStream
                .get(getResourceAsStream("/test-documents/testPDFEmbeddingAndEmbedded.docx"))) {
            ex.extract(tis, ex, tracker, context);
        }

        assertEquals(3, tracker.filenames.size());
        assertEquals(3, tracker.mediaTypes.size());
        assertEquals("image1.emf", tracker.filenames.get(0));
        assertEquals("attached.pdf", tracker.filenames.get(1));
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
        config.setOcrStrategy(OcrConfig.Strategy.NO_OCR);
        context.set(org.apache.tika.parser.pdf.PDFParserConfig.class, config);
        context.set(org.apache.tika.parser.Parser.class, p);

        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));
        try (TikaInputStream tis = TikaInputStream
                .get(getResourceAsStream("/test-documents/testPDF_childAttachments.pdf"))) {
            p.parse(tis, handler, new Metadata(), context);
        }

        List<Metadata> metadatas = handler.getMetadataList();
        assertEquals(5, metadatas.size());
        assertNull(metadatas.get(0).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("image0.jpg", metadatas.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("Press Quality(1).joboptions",
                metadatas.get(3).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("Unit10.doc", metadatas.get(4).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertImageContentType("image/jpeg",
                metadatas.get(1).get(Metadata.CONTENT_TYPE));
        assertImageContentType("image/tiff",
                metadatas.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("text/plain; charset=ISO-8859-1", metadatas.get(3).get(Metadata.CONTENT_TYPE));
        assertEquals(TYPE_DOC.toString(), metadatas.get(4).get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testEmbeddedDocsWithOCROnly() throws Exception {
        assumeTrue(canRunOCR(), "can't run OCR");
        //test default is "auto"
        assertEquals(OcrConfig.Strategy.AUTO, new PDFParserConfig().getOcrStrategy());
        testStrategy(null);
        //now test other options
        for (OcrConfig.Strategy strategy : OcrConfig.Strategy.values()) {
            testStrategy(strategy);
        }
    }

    private void testStrategy(OcrConfig.Strategy strategy) throws Exception {
        //make sure everything works with regular xml _and_ with recursive
        ParseContext context = new ParseContext();
        if (strategy != null) {
            PDFParserConfig config = new PDFParserConfig();
            config.setOcrStrategy(strategy);
            context.set(PDFParserConfig.class, config);
        };
        PDFParserConfig config = context.get(PDFParserConfig.class, new PDFParserConfig());
        config.setOcrRenderingStrategy(OcrConfig.RenderingStrategy.ALL);
        context.set(PDFParserConfig.class, config);
        XMLResult xmlResult = getXML("testPDFEmbeddingAndEmbedded.docx", context);

        //can get dehaystack depending on version of tesseract and/or preprocessing
        if (xmlResult.xml.contains("pdf_haystack") || xmlResult.xml.contains("dehaystack")) {
            //great
        } else {
            fail("couldn't find pdf_haystack or its variants");
        }
        assertContains("Haystack", xmlResult.xml);
        assertContains("Needle", xmlResult.xml);
        if (strategy == null || strategy != OcrConfig.Strategy.NO_OCR) {
            // Tesseract may see the t in haystack as a ! some times...
            //or it might see dehayslack...
            //TODO: figure out how to make this test less hacky
            String div = "<div class=\"ocr\">";
            if (xmlResult.xml.contains(div + "pdf_hays!ack")) {
            } else if (xmlResult.xml.contains(div + "pdf_haystack")) {
            } else if (xmlResult.xml.contains(div + "dehayslack")) {
            } else {
                fail("couldn't find acceptable variants of haystack");
            }
        } else {
            assertNotContained("<div class=\"ocr\">pdf_haystack", xmlResult.xml);
        }
        assertEquals(4, getRecursiveMetadata("testPDFEmbeddingAndEmbedded.docx", context).size());
    }


    @Test
    public void testFileInAnnotationExtractedIfNoContents() throws Exception {
        //TIKA-2845
        List<Metadata> contents =
                getRecursiveMetadata("testPDFFileEmbInAnnotation_noContents.pdf", NO_OCR());
        assertEquals(2, contents.size());
        assertContains("This is a Excel", contents.get(1).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testEmbeddedFilesInAnnotations() throws Exception {
        String xml = getXML("testPDFFileEmbInAnnotation.pdf", NO_OCR()).xml;

        assertTrue(xml.contains("This is a Excel"));
    }

    @Test
    public void testEmbeddedJPEG() throws Exception {
        //TIKA-1990, test that an embedded jpeg is correctly decoded
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setOcrStrategy(OcrConfig.Strategy.NO_OCR);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);

        List<Metadata> metadataList = getRecursiveMetadata("testPDF_childAttachments.pdf", context);
        //shouldn't change
        assertEquals(5, metadataList.size());
        //inlined jpeg metadata
        Metadata jpegMetadata = metadataList.get(1);
        assertImageContentType("image/jpeg", jpegMetadata.get(Metadata.CONTENT_TYPE));
        //the metadata parse will fail if the stream is not correctly decoded
        assertEquals("1425", jpegMetadata.get(Metadata.IMAGE_LENGTH));
    }

    @Test // TIKA-2232
    public void testEmbeddedJBIG2Image() throws Exception {

        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);
        config.setOcrStrategy(OcrConfig.Strategy.NO_OCR);
        context.set(PDFParserConfig.class, config);


        List<Metadata> metadatas = getRecursiveMetadata("testPDF_JBIG2.pdf", context);
        assertEquals(2, metadatas.size());
        assertContains("test images compressed using JBIG2",
                metadatas.get(0).get(TikaCoreProperties.TIKA_CONTENT));

        for (String key : metadatas.get(1).names()) {
            if (key.startsWith("X-TIKA:EXCEPTION")) {
                fail("Exception: " + metadatas.get(1).get(key));
            }
        }
        assertEquals("91", metadatas.get(1).get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "height"));
        assertEquals("352", metadatas.get(1).get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "width"));

        assertEquals("testPDF_JBIG2.pdf", metadatas.get(0).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("image0.jb2", metadatas.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(MediaType.image("x-jbig2").toString(),
                metadatas.get(1).get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testJBIG2OCROnly() throws Exception {
        assumeTrue(canRunOCR(), "can't run OCR");
        PDFParserConfig config = new PDFParserConfig();
        config.setOcrStrategy(OcrConfig.Strategy.OCR_ONLY);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        //make sure everything works with regular xml _and_ with recursive
        XMLResult xmlResult = getXML("testPDF_JBIG2.pdf", context);
        assertContains("Norconex", xmlResult.xml);
    }

    @Test
    public void testJPEG2000() throws Exception {
        assumeTrue(canRunOCR(), "can't run OCR");
        PDFParserConfig config = new PDFParserConfig();
        config.setOcrStrategy(OcrConfig.Strategy.OCR_ONLY);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        //make sure everything works with regular xml _and_ with recursive
        XMLResult xmlResult = getXML("testPDF_jpeg2000.pdf", context);
        assertContains("loan", xmlResult.xml.toLowerCase(Locale.US));
    }

    @Test
    public void testOCRAutoMode() throws Exception {
        assumeTrue(canRunOCR(), "can't run OCR");

        //default
        assertContains("Happy New Year", getXML("testOCR.pdf").xml);

        PDFParserConfig config = new PDFParserConfig();
        config.setOcrStrategy(OcrConfig.Strategy.AUTO);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        XMLResult xmlResult = getXML("testOCR.pdf", context);
        assertContains("Happy New Year", xmlResult.xml);

        config.setOcrStrategy(OcrConfig.Strategy.NO_OCR);
        String txt = getText("testOCR.pdf", new Metadata(), context);
        assertEquals("", txt.trim());
    }

    @Test
    public void testOCRNoText() throws Exception {
        assumeTrue(canRunOCR(), "can't run OCR");
        PDFParserConfig config = new PDFParserConfig();
        config.setOcrRenderingStrategy(OcrConfig.RenderingStrategy.ALL);
        config.setOcrStrategy(OcrConfig.Strategy.OCR_ONLY);
        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, config);
        XMLResult xmlResult = getXML("testPDF_XFA_govdocs1_258578.pdf", parseContext);
        assertContains("PARK", xmlResult.xml);
        assertContains("Applications", xmlResult.xml);

        config.setOcrRenderingStrategy(OcrConfig.RenderingStrategy.NO_TEXT);
        config.setOcrStrategy(OcrConfig.Strategy.OCR_ONLY);
        parseContext.set(PDFParserConfig.class, config);
        xmlResult = getXML("testPDF_XFA_govdocs1_258578.pdf", parseContext);
        assertContains("NATIONAL", xmlResult.xml);
        assertNotContained("Applications", xmlResult.xml);
    }

    @Test
    public void testTesseractInitializationWorks() throws Exception {
        //TIKA-2970 -- make sure that configurations set on the TesseractOCRParser
        //make it through to when the TesseractOCRParser is called via
        //the PDFParser
        assumeTrue(canRunOCR(), "can't run OCR");

        //via the config, tesseract should skip this file because it is too large
        Parser p = TikaLoaderHelper.getLoader("tika-config-ocr-for-pdf.json").loadAutoDetectParser();
        String text = getText(getResourceAsStream("/test-documents/testOCR.pdf"), p);
        assertEquals("", text.trim());

        //now override the max file size to ocr, and you should get text
        ParseContext pc = new ParseContext();
        TesseractOCRConfig tesseractOCRConfig = new TesseractOCRConfig();
        tesseractOCRConfig.setMaxFileSizeToOcr(10000000);
        pc.set(TesseractOCRConfig.class, tesseractOCRConfig);
        text = getText(getResourceAsStream("/test-documents/testOCR.pdf"), p, pc);
        assertContains("Happy", text);
    }

    @Test
    @Disabled("there's a subtle problem in setting the bytes in the TikaInputStream that needs to be fixed")
    public void testMuPDFInOCR() throws Exception {
        //TODO -- need to add "rendered by" to confirm that mutool was actually called
        //and that there wasn't some backoff to PDFBox the PDFParser
        assumeTrue(canRunOCR(), "can't run OCR");
        assumeTrue(hasMuPDF(), "does not have mupdf");
        Parser p = TikaLoaderHelper.getLoader("tika-rendering-mupdf-config.json").loadAutoDetectParser();
        String text = getText(getResourceAsStream("/test-documents/testOCR.pdf"), p);
        assertContains("Happy", text.trim());
    }

    @Test
    public void testIncrementalUpdatesInAnAttachedPDF() throws Exception {
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setParseIncrementalUpdates(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, pdfParserConfig);
        List<Metadata> metadataList = getRecursiveMetadata("test-incremental-updates.eml", parseContext);
        assertEquals(4, metadataList.size());
        assertEquals(2, metadataList.get(3).getInt(PDF.PDF_INCREMENTAL_UPDATE_COUNT));
        assertEquals(2,
                metadataList.get(3).getInt(TikaCoreProperties.VERSION_COUNT));
        long[] expected = new long[]{16242, 41226, 64872};
        long[] eofs = metadataList.get(3).getLongValues(PDF.EOF_OFFSETS);
        assertEquals(3, eofs.length);
        assertArrayEquals(expected, eofs);

        assertNotContained("Testing Incremental",
                metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("Testing Incremental",
                metadataList.get(2).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("Testing Incremental",
                metadataList.get(3).get(TikaCoreProperties.TIKA_CONTENT));

        assertNull(metadataList.get(0).get(PDF.INCREMENTAL_UPDATE_NUMBER));
        assertNull(metadataList.get(3).get(PDF.INCREMENTAL_UPDATE_NUMBER));
        assertEquals(0, metadataList.get(1).getInt(PDF.INCREMENTAL_UPDATE_NUMBER));
        assertEquals(1, metadataList.get(2).getInt(PDF.INCREMENTAL_UPDATE_NUMBER));

        assertEquals("/testPDF_incrementalUpdates.pdf/version-number-0",
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertEquals("/testPDF_incrementalUpdates.pdf/version-number-1",
                metadataList.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));

        assertEquals(TikaCoreProperties.EmbeddedResourceType.VERSION.toString(),
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.VERSION.toString(),
                metadataList.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testJavascriptInNamesTreeOne() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractActions(true);
        ParseContext pc = new ParseContext();
        pc.set(PDFParserConfig.class, config);
        List<Metadata> metadataList = getRecursiveMetadata("testPDFPackage.pdf", pc, true);
        assertEquals(4, metadataList.size());
        //look for markup in primary document
        Metadata m = metadataList.get(0);
        String xhtml = m.get(TikaCoreProperties.TIKA_CONTENT);
        Matcher matcher = Pattern.compile("<div ([^>]{0,1000})>").matcher(xhtml);
        boolean found = false;
        while (matcher.find()) {
            String div = matcher.group(1);
            if (div.contains("trigger=\"namesTree\"")) {
                assertContains("type=\"PDActionJavaScript\"", div);
                assertContains("class=\"javascript\"", div);
                assertContains("subtype=\"JavaScript\"", div);
                found = true;
            }
        }
        if (! found) {
            fail("failed to find js div in main document");
        }
        //now test js extraction
        Metadata js = metadataList.get(1);
        assertEquals("MACRO", js.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("NAMES_TREE", js.get(PDF.ACTION_TRIGGER));
        assertTrue(js.get(PDF.JS_NAME).startsWith("ADBE::FileAttachmentsCompatibility"));
        assertContains("app.viewerVersion", js.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testJavascriptInNamesTreeTwo() throws Exception {
        Set<String> expected = Set.of("!ADBE::0200_VersChkCode_XFACheck", "!ADBE::0100_VersChkVars", "!ADBE::0100_VersChkStrings");
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractActions(true);
        ParseContext pc = new ParseContext();
        pc.set(PDFParserConfig.class, config);
        List<Metadata> metadataList = getRecursiveMetadata("testPDF_XFA_govdocs1_258578.pdf", pc, true);
        Set<String> jsNames = new HashSet<>();
        for (Metadata m : metadataList) {
            String n = m.get(PDF.JS_NAME);
            if (!StringUtils.isBlank(n)) {
                jsNames.add(n);
            }
        }
        assertEquals(expected, jsNames);
    }

    @Test
    @Disabled("until we can sort the license of the test file")
    public void testJavascriptOnInstantiate() throws Exception {
        // test file: https://pdfa.org/wp-content/uploads/2021/12/Make-Buy-BOM-to-EBOM-Alignment-Example.pdf
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractActions(true);
        ParseContext pc = new ParseContext();
        pc.set(PDFParserConfig.class, config);
        List<Metadata> metadataList = getRecursiveMetadata("Make-Buy-BOM-to-EBOM-Alignment-Example.pdf", pc, true);
        assertEquals(6, metadataList.size());
        Metadata onInstantiate = metadataList.get(4);
        assertContains("scene.cameras.getByIndex", onInstantiate.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("MACRO", onInstantiate.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("3DD_ON_INSTANTIATE", onInstantiate.get(PDF.ACTION_TRIGGER));

        //test that the additional actions on the 3d object are processed
        assertContains("this.notify3DAnnotPageOpen()", metadataList.get(5).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testPDFParserConfigSerialization() throws Exception {
        // Test that PDFParserConfig can be serialized and deserialized through ParseContext
        PDFParserConfig config = new PDFParserConfig();
        config.setSortByPosition(true);
        config.setExtractInlineImages(true);
        config.setOcrStrategy(OcrConfig.Strategy.AUTO);

        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, config);

        // Serialize using ParseContextSerializer
        com.fasterxml.jackson.databind.ObjectMapper mapper = TikaObjectMapperFactory.getMapper();
        com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(ParseContext.class, new ParseContextSerializer());
        module.addDeserializer(ParseContext.class, new ParseContextDeserializer());
        mapper.registerModule(module);

        String json = mapper.writeValueAsString(parseContext);
        // Deserialize
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);

        // Verify PDFParserConfig was preserved - get it directly from ParseContext
        PDFParserConfig deserializedConfig = deserialized.get(PDFParserConfig.class);

        assertNotNull(deserializedConfig, "PDFParserConfig should not be null after deserialization");
        assertTrue(deserializedConfig.isSortByPosition(),
                "sortByPosition should be preserved");
        assertTrue(deserializedConfig.isExtractInlineImages(),
                "extractInlineImages should be preserved");
        assertEquals(OcrConfig.Strategy.AUTO, deserializedConfig.getOcrStrategy(),
                "ocrStrategy should be preserved");
    }

    @Test
    public void testRenderingBasic() throws Exception {
        ParseContext parseContext = configureRenderingParseContext();
        Parser p = TikaLoaderHelper.getLoader("tika-config-rendering.json").loadAutoDetectParser();
        List<Metadata> metadataList = getRecursiveMetadata("testPDF.pdf", p, parseContext);
        Map<Integer, byte[]> embedded =
                ((RenderCaptureExtractor) parseContext.get(EmbeddedDocumentExtractor.class))
                        .getEmbedded();
        assertEquals(1, embedded.size());
        assertTrue(embedded.containsKey(0));
        assertTrue(embedded.get(0).length > 1000);

        assertEquals(2, metadataList.size());
        Metadata tiffMetadata = metadataList.get(1);
        assertEquals("RENDERING", tiffMetadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(1, tiffMetadata.getInt(TikaPagedText.PAGE_NUMBER));
    }

    @Test
    public void testRenderingRotated() throws Exception {
        ParseContext parseContext = configureRenderingParseContext();
        Parser p = TikaLoaderHelper.getLoader("tika-config-rendering.json").loadAutoDetectParser();
        List<Metadata> metadataList = getRecursiveMetadata("testPDF_rotated.pdf", p, parseContext);
        Map<Integer, byte[]> embedded =
                ((RenderCaptureExtractor) parseContext.get(EmbeddedDocumentExtractor.class))
                        .getEmbedded();

        assertEquals(1, embedded.size());
        assertTrue(embedded.containsKey(0));
        assertTrue(embedded.get(0).length > 1000);

        assertEquals(2, metadataList.size());
        Metadata tiffMetadata = metadataList.get(1);
        assertEquals("RENDERING", tiffMetadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(1, tiffMetadata.getInt(TikaPagedText.PAGE_NUMBER));
        assertEquals(90.0, Double.parseDouble(tiffMetadata.get(TikaPagedText.PAGE_ROTATION)), 0.1);
    }

    @Test
    public void testInitializationOfNonPrimitivesViaJsonConfig() throws Exception {
        Parser p = TikaLoaderHelper.getLoader("tika-config-non-primitives.json").loadAutoDetectParser();
        assertTrue(p instanceof org.apache.tika.parser.AutoDetectParser);
        org.apache.tika.parser.AutoDetectParser adp = (org.apache.tika.parser.AutoDetectParser) p;
        java.util.Map<MediaType, Parser> parsers = adp.getParsers();
        Parser composite = parsers.get(MediaType.application("pdf"));
        Parser pdfParser =
                ((org.apache.tika.parser.CompositeParser) composite).getParsers()
                        .get(MediaType.application("pdf"));
        assertEquals("org.apache.tika.parser.pdf.PDFParser",
                pdfParser.getClass().getName());
        assertEquals(OcrConfig.Strategy.OCR_ONLY,
                ((PDFParser) pdfParser).getPDFParserConfig().getOcrStrategy());
        assertEquals(OcrConfig.ImageType.RGB,
                ((PDFParser) pdfParser).getPDFParserConfig().getOcrImageType());
    }

    private ParseContext configureRenderingParseContext() {
        ParseContext parseContext = new ParseContext();
        parseContext.set(EmbeddedDocumentExtractor.class, new RenderCaptureExtractor(parseContext));
        return parseContext;
    }

    private static class RenderCaptureExtractor extends ParsingEmbeddedDocumentExtractor {
        private int count = 0;
        Map<Integer, byte[]> embedded = new HashMap<>();

        public RenderCaptureExtractor(ParseContext context) {
            super(context);
        }

        @Override
        public void parseEmbedded(TikaInputStream tis, ContentHandler handler, Metadata metadata, ParseContext parseContext, boolean outputHtml) throws SAXException, IOException {

            byte[] bytes = Files.readAllBytes(tis.getPath());
            embedded.put(count++, bytes);
            super.parseEmbedded(tis, handler, metadata, parseContext, outputHtml);
        }

        public Map<Integer, byte[]> getEmbedded() {
            return embedded;
        }
    }

    /**
     * Asserts that the actual content type matches the expected type,
     * allowing for the "ocr-" prefix that appears when tesseract is available.
     * e.g., "image/jpeg" matches both "image/jpeg" and "image/ocr-jpeg".
     */
    private void assertImageContentType(String expected, String actual) {
        String ocrVariant = expected.replace("image/", "image/ocr-");
        assertTrue(expected.equals(actual) || ocrVariant.equals(actual),
                "Expected " + expected + " or " + ocrVariant + " but got: " + actual);
    }
}
