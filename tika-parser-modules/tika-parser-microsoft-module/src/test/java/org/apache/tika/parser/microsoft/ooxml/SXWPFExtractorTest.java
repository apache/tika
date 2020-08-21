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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.ContentHandler;


public class SXWPFExtractorTest extends TikaTest {

    private ParseContext parseContext;

    @Before
    public void setUp() {
        parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXDocxExtractor(true);
        officeParserConfig.setUseSAXPptxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

    }

    @Test
    public void basicTest() throws Exception {

        List<Metadata> metadataList = getRecursiveMetadata("testWORD_2006ml.docx", parseContext);

        assertEquals(8, metadataList.size());
        Metadata m = metadataList.get(0);

        assertEquals("2016-11-29T00:58:00Z", m.get(TikaCoreProperties.CREATED));
        assertEquals("2016-11-29T17:54:00Z", m.get(TikaCoreProperties.MODIFIED));
        assertEquals("My Document Title", m.get(TikaCoreProperties.TITLE));
        assertEquals("This is the Author", m.get(TikaCoreProperties.CREATOR));
        assertEquals("3", m.get(OfficeOpenXMLCore.REVISION));
        assertEquals("Allison, Timothy B.", m.get(TikaCoreProperties.MODIFIER));
        //assertEquals("0", m.get(OfficeOpenXMLExtended.DOC_SECURITY));
        assertEquals("260", m.get(Office.WORD_COUNT));
        assertEquals("3", m.get(Office.PARAGRAPH_COUNT));
        assertEquals("1742", m.get(Office.CHARACTER_COUNT_WITH_SPACES));
        assertEquals("12", m.get(Office.LINE_COUNT));
        assertEquals("16.0000", m.get(OfficeOpenXMLExtended.APP_VERSION));


        String content = m.get(RecursiveParserWrapper.TIKA_CONTENT);

        assertContainsCount("engaging title page", content, 1);
        //need \n to differentiate from metadata values
        assertContainsCount("This is the Author\n", content, 1);
        assertContainsCount("This is an engaging title page", content, 1);

        assertContains("My Document Title", content);
        assertContains("My Document Subtitle", content);

        assertContains("<p class=\"toc_1\">\t<a href=\"#_Toc467647605\">Heading1\t3</a></p>", content);


        assertContains("2. Really basic 2.", content);

        assertContainsCount("This is a text box", content, 1);

        assertContains("<p>This is a hyperlink: <a href=\"http://tika.apache.org\">tika</a></p>", content);

        assertContains("<p>This is a link to a local file: <a href=\"file:///C:/data/test.png\">test.png</a></p>", content);

        assertContains("<p>This is          10 spaces</p>", content);

        //caption
        assertContains("<p class=\"table_of_figures\">\t<a href=\"#_Toc467647797\">Table 1: Table1 Caption\t2</a></p>", content);

        //embedded table
        //TODO: figure out how to handle embedded tables in html
        assertContains("<td>Embedded table r1c1", content);

        //shape
        assertContainsCount("<p>This is text within a shape", content, 1);

        //sdt rich text
        assertContains("<p>Rich text content control", content);

        //sdt simple text
        assertContains("<p>Simple text content control", content);

        //sdt repeating
        assertContains("Repeating content", content);

        //sdt dropdown
        //TODO: get options for dropdown
        assertContains("Drop down1", content);

        //sdt date
        assertContains("<p>11/16/2016</p>", content);

        //test that <tab/> works
        assertContains("tab\ttab", content);

        assertContainsCount("serious word art", content, 1);
        assertContainsCount("Wordartr1c1", content, 1);

        //glossary document contents
        assertContains("Click or tap to enter a date", content);

        //basic b/i tags...make sure not to overlap!
        assertContains("<p>The <i>quick</i> brown <b>fox </b>j<i>um</i><b><i>ped</i></b> over",
                content);

        assertContains("This is a comment", content);

        assertContains("This is an endnote", content);

        assertContains("this is the footnote", content);

        assertContains("First page header", content);

        assertContains("Even page header", content);

        assertContains("Odd page header", content);

        assertContains("First page footer", content);

        assertContains("Even page footer", content);

        assertContains("Odd page footer", content);

        //test default does not include deleted
        assertNotContained("frog", content);

        assertContains("Mattmann", content);

        //TODO: extract chart text
//        assertContains("This is the chart title", content);

        //TODO: add chart parsing
//        assertContains("This is the chart", content);

    }

    /**
     * Test the plain text output of the Word converter
     *
     * @throws Exception
     */
    @Test
    public void testWord() throws Exception {

        XMLResult xmlResult = getXML("testWORD.docx", parseContext);
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Sample Word Document", xmlResult.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Keith Bennett", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
        assertTrue(xmlResult.xml.contains("Sample Word Document"));

    }

    /**
     * Test the plain text output of the Word converter
     *
     * @throws Exception
     */
    @Test
    public void testWordFootnote() throws Exception {
        XMLResult xmlResult = getXML("footnotes.docx", parseContext);
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
        XMLResult result = getXML("testWORD.docx", parseContext);
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
        assertContains("<h1>Heading Level 1</h1>", xml);
        assertTrue(xml.contains("<h2>Heading Level 2</h2>"));
        // Headings with anchor tags in them
        //TODO: still not getting bookmarks
        assertTrue(xml.contains("<h3>Heading Level 3<a name=\"OnLevel3\" /></h3>"));
//        assertTrue(xml.contains("<h3>Heading Level 3</h3>"));
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

        result = getXML("testWORD_3imgs.docx", parseContext);
        xml = result.xml;

        // Images 2-4 (there is no 1!)
        assertTrue("Image not found in:\n" + xml, xml.contains("<img src=\"embedded:image2.png\" alt=\"A description...\" />"));
        assertTrue("Image not found in:\n" + xml, xml.contains("<img src=\"embedded:image3.jpeg\" alt=\"A description...\" />"));
        assertTrue("Image not found in:\n" + xml, xml.contains("<img src=\"embedded:image4.png\" alt=\"A description...\" />"));

        // Text too
        assertTrue(xml.contains("<p>The end!</p>"));
    }

    @Test
    public void testContiguousHTMLFormatting() throws Exception {
        // TIKA-692: test document containing multiple
        // character runs within a bold tag:
        String xml = getXML("testWORD_bold_character_runs.docx", parseContext).xml;

        // Make sure bold text arrived as single
        // contiguous string even though Word parser
        // handled this as 3 character runs
        assertTrue("Bold text wasn't contiguous: " + xml, xml.contains("F<b>oob</b>a<b>r</b>"));

        // TIKA-692: test document containing multiple
        // character runs within a bold tag:
        xml = getXML("testWORD_bold_character_runs2.docx", parseContext).xml;

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
        List<Metadata> metadataList = getRecursiveMetadata("headerPic.docx", parseContext);
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
    public void testPicturesInVariousPlaces() throws Exception {
        //test that images are actually extracted from
        //headers, footers, comments, endnotes, footnotes
        List<Metadata> metadataList = getRecursiveMetadata("testWORD_embedded_pics.docx", parseContext);

        //only process embedded resources once
        assertEquals(3, metadataList.size());
        String content = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        for (int i = 1; i < 4; i++) {
            assertContains("header" + i + "_pic", content);
            assertContains("footer" + i + "_pic", content);
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
     * Test docx without headers
     * TIKA-633
     */
    @Test
    public void testNullHeaders() throws Exception {
        XMLResult xmlResult = getXML("NullHeader.docx", parseContext);
        assertEquals("Should have found some text", false,
                xmlResult.xml.isEmpty());

    }

    @Test
    public void testVarious() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testWORD_various.docx", metadata, parseContext);
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

        assertContains("Subject is here", content);
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
    public void testWordCustomProperties() throws Exception {
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);
        Metadata metadata = getXML("testWORD_custom_props.docx", parseContext).metadata;

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

    // TIKA-989:
    @Test
    public void testEmbeddedPDF() throws Exception {
        String xml = getXML("testWORD_embedded_pdf.docx", parseContext).xml;
        int i = xml.indexOf("Here is the pdf file:");
        int j = xml.indexOf("<div class=\"embedded\" id=\"rId5\" />");
        int k = xml.indexOf("Bye Bye");
        int l = xml.indexOf("<div class=\"embedded\" id=\"rId6\" />");
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
        assertContains("This is a piece of text that causes an exception",
                getXML("testWORD_no_format.docx", parseContext).xml);
    }

    @Test
    public void testSkipDeleted() throws Exception {
        ParseContext pc = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeDeletedContent(true);
        officeParserConfig.setUseSAXDocxExtractor(true);
        officeParserConfig.setIncludeMoveFromContent(true);
        pc.set(OfficeParserConfig.class, officeParserConfig);

        XMLResult r = getXML("testWORD_2006ml.docx", pc);
        assertContains("frog", r.xml);
        assertContainsCount("Second paragraph", r.xml, 2);

    }

    // TIKA-1005:
    @Test
    public void testTextInsideTextBox() throws Exception {
        String xml = getXML("testWORD_text_box.docx", parseContext).xml;
        assertContains("This text is directly in the body of the document.", xml);
        assertContains("This text is inside of a text box in the body of the document.", xml);
        assertContains("This text is inside of a text box in the header of the document.", xml);
        assertContains("This text is inside of a text box in the footer of the document.", xml);
    }

    //TIKA-2346
    @Test
    public void testTurningOffTextBoxExtraction() throws Exception {
        ParseContext pc = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeShapeBasedContent(false);
        officeParserConfig.setUseSAXDocxExtractor(true);
        pc.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testWORD_text_box.docx", pc).xml;
        assertContains("This text is directly in the body of the document.", xml);
        assertNotContained("This text is inside of a text box in the body of the document.", xml);
        assertNotContained("This text is inside of a text box in the header of the document.", xml);
        assertNotContained("This text is inside of a text box in the footer of the document.", xml);
    }

    /**
     * Test for missing text described in
     * <a href="https://issues.apache.org/jira/browse/TIKA-1130">TIKA-1130</a>.
     * and TIKA-1317
     */
    @Test
    public void testMissingText() throws Exception {

        XMLResult xmlResult = getXML("testWORD_missing_text.docx", parseContext);
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("BigCompany", xmlResult.xml);
        assertContains("Seasoned", xmlResult.xml);
        assertContains("Rich_text_in_cell", xmlResult.xml);

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
            getXML(fileName, parseContext);

            //return stderr
            System.setErr(origErr);

            String err = errContent.toString(UTF_8.name());
            assertTrue(err.length() == 0);
        }
    }

    @Test
    public void testDOCXThumbnail() throws Exception {
        String xml = getXML("testDOCX_Thumbnail.docx", parseContext).xml;
        int a = xml.indexOf("This file contains a thumbnail");
        int b = xml.indexOf("<div class=\"embedded\" id=\"/docProps/thumbnail.emf\" />");
        assertTrue(a != -1);
        assertTrue(b != -1);
        assertTrue(a < b);
    }

    @Test
    public void testEncrypted() throws Exception {
        Map<String, String> tests = new HashMap<String, String>();
        tests.put("testWORD_protected_passtika.docx",
                "This is an encrypted Word 2007 File");

        Metadata m = new Metadata();
        PasswordProvider passwordProvider = new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "tika";
            }
        };

        OfficeParserConfig opc = new OfficeParserConfig();
        opc.setUseSAXDocxExtractor(true);
        ParseContext passwordContext = new ParseContext();
        passwordContext.set(org.apache.tika.parser.PasswordProvider.class, passwordProvider);
        passwordContext.set(OfficeParserConfig.class, opc);
        for (Map.Entry<String, String> e : tests.entrySet()) {
            assertContains(e.getValue(), getXML(e.getKey(), passwordContext).xml);
        }

        //now try with no password
        for (Map.Entry<String, String> e : tests.entrySet()) {
            boolean exc = false;
            try {
                getXML(e.getKey(), parseContext);
            } catch (EncryptedDocumentException ex) {
                exc = true;
            }
            assertTrue(exc);
        }

    }

    @Test
    public void testDOCXParagraphNumbering() throws Exception {
        String xml = getXML("testWORD_numbered_list.docx", parseContext).xml;
        //SAX parser is getting this.  DOM parser is not!
        assertContains("add a list here", xml);
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
    public void testMultiAuthorsManagers() throws Exception {
        XMLResult r = getXML("testWORD_multi_authors.docx", parseContext);
        String[] authors = r.metadata.getValues(TikaCoreProperties.CREATOR);
        assertEquals(3, authors.length);
        assertEquals("author2", authors[1]);

        String[] managers = r.metadata.getValues(OfficeOpenXMLExtended.MANAGER);
        assertEquals(2, managers.length);
        assertEquals("manager1", managers[0]);
        assertEquals("manager2", managers[1]);
    }

    @Test
    public void testOrigSourcePath() throws Exception {
        Metadata embed1_zip_metadata = getRecursiveMetadata("test_recursive_embedded.docx", parseContext).get(2);
        assertContains("C:\\Users\\tallison\\AppData\\Local\\Temp\\embed1.zip",
                Arrays.asList(embed1_zip_metadata.getValues(TikaCoreProperties.ORIGINAL_RESOURCE_NAME)));
        assertContains("C:\\Users\\tallison\\Desktop\\tmp\\New folder (2)\\embed1.zip",
                Arrays.asList(embed1_zip_metadata.getValues(TikaCoreProperties.ORIGINAL_RESOURCE_NAME)));
    }

    @Test
    public void testBoldHyperlink() throws Exception {
        //TIKA-1255
        String xml = getXML("testWORD_boldHyperlink.docx", parseContext).xml;
        xml = xml.replaceAll("\\s+", " ");
        assertContains("<a href=\"http://tika.apache.org/\">hyper <b>link</b></a>", xml);
        assertContains("<a href=\"http://tika.apache.org/\"><b>hyper</b> link</a>; bold", xml);
    }

    @Test
    public void testLongForIntExceptionInSummaryDetails() throws Exception {
        //TIKA-2055
        assertContains("bold", getXML("testWORD_totalTimeOutOfRange.docx", parseContext).xml);
    }

    @Test
    public void testMacrosInDocm() throws Exception {

        Metadata parsedBy = new Metadata();
        parsedBy.add("X-Parsed-By",
                "org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFEventBasedWordExtractor");

        //test default is "don't extract macros"
        List<Metadata> metadataList = getRecursiveMetadata("testWORD_macros.docm", parseContext);
        for (Metadata metadata : metadataList) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }
        assertContainsAtLeast(parsedBy, metadataList);

        //now test that they were extracted
        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        officeParserConfig.setUseSAXDocxExtractor(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        metadataList = getRecursiveMetadata("testWORD_macros.docm", context);
        //check that content came out of the .docm file
        assertContains("quick", metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertContainsAtLeast(parsedBy, metadataList);


        Metadata minExpected = new Metadata();
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Embolden()");
        minExpected.add(RecursiveParserWrapper.TIKA_CONTENT.getName(), "Sub Italicize()");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);

        //test configuring via config file
        TikaConfig tikaConfig = new TikaConfig(this.getClass().getResourceAsStream("tika-config-sax-macros.xml"));
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        metadataList = getRecursiveMetadata("testWORD_macros.docm", parser);
        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);

    }

    @Test
    public void testEmbedded() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testWORD_embeded.docx", parseContext);
        Metadata main = metadataList.get(0);
        String content = main.get(RecursiveParserWrapper.TIKA_CONTENT);
        //make sure mark up is there
        assertContains("<img src=\"embedded:image2.jpeg\" alt=\"A description...\" />",
                content);

        assertContains("<div class=\"embedded\" id=\"rId8\" />",
                content);

        assertEquals(16, metadataList.size());
    }

    @Test
    public void testDotx() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testWORD_template.docx", parseContext);
        String content = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        assertContains("Metallica", content);
        assertContains("Hetfield", content);
        assertContains("one eye open", content);
        assertContains("Getting the perfect", content);
        //from glossary document
        assertContains("table rows", content);

        metadataList = getRecursiveMetadata("testWORD_template.dotx", parseContext);
        content = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        //from glossary document
        assertContainsCount("ready to write", content, 2);
    }

    @Test
    public void testDiagramData() throws Exception {
        assertContains("From here", getXML("testWORD_diagramData.docx", parseContext).xml);
    }

    @Test
    public void testDOCXChartData() throws Exception {
        String xml = getXML("testWORD_charts.docx", parseContext).xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testHeaderFooterNotExtraction() throws Exception {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeHeadersAndFooters(false);
        officeParserConfig.setUseSAXDocxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testWORD_various.docx", parseContext).xml;
        assertNotContained("This is the header text.", xml);
        assertNotContained("This is the footer text.", xml);
    }

    @Test
    public void testDOCXPhoneticStrings() throws Exception {
        OfficeParserConfig config = new OfficeParserConfig();
        config.setUseSAXDocxExtractor(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(OfficeParserConfig.class, config);
        assertContains("\u6771\u4EAC (\u3068\u3046\u304D\u3087\u3046)",
                getXML("testWORD_phonetic.docx", parseContext).xml);


        config.setConcatenatePhoneticRuns(false);
        String xml = getXML("testWORD_phonetic.docx", parseContext).xml;
        assertContains("\u6771\u4EAC", xml);
        assertNotContained("\u3068", xml);
    }

    @Test
    public void testTextDecoration() throws Exception {
        String xml = getXML("testWORD_various.docx", parseContext).xml;

        assertContains("<b>Bold</b>", xml);
        assertContains("<i>italic</i>", xml);
        assertContains("<u>underline</u>", xml);
        assertContains("<strike>strikethrough</strike>", xml);
    }

    @Test
    public void testTextDecorationNested() throws Exception {
        String xml = getXML("testWORD_various.docx", parseContext).xml;

        assertContains("<i>ita<strike>li</strike>c</i>", xml);
        assertContains("<i>ita<strike>l<u>i</u></strike>c</i>", xml);
        assertContains("<i><u>unde</u><strike><u>r</u></strike><u>line</u></i>", xml);

        //confirm that spaces aren't added for <strike/> and <u/>
        String txt = getText("testWORD_various.docx", new Metadata(), parseContext);
        assertContainsCount("italic", txt, 3);
        assertNotContained("ita ", txt);

        assertContainsCount("underline", txt, 2);
        assertNotContained("unde ", txt);
    }

    //TIKA-2807
    @Test
    public void testSDTInTextBox() throws Exception {
        String xml = getXML("testWORD_sdtInTextBox.docx", parseContext).xml;
        assertContains("rich-text-content-control_inside-text-box", xml);
        assertContainsCount("inside-text", xml, 1);
    }

}
