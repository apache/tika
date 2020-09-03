/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.tika.TikaTest;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class WordParserTest extends TikaTest {

    @Test
    public void testWordParser() throws Exception {
        try (InputStream input = WordParserTest.class.getResourceAsStream(
                "/test-documents/testWORD.doc")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            assertEquals(
                    "application/msword",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
            assertContains("Sample Word Document", handler.toString());
        }
    }

    @Test
    public void testWordWithWAV() throws Exception {
        try (InputStream input = WordParserTest.class.getResourceAsStream(
                "/test-documents/Doc1_ole.doc")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            assertContains("MSj00974840000[1].wav", handler.toString());
        }
    }

    /**
     * Test that the word converter is able to generate the
     *  correct HTML for the document
     */
    @Test
    public void testWordHTML() throws Exception {

        // Try with a document containing various tables and
        // formattings
        XMLResult result = getXML("testWORD.doc");
        String xml = result.xml;
        Metadata metadata = result.metadata;

        assertEquals(
                "application/msword",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
        assertTrue(xml.contains("Sample Word Document"));

        // Check that custom headings came through
        assertTrue(xml.contains("<h1 class=\"title\">"));
        // Regular headings
        assertTrue(xml.contains("<h1>Heading Level 1</h1>"));
        assertTrue(xml.contains("<h3>Heading Level 3</h3>"));
        // Bold and italic
        assertTrue(xml.contains("<b>BOLD</b>"));
        assertTrue(xml.contains("<i>ITALIC</i>"));
        // Table
        assertTrue(xml.contains("<table>"));
        assertTrue(xml.contains("<td>"));
        // TODO - Check for the nested table
        // Links
        assertTrue(xml.contains("<a href=\"http://tika.apache.org/\"><u>Tika</u></a>"));
        // Paragraphs with other styles
        assertTrue(xml.contains("<p class=\"signature\">This one"));

        // Try with a document that contains images
        xml = getXML("testWORD_3imgs.doc").xml;

        // Images 1-3
        assertTrue("Image not found in:\n" + xml, xml.contains("src=\"embedded:image1.png\""));
        assertTrue("Image not found in:\n" + xml, xml.contains("src=\"embedded:image2.jpg\""));
        assertTrue("Image not found in:\n" + xml, xml.contains("src=\"embedded:image3.png\""));

        // Text too
        assertTrue(xml.contains("<p>The end!"));

        // TIKA-692: test document containing multiple
        // character runs within a bold tag:
        xml = getXML("testWORD_bold_character_runs.doc").xml;

        // Make sure bold text arrived as single
        // contiguous string even though Word parser
        // handled this as 3 character runs
        assertTrue("Bold text wasn't contiguous: " + xml, xml.contains("F<b>oob</b>a<b>r</b>"));

        // TIKA-692: test document containing multiple
        // character runs within a bold tag:
        xml = getXML("testWORD_bold_character_runs2.doc").xml;

        // Make sure bold text arrived as single
        // contiguous string even though Word parser
        // handled this as 3 character runs
        assertTrue("Bold text wasn't contiguous: " + xml, xml.contains("F<b>oob</b>a<b>r</b>"));
    }

    @Test
    public void testEmbeddedNames() throws Exception {
        String result = getXML("testWORD_embedded_pdf.doc").xml;

        // Make sure the embedded div comes out after "Here
        // is the pdf file" and before "Bye Bye":
        int i = result.indexOf("Here is the pdf file:");
        assertTrue(i != -1);
        int j = result.indexOf("<div class=\"embedded\" id=\"_1402837031\" />");
        assertTrue(j != -1);
        int k = result.indexOf("Bye Bye");
        assertTrue(k != -1);

        assertTrue(i < j);
        assertTrue(j < k);
    }

    // TIKA-982
    @Test
    public void testEmbeddedRTF() throws Exception {
        String result = getXML("testWORD_embedded_rtf.doc").xml;
        assertTrue(result.contains("<div class=\"embedded\" id=\"_1404039792\" />"));
        assertTrue(result.contains("_1404039792.rtf"));
    }

    // TIKA-1019
    @Test
    public void testDocumentLink() throws Exception {
        String result = getXML("testDocumentLink.doc").xml;
        assertTrue(result.contains("<div class=\"embedded\" id=\"_1327495610\" />"));
        assertTrue(result.contains("_1327495610.unknown"));
    }

    @Test
    public void testWord6Parser() throws Exception {
        try (InputStream input = WordParserTest.class.getResourceAsStream(
                "/test-documents/testWORD6.doc")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            assertEquals(
                    "application/msword",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("The quick brown fox jumps over the lazy dog", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog", metadata.get(OfficeOpenXMLCore.SUBJECT));
            assertEquals("Nevin Nollop", metadata.get(TikaCoreProperties.CREATOR));
            assertContains("The quick brown fox jumps over the lazy dog", handler.toString());
        }
    }
    
    @Test
    public void testTextDecoration() throws Exception {
      XMLResult result = getXML("testWORD_various.doc");
      String xml = result.xml;

      assertTrue(xml.contains("<b>Bold</b>"));
      assertTrue(xml.contains("<i>italic</i>"));
      assertTrue(xml.contains("<u>underline</u>"));

    }

    @Test
    public void testTextDecorationNested() throws Exception {
        XMLResult result = getXML("testWORD_various.doc");
        String xml = result.xml;

        assertTrue(xml.contains("<i>ita<s>li</s>c</i>"));
        assertTrue(xml.contains("<i>ita<s>l<u>i</u></s>c</i>"));
        assertTrue(xml.contains("<i><u>unde<s>r</s>line</u></i>"));
    }


    //TIKA-2346
    @Test
    public void testTurningOffTextBox() throws Exception {
        ParseContext pc = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeShapeBasedContent(false);
        pc.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testWORD_various.doc", pc).xml;
        assertNotContained("text box", xml);
    }

    @Test
    public void testVarious() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = WordParserTest.class.getResourceAsStream(
                "/test-documents/testWORD_various.doc")) {
            new OfficeParser().parse(stream, handler, metadata, new ParseContext());
        }

        String content = handler.toString();
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
            //assertContains(row + ") Number bullet " + row, content);
            // TODO: WordExtractor fails to number the bullets:
            assertContains("Number bullet " + row, content);
        }

        for(int row=1;row<=2;row++) {
            for(int col=1;col<=3;col++) {
                assertContains("Row " + row + " Col " + col, content);
            }
        }

        assertContains("Keyword1 Keyword2", content);
        assertEquals("Keyword1 Keyword2",
                     metadata.get(TikaCoreProperties.SUBJECT));

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
    public void testHeaderFooterNotExtraction() throws Exception {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeHeadersAndFooters(false);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        String xml = getXML("testWORD_various.doc", parseContext).xml;
        assertNotContained("This is the header text.", xml);
        assertNotContained("This is the footer text.", xml);
    }

    /**
     * TIKA-1044 - Handle documents where parts of the
     *  text have no formatting or styles applied to them
     */
    @Test
    public void testNoFormat() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = WordParserTest.class.getResourceAsStream(
                "/test-documents/testWORD_no_format.doc")) {
            new OfficeParser().parse(stream, handler, metadata, new ParseContext());
        }

        String content = handler.toString();
        assertContains("Will generate an exception", content);
    }

    /**
     * Ensures that custom OLE2 (HPSF) properties are extracted
     */
    @Test
    public void testCustomProperties() throws Exception {
        Metadata metadata = new Metadata();

        try (InputStream input = WordParserTest.class.getResourceAsStream(
                "/test-documents/testWORD_custom_props.doc")) {
            ContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            context.set(Locale.class, Locale.US);
            new OfficeParser().parse(input, handler, metadata, context);
        }

        assertEquals("application/msword", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("EJ04325S", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Etienne Jouvin", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("2012-01-03T22:14:00Z", metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("2010-10-05T09:03:00Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("Microsoft Office Word", metadata.get(OfficeOpenXMLExtended.APPLICATION));
        assertEquals("1", metadata.get(Office.PAGE_COUNT));
        assertEquals("2", metadata.get(Office.WORD_COUNT));
        assertEquals("My Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("My Keyword", metadata.get(TikaCoreProperties.SUBJECT));
        assertEquals("Normal.dotm", metadata.get(OfficeOpenXMLExtended.TEMPLATE));
        assertEquals("My Comments", metadata.get(TikaCoreProperties.COMMENTS));
        // TODO: Move to OO subject in Tika 2.0
        assertEquals("My subject", metadata.get(OfficeOpenXMLCore.SUBJECT));
        assertEquals("EDF-DIT", metadata.get(OfficeOpenXMLExtended.COMPANY));
        assertEquals("MyStringValue", metadata.get("custom:MyCustomString"));
        assertEquals("2010-12-30T23:00:00Z", metadata.get("custom:MyCustomDate"));
    }

    @Test
    public void testExceptions1() throws Exception {
        XMLResult xml;
        Level logLevelStart = Logger.getRootLogger().getLevel();
        Logger.getRootLogger().setLevel(Level.ERROR);
        try {
            xml = getXML("testException1.doc");
            assertContains("total population", xml.xml);
            xml = getXML("testException2.doc");
            assertContains("electric charge", xml.xml);
        } finally {
            Logger.getRootLogger().setLevel(logLevelStart);
        }
    }

    @Test
    public void testTabularSymbol() throws Exception {
        assertContains("one two", getXML("testWORD_tabular_symbol.doc").xml.replaceAll("\\s+", " "));
    }

    /**
     * TIKA-1229 Hyperlinks in Headers should be output as such,
     *  not plain text with control characters
     */
    @Test
    public void testHeaderHyperlinks() throws Exception {
        XMLResult result = getXML("testWORD_header_hyperlink.doc");
        String xml = result.xml;
        Metadata metadata = result.metadata;

        assertEquals(
                "application/msword",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Lutz Theurer", metadata.get(TikaCoreProperties.CREATOR));
        assertContains("example.com", xml);

        // Check we don't have the special text HYPERLINK
        assertFalse(xml.contains("HYPERLINK"));

        // Check we do have the link
        assertContains("<a href=\"http://tw-systemhaus.de\"><u>http:", xml);

        // Check we do have the email
        assertContains("<a href=\"mailto:ab@example.com\"><u>ab@", xml);
    }

    @Test
    public void testControlCharacter() throws Exception {
        assertContains("<u>1.</u> <u>Introduzione</u><b> </b></a><u> </u></p>", getXML("testControlCharacters.doc").xml.replaceAll("\\s+", " "));
    }

    @Test
    public void testParagraphsAfterTables() throws Exception {
        XMLResult result = getXML("test_TIKA-1251.doc");

        String xml = result.xml;
        Metadata metadata = result.metadata;

        assertEquals(
                "application/msword",
                metadata.get(Metadata.CONTENT_TYPE));

        assertContains("<p><u>1. Organisering av vakten:</u></p>", xml);

    }

    @Test
    public void testHyperlinkStringIOOBESmartQuote() throws Exception {
        //TIKA-1512, one cause: closing double quote is a smart quote
        //test file contributed by user
        XMLResult result = getXML("testWORD_closingSmartQInHyperLink.doc");
        assertContains("href=\"https://issues.apache.org/jira/browse/TIKA-1512", result.xml);
    }

    @Test
    @Ignore //until we determine whether we can include test docs or not
    public void testHyperlinkStringLongNoCloseQuote() throws Exception {
        //TIKA-1512, one cause: no closing quote on really long string
        //test file derived from govdocs1 012152.doc
        XMLResult result = getXML("testWORD_longHyperLinkNoCloseQuote.doc");
        assertContains("href=\"http://www.lexis.com", result.xml);
    }

    @Test
    @Ignore //until we determine whether we can include test docs or not
    public void testHyperlinkStringLongCarriageReturn() throws Exception {
        //TIKA-1512, one cause: no closing quote, but carriage return
        //test file derived from govdocs1 040044.doc
        XMLResult result = getXML("testWORD_hyperLinkCarriageReturn.doc");
        assertContains("href=\"http://www.nib.org", result.xml);
    }

    @Test
    public void testDOCParagraphNumbering() throws Exception {
        String xml = getXML("testWORD_numbered_list.doc").xml;
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

        assertContains("add a list here", xml);
        //TODO: not currently pulling numbers out of comments
        assertContains(">comment list 1", xml);

    }

    @Test
    public void testDOCOverrideParagraphNumbering() throws Exception {
        String xml = getXML("testWORD_override_list_numbering.doc").xml;

        //Test 1
        assertContains("1.1.1.1...1 1.1.1.1...1", xml);
        assertContains("1st.2.3someText 1st.2.3someText", xml);
        assertContains("1st.2.2someOtherText.1 1st.2.2someOtherText.1", xml);
        assertContains("5th 5th", xml);


        //Test 2
        assertContains("1.a.I 1.a.I", xml);
        //test no reset because level 2 is not sufficient to reset
        assertContains("1.b.III 1.b.III", xml);
        //test restarted because of level 0's increment to 2
        assertContains("2.a.I 2.a.I", xml);
        //test handling of skipped level
        assertContains("2.b 2.b", xml);

        //Test 3
        assertContains("(1)) (1))", xml);
        //tests start level 1 at 17 and
        assertContains("2.17 2.17", xml);
        //tests that isLegal turns everything into decimal
        assertContains("2.18.2.1 2.18.2.1", xml);
        assertContains(">2 2", xml);

        //Test4
        assertContains(">1 1", xml);
        assertContains(">A A", xml);
        assertContains(">B B", xml);
        assertContains(">C C", xml);
        assertContains(">4 4", xml);

        //Test5
        assertContains(">00 00", xml);
        assertContains(">01 01", xml);
        assertContains(">01. 01.", xml);
        assertContains(">01..1 01..1", xml);
        assertContains(">02 02", xml);
    }

    @Test
    public void testMultiAuthorsManagers() throws Exception {
        XMLResult r = getXML("testWORD_multi_authors.doc");
        String[] authors = r.metadata.getValues(TikaCoreProperties.CREATOR);
        assertEquals(3, authors.length);
        assertEquals("author2", authors[1]);

        String[] managers = r.metadata.getValues(OfficeOpenXMLExtended.MANAGER);
        assertEquals(2, managers.length);
        assertEquals("manager1", managers[0]);
        assertEquals("manager2", managers[1]);
    }

    @Test
    public void testOrigLocation() throws Exception {
        Metadata metadata = getXML("testException2.doc").metadata;
        List<String> values = Arrays.asList(metadata.getValues(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));
        assertContains("C:\\Lab Documents\\Lab Manuals\\Physics 275-6\\276-s00\\07-Force-on-a-current-S00.doc", values);
        assertContains("Hard Drive:Course Folders:276:276-s00:07-Force-on-a-current-S00", values);
    }

    @Test
    public void testOrigSourcePath() throws Exception {
        Metadata embed1_zip_metadata = getRecursiveMetadata("test_recursive_embedded.doc").get(2);
        assertContains("C:\\Users\\tallison\\AppData\\Local\\Temp\\embed1.zip",
                Arrays.asList(embed1_zip_metadata.getValues(TikaCoreProperties.ORIGINAL_RESOURCE_NAME)));
        assertContains("C:\\Users\\tallison\\Desktop\\tmp\\New folder (2)\\embed1.zip",
                Arrays.asList(embed1_zip_metadata.getValues(TikaCoreProperties.ORIGINAL_RESOURCE_NAME)));
    }

    @Test
    public void testBoldHyperlink() throws Exception {
        //TIKA-1255
        String xml = getXML("testWORD_boldHyperlink.doc").xml;
        xml = xml.replaceAll("\\s+", " ");
        assertContains("<a href=\"http://tika.apache.org/\"><u>hyper <b>link</b></u></a>", xml);
        assertContains("<a href=\"http://tika.apache.org/\"><b><u>hyper</u></b><u> link</u></a>; bold" , xml);
    }

    @Test
    public void testHyperlinkSurroundedByItalics() throws Exception {
        //TIKA-2599
        String xml = getXML("testWORD_italicsSurroundingHyperlink.doc").xml;
        xml = xml.replaceAll("\\s+", " ");
        assertContains("<body><p><i>Italic Test before link </i><a href=\"http://www.google.com\"><b><i>" +
                "<u>hyperlink italics</u></i></b></a><i> Italic text after hyperlink</i></p>", xml);
    }

    @Test
    public void testMacros() throws  Exception {

        //test default is "don't extract macros"
        for (Metadata metadata : getRecursiveMetadata("testWORD_macros.doc")) {
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

        List<Metadata> metadataList = getRecursiveMetadata("testWORD_macros.doc", context);
        assertContainsAtLeast(minExpected, metadataList);

        //test configuring via config file
        TikaConfig tikaConfig = new TikaConfig(this.getClass().getResourceAsStream("tika-config-macros.xml"));
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);

        metadataList = getRecursiveMetadata("testWORD_macros.doc", parser);
        assertContainsAtLeast(minExpected, metadataList);
    }

    @Test
    public void testDeleted() throws Exception {
        //test classic behavior
        String xml = getXML("testWORD_2006ml.doc").xml;
        assertNotContained("frog", xml);

        //moveFrom is deleted in .doc files
        assertContainsCount("Second paragraph", xml, 1);
        //now test inclusion of deleted text
        ParseContext context = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setIncludeDeletedContent(true);
        context.set(OfficeParserConfig.class, officeParserConfig);
        XMLResult r = getXML("testWORD_2006ml.doc", context);
        assertContains("frog", r.xml);

        //moveFrom is deleted in .doc files
        assertContainsCount("Second paragraph", r.xml, 2);
    }

    @Test
    public void testProtected() throws Exception {
        try {
            getXML("testWORD_protected_passtika.doc");
            fail("should have thrown encrypted document exception");
        } catch (org.apache.tika.exception.EncryptedDocumentException e) {

        }
    }

    @Test
    public void testPhonetic() throws Exception {
        //TODO: fix this
        //current behavior:
        //EQ \\* jc2 \\* "Font:MS Mincho" \\* hps11 \\o\\ad(\\s\\up 10(とうきょう),東京)
        //We need to parse that string and separate content + phonetic
        assertContains("\\s\\up 10(\u3068\u3046\u304D\u3087\u3046),\u6771\u4EAC",
                getXML("testWORD_phonetic.doc").xml);
    }

    @Test
    public void testSpecialControlCharacter() throws Exception {
        //TIKA-2459
        assertContains("Paragraph one", getXML("testWORD_specialControlCharacter1415.doc").xml);
    }
}

