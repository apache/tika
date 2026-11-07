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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.sax.XHTMLContentHandler;

/** Text clean-up and markup shared by the .one and FSSHTTPB walkers. */
public final class OneNoteText {

    /** MS-ONE TextExtendedAscii holds code-page bytes; Western text is windows-1252. */
    public static final Charset EXTENDED_ASCII = Charset.forName("windows-1252");

    /** OneNote's prefix for its own OCR of a picture or file printout, stored as alt text. */
    static final String MACHINE_ALT_TEXT = "Machine generated alternative text:";

    private OneNoteText() {
    }

    /**
     * @return the text with NUL terminators removed and vertical tabs (OneNote soft line
     * breaks) and CR/CRLF (alt text) as newlines, or {@code null} if nothing but spaces and U+FFFC
     * object-placeholders is left
     */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        String s = normalize(text);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\uFFFC' && !Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                return s;
            }
        }
        return null;
    }

    private static String normalize(String text) {
        return text.replace("\u0000", "").replace('\u000B', '\n')
                .replace("\r\n", "\n").replace('\r', '\n');
    }

    /** @return the best handwriting-recognition alternate, or null if there is none */
    static String bestInkAlternate(String alternates) {
        for (String alternate : alternates.split("\u0000")) {
            if (!alternate.isBlank()) {
                return alternate.strip();
            }
        }
        return null;
    }

    /**
     * Emits a paragraph whose U+FFFC placeholders stand for inline ink, replacing them in
     * order with the recognized words in {@code <span class="handwriting">}.
     *
     * @return whether anything was emitted
     */
    static boolean emitParagraphWithInk(XHTMLContentHandler xhtml, String text,
                                        List<String> inkWords) throws SAXException {
        String s = normalize(text);
        // alternating runs: typed text at even indexes, handwriting at odd
        List<StringBuilder> runs = new ArrayList<>();
        runs.add(new StringBuilder());
        int next = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\uFFFC') {
                if (next < inkWords.size()) {
                    appendInk(runs, inkWords.get(next++));
                }
            } else {
                if (runs.size() % 2 == 0) {
                    runs.add(new StringBuilder());
                }
                runs.get(runs.size() - 1).append(c);
            }
        }
        while (next < inkWords.size()) {
            appendInk(runs, inkWords.get(next++));
        }
        boolean any = false;
        for (int i = 0; i < runs.size() && !any; i++) {
            any = clean(runs.get(i).toString()) != null;
        }
        if (!any) {
            return false;
        }
        xhtml.startElement("p");
        for (int i = 0; i < runs.size(); i++) {
            String run = runs.get(i).toString();
            if (run.isEmpty()) {
                continue;
            }
            if (i % 2 == 0) {
                xhtml.characters(run);
            } else {
                xhtml.startElement("span", "class", "handwriting");
                xhtml.characters(run);
                xhtml.endElement("span");
            }
        }
        xhtml.endElement("p");
        return true;
    }

    private static void appendInk(List<StringBuilder> runs, String word) {
        if (runs.size() % 2 == 1) {
            runs.add(new StringBuilder());
        }
        StringBuilder ink = runs.get(runs.size() - 1);
        // recognized punctuation is its own word; keep it against the previous one
        if (ink.length() > 0 && !word.matches("[.,;:!?)\\]}]+")) {
            ink.append(' ');
        }
        ink.append(word);
    }

    /**
     * Emits a picture as {@code <p><img src="embedded:fileName" alt="altText"/></p>}, leaving out
     * whichever is missing; OneNote's own OCR alt text goes in a following
     * {@code <div class="ocr">} instead, as body text.
     *
     * @return whether an element was emitted
     */
    public static boolean emitImage(XHTMLContentHandler xhtml, String fileName, String altText)
            throws SAXException {
        fileName = fileName == null ? null : clean(fileName.strip());
        altText = altText == null ? null : clean(altText.strip());
        String ocr = null;
        if (altText != null && altText.startsWith(MACHINE_ALT_TEXT)) {
            ocr = clean(altText.substring(MACHINE_ALT_TEXT.length()).strip());
            altText = null;
        }
        if (fileName == null && altText == null && ocr == null) {
            return false;
        }
        AttributesImpl atts = new AttributesImpl();
        if (fileName != null) {
            atts.addAttribute("", "src", "src", "CDATA", "embedded:" + fileName);
        }
        if (altText != null) {
            atts.addAttribute("", "alt", "alt", "CDATA", altText);
        }
        // its own paragraph, so Markdown does not run it into the next inline element
        if (fileName != null || altText != null) {
            xhtml.startElement("p");
            xhtml.startElement("img", atts);
            xhtml.endElement("img");
            xhtml.endElement("p");
        }
        if (ocr != null) {
            xhtml.startElement("div", "class", "ocr");
            xhtml.characters(ocr);
            xhtml.endElement("div");
        }
        return true;
    }

    public static boolean isImageText(OneNotePropertyEnum property) {
        return property == OneNotePropertyEnum.ImageFilename ||
                property == OneNotePropertyEnum.ImageAltText;
    }
}
