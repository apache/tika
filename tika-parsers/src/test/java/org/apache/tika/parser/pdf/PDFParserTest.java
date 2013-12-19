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
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.microsoft.AbstractPOIContainerExtractionTest.TrackingHandler;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
/**
 * Test case for parsing pdf files.
 */
public class PDFParserTest extends TikaTest {

    public static final MediaType TYPE_EMF = MediaType.application("x-emf");
    public static final MediaType TYPE_PDF = MediaType.application("pdf");
    public static final MediaType TYPE_DOCX = MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document");
    

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

        assertTrue(content.contains("Apache Tika"));
        assertTrue(content.contains("Tika - Content Analysis Toolkit"));
        assertTrue(content.contains("incubator"));
        assertTrue(content.contains("Apache Software Foundation"));
        // testing how the end of one paragraph is separated from start of the next one
        assertTrue("should have word boundary after headline", 
                !content.contains("ToolkitApache"));
        assertTrue("should have word boundary between paragraphs", 
                !content.contains("libraries.Apache"));
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
        
        assertTrue(content.contains("Hello World!"));
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

       assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("The Bank of England", metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("The Bank of England", metadata.get(Metadata.AUTHOR));
       assertEquals("Speeches by Andrew G Haldane", metadata.get(OfficeOpenXMLCore.SUBJECT));
       assertEquals("Speeches by Andrew G Haldane", metadata.get(Metadata.SUBJECT));
       assertEquals("Rethinking the Financial Network, Speech by Andrew G Haldane, Executive Director, Financial Stability delivered at the Financial Student Association, Amsterdam on 28 April 2009", metadata.get(TikaCoreProperties.TITLE));

       String content = handler.toString();
       assertTrue(content.contains("RETHINKING THE FINANCIAL NETWORK"));
       assertTrue(content.contains("On 16 November 2002"));
       assertTrue(content.contains("In many important respects"));
       
       
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

       assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("The Bank of England", metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("Speeches by Andrew G Haldane", metadata.get(OfficeOpenXMLCore.SUBJECT));
       assertEquals("Speeches by Andrew G Haldane", metadata.get(Metadata.SUBJECT));
       assertEquals("Rethinking the Financial Network, Speech by Andrew G Haldane, Executive Director, Financial Stability delivered at the Financial Student Association, Amsterdam on 28 April 2009", metadata.get(TikaCoreProperties.TITLE));

       assertTrue(content.contains("RETHINKING THE FINANCIAL NETWORK"));
       assertTrue(content.contains("On 16 November 2002"));
       assertTrue(content.contains("In many important respects"));
    }

    @Test
    public void testTwoTextBoxes() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDFTwoTextBoxes.pdf");
        String content = getText(stream, parser);
        content = content.replaceAll("\\s+"," ");
        assertTrue(content.contains("Left column line 1 Left column line 2 Right column line 1 Right column line 2"));
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
       try{
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
       
       //plagiarized from POIContainerExtractionTest.  Thank you!
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
       assertEquals("My first attachment", tracker.filenames.get(2));
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
        Parser defaultParser = new AutoDetectParser();
        Parser sequentialParser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setUseNonSequentialParser(true);
        context.set(PDFParserConfig.class, config);

        File testDocs = new File(this.getClass().getResource("/test-documents").toURI());
        int pdfs = 0;
        Set<String> knownMetadataDiffs = new HashSet<String>();
        //PDFBox-1792/Tika-1203
        knownMetadataDiffs.add("testAnnotations.pdf");
        //PDFBox-1806
        knownMetadataDiffs.add("test_acroForm2.pdf");

        //empty for now
        Set<String> knownContentDiffs = new HashSet<String>();

        for (File f : testDocs.listFiles()){
            if (! f.getName().toLowerCase().endsWith(".pdf")){
                continue;
            }

            pdfs++;
            Metadata defaultMetadata = new Metadata();
            String defaultContent = getText(new FileInputStream(f), defaultParser, defaultMetadata);

            Metadata sequentialMetadata = new Metadata();
            String sequentialContent = getText(new FileInputStream(f), sequentialParser, context, sequentialMetadata);

            if (knownContentDiffs.contains(f.getName())){
                assertFalse(f.getName(), defaultContent.equals(sequentialContent));
            } else {
                assertEquals(f.getName(), defaultContent, sequentialContent);
            }

            //skip this one file.
            if (knownMetadataDiffs.contains(f.getName())){
                assertFalse(f.getName(), defaultMetadata.equals(sequentialMetadata));
            } else {
                assertEquals(f.getName(), defaultMetadata, sequentialMetadata);
            }
        }
        //make sure nothing went wrong with getting the resource to test-documents
        //This will require modification with each new pdf test.
        //If this is too annoying, we can turn it off.
        assertEquals("Number of pdf files tested", 14, pdfs);
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
}
