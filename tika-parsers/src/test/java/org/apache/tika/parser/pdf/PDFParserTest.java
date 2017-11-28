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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.AccessPermissionException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPMM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing pdf files.
 */
public class PDFParserTest extends TikaTest {

    public static final MediaType TYPE_TEXT = MediaType.TEXT_PLAIN;
    public static final MediaType TYPE_EMF = MediaType.image("emf");
    public static final MediaType TYPE_PDF = MediaType.application("pdf");
    public static final MediaType TYPE_DOCX = MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document");
    public static final MediaType TYPE_DOC = MediaType.application("msword");
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

    private static int substringCount(String needle, String haystack) {
        int upto = -1;
        int count = 0;
        while (true) {
            final int next = haystack.indexOf(needle, upto);
            if (next == -1) {
                break;
            }
            count++;
            upto = next + 1;
        }

        return count;
    }

    @Test
    public void testPdfParsing() throws Exception {

        XMLResult r = getXML("testPDF.pdf");
        Metadata metadata = r.metadata;
        String xml = r.xml;
        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Bertrand Delacr\u00e9taz", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Bertrand Delacr\u00e9taz", metadata.get(Metadata.AUTHOR));
        assertEquals("Firefox", metadata.get(TikaCoreProperties.CREATOR_TOOL));
        assertEquals("Apache Tika - Apache Tika", metadata.get(TikaCoreProperties.TITLE));

        // Can't reliably test dates yet - see TIKA-451
//        assertEquals("Sat Sep 15 10:02:31 BST 2007", metadata.get(Metadata.CREATION_DATE));
//        assertEquals("Sat Sep 15 10:02:31 BST 2007", metadata.get(Metadata.LAST_MODIFIED));

        assertContains("Apache Tika", xml);
        assertContains("Tika - Content Analysis Toolkit", xml);
        assertContains("incubator", xml);
        assertContains("Apache Software Foundation", xml);
        // testing how the end of one paragraph is separated from start of the next one
        assertTrue("should have word boundary after headline",
                !xml.contains("ToolkitApache"));
        assertTrue("should have word boundary between paragraphs",
                !xml.contains("libraries.Apache"));
    }

    @Test
    public void testPdfParsingMetadataOnly() throws Exception {

        Metadata metadata = getXML("testPDF.pdf").metadata;
        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Bertrand Delacr\u00e9taz", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Firefox", metadata.get(TikaCoreProperties.CREATOR_TOOL));
        assertEquals("Apache Tika - Apache Tika", metadata.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testCustomMetadata() throws Exception {

        XMLResult r = getXML("testPDF-custommetadata.pdf");
        Metadata metadata = r.metadata;
        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Document author", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Document author", metadata.get(Metadata.AUTHOR));
        assertEquals("Document title", metadata.get(TikaCoreProperties.TITLE));

        assertEquals("Custom Value", metadata.get("Custom Property"));

        assertEquals("Array Entry 1", metadata.get("Custom Array"));
        assertEquals(2, metadata.getValues("Custom Array").length);
        assertEquals("Array Entry 1", metadata.getValues("Custom Array")[0]);
        assertEquals("Array Entry 2", metadata.getValues("Custom Array")[1]);

        assertContains("Hello World!", r.xml);
    }

    /**
     * PDFs can be "protected" with the default password. This means
     * they're encrypted (potentially both text and metadata),
     * but we can decrypt them easily.
     */
    @Test
    public void testProtectedPDF() throws Exception {
        XMLResult r = getXML("testPDF_protected.pdf");
        Metadata metadata = r.metadata;
        assertEquals("true", metadata.get("pdf:encrypted"));
        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("The Bank of England", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("The Bank of England", metadata.get(Metadata.AUTHOR));
        assertEquals("Speeches by Andrew G Haldane", metadata.get(OfficeOpenXMLCore.SUBJECT));
        assertEquals("Speeches by Andrew G Haldane", metadata.get(Metadata.SUBJECT));
        assertEquals("Rethinking the Financial Network, Speech by Andrew G Haldane, Executive Director, Financial Stability delivered at the Financial Student Association, Amsterdam on 28 April 2009", metadata.get(TikaCoreProperties.TITLE));

        assertContains("RETHINKING THE FINANCIAL NETWORK", r.xml);
        assertContains("On 16 November 2002", r.xml);
        assertContains("In many important respects", r.xml);


        // Try again with an explicit empty password
        ParseContext context = new ParseContext();
        context.set(PasswordProvider.class, new PasswordProvider() {
            public String getPassword(Metadata metadata) {
                return "";
            }
        });
        r = getXML("testPDF_protected.pdf", context);
        metadata = r.metadata;
        assertEquals("true", metadata.get("pdf:encrypted"));

        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("The Bank of England", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Speeches by Andrew G Haldane", metadata.get(OfficeOpenXMLCore.SUBJECT));
        assertEquals("Speeches by Andrew G Haldane", metadata.get(Metadata.SUBJECT));
        assertEquals("Rethinking the Financial Network, Speech by Andrew G Haldane, Executive Director, Financial Stability delivered at the Financial Student Association, Amsterdam on 28 April 2009", metadata.get(TikaCoreProperties.TITLE));

        assertContains("RETHINKING THE FINANCIAL NETWORK", r.xml);
        assertContains("On 16 November 2002", r.xml);
        assertContains("In many important respects", r.xml);

        //now test wrong password
        context.set(PasswordProvider.class, new PasswordProvider() {
            public String getPassword(Metadata metadata) {
                return "WRONG!!!!";
            }
        });

        boolean ex = false;
        ContentHandler handler = new BodyContentHandler();
        metadata = new Metadata();
        try (InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDF_protected.pdf")) {
            Parser parser = new AutoDetectParser();
            parser.parse(stream, handler, metadata, context);
        } catch (EncryptedDocumentException e) {
            ex = true;
        }
        assertTrue("encryption exception", ex);
        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("true", metadata.get("pdf:encrypted"));
        //pdf:encrypted, X-Parsed-By and Content-Type
        assertEquals("very little metadata should be parsed", 3, metadata.names().length);
        assertEquals(0, handler.toString().length());
    }

    @Test
    public void testTwoTextBoxes() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        String content;
        try(InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDFTwoTextBoxes.pdf")) {
            content = getText(stream, parser);
        }
        content = content.replaceAll("\\s+", " ");
        assertContains("Left column line 1 Left column line 2 Right column line 1 Right column line 2", content);
    }

    @Test
    public void testVarious() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        Metadata metadata = new Metadata();
        String content;
        try(InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDFVarious.pdf")) {
            content = getText(stream, parser, metadata);
        }
        //content = content.replaceAll("\\s+"," ");
        assertContains("Footnote appears here", content);
        assertContains("This is a footnote.", content);
        assertContains("This is the header text.", content);
        assertContains("This is the footer text.", content);
        assertContains("Here is a text box", content);
        assertContains("Bold", content);
        assertContains("italic", content);
        assertContains("underline", content);
        assertContains("superscript", content);
        assertContains("subscript", content);
        assertContains("Here is a citation:", content);
        assertContains("Figure 1 This is a caption for Figure 1", content);
        assertContains("(Kramer)", content);
        assertContains("Row 1 Col 1 Row 1 Col 2 Row 1 Col 3 Row 2 Col 1 Row 2 Col 2 Row 2 Col 3", content.replaceAll("\\s+"," "));
        assertContains("Row 1 column 1 Row 2 column 1 Row 1 column 2 Row 2 column 2", content.replaceAll("\\s+"," "));
        assertContains("This is a hyperlink", content);
        assertContains("Here is a list:", content);
        for(int row=1;row<=3;row++) {
            //assertContains("·\tBullet " + row, content);
            //assertContains("\u00b7\tBullet " + row, content);
            assertContains("Bullet " + row, content);
        }
        assertContains("Here is a numbered list:", content);
        for(int row=1;row<=3;row++) {
            //assertContains(row + ")\tNumber bullet " + row, content);
            assertContains(row + ") Number bullet " + row, content);
        }

        for(int row=1;row<=2;row++) {
            for(int col=1;col<=3;col++) {
                assertContains("Row " + row + " Col " + col, content);
            }
        }

        assertContains("Keyword1 Keyword2", content);
        assertEquals("Keyword1 Keyword2",
                     metadata.get(Metadata.KEYWORDS));

        assertContains("Subject is here", content);
        assertEquals("Subject is here",
                     metadata.get(OfficeOpenXMLCore.SUBJECT));
        assertEquals("Subject is here",
                     metadata.get(Metadata.SUBJECT));

        assertContains("Suddenly some Japanese text:", content);
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", content);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f", content);

        assertContains("And then some Gothic text:", content);
        // TODO: I saved the word doc as a PDF, but that
        // process somehow, apparently lost the gothic
        // chars, so we cannot test this here:
        //assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A", content);
    }

    @Test
    public void testAnnotations() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        String content;
        try(InputStream stream = getResourceAsStream("/test-documents/testAnnotations.pdf")){
            content = getText(stream, parser);
        }
        content = content.replaceAll("[\\s\u00a0]+", " ");
        assertContains("Here is some text", content);
        assertContains("Here is a comment", content);

        // Test w/ annotation text disabled:
        PDFParser pdfParser = new PDFParser();
        pdfParser.getPDFParserConfig().setExtractAnnotationText(false);
        try(InputStream stream = getResourceAsStream("/test-documents/testAnnotations.pdf")) {
            content = getText(stream, pdfParser);
        }
        content = content.replaceAll("[\\s\u00a0]+", " ");
        assertContains("Here is some text", content);
        assertEquals(-1, content.indexOf("Here is a comment"));

        // annotation text disabled through parsecontext
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractAnnotationText(false);
        context.set(PDFParserConfig.class, config);
        try (InputStream stream = getResourceAsStream("/test-documents/testAnnotations.pdf")) {
            content = getText(stream, parser, context);
        }
        content = content.replaceAll("[\\s\u00a0]+", " ");
        assertContains("Here is some text", content);
        assertEquals(-1, content.indexOf("Here is a comment"));


        // TIKA-738: make sure no extra </p> tags
        String xml = getXML("testAnnotations.pdf").xml;
        assertEquals(substringCount("<p>", xml),
                substringCount("</p>", xml));
    }

    // TIKA-981
    @Test
    public void testPopupAnnotation() throws Exception {
        XMLResult r = getXML("testPopupAnnotation.pdf");
        assertContains("this is the note", r.xml);
        assertContains("igalsh", r.xml);
    }

    @Test
    public void testEmbeddedPDFs() throws Exception {
        String xml = getXML("testPDFPackage.pdf").xml;
        assertContains("PDF1", xml);
        assertContains("PDF2", xml);
    }

    @Test
    public void testPageNumber() throws Exception {
        final XMLResult result = getXML("testPageNumber.pdf");
        final String content = result.xml.replaceAll("\\s+", "");
        assertContains("<p>1</p>", content);
    }

    /**
     * Test to ensure that Links are extracted from the text
     * <p/>
     * Note - the PDF contains the text "This is a hyperlink" which
     * a hyperlink annotation, linking to the tika site, on it. This
     * test will need updating when we're able to apply the annotation
     * to the text itself, rather than following on afterwards as now
     */
    @Test
    public void testLinks() throws Exception {
        final XMLResult result = getXML("testPDFVarious.pdf");
        assertContains("<div class=\"annotation\"><a href=\"http://tika.apache.org/\">"+
                "http://tika.apache.org/</a></div>", result.xml);
    }

    @Test
    public void testDisableAutoSpace() throws Exception {
        PDFParser parser = new PDFParser();
        parser.getPDFParserConfig().setEnableAutoSpace(false);
        XMLResult r = getXML("testExtraSpaces.pdf", parser);

        String content = r.xml.replaceAll("[\\s\u00a0]+", " ");
        // Text is correct when autoSpace is off:
        assertContains("Here is some formatted text", content);

        parser.getPDFParserConfig().setEnableAutoSpace(true);
        r = getXML("testExtraSpaces.pdf", parser);
        content = r.xml.replaceAll("[\\s\u00a0]+", " ");
        // Text is correct when autoSpace is off:

        // Text has extra spaces when autoSpace is on
        assertEquals(-1, content.indexOf("Here is some formatted text"));

        //now try with autodetect
        Parser autoParser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        context.set(PDFParserConfig.class, config);
        //default is true
        r = getXML("testExtraSpaces.pdf", autoParser, context);
        content = r.xml.replaceAll("[\\s\u00a0]+", " ");
        // Text has extra spaces when autoSpace is on
        assertEquals(-1, content.indexOf("Here is some formatted text"));

        config.setEnableAutoSpace(false);
        r = getXML("testExtraSpaces.pdf", parser, context);
        content = r.xml.replaceAll("[\\s\u00a0]+", " ");

        // Text is correct when autoSpace is off:
        assertContains("Here is some formatted text", content);

    }

    @Test
    public void testDuplicateOverlappingText() throws Exception {
        PDFParser parser = new PDFParser();
        // Default is false (keep overlapping text):
        XMLResult r = getXML("testOverlappingText.pdf", parser);
        assertContains("Text the first timeText the second time", r.xml);

        parser.getPDFParserConfig().setSuppressDuplicateOverlappingText(true);
        r = getXML("testOverlappingText.pdf", parser);
        // "Text the first" was dedup'd:
        assertContains("Text the first timesecond time", r.xml);

        //now try with autodetect
        Parser autoParser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        context.set(PDFParserConfig.class, config);
        r = getXML("testOverlappingText.pdf", autoParser, context);
        // Default is false (keep overlapping text):
        assertContains("Text the first timeText the second time", r.xml);

        config.setSuppressDuplicateOverlappingText(true);
        r = getXML("testOverlappingText.pdf", autoParser, context);
        // "Text the first" was dedup'd:
        assertContains("Text the first timesecond time", r.xml);

    }

    @Test
    public void testSortByPosition() throws Exception {
        PDFParser parser = new PDFParser();
        parser.getPDFParserConfig().setEnableAutoSpace(false);
        InputStream stream = getResourceAsStream("/test-documents/testPDFTwoTextBoxes.pdf");
        // Default is false (do not sort):
        String content = getText(stream, parser);
        content = content.replaceAll("\\s+", " ");
        assertContains("Left column line 1 Left column line 2 Right column line 1 Right column line 2", content);

        parser.setSortByPosition(true);
        stream = getResourceAsStream("/test-documents/testPDFTwoTextBoxes.pdf");
        content = getText(stream, parser);
        content = content.replaceAll("\\s+", " ");
        // Column text is now interleaved:
        assertContains("Left column line 1 Right column line 1 Left colu mn line 2 Right column line 2", content);

        //now try setting autodetect via parsecontext        
        AutoDetectParser autoParser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        context.set(PDFParserConfig.class, config);
        stream = getResourceAsStream("/test-documents/testPDFTwoTextBoxes.pdf");
        // Default is false (do not sort):
        content = getText(stream, autoParser, context);
        content = content.replaceAll("\\s+", " ");
        assertContains("Left column line 1 Left column line 2 Right column line 1 Right column line 2", content);

        config.setSortByPosition(true);
        context.set(PDFParserConfig.class, config);
        stream = getResourceAsStream("/test-documents/testPDFTwoTextBoxes.pdf");
        content = getText(stream, parser);
        content = content.replaceAll("\\s+", " ");
        // Column text is now interleaved:
        assertContains("Left column line 1 Right column line 1 Left colu mn line 2 Right column line 2", content);

    }

    // TIKA-1035
    @Test
    public void testBookmarks() throws Exception {
        String xml = getXML("testPDF_bookmarks.pdf").xml;
        int i = xml.indexOf("Denmark bookmark is here");
        int j = xml.indexOf("</body>");
        assertTrue(i != -1);
        assertTrue(j != -1);
        assertTrue(i < j);
    }

    // TIKA-2303
    @Test
    public void testTurningOffBookmarks() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractBookmarksText(false);
        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, config);
        String xml = getXML("testPDF_bookmarks.pdf", parseContext).xml;
        assertNotContained("Denmark bookmark is here", xml);
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

    // TIKA-973
    //commented out until test documents that are unambiguously
    //consistent with Apache License v2.0 are contributed.
    //TODO: add back test for AcroForm extraction; test document should include
    //recursive forms
/*    public void testAcroForm() throws Exception{
       Parser p = new AutoDetectParser();
       ParseContext context = new ParseContext();
       InputStream stream = getResourceAsStream("/test-documents/testPDF_acroForm1.pdf");
       String txt = getText(stream, p, context);
       stream.close();

       //simple first level form contents
       assertContains("to: John Doe", txt);
       //checkbox
       assertContains("xpackaging: Yes", txt);
       
       //this guarantees that the form processor
       //worked recursively at least once...i.e. it didn't just
       //take the first form
       stream = getResourceAsStream("/test-documents/testPDF_acroForm2.pdf");
       txt = getText(stream, p, context);
       stream.close();
       assertContains("123 Main St.", txt);
       
       
       //now test with nonsequential parser
       PDFParserConfig config = new PDFParserConfig();
       config.setUseNonSequentialParser(true);
       context.set(PDFParserConfig.class, config);
       stream = getResourceAsStream("/test-documents/testPDF_acroForm1.pdf");
       txt = getText(stream, p, context);
       stream.close();
       
       //simple first level form contents
       assertContains("to: John Doe", txt);
       //checkbox
       assertContains("xpackaging: Yes", txt);
       
       //this guarantees that the form processor
       //worked recursively at least once...i.e. it didn't just
       //take the first form
       stream = getResourceAsStream("/test-documents/testPDF_acroForm2.pdf");
       txt = getText(stream, p, context);
       assertContains("123 Main St.", txt);
       stream.close();     
    }
*/

    //TIKA-1226
    @Test
    public void testSignatureInAcroForm() throws Exception {
        //The current test doc does not contain any content in the signature area.
        //This just tests that a RuntimeException is not thrown.
        //TODO: find a better test file for this issue.
        String xml = getXML("/testPDF_acroform3.pdf").xml;
        assertTrue("found", (xml.contains("<li>aTextField: TIKA-1226</li>")));
    }

    @Test // TIKA-1228, TIKA-1268
    public void testEmbeddedFilesInChildren() throws Exception {
        String xml = getXML("/testPDF_childAttachments.pdf").xml;
        //"regressiveness" exists only in Unit10.doc not in the container pdf document
        assertTrue(xml.contains("regressiveness"));

        RecursiveParserWrapper p = new RecursiveParserWrapper(new AutoDetectParser(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);
        context.set(org.apache.tika.parser.pdf.PDFParserConfig.class, config);
        context.set(org.apache.tika.parser.Parser.class, p);

        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/testPDF_childAttachments.pdf"))) {
            p.parse(tis, new BodyContentHandler(-1), new Metadata(), context);
        }

        List<Metadata> metadatas = p.getMetadata();

        assertEquals(5, metadatas.size());
        assertNull(metadatas.get(0).get(Metadata.RESOURCE_NAME_KEY));
        assertEquals("image0.jpg", metadatas.get(1).get(Metadata.RESOURCE_NAME_KEY));
        assertEquals("Press Quality(1).joboptions", metadatas.get(3).get(Metadata.RESOURCE_NAME_KEY));
        assertEquals("Unit10.doc", metadatas.get(4).get(Metadata.RESOURCE_NAME_KEY));
        assertEquals(MediaType.image("jpeg").toString(), metadatas.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals(MediaType.image("tiff").toString(), metadatas.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("text/plain; charset=ISO-8859-1", metadatas.get(3).get(Metadata.CONTENT_TYPE));
        assertEquals(TYPE_DOC.toString(), metadatas.get(4).get(Metadata.CONTENT_TYPE));
    }

    @Test // TIKA-2232
    public void testEmbeddedJBIG2Image() throws Exception {

        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);
        context.set(PDFParserConfig.class, config);


        List<Metadata> metadatas = getRecursiveMetadata("testPDF_JBIG2.pdf", context);
        assertEquals(2, metadatas.size());
        assertContains("test images compressed using JBIG2", metadatas.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));

        for (String key : metadatas.get(1).names()) {
            if (key.startsWith("X-TIKA:EXCEPTION")) {
                fail("Exception: " + metadatas.get(1).get(key));
            }
        }
        assertEquals("Invalid height.", "91", metadatas.get(1).get("height"));
        assertEquals("Invalid width.", "352", metadatas.get(1).get("width"));
        
        assertNull(metadatas.get(0).get(Metadata.RESOURCE_NAME_KEY));
        assertEquals("image0.jb2", 
                metadatas.get(1).get(Metadata.RESOURCE_NAME_KEY));
        assertEquals(MediaType.image("x-jbig2").toString(), 
                metadatas.get(1).get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testEmbeddedFilesInAnnotations() throws Exception {
        String xml = getXML("/testPDFFileEmbInAnnotation.pdf").xml;

        assertTrue(xml.contains("This is a Excel"));
    }

    @Test
    public void testSingleCloseDoc() throws Exception {
        //TIKA-1341
        Parser p = new AutoDetectParser();
        Metadata m = new Metadata();
        ParseContext c = new ParseContext();
        ContentHandler h = new EventCountingHandler();
        try(InputStream is = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDFTripleLangTitle.pdf")) {
            p.parse(is, h, m, c);
        }
        assertEquals(1, ((EventCountingHandler) h).getEndDocument());
    }

    @Test
    public void testVersions() throws Exception {

        Map<String, String> dcFormat = new HashMap<String, String>();
        dcFormat.put("4.x", "application/pdf; version=1.3");
        dcFormat.put("5.x", "application/pdf; version=1.4");
        dcFormat.put("6.x", "application/pdf; version=1.5");
        dcFormat.put("7.x", "application/pdf; version=1.6");
        dcFormat.put("8.x", "application/pdf; version=1.7");
        dcFormat.put("9.x", "application/pdf; version=1.7");
        dcFormat.put("10.x", "application/pdf; version=1.7");
        dcFormat.put("11.x.PDFA-1b", "application/pdf; version=1.7");

        Map<String, String> pdfVersions = new HashMap<String, String>();
        pdfVersions.put("4.x", "1.3");
        pdfVersions.put("5.x", "1.4");
        pdfVersions.put("6.x", "1.5");
        pdfVersions.put("7.x", "1.6");
        pdfVersions.put("8.x", "1.7");
        pdfVersions.put("9.x", "1.7");
        pdfVersions.put("10.x", "1.7");
        pdfVersions.put("11.x.PDFA-1b", "1.7");

        Map<String, String> pdfExtensionVersions = new HashMap<String, String>();
        pdfExtensionVersions.put("9.x", "1.7 Adobe Extension Level 3");
        pdfExtensionVersions.put("10.x", "1.7 Adobe Extension Level 8");
        pdfExtensionVersions.put("11.x.PDFA-1b", "1.7 Adobe Extension Level 8");

        for (Map.Entry<String, String> e : dcFormat.entrySet()) {
            String fName = "testPDF_Version." + e.getKey() + ".pdf";

            XMLResult r = getXML(fName);
            boolean foundDC = false;
            String[] vals = r.metadata.getValues("dc:format");
            for (String v : vals) {
                if (v.equals(e.getValue())) {
                    foundDC = true;
                }
            }
            assertTrue("dc:format ::" + e.getValue(), foundDC);
            String extensionVersionTruth = pdfExtensionVersions.get(e.getKey());
            if (extensionVersionTruth != null) {
                assertEquals("pdf:PDFExtensionVersion :: " + extensionVersionTruth,
                        extensionVersionTruth,
                        r.metadata.get("pdf:PDFExtensionVersion"));
            }
            assertEquals("pdf:PDFVersion", pdfVersions.get(e.getKey()),
                    r.metadata.get("pdf:PDFVersion"));
        }
        //now test full 11.x
        XMLResult r = getXML("testPDF_Version.11.x.PDFA-1b.pdf");
        Set<String> versions = new HashSet<String>();
        for (String fmt : r.metadata.getValues("dc:format")) {
            versions.add(fmt);
        }

        for (String hit : new String[]{"application/pdf; version=1.7",
                "application/pdf; version=\"A-1b\"",
                "application/pdf; version=\"1.7 Adobe Extension Level 8\""
        }) {
            assertTrue(hit, versions.contains(hit));
        }

        assertEquals("pdfaid:conformance", r.metadata.get("pdfaid:conformance"), "B");
        assertEquals("pdfaid:part", r.metadata.get("pdfaid:part"), "1");
    }

    @Test
    public void testMultipleAuthors() throws Exception {

        XMLResult r = getXML("testPDF_twoAuthors.pdf");
        String[] keys = new String[]{
                "dc:creator",
                "meta:author",
                "creator",
                "Author"
        };

        for (String k : keys) {
            String[] vals = r.metadata.getValues(k);
            assertEquals("number of authors == 2 for key: " + k, 2, vals.length);
            Set<String> set = new HashSet<String>();
            set.add(vals[0]);
            set.add(vals[1]);
            assertTrue("Sample Author 1", set.contains("Sample Author 1"));
            assertTrue("Sample Author 2", set.contains("Sample Author 2"));
        }
    }

    //STUB test for once TIKA-1295 is fixed
    @Test
    public void testMultipleTitles() throws Exception {
        XMLResult r = getXML("testPDFTripleLangTitle.pdf");
        //TODO: add other tests as part of TIKA-1295
        //dc:title-fr-ca (or whatever we decide) should be "Bonjour World"
        //dc:title-zh-ch is currently hosed...bug in PDFBox while injecting xmp?
        //
        assertEquals("Hello World", r.metadata.get("dc:title"));
    }

    @Test
    public void testInlineSelector() throws Exception {

        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);
        ParseContext context = new ParseContext();
        context.set(org.apache.tika.parser.pdf.PDFParserConfig.class, config);
        context.set(org.apache.tika.parser.Parser.class, new AutoDetectParser());

        List<Metadata> metadatas = getRecursiveMetadata("testPDF_childAttachments.pdf", context);
        int inline = 0;
        int attach = 0;
        for (Metadata m : metadatas) {
            String v = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null) {
                if (v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())) {
                    inline++;
                } else if (v.equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())) {
                    attach++;
                }
            }
        }
        assertEquals(2, inline);
        assertEquals(2, attach);

        //now try turning off inline

        context.set(org.apache.tika.extractor.DocumentSelector.class, new AvoidInlineSelector());
        inline = 0;
        attach = 0;

        metadatas = getRecursiveMetadata("testPDF_childAttachments.pdf", context);
        for (Metadata m : metadatas) {
            String v = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null) {
                if (v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())) {
                    inline++;
                } else if (v.equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())) {
                    attach++;
                }
            }
        }
        assertEquals(0, inline);
        assertEquals(2, attach);

    }


    @Test
    public void testInlineConfig() throws Exception {

        List<Metadata> metadatas = getRecursiveMetadata("testPDF_childAttachments.pdf");
        int inline = 0;
        int attach = 0;
        for (Metadata m : metadatas) {
            String v = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null) {
                if (v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())) {
                    inline++;
                } else if (v.equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())) {
                    attach++;
                }
            }
        }
        assertEquals(0, inline);
        assertEquals(2, attach);

        //now try turning off inline
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);

        ParseContext context = new ParseContext();
        context.set(org.apache.tika.parser.pdf.PDFParserConfig.class, config);
        context.set(org.apache.tika.parser.Parser.class, new AutoDetectParser());
        inline = 0;
        attach = 0;

        metadatas = getRecursiveMetadata("testPDF_childAttachments.pdf", context);
        for (Metadata m : metadatas) {
            String v = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null) {
                if (v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())) {
                    inline++;
                } else if (v.equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())) {
                    attach++;
                }
            }
        }
        assertEquals(2, inline);
        assertEquals(2, attach);
    }

    @Test //TIKA-1376
    public void testEmbeddedFileNameExtraction() throws Exception {
        List<Metadata> metadatas = getRecursiveMetadata("testPDF_multiFormatEmbFiles.pdf");
        assertEquals("metadata size", 5, metadatas.size());
        Metadata firstAttachment = metadatas.get(1);
        assertEquals("attachment file name", "Test.txt", firstAttachment.get(Metadata.RESOURCE_NAME_KEY));
    }

    @Test //TIKA-1374
    public void testOSSpecificEmbeddedFileExtraction() throws Exception {
        List<Metadata> metadatas = getRecursiveMetadata("testPDF_multiFormatEmbFiles.pdf");
        assertEquals("metadata size", 5, metadatas.size());

        assertEquals("file name", "Test.txt", metadatas.get(1).get(Metadata.RESOURCE_NAME_KEY));
        assertContains("os specific", metadatas.get(1).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("file name", "TestMac.txt", metadatas.get(2).get(Metadata.RESOURCE_NAME_KEY));
        assertContains("mac embedded", metadatas.get(2).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("file name", "TestDos.txt", metadatas.get(3).get(Metadata.RESOURCE_NAME_KEY));
        assertContains("dos embedded", metadatas.get(3).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("file name", "TestUnix.txt", metadatas.get(4).get(Metadata.RESOURCE_NAME_KEY));
        assertContains("unix embedded", metadatas.get(4).get(RecursiveParserWrapper.TIKA_CONTENT));

    }

    @Test //TIKA-1427
    public void testEmbeddedFileMarkup() throws Exception {
        Parser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        context.set(org.apache.tika.parser.Parser.class, parser);

        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);
        context.set(org.apache.tika.parser.pdf.PDFParserConfig.class, config);

        XMLResult r = getXML("testPDF_childAttachments.pdf", context);
        //regular attachment
        assertContains("<div source=\"attachment\" class=\"embedded\" id=\"Unit10.doc\" />", r.xml);
        //inline image
        assertContains("<img src=\"embedded:image1.tif\" alt=\"image1.tif\" />", r.xml);

        //doc embedded inside an annotation
        r = getXML("testPDFFileEmbInAnnotation.pdf");
        assertContains("<div source=\"annotation\" class=\"embedded\" id=\"Excel.xlsx\" />", r.xml);
    }

    //Access checker tests

    @Test
    public void testLegacyAccessChecking() throws Exception {
        //test that default behavior doesn't throw AccessPermissionException
        for (String file : new String[]{
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {
            String xml = getXML(file).xml;
            assertContains("Hello World", xml);
        }

        //now try with the user password
        PasswordProvider provider = new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "user";
            }
        };

        ParseContext context = new ParseContext();
        context.set(PasswordProvider.class, provider);
        Parser parser = new AutoDetectParser();

        for (String path : new String[]{
                "testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",
        }) {
            assertContains("Hello World", getXML(path, context).xml);
        }
    }

    @Test
    public void testAccessCheckingEmptyPassword() throws Exception {
        PDFParserConfig config = new PDFParserConfig();

        //don't allow extraction, not even for accessibility
        config.setAccessChecker(new AccessChecker(false));
        Parser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);

        //test exception for empty password
        for (String path : new String[]{
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {
            assertException("/test-documents/" + path, parser, context, AccessPermissionException.class);
        }

        config.setAccessChecker(new AccessChecker(true));
        assertException("/test-documents/" + "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                parser, context, AccessPermissionException.class);

        assertContains("Hello World",
                getXML("testPDF_no_extract_yes_accessibility_owner_empty.pdf",
                        context).xml);
    }

    @Test
    public void testAccessCheckingUserPassword() throws Exception {
        ParseContext context = new ParseContext();

        PDFParserConfig config = new PDFParserConfig();
        //don't allow extraction, not even for accessibility
        config.setAccessChecker(new AccessChecker(false));
        PasswordProvider passwordProvider = new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "user";
            }
        };

        context.set(PasswordProvider.class, passwordProvider);
        context.set(PDFParserConfig.class, config);

        Parser parser = new AutoDetectParser();

        //test bad passwords
        for (String path : new String[]{
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {
            assertException("/test-documents/" + path, parser, context, EncryptedDocumentException.class);
        }

        //bad password is still a bad password
        config.setAccessChecker(new AccessChecker(true));
        for (String path : new String[]{
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {
            assertException("/test-documents/" + path, parser, context, EncryptedDocumentException.class);
        }

        //now test documents that require this "user" password
        assertException("/test-documents/" + "testPDF_no_extract_no_accessibility_owner_user.pdf",
                parser, context, AccessPermissionException.class);

        assertContains("Hello World",
                    getXML("testPDF_no_extract_yes_accessibility_owner_user.pdf", context).xml);

        config.setAccessChecker(new AccessChecker(false));
        for (String path : new String[]{
                "testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",
        }) {
            assertException("/test-documents/" + path, parser, context, AccessPermissionException.class);
        }
    }

    @Test
    public void testAccessCheckingOwnerPassword() throws Exception {
        ParseContext context = new ParseContext();

        PDFParserConfig config = new PDFParserConfig();
        //don't allow extraction, not even for accessibility
        config.setAccessChecker(new AccessChecker(true));
        PasswordProvider passwordProvider = new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "owner";
            }
        };

        context.set(PasswordProvider.class, passwordProvider);
        context.set(PDFParserConfig.class, config);

        //with owner's password, text can be extracted, no matter the AccessibilityChecker's settings
        for (String path : new String[]{
                "testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {

            assertContains("Hello World", getXML(path, context).xml);
        }

        //really, with owner's password, all extraction is allowed
        config.setAccessChecker(new AccessChecker(false));
        for (String path : new String[]{
                "testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {
            assertContains("Hello World", getXML(path, context).xml);
        }
    }

    @Test
    public void testPDFEncodedStringsInXMP() throws Exception {
        //TIKA-1678
        XMLResult r = getXML("testPDF_PDFEncodedStringInXMP.pdf");
        assertEquals("Microsoft", r.metadata.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testXFAExtractionBasic() throws Exception {
        XMLResult r = getXML("testPDF_XFA_govdocs1_258578.pdf");
        //contains content existing only in the "regular" pdf
        assertContains("Mount Rushmore National Memorial", r.xml);
        //contains xfa fields and data
        assertContains("<li fieldName=\"School_Name\">School Name: my_school</li>",
            r.xml);
    }

    @Test
    public void testXFAOnly() throws Exception {
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setIfXFAExtractOnlyXFA(true);
        context.set(PDFParserConfig.class, config);
        String xml = getXML("testPDF_XFA_govdocs1_258578.pdf", context).xml;
        assertContains("<li fieldName=\"Room_1\">Room [1]: my_room1</li>", xml);
        assertContains("</xfa_content></body></html>", xml);

        assertNotContained("Mount Rushmore National Memorial", xml);
    }

    @Test
    public void testXMPMM() throws Exception {

        Metadata m = getXML("testPDF_twoAuthors.pdf").metadata;
        assertEquals("uuid:0e46913c-72b9-40c0-8232-69e362abcd1e",
                m.get(XMPMM.DOCUMENTID));

        m = getXML("testPDF_Version.11.x.PDFA-1b.pdf").metadata;
        assertEquals("uuid:cccee1fc-51b3-4b52-ac86-672af3974d25",
                m.get(XMPMM.DOCUMENTID));

        //now test for 7 elements in each parallel array
        //from the history section
        assertArrayEquals(new String[]{
                "uuid:0313504b-a0b0-4dac-a9f0-357221f2eadf",
                "uuid:edc4279e-0d5f-465e-b13e-1298402fd11c",
                "uuid:f565b775-43f3-4a9a-8541-e98c4115db6d",
                "uuid:9fd5e0a8-14a5-4920-ad7f-870c0b8ee65f",
                "uuid:09b6cfba-efde-4e07-a77f-70de858cc0aa",
                "uuid:1e4ffbd7-dabc-4aae-801c-15b3404ade36",
                "uuid:c1669773-a6ca-4bdd-aade-519030d0af00"
        }, m.getValues(XMPMM.HISTORY_EVENT_INSTANCEID));

        assertArrayEquals(new String[]{
                "converted",
                "converted",
                "converted",
                "converted",
                "converted",
                "converted",
                "converted"
        }, m.getValues(XMPMM.HISTORY_ACTION));

        assertArrayEquals(new String[]{
                "Preflight",
                "Preflight",
                "Preflight",
                "Preflight",
                "Preflight",
                "Preflight",
                "Preflight"
        }, m.getValues(XMPMM.HISTORY_SOFTWARE_AGENT));

        assertArrayEquals(new String[]{
                "2014-03-04T23:50:41Z",
                "2014-03-04T23:50:42Z",
                "2014-03-04T23:51:34Z",
                "2014-03-04T23:51:36Z",
                "2014-03-04T23:51:37Z",
                "2014-03-04T23:52:22Z",
                "2014-03-04T23:54:48Z"
        }, m.getValues(XMPMM.HISTORY_WHEN));
    }

    @Test
    public void testSkipBadPage() throws Exception {
        //test file comes from govdocs1
        //can't use TikaTest shortcuts because of exception
        Parser p = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler(-1);
        Metadata m = new Metadata();
        ParseContext context = new ParseContext();
        boolean tikaEx = false;
        try (InputStream is = getResourceAsStream("/test-documents/testPDF_bad_page_303226.pdf")) {
            p.parse(is, handler, m, context);
        } catch (TikaException e) {
            tikaEx = true;
        }
        String content = handler.toString();
        assertTrue("Should have thrown exception", tikaEx);
        assertEquals(1, m.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING).length);
        assertContains("Unknown dir", m.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertContains("1309.61", content);

        //now try throwing exception immediately
        PDFParserConfig config = new PDFParserConfig();
        config.setCatchIntermediateIOExceptions(false);
        context.set(PDFParserConfig.class, config);

        handler = new BodyContentHandler(-1);
        m = new Metadata();
        tikaEx = false;
        try (InputStream is = getResourceAsStream("/test-documents/testPDF_bad_page_303226.pdf")) {
            p.parse(is, handler, m, context);
        } catch (TikaException e) {
            tikaEx = true;
        }
        content = handler.toString();
        assertTrue("Should have thrown exception", tikaEx);
        assertEquals(0, m.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING).length);
        assertNotContained("1309.61", content);
    }
    @Test
    public void testEmbeddedJPEG() throws Exception {
        //TIKA-1990, test that an embedded jpeg is correctly decoded
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);

        List<Metadata> metadataList = getRecursiveMetadata("testPDF_childAttachments.pdf", context);
        //sanity check
        assertEquals(5, metadataList.size());
        //inlined jpeg metadata
        Metadata jpegMetadata = metadataList.get(1);
        assertEquals("image/jpeg", jpegMetadata.get(Metadata.CONTENT_TYPE));
        //the metadata parse will fail if the stream is not correctly decoded
        assertEquals("1425", jpegMetadata.get(Metadata.IMAGE_LENGTH));
    }

    @Test
    public void testEmbeddedDocsWithOCROnly() throws Exception {
        if (! canRunOCR()) { return; }

        for (PDFParserConfig.OCR_STRATEGY strategy : PDFParserConfig.OCR_STRATEGY.values()) {
            PDFParserConfig config = new PDFParserConfig();
            config.setOcrStrategy(strategy);
            ParseContext context = new ParseContext();
            context.set(PDFParserConfig.class, config);
            context.set(Parser.class, new AutoDetectParser());
            //make sure everything works with regular xml _and_ with recursive
            XMLResult xmlResult = getXML("testPDFEmbeddingAndEmbedded.docx", context);
            assertContains("pdf_haystack", xmlResult.xml);
            assertContains("Haystack", xmlResult.xml);
            assertContains("Needle", xmlResult.xml);
            if (! strategy.equals(PDFParserConfig.OCR_STRATEGY.NO_OCR)) {
                // Tesseract may see the t in haystack as a ! some times...
                String div = "<div class=\"ocr\">pdf_hays";
                if (xmlResult.xml.contains(div+"!ack")) {
                   assertContains(div+"!ack", xmlResult.xml);
                } else {
                   assertContains(div+"tack", xmlResult.xml);
                }
            } else {
                assertNotContained("<div class=\"ocr\">pdf_haystack", xmlResult.xml);
            }
            assertEquals(4, getRecursiveMetadata("testPDFEmbeddingAndEmbedded.docx", context).size());
        }

    }

    @Test
    public void testJBIG2OCROnly() throws Exception {
        if (!canRunOCR()) {
            return;
        }
        PDFParserConfig config = new PDFParserConfig();
        config.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.OCR_ONLY);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser());
        //make sure everything works with regular xml _and_ with recursive
        XMLResult xmlResult = getXML("testPDF_JBIG2.pdf", context);
        assertContains("Norconex", xmlResult.xml);
    }


    @Test
    public void testInitializationViaConfig() throws Exception {
        InputStream is = getClass().getResourceAsStream("/org/apache/tika/parser/pdf/tika-config.xml");
        assertNotNull(is);
        TikaConfig tikaConfig = new TikaConfig(is);
        Parser p = new AutoDetectParser(tikaConfig);
        String text = getText(getResourceAsStream("/test-documents/testPDFTwoTextBoxes.pdf"), p);
        text = text.replaceAll("\\s+", " ");

        // Column text is now interleaved:
        assertContains("Left column line 1 Right column line 1 Left colu mn line 2 Right column line 2", text);

    }

    @Test
    public void testInitializationOfNonPrimitivesViaConfig() throws Exception {
        InputStream is = getClass().getResourceAsStream("/org/apache/tika/parser/pdf/tika-config-non-primitives.xml");
        assertNotNull(is);
        TikaConfig tikaConfig = new TikaConfig(is);
        AutoDetectParser p = new AutoDetectParser(tikaConfig);
        Map<MediaType, Parser> parsers = p.getParsers();
        Parser composite = parsers.get(MediaType.application("pdf"));
        Parser pdfParser = ((CompositeParser)composite).getParsers().get(MediaType.application("pdf"));
        assertEquals("org.apache.tika.parser.pdf.PDFParser", pdfParser.getClass().getName());
        assertEquals(PDFParserConfig.OCR_STRATEGY.OCR_ONLY, ((PDFParser)pdfParser).getPDFParserConfig().getOcrStrategy());
        assertEquals(ImageType.RGB, ((PDFParser)pdfParser).getPDFParserConfig().getOcrImageType());

    }

    @Test
    public void testDiffTitles() throws Exception {
        //different titles in xmp vs docinfo
        Metadata m = getXML("testPDF_diffTitles.pdf").metadata;
        assertEquals("this is a new title", m.get(PDF.DOC_INFO_TITLE));
        assertEquals("Sample Title", m.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testMaxLength() throws Exception {
        InputStream is = getResourceAsStream("/test-documents/testPDF.pdf");
        String content = new Tika().parseToString(is, new Metadata(), 100);
        assertTrue(content.length() == 100);
        assertContains("Tika - Content", content);
    }

    @Test
    public void testConfiguringMoreParams() throws Exception {
        try (InputStream configIs = getClass().getResourceAsStream("/org/apache/tika/parser/pdf/tika-inline-config.xml")) {
            assertNotNull(configIs);
            TikaConfig tikaConfig = new TikaConfig(configIs);
            AutoDetectParser p = new AutoDetectParser(tikaConfig);
            //make absolutely certain the functionality works!
            List<Metadata> metadata = getRecursiveMetadata("testOCR.pdf", p);
            assertEquals(2, metadata.size());
            Map<MediaType, Parser> parsers = p.getParsers();
            Parser composite = parsers.get(MediaType.application("pdf"));
            Parser pdfParser = ((CompositeParser)composite).getParsers().get(MediaType.application("pdf"));
            assertTrue(pdfParser instanceof PDFParser);
            PDFParserConfig pdfParserConfig = ((PDFParser)pdfParser).getPDFParserConfig();
            assertEquals(new AccessChecker(true), pdfParserConfig.getAccessChecker());
            assertEquals(true, pdfParserConfig.getExtractInlineImages());
            assertEquals(false, pdfParserConfig.getExtractUniqueInlineImagesOnly());
            assertEquals(314, pdfParserConfig.getOcrDPI());
            assertEquals(2.1f, pdfParserConfig.getOcrImageQuality(), .01f);
            assertEquals(1.3f, pdfParserConfig.getOcrImageScale(), .01f);
            assertEquals("jpeg", pdfParserConfig.getOcrImageFormatName());
            assertEquals(524288000, pdfParserConfig.getMaxMainMemoryBytes());
            assertEquals(false, pdfParserConfig.getCatchIntermediateIOExceptions());

        }
    }

    //TODO: figure out how to test jp2 embedded with OCR

    private void assertException(String path, Parser parser, ParseContext context, Class expected) {
        boolean noEx = false;
        InputStream is = getResourceAsStream(path);
        try {
            String text = getText(is, parser, context);
            noEx = true;
        } catch (Exception e) {
            assertEquals("Not the right exception: " + path, expected, e.getClass());
        } finally {
            IOUtils.closeQuietly(is);
        }
        assertFalse(path + " should have thrown exception", noEx);
    }

    /**
     * Simple class to count end of document events.  If functionality is useful,
     * move to org.apache.tika in src/test
     */
    private class EventCountingHandler extends ContentHandlerDecorator {
        private int endDocument = 0;

        @Override
        public void endDocument() {
            endDocument++;
        }

        public int getEndDocument() {
            return endDocument;
        }
    }

    private class AvoidInlineSelector implements DocumentSelector {

        @Override
        public boolean select(Metadata metadata) {
            String v = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null && v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())) {
                return false;
            }
            return true;
        }
    }
}
