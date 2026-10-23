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
package org.apache.tika.parser.vlm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.xml.secure.SecureTransformerFactory;
import org.junit.jupiter.api.Test;

public class MarkdownToXHTMLEmitterTest {

    @Test
    void testHeadings() throws Exception {
        String md = "# Heading 1\n\n## Heading 2\n\n### Heading 3\n";
        String xml = emit(md);
        assertContains("<h1>Heading 1</h1>", xml);
        assertContains("<h2>Heading 2</h2>", xml);
        assertContains("<h3>Heading 3</h3>", xml);
    }

    @Test
    void testDroppedElementsStillSeparateText() throws Exception {
        String split = "<table>\n<tr><td>North</td><td>120</td></tr>\n\n<tr><td>South</td><td>99</td></tr>\n</table>";
        String xml = emit(split);
        assertContains("<td>North</td>", xml);
        assertContains("South 99", xml);
        assertContains("k v", emit("<dl><dt>k</dt><dd>v</dd></dl>"));
        assertContains("a b", emit("<div>a</div><div>b</div>"));
    }

    @Test
    void testParagraph() throws Exception {
        String md = "Hello world.\n\nSecond paragraph.";
        String xml = emit(md);
        assertContains("<p>Hello world.</p>", xml);
        assertContains("<p>Second paragraph.</p>", xml);
    }

    @Test
    void testBoldAndItalic() throws Exception {
        String md = "This is **bold** and *italic* text.";
        String xml = emit(md);
        assertContains("<b>bold</b>", xml);
        assertContains("<i>italic</i>", xml);
    }

    @Test
    void testStrikethrough() throws Exception {
        String md = "This is ~~deleted~~ text.";
        String xml = emit(md);
        assertContains("<s>deleted</s>", xml);
    }

    @Test
    void testLink() throws Exception {
        String md = "Visit [Apache Tika](https://tika.apache.org) today.";
        String xml = emit(md);
        assertContains("<a href=\"https://tika.apache.org\">Apache Tika</a>", xml);
    }

    @Test
    void testImage() throws Exception {
        String md = "![alt text](https://example.com/img.png)";
        String xml = emit(md);
        assertContains("src=\"https://example.com/img.png\"", xml);
        assertContains("alt=\"alt text\"", xml);
    }

    @Test
    void testUnorderedList() throws Exception {
        String md = "- item one\n- item two\n- item three\n";
        String xml = emit(md);
        assertContains("<ul>", xml);
        assertContains("<li>item one</li>", xml);
        assertContains("<li>item two</li>", xml);
        assertContains("<li>item three</li>", xml);
        assertContains("</ul>", xml);
    }

    @Test
    void testOrderedList() throws Exception {
        String md = "1. first\n2. second\n3. third\n";
        String xml = emit(md);
        assertContains("<ol>", xml);
        assertContains("<li>first</li>", xml);
        assertContains("<li>second</li>", xml);
        assertContains("<li>third</li>", xml);
        assertContains("</ol>", xml);
    }

    @Test
    void testBlockquote() throws Exception {
        String md = "> This is quoted text.\n";
        String xml = emit(md);
        assertContains("<blockquote>", xml);
        assertContains("This is quoted text.", xml);
        assertContains("</blockquote>", xml);
    }

    @Test
    void testFencedCodeBlock() throws Exception {
        String md = "```python\nprint('hello')\n```\n";
        String xml = emit(md);
        assertContains("<pre>", xml);
        assertContains("<code", xml);
        assertContains("language-python", xml);
        assertContains("print('hello')", xml);
        assertContains("</code>", xml);
        assertContains("</pre>", xml);
    }

    @Test
    void testIndentedCodeBlock() throws Exception {
        String md = "    int x = 1;\n    int y = 2;\n";
        String xml = emit(md);
        assertContains("<pre>", xml);
        assertContains("<code>", xml);
        assertContains("int x = 1;", xml);
        assertContains("</code>", xml);
        assertContains("</pre>", xml);
    }

    @Test
    void testInlineCode() throws Exception {
        String md = "Use the `parse()` method.";
        String xml = emit(md);
        assertContains("<code>parse()</code>", xml);
    }

    @Test
    void testThematicBreak() throws Exception {
        String md = "Above\n\n---\n\nBelow";
        String xml = emit(md);
        assertContains("<hr/>", xml);
    }

    @Test
    void testGfmTable() throws Exception {
        String md = "| Name | Age |\n| --- | --- |\n| Alice | 30 |\n| Bob | 25 |\n";
        String xml = emit(md);
        assertContains("<table>", xml);
        assertContains("<thead>", xml);
        assertContains("<th>Name</th>", xml);
        assertContains("<th>Age</th>", xml);
        assertContains("</thead>", xml);
        assertContains("<tbody>", xml);
        assertContains("<td>Alice</td>", xml);
        assertContains("<td>30</td>", xml);
        assertContains("<td>Bob</td>", xml);
        assertContains("<td>25</td>", xml);
        assertContains("</tbody>", xml);
        assertContains("</table>", xml);
    }

    @Test
    void testTableAlignment() throws Exception {
        String md = "| Left | Center | Right |\n| :--- | :---: | ---: |\n| a | b | c |\n";
        String xml = emit(md);
        assertContains("align=\"left\"", xml);
        assertContains("align=\"center\"", xml);
        assertContains("align=\"right\"", xml);
    }

    @Test
    void testHardLineBreak() throws Exception {
        String md = "line one  \nline two\n";
        String xml = emit(md);
        assertContains("<br/>", xml);
    }

    @Test
    void testNestedList() throws Exception {
        String md = "- outer\n  - inner\n- outer2\n";
        String xml = emit(md);
        // Should have a nested ul inside li
        assertContains("<ul>", xml);
        assertContains("<li>outer", xml);
        assertContains("<li>inner</li>", xml);
        assertContains("<li>outer2</li>", xml);
    }

    @Test
    void testComplexDocument() throws Exception {
        String md = "# Invoice\n\n"
                + "**Customer:** John Doe\n\n"
                + "| Item | Qty | Price |\n"
                + "| --- | --- | --- |\n"
                + "| Widget | 5 | $10.00 |\n"
                + "| Gadget | 2 | $25.00 |\n\n"
                + "## Notes\n\n"
                + "- Delivered on time\n"
                + "- No defects found\n";
        String xml = emit(md);
        assertContains("<h1>Invoice</h1>", xml);
        assertContains("<b>Customer:</b>", xml);
        assertContains("<table>", xml);
        assertContains("<th>Item</th>", xml);
        assertContains("<td>Widget</td>", xml);
        assertContains("<h2>Notes</h2>", xml);
        assertContains("<li>Delivered on time</li>", xml);
    }

    @Test
    void testEmptyInput() throws Exception {
        String xml = emit("");
        // Should produce just the root wrapper, no content elements
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<root xmlns=\"http://www.w3.org/1999/xhtml\"/>", xml);
    }

    @Test
    void testNullInput() throws Exception {
        String xml = emit(null);
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<root xmlns=\"http://www.w3.org/1999/xhtml\"/>", xml);
    }

    @Test
    void testHtmlTableBlock() throws Exception {
        // verbatim jina-ocr-v1 output for a three-column table followed by a sentence
        String md = "<table>\n<thead>\n  <tr class=\"table-header\">\n    <th>Region</th>\n"
                + "    <th>Q1</th>\n    <th>Q2</th>\n  </tr>\n</thead>\n<tbody>\n"
                + "  <tr class=\"row-odd\">\n    <th>North</th>\n    <th>120</th>\n"
                + "    <th>135</th>\n  </tr>\n</tbody>\n</table>\n\n"
                + "Totals are in thousands of units.\n";
        String xml = emit(md);
        assertContains("<table><thead><tr><th>Region</th><th>Q1</th><th>Q2</th></tr></thead>"
                + "<tbody><tr><th>North</th><th>120</th><th>135</th></tr></tbody></table>", xml);
        assertContains("<p>Totals are in thousands of units.</p>", xml);
        assertFalse(xml.contains("&lt;"), "raw tags leaked as text:\n" + xml);
        assertFalse(xml.contains("class="), "attributes should be dropped:\n" + xml);
    }

    @Test
    void testHtmlBlockUnknownElementsKeepTextDropScripts() throws Exception {
        String md = "<div class=\"x\"><span>hello</span> <strong>there</strong>"
                + "<script>alert(1)</script><style>p{}</style> a &amp; b</div>\n";
        String xml = emit(md);
        assertContains("hello <b>there</b> a &amp; b", xml);
        assertFalse(xml.contains("alert"), xml);
        assertFalse(xml.contains("p{}"), xml);
        assertFalse(xml.contains("<div"), xml);
        assertFalse(xml.contains("<span"), xml);
    }

    @Test
    void testHtmlTableKeepsColspanRowspan() throws Exception {
        String md = "<table><tr><th colspan=\"2\" class=\"hdr\">Sales</th></tr>"
                + "<tr><td rowspan=\"1\" colspan=\"0\" style=\"x\">a</td>"
                + "<td rowspan=\" 3 \">b</td></tr></table>\n";
        String xml = emit(md);
        assertContains("<th colspan=\"2\">Sales</th>", xml);
        assertContains("<td>a</td>", xml);
        assertContains("<td rowspan=\"3\">b</td>", xml);
        assertFalse(xml.contains("class="), xml);
        assertFalse(xml.contains("style="), xml);
    }

    @Test
    void testHtmlBlockUnclosedTable() throws Exception {
        String md = "<table><tr><td>a<td>b\n";
        String xml = emit(md);
        assertContains("<table><tbody><tr><td>a</td><td>b</td></tr></tbody></table>", xml);
    }

    @Test
    void testInlineHtmlTagsDroppedTextKept() throws Exception {
        String md = "This is <b>bold</b>, x<sup>2</sup> and a<br>break.";
        String xml = emit(md);
        assertContains("<p>This is bold, x2 and a<br/>break.</p>", xml);
    }

    private static final String XHTML_NS = "http://www.w3.org/1999/xhtml";

    /**
     * Emit markdown through the emitter, wrapping in a root element in the
     * XHTML namespace so emitter-emitted elements inherit the namespace and
     * the serializer doesn't redeclare xmlns on every child.
     */
    private String emit(String markdown) throws Exception {
        StringWriter sw = new StringWriter();
        SAXTransformerFactory tf =
                (SAXTransformerFactory) SecureTransformerFactory.newInstance();
        TransformerHandler th = tf.newTransformerHandler();
        th.getTransformer().setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        th.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
        th.setResult(new StreamResult(sw));

        th.startDocument();
        th.startElement(XHTML_NS, "root", "root", new org.xml.sax.helpers.AttributesImpl());
        MarkdownToXHTMLEmitter.emit(markdown, th);
        th.endElement(XHTML_NS, "root", "root");
        th.endDocument();

        return sw.toString();
    }

    private static void assertContains(String needle, String haystack) {
        assertTrue(haystack.contains(needle),
                "Expected to find [" + needle + "] in:\n" + haystack);
    }
}
