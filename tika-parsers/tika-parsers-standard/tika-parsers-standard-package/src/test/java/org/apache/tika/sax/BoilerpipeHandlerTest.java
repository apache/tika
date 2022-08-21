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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.io.Writer;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.boilerpipe.BoilerpipeContentHandler;

public class BoilerpipeHandlerTest extends TikaTest {
    /**
     * Test case for TIKA-420
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-420">TIKA-420</a>
     */
    @Test
    public void testBoilerplateRemoval() throws Exception {
        String path = "/test-documents/boilerplate.html";

        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();
        new HtmlParser()
                .parse(getResourceAsStream(path), new BoilerpipeContentHandler(handler), metadata,
                        new ParseContext());

        String content = handler.toString();
        assertTrue(content.startsWith("This is the real meat"));
        assertTrue(content.endsWith("This is the end of the text.\n"));
        assertFalse(content.contains("boilerplate"));
        assertFalse(content.contains("footer"));
    }

    /**
     * Test case for TIKA-564. Support returning markup from BoilerpipeContentHandler.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-564">TIKA-564</a>
     */
    @Test
    public void testBoilerplateWithMarkup() throws Exception {
        String path = "/test-documents/boilerplate.html";

        Metadata metadata = new Metadata();
        StringWriter sw = new StringWriter();
        ContentHandler ch = makeHtmlTransformer(sw);
        BoilerpipeContentHandler bpch = new BoilerpipeContentHandler(ch);
        bpch.setIncludeMarkup(true);

        new HtmlParser().parse(getResourceAsStream(path), bpch, metadata, new ParseContext());

        String content = sw.toString();
        assertTrue(content.contains("<body><table><tr><td><table><tr><td>"),
                "Has empty table elements");
        assertTrue(content.contains("<a shape=\"rect\" href=\"Main.php\"/>"), "Has empty a element");
        assertTrue(content.contains("<p>This is the real meat"), "Has real content");
        assertTrue(content.endsWith("</p></body></html>"), "Ends with appropriate HTML");
        assertFalse(content.contains("boilerplate"));
        assertFalse(content.contains("footer"));
    }

    /**
     * Test case for TIKA-961
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-961">TIKA-961</a>
     */
    @Test
    public void testBoilerplateWhitespace() throws Exception {
        String path = "/test-documents/boilerplate-whitespace.html";

        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();

        BoilerpipeContentHandler bpHandler = new BoilerpipeContentHandler(handler);
        bpHandler.setIncludeMarkup(true);

        new HtmlParser().parse(getResourceAsStream(path), bpHandler, metadata, new ParseContext());

        String content = handler.toString();

        // Should not contain item_aitem_b
        assertFalse(content.contains("item_aitem_b"));

        // Should contain the two list items with a newline in between.
        assertContains("item_a\nitem_b", content);

        // Should contain 有什么需要我帮你的 (can i help you) without whitespace
        assertContains("有什么需要我帮你的", content);
    }

    /**
     * Test case for TIKA-2683
     *
     * @see <a href="https://issues.apache.org/jira/projects/TIKA/issues/TIKA-2683">TIKA-2683</a>
     */
    @Test
    public void testBoilerplateMissingWhitespace() throws Exception {
        String path = "/test-documents/testBoilerplateMissingSpace.html";

        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();

        BoilerpipeContentHandler bpHandler = new BoilerpipeContentHandler(handler);
        bpHandler.setIncludeMarkup(true);

        new HtmlParser().parse(getResourceAsStream(path), bpHandler, metadata, new ParseContext());

        String content = handler.toString();

        // Should contain space between these two words as mentioned in HTML
        assertContains("family Psychrolutidae", content);

        // Shouldn't add new-line chars around brackets; This is not how the HTML look
        assertContains("(Psychrolutes marcidus)", content);
    }

    /**
     * Create ContentHandler that transforms SAX events into textual HTML output,
     * and writes it out to <writer> - typically this is a StringWriter.
     *
     * @param writer Where to write resulting HTML text.
     * @return ContentHandler suitable for passing to parse() methods.
     * @throws Exception
     */
    private ContentHandler makeHtmlTransformer(Writer writer) throws Exception {
        SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "html");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
        handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, "utf-8");
        handler.setResult(new StreamResult(writer));
        return handler;
    }
}
