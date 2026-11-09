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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * @return the text with NUL terminators and U+FFFC object placeholders removed and vertical
     * tabs (OneNote soft line breaks) and CR/CRLF (alt text) as newlines, or {@code null} if only
     * spaces are left
     */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        String s = normalize(text);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                return s;
            }
        }
        return null;
    }

    private static String normalize(String text) {
        return text.replace("\u0000", "").replace("\uFFFC", "").replace('\u000B', '\n')
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

    /** One run of a paragraph's text: where it ends, and whether it is hyperlink text. */
    public record TextRun(int end, boolean hyperlink) {
    }

    private static final Pattern HYPERLINK_FIELD =
            Pattern.compile("\uFDDFHYPERLINK\\s+\"([^\"]*)\"");

    /** @return whether the paragraph needs {@link #emitRichText} rather than plain text */
    public static boolean isRichText(String text, List<String> inkWords) {
        return !inkWords.isEmpty() || text.indexOf('\uFDDF') >= 0;
    }

    /**
     * Emits a paragraph whose U+FFFC placeholders stand for inline ink, replaced in order with
     * the recognized words in {@code <span class="handwriting">}, and whose hyperlink fields
     * become {@code <a href>}. OneNote does not delimit a link's text; it is the hyperlink runs
     * after the field code, or, with no runs, the text up to the next field.
     *
     * @param runs the paragraph's text runs, or null if it has none
     * @return whether anything was emitted
     */
    public static boolean emitRichText(XHTMLContentHandler xhtml, String text, List<TextRun> runs,
                                List<String> inkWords) throws SAXException {
        List<Segment> linked = linkSegments(text, runs == null || runs.isEmpty() ? null : runs);
        List<Segment> segments = new ArrayList<>();
        int next = 0;
        for (Segment segment : linked) {
            if (segment.url != null) {
                add(segments, Segment.LINK, segment.text.toString(), segment.url);
                continue;
            }
            StringBuilder typed = new StringBuilder();
            String s = segment.text.toString();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c != '\uFFFC') {
                    typed.append(c);
                } else if (next < inkWords.size()) {
                    add(segments, Segment.TEXT, typed.toString(), null);
                    typed.setLength(0);
                    appendInk(segments, inkWords.get(next++));
                }
            }
            add(segments, Segment.TEXT, typed.toString(), null);
        }
        while (next < inkWords.size()) {
            appendInk(segments, inkWords.get(next++));
        }
        boolean any = false;
        for (int i = 0; i < segments.size() && !any; i++) {
            any = clean(segments.get(i).text.toString()) != null;
        }
        if (!any) {
            return false;
        }
        xhtml.startElement("p");
        for (Segment segment : segments) {
            String s = normalize(segment.text.toString());
            if (s.isEmpty()) {
                continue;
            }
            if (segment.kind == Segment.INK) {
                xhtml.startElement("span", "class", "handwriting");
                xhtml.characters(s);
                xhtml.endElement("span");
            } else if (segment.kind == Segment.LINK && clean(s) != null) {
                xhtml.startElement("a", "href", segment.url);
                xhtml.characters(s);
                xhtml.endElement("a");
            } else {
                xhtml.characters(s);
            }
        }
        xhtml.endElement("p");
        return true;
    }

    /**
     * Drops hyperlink field codes and links the text after each: while its runs are flagged as
     * hyperlink, or, with no runs, up to the next field.
     */
    private static List<Segment> linkSegments(String text, List<TextRun> runs) {
        List<Segment> segments = new ArrayList<>();
        Matcher m = HYPERLINK_FIELD.matcher(text);
        int nextField = m.find() ? m.start() : -1;
        String url = null;
        int run = 0;
        StringBuilder pending = new StringBuilder();
        String pendingUrl = null;
        for (int i = 0; i < text.length(); i++) {
            if (i == nextField) {
                i = m.end() - 1;
                url = m.group(1).isEmpty() ? null : m.group(1);
                nextField = m.find() ? m.start() : -1;
                continue;
            }
            if (runs != null) {
                while (run < runs.size() - 1 && i >= runs.get(run).end()) {
                    run++;
                }
                if (!runs.get(run).hyperlink()) {
                    url = null;
                }
            }
            if (!Objects.equals(url, pendingUrl)) {
                addLinked(segments, pending.toString(), pendingUrl);
                pending.setLength(0);
                pendingUrl = url;
            }
            pending.append(text.charAt(i));
        }
        addLinked(segments, pending.toString(), pendingUrl);
        return segments;
    }

    private static void addLinked(List<Segment> segments, String text, String url) {
        add(segments, url == null ? Segment.TEXT : Segment.LINK, text, url);
    }

    private static void add(List<Segment> segments, int kind, String text, String url) {
        if (text.isEmpty()) {
            return;
        }
        Segment last = segments.isEmpty() ? null : segments.get(segments.size() - 1);
        if (last != null && last.kind == kind && Objects.equals(last.url, url)) {
            last.text.append(text);
        } else {
            segments.add(new Segment(kind, text, url));
        }
    }

    private static void appendInk(List<Segment> segments, String word) {
        Segment last = segments.isEmpty() ? null : segments.get(segments.size() - 1);
        if (last == null || last.kind != Segment.INK) {
            segments.add(new Segment(Segment.INK, word, null));
        } else {
            // recognized punctuation is its own word; keep it against the previous one
            if (!word.matches("[.,;:!?)\\]}]+")) {
                last.text.append(' ');
            }
            last.text.append(word);
        }
    }

    private static final class Segment {
        static final int TEXT = 0;
        static final int INK = 1;
        static final int LINK = 2;

        final int kind;
        final StringBuilder text;
        final String url;

        Segment(int kind, String text, String url) {
            this.kind = kind;
            this.text = new StringBuilder(text);
            this.url = url;
        }
    }

    /** @return the run ends in a TextRunIndex property: little-endian uint32 values */
    public static int[] textRunEnds(byte[] data) {
        int[] ends = new int[data.length / 4];
        for (int i = 0; i < ends.length; i++) {
            ends[i] = (data[4 * i] & 0xFF) | (data[4 * i + 1] & 0xFF) << 8 |
                    (data[4 * i + 2] & 0xFF) << 16 | (data[4 * i + 3] & 0xFF) << 24;
        }
        return ends;
    }

    /**
     * @param ends the TextRunIndex run ends; the last run has no entry and ends with the text
     * @param hyperlink per run, from its TextRunFormatting style
     * @return the runs, or null if the counts disagree
     */
    public static List<TextRun> textRuns(int[] ends, List<Boolean> hyperlink) {
        if (hyperlink.size() != ends.length + 1) {
            return null;
        }
        List<TextRun> runs = new ArrayList<>();
        for (int i = 0; i < hyperlink.size(); i++) {
            runs.add(new TextRun(i < ends.length ? ends[i] : Integer.MAX_VALUE, hyperlink.get(i)));
        }
        return runs;
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

    public static boolean isExtendedAscii(OneNotePropertyEnum property) {
        return property == OneNotePropertyEnum.TextExtendedAscii ||
                property == OneNotePropertyEnum.TextExtendedAscii2007;
    }

    public static boolean isImageText(OneNotePropertyEnum property) {
        return property == OneNotePropertyEnum.ImageFilename ||
                property == OneNotePropertyEnum.ImageAltText;
    }
}
