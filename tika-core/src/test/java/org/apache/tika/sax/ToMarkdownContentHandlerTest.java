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
package org.apache.tika.sax;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Test cases for the {@link ToMarkdownContentHandler} class.
 */
public class ToMarkdownContentHandlerTest {

    private static final String XHTML = "http://www.w3.org/1999/xhtml";
    private static final Attributes EMPTY = new AttributesImpl();

    private static void startElement(ContentHandler handler, String name) throws Exception {
        handler.startElement(XHTML, name, name, EMPTY);
    }

    private static void startElement(ContentHandler handler, String name, String attrName,
                                     String attrValue) throws Exception {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", attrName, attrName, "CDATA", attrValue);
        handler.startElement(XHTML, name, name, atts);
    }

    private static void startElement(ContentHandler handler, String name, AttributesImpl atts)
            throws Exception {
        handler.startElement(XHTML, name, name, atts);
    }

    private static void endElement(ContentHandler handler, String name) throws Exception {
        handler.endElement(XHTML, name, name);
    }

    private static void chars(ContentHandler handler, String text) throws Exception {
        char[] ch = text.toCharArray();
        handler.characters(ch, 0, ch.length);
    }

    /** A replayable sequence of SAX events, for the behaviour-lock tests below. */
    private interface Events {
        void emit(ContentHandler h) throws Exception;
    }

    /**
     * Render the given events and assert the handler does not throw, returning the
     * Markdown. The Markdown is now produced by building a commonmark AST, which is
     * stricter than the string writer it replaced, so these tests both document the
     * handler's tolerance of malformed/illegal input and fail loudly if a future
     * commonmark upgrade starts throwing on input we currently render.
     */
    private static String renderNoThrow(String label, Events events) {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        return assertDoesNotThrow(() -> {
            handler.startDocument();
            events.emit(handler);
            handler.endDocument();
            return handler.toString();
        }, label);
    }

    @Test
    public void testHeadings() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "h1");
        chars(handler, "Title");
        endElement(handler, "h1");

        startElement(handler, "h2");
        chars(handler, "Subtitle");
        endElement(handler, "h2");

        startElement(handler, "h3");
        chars(handler, "Section");
        endElement(handler, "h3");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("# Title"));
        assertTrue(result.contains("## Subtitle"));
        assertTrue(result.contains("### Section"));
    }

    @Test
    public void testAllHeadingLevels() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        for (int i = 1; i <= 6; i++) {
            startElement(handler, "h" + i);
            chars(handler, "H" + i);
            endElement(handler, "h" + i);
        }

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("# H1"));
        assertTrue(result.contains("## H2"));
        assertTrue(result.contains("### H3"));
        assertTrue(result.contains("#### H4"));
        assertTrue(result.contains("##### H5"));
        assertTrue(result.contains("###### H6"));
    }

    @Test
    public void testParagraphs() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        chars(handler, "First paragraph.");
        endElement(handler, "p");

        startElement(handler, "p");
        chars(handler, "Second paragraph.");
        endElement(handler, "p");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("First paragraph."));
        assertTrue(result.contains("Second paragraph."));
        // Paragraphs should be separated by blank line
        assertTrue(result.contains("First paragraph.\n\nSecond paragraph."));
    }

    @Test
    public void testBold() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        chars(handler, "This is ");
        startElement(handler, "b");
        chars(handler, "bold");
        endElement(handler, "b");
        chars(handler, " text.");
        endElement(handler, "p");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("**bold**"));
    }

    @Test
    public void testStrong() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        startElement(handler, "strong");
        chars(handler, "strong");
        endElement(handler, "strong");
        endElement(handler, "p");

        handler.endDocument();

        assertTrue(handler.toString().contains("**strong**"));
    }

    @Test
    public void testItalic() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        chars(handler, "This is ");
        startElement(handler, "i");
        chars(handler, "italic");
        endElement(handler, "i");
        chars(handler, " text.");
        endElement(handler, "p");

        handler.endDocument();

        assertTrue(handler.toString().contains("*italic*"));
    }

    @Test
    public void testEmphasis() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        startElement(handler, "em");
        chars(handler, "emphasized");
        endElement(handler, "em");
        endElement(handler, "p");

        handler.endDocument();

        assertTrue(handler.toString().contains("*emphasized*"));
    }

    @Test
    public void testLink() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        chars(handler, "Click ");
        startElement(handler, "a", "href", "https://example.com");
        chars(handler, "here");
        endElement(handler, "a");
        chars(handler, " for more.");
        endElement(handler, "p");

        handler.endDocument();

        assertTrue(handler.toString().contains("[here](https://example.com)"));
    }

    @Test
    public void testImage() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "alt", "alt", "CDATA", "A photo");
        atts.addAttribute("", "src", "src", "CDATA", "photo.jpg");
        startElement(handler, "img", atts);
        endElement(handler, "img");
        endElement(handler, "p");

        handler.endDocument();

        assertTrue(handler.toString().contains("![A photo](photo.jpg)"));
    }

    @Test
    public void testUnorderedList() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "ul");
        startElement(handler, "li");
        chars(handler, "Apple");
        endElement(handler, "li");
        startElement(handler, "li");
        chars(handler, "Banana");
        endElement(handler, "li");
        startElement(handler, "li");
        chars(handler, "Cherry");
        endElement(handler, "li");
        endElement(handler, "ul");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("- Apple"));
        assertTrue(result.contains("- Banana"));
        assertTrue(result.contains("- Cherry"));
    }

    @Test
    public void testOrderedList() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "ol");
        startElement(handler, "li");
        chars(handler, "First");
        endElement(handler, "li");
        startElement(handler, "li");
        chars(handler, "Second");
        endElement(handler, "li");
        startElement(handler, "li");
        chars(handler, "Third");
        endElement(handler, "li");
        endElement(handler, "ol");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("1. First"));
        assertTrue(result.contains("2. Second"));
        assertTrue(result.contains("3. Third"));
    }

    @Test
    public void testNestedLists() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "ul");
        startElement(handler, "li");
        chars(handler, "Fruit");

        startElement(handler, "ul");
        startElement(handler, "li");
        chars(handler, "Apple");
        endElement(handler, "li");
        startElement(handler, "li");
        chars(handler, "Banana");
        endElement(handler, "li");
        endElement(handler, "ul");

        endElement(handler, "li");
        startElement(handler, "li");
        chars(handler, "Vegetable");
        endElement(handler, "li");
        endElement(handler, "ul");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("- Fruit"));
        assertTrue(result.contains("  - Apple"));
        assertTrue(result.contains("  - Banana"));
        assertTrue(result.contains("- Vegetable"));
    }

    @Test
    public void testTable() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "table");

        // Header row
        startElement(handler, "tr");
        startElement(handler, "th");
        chars(handler, "Name");
        endElement(handler, "th");
        startElement(handler, "th");
        chars(handler, "Age");
        endElement(handler, "th");
        endElement(handler, "tr");

        // Data row
        startElement(handler, "tr");
        startElement(handler, "td");
        chars(handler, "Alice");
        endElement(handler, "td");
        startElement(handler, "td");
        chars(handler, "30");
        endElement(handler, "td");
        endElement(handler, "tr");

        endElement(handler, "table");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("|Name|Age|"));
        assertTrue(result.contains("|---|---|"));
        assertTrue(result.contains("|Alice|30|"));
    }

    @Test
    public void testFencedCodeBlock() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "pre");
        startElement(handler, "code");
        chars(handler, "int x = 42;");
        endElement(handler, "code");
        endElement(handler, "pre");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("```\n"));
        assertTrue(result.contains("int x = 42;"));
        assertTrue(result.contains("\n```"));
    }

    @Test
    public void testInlineCode() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        chars(handler, "Use the ");
        startElement(handler, "code");
        chars(handler, "println");
        endElement(handler, "code");
        chars(handler, " function.");
        endElement(handler, "p");

        handler.endDocument();

        assertTrue(handler.toString().contains("`println`"));
    }

    @Test
    public void testBlockquote() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "blockquote");
        chars(handler, "To be or not to be.");
        endElement(handler, "blockquote");

        handler.endDocument();

        assertTrue(handler.toString().contains("> To be or not to be."));
    }

    @Test
    public void testHorizontalRule() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        chars(handler, "Above");
        endElement(handler, "p");

        startElement(handler, "hr");
        endElement(handler, "hr");

        startElement(handler, "p");
        chars(handler, "Below");
        endElement(handler, "p");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("---"));
        assertTrue(result.contains("Above"));
        assertTrue(result.contains("Below"));
    }

    @Test
    public void testLineBreak() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        chars(handler, "Line one");
        startElement(handler, "br");
        endElement(handler, "br");
        chars(handler, "Line two");
        endElement(handler, "p");

        handler.endDocument();

        // GFM hard line break: two trailing spaces before the newline
        assertTrue(handler.toString().contains("Line one  \nLine two"));
    }

    @Test
    public void testBoldInsideListItem() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "ul");
        startElement(handler, "li");
        startElement(handler, "b");
        chars(handler, "Important");
        endElement(handler, "b");
        chars(handler, " item");
        endElement(handler, "li");
        endElement(handler, "ul");

        handler.endDocument();

        assertTrue(handler.toString().contains("- **Important** item"));
    }

    @Test
    public void testLinkInsideHeading() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "h2");
        startElement(handler, "a", "href", "https://example.com");
        chars(handler, "Linked Title");
        endElement(handler, "a");
        endElement(handler, "h2");

        handler.endDocument();

        assertTrue(handler.toString().contains("## [Linked Title](https://example.com)"));
    }

    @Test
    public void testScriptContentSkipped() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        chars(handler, "Before");
        endElement(handler, "p");

        startElement(handler, "script");
        chars(handler, "alert('xss');");
        endElement(handler, "script");

        startElement(handler, "p");
        chars(handler, "After");
        endElement(handler, "p");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("Before"));
        assertTrue(result.contains("After"));
        assertFalse(result.contains("alert"));
    }

    @Test
    public void testStyleContentSkipped() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        chars(handler, "Visible");
        endElement(handler, "p");

        startElement(handler, "style");
        chars(handler, "body { color: red; }");
        endElement(handler, "style");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("Visible"));
        assertFalse(result.contains("color"));
    }

    @Test
    public void testMarkdownEscaping() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        chars(handler, "Special chars: * _ [ ] # | \\ `");
        endElement(handler, "p");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("\\*"));
        assertTrue(result.contains("\\_"));
        assertTrue(result.contains("\\["));
        assertTrue(result.contains("\\]"));
        // '#' only needs escaping at line start, so it is left bare mid-line
        assertTrue(result.contains("#"));
        assertFalse(result.contains("\\#"));
        assertTrue(result.contains("\\|"));
        assertTrue(result.contains("\\\\"));
        assertTrue(result.contains("\\`"));
    }

    @Test
    public void testNoEscapingInCodeBlock() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "pre");
        startElement(handler, "code");
        chars(handler, "x * y = z");
        endElement(handler, "code");
        endElement(handler, "pre");

        handler.endDocument();

        String result = handler.toString();
        // Inside code blocks, * should NOT be escaped
        assertTrue(result.contains("x * y = z"));
        assertFalse(result.contains("\\*"));
    }

    @Test
    public void testNoEscapingInInlineCode() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        startElement(handler, "code");
        chars(handler, "a*b");
        endElement(handler, "code");
        endElement(handler, "p");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("`a*b`"));
    }

    @Test
    public void testDefinitionList() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "dl");
        startElement(handler, "dt");
        chars(handler, "Term");
        endElement(handler, "dt");
        startElement(handler, "dd");
        chars(handler, "Definition of the term");
        endElement(handler, "dd");
        endElement(handler, "dl");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("**Term**"));
        assertTrue(result.contains(": Definition of the term"));
    }

    @Test
    public void testDiv() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "div");
        chars(handler, "Content in div");
        endElement(handler, "div");

        startElement(handler, "div");
        chars(handler, "Another div");
        endElement(handler, "div");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("Content in div"));
        assertTrue(result.contains("Another div"));
        // Divs should be separated
        assertTrue(result.contains("Content in div\n\nAnother div"));
    }

    @Test
    public void testNoExcessiveBlankLines() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        // Simulate SAX events with whitespace text nodes between elements,
        // as typically produced by XHTML parsers
        startElement(handler, "div");
        chars(handler, "\n  ");
        startElement(handler, "p");
        chars(handler, "First");
        endElement(handler, "p");
        chars(handler, "\n  ");
        endElement(handler, "div");

        chars(handler, "\n  ");

        startElement(handler, "div");
        chars(handler, "\n  ");
        endElement(handler, "div");

        chars(handler, "\n  ");

        startElement(handler, "div");
        chars(handler, "\n  ");
        startElement(handler, "p");
        chars(handler, "Second");
        endElement(handler, "p");
        chars(handler, "\n  ");
        endElement(handler, "div");

        handler.endDocument();

        String result = handler.toString();
        // Should not have more than one blank line (two consecutive newlines) anywhere
        assertFalse(result.contains("\n\n\n"),
                "Output should not contain triple newlines: " + result);
        assertContains("First", result);
        assertContains("Second", result);
    }

    @Test
    public void testInlineSpacesPreserved() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        startElement(handler, "b");
        chars(handler, "bold");
        endElement(handler, "b");
        chars(handler, " ");
        startElement(handler, "i");
        chars(handler, "italic");
        endElement(handler, "i");
        endElement(handler, "p");

        handler.endDocument();

        String result = handler.toString();
        // Space between bold and italic should be preserved
        assertTrue(result.contains("**bold** *italic*"));
    }

    private static void assertContains(String needle, String haystack) {
        assertTrue(haystack.contains(needle),
                "Expected to find '" + needle + "' in: " + haystack);
    }

    @Test
    public void testLinkTextEscaped() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        startElement(handler, "a", "href", "https://good.example");
        chars(handler, "x](https://evil.example)");
        endElement(handler, "a");
        endElement(handler, "p");

        handler.endDocument();

        String result = handler.toString();
        // the ] in the link text must be escaped so it cannot close the link early
        assertTrue(result.contains("x\\]"), result);
        assertFalse(result.contains("[x](https://evil.example)"), result);
    }

    @Test
    public void testLinkHrefNoBreakout() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        startElement(handler, "a", "href", "https://evil.example) text");
        chars(handler, "click");
        endElement(handler, "a");
        endElement(handler, "p");

        handler.endDocument();

        String result = handler.toString();
        // a destination with spaces/parens must be wrapped so it cannot terminate (..) early
        assertTrue(result.contains("](<https://evil.example"), result);
        assertFalse(result.contains("](https://evil.example)"), result);
    }

    @Test
    public void testImageAltAndSrcEscaped() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "p");
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "alt", "alt", "CDATA", "a]b");
        atts.addAttribute("", "src", "src", "CDATA", "https://evil.example) x");
        startElement(handler, "img", atts);
        endElement(handler, "img");
        endElement(handler, "p");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("a\\]b"), result);
        assertTrue(result.contains("](<https://evil.example"), result);
        assertFalse(result.contains("](https://evil.example)"), result);
    }

    @Test
    public void testTableCellPipeEscaped() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "table");
        startElement(handler, "tr");
        startElement(handler, "td");
        chars(handler, "a|b");
        endElement(handler, "td");
        startElement(handler, "td");
        chars(handler, "c");
        endElement(handler, "td");
        endElement(handler, "tr");
        endElement(handler, "table");

        handler.endDocument();

        String result = handler.toString();
        // a pipe inside a cell must be escaped so it does not inject an extra column
        assertTrue(result.contains("|a\\|b|c|"), result);
    }

    @Test
    public void testTableCellNewlineFolded() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "table");
        startElement(handler, "tr");
        startElement(handler, "td");
        chars(handler, "a\nb");
        endElement(handler, "td");
        endElement(handler, "tr");
        endElement(handler, "table");

        handler.endDocument();

        String result = handler.toString();
        // a newline inside a cell must not terminate the table row
        assertTrue(result.contains("|a b|"), result);
    }

    @Test
    public void testHandlerTypeParsingMarkdown() {
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.MARKDOWN,
                BasicContentHandlerFactory.parseHandlerType("markdown",
                        BasicContentHandlerFactory.HANDLER_TYPE.TEXT));
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.MARKDOWN,
                BasicContentHandlerFactory.parseHandlerType("md",
                        BasicContentHandlerFactory.HANDLER_TYPE.TEXT));
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.MARKDOWN,
                BasicContentHandlerFactory.parseHandlerType("MARKDOWN",
                        BasicContentHandlerFactory.HANDLER_TYPE.TEXT));
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.MARKDOWN,
                BasicContentHandlerFactory.parseHandlerType("MD",
                        BasicContentHandlerFactory.HANDLER_TYPE.TEXT));
    }

    @Test
    public void testFactoryCreatesMarkdownHandler() {
        BasicContentHandlerFactory factory =
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.MARKDOWN, -1);
        org.xml.sax.ContentHandler handler = factory.createHandler();
        assertTrue(handler instanceof ToMarkdownContentHandler);
    }

    @Test
    public void testTableWithOnlyTd() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "table");

        startElement(handler, "tr");
        startElement(handler, "td");
        chars(handler, "A");
        endElement(handler, "td");
        startElement(handler, "td");
        chars(handler, "B");
        endElement(handler, "td");
        endElement(handler, "tr");

        startElement(handler, "tr");
        startElement(handler, "td");
        chars(handler, "C");
        endElement(handler, "td");
        startElement(handler, "td");
        chars(handler, "D");
        endElement(handler, "td");
        endElement(handler, "tr");

        endElement(handler, "table");

        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("|A|B|"));
        assertTrue(result.contains("|---|---|"));
        assertTrue(result.contains("|C|D|"));
    }

    @Test
    public void testNestedTablesIgnored() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();

        startElement(handler, "table");

        // Outer header row
        startElement(handler, "tr");
        startElement(handler, "th");
        chars(handler, "Outer1");
        endElement(handler, "th");
        startElement(handler, "th");
        chars(handler, "Outer2");
        endElement(handler, "th");
        endElement(handler, "tr");

        // Outer data row with nested table in second cell
        startElement(handler, "tr");
        startElement(handler, "td");
        chars(handler, "A");
        endElement(handler, "td");
        startElement(handler, "td");
        chars(handler, "B");

        // Nested table -- should be ignored
        startElement(handler, "table");
        startElement(handler, "tr");
        startElement(handler, "td");
        chars(handler, "Inner");
        endElement(handler, "td");
        endElement(handler, "tr");
        endElement(handler, "table");

        endElement(handler, "td");
        endElement(handler, "tr");

        endElement(handler, "table");

        handler.endDocument();

        String result = handler.toString();
        // Outer table should be rendered
        assertTrue(result.contains("|Outer1|Outer2|"));
        assertTrue(result.contains("|---|---|"));
        // Inner cell text gets folded into the outer cell ("B" + "Inner" = "BInner")
        assertTrue(result.contains("|A|BInner|"));
        // Inner table structure should not appear as a separate table
        assertFalse(result.contains("|Inner|"));
    }

    private static final String[] ALL_ELEMENTS = {
            "h1", "h2", "h3", "h4", "h5", "h6",
            "p", "div", "span",
            "b", "strong", "i", "em",
            "a", "img",
            "ul", "ol", "li",
            "table", "tr", "th", "td",
            "blockquote", "pre", "code",
            "br", "hr",
            "dl", "dt", "dd",
            "script", "style",
            "html", "head", "body", "title", "meta"
    };

    /**
     * Randomized test: fire random sequences of startElement/endElement/characters
     * events with no guarantee of proper nesting. The handler must not throw any
     * runtime exceptions (e.g., EmptyStackException, NullPointerException,
     * IndexOutOfBoundsException).
     */
    @RepeatedTest(100)
    public void testRandomUnbalancedTags() throws Exception {
        Random rng = new Random();
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();

        assertDoesNotThrow(() -> {
            handler.startDocument();

            int numEvents = 50 + rng.nextInt(150);
            for (int i = 0; i < numEvents; i++) {
                int action = rng.nextInt(4);
                String elem = ALL_ELEMENTS[rng.nextInt(ALL_ELEMENTS.length)];
                switch (action) {
                    case 0:
                        // start element (possibly with attributes)
                        if (elem.equals("a")) {
                            startElement(handler, elem, "href", randomText(rng));
                        } else if (elem.equals("img")) {
                            AttributesImpl atts = new AttributesImpl();
                            atts.addAttribute("", "src", "src", "CDATA", randomText(rng));
                            atts.addAttribute("", "alt", "alt", "CDATA", randomText(rng));
                            startElement(handler, elem, atts);
                        } else {
                            startElement(handler, elem);
                        }
                        break;
                    case 1:
                        // end element (possibly unmatched)
                        endElement(handler, elem);
                        break;
                    case 2:
                        // characters -- random content incl. control chars, null bytes,
                        // unpaired surrogates and non-characters (NOT relying on an
                        // upstream SafeContentHandler to have cleaned them)
                        chars(handler, randomText(rng));
                        break;
                    case 3:
                        // ignorable whitespace
                        char[] ws = "  \t\n".toCharArray();
                        handler.ignorableWhitespace(ws, 0, ws.length);
                        break;
                }
            }

            handler.endDocument();
        });

        // Just verify we can get the output without error
        assertDoesNotThrow(() -> handler.toString());
    }

    /**
     * Test extra endElement calls with no matching start -- should not throw.
     */
    @Test
    public void testExtraEndElements() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();

        assertDoesNotThrow(() -> {
            handler.startDocument();

            // End elements with no matching starts
            endElement(handler, "p");
            endElement(handler, "table");
            endElement(handler, "tr");
            endElement(handler, "td");
            endElement(handler, "ul");
            endElement(handler, "li");
            endElement(handler, "a");
            endElement(handler, "pre");
            endElement(handler, "code");
            endElement(handler, "blockquote");
            endElement(handler, "b");
            endElement(handler, "i");
            endElement(handler, "script");
            endElement(handler, "style");

            handler.endDocument();
        });
    }

    /**
     * Test start elements with no matching end -- should not throw.
     */
    @Test
    public void testUnclosedElements() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();

        assertDoesNotThrow(() -> {
            handler.startDocument();

            startElement(handler, "p");
            chars(handler, "unclosed paragraph");
            startElement(handler, "b");
            chars(handler, "unclosed bold");
            startElement(handler, "a", "href", "http://example.com");
            chars(handler, "unclosed link");
            startElement(handler, "ul");
            startElement(handler, "li");
            chars(handler, "unclosed list item");
            startElement(handler, "table");
            startElement(handler, "tr");
            startElement(handler, "td");
            chars(handler, "unclosed cell");
            startElement(handler, "blockquote");
            chars(handler, "unclosed quote");
            startElement(handler, "pre");
            chars(handler, "unclosed pre");

            handler.endDocument();
        });
    }

    @Test
    public void testMisnestedBlockInsideInlineAndCrossedClose() throws Exception {
        // Classic broken nesting a misbehaving parser can emit: a block opened
        // inside an inline, with crossed end tags (<p><div></p></div>). The AST
        // builder must not throw (commonmark rejects a block under an inline) and
        // must still render the text.
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        String result = assertDoesNotThrow(() -> {
            handler.startDocument();
            startElement(handler, "p");
            startElement(handler, "div");
            chars(handler, "misnested text");
            endElement(handler, "p");
            endElement(handler, "div");
            handler.endDocument();
            return handler.toString();
        });
        assertContains("misnested text", result);
    }

    @Test
    public void testInlineSpanningBlockBoundaries() throws Exception {
        // <b>bold<p>para</b>more</p>: inline opened, a block opened inside it, the
        // inline closed across the block boundary, then more text. Must not throw
        // and must preserve all the text.
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        String result = assertDoesNotThrow(() -> {
            handler.startDocument();
            startElement(handler, "b");
            chars(handler, "bold");
            startElement(handler, "p");
            chars(handler, "para");
            endElement(handler, "b");
            chars(handler, "more");
            endElement(handler, "p");
            handler.endDocument();
            return handler.toString();
        });
        assertContains("bold", result);
        assertContains("para", result);
        assertContains("more", result);
    }

    @Test
    public void testContentPreservedWhenParserThrowsBeforeEndDocument() throws Exception {
        // A parser that writes content and then throws never calls endDocument().
        // Because this handler builds an AST and renders at endDocument, partial
        // content would be lost if toString() relied solely on a completed render.
        // It must instead render what has been received so far -- matching the
        // streaming writer this replaced. (Regression guard for the rmeta NPE case.)
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();
        startElement(handler, "p");
        chars(handler, "some content");
        endElement(handler, "p");
        // deliberately NO endDocument() -- simulates a parser exception mid-document
        assertContains("some content", handler.toString());
    }

    @Test
    public void testPartialContentWithStillOpenElementsNoEndDocument() throws Exception {
        // Same failure mode, but the exception lands while elements are still open
        // (no end tags, no endDocument). Content received so far must still surface.
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();
        startElement(handler, "h1");
        chars(handler, "Heading");
        startElement(handler, "p");
        chars(handler, "body text");
        // no end tags, no endDocument()
        String result = handler.toString();
        assertContains("Heading", result);
        assertContains("body text", result);
    }

    /**
     * Test deeply nested elements of the same type -- should not throw.
     */
    @Test
    public void testDeeplyNestedSameElement() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();

        assertDoesNotThrow(() -> {
            handler.startDocument();

            // Deeply nested lists
            for (int i = 0; i < 50; i++) {
                startElement(handler, "ul");
                startElement(handler, "li");
                chars(handler, "level " + i);
            }
            for (int i = 0; i < 50; i++) {
                endElement(handler, "li");
                endElement(handler, "ul");
            }

            // Deeply nested blockquotes
            for (int i = 0; i < 20; i++) {
                startElement(handler, "blockquote");
            }
            chars(handler, "deep quote");
            for (int i = 0; i < 20; i++) {
                endElement(handler, "blockquote");
            }

            handler.endDocument();
        });
    }

    /**
     * Test interleaved (improperly nested) elements -- should not throw.
     */
    @Test
    public void testInterleavedElements() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();

        assertDoesNotThrow(() -> {
            handler.startDocument();

            // <b><i>text</b></i> -- improper nesting
            startElement(handler, "b");
            startElement(handler, "i");
            chars(handler, "interleaved");
            endElement(handler, "b");
            endElement(handler, "i");

            // <table><p>text</table></p>
            startElement(handler, "table");
            startElement(handler, "p");
            chars(handler, "table with p");
            endElement(handler, "table");
            endElement(handler, "p");

            // <ul><h1>text</ul></h1>
            startElement(handler, "ul");
            startElement(handler, "h1");
            chars(handler, "list with heading");
            endElement(handler, "ul");
            endElement(handler, "h1");

            handler.endDocument();
        });
    }

    // --- untrusted content cannot break out of inline/table structure ---

    @Test
    public void testLinkTextCannotBreakOut() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();
        startElement(handler, "p");
        startElement(handler, "a", "href", "https://good.example");
        chars(handler, "x](https://evil.example)");
        endElement(handler, "a");
        endElement(handler, "p");
        handler.endDocument();

        String result = handler.toString();
        // the ] in the link text is escaped, so it cannot close the link early
        assertTrue(result.contains("x\\]"), result);
        assertFalse(result.contains("x](https://evil.example)"), result);
        assertTrue(result.contains("](https://good.example)"), result);
    }

    @Test
    public void testLinkDestinationWithSpaceAndParenWrapped() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();
        startElement(handler, "p");
        startElement(handler, "a", "href", "https://evil.example) text");
        chars(handler, "click");
        endElement(handler, "a");
        endElement(handler, "p");
        handler.endDocument();

        String result = handler.toString();
        // a destination with spaces/parens is wrapped in <> so it cannot terminate early
        assertTrue(result.contains("](<https://evil.example) text>)"), result);
    }

    @Test
    public void testTableCellPipeAndNewlineContained() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();
        startElement(handler, "table");
        startElement(handler, "tr");
        startElement(handler, "th");
        chars(handler, "h1");
        endElement(handler, "th");
        startElement(handler, "th");
        chars(handler, "h2");
        endElement(handler, "th");
        endElement(handler, "tr");
        startElement(handler, "tr");
        startElement(handler, "td");
        chars(handler, "a|b\nc");
        endElement(handler, "td");
        startElement(handler, "td");
        chars(handler, "d");
        endElement(handler, "td");
        endElement(handler, "tr");
        endElement(handler, "table");
        handler.endDocument();

        String result = handler.toString();
        // pipe escaped, newline folded: cell stays one cell, row stays two columns
        assertTrue(result.contains("|a\\|b c|d|"), result);
    }

    @Test
    public void testImageAltAndSrcCannotBreakOut() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();
        startElement(handler, "p");
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "alt", "alt", "CDATA", "a]b");
        atts.addAttribute("", "src", "src", "CDATA", "https://evil.example) x");
        startElement(handler, "img", atts);
        endElement(handler, "img");
        endElement(handler, "p");
        handler.endDocument();

        String result = handler.toString();
        assertTrue(result.contains("a\\]b"), result);
        assertFalse(result.contains("](https://evil.example) x)"), result);
    }

    @Test
    public void testBoldInsideLinkIsNested() throws Exception {
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        handler.startDocument();
        startElement(handler, "p");
        startElement(handler, "a", "href", "https://example.com");
        startElement(handler, "b");
        chars(handler, "bold link");
        endElement(handler, "b");
        endElement(handler, "a");
        endElement(handler, "p");
        handler.endDocument();

        String result = handler.toString();
        // inline markup nests correctly inside the link text
        assertTrue(result.contains("[**bold link**](https://example.com)"), result);
    }

    @Test
    public void testInlineContentInStructuralContainersDoesNotThrow() {
        // A parser may emit inline content directly inside a list/table container
        // (no list item / cell). commonmark tolerates the resulting tree today.
        renderNoThrow("bold in ul", h -> {
            startElement(h, "ul"); startElement(h, "b"); chars(h, "x");
            endElement(h, "b"); endElement(h, "ul");
        });
        renderNoThrow("italic in ol", h -> {
            startElement(h, "ol"); startElement(h, "i"); chars(h, "x");
            endElement(h, "i"); endElement(h, "ol");
        });
        renderNoThrow("bold in tr without cell", h -> {
            startElement(h, "table"); startElement(h, "tr"); startElement(h, "b"); chars(h, "x");
            endElement(h, "b"); endElement(h, "tr"); endElement(h, "table");
        });
        renderNoThrow("inline directly in table", h -> {
            startElement(h, "table"); startElement(h, "b"); chars(h, "x");
            endElement(h, "b"); endElement(h, "table");
        });
        renderNoThrow("img in tr without cell", h -> {
            startElement(h, "table"); startElement(h, "tr"); startElement(h, "img");
            endElement(h, "img"); endElement(h, "tr"); endElement(h, "table");
        });
        renderNoThrow("br in ul", h -> {
            startElement(h, "ul"); startElement(h, "br"); endElement(h, "br"); endElement(h, "ul");
        });
        renderNoThrow("link in ul", h -> {
            startElement(h, "ul"); startElement(h, "a", "href", "http://e"); chars(h, "x");
            endElement(h, "a"); endElement(h, "ul");
        });
        renderNoThrow("heading inside link", h -> {
            startElement(h, "a", "href", "http://e"); startElement(h, "h1"); chars(h, "x");
            endElement(h, "h1"); endElement(h, "a");
        });
        renderNoThrow("table inside paragraph", h -> {
            startElement(h, "p"); startElement(h, "table"); startElement(h, "tr"); startElement(h, "td");
            chars(h, "x"); endElement(h, "td"); endElement(h, "tr"); endElement(h, "table"); endElement(h, "p");
        });
        renderNoThrow("list inside table", h -> {
            startElement(h, "table"); startElement(h, "ul"); startElement(h, "li"); chars(h, "x");
            endElement(h, "li"); endElement(h, "ul"); endElement(h, "table");
        });
    }

    @Test
    public void testTableAndListEdgeCasesDoNotThrow() {
        renderNoThrow("empty table", h -> {
            startElement(h, "table"); endElement(h, "table");
        });
        renderNoThrow("table with only th", h -> {
            startElement(h, "table"); startElement(h, "th"); chars(h, "x");
            endElement(h, "th"); endElement(h, "table");
        });
        renderNoThrow("table with empty cells", h -> {
            startElement(h, "table"); startElement(h, "tr"); startElement(h, "td");
            endElement(h, "td"); endElement(h, "tr"); endElement(h, "table");
        });
        renderNoThrow("td outside table", h -> {
            startElement(h, "td"); chars(h, "x"); endElement(h, "td");
        });
        renderNoThrow("tr outside table", h -> {
            startElement(h, "tr"); startElement(h, "td"); chars(h, "x");
            endElement(h, "td"); endElement(h, "tr");
        });
        renderNoThrow("li without a list", h -> {
            startElement(h, "li"); chars(h, "x"); endElement(h, "li");
        });
        renderNoThrow("stray text between list items", h -> {
            startElement(h, "ol"); startElement(h, "li"); chars(h, "a"); endElement(h, "li");
            chars(h, "stray"); startElement(h, "li"); chars(h, "b"); endElement(h, "li"); endElement(h, "ol");
        });
    }

    @Test
    public void testDeeplyNestedElementsDoNotThrow() {
        // Pathologically deep nesting: the renderer walks the tree recursively.
        renderNoThrow("blockquote x1000", h -> {
            for (int i = 0; i < 1000; i++) {
                startElement(h, "blockquote");
            }
            chars(h, "deep");
            for (int i = 0; i < 1000; i++) {
                endElement(h, "blockquote");
            }
        });
        renderNoThrow("div x2000", h -> {
            for (int i = 0; i < 2000; i++) {
                startElement(h, "div");
            }
            chars(h, "deep");
            for (int i = 0; i < 2000; i++) {
                endElement(h, "div");
            }
        });
    }

    @RepeatedTest(200)
    public void fuzzRandomCharacterContent() {
        // The handler must not throw on arbitrary character content on its own --
        // control chars, null bytes, unpaired surrogates, non-characters. It does NOT
        // rely on SafeContentHandler having sanitized the stream: that runs in the parse
        // pipeline, but this handler is public API and may be used directly.
        Random rng = new Random();
        String text = randomText(rng);

        assertDoesNotThrow(() -> {
            ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
            handler.startDocument();
            startElement(handler, "p");
            chars(handler, text);
            endElement(handler, "p");
            startElement(handler, "table");
            startElement(handler, "tr");
            startElement(handler, "td");
            chars(handler, text);
            endElement(handler, "td");
            endElement(handler, "tr");
            endElement(handler, "table");
            AttributesImpl atts = new AttributesImpl();
            atts.addAttribute("", "src", "src", "CDATA", text);
            atts.addAttribute("", "alt", "alt", "CDATA", text);
            startElement(handler, "p");
            startElement(handler, "a", "href", text);
            chars(handler, text);
            endElement(handler, "a");
            startElement(handler, "img", atts);
            endElement(handler, "img");
            endElement(handler, "p");
            handler.endDocument();
            return handler.toString();
        }, () -> "StringWriter path threw for input: " + describe(text));

        assertDoesNotThrow(() -> {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            ToMarkdownContentHandler handler = new ToMarkdownContentHandler(bos, "UTF-8");
            handler.startDocument();
            startElement(handler, "p");
            chars(handler, text);
            endElement(handler, "p");
            handler.endDocument();
        }, () -> "OutputStream(UTF-8) path threw for input: " + describe(text));
    }

    /** Random string spanning the full BMP: control chars, surrogates, non-characters. */
    private static String randomText(Random rng) {
        char[] buf = new char[rng.nextInt(32)];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (char) rng.nextInt(0x10000);
        }
        return new String(buf);
    }

    /** Hex-escapes a string so a fuzz failure reports the exact offending input. */
    private static String describe(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 6);
        for (int i = 0; i < s.length(); i++) {
            sb.append(String.format("\\u%04x", (int) s.charAt(i)));
        }
        return sb.toString();
    }
}
