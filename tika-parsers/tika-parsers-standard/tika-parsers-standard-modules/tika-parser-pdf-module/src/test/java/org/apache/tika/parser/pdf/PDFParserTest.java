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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.AccessPermissionException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.metadata.Font;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.metadata.XMP;
import org.apache.tika.metadata.XMPMM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.utils.ExceptionUtils;

/**
 * Test case for parsing pdf files.
 */
public class PDFParserTest extends TikaTest {

    public static Level PDFBOX_LOG_LEVEL = Level.INFO;

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
        assertTrue(!xml.contains("ToolkitApache"), "should have word boundary after headline");
        assertTrue(!xml.contains("libraries.Apache"),
                "should have word boundary between paragraphs");
    }

    @Test
    public void testFontNameExtraction() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractFontNames(true);
        ParseContext pc = new ParseContext();
        pc.set(PDFParserConfig.class, config);
        XMLResult r = getXML("testPDFVarious.pdf", pc);
        assertContains("ABCDEE+Calibri", r.metadata.get(Font.FONT_NAME));
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
        assertEquals("Speeches by Andrew G Haldane",
                metadata.get(TikaCoreProperties.SUBJECT));
        assertEquals(
                "Rethinking the Financial Network, Speech by Andrew G Haldane, " +
                        "Executive Director, Financial Stability " +
                        "delivered at the Financial Student " +
                        "Association, Amsterdam on 28 April 2009",
                metadata.get(TikaCoreProperties.TITLE));

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
        assertEquals("Speeches by Andrew G Haldane", metadata.get(TikaCoreProperties.SUBJECT));
        assertEquals(
                "Rethinking the Financial Network, Speech by Andrew G Haldane, " +
                        "Executive Director, Financial Stability delivered at the " +
                        "Financial Student Association, Amsterdam on 28 April 2009",
                metadata.get(TikaCoreProperties.TITLE));

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
        try (InputStream stream = getResourceAsStream("/test-documents/testPDF_protected.pdf")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, context);
        } catch (EncryptedDocumentException e) {
            ex = true;
        }
        assertTrue(ex, "encryption exception");
        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("true", metadata.get("pdf:encrypted"));
        //pdf:encrypted, X-Parsed-By and Content-Type
        assertEquals(5, metadata.names().length, "very little metadata should be parsed");
        assertEquals(0, handler.toString().length());
    }

    @Test
    public void testTwoTextBoxes() throws Exception {
        String content;
        try (InputStream stream = getResourceAsStream(
                "/test-documents/testPDFTwoTextBoxes.pdf")) {
            content = getText(stream, AUTO_DETECT_PARSER);
        }
        content = content.replaceAll("\\s+", " ");
        assertContains(
                "Left column line 1 Left column line 2 Right column line 1 Right column line 2",
                content);
    }

    @Test
    public void testVarious() throws Exception {
        Metadata metadata = new Metadata();
        String content;
        try (InputStream stream = getResourceAsStream("/test-documents/testPDFVarious.pdf")) {
            content = getText(stream, AUTO_DETECT_PARSER, metadata);
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
        assertContains("Row 1 Col 1 Row 1 Col 2 Row 1 Col 3 Row " +
                        "2 Col 1 Row 2 Col 2 Row 2 Col 3",
                content.replaceAll("\\s+", " "));
        assertContains("Row 1 column 1 Row 2 column 1 Row 1 column 2 Row 2 column 2",
                content.replaceAll("\\s+", " "));
        assertContains("This is a hyperlink", content);
        assertContains("Here is a list:", content);
        for (int row = 1; row <= 3; row++) {
            //assertContains("·\tBullet " + row, content);
            //assertContains("\u00b7\tBullet " + row, content);
            assertContains("Bullet " + row, content);
        }
        assertContains("Here is a numbered list:", content);
        for (int row = 1; row <= 3; row++) {
            //assertContains(row + ")\tNumber bullet " + row, content);
            assertContains(row + ") Number bullet " + row, content);
        }

        for (int row = 1; row <= 2; row++) {
            for (int col = 1; col <= 3; col++) {
                assertContains("Row " + row + " Col " + col, content);
            }
        }

        assertContains("Keyword1 Keyword2", content);
        assertContains("Keyword1 Keyword2", metadata.get(TikaCoreProperties.SUBJECT));

        assertContains("Subject is here", content);
        assertContains("Subject is here",
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));

        assertContains("Suddenly some Japanese text:", content);
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", content);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e" +
                        "\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f",
                content);

        assertContains("And then some Gothic text:", content);
        // TODO: I saved the word doc as a PDF, but that
        // process somehow, apparently lost the gothic
        // chars, so we cannot test this here:
        //assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44
        // \uD800\uDF39\uD800\uDF43\uD800\uDF3A", content);
    }

    @Test
    public void testAnnotations() throws Exception {
        String content;
        try (InputStream stream = getResourceAsStream("/test-documents/testAnnotations.pdf")) {
            content = getText(stream, AUTO_DETECT_PARSER);
        }
        content = content.replaceAll("[\\s\u00a0]+", " ");
        assertContains("Here is some text", content);
        assertContains("Here is a comment", content);

        // Test w/ annotation text disabled:
        PDFParser pdfParser = new PDFParser();
        pdfParser.getPDFParserConfig().setExtractAnnotationText(false);
        try (InputStream stream = getResourceAsStream("/test-documents/testAnnotations.pdf")) {
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
            content = getText(stream, AUTO_DETECT_PARSER, context);
        }
        content = content.replaceAll("[\\s\u00a0]+", " ");
        assertContains("Here is some text", content);
        assertEquals(-1, content.indexOf("Here is a comment"));


        // TIKA-738: make sure no extra </p> tags
        String xml = getXML("testAnnotations.pdf").xml;
        assertEquals(substringCount("<p>", xml), substringCount("</p>", xml));
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
        List<Metadata> metadataList = getRecursiveMetadata("testPDFPackage.pdf");
        assertEquals(3, metadataList.size());
        assertEquals("true", metadataList.get(0).get(PDF.HAS_COLLECTION));
        assertContains("Adobe recommends using Adobe Reader ",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("<p>PDF1", metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("<p>PDF2", metadataList.get(2).get(TikaCoreProperties.TIKA_CONTENT));
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
        assertContains("<div class=\"annotation\"><a href=\"http://tika.apache.org/\">" +
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
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        context.set(PDFParserConfig.class, config);
        //default is true
        r = getXML("testExtraSpaces.pdf", context);
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
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        context.set(PDFParserConfig.class, config);
        r = getXML("testOverlappingText.pdf", context);
        // Default is false (keep overlapping text):
        assertContains("Text the first timeText the second time", r.xml);

        config.setSuppressDuplicateOverlappingText(true);
        r = getXML("testOverlappingText.pdf", context);
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
        assertContains(
                "Left column line 1 Left column line 2 Right column line 1 Right column line 2",
                content);

        parser.setSortByPosition(true);
        stream = getResourceAsStream("/test-documents/testPDFTwoTextBoxes.pdf");
        content = getText(stream, parser);
        content = content.replaceAll("\\s+", " ");
        // Column text is now interleaved:
        assertContains(
                "Left column line 1 Right column line 1 Left colu mn line 2 Right column line 2",
                content);

        //now try setting autodetect via parsecontext
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        context.set(PDFParserConfig.class, config);
        // Default is false (do not sort):
        content = getText("testPDFTwoTextBoxes.pdf", new Metadata(), context);
        content = content.replaceAll("\\s+", " ");
        assertContains(
                "Left column line 1 Left column line 2 Right column line 1 Right column line 2",
                content);

        config.setSortByPosition(true);
        context.set(PDFParserConfig.class, config);
        stream = getResourceAsStream("/test-documents/testPDFTwoTextBoxes.pdf");
        content = getText("testPDFTwoTextBoxes.pdf", new Metadata(), context);
        content = content.replaceAll("\\s+", " ");
        // Column text is now interleaved:
        assertContains(
                "Left column line 1 Right column line 1 Left colu mn line 2 Right column line 2",
                content);

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
        XMLResult result = getXML("testPDF_acroform3.pdf");
        Metadata m = result.metadata;
        assertEquals("true", m.get(PDF.HAS_XMP));
        assertEquals("true", m.get(PDF.HAS_ACROFORM_FIELDS));
        assertEquals("false", m.get(PDF.HAS_XFA));
        assertContains("<li>aTextField: TIKA-1226</li>", result.xml);
    }

    @Test
    public void testSingleCloseDoc() throws Exception {
        //TIKA-1341
        Metadata m = new Metadata();
        ParseContext c = new ParseContext();
        ContentHandler h = new EventCountingHandler();
        try (InputStream is = getResourceAsStream("/test-documents/testPDFTripleLangTitle.pdf")) {
            AUTO_DETECT_PARSER.parse(is, h, m, c);
        }
        assertEquals(1, ((EventCountingHandler) h).getEndDocument());
    }

    @Test
    public void testVersions() throws Exception {

        Map<String, String> dcFormat = new HashMap<>();
        dcFormat.put("4.x", "application/pdf; version=1.3");
        dcFormat.put("5.x", "application/pdf; version=1.4");
        dcFormat.put("6.x", "application/pdf; version=1.5");
        dcFormat.put("7.x", "application/pdf; version=1.6");
        dcFormat.put("8.x", "application/pdf; version=1.7");
        dcFormat.put("9.x", "application/pdf; version=1.7");
        dcFormat.put("10.x", "application/pdf; version=1.7");
        dcFormat.put("11.x.PDFA-1b", "application/pdf; version=1.7");

        Map<String, String> pdfVersions = new HashMap<>();
        pdfVersions.put("4.x", "1.3");
        pdfVersions.put("5.x", "1.4");
        pdfVersions.put("6.x", "1.5");
        pdfVersions.put("7.x", "1.6");
        pdfVersions.put("8.x", "1.7");
        pdfVersions.put("9.x", "1.7");
        pdfVersions.put("10.x", "1.7");
        pdfVersions.put("11.x.PDFA-1b", "1.7");

        Map<String, String> pdfExtensionVersions = new HashMap<>();
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
                    break;
                }
            }
            assertTrue(foundDC, "dc:format ::" + e.getValue());
            String extensionVersionTruth = pdfExtensionVersions.get(e.getKey());
            if (extensionVersionTruth != null) {
                assertEquals(extensionVersionTruth, r.metadata.get("pdf:PDFExtensionVersion"),
                        "pdf:PDFExtensionVersion :: " + extensionVersionTruth);
            }
            assertEquals(pdfVersions.get(e.getKey()),
                    r.metadata.get("pdf:PDFVersion"), "pdf:PDFVersion");
        }
        //now test full 11.x
        XMLResult r = getXML("testPDF_Version.11.x.PDFA-1b.pdf");
        Set<String> versions = new HashSet<>(Arrays.asList(r.metadata.getValues("dc:format")));

        for (String hit : new String[]{"application/pdf; version=1.7",
                "application/pdf; version=\"1.7 Adobe Extension Level 8\""}) {
            assertTrue(versions.contains(hit), hit);
        }

        assertEquals(r.metadata.get("pdfaid:conformance"), "B", "pdfaid:conformance");
        assertEquals(r.metadata.get("pdfaid:part"), "1", "pdfaid:part");
    }

    @Test
    public void testMultipleAuthors() throws Exception {

        XMLResult r = getXML("testPDF_twoAuthors.pdf");
        List<String> authors = Arrays.asList(r.metadata.getValues(TikaCoreProperties.CREATOR));
        assertContains("Sample Author 1", authors);
        assertContains("Sample Author 2", authors);

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

        List<Metadata> metadatas = getRecursiveMetadata("testPDF_childAttachments.pdf", context);
        int inline = 0;
        int attach = 0;
        for (Metadata m : metadatas) {
            String v = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null) {
                if (v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())) {
                    inline++;
                } else if (v
                        .equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())) {
                    attach++;
                }
            }
        }
        assertEquals(2, inline);
        assertEquals(2, attach);
        assertEquals(1, metadatas.get(1).getInt(TikaPagedText.PAGE_NUMBER));
        assertEquals(66, metadatas.get(2).getInt(TikaPagedText.PAGE_NUMBER));
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
                } else if (v
                        .equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())) {
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
                } else if (v
                        .equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())) {
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
        inline = 0;
        attach = 0;

        metadatas = getRecursiveMetadata("testPDF_childAttachments.pdf", context);
        for (Metadata m : metadatas) {
            String v = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null) {
                if (v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())) {
                    inline++;
                } else if (v
                        .equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())) {
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
        assertEquals(5, metadatas.size());
        Metadata firstAttachment = metadatas.get(1);
        assertEquals("Test.txt", firstAttachment.get(TikaCoreProperties.RESOURCE_NAME_KEY),
                "attachment file name");
    }

    @Test //TIKA-1427
    public void testEmbeddedFileMarkup() throws Exception {
        ParseContext context = new ParseContext();
        context.set(org.apache.tika.parser.Parser.class, AUTO_DETECT_PARSER);

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
        assertContains("<div source=\"annotationFileAttachment\" class=\"embedded\" id=\"Excel" +
                ".xlsx\" />", r.xml);
    }

    //Access checker tests

    @Test
    public void testLegacyAccessChecking() throws Exception {
        //test that default behavior doesn't throw AccessPermissionException
        for (String file : new String[]{"testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",}) {
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

        for (String path : new String[]{"testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",}) {
            assertContains("Hello World", getXML(path, context).xml);
        }
    }

    @Test
    public void testAccessCheckingEmptyPassword() throws Exception {
        PDFParserConfig config = new PDFParserConfig();

        //don't allow extraction, not even for accessibility
        config.setAccessChecker(new AccessChecker(false));
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);

        //test exception for empty password
        for (String path : new String[]{"testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",}) {
            assertException("/test-documents/" + path, AUTO_DETECT_PARSER, context,
                    AccessPermissionException.class);
        }

        config.setAccessChecker(new AccessChecker(true));
        assertException("/test-documents/" + "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                AUTO_DETECT_PARSER, context, AccessPermissionException.class);

        assertContains("Hello World",
                getXML("testPDF_no_extract_yes_accessibility_owner_empty.pdf", context).xml);
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

        //test bad passwords
        for (String path : new String[]{"testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",}) {
            assertException("/test-documents/" + path, AUTO_DETECT_PARSER, context,
                    EncryptedDocumentException.class);
        }

        //bad password is still a bad password
        config.setAccessChecker(new AccessChecker(true));
        for (String path : new String[]{"testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",}) {
            assertException("/test-documents/" + path, AUTO_DETECT_PARSER, context,
                    EncryptedDocumentException.class);
        }

        //now test documents that require this "user" password
        assertException("/test-documents/" + "testPDF_no_extract_no_accessibility_owner_user.pdf",
                AUTO_DETECT_PARSER, context, AccessPermissionException.class);

        assertContains("Hello World",
                getXML("testPDF_no_extract_yes_accessibility_owner_user.pdf", context).xml);

        config.setAccessChecker(new AccessChecker(false));
        for (String path : new String[]{"testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",}) {
            assertException("/test-documents/" + path, AUTO_DETECT_PARSER, context,
                    AccessPermissionException.class);
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

        //with owner's password, text can be extracted, no matter the
        // AccessibilityChecker's settings
        for (String path : new String[]{"testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",}) {

            assertContains("Hello World", getXML(path, context).xml);
        }

        //really, with owner's password, all extraction is allowed
        config.setAccessChecker(new AccessChecker(false));
        for (String path : new String[]{"testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",}) {
            assertContains("Hello World", getXML(path, context).xml);
        }
    }

    @Test
    public void testNoXMP() throws Exception {
        assertEquals("false", getXML("testPDF.pdf").metadata.get(PDF.HAS_XMP));
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
        Metadata m = r.metadata;
        assertEquals("true", m.get(PDF.HAS_XFA));
        assertEquals("true", m.get(PDF.HAS_ACROFORM_FIELDS));
        assertEquals("true", m.get(PDF.HAS_XMP));
        //contains content existing only in the "regular" pdf
        assertContains("Mount Rushmore National Memorial", r.xml);
        //contains xfa fields and data
        assertContains("<li fieldName=\"School_Name\">School Name: my_school</li>", r.xml);
        //This file does not have multiple values for a given key.
        //It is not actually a useful test for TIKA-4171. We should
        //find a small example test file and run something like this.
        Matcher matcher = Pattern.compile("<li fieldName=").matcher(r.xml);
        int listItems = 0;
        while (matcher.find()) {
            listItems++;
        }
        assertEquals(27, listItems);
    }

    @Test
    public void testXFAOnly() throws Exception {
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setIfXFAExtractOnlyXFA(true);
        context.set(PDFParserConfig.class, config);
        String xml = getXML("testPDF_XFA_govdocs1_258578.pdf", context).xml;
        assertContains("<body><div class=\"xfa_content\">", xml);
        assertContains("<li fieldName=\"Room_1\">Room [1]: my_room1</li>", xml);

        assertNotContained("Mount Rushmore National Memorial", xml);
    }

    @Test
    public void testXMPMM() throws Exception {

        Metadata m = getXML("testPDF_twoAuthors.pdf").metadata;
        assertEquals("uuid:0e46913c-72b9-40c0-8232-69e362abcd1e", m.get(XMPMM.DOCUMENTID));

        m = getXML("testPDF_Version.11.x.PDFA-1b.pdf").metadata;
        assertEquals("uuid:cccee1fc-51b3-4b52-ac86-672af3974d25", m.get(XMPMM.DOCUMENTID));

        //now test for 7 elements in each parallel array
        //from the history section
        assertArrayEquals(
                new String[]{"uuid:0313504b-a0b0-4dac-a9f0-357221f2eadf",
                    "uuid:edc4279e-0d5f-465e-b13e-1298402fd11c",
                    "uuid:f565b775-43f3-4a9a-8541-e98c4115db6d",
                    "uuid:9fd5e0a8-14a5-4920-ad7f-870c0b8ee65f",
                    "uuid:09b6cfba-efde-4e07-a77f-70de858cc0aa",
                    "uuid:1e4ffbd7-dabc-4aae-801c-15b3404ade36",
                    "uuid:c1669773-a6ca-4bdd-aade-519030d0af00"},
                m.getValues(XMPMM.HISTORY_EVENT_INSTANCEID));

        assertArrayEquals(
                new String[]{"converted", "converted", "converted", "converted", "converted",
                        "converted", "converted"}, m.getValues(XMPMM.HISTORY_ACTION));

        assertArrayEquals(
                new String[]{"Preflight", "Preflight", "Preflight", "Preflight", "Preflight",
                        "Preflight", "Preflight"}, m.getValues(XMPMM.HISTORY_SOFTWARE_AGENT));

        assertArrayEquals(
                new String[]{"2014-03-04T22:50:41Z", "2014-03-04T22:50:42Z", "2014-03-04T22:51:34Z",
                        "2014-03-04T22:51:36Z", "2014-03-04T22:51:37Z", "2014-03-04T22:52:22Z",
                        "2014-03-04T22:54:48Z"}, m.getValues(XMPMM.HISTORY_WHEN));
    }

    @Test
    public void testSkipBadPage() throws Exception {
        //test file comes from govdocs1
        //can't use TikaTest shortcuts because of exception
        ContentHandler handler = new BodyContentHandler(-1);
        Metadata m = new Metadata();
        ParseContext context = new ParseContext();
        try (InputStream is = getResourceAsStream("/test-documents/testPDF_bad_page_303226.pdf")) {
            AUTO_DETECT_PARSER.parse(is, handler, m, context);
        }
        //as of PDFBox 2.0.28, exceptions are no longer thrown for this problem
        String content = handler.toString();
        assertEquals(0, m.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING).length);
        //assertContains("Unknown dir", m.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertContains("1309.61", content);

        //now try throwing exception immediately
        PDFParserConfig config = new PDFParserConfig();
        config.setCatchIntermediateIOExceptions(false);
        context.set(PDFParserConfig.class, config);

        handler = new BodyContentHandler(-1);
        m = new Metadata();
        try (InputStream is = getResourceAsStream("/test-documents/testPDF_bad_page_303226.pdf")) {
            AUTO_DETECT_PARSER.parse(is, handler, m, context);
        }
        content = handler.toString();
        assertEquals(0, m.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING).length);
        assertContains("1309.61", content);
    }

    @Test
    public void testInitializationViaConfig() throws Exception {
        try (InputStream is = getResourceAsStream(
                "/org/apache/tika/parser/pdf/tika-config.xml")) {
            assertNotNull(is);
            TikaConfig tikaConfig = new TikaConfig(is);
            Parser p = new AutoDetectParser(tikaConfig);

            String text =
                    getText(getResourceAsStream("/test-documents/testPDFTwoTextBoxes.pdf"), p);
            text = text.replaceAll("\\s+", " ");

            // Column text is now interleaved:
            assertContains(
                    "Left column line 1 Right column line 1 " +
                            "Left colu mn line 2 Right column line 2",
                    text);

            //test overriding underlying settings with PDFParserConfig
            ParseContext pc = new ParseContext();
            PDFParserConfig config = new PDFParserConfig();
            config.setSortByPosition(false);
            pc.set(PDFParserConfig.class, config);
            text = getText("testPDFTwoTextBoxes.pdf", p, new Metadata(), pc);
            text = text.replaceAll("\\s+", " ");
            // Column text is not interleaved:
            assertContains("Left column line 1 Left column line 2 ", text);

            //test a new PDFParserConfig and setting another value
            //this tests that the underlying "sortByPosition" as set
            //in the config file is still operative
            config = new PDFParserConfig();
            config.setOcrDPI(10000);
            config.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
            pc.set(PDFParserConfig.class, config);
            text = getText("testPDFTwoTextBoxes.pdf", p, new Metadata(), pc);
            text = text.replaceAll("\\s+", " ");

            // Column text is now interleaved:
            assertContains(
                    "Left column line 1 Right column line 1 Left " +
                            "colu mn line 2 Right column line 2",
                    text);
        }
    }

    @Test
    public void testInitializationOfNonPrimitivesViaConfig() throws Exception {
        try (InputStream is = getResourceAsStream(
                "/org/apache/tika/parser/pdf/tika-config-non-primitives.xml")) {
            assertNotNull(is);
            TikaConfig tikaConfig = new TikaConfig(is);
            AutoDetectParser p = new AutoDetectParser(tikaConfig);
            Map<MediaType, Parser> parsers = p.getParsers();
            Parser composite = parsers.get(MediaType.application("pdf"));
            Parser pdfParser =
                    ((CompositeParser) composite).getParsers().get(MediaType.application("pdf"));
            assertEquals("org.apache.tika.parser.pdf.PDFParser",
                    pdfParser.getClass().getName());
            assertEquals(PDFParserConfig.OCR_STRATEGY.OCR_ONLY,
                    ((PDFParser) pdfParser).getPDFParserConfig().getOcrStrategy());
            assertEquals(PDFParserConfig.TikaImageType.GRAY.RGB,
                    ((PDFParser) pdfParser).getPDFParserConfig().getOcrImageType());
        }
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
        try (InputStream configIs = getResourceAsStream(
                "/org/apache/tika/parser/pdf/tika-inline-config.xml")) {
            assertNotNull(configIs);
            TikaConfig tikaConfig = new TikaConfig(configIs);
            AutoDetectParser p = new AutoDetectParser(tikaConfig);
            //make absolutely certain the functionality works!
            List<Metadata> metadata = getRecursiveMetadata("testOCR.pdf", p);
            assertEquals(2, metadata.size());
            Map<MediaType, Parser> parsers = p.getParsers();
            Parser composite = parsers.get(MediaType.application("pdf"));
            Parser pdfParser =
                    ((CompositeParser) composite).getParsers().get(MediaType.application("pdf"));
            assertTrue(pdfParser instanceof PDFParser);
            PDFParserConfig pdfParserConfig = ((PDFParser) pdfParser).getPDFParserConfig();
            assertEquals(new AccessChecker(true), pdfParserConfig.getAccessChecker());
            assertEquals(true, pdfParserConfig.isExtractInlineImages());
            assertEquals(false, pdfParserConfig.isExtractUniqueInlineImagesOnly());
            assertEquals(314, pdfParserConfig.getOcrDPI());
            assertEquals(2.1f, pdfParserConfig.getOcrImageQuality(), .01f);
            assertEquals("jpeg", pdfParserConfig.getOcrImageFormatName());
            assertEquals(524288000, pdfParserConfig.getMaxMainMemoryBytes());
            assertEquals(false, pdfParserConfig.isCatchIntermediateIOExceptions());

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
            assertEquals(expected, e.getClass(), "Not the right exception: " + path);
        } finally {
            IOUtils.closeQuietly(is);
        }
        assertFalse(noEx, path + " should have thrown exception");
    }

    @Test
    public void testLanguageMetadata() throws Exception {
        assertEquals("de-CH",
                getXML("testPDF-custommetadata.pdf").metadata.get(TikaCoreProperties.LANGUAGE));
        assertEquals("zh-CN",
                getXML("testPDFFileEmbInAnnotation.pdf").metadata.get(TikaCoreProperties.LANGUAGE));
    }

    @Test
    public void testAngles() throws Exception {
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setDetectAngles(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, pdfParserConfig);
        String xml = getXML("testPDF_angles.pdf", parseContext).xml;
        //make sure there is only one page!
        assertContainsCount("<div class=\"page\">", xml, 1);
        assertContains("IN-DEMAND", xml);
        assertContains("natural underground", xml);
        assertContains("transport mined materials", xml);
    }

    @Test
    public void testAnglesOnPageRotation() throws Exception {
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setDetectAngles(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, pdfParserConfig);
        String xml = getXML("testPDF_rotated.pdf", parseContext).xml;
        assertContains("until a further review indicates that the infrastructure", xml);
    }

    @Test
    public void testUnmappedUnicodeStats() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPDF_bad_page_303226.pdf", true);
        Metadata m = metadataList.get(0);
        int[] totalChars = m.getIntValues(PDF.CHARACTERS_PER_PAGE);
        int[] unmappedUnicodeChars = m.getIntValues(PDF.UNMAPPED_UNICODE_CHARS_PER_PAGE);
        int totalUnmappedChars = m.getInt(PDF.TOTAL_UNMAPPED_UNICODE_CHARS);
        float overallPercentage =
                Float.parseFloat(m.get(PDF.OVERALL_PERCENTAGE_UNMAPPED_UNICODE_CHARS));

        //weird issue with pdfbox 2.0.20
        //this test passes in my IDE, but does not pass with mvn clean install from commandline
        if (totalChars[15] > 0) {
            assertEquals(3805, totalChars[15]);
            assertEquals(120, unmappedUnicodeChars[15]);
            assertEquals(126, totalUnmappedChars);
            assertEquals(0.00146, overallPercentage, 0.0001f);
            assertTrue(Boolean.parseBoolean(m.get(PDF.CONTAINS_NON_EMBEDDED_FONT)));
            assertFalse(Boolean.parseBoolean(m.get(PDF.CONTAINS_DAMAGED_FONT)));
        }
        //confirm all works with angles
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setDetectAngles(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, pdfParserConfig);
        metadataList = getRecursiveMetadata("testPDF_bad_page_303226.pdf", parseContext, true);
        m = metadataList.get(0);
        totalChars = m.getIntValues(PDF.CHARACTERS_PER_PAGE);
        unmappedUnicodeChars = m.getIntValues(PDF.UNMAPPED_UNICODE_CHARS_PER_PAGE);
        if (totalChars[15] > 0) {
            assertEquals(3805, totalChars[15]);
            assertEquals(120, unmappedUnicodeChars[15]);
        }

    }

    @Test
    public void testNPEInPDFParserConfig() {
        //TIKA-3091
        PDFParserConfig config = new PDFParserConfig();
        //don't care about values; want to make sure no NPE is thrown
        String txt = config.toString();
        config.hashCode();
        config.equals(new PDFParserConfig());
    }

    @Test //TIKA-3041
    @Disabled("turn back on if we add file from PDFBOX-52")
    public void testPDFBox52() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);

        List<Metadata> metadataList = getRecursiveMetadata("testPDF_PDFBOX-52.pdf", context);
        int max = 0;
        Matcher matcher = Pattern.compile("image(\\d+)").matcher("");
        for (Metadata m : metadataList) {
            String n = m.get(TikaCoreProperties.RESOURCE_NAME_KEY);

            if (n != null && matcher.reset(n).find()) {
                int i = Integer.parseInt(matcher.group(1));
                if (i > max) {
                    max = i;
                }
            }
        }
        assertEquals(37, metadataList.size());
        assertEquals(35, max);
    }

    @Test
    public void testXMPBasicSchema() throws Exception {
        //TIKA-3101
        List<Metadata> metadataList = getRecursiveMetadata("testPDF_XMPBasicSchema.pdf");
        Metadata m = metadataList.get(0);
        //these two fields derive from the basic schema in the XMP, not dublin core
        assertEquals("Hewlett-Packard MFP", m.get(XMP.CREATOR_TOOL));
        assertEquals("1998-08-29T14:53:15Z", m.get(XMP.CREATE_DATE));
    }

    @Test
    public void testXMPPDFSchema() throws Exception {
        //as of this writing, we don't currently have any pdfs in our
        //test suite with data that is different btwn pdf doc info and xmp. :(
        Metadata metadata = getXML("testPopupAnnotation.pdf").metadata;
        assertEquals("IBM Lotus Symphony 3.0", metadata.get(PDF.PRODUCER));
    }

    @Test
    public void testExtractInlineImageMetadata() throws Exception {
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImageMetadataOnly(true);
        context.set(PDFParserConfig.class, config);
        List<Metadata> metadataList = getRecursiveMetadata("testOCR.pdf", context);
        assertNull(context.get(ZeroByteFileException.IgnoreZeroByteFileException.class));
        assertEquals(2, metadataList.size());
        assertEquals("image/png", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("/image0.png",
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertEquals(261, (int) metadataList.get(1).getInt(Metadata.IMAGE_LENGTH));
        assertEquals(934, (int) metadataList.get(1).getInt(Metadata.IMAGE_WIDTH));
        assertEquals("image0.png", metadataList.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    /**
     * Simple class to count end of document events.  If functionality is useful,
     * move to org.apache.tika in src/test
     */
    private static class EventCountingHandler extends ContentHandlerDecorator {
        private int endDocument = 0;

        @Override
        public void endDocument() {
            endDocument++;
        }

        public int getEndDocument() {
            return endDocument;
        }
    }

    private static class AvoidInlineSelector implements DocumentSelector {

        @Override
        public boolean select(Metadata metadata) {
            String v = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null && v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())) {
                return false;
            }
            return true;
        }
    }

    @Test
    public void testDeeplyEmbeddedAttachments() throws Exception {
        //test file comes from pdfcpu issue #120: https://github.com/pdfcpu/pdfcpu/issues/201
        //in our regression corpus: pdfcpu-201-0.zip-0.pdf");
        List<Metadata> metadataList = getRecursiveMetadata(
                "testPDF_deeplyEmbeddedAttachments.pdf");
        assertEquals(21, metadataList.size());
    }

    @Test
    public void testEmbeddedRichMedia() throws Exception {
        List<Metadata> metadata = getRecursiveMetadata("testFlashInPDF.pdf");
        assertEquals(2, metadata.size());
        assertEquals("application/x-shockwave-flash", metadata.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("TestMovie02.swf", metadata.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("15036", metadata.get(1).get(Metadata.CONTENT_LENGTH));
        assertEquals("RichMedia", metadata.get(0).getValues(PDF.ANNOTATION_SUBTYPES)[0]);
        assertEquals("RM1", metadata.get(0).getValues(PDF.ANNOTATION_TYPES)[0]);
    }


    @Test
    public void testCustomGraphicsEngineFactory() throws Exception {
        try (InputStream is =
                     getResourceAsStream(
                             "tika-config-custom-graphics-engine.xml")) {
            assertNotNull(is);
            TikaConfig tikaConfig = new TikaConfig(is);
            Parser p = new AutoDetectParser(tikaConfig);
            try {
                List<Metadata> metadataList = getRecursiveMetadata("testPDF_JBIG2.pdf", p);
                fail("should have thrown a runtime exception");
            } catch (TikaException e) {
                String stack = ExceptionUtils.getStackTrace(e);
                assertContains("testing123", stack);
            }
        }
    }

    @Test
    public void testAI() throws Exception {
        //This is file 1508.ai on PDFBOX-3385
        //I changed the extension to pdf to make sure that the detection is
        //coming from the structural chek we're now doing.
        List<Metadata> metadataList = getRecursiveMetadata("testPDF_AdobeIllustrator.pdf");
        assertEquals("application/illustrator", metadataList.get(0).get(Metadata.CONTENT_TYPE));
        //we should try to find a small illustrator file xmp and the structural
        //components we're looking for.
    }

    @Test
    public void testThrowOnEncryptedPayload() throws Exception {
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setThrowOnEncryptedPayload(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, pdfParserConfig);
        assertThrows(EncryptedDocumentException.class, () -> {
            getRecursiveMetadata("testMicrosoftIRMServices.pdf", parseContext);
        });
    }

    @Test
    public void testAFRelationshipAndException() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testMicrosoftIRMServices.pdf");
        assertEquals(2, metadataList.size());
        assertEquals("EncryptedPayload", metadataList.get(1).get(PDF.ASSOCIATED_FILE_RELATIONSHIP));
        assertContains("EncryptedDocumentException",
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_EXCEPTION));

    }
    @Test
    public void testDefaultPDFOCR() throws Exception {
        //test that even with no ocr -- there is no tesseract ocr parser in this module --
        // AUTO mode would have returned one page that would have been OCR'd had there been OCR.
        List<Metadata> metadataList = getRecursiveMetadata("testOCR.pdf");
        assertEquals(1, metadataList.size());
        assertEquals(1, metadataList.get(0).getInt(PDF.OCR_PAGE_COUNT));
    }
    /**
     * TODO -- need to test signature extraction
     */

    /**
    @Test
    public void testWriteLimit() throws Exception {
        for (int i = 0; i < 10000; i += 13) {
            Metadata metadata = testWriteLimit("testPDF_childAttachments.pdf", i);
            assertEquals("true", metadata.get(TikaCoreProperties.WRITE_LIMIT_REACHED));
            int len = metadata.get(TikaCoreProperties.TIKA_CONTENT).length();
            System.out.println(len + " : " + i);
            assertTrue(len <= i);
        }
    }

    private Metadata testWriteLimit(String fileName, int limit) throws Exception {
        BasicContentHandlerFactory factory = new BasicContentHandlerFactory(
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, limit
        );
        ContentHandler contentHandler = factory.getNewContentHandler();
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        try (InputStream is = getResourceAsStream("/test-documents/" + fileName)) {
            AUTO_DETECT_PARSER.parse(is, contentHandler, metadata, parseContext);
        } catch (WriteLimitReachedException e) {
            //e.printStackTrace();
        }
        metadata.set(TikaCoreProperties.TIKA_CONTENT, contentHandler.toString());
        return metadata;
    }*/
}
