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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.tika.TikaTest;
import org.apache.tika.exception.AccessPermissionException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.ToXMLContentHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;
/**
 * Test case for parsing pdf files.
 */
public class PDFParserTest extends TikaTest {

    public static final MediaType TYPE_TEXT = MediaType.TEXT_PLAIN;    
    public static final MediaType TYPE_EMF = MediaType.application("x-emf");
    public static final MediaType TYPE_PDF = MediaType.application("pdf");
    public static final MediaType TYPE_DOCX = MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document");
    public static final MediaType TYPE_DOC = MediaType.application("msword");
    public static Level PDFBOX_LOG_LEVEL = Level.INFO;

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

    @Test
    public void testPdfParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        Metadata metadata = new Metadata();

        InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDF.pdf");

        String content = getText(stream, parser, metadata);

        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Bertrand Delacr\u00e9taz", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Bertrand Delacr\u00e9taz", metadata.get(Metadata.AUTHOR));
        assertEquals("Firefox", metadata.get(TikaCoreProperties.CREATOR_TOOL));
        assertEquals("Apache Tika - Apache Tika", metadata.get(TikaCoreProperties.TITLE));
        
        // Can't reliably test dates yet - see TIKA-451 
//        assertEquals("Sat Sep 15 10:02:31 BST 2007", metadata.get(Metadata.CREATION_DATE));
//        assertEquals("Sat Sep 15 10:02:31 BST 2007", metadata.get(Metadata.LAST_MODIFIED));

        assertContains("Apache Tika", content);
        assertContains("Tika - Content Analysis Toolkit", content);
        assertContains("incubator", content);
        assertContains("Apache Software Foundation", content);
        // testing how the end of one paragraph is separated from start of the next one
        assertTrue("should have word boundary after headline", 
                !content.contains("ToolkitApache"));
        assertTrue("should have word boundary between paragraphs", 
                !content.contains("libraries.Apache"));
    }
    
    @Test
    public void testPdfParsingMetadataOnly() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        Metadata metadata = new Metadata();

        InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDF.pdf");

        try {
            parser.parse(stream, null, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Bertrand Delacr\u00e9taz", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Firefox", metadata.get(TikaCoreProperties.CREATOR_TOOL));
        assertEquals("Apache Tika - Apache Tika", metadata.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testCustomMetadata() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        Metadata metadata = new Metadata();

        InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDF-custommetadata.pdf");

        String content = getText(stream, parser, metadata);

        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Document author", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Document author", metadata.get(Metadata.AUTHOR));
        assertEquals("Document title", metadata.get(TikaCoreProperties.TITLE));
        
        assertEquals("Custom Value", metadata.get("Custom Property"));
        
        assertEquals("Array Entry 1", metadata.get("Custom Array"));
        assertEquals(2, metadata.getValues("Custom Array").length);
        assertEquals("Array Entry 1", metadata.getValues("Custom Array")[0]);
        assertEquals("Array Entry 2", metadata.getValues("Custom Array")[1]);
        
        assertContains("Hello World!", content);
    }
    
    /**
     * PDFs can be "protected" with the default password. This means
     *  they're encrypted (potentially both text and metadata),
     *  but we can decrypt them easily.
     */
    @Test
    public void testProtectedPDF() throws Exception {
       Parser parser = new AutoDetectParser(); // Should auto-detect!
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();
       ParseContext context = new ParseContext();

       InputStream stream = PDFParserTest.class.getResourceAsStream(
               "/test-documents/testPDF_protected.pdf");
       try {
           parser.parse(stream, handler, metadata, context);
       } finally {
           stream.close();
       }

       assertEquals("true", metadata.get("pdf:encrypted"));
       assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("The Bank of England", metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("The Bank of England", metadata.get(Metadata.AUTHOR));
       assertEquals("Speeches by Andrew G Haldane", metadata.get(OfficeOpenXMLCore.SUBJECT));
       assertEquals("Speeches by Andrew G Haldane", metadata.get(Metadata.SUBJECT));
       assertEquals("Rethinking the Financial Network, Speech by Andrew G Haldane, Executive Director, Financial Stability delivered at the Financial Student Association, Amsterdam on 28 April 2009", metadata.get(TikaCoreProperties.TITLE));

       String content = handler.toString();
       assertContains("RETHINKING THE FINANCIAL NETWORK", content);
       assertContains("On 16 November 2002", content);
       assertContains("In many important respects", content);
       
       
       // Try again with an explicit empty password
       handler = new BodyContentHandler();
       metadata = new Metadata();
       
       context = new ParseContext();
       context.set(PasswordProvider.class, new PasswordProvider() {
           public String getPassword(Metadata metadata) {
              return "";
          }
       });
       
       stream = PDFParserTest.class.getResourceAsStream(
                  "/test-documents/testPDF_protected.pdf");
       try {
          parser.parse(stream, handler, metadata, context);
       } finally {
          stream.close();
       }
       assertEquals("true", metadata.get("pdf:encrypted"));

       assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("The Bank of England", metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("Speeches by Andrew G Haldane", metadata.get(OfficeOpenXMLCore.SUBJECT));
       assertEquals("Speeches by Andrew G Haldane", metadata.get(Metadata.SUBJECT));
       assertEquals("Rethinking the Financial Network, Speech by Andrew G Haldane, Executive Director, Financial Stability delivered at the Financial Student Association, Amsterdam on 28 April 2009", metadata.get(TikaCoreProperties.TITLE));

       assertContains("RETHINKING THE FINANCIAL NETWORK", content);
       assertContains("On 16 November 2002", content);
       assertContains("In many important respects", content);

        //now test wrong password
        handler = new BodyContentHandler();
        metadata = new Metadata();
        context = new ParseContext();
        context.set(PasswordProvider.class, new PasswordProvider() {
            public String getPassword(Metadata metadata) {
                return "WRONG!!!!";
            }
        });

        stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDF_protected.pdf");
        boolean ex = false;
        try {
            parser.parse(stream, handler, metadata, context);
        } catch (EncryptedDocumentException e) {
            ex = true;
        } finally {
            stream.close();
        }
        content = handler.toString();

        assertTrue("encryption exception", ex);
        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("true", metadata.get("pdf:encrypted"));
        //pdf:encrypted, X-Parsed-By and Content-Type
        assertEquals("very little metadata should be parsed", 3, metadata.names().length);
        assertEquals(0, content.length());

        //now test wrong password with non sequential parser
        handler = new BodyContentHandler();
        metadata = new Metadata();
        context = new ParseContext();
        context.set(PasswordProvider.class, new PasswordProvider() {
            public String getPassword(Metadata metadata) {
                return "WRONG!!!!";
            }
        });
        PDFParserConfig config = new PDFParserConfig();
        config.setUseNonSequentialParser(true);
        context.set(PDFParserConfig.class, config);

        stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDF_protected.pdf");
        ex = false;
        try {
            parser.parse(stream, handler, metadata, context);
        } catch (EncryptedDocumentException e) {
            ex = true;
        } finally {
            stream.close();
        }
        content = handler.toString();
        assertTrue("encryption exception", ex);
        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("true", metadata.get("pdf:encrypted"));

        //pdf:encrypted, X-Parsed-By and Content-Type
        assertEquals("very little metadata should be parsed", 3, metadata.names().length);
        assertEquals(0, content.length());
    }

    @Test
    public void testTwoTextBoxes() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDFTwoTextBoxes.pdf");
        String content = getText(stream, parser);
        content = content.replaceAll("\\s+"," ");
        assertContains("Left column line 1 Left column line 2 Right column line 1 Right column line 2", content);
    }

    @Test
    public void testVarious() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        Metadata metadata = new Metadata();
        InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDFVarious.pdf");

        String content = getText(stream, parser, metadata);
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
        InputStream stream = getResourceAsStream("/test-documents/testAnnotations.pdf");
        String content = getText(stream, parser);
        content = content.replaceAll("[\\s\u00a0]+"," ");
        assertContains("Here is some text", content);
        assertContains("Here is a comment", content);

        // Test w/ annotation text disabled:
        PDFParser pdfParser = new PDFParser();
        pdfParser.getPDFParserConfig().setExtractAnnotationText(false);
        stream = getResourceAsStream("/test-documents/testAnnotations.pdf");
        content = getText(stream, pdfParser);
        content = content.replaceAll("[\\s\u00a0]+"," ");
        assertContains("Here is some text", content);
        assertEquals(-1, content.indexOf("Here is a comment"));

        // annotation text disabled through parsecontext
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractAnnotationText(false);
        context.set(PDFParserConfig.class, config);
        stream = getResourceAsStream("/test-documents/testAnnotations.pdf");
        content = getText(stream, parser, context);
        content = content.replaceAll("[\\s\u00a0]+"," ");
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
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        InputStream stream = getResourceAsStream("/test-documents/testPopupAnnotation.pdf");
        String content = getText(stream, parser);
        assertContains("this is the note", content);
        assertContains("igalsh", content);
    }

    @Test
    public void testEmbeddedPDFs() throws Exception {
        String xml = getXML("testPDFPackage.pdf").xml;
        assertContains("PDF1", xml);
        assertContains("PDF2", xml);
    }

    private static int substringCount(String needle, String haystack) {
        int upto = -1;
        int count = 0;
        while(true) {
            final int next = haystack.indexOf(needle, upto);
            if (next == -1) {
                break;
            }
            count++;
            upto = next+1;
        }

        return count;
    }

    @Test
    public void testPageNumber() throws Exception {
        final XMLResult result = getXML("testPageNumber.pdf");
        final String content = result.xml.replaceAll("\\s+","");
        assertContains("<p>1</p>", content);
    }

    /**
     * Test to ensure that Links are extracted from the text
     * 
     * Note - the PDF contains the text "This is a hyperlink" which
     *  a hyperlink annotation, linking to the tika site, on it. This
     *  test will need updating when we're able to apply the annotation
     *  to the text itself, rather than following on afterwards as now 
     */
    @Test
    public void testLinks() throws Exception {
        final XMLResult result = getXML("testPDFVarious.pdf");
        assertContains("<div class=\"annotation\"><a href=\"http://tika.apache.org/\" /></div>", result.xml);
    }

    @Test
    public void testDisableAutoSpace() throws Exception {
        PDFParser parser = new PDFParser();
        parser.getPDFParserConfig().setEnableAutoSpace(false);
        InputStream stream = getResourceAsStream("/test-documents/testExtraSpaces.pdf");
        String content = getText(stream, parser);
        content = content.replaceAll("[\\s\u00a0]+"," ");
        // Text is correct when autoSpace is off:
        assertContains("Here is some formatted text", content);

        parser.getPDFParserConfig().setEnableAutoSpace(true);
        stream = getResourceAsStream("/test-documents/testExtraSpaces.pdf");
        content = getText(stream, parser);
        content = content.replaceAll("[\\s\u00a0]+"," ");
        // Text is correct when autoSpace is off:

        // Text has extra spaces when autoSpace is on
        assertEquals(-1, content.indexOf("Here is some formatted text"));
        
        //now try with autodetect
        Parser autoParser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        context.set(PDFParserConfig.class, config);
        //default is true
        stream = getResourceAsStream("/test-documents/testExtraSpaces.pdf");
        content = getText(stream, autoParser, context);
        content = content.replaceAll("[\\s\u00a0]+"," ");
        // Text has extra spaces when autoSpace is on
        assertEquals(-1, content.indexOf("Here is some formatted text"));

        config.setEnableAutoSpace(false);
        
        stream = getResourceAsStream("/test-documents/testExtraSpaces.pdf");
        content = getText(stream, parser, context);
        content = content.replaceAll("[\\s\u00a0]+"," ");
        // Text is correct when autoSpace is off:
        assertContains("Here is some formatted text", content);
        
    }

    @Test
    public void testDuplicateOverlappingText() throws Exception {
        PDFParser parser = new PDFParser();
        InputStream stream = getResourceAsStream("/test-documents/testOverlappingText.pdf");
        // Default is false (keep overlapping text):
        String content = getText(stream, parser);
        assertContains("Text the first timeText the second time", content);

        parser.getPDFParserConfig().setSuppressDuplicateOverlappingText(true);
        stream = getResourceAsStream("/test-documents/testOverlappingText.pdf");
        content = getText(stream, parser);
        // "Text the first" was dedup'd:
        assertContains("Text the first timesecond time", content);
        
        //now try with autodetect
        Parser autoParser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        context.set(PDFParserConfig.class, config);
        stream = getResourceAsStream("/test-documents/testOverlappingText.pdf");
        // Default is false (keep overlapping text):
        content = getText(stream, autoParser, context);
        assertContains("Text the first timeText the second time", content);

        config.setSuppressDuplicateOverlappingText(true);
        stream = getResourceAsStream("/test-documents/testOverlappingText.pdf");
        content = getText(stream, autoParser, context);
        // "Text the first" was dedup'd:
        assertContains("Text the first timesecond time", content);

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

        parser.getPDFParserConfig().setSortByPosition(true);
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
    
    //TIKA-1124
    @Test
    public void testEmbeddedPDFEmbeddingAnotherDocument() throws Exception {
       /* format of test doc:
         docx/
            pdf/
               docx
       */ 
       Parser parser = new AutoDetectParser(); // Should auto-detect!
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();
       ParseContext context = new ParseContext();
       String content = "";
       InputStream stream = null;
       try {
          context.set(org.apache.tika.parser.Parser.class, parser);
          stream = getResourceAsStream("/test-documents/testPDFEmbeddingAndEmbedded.docx");
          parser.parse(stream, handler, metadata, context);
          content = handler.toString();
       } finally {
          stream.close();
       }
       int outerHaystack = content.indexOf("Outer_haystack");
       int pdfHaystack = content.indexOf("pdf_haystack");
       int needle = content.indexOf("Needle");
       assertTrue(outerHaystack > -1);
       assertTrue(pdfHaystack > -1);
       assertTrue(needle > -1);
       assertTrue(needle > pdfHaystack && pdfHaystack > outerHaystack);
       
       TrackingHandler tracker = new TrackingHandler();
       TikaInputStream tis;
       ContainerExtractor ex = new ParserContainerExtractor();
       try{
          tis= TikaInputStream.get(getResourceAsStream("/test-documents/testPDFEmbeddingAndEmbedded.docx"));
          ex.extract(tis, ex, tracker);
       } finally {
          stream.close();
       }
       assertEquals(true, ex.isSupported(tis));
       assertEquals(3, tracker.filenames.size());
       assertEquals(3, tracker.mediaTypes.size());
       assertEquals("image1.emf", tracker.filenames.get(0));
       assertNull(tracker.filenames.get(1));
       assertEquals("Test.docx", tracker.filenames.get(2));
       assertEquals(TYPE_EMF, tracker.mediaTypes.get(0));
       assertEquals(TYPE_PDF, tracker.mediaTypes.get(1));
       assertEquals(TYPE_DOCX, tracker.mediaTypes.get(2));
   }

    /**
     * tests for equality between traditional sequential parser
     * and newer nonsequential parser.
     * 
     * TODO: more testing
     */
    @Test
    public void testSequentialParser() throws Exception{

        Parser sequentialParser = new AutoDetectParser();
        Parser nonSequentialParser = new AutoDetectParser();

        ParseContext seqContext = new ParseContext();
        PDFParserConfig seqConfig = new PDFParserConfig();
        seqConfig.setUseNonSequentialParser(false);
        seqContext.set(PDFParserConfig.class, seqConfig);

        ParseContext nonSeqContext = new ParseContext();
        PDFParserConfig nonSeqConfig = new PDFParserConfig();
        nonSeqConfig.setUseNonSequentialParser(true);
        nonSeqContext.set(PDFParserConfig.class, nonSeqConfig);

        File testDocs = new File(this.getClass().getResource("/test-documents").toURI());
        int pdfs = 0;
        Set<String> knownMetadataDiffs = new HashSet<String>();
        //PDFBox-1792/Tika-1203
        knownMetadataDiffs.add("testAnnotations.pdf");
        // Added for TIKA-93.
        knownMetadataDiffs.add("testOCR.pdf");

        //empty for now
        Set<String> knownContentDiffs = new HashSet<String>();

        for (File f : testDocs.listFiles()) {
            if (! f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                continue;
            }

            String sequentialContent = null;
            Metadata sequentialMetadata = new Metadata();
            try {
                sequentialContent = getText(new FileInputStream(f), 
                        sequentialParser, seqContext, sequentialMetadata);
            } catch (EncryptedDocumentException e) {
                //silently skip a file that requires a user password
                continue;
            } catch (Exception e) {
                throw new TikaException("Sequential Parser failed on test file " + f, e);
            }

            pdfs++;

            String nonSequentialContent = null;
            Metadata nonSequentialMetadata = new Metadata();
            try {
                nonSequentialContent = getText(new FileInputStream(f), 
                     nonSequentialParser, nonSeqContext, nonSequentialMetadata);
            } catch (Exception e) {
                throw new TikaException("Non-Sequential Parser failed on test file " + f, e);
            }

            if (knownContentDiffs.contains(f.getName())) {
                assertFalse(f.getName(), sequentialContent.equals(nonSequentialContent));
            } else {
                assertEquals(f.getName(), sequentialContent, nonSequentialContent);
            }

            //skip this one file.
            if (knownMetadataDiffs.contains(f.getName())) {
                assertFalse(f.getName(), sequentialMetadata.equals(nonSequentialMetadata));
            } else {
                assertEquals(f.getName(), sequentialMetadata, nonSequentialMetadata);
            }
        }
        //make sure nothing went wrong with getting the resource to test-documents
        //must have tested >= 15 pdfs
        boolean ge15 = (pdfs >= 15);
        assertTrue("Number of pdf files tested >= 15 in non-sequential parser test", ge15);
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
        TikaInputStream tis = null;
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);
        context.set(org.apache.tika.parser.pdf.PDFParserConfig.class, config);
        context.set(org.apache.tika.parser.Parser.class, p);

        try {
            tis= TikaInputStream.get(
                    getResourceAsStream("/test-documents/testPDF_childAttachments.pdf"));
            p.parse(tis, new BodyContentHandler(-1), new Metadata(), context);
        } finally {
            if (tis != null) {
                tis.close();
            }
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


    @Test
    public void testEmbeddedFilesInAnnotations() throws Exception {
        String xml = getXML("/testPDFFileEmbInAnnotation.pdf").xml;

        assertTrue(xml.contains("This is a Excel"));
    }

    @Test
    public void testSingleCloseDoc() throws Exception {
        //TIKA-1341
        InputStream is = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDFTripleLangTitle.pdf");
        Parser p = new AutoDetectParser();
        Metadata m = new Metadata();
        ParseContext c = new ParseContext();
        ContentHandler h = new EventCountingHandler();
        p.parse(is, h,  m,  c);
        assertEquals(1, ((EventCountingHandler)h).getEndDocument());
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

        Parser p = new AutoDetectParser();
        for (Map.Entry<String, String> e : dcFormat.entrySet()) {
            String fName = "testPDF_Version."+e.getKey()+".pdf";
            InputStream is = PDFParserTest.class.getResourceAsStream(
                    "/test-documents/"+fName);
            Metadata m = new Metadata();
            ContentHandler h = new BodyContentHandler();
            ParseContext c = new ParseContext();
            p.parse(is, h, m, c);
            is.close();
            boolean foundDC = false;
            String[] vals = m.getValues("dc:format");
            for (String v : vals) {
                if (v.equals(e.getValue())) {
                    foundDC = true;
                }
            }
            assertTrue("dc:format ::" + e.getValue(), foundDC);
            String extensionVersionTruth = pdfExtensionVersions.get(e.getKey());
            if (extensionVersionTruth != null) {
                assertEquals("pdf:PDFExtensionVersion :: "+extensionVersionTruth,
                        extensionVersionTruth, 
                        m.get("pdf:PDFExtensionVersion"));
            }
            assertEquals("pdf:PDFVersion", pdfVersions.get(e.getKey()),
                    m.get("pdf:PDFVersion"));
        }
        //now test full 11.x
        String fName = "testPDF_Version.11.x.PDFA-1b.pdf";
        InputStream is = PDFParserTest.class.getResourceAsStream(
                "/test-documents/"+fName);
        Metadata m = new Metadata();
        ParseContext c = new ParseContext();
        ContentHandler h = new BodyContentHandler();
        p.parse(is, h, m, c);
        is.close();
        Set<String> versions = new HashSet<String>();
        for (String fmt : m.getValues("dc:format")) {
            versions.add(fmt);
        }
        
        for (String hit : new String[]{ "application/pdf; version=1.7",
          "application/pdf; version=\"A-1b\"",
          "application/pdf; version=\"1.7 Adobe Extension Level 8\""
        }) {
            assertTrue(hit, versions.contains(hit));
        }
        
        assertEquals("pdfaid:conformance", m.get("pdfaid:conformance"), "B");
        assertEquals("pdfaid:part", m.get("pdfaid:part"), "1");
    }

    @Test
    public void testMultipleAuthors() throws Exception {
        String fName = "testPDF_twoAuthors.pdf";
        InputStream is = PDFParserTest.class.getResourceAsStream(
                "/test-documents/"+fName);
        Parser p = new AutoDetectParser();
        Metadata m = new Metadata();
        ParseContext c = new ParseContext();
        ContentHandler h = new BodyContentHandler();
        p.parse(is, h, m, c);
        is.close();
        
        String[] keys = new String[] {
                "dc:creator",
                "meta:author",
                "creator",
                "Author"
        };

        for (String k : keys) {
            String[] vals = m.getValues(k);
            assertEquals("number of authors == 2 for key: "+ k, 2, vals.length);
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
        InputStream is = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDFTripleLangTitle.pdf");
        Parser p = new AutoDetectParser();
        Metadata m = new Metadata();
        ParseContext c = new ParseContext();
        ContentHandler h = new BodyContentHandler();
        p.parse(is, h, m, c);
        is.close();
        //TODO: add other tests as part of TIKA-1295
        //dc:title-fr-ca (or whatever we decide) should be "Bonjour World"
        //dc:title-zh-ch is currently hosed...bug in PDFBox while injecting xmp?
        //
        assertEquals("Hello World", m.get("dc:title"));
    }

    @Test
    public void testInlineSelector() throws Exception {
        
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);

        Parser defaultParser = new AutoDetectParser();

        RecursiveParserWrapper p = new RecursiveParserWrapper(defaultParser,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));
        ParseContext context = new ParseContext();
        context.set(org.apache.tika.parser.pdf.PDFParserConfig.class, config);
        context.set(org.apache.tika.parser.Parser.class, p);
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(-1);
        String path = "/test-documents/testPDF_childAttachments.pdf";
        InputStream stream = TikaInputStream.get(this.getClass().getResource(path));

        p.parse(stream, handler, metadata, context);

        List<Metadata> metadatas = p.getMetadata();
        int inline = 0;
        int attach = 0;
        for (Metadata m : metadatas) {
            String v = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null) {
                if (v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())){
                    inline++;
                } else if (v.equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())){
                    attach++;
                }
            }
        }
        assertEquals(2, inline);
        assertEquals(2, attach);

        stream.close();
        p.reset();

        //now try turning off inline
        stream = TikaInputStream.get(this.getClass().getResource(path));

        context.set(org.apache.tika.extractor.DocumentSelector.class, new AvoidInlineSelector());
        inline = 0;
        attach = 0;
        handler = new BodyContentHandler(-1);
        metadata = new Metadata();
        p.parse(stream, handler, metadata, context);

        metadatas = p.getMetadata();
        for (Metadata m : metadatas) {
            String v = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null) {
                if (v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())){
                    inline++;
                } else if (v.equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())){
                    attach++;
                }
            }
        }
        assertEquals(0, inline);
        assertEquals(2, attach);

    }


    @Test
    public void testInlineConfig() throws Exception {
        
        Parser defaultParser = new AutoDetectParser();
        RecursiveParserWrapper p = new RecursiveParserWrapper(defaultParser,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));
        ParseContext context = new ParseContext();
        context.set(org.apache.tika.parser.Parser.class, p);
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(-1);
        String path = "/test-documents/testPDF_childAttachments.pdf";
        InputStream stream = TikaInputStream.get(this.getClass().getResource(path));

        p.parse(stream, handler, metadata, context);

        List<Metadata> metadatas = p.getMetadata();
        int inline = 0;
        int attach = 0;
        for (Metadata m : metadatas) {
            String v = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null) {
                if (v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())){
                    inline++;
                } else if (v.equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())){
                    attach++;
                }
            }
        }
        assertEquals(0, inline);
        assertEquals(2, attach);

        stream.close();
        p.reset();

        //now try turning off inline
        stream = TikaInputStream.get(this.getClass().getResource(path));
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);

        context.set(org.apache.tika.parser.pdf.PDFParserConfig.class, config);
        inline = 0;
        attach = 0;
        handler = new BodyContentHandler(-1);
        metadata = new Metadata();
        p.parse(stream, handler, metadata, context);

        metadatas = p.getMetadata();
        for (Metadata m : metadatas) {
            String v = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (v != null) {
                if (v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())){
                    inline++;
                } else if (v.equals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString())){
                    attach++;
                }
            }
        }
        assertEquals(2, inline);
        assertEquals(2, attach);
    }

    @Test //TIKA-1376
    public void testEmbeddedFileNameExtraction() throws Exception {
        InputStream is = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDF_multiFormatEmbFiles.pdf");
        RecursiveParserWrapper p = new RecursiveParserWrapper(
                new AutoDetectParser(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));
        Metadata m = new Metadata();
        ParseContext c = new ParseContext();
        c.set(org.apache.tika.parser.Parser.class, p);
        ContentHandler h = new BodyContentHandler();
        p.parse(is, h, m, c);
        is.close();
        List<Metadata> metadatas = p.getMetadata();
        assertEquals("metadata size", 5, metadatas.size());
        Metadata firstAttachment = metadatas.get(1);
        assertEquals("attachment file name", "Test.txt", firstAttachment.get(Metadata.RESOURCE_NAME_KEY));
    }

    @Test //TIKA-1374
    public void testOSSpecificEmbeddedFileExtraction() throws Exception {
        InputStream is = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDF_multiFormatEmbFiles.pdf");
        RecursiveParserWrapper p = new RecursiveParserWrapper(
                new AutoDetectParser(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        Metadata m = new Metadata();
        ParseContext c = new ParseContext();
        c.set(org.apache.tika.parser.Parser.class, p);
        ContentHandler h = new BodyContentHandler();
        p.parse(is, h, m, c);
        is.close();
        List<Metadata> metadatas = p.getMetadata();
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


        Metadata metadata = new Metadata();
        ContentHandler handler = new ToXMLContentHandler();
        String path = "/test-documents/testPDF_childAttachments.pdf";
        InputStream stream = null;
        try {
            stream = TikaInputStream.get(this.getClass().getResource(path));
            parser.parse(stream, handler, metadata, context);
        } finally {
            IOUtils.closeQuietly(stream);
        }

        String xml = handler.toString();
        //regular attachment
        assertContains("<div class=\"embedded\" id=\"Unit10.doc\" />", xml);
        //inline image
        assertContains("<img src=\"embedded:image1.tif\" alt=\"image1.tif\" />", xml);

        //doc embedded inside an annotation
        xml = getXML("testPDFFileEmbInAnnotation.pdf").xml;
        assertContains("<div class=\"embedded\" id=\"Excel.xlsx\" />", xml);
    }

    //Access checker tests

    @Test
    public void testLegacyAccessChecking() throws Exception {
        //test that default behavior doesn't throw AccessPermissionException
        for (String file : new String[] {
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

        for (String path : new String[] {
                "testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",
        }) {
            InputStream stream = null;
            try {
                stream = TikaInputStream.get(this.getClass().getResource("/test-documents/"+path));
                String text = getText(stream, parser, context);
                assertContains("Hello World", text);
            } finally {
                IOUtils.closeQuietly(stream);
            }
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
        for (String path : new String[] {
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {
            assertException("/test-documents/"+path, parser, context, AccessPermissionException.class);
        }

        config.setAccessChecker(new AccessChecker(true));
        assertException("/test-documents/" + "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                parser, context, AccessPermissionException.class);

        InputStream is = null;
        try {
            is = getResourceAsStream("/test-documents/"+ "testPDF_no_extract_yes_accessibility_owner_empty.pdf");
            assertContains("Hello World", getText(is, parser, context));
        } finally {
            IOUtils.closeQuietly(is);
        }
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
        for (String path : new String[] {
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {
            assertException("/test-documents/"+path, parser, context, EncryptedDocumentException.class);
        }

        //bad password is still a bad password
        config.setAccessChecker(new AccessChecker(true));
        for (String path : new String[] {
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {
            assertException("/test-documents/"+path, parser, context, EncryptedDocumentException.class);
        }

        //now test documents that require this "user" password
        assertException("/test-documents/"+"testPDF_no_extract_no_accessibility_owner_user.pdf",
                parser, context, AccessPermissionException.class);


        InputStream is = null;
        try {
            is = getResourceAsStream("/test-documents/"+ "testPDF_no_extract_yes_accessibility_owner_user.pdf");
            assertContains("Hello World", getText(is, parser, context));
        } finally {
            IOUtils.closeQuietly(is);
        }

        config.setAccessChecker(new AccessChecker(false));
        for (String path : new String[] {
                "testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",
        }) {
            assertException("/test-documents/"+path, parser, context, AccessPermissionException.class);
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

        Parser parser = new AutoDetectParser();
        //with owner's password, text can be extracted, no matter the AccessibilityChecker's settings
        for (String path : new String[] {
                "testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {

            InputStream is = null;
            try {
                is = getResourceAsStream("/test-documents/" + "testPDF_no_extract_yes_accessibility_owner_user.pdf");
                assertContains("Hello World", getText(is, parser, context));
            } finally {
                IOUtils.closeQuietly(is);
            }
        }

        //really, with owner's password, all extraction is allowed
        config.setAccessChecker(new AccessChecker(false));
        for (String path : new String[] {
                "testPDF_no_extract_no_accessibility_owner_user.pdf",
                "testPDF_no_extract_yes_accessibility_owner_user.pdf",
                "testPDF_no_extract_no_accessibility_owner_empty.pdf",
                "testPDF_no_extract_yes_accessibility_owner_empty.pdf",
        }) {

            InputStream is = null;
            try {
                is = getResourceAsStream("/test-documents/" + "testPDF_no_extract_yes_accessibility_owner_user.pdf");
                assertContains("Hello World", getText(is, parser, context));
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }

    private void assertException(String path, Parser parser, ParseContext context, Class expected) {
        boolean noEx = false;
        InputStream is = getResourceAsStream(path);
        try {
            String text = getText(is, parser, context);
            noEx = true;
        } catch (Exception e) {
            assertEquals("Not the right exception: "+path, expected, e.getClass());
        } finally {
            IOUtils.closeQuietly(is);
        }
        assertFalse(path + " should have thrown exception", noEx);
    }
    /**
     * 
     * Simple class to count end of document events.  If functionality is useful,
     * move to org.apache.tika in src/test
     *
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
            if (v != null && v.equals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString())){
                return false;
            }
            return true;
        }
    }
}
