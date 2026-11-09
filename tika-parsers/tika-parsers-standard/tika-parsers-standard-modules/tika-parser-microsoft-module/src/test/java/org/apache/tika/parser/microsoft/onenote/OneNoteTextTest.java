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
package org.apache.tika.parser.microsoft.onenote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class OneNoteTextTest {

    @Test
    public void testClean() {
        assertEquals("Untitled picture.png", OneNoteText.clean("Untitled picture.png\u0000"));
        assertEquals("Answer:\n\n", OneNoteText.clean("Answer:\u000B\u000B"));
        assertEquals("Pages view:\nCamping\n", OneNoteText.clean("Pages view:\r\nCamping\r"));
        assertEquals("a b", OneNoteText.clean("a \uFFFCb"));
        assertNull(OneNoteText.clean("\uFFFC\uFFFC"));
        assertNull(OneNoteText.clean("\u0000"));
        assertNull(OneNoteText.clean(" \u00A0\u000B"));
        assertNull(OneNoteText.clean(null));
    }

    @Test
    public void testExtendedAscii() {
        assertEquals("\u00DCbung Th\u00E9venin\u00A0",
                new String(new byte[]{(byte) 0xDC, 'b', 'u', 'n', 'g', ' ', 'T', 'h', (byte) 0xE9,
                        'v', 'e', 'n', 'i', 'n', (byte) 0xA0}, OneNoteText.EXTENDED_ASCII));
    }

    @Test
    public void testBestInkAlternate() {
        assertEquals("algebras,", OneNoteText.bestInkAlternate(
                "algebras,\u0000algebras\u0000alphas,\u0000\u0000"));
        assertNull(OneNoteText.bestInkAlternate("\u0000\u0000"));
    }

    @Test
    public void testInkReplacesPlaceholders() throws SAXException {
        // ink only: the words share one span, punctuation kept against its word
        assertEquals("<p><span class=\"handwriting\">This is a test!</span></p>",
                body(x -> OneNoteText.emitRichText(x, "\uFFFC\uFFFC\uFFFC\uFFFC\uFFFC", null,
                        List.of("This", "is", "a", "test", "!"))));
        // mixed with typed text, in place
        assertEquals("<p>Call <span class=\"handwriting\">Bernard</span> at noon</p>",
                body(x -> OneNoteText.emitRichText(x, "Call \uFFFC at noon", null,
                        List.of("Bernard"))));
        // unrecognized ink leaves nothing to emit
        assertEquals("", body(x -> assertFalse(
                OneNoteText.emitRichText(x, "\uFFFC\uFFFC", null, List.of()))));
    }

    @Test
    public void testHyperlinkKeepsParagraphText() throws SAXException {
        String field = "﷟HYPERLINK \"http://x\"";
        String text = "Read the " + field + "topic on the site";
        // the field code and the link text are runs flagged as hyperlink
        List<OneNoteText.TextRun> runs = OneNoteText.textRuns(
                new int[]{9, 9 + field.length(), 9 + field.length() + 5},
                List.of(false, true, true, false));
        assertEquals("<p>Read the <a href=\"http://x\">topic</a> on the site</p>",
                body(x -> OneNoteText.emitRichText(x, text, runs, List.of())));
        // without runs a link reaches the next field
        assertEquals("<p>Visit <a href=\"http://a\">A and </a><a href=\"http://b\">B</a></p>",
                body(x -> OneNoteText.emitRichText(x, "Visit ﷟HYPERLINK \"http://a\"A and " +
                        "﷟HYPERLINK \"http://b\"B", null, List.of())));
        assertNull(OneNoteText.textRuns(new int[]{4}, List.of(true)));
        assertArrayEquals(new int[]{84, 300}, OneNoteText.textRunEnds(
                new byte[]{84, 0, 0, 0, 44, 1, 0, 0}));
    }

    @Test
    public void testImage() throws SAXException {
        assertEquals("<p><img src=\"embedded:a.png\" alt=\"Play button\" /></p>",
                body(x -> OneNoteText.emitImage(x, "a.png\u0000", "Play button\r\n")));
        // OneNote's OCR of a printout is body text, not alt text
        assertEquals("<p><img src=\"embedded:b.png\" /></p><div class=\"ocr\">Name:\nDate:</div>",
                body(x -> OneNoteText.emitImage(x, "b.png",
                        "Machine generated alternative text:\r\nName:\r\nDate:\r\n")));
        assertEquals("<p><img src=\"embedded:c.png\" /></p><div class=\"ocr\">Nom</div>",
                body(x -> OneNoteText.emitImage(x, "c.png",
                        "Texte de remplacement g\u00E9n\u00E9r\u00E9 par une machine\u00A0:\r\nNom")));
        assertEquals("<p><img src=\"embedded:d.png\" /></p><div class=\"ocr\">\u540D\u524D</div>",
                body(x -> OneNoteText.emitImage(x, "d.png", "\u81EA\u52D5\u751F\u6210\u3055\u308C\u305F\u4EE3\u66FF\u30C6\u30AD\u30B9\u30C8: \u540D\u524D")));
    }

    private interface Emitter {
        void emit(XHTMLContentHandler xhtml) throws SAXException;
    }

    private static String body(Emitter emitter) throws SAXException {
        ToXMLContentHandler handler = new ToXMLContentHandler();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, new Metadata(),
                new ParseContext());
        xhtml.startDocument();
        emitter.emit(xhtml);
        xhtml.endDocument();
        String xml = handler.toString();
        if (!xml.contains("</body>")) {
            return "";
        }
        // XHTMLContentHandler ends each block with a newline
        return xml.substring(xml.indexOf("<body>") + 6, xml.indexOf("</body>")).strip()
                .replace("</p>\n", "</p>");
    }
}
