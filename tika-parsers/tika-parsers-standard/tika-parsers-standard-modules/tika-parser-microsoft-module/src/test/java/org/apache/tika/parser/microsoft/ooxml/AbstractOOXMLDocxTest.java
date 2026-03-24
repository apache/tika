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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;

public abstract class AbstractOOXMLDocxTest extends TikaTest {

    abstract ParseContext getParseContext();

    protected OfficeParserConfig getOrCreateOfficeParserConfig(ParseContext parseContext) {
        OfficeParserConfig config = parseContext.get(OfficeParserConfig.class);
        if (config == null) {
            config = new OfficeParserConfig();
            parseContext.set(OfficeParserConfig.class, config);
        }
        return config;
    }

    /**
     * Test the plain text output of the Word converter
     *
     * @throws Exception
     */
    @Test
    public void testWord() throws Exception {

        XMLResult xmlResult = getXML("testWORD.docx", getParseContext());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
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
        XMLResult xmlResult = getXML("footnotes.docx", getParseContext());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertTrue(xmlResult.xml.contains("snoska"));
        //TIKA-4657 -- footnote content should be in a div with class "footnote"
        // and should not be nested inside the paragraph
        assertContains("<div class=\"footnote\">", xmlResult.xml);
        assertNotContained("<p><div class=\"footnote\">", xmlResult.xml);
    }

    @Test
    public void testEndnoteWithTable() throws Exception {
        XMLResult xmlResult = getXML("testWORD_endnote_table.docx", getParseContext());
        assertContains("Cat Property Act", xmlResult.xml);
        //TIKA-4657 -- endnote content should be in a div with class "endnote"
        assertContains("<div class=\"endnote\">", xmlResult.xml);
    }

    /**
     * Test that the word converter is able to generate the
     * correct HTML for the document
     */
    @Test
    public void testWordHTML() throws Exception {
        XMLResult result = getXML("testWORD.docx", getParseContext());
        String xml = result.xml;
        Metadata metadata = result.metadata;
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
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
        // Bookmark position relative to text differs between DOM and SAX
        assertContains("Heading Level 3", xml);
        assertContains("<a name=\"OnLevel3\" />", xml);
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

        result = getXML("testWORD_3imgs.docx", getParseContext());
        xml = result.xml;

        // Images 2-4 (there is no 1!)
        assertTrue(xml.contains("<img src=\"embedded:image2.png\" alt=\"A description...\" />"),
                "Image not found in:\n" + xml);
        assertTrue(xml.contains("<img src=\"embedded:image3.jpeg\" alt=\"A description...\" />"),
                "Image not found in:\n" + xml);
        assertTrue(xml.contains("<img src=\"embedded:image4.png\" alt=\"A description...\" />"),
                "Image not found in:\n" + xml);

        // Text too
        assertTrue(xml.contains("<p>The end!</p>"));
    }

    /**
     * Test that we can extract image from docx header
     */
    @Test
    public void testWordPicturesInHeader() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("headerPic.docx", getParseContext());
        assertEquals(2, metadataList.size());
        Metadata m = metadataList.get(0);
        String mainContent = m.get(TikaCoreProperties.TIKA_CONTENT);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                m.get(Metadata.CONTENT_TYPE));
        // Check that custom headings came through
        assertTrue(mainContent.contains("<img"));
    }

    /**
     * Test docx without headers
     * TIKA-633
     */
    @Test
    public void testNullHeaders() throws Exception {
        XMLResult xmlResult = getXML("NullHeader.docx", getParseContext());
        assertEquals(false, xmlResult.xml.isEmpty(), "Should have found some text");
    }

    /**
     * Test for missing text described in
     * <a href="https://issues.apache.org/jira/browse/TIKA-1130">TIKA-1130</a>.
     * and TIKA-1317
     */
    @Test
    public void testMissingText() throws Exception {

        XMLResult xmlResult = getXML("testWORD_missing_text.docx", getParseContext());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("BigCompany", xmlResult.xml);
        assertContains("Seasoned", xmlResult.xml);
        assertContains("Rich_text_in_cell", xmlResult.xml);

    }

    @Test
    public void testBoldHyperlink() throws Exception {
        //TIKA-1255
        String xml = getXML("testWORD_boldHyperlink.docx", getParseContext()).xml;
        xml = xml.replaceAll("\\s+", " ");
        assertContains("<a href=\"http://tika.apache.org/\">hyper <b>link</b></a>", xml);
        assertContains("<a href=\"http://tika.apache.org/\"><b>hyper</b> link</a>; bold", xml);
    }

    @Test
    public void testLongForIntExceptionInSummaryDetails() throws Exception {
        //TIKA-2055
        assertContains("bold",
                getXML("testWORD_totalTimeOutOfRange.docx", getParseContext()).xml);
    }

    @Test
    public void testMultiAuthorsManagers() throws Exception {
        XMLResult r = getXML("testWORD_multi_authors.docx", getParseContext());
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
        Metadata embed1_zip_metadata =
                getRecursiveMetadata("test_recursive_embedded.docx", getParseContext()).get(2);
        assertContains("C:\\Users\\tallison\\AppData\\Local\\Temp\\embed1.zip", Arrays.asList(
                embed1_zip_metadata.getValues(TikaCoreProperties.ORIGINAL_RESOURCE_NAME)));
        assertContains("C:\\Users\\tallison\\Desktop\\tmp\\New folder (2)\\embed1.zip",
                Arrays.asList(
                        embed1_zip_metadata.getValues(
                                TikaCoreProperties.ORIGINAL_RESOURCE_NAME)));
    }

    @Test
    public void testDOCXThumbnail() throws Exception {
        String xml = getXML("testDOCX_Thumbnail.docx", getParseContext()).xml;
        int a = xml.indexOf("This file contains a thumbnail");
        int b = xml.indexOf("<div class=\"embedded\" id=\"/docProps/thumbnail.emf\" />");
        assertTrue(a != -1);
        assertTrue(b != -1);
        assertTrue(a < b);
    }

    @Test
    public void testWordCustomProperties() throws Exception {
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);
        Metadata metadata = getXML("testWORD_custom_props.docx", getParseContext()).metadata;

        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
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
        assertEquals("My subject", metadata.get(DublinCore.SUBJECT));
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
        String xml = getXML("testWORD_embedded_pdf.docx", getParseContext()).xml;
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
        String xml = getXML("testWORD_null_style.docx", getParseContext()).xml;
        assertContains("Test av styrt dokument", xml);
    }

    /**
     * TIKA-1044 - Handle word documents where parts of the
     * text have no formatting or styles applied to them
     */
    @Test
    public void testNoFormat() throws Exception {
        assertContains("This is a piece of text that causes an exception",
                getXML("testWORD_no_format.docx", getParseContext()).xml);
    }

    // TIKA-1005:
    @Test
    public void testTextInsideTextBox() throws Exception {
        String xml = getXML("testWORD_text_box.docx", getParseContext()).xml;
        assertContains("This text is directly in the body of the document.", xml);
        assertContains("This text is inside of a text box in the body of the document.", xml);
        assertContains("This text is inside of a text box in the header of the document.", xml);
        assertContains("This text is inside of a text box in the footer of the document.", xml);
    }

    //TIKA-2807
    @Test
    public void testSDTInTextBox() throws Exception {
        String xml = getXML("testWORD_sdtInTextBox.docx", getParseContext()).xml;
        assertContains("rich-text-content-control_inside-text-box", xml);
        assertContainsCount("inside-text", xml, 1);
    }

    @Test
    public void testDOCXOverrideParagraphNumbering() throws Exception {
        String xml = getXML("testWORD_override_list_numbering.docx", getParseContext()).xml;

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
    public void testDiagramData() throws Exception {
        assertContains("From here",
                getXML("testWORD_diagramData.docx", getParseContext()).xml);
    }

    @Test
    public void testDOCXChartData() throws Exception {
        String xml = getXML("testWORD_charts.docx", getParseContext()).xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
    }

    @Test
    public void testDOCXPhoneticStrings() throws Exception {
        assertContains("\u6771\u4EAC (\u3068\u3046\u304D\u3087\u3046)",
                getXML("testWORD_phonetic.docx", getParseContext()).xml);

        OfficeParserConfig config = new OfficeParserConfig();
        config.setConcatenatePhoneticRuns(false);
        ParseContext parseContext = getParseContext();
        parseContext.set(OfficeParserConfig.class, config);
        String xml = getXML("testWORD_phonetic.docx", parseContext).xml;
        assertContains("\u6771\u4EAC", xml);
        assertNotContained("\u3068", xml);
    }

    @Test
    public void testVarious() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testWORD_various.docx", metadata, getParseContext());
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
        assertContains("Row 1 Col 1 Row 1 Col 2 Row 1 Col 3 Row 2 Col 1 Row 2 Col 2 Row 2 Col 3",
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
        assertEquals("Keyword1 Keyword2", metadata.get(Office.KEYWORDS));
        assertContains("Keyword1 Keyword2",
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));

        assertContains("Subject is here", content);
        assertEquals("Subject is here", metadata.get(DublinCore.SUBJECT));
        assertContains("Subject is here",
                Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));

        assertContains("Suddenly some Japanese text:", content);
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", content);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f",
                content);

        assertContains("And then some Gothic text:", content);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A",
                content);
    }

    /**
     * Test extraction of field-based hyperlinks using instrText/fldChar.
     * These are hyperlinks embedded as field codes rather than relationship-based hyperlinks.
     */
    @Test
    public void testInstrTextHyperlink() throws Exception {
        String xml = getXML("testInstrLink.docx", getParseContext()).xml;
        // The document contains a HYPERLINK field code in instrText
        assertContains("<a href=\"https://exmaple.com/file\">", xml);
        assertContains("Access Document(s)", xml);
    }

    /**
     * Test extraction of external reference field codes (INCLUDEPICTURE, INCLUDETEXT, IMPORT,
     * LINK).
     * These can be used to hide malicious URLs in documents.
     */
    @Test
    public void testExternalRefFieldCodes() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testExternalRefs.docx", getParseContext());
        Metadata m = metadataList.get(0);
        // Check metadata flag is set
        assertEquals("true", m.get(Office.HAS_FIELD_HYPERLINKS));

        String xml = getXML("testExternalRefs.docx", getParseContext()).xml;
        // Test INCLUDEPICTURE field code
        assertContains("class=\"external-ref-INCLUDEPICTURE\"", xml);
        assertContains("http://example.com/tracking.png", xml);
        // Test INCLUDETEXT field code
        assertContains("class=\"external-ref-INCLUDETEXT\"", xml);
        assertContains("http://example.org/payload.txt", xml);
        // Test IMPORT field code
        assertContains("class=\"external-ref-IMPORT\"", xml);
        assertContains("http://example.net/exploit.wmf", xml);
        // Test LINK field code
        assertContains("class=\"external-ref-LINK\"", xml);
        assertContains("http://test.invalid/cmd.docx", xml);
    }

    @Test
    public void testTextDecoration() throws Exception {
        String xml = getXML("testWORD_various.docx", getParseContext()).xml;

        assertContains("<b>Bold</b>", xml);
        assertContains("<i>italic</i>", xml);
        assertContains("<u>underline</u>", xml);
        assertContains("<s>strikethrough</s>", xml);
    }

    @Test
    public void testTextDecorationNested() throws Exception {
        String xml = getXML("testWORD_various.docx", getParseContext()).xml;

        assertContains("<i>ita<s>li</s>c</i>", xml);
        assertContains("<i>ita<s>l<u>i</u></s>c</i>", xml);

        String txt = getText("testWORD_various.docx", new Metadata(), getParseContext());
        assertContainsCount("italic", txt, 3);
        assertNotContained("ita ", txt);

        assertContainsCount("underline", txt, 2);
        assertNotContained("unde ", txt);
    }

    // TIKA-692
    @Test
    public void testContiguousHTMLFormatting() throws Exception {
        String xml = getXML("testWORD_bold_character_runs.docx", getParseContext()).xml;
        assertTrue(xml.contains("F<b>oob</b>a<b>r</b>"),
                "Bold text wasn't contiguous: " + xml);

        xml = getXML("testWORD_bold_character_runs2.docx", getParseContext()).xml;
        assertTrue(xml.contains("F<b>oob</b>a<b>r</b>"),
                "Bold text wasn't contiguous: " + xml);
    }

    //TIKA-2346
    @Test
    public void testTurningOffTextBoxExtraction() throws Exception {
        ParseContext pc = getParseContext();
        OfficeParserConfig config = getOrCreateOfficeParserConfig(pc);
        config.setIncludeShapeBasedContent(false);
        String xml = getXML("testWORD_text_box.docx", pc).xml;
        assertContains("This text is directly in the body of the document.", xml);
        assertNotContained(
                "This text is inside of a text box in the body of the document.", xml);
        assertNotContained(
                "This text is inside of a text box in the header of the document.", xml);
        assertNotContained(
                "This text is inside of a text box in the footer of the document.", xml);
    }

    @Test
    public void testHeaderFooterNotExtraction() throws Exception {
        ParseContext pc = getParseContext();
        OfficeParserConfig config = getOrCreateOfficeParserConfig(pc);
        config.setIncludeHeadersAndFooters(false);
        String xml = getXML("testWORD_various.docx", pc).xml;
        assertNotContained("This is the header text.", xml);
        assertNotContained("This is the footer text.", xml);
    }

    @Test
    public void testWordMissingOOXMLBeans() throws Exception {
        String[] fileNames = new String[]{"testWORD_missing_ooxml_bean1.docx"};
        PrintStream origErr = System.err;
        for (String fileName : fileNames) {
            ByteArrayOutputStream errContent = new ByteArrayOutputStream();
            System.setErr(new PrintStream(errContent, true, UTF_8.name()));
            getXML(fileName, getParseContext());
            System.setErr(origErr);
            String err = errContent.toString(UTF_8.name());
            assertTrue(err.isEmpty(),
                    "expected no error msg, but got >" + err + "<");
        }
    }


    @Test
    public void testFeatureExtraction() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_features.docx", getParseContext());
        Metadata m = metadataList.get(0);
        assertContains("Kyle Reese", Arrays.asList(m.getValues(Office.COMMENT_PERSONS)));
        assertEquals("true", m.get(Office.HAS_HIDDEN_TEXT));
        assertEquals("true", m.get(Office.HAS_TRACK_CHANGES));
        assertEquals("true", m.get(Office.HAS_COMMENTS));
    }

    @Test
    public void testDotx() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_template.docx", getParseContext());
        String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Metallica", content);
        assertContains("Hetfield", content);
        assertContains("one eye open", content);
        assertContains("Getting the perfect", content);
    }
}
