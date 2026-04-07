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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.microsoft.OfficeParserConfig;

/**
 * Tests for the DOCX parser.
 */
public class OOXMLDocxSAXTest extends TikaTest {

    // ---- tests from AbstractOOXMLDocxTest ----

    @Test
    public void testWord() throws Exception {
        XMLResult xmlResult = getXML("testWORD.docx");
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Sample Word Document", xmlResult.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Keith Bennett", xmlResult.metadata.get(TikaCoreProperties.CREATOR));
        assertTrue(xmlResult.xml.contains("Sample Word Document"));
    }

    @Test
    public void testWordFootnote() throws Exception {
        XMLResult xmlResult = getXML("footnotes.docx");
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertTrue(xmlResult.xml.contains("snoska"));
        assertContains("<div class=\"footnote\">", xmlResult.xml);
        assertNotContained("<p><div class=\"footnote\">", xmlResult.xml);
    }

    @Test
    public void testEndnoteWithTable() throws Exception {
        XMLResult xmlResult = getXML("testWORD_endnote_table.docx");
        assertContains("Cat Property Act", xmlResult.xml);
        assertContains("<div class=\"endnote\">", xmlResult.xml);
    }

    @Test
    public void testWordHTML() throws Exception {
        XMLResult result = getXML("testWORD.docx");
        String xml = result.xml;
        Metadata metadata = result.metadata;
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
        assertTrue(xml.contains("Sample Word Document"));
        assertTrue(xml.contains("<h1 class=\"title\">"));
        assertContains("<h1>Heading Level 1</h1>", xml);
        assertTrue(xml.contains("<h2>Heading Level 2</h2>"));
        assertContains("Heading Level 3", xml);
        assertContains("<a name=\"OnLevel3\" />", xml);
        assertTrue(xml.contains("<b>BOLD</b>"));
        assertTrue(xml.contains("<i>ITALIC</i>"));
        assertTrue(xml.contains("<table>"));
        assertTrue(xml.contains("<td>"));
        assertTrue(xml.contains("<a href=\"http://tika.apache.org/\">Tika</a>"));
        assertContains("<a href=\"#OnMainHeading\">The Main Heading Bookmark</a>", xml);
        assertTrue(xml.contains("<p class=\"signature\">This one"));

        result = getXML("testWORD_3imgs.docx");
        xml = result.xml;

        assertTrue(xml.contains("<img src=\"embedded:image2.png\" alt=\"A description...\" />"),
                "Image not found in:\n" + xml);
        assertTrue(xml.contains("<img src=\"embedded:image3.jpeg\" alt=\"A description...\" />"),
                "Image not found in:\n" + xml);
        assertTrue(xml.contains("<img src=\"embedded:image4.png\" alt=\"A description...\" />"),
                "Image not found in:\n" + xml);
        assertTrue(xml.contains("<p>The end!</p>"));
    }

    @Test
    public void testWordPicturesInHeader() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("headerPic.docx");
        assertEquals(2, metadataList.size());
        Metadata m = metadataList.get(0);
        String mainContent = m.get(TikaCoreProperties.TIKA_CONTENT);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                m.get(Metadata.CONTENT_TYPE));
        assertTrue(mainContent.contains("<img"));
    }

    @Test
    public void testNullHeaders() throws Exception {
        XMLResult xmlResult = getXML("NullHeader.docx");
        assertEquals(false, xmlResult.xml.isEmpty(), "Should have found some text");
    }

    @Test
    public void testMissingText() throws Exception {
        XMLResult xmlResult = getXML("testWORD_missing_text.docx");
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("BigCompany", xmlResult.xml);
        assertContains("Seasoned", xmlResult.xml);
        assertContains("Rich_text_in_cell", xmlResult.xml);
    }

    @Test
    public void testBoldHyperlink() throws Exception {
        String xml = getXML("testWORD_boldHyperlink.docx").xml;
        xml = xml.replaceAll("\\s+", " ");
        assertContains("<a href=\"http://tika.apache.org/\">hyper <b>link</b></a>", xml);
        assertContains("<a href=\"http://tika.apache.org/\"><b>hyper</b> link</a>; bold", xml);
    }

    @Test
    public void testLongForIntExceptionInSummaryDetails() throws Exception {
        assertContains("bold",
                getXML("testWORD_totalTimeOutOfRange.docx").xml);
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
    public void testOrigSourcePath() throws Exception {
        Metadata embed1_zip_metadata =
                getRecursiveMetadata("test_recursive_embedded.docx").get(2);
        assertContains("C:\\Users\\tallison\\AppData\\Local\\Temp\\embed1.zip", Arrays.asList(
                embed1_zip_metadata.getValues(TikaCoreProperties.ORIGINAL_RESOURCE_NAME)));
        assertContains("C:\\Users\\tallison\\Desktop\\tmp\\New folder (2)\\embed1.zip",
                Arrays.asList(
                        embed1_zip_metadata.getValues(
                                TikaCoreProperties.ORIGINAL_RESOURCE_NAME)));
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
    public void testWordCustomProperties() throws Exception {
        Metadata metadata = getXML("testWORD_custom_props.docx").metadata;
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

    @Test
    public void testEmbeddedPDF() throws Exception {
        String xml = getXML("testWORD_embedded_pdf.docx").xml;
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

    @Test
    public void testWordNullStyle() throws Exception {
        String xml = getXML("testWORD_null_style.docx").xml;
        assertContains("Test av styrt dokument", xml);
    }

    @Test
    public void testNoFormat() throws Exception {
        assertContains("This is a piece of text that causes an exception",
                getXML("testWORD_no_format.docx").xml);
    }

    @Test
    public void testTextInsideTextBox() throws Exception {
        String xml = getXML("testWORD_text_box.docx").xml;
        assertContains("This text is directly in the body of the document.", xml);
        assertContains("This text is inside of a text box in the body of the document.", xml);
        assertContains("This text is inside of a text box in the header of the document.", xml);
        assertContains("This text is inside of a text box in the footer of the document.", xml);
    }

    @Test
    public void testSDTInTextBox() throws Exception {
        String xml = getXML("testWORD_sdtInTextBox.docx").xml;
        assertContains("rich-text-content-control_inside-text-box", xml);
        assertContainsCount("inside-text", xml, 1);
    }

    @Test
    public void testDOCXOverrideParagraphNumbering() throws Exception {
        String xml = getXML("testWORD_override_list_numbering.docx").xml;

        assertContains("<p>1.1.1.1...1 1.1.1.1...1</p>", xml);
        assertContains("1st.2.3someText 1st.2.3someText", xml);
        assertContains("1st.2.2someOtherText.1 1st.2.2someOtherText.1", xml);
        assertContains("5th 5th", xml);

        assertContains("1.a.I 1.a.I", xml);
        assertContains("<p>1.b.III 1.b.III</p>", xml);
        assertContains("2.a.I 2.a.I", xml);
        assertContains("<p>2.b 2.b</p>", xml);

        assertContains("(1)) (1))", xml);
        assertContains("2.17 2.17", xml);
        assertContains("2.18.2.1 2.18.2.1", xml);
        assertContains("<p>2 2</p>", xml);

        assertContains("<p>1 1</p>", xml);
        assertContains("<p>A A</p>", xml);
        assertContains("<p>B B</p>", xml);
        assertContains("<p>C C</p>", xml);
        assertContains("<p>4 4</p>", xml);

        assertContains(">00 00", xml);
        assertContains(">01 01", xml);
        assertContains(">01. 01.", xml);
        assertContains(">01..1 01..1", xml);
        assertContains(">02 02", xml);
    }

    @Test
    public void testDiagramData() throws Exception {
        assertContains("From here",
                getXML("testWORD_diagramData.docx").xml);
    }

    @Test
    public void testDOCXChartData() throws Exception {
        String xml = getXML("testWORD_charts.docx").xml;
        assertContains("peach", xml);
        assertContains("March\tApril", xml);
        assertNotContained("chartSpace", xml);
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
    public void testVarious() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testWORD_various.docx", metadata);
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
            assertContains("Bullet " + row, content);
        }
        assertContains("Here is a numbered list:", content);
        for (int row = 1; row <= 3; row++) {
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
        assertContains("\uff08\uff27\uff28\uff31\uff09", content);
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f",
                content);
        assertContains("And then some Gothic text:", content);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A",
                content);
    }

    @Test
    public void testInstrTextHyperlink() throws Exception {
        String xml = getXML("testInstrLink.docx").xml;
        assertContains("<a href=\"https://exmaple.com/file\">", xml);
        assertContains("Access Document(s)", xml);
    }

    @Test
    public void testExternalRefFieldCodes() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testExternalRefs.docx");
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_FIELD_HYPERLINKS));
        String xml = getXML("testExternalRefs.docx").xml;
        assertContains("class=\"external-ref-INCLUDEPICTURE\"", xml);
        assertContains("http://example.com/tracking.png", xml);
        assertContains("class=\"external-ref-INCLUDETEXT\"", xml);
        assertContains("http://example.org/payload.txt", xml);
        assertContains("class=\"external-ref-IMPORT\"", xml);
        assertContains("http://example.net/exploit.wmf", xml);
        assertContains("class=\"external-ref-LINK\"", xml);
        assertContains("http://test.invalid/cmd.docx", xml);
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
        String txt = getText("testWORD_various.docx");
        assertContainsCount("italic", txt, 3);
        assertNotContained("ita ", txt);
        assertContainsCount("underline", txt, 2);
        assertNotContained("unde ", txt);
    }

    @Test
    public void testContiguousHTMLFormatting() throws Exception {
        String xml = getXML("testWORD_bold_character_runs.docx").xml;
        assertTrue(xml.contains("F<b>oob</b>a<b>r</b>"),
                "Bold text wasn't contiguous: " + xml);
        xml = getXML("testWORD_bold_character_runs2.docx").xml;
        assertTrue(xml.contains("F<b>oob</b>a<b>r</b>"),
                "Bold text wasn't contiguous: " + xml);
    }

    @Test
    public void testTurningOffTextBoxExtraction() throws Exception {
        ParseContext pc = new ParseContext();
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeShapeBasedContent(false);
        pc.set(OfficeParserConfig.class, config);
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
        ParseContext pc = new ParseContext();
        OfficeParserConfig config = new OfficeParserConfig();
        config.setIncludeHeadersAndFooters(false);
        pc.set(OfficeParserConfig.class, config);
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
            getXML(fileName);
            System.setErr(origErr);
            String err = errContent.toString(UTF_8.name());
            assertTrue(err.isEmpty(),
                    "expected no error msg, but got >" + err + "<");
        }
    }

    @Test
    public void testFeatureExtraction() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_features.docx");
        Metadata m = metadataList.get(0);
        assertContains("Kyle Reese", Arrays.asList(m.getValues(Office.COMMENT_PERSONS)));
        assertEquals("true", m.get(Office.HAS_HIDDEN_TEXT));
        assertEquals("true", m.get(Office.HAS_TRACK_CHANGES));
        assertEquals("true", m.get(Office.HAS_COMMENTS));
    }

    @Test
    public void testDotx() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_template.docx");
        String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Metallica", content);
        assertContains("Hetfield", content);
        assertContains("one eye open", content);
        assertContains("Getting the perfect", content);
    }

    // ---- SAX-specific tests ----

    @Test
    public void testTextDecorationNestedUnderlineStrike() throws Exception {
        String xml = getXML("testWORD_various.docx").xml;
        assertContains("<i><u>unde</u><s><u>r</u></s><u>line</u></i>", xml);
    }

    @Test
    public void basicTest() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_2006ml.docx");

        assertEquals(8, metadataList.size());
        Metadata m = metadataList.get(0);

        assertEquals("2016-11-29T00:58:00Z", m.get(TikaCoreProperties.CREATED));
        assertEquals("2016-11-29T17:54:00Z", m.get(TikaCoreProperties.MODIFIED));
        assertEquals("My Document Title", m.get(TikaCoreProperties.TITLE));
        assertEquals("This is the Author", m.get(TikaCoreProperties.CREATOR));
        assertEquals("3", m.get(OfficeOpenXMLCore.REVISION));
        assertEquals("Allison, Timothy B.", m.get(TikaCoreProperties.MODIFIER));
        assertEquals("260", m.get(Office.WORD_COUNT));
        assertEquals("3", m.get(Office.PARAGRAPH_COUNT));
        assertEquals("1742", m.get(Office.CHARACTER_COUNT_WITH_SPACES));
        assertEquals("12", m.get(Office.LINE_COUNT));
        assertEquals("16.0000", m.get(OfficeOpenXMLExtended.APP_VERSION));

        String content = m.get(TikaCoreProperties.TIKA_CONTENT);

        assertContainsCount("engaging title page", content, 1);
        assertContainsCount("This is the Author\n", content, 1);
        assertContainsCount("This is an engaging title page", content, 1);

        assertContains("My Document Title", content);
        assertContains("My Document Subtitle", content);

        assertContains(
                "<p class=\"toc_1\">\t<a href=\"#_Toc467647605\">Heading1\t3</a></p>",
                content);

        assertContains("2. Really basic 2.", content);

        assertContainsCount("This is a text box", content, 1);

        assertContains(
                "<p>This is a hyperlink: <a href=\"http://tika.apache.org\">tika</a></p>",
                content);

        assertContains(
                "<p>This is a link to a local file: " +
                        "<a href=\"file:///C:/data/test.png\">test.png</a></p>",
                content);

        assertContains("<p>This is          10 spaces</p>", content);

        assertContains(
                "<p class=\"table_of_figures\">\t" +
                        "<a href=\"#_Toc467647797\">Table 1: Table1 Caption\t2</a></p>",
                content);

        assertContains("<td>Embedded table r1c1", content);
        assertContainsCount("<p>This is text within a shape", content, 1);
        assertContains("<p>Rich text content control", content);
        assertContains("<p>Simple text content control", content);
        assertContains("Repeating content", content);
        assertContains("Drop down1", content);
        assertContains("<p>11/16/2016</p>", content);
        assertContains("tab\ttab", content);
        assertContainsCount("serious word art", content, 1);
        assertContainsCount("Wordartr1c1", content, 1);
        assertContains("Click or tap to enter a date", content);
        assertContains(
                "<p>The <i>quick</i> brown <b>fox </b>j<i>um</i><b><i>ped</i></b> over",
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
        assertNotContained("frog", content);
        assertContains("Mattmann", content);
    }

    @Test
    public void testPicturesInVariousPlaces() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_embedded_pics.docx");

        assertEquals(3, metadataList.size());
        String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        for (int i = 1; i < 4; i++) {
            assertContains("header" + i + "_pic", content);
            assertContains("footer" + i + "_pic", content);
        }
        assertContains("body_pic.jpg", content);
        assertContains("sdt_pic.jpg", content);
        assertContains("deeply_embedded_pic", content);
        assertContains("deleted_pic", content);
        assertContains("footnotes_pic", content);
        assertContains("comments_pic", content);
        assertContains("endnotes_pic", content);

        assertContainsCount("<img src=", content, 14);
    }

    @Test
    public void testSkipDeleted() throws Exception {
        ParseContext pc = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeDeletedContent(true);
        officeParserConfig.setIncludeMoveFromContent(true);
        pc.set(OfficeParserConfig.class, officeParserConfig);

        XMLResult r = getXML("testWORD_2006ml.docx", pc);
        assertContains("frog", r.xml);
        assertContainsCount("Second paragraph", r.xml, 2);
    }

    @Test
    public void testEncrypted() throws Exception {
        Map<String, String> tests = new HashMap<>();
        tests.put("testWORD_protected_passtika.docx",
                "This is an encrypted Word 2007 File");

        PasswordProvider passwordProvider = metadata -> "tika";
        OfficeParserConfig opc = new OfficeParserConfig();
        ParseContext passwordContext = new ParseContext();
        passwordContext.set(PasswordProvider.class, passwordProvider);
        passwordContext.set(OfficeParserConfig.class, opc);
        for (Map.Entry<String, String> e : tests.entrySet()) {
            assertContains(e.getValue(), getXML(e.getKey(), passwordContext).xml);
        }

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
        assertContains(
                "Some-1-CrazyFormat Greek numbering with crazy format - alpha", xml);
        assertContains("1.1.1. 1.1.1", xml);
        assertContains("1.1. 1.2-&gt;1.1  //set the value", xml);
    }

    @Test
    public void testMacrosInDocm() throws Exception {
        Metadata parsedBy = new Metadata();
        parsedBy.add(TikaCoreProperties.TIKA_PARSED_BY,
                "org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFEventBasedWordExtractor");

        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_macros.docm");
        for (Metadata metadata : metadataList) {
            if (metadata.get(Metadata.CONTENT_TYPE).equals("text/x-vbasic")) {
                fail("Shouldn't have extracted macros as default");
            }
        }
        assertContainsAtLeast(parsedBy, metadataList);

        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractMacros(true);
        context.set(OfficeParserConfig.class, officeParserConfig);

        metadataList = getRecursiveMetadata("testWORD_macros.docm", context);
        assertContains("quick",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertContainsAtLeast(parsedBy, metadataList);

        Metadata minExpected = new Metadata();
        minExpected.add(TikaCoreProperties.TIKA_CONTENT.getName(), "Sub Embolden()");
        minExpected.add(TikaCoreProperties.TIKA_CONTENT.getName(), "Sub Italicize()");
        minExpected.add(Metadata.CONTENT_TYPE, "text/x-vbasic");
        minExpected.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);

        AutoDetectParser parser = (AutoDetectParser) TikaLoader.load(
                getConfigPath(OOXMLDocxSAXTest.class, "tika-config-sax-macros.json"))
                .loadAutoDetectParser();
        metadataList = getRecursiveMetadata("testWORD_macros.docm", parser);
        assertContainsAtLeast(minExpected, metadataList);
        assertContainsAtLeast(parsedBy, metadataList);
    }

    @Test
    public void testEmbedded() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testWORD_embeded.docx");
        Metadata main = metadataList.get(0);
        String content = main.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains(
                "<img src=\"embedded:image2.jpeg\" alt=\"A description...\" />", content);
        assertContains("<div class=\"embedded\" id=\"rId8\" />", content);
        assertEquals(16, metadataList.size());
    }

    @Test
    public void testInitializationViaConfig() throws Exception {
        AutoDetectParser p = (AutoDetectParser) TikaLoader.load(
                getConfigPath(OOXMLDocxSAXTest.class, "tika-config-sax-docx.json"))
                .loadAutoDetectParser();
        XMLResult xml = getXML("testWORD_2006ml.docx", p, new Metadata());
        assertContains("engaging title", xml.xml);
    }

    @Test
    public void testTruncatedSAXDocx() throws Exception {
        assertThrows(TikaException.class, () -> {
            getRecursiveMetadata("testWORD_truncated.docx");
        });
    }

    // Security feature tests

    @Test
    public void testHoverAndVmlHyperlinks() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testHoverAndVml.docx");
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_HOVER_HYPERLINKS));
        assertEquals("true", m.get(Office.HAS_VML_HYPERLINKS));

        String xml = getXML("testHoverAndVml.docx").xml;
        assertContains("class=\"external-ref-hlinkHover\"", xml);
        assertContains("http://hover.example.com/phishing", xml);
        assertContains("class=\"external-ref-vml-shape-href\"", xml);
        assertContains("http://vml.example.org/shape-link", xml);
    }

    @Test
    public void testMailMerge() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testMailMerge.docx");
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_MAIL_MERGE));
    }

    @Test
    public void testAttachedTemplate() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testAttachedTemplate.docx");
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_ATTACHED_TEMPLATE));

        String xml = getXML("testAttachedTemplate.docx").xml;
        assertContains("class=\"external-ref-attachedTemplate\"", xml);
        assertContains("example.com/templates", xml);
    }

    @Test
    public void testSubdocument() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testSubdocument.docx");
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_SUBDOCUMENTS));

        String xml = getXML("testSubdocument.docx").xml;
        assertContains("class=\"external-ref-subDocument\"", xml);
        assertContains("example.org/chapters", xml);
    }

    @Test
    public void testFrameset() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testFrameset.docx");
        Metadata m = metadataList.get(0);
        assertEquals("true", m.get(Office.HAS_FRAMESETS));
    }

    @Test
    public void testNamespaceInFragments() throws Exception {
        String filePath = System.getProperty("tika.test.docx.namespace");
        if (filePath == null) {
            return;
        }
        java.io.File f = new java.io.File(filePath);
        if (!f.isFile()) {
            return;
        }
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        org.xml.sax.ContentHandler handler =
                new org.apache.tika.sax.BodyContentHandler(-1);
        try (TikaInputStream tis = TikaInputStream.get(f.toPath())) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }
        String[] warnings = metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        for (String w : warnings) {
            assertNotContained("not bound", w);
        }
    }
}
