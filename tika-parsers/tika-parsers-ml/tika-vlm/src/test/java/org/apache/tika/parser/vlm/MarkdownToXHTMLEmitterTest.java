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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

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
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><root/>", xml);
    }

    @Test
    void testNullInput() throws Exception {
        String xml = emit(null);
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><root/>", xml);
    }

    /**
     * Emit markdown through the emitter, wrapping in a root element so
     * the SAX output is well-formed XML we can assert against.
     */
    private String emit(String markdown) throws Exception {
        StringWriter sw = new StringWriter();
        SAXTransformerFactory tf =
                (SAXTransformerFactory) TransformerFactory.newInstance();
        TransformerHandler th = tf.newTransformerHandler();
        th.getTransformer().setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        th.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
        th.setResult(new StreamResult(sw));

        th.startDocument();
        th.startElement("", "root", "root", new org.xml.sax.helpers.AttributesImpl());
        MarkdownToXHTMLEmitter.emit(markdown, th);
        th.endElement("", "root", "root");
        th.endDocument();

        return sw.toString();
    }

    private static void assertContains(String needle, String haystack) {
        assertTrue(haystack.contains(needle),
                "Expected to find [" + needle + "] in:\n" + haystack);
    }
}
