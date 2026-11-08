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

    /**
     * MS-ONE TextExtendedAscii. OneNote writes it only for text that fits Latin-1, whatever
     * the paragraph's language ID (zh-CN and ja text holds NBSP and U+00D7; cs and ru text
     * holds nothing that windows-1250/1251 would read differently), so the code page is not
     * taken from the LCID.
     */
    public static final Charset EXTENDED_ASCII = Charset.forName("windows-1252");

    /**
     * OneNote's localized prefixes for its own OCR of a picture or file printout, stored as
     * alt text; those seen in the wild, compared after {@link #normalizeLabel}.
     */
    static final List<String> MACHINE_ALT_TEXT_LABELS = List.of(
            "Machine generated alternative text:",
            "Texte de remplacement g\u00E9n\u00E9r\u00E9 par une machine :",
            "\u81EA\u52D5\u751F\u6210\u3055\u308C\u305F\u4EE3\u66FF\u30C6\u30AD\u30B9\u30C8:");

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
        int ocrStart = altText == null ? -1 : machineAltTextStart(altText);
        if (ocrStart >= 0) {
            ocr = clean(altText.substring(ocrStart).strip());
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

    /** @return where the OCR text after OneNote's label starts, or -1 if there is no label */
    static int machineAltTextStart(String altText) {
        String normalized = normalizeLabel(altText);
        for (String label : MACHINE_ALT_TEXT_LABELS) {
            if (normalized.startsWith(label)) {
                // normalizeLabel keeps lengths, so the offset holds in the original
                return label.length();
            }
        }
        return -1;
    }

    private static String normalizeLabel(String s) {
        return s.replace('\u00A0', ' ').replace('\uFF1A', ':');
    }

    public static boolean isImageText(OneNotePropertyEnum property) {
        return property == OneNotePropertyEnum.ImageFilename ||
                property == OneNotePropertyEnum.ImageAltText;
    }
}
