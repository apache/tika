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
package org.apache.tika.parser.subtitle;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for WebVTT ({@code text/vtt}) and SubRip ({@code application/x-subrip}) subtitles.
 * Each cue becomes {@code <div class="cue" data-start data-end data-id>} with its text in
 * {@code <p>}; times are normalized to {@code HH:MM:SS.mmm}. WebVTT {@code NOTE} blocks become
 * {@code <div class="note">}, a header {@code Language:} sets {@code dc:language}; styling
 * markup, cue settings and {@code STYLE}/{@code REGION} blocks are dropped. Lenient about the
 * common SubRip defects: missing blank lines between cues and blank lines inside a cue.
 */
@TikaComponent
public class SubtitleParser extends AbstractEncodingDetectorParser {

    private static final long serialVersionUID = 4127915370255834561L;

    static final MediaType VTT = MediaType.text("vtt");
    static final MediaType SUBRIP = MediaType.application("x-subrip");

    private static final Set<MediaType> SUPPORTED_TYPES = Set.of(VTT, SUBRIP);

    // longer lines are split, not buffered: a hostile file may be one endless line
    static final int MAX_LINE_CHARS = 64 * 1024;

    // [hh:]mm:ss[.,]mmm; lenient on digit counts and separator, as real SRT is
    private static final String TIME = "((?:\\d{1,9}:)?\\d{1,2}:\\d{1,2}(?:[.,:]\\d{1,3})?)";
    private static final Pattern TIMING =
            Pattern.compile("^\\s*" + TIME + "\\s*-->(?:\\s*" + TIME + ")?");
    private static final Pattern LANGUAGE_HEADER =
            Pattern.compile("Language\\s*:\\s*(\\S.{0,63})", Pattern.CASE_INSENSITIVE);

    // WebVTT escapes '<', so every '<' opens a tag (class, voice, lang, ruby, timestamp)
    private static final Pattern VTT_TAG = Pattern.compile("<[^>]*>?");
    // SubRip has no escaping: strip only the tags players honor, keep a literal "a < b"
    private static final Pattern SRT_TAG =
            Pattern.compile("</?(?:i|b|u|s|font)(?:\\s[^<>]*)?>", Pattern.CASE_INSENSITIVE);
    // SSA override blocks some SubRip writers carry over, e.g. {\an8}
    private static final Pattern SSA_OVERRIDE = Pattern.compile("\\{\\\\[^{}]*}");
    private static final Pattern VTT_ENTITY =
            Pattern.compile("&(amp|lt|gt|nbsp|lrm|rlm|#\\d{1,7}|#[xX][0-9a-fA-F]{1,6});");

    public SubtitleParser() {
    }

    public SubtitleParser(EncodingDetector encodingDetector) {
        super(encodingDetector);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        MediaType declared = MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));
        MediaType type = declared != null && VTT.equals(declared.getBaseType()) ? VTT : SUBRIP;
        tis.setCloseShield();
        try (AutoDetectReader reader = new AutoDetectReader(tis, metadata,
                getEncodingDetector(context))) {
            Charset charset = reader.getCharset();
            metadata.set(HttpHeaders.CONTENT_TYPE, new MediaType(type, charset).toString());
            metadata.set(HttpHeaders.CONTENT_ENCODING, charset.name());

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            try {
                new CueWriter(xhtml, metadata, type == VTT).write(new LineReader(reader));
            } finally {
                xhtml.endDocument();
            }
        } finally {
            tis.removeCloseShield();
        }
    }

    static boolean isTiming(String line) {
        return line != null && line.contains("-->") && TIMING.matcher(line).find();
    }

    /** {@code HH:MM:SS.mmm} for a time {@link #TIMING} matched, or null if out of range. */
    static String normalizeTime(String raw) {
        String[] parts = raw.split("[:.,]");
        boolean fraction = raw.indexOf('.') >= 0 || raw.indexOf(',') >= 0 || parts.length == 4;
        int whole = fraction ? parts.length - 1 : parts.length;
        String ms = fraction ? (parts[parts.length - 1] + "00").substring(0, 3) : "000";
        long h = whole == 3 ? Long.parseLong(parts[0]) : 0;
        int m = Integer.parseInt(parts[whole - 2]);
        int s = Integer.parseInt(parts[whole - 1]);
        if (m > 59 || s > 59) {
            return null;
        }
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%s", h, m, s, ms);
    }

    static String clean(String line, boolean vtt) {
        if (vtt) {
            return decodeEntities(VTT_TAG.matcher(line).replaceAll(""));
        }
        return SSA_OVERRIDE.matcher(SRT_TAG.matcher(line).replaceAll("")).replaceAll("");
    }

    private static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        Matcher m = VTT_ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder(s.length());
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(entity(m.group(1), m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String entity(String name, String raw) {
        switch (name) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "nbsp":
                return " ";
            case "lrm":
                return "‎";
            case "rlm":
                return "‏";
            default:
                break;
        }
        boolean hex = name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X');
        int cp = Integer.parseInt(name.substring(hex ? 2 : 1), hex ? 16 : 10);
        boolean ok = cp > 0 && Character.isValidCodePoint(cp)
                && !(cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
        return ok ? new String(Character.toChars(cp)) : raw;
    }

    private static final class CueWriter {
        private enum Block { NONE, HEADER, NOTE, SKIP, CUE }

        private final XHTMLContentHandler xhtml;
        private final Metadata metadata;
        private final boolean vtt;
        private Block block = Block.NONE;
        private AttributesImpl pendingCue;
        private String pendingId;
        private boolean divOpen;
        private boolean pOpen;

        CueWriter(XHTMLContentHandler xhtml, Metadata metadata, boolean vtt) {
            this.xhtml = xhtml;
            this.metadata = metadata;
            this.vtt = vtt;
        }

        void write(LineReader lines) throws IOException, SAXException {
            boolean blockStart = true;
            boolean first = true;
            try {
                String line = lines.next();
                while (line != null) {
                    String next = lines.next();
                    if (first && line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                    if (line.isBlank()) {
                        closeP();
                        if (block != Block.CUE) {
                            closeDiv();
                            block = Block.NONE;
                        }
                        blockStart = true;
                    } else if (blockStart && vtt && startMetaBlock(line, first)) {
                        blockStart = false;
                    } else {
                        blockStart = false;
                        line(line, next);
                    }
                    first = false;
                    line = next;
                }
            } finally {
                closeP();
                closeDiv();
            }
        }

        private boolean startMetaBlock(String line, boolean first)
                throws SAXException {
            if (first && startsWithKeyword(line, "WEBVTT")) {
                block = Block.HEADER;
            } else if (startsWithKeyword(line, "NOTE")) {
                closeDiv();
                block = Block.NOTE;
                text(line.substring(4));
            } else if (startsWithKeyword(line, "STYLE") || startsWithKeyword(line, "REGION")) {
                closeDiv();
                block = Block.SKIP;
            } else {
                return false;
            }
            return true;
        }

        private void line(String line, String next) throws SAXException {
            switch (block) {
                case HEADER:
                    Matcher m = LANGUAGE_HEADER.matcher(line);
                    if (m.matches()) {
                        metadata.set(TikaCoreProperties.LANGUAGE, m.group(1).strip());
                    }
                    return;
                case SKIP:
                    return;
                case NOTE:
                    text(line);
                    return;
                default:
                    break;
            }
            Matcher timing = TIMING.matcher(line);
            if (line.contains("-->") && timing.find()) {
                closeDiv();
                block = Block.CUE;
                pendingCue = new AttributesImpl();
                pendingCue.addAttribute("", "class", "class", "CDATA", "cue");
                addTime("data-start", timing.group(1));
                if (timing.group(2) != null) {
                    addTime("data-end", timing.group(2));
                }
                if (pendingId != null) {
                    pendingCue.addAttribute("", "data-id", "data-id", "CDATA", pendingId);
                    pendingId = null;
                }
            } else if (isTiming(next)) {
                // a line right before a timing line is that cue's identifier
                pendingId = line.strip();
            } else {
                text(line);
            }
        }

        private void addTime(String name, String raw) {
            String t = normalizeTime(raw);
            if (t != null) {
                pendingCue.addAttribute("", name, name, "CDATA", t);
            }
        }

        private static boolean startsWithKeyword(String line, String keyword) {
            return line.startsWith(keyword) && (line.length() == keyword.length()
                    || line.charAt(keyword.length()) == ' '
                    || line.charAt(keyword.length()) == '\t');
        }

        private void text(String line) throws SAXException {
            String cleaned = block == Block.NOTE ? line.strip() : clean(line, vtt).strip();
            if (cleaned.isEmpty()) {
                return;
            }
            if (!divOpen) {
                if (block == Block.NOTE) {
                    xhtml.startElement("div", "class", "note");
                } else if (pendingCue != null) {
                    xhtml.startElement("div", pendingCue);
                } else {
                    // text with no timing yet, e.g. a file that starts mid-cue
                    xhtml.startElement("div", "class", "cue");
                    block = Block.CUE;
                }
                pendingCue = null;
                divOpen = true;
            }
            if (!pOpen) {
                xhtml.startElement("p");
                pOpen = true;
            } else {
                xhtml.characters("\n");
            }
            xhtml.characters(cleaned);
        }

        private void closeP() throws SAXException {
            if (pOpen) {
                xhtml.endElement("p");
                pOpen = false;
            }
        }

        private void closeDiv() throws SAXException {
            closeP();
            if (divOpen) {
                xhtml.endElement("div");
                divOpen = false;
            }
            pendingCue = null;
        }
    }

    /** Lines split on LF, CRLF or CR; a line over {@link #MAX_LINE_CHARS} comes back in pieces. */
    static final class LineReader {
        private final Reader reader;
        private final char[] buf = new char[8192];
        private int pos;
        private int len;
        private boolean pendingLf;

        LineReader(Reader reader) {
            this.reader = reader;
        }

        String next() throws IOException {
            StringBuilder sb = null;
            while (true) {
                if (pos == len) {
                    len = reader.read(buf);
                    pos = 0;
                    if (len <= 0) {
                        len = 0;
                        return sb == null ? null : sb.toString();
                    }
                }
                char c = buf[pos++];
                if (pendingLf) {
                    pendingLf = false;
                    if (c == '\n') {
                        continue;
                    }
                }
                if (sb == null) {
                    sb = new StringBuilder();
                }
                if (c == '\n') {
                    return sb.toString();
                }
                if (c == '\r') {
                    pendingLf = true;
                    return sb.toString();
                }
                sb.append(c);
                if (sb.length() >= MAX_LINE_CHARS) {
                    return sb.toString();
                }
            }
        }
    }
}
