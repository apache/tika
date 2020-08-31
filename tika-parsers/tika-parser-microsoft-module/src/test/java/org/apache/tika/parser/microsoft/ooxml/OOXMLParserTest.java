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
package org.apache.tika.parser.microsoft.ooxml;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.util.LocaleUtil;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.microsoft.ExcelParserTest;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.WordParserTest;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class OOXMLParserTest extends TikaTest {

    private static Locale USER_LOCALE = null;

    @BeforeClass
    public static void setUp() {
        USER_LOCALE = LocaleUtil.getUserLocale();
        LocaleUtil.setUserLocale(Locale.US);
    }

    @AfterClass
    public static void tearDown() {
        LocaleUtil.setUserLocale(USER_LOCALE);
    }


    @Test
    public void testExcel() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);

        String content = getText("testEXCEL.xlsx", metadata, context);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Simple Excel document", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));

        assertContains("Sample Excel Worksheet", content);
        assertContains("Numbers and their Squares", content);
        assertContains("9", content);
        assertNotContained("9.0", content);
        assertContains("196", content);
        assertNotContained("196.0", content);
        assertEquals("false", metadata.get(TikaCoreProperties.PROTECTED));

    }

    @Test
    public void testExcelFormats() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);

        String content = getText("testEXCEL-formats.xlsx", metadata, context);
        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                metadata.get(Metadata.CONTENT_TYPE));

        // Number #,##0.00
        assertContains("1,599.99", content);
        assertContains("-1,599.99", content);

        // Currency $#,##0.00;[Red]($#,##0.00)
        assertContains("$1,599.99", content);
        assertContains("$1,599.99)", content);

        // Scientific 0.00E+00
        // poi <=3.8beta1 returns 1.98E08, newer versions return 1.98+E08
        assertTrue(content.contains("1.98E08") || content.contains("1.98E+08"));
        assertTrue(content.contains("-1.98E08") || content.contains("-1.98E+08"));

        // Percentage
        assertContains("2.50%", content);
        // Excel rounds up to 3%, but that requires Java 1.6 or later
        if (System.getProperty("java.version").startsWith("1.5")) {
            assertContains("2%", content);
        } else {
            assertContains("3%", content);
        }

        // Time Format: h:mm
        assertContains("6:15", content);
        assertContains("18:15", content);

        // Date Format: d-mmm-yy
        assertContains("17-May-07", content);

        // Currency $#,##0.00;[Red]($#,##0.00)
        assertContains("$1,599.99", content);
        assertContains("($1,599.99)", content);

        // Fraction (2.5): # ?/?
        assertContains("2 1/2", content);

        // Below assertions represent outstanding formatting issues to be addressed
        // they are included to allow the issues to be progressed with the Apache POI
        // team - See TIKA-103.

        /*************************************************************************
         // Date Format: m/d/yy
         assertContains("03/10/2009", content);

         // Date/Time Format
         assertContains("19/01/2008 04:35", content);

         // Custom Number (0 "dollars and" .00 "cents")
         assertContains("19 dollars and .99 cents", content);

         // Custom Number ("At" h:mm AM/PM "on" dddd mmmm d"," yyyy)
         assertContains("At 4:20 AM on Thursday May 17, 2007", content);
         **************************************************************************/

    }

    @Test
    @Ignore("OOXML-Strict not currently supported by POI, see #57699")
    public void testExcelStrict() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);

        String content = getText("testEXCEL.strict.xlsx", metadata, context);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Sample Spreadsheet", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Nick Burch", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Spreadsheet for testing", metadata.get(TikaCoreProperties.DESCRIPTION));
        assertContains("Test spreadsheet", content);
        assertContains("This one is red", content);
        assertContains("cb=10", content);
        assertNotContained("10.0", content);
        assertContains("cb=sum", content);
        assertNotContained("13.0", content);
        assertEquals("false", metadata.get(TikaCoreProperties.PROTECTED));

    }

    /**
     * We have a number of different powerpoint files,
     * such as presentation, macro-enabled etc
     */
    @Test
    public void testPowerPoint() throws Exception {
        String[] extensions = new String[]{
                "pptx", "pptm", "ppsm", "ppsx", "potm"
                //"thmx", // TIKA-418: Will be supported in POI 3.7 beta 2
                //"xps" // TIKA-418: Not yet supported by POI
        };

        String[] mimeTypes = new String[]{
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-powerpoint.template.macroenabled.12"
        };

        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            String filename = "testPPT." + extension;

            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            String content = getText(filename, metadata, context);

            assertEquals(
                    "Mime-type checking for " + filename,
                    mimeTypes[i],
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Attachment Test", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Rajiv", metadata.get(TikaCoreProperties.CREATOR));

            // Theme files don't have the text in them
            if (extension.equals("thmx")) {
                assertEquals("", content);
            } else {
                assertTrue(
                        "Text missing for " + filename + "\n" + content,
                        content.contains("Attachment Test")
                );
                assertTrue(
                        "Text missing for " + filename + "\n" + content,
                        content.contains("This is a test file data with the same content")
                );
                assertTrue(
                        "Text missing for " + filename + "\n" + content,
                        content.contains("content parsing")
                );
                assertTrue(
                        "Text missing for " + filename + "\n" + content,
                        content.contains("Different words to test against")
                );
                assertTrue(
                        "Text missing for " + filename + "\n" + content,
                        content.contains("Mystery")
                );
            }
        }

    }

    /**
     * Test that the metadata is already extracted when the body is processed.
     * See TIKA-1109
     */
    @Test
    public void testPowerPointMetadataEarly() throws Exception {
        String[] extensions = new String[]{
                "pptx", "pptm", "ppsm", "ppsx", "potm"
                //"thmx", // TIKA-418: Will be supported in POI 3.7 beta 2
                //"xps" // TIKA-418: Not yet supported by POI
        };

        final String[] mimeTypes = new String[]{
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-powerpoint.template.macroenabled.12"
        };

        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            final String filename = "testPPT." + extension;
            final Metadata metadata = new Metadata();

            // Allow the value to be access from the inner class
            final int currentI = i;
            ContentHandler handler = new BodyContentHandler() {
                public void startDocument() {
                    assertEquals(
                            "Mime-type checking for " + filename,
                            mimeTypes[currentI],
                            metadata.get(Metadata.CONTENT_TYPE));
                    assertEquals("Attachment Test", metadata.get(TikaCoreProperties.TITLE));
                    assertEquals("Rajiv", metadata.get(TikaCoreProperties.CREATOR));

                }

            };
            ParseContext context = new ParseContext();

            try (InputStream input = getResourceAsStream("/test-documents/"+filename)) {
                AUTO_DETECT_PARSER.parse(input, handler, metadata, context);
            }
        }
    }

    /**
     * For the PowerPoint formats we don't currently support, ensure that
     * we don't break either
     */
    @Test
    public void testUnsupportedPowerPoint() throws Exception {
        String[] extensions = new String[]{"xps", "thmx"};
        String[] mimeTypes = new String[]{
                "application/vnd.ms-xpsdocument",
                "application/vnd.openxmlformats-officedocument" // Is this right?
        };

        for (int i = 0; i < extensions.length; i++) {
            String extension = extensions[i];
            String filename = "testPPT." + extension;

            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            getXML(filename, metadata);
                // Should get the metadata
                assertEquals(
                        "Mime-type checking for " + filename,
                        mimeTypes[i],
                        metadata.get(Metadata.CONTENT_TYPE));


        }
    }

    /**
     * Test the plain text output of the Word converter
     *
     * @throws Exception
     */
    @Test
    public void testWord() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testWORD.docx", metadata, new ParseContext());
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
        assertTrue(content.contains("Sample Word Document"));

    }

    /**
     * Test the plain text output of the Word converter
     *
     * @throws Exception
     */
    @Test
    public void testWordFootnote() throws Exception {
        XMLResult xmlResult = getXML("footnotes.docx");
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertTrue(xmlResult.xml.contains("snoska"));
    }

    /**
     * Test that the word converter is able to generate the
     * correct HTML for the document
     */
    @Test
    public void testWordHTML() throws Exception {
        XMLResult result = getXML("testWORD.docx");
        String xml = result.xml;
        Metadata metadata = result.metadata;
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
        assertTrue(xml.contains("Sample Word Document"));

        // Check that custom headings came through
        assertTrue(xml.contains("<h1 class=\"title\">"));
        // Regular headings
        assertTrue(xml.contains("<h1>Heading Level 1</h1>"));
        assertTrue(xml.contains("<h2>Heading Level 2</h2>"));
        // Headings with anchor tags in them
        assertTrue(xml.contains("<h3><a name=\"OnLevel3\" />Heading Level 3</h3>"));
        // Bold and italic
        assertTrue(xml.contains("<b>BOLD</b>"));
        assertTrue(xml.contains("<i>ITALIC</i>"));
        // Table
        assertTrue(xml.contains("<table>"));
        assertTrue(xml.contains("<td>"));
        // Links
        assertTrue(xml.contains("<a href=\"http://tika.apache.org/\">Tika</a>"));
        // Anchor links
        assertContains("<a href=\"#OnMainHeading\">The Main Heading Bookmark</a>", xml);
        // Paragraphs with other styles
        assertTrue(xml.contains("<p class=\"signature\">This one"));

        result = getXML("testWORD_3imgs.docx");
        xml = result.xml;

        // Images 2-4 (there is no 1!)
        assertTrue("Image not found in:\n" + xml, xml.contains("<img src=\"embedded:image2.png\" alt=\"A description...\" />"));
        assertTrue("Image not found in:\n" + xml, xml.contains("<img src=\"embedded:image3.jpeg\" alt=\"A description...\" />"));
        assertTrue("Image not found in:\n" + xml, xml.contains("<img src=\"embedded:image4.png\" alt=\"A description...\" />"));

        // Text too
        assertTrue(xml.contains("<p>The end!</p>"));

        // TIKA-692: test document containing multiple
        // character runs within a bold tag:
        xml = getXML("testWORD_bold_character_runs.docx").xml;

        // Make sure bold text arrived as single
        // contiguous string even though Word parser
        // handled this as 3 character runs
        assertTrue("Bold text wasn't contiguous: " + xml, xml.contains("F<b>oob</b>a<b>r</b>"));

        // TIKA-692: test document containing multiple
        // character runs within a bold tag:
        xml = getXML("testWORD_bold_character_runs2.docx").xml;

        // Make sure bold text arrived as single
        // contiguous string even though Word parser
        // handled this as 3 character runs
        assertTrue("Bold text wasn't contiguous: " + xml, xml.contains("F<b>oob</b>a<b>r</b>"));
    }

    /**
     * Test that we can extract image from docx header
     */
    @Test
    public void testWordPicturesInHeader() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("headerPic.docx");
        assertEquals(2, metadataList.size());
        Metadata m = metadataList.get(0);
        String mainContent = m.get(RecursiveParserWrapper.TIKA_CONTENT);
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                m.get(Metadata.CONTENT_TYPE));
        // Check that custom headings came through
        assertTrue(mainContent.contains("<img"));
    }

    @Test
    @Ignore("need to add links in xhtml")
    public void testPicturesInVariousPlaces() throws Exception {
        //test that images are actually extracted from
        //headers, footers, comments, endnotes, footnotes
        List<Metadata> metadataList = getRecursiveMetadata("testWORD_embedded_pics.docx");

        //only process embedded resources once
        assertEquals(3, metadataList.size());
        String content = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        for (int i = 1; i < 4; i++) {
            assertContains("header"+i+"_pic", content);
            assertContains("footer"+i+"_pic", content);
        }
        assertContains("body_pic.jpg", content);
        assertContains("sdt_pic.jpg", content);
        assertContains("deeply_embedded_pic", content);
        assertContains("deleted_pic", content);//TODO: don't extract this
        assertContains("footnotes_pic", content);
        assertContains("comments_pic", content);
        assertContains("endnotes_pic", content);
//        assertContains("sdt2_pic.jpg", content);//name of file is not stored in image-sdt

        assertContainsCount("<img src=", content, 14);
    }
    /**
     * Documents with some sheets are protected, but not all.
     * See TIKA-364.
     */
    @Test
    public void testProtectedExcelSheets() throws Exception {

        Metadata metadata = getXML("protectedSheets.xlsx").metadata;

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                metadata.get(Metadata.CONTENT_TYPE));

        assertEquals("true", metadata.get(TikaCoreProperties.PROTECTED));

    }

    /**
     * An excel document which is password protected.
     * See TIKA-437.
     */
    @Test
    public void testProtectedExcelFile() throws Exception {
        XMLResult xmlResult = getXML("protectedFile.xlsx");

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));

        assertEquals("true", xmlResult.metadata.get(TikaCoreProperties.PROTECTED));

        assertContains("Office", xmlResult.xml);
    }

    /**
     * Test docx without headers
     * TIKA-633
     */
    @Test
    public void testNullHeaders() throws Exception {

        assertEquals("Should have found some text", false,
                getXML("NullHeader.docx").xml.isEmpty());
    }

    @Test
    public void testTextDecoration() throws Exception {
        String xml = getXML("testWORD_various.docx").xml;

        assertContains("<b>Bold</b>", xml);
        assertContains("<i>italic</i>", xml);
        assertContains("<u>underline</u>", xml);
        assertContains("<s>strikethrough</s>", xml);
    }

    @Test
    public void testTextDecorationNested() throws Exception {
        String xml = getXML("testWORD_various.docx").xml;

        assertContains("<i>ita<s>li</s>c</i>", xml);
        assertContains("<i>ita<s>l<u>i</u></s>c</i>", xml);
        assertContains("<i><u>unde<s>r</s>line</u></i>", xml);

        //confirm that spaces aren't added for </s> and </u>
        String txt = getText("testWORD_various.docx");
        assertContainsCount("italic", txt, 3);
        assertNotContained("ita ", txt);

        assertContainsCount("underline", txt, 2);
        assertNotContained("unde ", txt);
    }

    @Test
    public void testVarious() throws Exception {
        Metadata metadata = new Metadata();

        String content = getText("testWORD_various.docx", metadata);
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
        assertContains("Row 1 Col 1 Row 1 Col 2 Row 1 Col 3 Row 2 Col 1 Row 2 Col 2 Row 2 Col 3", content.replaceAll("\\s+", " "));
        assertContains("Row 1 column 1 Row 2 column 1 Row 1 column 2 Row 2 column 2", content.replaceAll("\\s+", " "));
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
            //assertContains(row + ") Number bullet " + row, content);
            // TODO: OOXMLExtractor fails to number the bullets:
            assertContains("Number bullet " + row, content);
        }

        for (int row = 1; row <= 2; row++) {
            for (int col = 1; col <= 3; col++) {
                assertContains("Row " + row + " Col " + col, content);
            }
        }

        assertContains("Keyword1 Keyword2", content);
        assertEquals("Keyword1 Keyword2",
                metadata.get(Office.KEYWORDS));
        assertContains("Keyword1 Keyword2",
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));


        assertContains("Subject is here", content);
        assertContains("Subject is here",
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));
        assertEquals("Subject is here",
                metadata.get(OfficeOpenXMLCore.SUBJECT));


        assertContains("Suddenly some Japanese text:", content);
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", content);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f", content);

        assertContains("And then some Gothic text:", content);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A", content);
    }

    @Test
    public void testDOCXHeaderFooterNotExtraction() throws Exception {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeHeadersAndFooters(false);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testWORD_various.docx", parseContext).xml;
        assertNotContained("This is the header text.", xml);
        assertNotContained("This is the footer text.", xml);
    }

    @Test
    public void testVariousPPTX() throws Exception {
        Metadata metadata = new Metadata();
        String xml = getXML("testPPT_various.pptx", metadata).xml;
        assertContains("<p>Footnote appears here", xml);
        assertContains("<p>[1] This is a footnote.", xml);
        assertContains("<p>This is the header text.</p>", xml);
        assertContains("<p>This is the footer text.</p>", xml);
        assertContains("<p>Here is a text box</p>", xml);
        assertContains("<p>Bold", xml);
        assertContains("italic underline superscript subscript", xml);
        assertContains("<p>Here is a citation:", xml);
        assertContains("Figure 1 This is a caption for Figure 1", xml);
        assertContains("(Kramer)", xml);
        assertContains("<table><tr>\t<td>Row 1 Col 1</td>", xml);
        assertContains("<td>Row 2 Col 2</td>\t<td>Row 2 Col 3</td></tr>", xml);
        assertContains("<p>Row 1 column 1</p>", xml);
        assertContains("<p>Row 2 column 2</p>", xml);
        assertContains("<p><a href=\"http://tika.apache.org/\">This is a hyperlink</a>", xml);
        assertContains("<p>Here is a list:", xml);
        for (int row = 1; row <= 3; row++) {
            //assertContains("·\tBullet " + row, content);
            //assertContains("\u00b7\tBullet " + row, content);
            assertContains("<p>Bullet " + row, xml);
        }
        assertContains("Here is a numbered list:", xml);
        for (int row = 1; row <= 3; row++) {
            //assertContains(row + ")\tNumber bullet " + row, content);
            //assertContains(row + ") Number bullet " + row, content);
            // TODO: OOXMLExtractor fails to number the bullets:
            assertContains("<p>Number bullet " + row, xml);
        }

        for (int row = 1; row <= 2; row++) {
            for (int col = 1; col <= 3; col++) {
                assertContains("Row " + row + " Col " + col, xml);
            }
        }

        assertContains("Keyword1 Keyword2", xml);
        assertEquals("Keyword1 Keyword2",
                metadata.get(Office.KEYWORDS));

        assertContains("Subject is here", xml);
        assertEquals("Subject is here",
                metadata.get(OfficeOpenXMLCore.SUBJECT));

        assertContains("Keyword1 Keyword2",
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));
        assertContains("Subject is here",
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));


        assertContains("Suddenly some Japanese text:", xml);
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", xml);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f", xml);

        assertContains("And then some Gothic text:", xml);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A", xml);
    }

    @Test
    public void testSkipHeaderFooter() throws Exception {
        //now test turning off header/footer
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeHeadersAndFooters(false);
        ParseContext context = new ParseContext();
        context.set(OfficeParserConfig.class, config);
        String xml = getXML("testPPT_various.pptx", context).xml;
        assertNotContained("This is the header text", xml);

    }

    @Test
    public void testCommentPPTX() throws Exception {
        XMLResult r = getXML("testPPT_comment.pptx");
        assertContains("<p class=\"slide-comment\"><b>Allison, Timothy B. (ATB)", r.xml);
    }

    @Test
    public void testMasterFooter() throws Exception {
        String content = getText("testPPT_masterFooter.pptx");
        assertContains("Master footer is here", content);
    }

    @Test
    @Ignore("can't tell why this isn't working")
    public void testTurningOffMasterContent() throws Exception {
        //now test turning off master content

        //the underlying xml has "Master footer" in
        //the actual slide's xml, not just in the master slide.
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeSlideMasterContent(false);
        ParseContext context = new ParseContext();
        context.set(OfficeParserConfig.class, config);
        String xml = getXML("testPPT_masterFooter.pptx", context).xml;
        assertNotContained("Master footer", xml);
    }

    /**
     * TIKA-712 Master Slide Text from PPT and PPTX files
     * should be extracted too
     */
    @Test
    public void testMasterText() throws Exception {

        String content = getText("testPPT_masterText.pptx");
        assertContains("Text that I added to the master slide", content);

        //now test turning off master content
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeSlideMasterContent(false);
        ParseContext context = new ParseContext();
        context.set(OfficeParserConfig.class, config);
        content = getXML("testPPT_masterText.pptx", context).xml;
        assertNotContained("Text that I added", content);
    }

    @Test
    public void testMasterText2() throws Exception {
        String content = getText("testPPT_masterText2.pptx");
        assertContains("Text that I added to the master slide", content);

        //now test turning off master content
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeSlideMasterContent(false);
        ParseContext context = new ParseContext();
        context.set(OfficeParserConfig.class, config);
        content = getXML("testPPT_masterText2.pptx", context).xml;
        assertNotContained("Text that I added", content);
    }

    @Test
    public void testWordArt() throws Exception {
        assertContains("Here is some red word Art",
                getText("testWordArt.pptx"));
    }

    /**
     * Ensures that custom OOXML properties are extracted
     */
    @Test
    public void testExcelCustomProperties() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);
        getXML("testEXCEL_custom_props.xlsx", metadata, context);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(null, metadata.get(TikaCoreProperties.CREATOR));
        assertEquals(null, metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("2006-09-12T15:06:44Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2011-08-22T14:24:38Z", metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("Microsoft Excel", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertEquals("true", metadata.get("custom:myCustomBoolean"));
        assertEquals("3", metadata.get("custom:myCustomNumber"));
        assertEquals("MyStringValue", metadata.get("custom:MyCustomString"));
        assertEquals("2010-12-30T22:00:00Z", metadata.get("custom:MyCustomDate"));
        assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }

    @Test
    public void testWordCustomProperties() throws Exception {
        Metadata metadata = new Metadata();

        try (InputStream input = OOXMLParserTest.class.getResourceAsStream(
                "/test-documents/testWORD_custom_props.docx")) {
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OOXMLParser().parse(input, handler, metadata, context);
        }

        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("EJ04325S", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Etienne Jouvin", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("2011-07-29T16:52:00Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2012-01-03T22:14:00Z", metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("Microsoft Office Word", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertEquals("1", metadata.get(Office.PAGE_COUNT));
        assertEquals("2", metadata.get(Office.WORD_COUNT));
        assertEquals("My Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("My Keyword", metadata.get(Office.KEYWORDS));
        assertContains("My Keyword",
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));

        assertEquals("Normal.dotm", metadata.get(OfficeOpenXMLExtended.TEMPLATE));
        assertEquals("My subject", metadata.get(OfficeOpenXMLCore.SUBJECT));
        assertEquals("EDF-DIT", metadata.get(TikaCoreProperties.PUBLISHER));
        assertEquals("true", metadata.get("custom:myCustomBoolean"));
        assertEquals("3", metadata.get("custom:myCustomNumber"));
        assertEquals("MyStringValue", metadata.get("custom:MyCustomString"));
        assertEquals("2010-12-30T23:00:00Z", metadata.get("custom:MyCustomDate"));
        assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }

    @Test
    public void testPowerPointCustomProperties() throws Exception {
        Metadata metadata = new Metadata();

        try (InputStream input = OOXMLParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_custom_props.pptx")) {
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OOXMLParser().parse(input, handler, metadata, context);
        }

        assertEquals(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("JOUVIN ETIENNE", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("EJ04325S", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("2011-08-22T13:30:53Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2011-08-22T13:32:49Z", metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("1", metadata.get(Office.SLIDE_COUNT));
        assertEquals("3", metadata.get(Office.WORD_COUNT));
        assertEquals("Test extraction properties pptx", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("true", metadata.get("custom:myCustomBoolean"));
        assertEquals("3", metadata.get("custom:myCustomNumber"));
        assertEquals("MyStringValue", metadata.get("custom:MyCustomString"));
        assertEquals("2010-12-30T22:00:00Z", metadata.get("custom:MyCustomDate"));
        assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }

    // TIKA-989:
    @Test
    public void testEmbeddedPDF() throws Exception {
        Metadata metadata = new Metadata();
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
        handler.setResult(new StreamResult(sw));

        try (InputStream input = OOXMLParserTest.class.getResourceAsStream(
                "/test-documents/testWORD_embedded_pdf.docx")) {
            new OOXMLParser().parse(input, handler, metadata, new ParseContext());
        }
        String xml = sw.toString();
        int i = xml.indexOf("Here is the pdf file:");
        int j = xml.indexOf("<div class=\"embedded\" id=\"rId5\"/>");
        int k = xml.indexOf("Bye Bye");
        int l = xml.indexOf("<div class=\"embedded\" id=\"rId6\"/>");
        int m = xml.indexOf("Bye for real.");
        assertTrue(i != -1);
        assertTrue(j != -1);
        assertTrue(k != -1);
        assertTrue(l != -1);
        assertTrue(m != -1);
        assertTrue(i < j);
        assertTrue(j < k);
        assertTrue(k < l);
        assertTrue(l < m);
    }

    // TIKA-997:
    @Test
    public void testEmbeddedZipInPPTX() throws Exception {
        String xml = getXML("test_embedded_zip.pptx").xml;
        int h = xml.indexOf("<div class=\"embedded\" id=\"slide1_rId3\" />");
        int i = xml.indexOf("Send me a note");
        int j = xml.indexOf("<div class=\"embedded\" id=\"slide2_rId4\" />");
        int k = xml.indexOf("<p>No title</p>");
        assertTrue(h != -1);
        assertTrue(i != -1);
        assertTrue(j != -1);
        assertTrue(k != -1);
        assertTrue(h < i);
        assertTrue(i < j);
        assertTrue(j < k);
    }

    // TIKA-1006
    @Test
    public void testWordNullStyle() throws Exception {
        String xml = getXML("testWORD_null_style.docx").xml;
        assertContains("Test av styrt dokument", xml);
    }

    /**
     * TIKA-1044 - Handle word documents where parts of the
     * text have no formatting or styles applied to them
     */
    @Test
    public void testNoFormat() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = WordParserTest.class.getResourceAsStream(
                "/test-documents/testWORD_no_format.docx")) {
            new OOXMLParser().parse(stream, handler, metadata, new ParseContext());
        }

        String content = handler.toString();
        assertContains("This is a piece of text that causes an exception", content);
    }

    // TIKA-1005:
    @Test
    public void testTextInsideTextBox() throws Exception {
        String xml = getXML("testWORD_text_box.docx").xml;
        assertContains("This text is directly in the body of the document.", xml);
        assertContains("This text is inside of a text box in the body of the document.", xml);
        assertContains("This text is inside of a text box in the header of the document.", xml);
        assertContains("This text is inside of a text box in the footer of the document.", xml);
    }

    //TIKA-2807
    @Test
    public void testSDTInTextBox() throws Exception {
        String xml = getXML("testWORD_sdtInTextBox.docx").xml;
        assertContains("rich-text-content-control_inside-text-box", xml);
        assertContainsCount("inside-text", xml, 1);
    }

    //TIKA-2346
    @Test
    public void testTurningOffTextBoxExtraction() throws Exception {
        ParseContext pc = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeShapeBasedContent(false);
        pc.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testWORD_text_box.docx", pc).xml;
        assertContains("This text is directly in the body of the document.", xml);
        assertNotContained("This text is inside of a text box in the body of the document.", xml);
        assertNotContained("This text is inside of a text box in the header of the document.", xml);
        assertNotContained("This text is inside of a text box in the footer of the document.", xml);
    }

    // TIKA-1032:
    @Test
    public void testEmbeddedPPTXTwoSlides() throws Exception {
        String xml = getXML("testPPT_embedded_two_slides.pptx").xml;
        assertContains("<div class=\"embedded\" id=\"slide1_rId7\" />", xml);
        assertContains("<div class=\"embedded\" id=\"slide2_rId7\" />", xml);
    }

    /**
     * Test for missing text described in
     * <a href="https://issues.apache.org/jira/browse/TIKA-1130">TIKA-1130</a>.
     * and TIKA-1317
     */
    @Test
    public void testMissingText() throws Exception {
        XMLResult xmlResult = getXML("testWORD_missing_text.docx");
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("BigCompany", xmlResult.xml);
        assertContains("Seasoned", xmlResult.xml);
        assertContains("Rich_text_in_cell", xmlResult.xml);

    }

    //TIKA-1100:
    @Test
    public void testExcelTextBox() throws Exception {
        XMLResult r = getXML("testEXCEL_textbox.xlsx");
        assertContains("some autoshape", r.xml);
    }

    //TIKA-2346
    @Test
    public void testTurningOffTextBoxExtractionExcel() throws Exception {

        ParseContext pc = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeShapeBasedContent(false);
        pc.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testEXCEL_textbox.xlsx", pc).xml;
        assertNotContained("autoshape", xml);
    }

    //TIKA-792; with room for future missing bean tests
    @Test
    public void testWordMissingOOXMLBeans() throws Exception {
        //If a bean is missing, POI prints stack trace to stderr 
        String[] fileNames = new String[]{
                "testWORD_missing_ooxml_bean1.docx",//TIKA-792
        };
        PrintStream origErr = System.err;
        for (String fileName : fileNames) {
            //grab stderr
            ByteArrayOutputStream errContent = new ByteArrayOutputStream();
            System.setErr(new PrintStream(errContent, true, UTF_8.name()));
            getXML(fileName);

            //return stderr
            System.setErr(origErr);

            String err = errContent.toString(UTF_8.name());
            assertTrue(err.length() == 0);
        }
    }

    //TIKA-817
    @Test
    public void testPPTXAutodate() throws Exception {
        //Following POI-52368, the stored date is extracted,
        //not the auto-generated date.

        XMLResult result = getXML("testPPT_autodate.pptx");
        assertContains("<p>Now</p>\n" +
                "<p>2011-12-19 10:20:04 AM</p>\n", result.xml);

    }

    @Test
    public void testDOCXThumbnail() throws Exception {
        String xml = getXML("testDOCX_Thumbnail.docx").xml;
        int a = xml.indexOf("This file contains a thumbnail");
        int b = xml.indexOf("<div class=\"embedded\" id=\"/docProps/thumbnail.emf\" />");

        assertTrue(a != -1);
        assertTrue(b != -1);
        assertTrue(a < b);
    }

    @Test
    public void testXLSXThumbnail() throws Exception {
        String xml = getXML("testXLSX_Thumbnail.xlsx").xml;
        int a = xml.indexOf("This file contains an embedded thumbnail by default");
        int b = xml.indexOf("<div class=\"embedded\" id=\"/docProps/thumbnail.wmf\" />");

        assertTrue(a != -1);
        assertTrue(b != -1);
        assertTrue(a < b);
    }

    @Test
    public void testPPTXThumbnail() throws Exception {
        String xml = getXML("testPPTX_Thumbnail.pptx").xml;
        int a = xml.indexOf("<body><div class=\"slide-content\"><p>This file contains an embedded thumbnail");
        int b = xml.indexOf("<div class=\"embedded\" id=\"/docProps/thumbnail.jpeg\" />");
        assertTrue(a != -1);
        assertTrue(b != -1);
        assertTrue(a < b);
    }

    @Test
    public void testEncrypted() throws Exception {
        Map<String, String> tests = new HashMap<String, String>();
        //the first three contain javax.crypto.CipherInputStream
        tests.put("testWORD_protected_passtika.docx",
                "This is an encrypted Word 2007 File");
        tests.put("testPPT_protected_passtika.pptx",
                "This is an encrypted PowerPoint 2007 slide.");
        tests.put("testEXCEL_protected_passtika.xlsx",
                "This is an Encrypted Excel spreadsheet.");
        //TIKA-2873 this one contains a ChunkedCipherInputStream
        //that is buggy at the POI level...can unwrap TikaInputStream in OfficeParser
        //once https://bz.apache.org/bugzilla/show_bug.cgi?id=63431 is fixed.
        tests.put("testEXCEL_protected_passtika_2.xlsx",
                "This is an Encrypted Excel spreadsheet with a ChunkedCipherInputStream.");

        PasswordProvider passwordProvider = new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "tika";
            }
        };
        ParseContext passwordContext = new ParseContext();
        passwordContext.set(org.apache.tika.parser.PasswordProvider.class, passwordProvider);

        for (Map.Entry<String, String> e : tests.entrySet()) {
            XMLResult xmlResult = getXML(e.getKey(), passwordContext);
            assertContains(e.getValue(), xmlResult.xml);
        }

        ParseContext context = new ParseContext();
        //now try with no password
        for (Map.Entry<String, String> e : tests.entrySet()) {
            boolean exc = false;
            try {
                getXML(e.getKey());
            } catch (EncryptedDocumentException ex) {
                exc = true;
            }
            assertTrue(exc);
        }
    }

    @Test
    public void testDOCXParagraphNumbering() throws Exception {
        String xml = getXML("testWORD_numbered_list.docx").xml;
        assertContains("1) This", xml);
        assertContains("a) Is", xml);
        assertContains("i) A multi", xml);
        assertContains("ii) Level", xml);
        assertContains("1. Within cell 1", xml);
        assertContains("b. Cell b", xml);
        assertContains("iii) List", xml);
        assertContains("2) foo", xml);
        assertContains("ii) baz", xml);
        assertContains("ii) foo", xml);
        assertContains("II. bar", xml);
        assertContains("6. six", xml);
        assertContains("7. seven", xml);
        assertContains("a. seven a", xml);
        assertContains("e. seven e", xml);
        assertContains("2. A ii 2", xml);
        assertContains("3. page break list 3", xml);
        assertContains("Some-1-CrazyFormat Greek numbering with crazy format - alpha", xml);
        assertContains("1.1.1. 1.1.1", xml);
        assertContains("1.1. 1.2-&gt;1.1  //set the value", xml);

        //TODO: comment is not being extracted!
        //assertContains("add a list here", xml);
    }

    @Test
    public void testDOCXOverrideParagraphNumbering() throws Exception {
        String xml = getXML("testWORD_override_list_numbering.docx").xml;

        //Test 1
        assertContains("<p>1.1.1.1...1 1.1.1.1...1</p>", xml);
        assertContains("1st.2.3someText 1st.2.3someText", xml);
        assertContains("1st.2.2someOtherText.1 1st.2.2someOtherText.1", xml);
        assertContains("5th 5th", xml);


        //Test 2
        assertContains("1.a.I 1.a.I", xml);
        //test no reset because level 2 is not sufficient to reset
        assertContains("<p>1.b.III 1.b.III</p>", xml);
        //test restarted because of level 0's increment to 2
        assertContains("2.a.I 2.a.I", xml);
        //test handling of skipped level
        assertContains("<p>2.b 2.b</p>", xml);

        //Test 3
        assertContains("(1)) (1))", xml);
        //tests start level 1 at 17 and
        assertContains("2.17 2.17", xml);
        //tests that isLegal turns everything into decimal
        assertContains("2.18.2.1 2.18.2.1", xml);
        assertContains("<p>2 2</p>", xml);

        //Test4
        assertContains("<p>1 1</p>", xml);
        assertContains("<p>A A</p>", xml);
        assertContains("<p>B B</p>", xml);
        //this tests overrides
        assertContains("<p>C C</p>", xml);
        assertContains("<p>4 4</p>", xml);

        //Test5
        assertContains(">00 00", xml);
        assertContains(">01 01", xml);
        assertContains(">01. 01.", xml);
        assertContains(">01..1 01..1", xml);
        assertContains(">02 02", xml);
    }

    @Test
    public void testExcelHeaderAndFooterExtraction() throws Exception {
        XMLResult xml = getXML("testEXCEL_headers_footers.xlsx");

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xml.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Internal spreadsheet", xml.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Aeham Abushwashi", xml.metadata.get(TikaCoreProperties.CREATOR));

        String content = xml.xml;
        assertContains("John Smith1", content);
        assertContains("John Smith50", content);
        assertContains("1 Corporate HQ", content);
        assertContains("Header - Corporate Spreadsheet", content);
        assertContains("Header - For Internal Use Only", content);
        assertContains("Header - Author: John Smith", content);
        assertContains("Footer - Corporate Spreadsheet", content);
        assertContains("Footer - For Internal Use Only", content);
        assertContains("Footer - Author: John Smith", content);
    }

    @Test
    public void testExcelHeaderAndFooterNotExtraction() throws Exception {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeHeadersAndFooters(false);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

        String content = getXML("testEXCEL_headers_footers.xlsx", parseContext).xml;
        assertNotContained("Header - Corporate Spreadsheet", content);
        assertNotContained("Header - For Internal Use Only", content);
        assertNotContained("Header - Author: John Smith", content);
        assertNotContained("Footer - Corporate Spreadsheet", content);
        assertNotContained("Footer - For Internal Use Only", content);
        assertNotContained("Footer - Author: John Smith", content);
    }


    @Test
    public void testMultiAuthorsManagers() throws Exception {
        XMLResult r = getXML("testWORD_multi_authors.docx");
        String[] authors = r.metadata.getValues(TikaCoreProperties.CREATOR);
        assertEquals(3, authors.length);
        assertEquals("author2", authors[1]);

        String[] managers = r.metadata.getValues(OfficeOpenXMLExtended.MANAGER);
        assertEquals(2, managers.length);
        assertEquals("manager1", managers[0]);
        assertEquals("manager2", managers[1]);
    }

    @Test
    public void testHyperlinksInXLSX() throws Exception {
        String xml = getXML("testEXCEL_hyperlinks.xlsx").xml;
        //external url
        assertContains("<a href=\"http://tika.apache.org/\">", xml);
        //mail url
        assertContains("<a href=\"mailto:user@tika.apache.org?subject=help\">", xml);
        //external linked file
        assertContains("<a href=\"linked_file.txt.htm\">", xml);
        //link on textbox
        assertContains("<a href=\"http://tika.apache.org/1.12/gettingstarted.html\">", xml);
    }


    @Test
    public void testOrigSourcePath() throws Exception {
        Metadata embed1_zip_metadata = getRecursiveMetadata("test_recursive_embedded.docx").get(2);
        assertContains("C:\\Users\\tallison\\AppData\\Local\\Temp\\embed1.zip",
                Arrays.asList(embed1_zip_metadata.getValues(TikaCoreProperties.ORIGINAL_RESOURCE_NAME)));
        assertContains("C:\\Users\\tallison\\Desktop\\tmp\\New folder (2)\\embed1.zip",
                Arrays.asList(embed1_zip_metadata.getValues(TikaCoreProperties.ORIGINAL_RESOURCE_NAME)));
    }

    @Test
    public void testBigIntegersWGeneralFormat() throws Exception {
        //TIKA-2025
        String xml = getXML("testEXCEL_big_numbers.xlsx").xml;
        assertContains("123456789012345", xml);//15 digit number
        assertContains("123456789012346", xml);//15 digit formula
        Locale locale = LocaleUtil.getUserLocale();

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        //16 digit number is treated as scientific notation as is the 16 digit formula
        assertContains("1"+symbols.getDecimalSeparator()+"23456789012345E+15</td>\t"+
                "<td>1"+symbols.getDecimalSeparator()+"23456789012345E+15", xml);
    }

    @Test
    public void testBigIntegersWGeneralFormatWLocaleIT() throws Exception {
        LocaleUtil.setUserLocale(Locale.ITALIAN);
        //TIKA-2438
        try {
            String xml = getXML("testEXCEL_big_numbers.xlsx").xml;
            assertContains("123456789012345", xml);//15 digit number
            assertContains("123456789012346", xml);//15 digit formula
            Locale locale = LocaleUtil.getUserLocale();

            DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
            //16 digit number is treated as scientific notation as is the 16 digit formula
            assertContains("1" + symbols.getDecimalSeparator() + "23456789012345E+15</td>\t" +
                    "<td>1" + symbols.getDecimalSeparator() + "23456789012345E+15", xml);
        } finally {
            LocaleUtil.setUserLocale(USER_LOCALE);
        }
    }


    @Test
    public void testBoldHyperlink() throws Exception {
        //TIKA-1255
        String xml = getXML("testWORD_boldHyperlink.docx").xml;
        xml = xml.replaceAll("\\s+", " ");
        assertContains("<a href=\"http://tika.apache.org/\">hyper <b>link</b></a>", xml);
        assertContains("<a href=\"http://tika.apache.org/\"><b>hyper</b> link</a>; bold", xml);
    }

    @Test
    public void testLongForIntExceptionInSummaryDetails() throws Exception {
        //TIKA-2055
        assertContains("bold", getXML("testWORD_totalTimeOutOfRange.docx").xml);
    }

    @Test
    public void testMacrosInDocm() throws Exception {

        //test default is "don't extract macros"
        for (Metadata metadata : getRecursiveMetadata("testWORD_macros.docm")) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }

        //now test that they were extracted
        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        context.set(OfficeParserConfig.class, officeParserConfig);


        Metadata minExpected = new Metadata();
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Embolden()");
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Italicize()");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        assertContainsAtLeast(minExpected, getRecursiveMetadata("testWORD_macros.docm", context));

        //test configuring via config file
        TikaConfig tikaConfig = new TikaConfig(this.getClass().getResourceAsStream("tika-config-dom-macros.xml"));
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        assertContainsAtLeast(minExpected, getRecursiveMetadata("testWORD_macros.docm", parser));

    }

    @Test
    public void testMacrosInPptm() throws Exception {

        //test default is "don't extract macros"
        for (Metadata metadata : getRecursiveMetadata("testPPT_macros.pptm")) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }

        //now test that they were extracted
        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        Metadata minExpected = new Metadata();
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Embolden()");
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Italicize()");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        assertContainsAtLeast(minExpected, getRecursiveMetadata("testPPT_macros.pptm", context));

        //test configuring via config file
        TikaConfig tikaConfig = new TikaConfig(this.getClass().getResourceAsStream("tika-config-dom-macros.xml"));
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        assertContainsAtLeast(minExpected, getRecursiveMetadata("testPPT_macros.pptm", parser));

    }

    @Test
    public void testMacroinXlsm() throws Exception {

        //test default is "don't extract macros"
        for (Metadata metadata : getRecursiveMetadata("testEXCEL_macro.xlsm")) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }

        //now test that they were extracted
        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        Metadata minExpected = new Metadata();
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Dirty()");
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "dirty dirt dirt");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        assertContainsAtLeast(minExpected,
                getRecursiveMetadata("testEXCEL_macro.xlsm", context));

        //test configuring via config file
        TikaConfig tikaConfig = new TikaConfig(this.getClass().getResourceAsStream("tika-config-dom-macros.xml"));
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        assertContainsAtLeast(minExpected, getRecursiveMetadata("testEXCEL_macro.xlsm", parser));

    }

    //@Test //use this for lightweight benchmarking to compare xwpf options
    public void testBatch() throws Exception {
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXDocxExtractor(true);
        long started = System.currentTimeMillis();
        int ex = 0;
        for (int i = 0; i < 100; i++) {
            for (File f : getResourceAsFile("/test-documents").listFiles()) {
                if (!f.getName().endsWith(".docx")) {
                    continue;
                }
                try (InputStream is = TikaInputStream.get(f)) {
                    ParseContext parseContext = new ParseContext();
                    parseContext.set(OfficeParserConfig.class, officeParserConfig);
                    //test only the extraction of the main docx content, not embedded docs
                    parseContext.set(Parser.class, new EmptyParser());
                    XMLResult r = getXML(is, AUTO_DETECT_PARSER, new Metadata(), parseContext);
                } catch (Exception e) {
                    ex++;

                }
            }
        }
        System.out.println("elapsed: "+(System.currentTimeMillis()-started) + " with " + ex + " exceptions");
    }

    @Test
    public void testInitializationViaConfig() throws Exception {
        //NOTE: this test relies on a bug in the DOM extractor that
        //is passing over the title information.
        //once we fix that, this test will no longer be meaningful!
        InputStream is = getClass().getResourceAsStream("/org/apache/tika/parser/microsoft/tika-config-sax-docx.xml");
        assertNotNull(is);
        TikaConfig tikaConfig = new TikaConfig(is);
        AutoDetectParser p = new AutoDetectParser(tikaConfig);
        XMLResult xml = getXML("testWORD_2006ml.docx", p, new Metadata());
        assertContains("engaging title", xml.xml);

    }

    @Test
    public void testExcelXLSB() throws Exception {
        Detector detector = new DefaultDetector();

        Metadata m = new Metadata();
        m.add(TikaCoreProperties.RESOURCE_NAME_KEY, "excel.xlsb");

        // Should be detected correctly
        MediaType type;
        try (InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/testEXCEL.xlsb")) {
            type = detector.detect(input, m);
            assertEquals("application/vnd.ms-excel.sheet.binary.macroenabled.12", type.toString());
        }

        // OfficeParser won't handle it
        assertEquals(false, (new OfficeParser()).getSupportedTypes(new ParseContext()).contains(type));

        // OOXMLParser will (soon) handle it
        assertTrue((new OOXMLParser()).getSupportedTypes(new ParseContext()).contains(type));

        // AutoDetectParser doesn't break on it
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);
        String content = getText("testEXCEL.xlsb", new Metadata(), context);
        assertContains("This is an example spreadsheet", content);
    }

    @Test
    public void testXLSBVarious() throws Exception {
        try {
            LocaleUtil.setUserLocale(Locale.US);
            //have to set to US because of a bug in POI for $   3.03 in Locale.ITALIAN
            OfficeParserConfig officeParserConfig = new OfficeParserConfig();
            officeParserConfig.setExtractMacros(true);
            ParseContext parseContext = new ParseContext();
            parseContext.set(OfficeParserConfig.class, officeParserConfig);
            List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_various.xlsb", parseContext);
            assertEquals(4, metadataList.size());

            String xml = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
            assertContains("<td>13</td>", xml);
            assertContains("<td>13.1211231321</td>", xml);
            assertContains("<td>$   3.03</td>", xml);
            assertContains("<td>20%</td>", xml);
            assertContains("<td>13.12</td>", xml);
            assertContains("<td>123456789012345</td>", xml);
            assertContains("<td>1.23456789012345E+15</td>", xml);
            assertContains("test comment2", xml);

            assertContains("comment4 (end of row)", xml);


            assertContains("<td>1/4</td>", xml);
            assertContains("<td>3/9/17</td>", xml);
            assertContains("<td>4</td>", xml);
            assertContains("<td>2</td>", xml);

            assertContains("<td>   46/1963</td>", xml);
            assertContains("<td>  3/128</td>", xml);
            assertContains("test textbox", xml);

            assertContains("test WordArt", xml);

            assertContains("<a href=\"http://lucene.apache.org/\">http://lucene.apache.org/</a>", xml);
            assertContains("<a href=\"http://tika.apache.org/\">http://tika.apache.org/</a>", xml);

            assertContains("OddLeftHeader OddCenterHeader OddRightHeader", xml);
            assertContains("EvenLeftHeader EvenCenterHeader EvenRightHeader", xml);

            assertContains("FirstPageLeftHeader FirstPageCenterHeader FirstPageRightHeader", xml);
            assertContains("OddLeftFooter OddCenterFooter OddRightFooter", xml);
            assertContains("EvenLeftFooter EvenCenterFooter EvenRightFooter", xml);
            assertContains("FirstPageLeftFooter FirstPageCenterFooter FirstPageRightFooter", xml);
        } finally {
            LocaleUtil.setUserLocale(USER_LOCALE);
        }
    }

    @Test
    public void testXLSBNoHeaderFooters() throws Exception {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeHeadersAndFooters(false);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testEXCEL_various.xlsb", parseContext).xml;
        assertNotContained("OddLeftHeader OddCenterHeader OddRightHeader", xml);
        assertNotContained("EvenLeftHeader EvenCenterHeader EvenRightHeader", xml);

        assertNotContained("FirstPageLeftHeader FirstPageCenterHeader FirstPageRightHeader", xml);
        assertNotContained("OddLeftFooter OddCenterFooter OddRightFooter", xml);
        assertNotContained("EvenLeftFooter EvenCenterFooter EvenRightFooter", xml);
        assertNotContained("FirstPageLeftFooter FirstPageCenterFooter FirstPageRightFooter", xml);

    }

    @Test
    public void testPOI61034() throws Exception {
        //tests temporary work around until POI 3.17-beta1 is released
        XMLResult r = getXML("testEXCEL_poi-61034.xlsx");
        Matcher m = Pattern.compile("<h1>(Sheet\\d+)</h1>").matcher(r.xml);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            String sheetName = m.group(1);
            if (seen.contains(sheetName)) {
                fail("Should only see each sheet once: "+sheetName);
            }
            seen.add(sheetName);
        }

    }

    @Test
    public void testXLSBOriginalPath() throws Exception {
        assertEquals("C:\\Users\\tallison\\Desktop\\working\\TIKA-1945\\",
                getXML("testEXCEL_diagramData.xlsb").metadata.get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));
    }

    @Test
    public void testXLSXOriginalPath() throws Exception {
        assertEquals("C:\\Users\\tallison\\Desktop\\working\\TIKA-1945\\",
                getXML("testEXCEL_diagramData.xlsx").metadata.get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));
    }

    @Test
    public void testXLSBDiagramData() throws Exception {
        assertContains("SmartArt",
                getXML("testEXCEL_diagramData.xlsb").xml);
    }

    @Test
    public void testXLSXDiagramData() throws Exception {
        assertContains("SmartArt",
                getXML("testEXCEL_diagramData.xlsx").xml);
    }

    @Test
    public void testDOCXDiagramData() throws Exception {
        assertContains("From here", getXML("testWORD_diagramData.docx").xml);
    }

    @Test
    public void testPPTXDiagramData() throws Exception {
        assertContains("President", getXML("testPPT_diagramData.pptx").xml);
    }

    @Test
    public void testXLSXChartData() throws Exception {
        String xml = getXML("testEXCEL_charts.xlsx").xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testXLSBChartData() throws Exception {
        String xml = getXML("testEXCEL_charts.xlsb").xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testDOCXChartData() throws Exception {
        String xml = getXML("testWORD_charts.docx").xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testPPTXChartData() throws Exception {
        String xml = getXML("testPPT_charts.pptx").xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testPPTXGroups() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_groups.pptx");
        assertEquals(3, metadataList.size());
        String content = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        assertContains("WordArt1", content);
        assertContains("WordArt2", content);
        assertContainsCount("Ungrouped text box", content, 1);//should only be 1
        assertContains("Text box1", content);
        assertContains("Text box2", content);
        assertContains("Text box3", content);
        assertContains("Text box4", content);
        assertContains("Text box5", content);


        assertContains("href=\"http://tika.apache.org", content);
        assertContains("smart1", content);
        assertContains("MyTitle", content);

        assertEquals("/image1.jpg",
                metadataList.get(1).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH));

        assertEquals("/thumbnail.jpeg",
                metadataList.get(2).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH));
    }

    @Test
    public void testXLSXPhoneticStrings() throws Exception {
        //This unit test and test file come from Apache POI 51519.xlsx

        //test default concatenates = true
		assertContains("\u65E5\u672C\u30AA\u30E9\u30AF\u30EB \u30CB\u30DB\u30F3",
                getXML("testEXCEL_phonetic.xlsx").xml);

        //test turning it off
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setConcatenatePhoneticRuns(false);
        ParseContext pc = new ParseContext();
        pc.set(OfficeParserConfig.class, officeParserConfig);
        assertNotContained("\u65E5\u672C\u30AA\u30E9\u30AF\u30EB \u30CB\u30DB\u30F3",
                getXML("testEXCEL_phonetic.xlsx", pc).xml);


        //test configuring via config file
        TikaConfig tikaConfig = new TikaConfig(OfficeParser.class.getResourceAsStream("tika-config-exclude-phonetic.xml"));
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        assertNotContained("\u65E5\u672C\u30AA\u30E9\u30AF\u30EB \u30CB\u30DB\u30F3",
                getXML("testEXCEL_phonetic.xlsx", parser).xml);

    }

    @Test
    public void testDOCXPhoneticStrings() throws Exception {

        assertContains("\u6771\u4EAC (\u3068\u3046\u304D\u3087\u3046)",
                getXML("testWORD_phonetic.docx").xml);

        OfficeParserConfig config = new OfficeParserConfig();
        config.setConcatenatePhoneticRuns(false);
        ParseContext parseContext = new ParseContext();
        parseContext.set(OfficeParserConfig.class, config);
        String xml = getXML("testWORD_phonetic.docx", parseContext).xml;
        assertContains("\u6771\u4EAC", xml);
        assertNotContained("\u3068", xml);
    }

    @Test
    public void testEmbeddedMedia() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_embeddedMP3.pptx");
        assertEquals(4, metadataList.size());
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals("audio/mpeg", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("image/png", metadataList.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("image/jpeg", metadataList.get(3).get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testEmbeddedXLSInOLEObject() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_oleWorkbook.pptx");
        assertEquals(4, metadataList.size());
        Metadata xlsx = metadataList.get(2);
        assertContains("<h1>Sheet1</h1>", xlsx.get(RecursiveParserWrapper.TIKA_CONTENT));
        assertContains("<td>1</td>", xlsx.get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx.get(Metadata.CONTENT_TYPE));
    }


    @Test
    public void testSigned() throws Exception {
        Metadata m = getXML("testWORD_signed.docx").metadata;
        assertEquals("true", m.get(TikaCoreProperties.HAS_SIGNATURE));

        m = getXML("testEXCEL_signed.xlsx").metadata;
        assertEquals("true", m.get(TikaCoreProperties.HAS_SIGNATURE));

        m = getXML("testPPT_signed.pptx").metadata;
        assertEquals("true", m.get(TikaCoreProperties.HAS_SIGNATURE));

    }

    @Test(expected = org.apache.tika.exception.TikaException.class)
    public void testTruncatedSAXDocx() throws Exception {
        ParseContext pc = new ParseContext();
        OfficeParserConfig c = new OfficeParserConfig();
        c.setUseSAXDocxExtractor(true);
        pc.set(OfficeParserConfig.class, c);
        getRecursiveMetadata("testWORD_truncated.docx", pc);
    }

    @Test
    public void testDateFormat() throws Exception {
        TikaConfig tikaConfig = new TikaConfig(
                this.getClass().getResourceAsStream("tika-config-custom-date-override.xml"));
        Parser p = new AutoDetectParser(tikaConfig);
        String xml = getXML("testEXCEL_dateFormats.xlsx", p).xml;
        assertContains("2018-09-20", xml);
        assertContains("1996-08-10", xml);
    }

    @Test
    public void testDocSecurity() throws Exception {
        assertEquals(OfficeOpenXMLExtended.SECURITY_PASSWORD_PROTECTED,
                getRecursiveMetadata("protectedFile.xlsx")
                .get(0).get(OfficeOpenXMLExtended.DOC_SECURITY_STRING));
        assertEquals(OfficeOpenXMLExtended.SECURITY_READ_ONLY_ENFORCED,
                getRecursiveMetadata("testWORD_docSecurity.docx")
                        .get(0).get(OfficeOpenXMLExtended.DOC_SECURITY_STRING));
    }
}


