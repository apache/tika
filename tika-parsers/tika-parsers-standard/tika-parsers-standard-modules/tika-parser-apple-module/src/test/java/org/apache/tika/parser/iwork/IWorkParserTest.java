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
package org.apache.tika.parser.iwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Tests if the IWork parser parses the content and metadata properly of the supported formats.
 */
public class IWorkParserTest extends TikaTest {

    private IWorkPackageParser iWorkParser;

    @BeforeEach
    public void setUp() {
        iWorkParser = new IWorkPackageParser();
    }

    /**
     * Check the given InputStream is not closed by the Parser (TIKA-1117).
     *
     * @throws Exception
     */
    @Test
    public void testStreamNotClosed() throws Exception {
        InputStream input = getResourceAsStream("/test-documents/testKeynote.key");
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        iWorkParser.parse(input, handler, metadata);
        input.read();   // Will throw an Exception if the stream was already closed.
    }

    @Test
    public void testParseKeynote() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testKeynote.key", iWorkParser, metadata);

        // Make sure enough keys came through
        // (Exact numbers will vary based on composites)
        assertTrue(metadata.size() >= 6, "Insufficient metadata found " + metadata.size());
        List<String> metadataKeys = Arrays.asList(metadata.names());
        assertTrue(metadataKeys.contains(Metadata.CONTENT_TYPE),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(Office.SLIDE_COUNT.getName()),
                "Metadata not found in " + metadataKeys);
//        assertTrue("Metadata not found in " + metadataKeys,
//        metadataKeys.contains(Office.SLIDE_COUNT.getName()));
        assertTrue(metadataKeys.contains(TikaCoreProperties.CREATOR.getName()),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(TikaCoreProperties.TITLE.getName()),
                "Metadata not found in " + metadataKeys);

        // Check the metadata values
        assertEquals("application/vnd.apple.keynote", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("3", metadata.get(Office.SLIDE_COUNT));
        assertEquals("1024", metadata.get(KeynoteContentHandler.PRESENTATION_WIDTH));
        assertEquals("768", metadata.get(KeynoteContentHandler.PRESENTATION_HEIGHT));
        assertEquals("Tika user", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Apache tika", metadata.get(TikaCoreProperties.TITLE));

        assertContains("A sample presentation", content);
        assertContains("For the Apache Tika project", content);
        assertContains("Slide 1", content);
        assertContains("Some random text for the sake of testability.", content);
        assertContains("A nice comment", content);
        assertContains("A nice note", content);

        // test table data
        assertContains("Cell one", content);
        assertContains("Cell two", content);
        assertContains("Cell three", content);
        assertContains("Cell four", content);
        assertContains("Cell 5", content);
        assertContains("Cell six", content);
        assertContains("7", content);
        assertContains("Cell eight", content);
        assertContains("5/5/1985", content);
    }

    // TIKA-910
    @Test
    public void testKeynoteTextBoxes() throws Exception {
        String content = getText("testTextBoxes.key", iWorkParser);
        assertTrue(content.replaceAll("\\s+", " ").contains("text1 text2 text3"));
    }

    // TIKA-910
    @Test
    public void testKeynoteBulletPoints() throws Exception {
        String content = getText("testBulletPoints.key", iWorkParser);
        assertTrue(content.replaceAll("\\s+", " ")
                .contains("bullet point 1 bullet point 2 bullet point 3"));
    }

    // TIKA-923
    @Test
    public void testKeynoteTables() throws Exception {
        String content = getText("testTables.key", iWorkParser);
        content = content.replaceAll("\\s+", " ");
        assertContains("row 1 row 2 row 3", content);
    }

    // TIKA-923
    @Test
    public void testKeynoteMasterSlideTable() throws Exception {
        String content = getText("testMasterSlideTable.key", iWorkParser);
        content = content.replaceAll("\\s+", " ");
        assertContains("master row 1", content);
        assertContains("master row 2", content);
        assertContains("master row 3", content);
    }

    @Test
    public void testParsePages() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testPages.pages", iWorkParser, metadata);
        // Make sure enough keys came through
        // (Exact numbers will vary based on composites)
        assertTrue(metadata.size() >= 50, "Insufficient metadata found " + metadata.size());
        List<String> metadataKeys = Arrays.asList(metadata.names());
        assertTrue(metadataKeys.contains(Metadata.CONTENT_TYPE),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(Office.PAGE_COUNT.getName()),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(TikaCoreProperties.CREATOR.getName()),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(TikaCoreProperties.TITLE.getName()),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(TikaCoreProperties.MODIFIED.getName()),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(TikaCoreProperties.LANGUAGE.getName()),
                "Metadata not found in " + metadataKeys);

        // Check the metadata values
        assertEquals("application/vnd.apple.pages", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Tika user", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Apache tika", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("2010-05-09T21:34:38+0200", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2010-05-09T23:50:36+0200", metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("en", metadata.get(TikaCoreProperties.LANGUAGE));
        assertEquals("2", metadata.get(Office.PAGE_COUNT));

        // text on page 1
        assertContains("Sample pages document", content);
        assertContains("Some plain text to parse.", content);
        assertContains("Cell one", content);
        assertContains("Cell two", content);
        assertContains("Cell three", content);
        assertContains("Cell four", content);
        assertContains("Cell five", content);
        assertContains("Cell six", content);
        assertContains("Cell seven", content);
        assertContains("Cell eight", content);
        assertContains("Cell nine", content);
        assertContains("Both Pages 1.x and Keynote 2.x", content); // ...

        // text on page 2
        assertContains("A second page....", content);
        assertContains("Extensible Markup Language", content); // ...
    }

    // TIKA-904
    @Test
    public void testPagesLayoutMode() throws Exception {
        String content = getText("testPagesLayout.pages");
        assertContains("text box 1 - here is some text", content);
        assertContains("created in a text box in layout mode", content);
        assertContains("text box 2 - more text!@!$@#", content);
        assertContains("this is text inside of a green box", content);
        assertContains("text inside of a green circle", content);
    }

    @Test
    public void testParseNumbers() throws Exception {
        Metadata metadata = new Metadata();
        String content = getText("testNumbers.numbers", iWorkParser, metadata);

        // Make sure enough keys came through
        // (Exact numbers will vary based on composites)
        assertTrue(metadata.size() >= 8, "Insufficient metadata found " + metadata.size());
        List<String> metadataKeys = Arrays.asList(metadata.names());
        assertTrue(metadataKeys.contains(Metadata.CONTENT_TYPE),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(Office.PAGE_COUNT.getName()),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(TikaCoreProperties.CREATOR.getName()),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(TikaCoreProperties.COMMENTS.getName()),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(TikaCoreProperties.TITLE.getName()),
                "Metadata not found in " + metadataKeys);
        assertTrue(metadataKeys.contains(TikaCoreProperties.TITLE.getName()),
                "Metadata not found in " + metadataKeys);

        // Check the metadata values
        assertEquals("2", metadata.get(Office.PAGE_COUNT));
        assertEquals("Tika User", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Account checking", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("a comment", metadata.get(TikaCoreProperties.COMMENTS));

        assertContains("Category", content);
        assertContains("Home", content);
        assertContains("-226", content);
        assertContains("-137.5", content);
        assertContains("Checking Account: 300545668", content);
        assertContains("4650", content);
        assertContains("Credit Card", content);
        assertContains("Groceries", content);
        assertContains("-210", content);
        assertContains("Food", content);
        assertContains("Try adding your own account transactions to this table.", content);
    }

    // TIKA- 924
    @Test
    public void testParseNumbersTableNames() throws Exception {
        String content = getText("tableNames.numbers", iWorkParser);
        assertContains("This is the main table", content);
    }

    @Test
    public void testParseNumbersTableHeaders() throws Exception {
        String content = getText("tableHeaders.numbers");
        for (int header = 1; header <= 5; header++) {
            assertContains("header" + header, content);
        }
        for (int row = 1; row <= 3; row++) {
            assertContains("row" + row, content);
        }
    }

    /**
     * We don't currently support password protected Pages files, as
     * we don't know how the encryption works (it's not regular Zip
     * Encryption). See TIKA-903 for details
     */
    @Test
    public void testParsePagesPasswordProtected() throws Exception {
        // Document password is "tika", but we can't use that yet...
        Metadata metadata = new Metadata();
        String content = getText("testPagesPwdProtected.pages", iWorkParser, metadata);
        assertEquals("", content);

        // Will have been identified as encrypted
        assertEquals("application/x-tika-iworks-protected", metadata.get(Metadata.CONTENT_TYPE));
    }

    /**
     * Check we get headers, footers and footnotes from Pages
     */
    @Test
    public void testParsePagesHeadersFootersFootnotes() throws Exception {
        String footnote = "Footnote: Do a lot of people really use iWork?!?!";
        String header = "THIS IS SOME HEADER TEXT";
        String footer = "THIS IS SOME FOOTER TEXT\t1";
        String footer2 = "THIS IS SOME FOOTER TEXT\t2";

        String content = getText("testPagesHeadersFootersFootnotes.pages", iWorkParser);

        // Check regular text
        assertContains("Both Pages 1.x", content); // P1
        assertContains("understanding the Pages document", content); // P1
        assertContains("should be page 2", content); // P2

        // Check for headers, footers and footnotes
        assertContains(header, content);
        assertContains(footer, content);
        assertContains(footer2, content);
        assertContains(footnote, content);
    }

    /**
     * Check we get upper-case Roman numerals within the footer for AutoPageNumber.
     */
    @Test
    public void testParsePagesHeadersFootersRomanUpper() throws Exception {
        String header = "THIS IS SOME HEADER TEXT";
        String footer = "THIS IS SOME FOOTER TEXT\tI";
        String footer2 = "THIS IS SOME FOOTER TEXT\tII";

        String content = getText("testPagesHeadersFootersRomanUpper.pages", iWorkParser);

        // Check for headers, footers and footnotes
        assertContains(header, content);
        assertContains(footer, content);
        assertContains(footer2, content);
    }

    /**
     * Check we get lower-case Roman numerals within the footer for AutoPageNumber.
     */
    @Test
    public void testParsePagesHeadersFootersRomanLower() throws Exception {
        String header = "THIS IS SOME HEADER TEXT";
        String footer = "THIS IS SOME FOOTER TEXT\ti";
        String footer2 = "THIS IS SOME FOOTER TEXT\tii";

        String content = getText("testPagesHeadersFootersRomanLower.pages", iWorkParser);

        // Check for headers, footers and footnotes
        assertContains(header, content);
        assertContains(footer, content);
        assertContains(footer2, content);
    }

    /**
     * Check we get upper-case alpha-numeric letters within the footer for AutoPageNumber.
     */
    @Test
    public void testParsePagesHeadersAlphaUpper() throws Exception {
        String header = "THIS IS SOME HEADER TEXT\tA";
        String footer = "THIS IS SOME FOOTER TEXT\tA";
        String footer2 = "THIS IS SOME FOOTER TEXT\tB";

        String content = getText("testPagesHeadersFootersAlphaUpper.pages", iWorkParser);

        // Check for headers, footers and footnotes
        assertContains(header, content);
        assertContains(footer, content);
        assertContains(footer2, content);
    }

    /**
     * Check we get lower-case alpha-numeric letters within the footer for AutoPageNumber.
     */
    @Test
    public void testParsePagesHeadersAlphaLower() throws Exception {
        String header = "THIS IS SOME HEADER TEXT";
        String footer = "THIS IS SOME FOOTER TEXT\ta";
        String footer2 = "THIS IS SOME FOOTER TEXT\tb";

        String content = getText("testPagesHeadersFootersAlphaLower.pages", iWorkParser);

        // Check for headers, footers and footnotes
        assertContains(header, content);
        assertContains(footer, content);
        assertContains(footer2, content);
    }

    /**
     * Check we get annotations (eg comments) from Pages
     */
    @Test
    public void testParsePagesAnnotations() throws Exception {
        String commentA = "comment about the APXL file";
        String commentB = "comment about UIMA";

        String content = getText("testPagesComments.pages", iWorkParser);

        // Check regular text
        assertContains("Both Pages 1.x", content); // P1
        assertContains("understanding the Pages document", content); // P1
        assertContains("should be page 2", content); // P2

        // Check for comments
        assertContains(commentA, content);
        assertContains(commentB, content);
    }

    // TIKA-918
    @Test
    public void testNumbersExtractChartNames() throws Exception {
        String content = getText("testNumbersCharts.numbers");
        assertContains("Expenditure by Category", content);
        assertContains("Currency Chart name", content);
        assertContains("Chart 2", content);
    }

    //TIKA-3020
    @Test
    public void testKeyNoteTableMarkup() throws Exception {
        String expected =
                "<table><tr>\t<td>Cell one</td>\t<td>Cell two</td>\t<td>Cell three</td></tr>" +
                        "<tr>\t<td>Cell four</td>\t<td>Cell 5</td>\t<td>Cell six</td></tr>" +
                        "<tr>\t<td>7</td>\t<td>Cell eight</td>\t<td>5/5/1985</td></tr>" +
                        "</table>";
        String xml = getXML("testKeynote.key", iWorkParser).xml;
        xml = xml.replaceAll("[\r\n]", "");
        assertContains(expected, xml);

    }
}
