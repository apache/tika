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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class SubtitleParserTest extends TikaTest {

    private static final Pattern PARAGRAPH = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);

    private XMLResult parse(String content, String name, Charset charset) throws Exception {
        Metadata metadata = new Metadata();
        if (name != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        }
        try (TikaInputStream tis = TikaInputStream.get(content.getBytes(charset))) {
            return getXML(tis, AUTO_DETECT_PARSER, metadata);
        }
    }

    private static List<String> cues(XMLResult r) {
        List<String> cues = new ArrayList<>();
        Matcher m = PARAGRAPH.matcher(r.xml);
        while (m.find()) {
            cues.add(m.group(1));
        }
        return cues;
    }

    @Test
    public void testWebVtt() throws Exception {
        String vtt = "WEBVTT - a title\nKind: captions\nLanguage: de-DE\n\n"
                + "STYLE\n::cue { color: red }\n\n"
                + "REGION\nid:fred\n\n"
                + "NOTE a comment\nspanning two lines\n\n"
                + "intro\n00:00:01.000 --> 00:00:04.000 align:start\n"
                + "<v Roger>Never drink <b>liquid</b> <c.loud>nitrogen</c>.\n\n"
                + "00:05.000 --> 00:09.000\n- It will <00:06.000>perforate &amp; burn,\n"
                + "- you &lt;3 &#x263A;\n";
        XMLResult r = parse(vtt, "x.vtt", StandardCharsets.UTF_8);
        assertEquals(List.of("a comment\nspanning two lines", "Never drink liquid nitrogen.",
                "- It will perforate &amp; burn,\n- you &lt;3 ☺"), cues(r));
        assertContains("<div class=\"note\"><p>a comment", r.xml);
        assertContains("<div class=\"cue\" data-start=\"00:00:01.000\" data-end=\"00:00:04.000\""
                + " data-id=\"intro\"><p>Never", r.xml);
        assertContains("<div class=\"cue\" data-start=\"00:00:05.000\" data-end=\"00:00:09.000\">",
                r.xml);
        assertNotContained("color", r.xml);
        assertNotContained("fred", r.xml);
        assertNotContained("align", r.xml);
        assertNotContained("captions", r.xml);
        assertEquals("de-DE", r.metadata.get(TikaCoreProperties.LANGUAGE));
        assertTrue(r.metadata.get(HttpHeaders.CONTENT_TYPE).startsWith("text/vtt; charset="),
                r.metadata.get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testSubRip() throws Exception {
        String srt = "1\n00:01:14,440 --> 00:01:15,760\n<i>On peut écrire</i> {\\an8}a < b\n\n"
                + "2\n00:01:15,880 --> 00:01:19,520\n<font color=\"#ffffff\">deux</font>\nlignes\n";
        XMLResult r = parse(srt, "x.srt", StandardCharsets.UTF_8);
        assertEquals(List.of("On peut écrire a &lt; b", "deux\nlignes"), cues(r));
        assertContains("<div class=\"cue\" data-start=\"00:01:14.440\" data-end=\"00:01:15.760\""
                + " data-id=\"1\">", r.xml);
        assertTrue(r.metadata.get(HttpHeaders.CONTENT_TYPE)
                .startsWith("application/x-subrip; charset="));
    }

    @Test
    public void testSubRipDefects() throws Exception {
        // missing blank line before cue 2; blank line inside cue 3; dotted milliseconds
        String srt = "1\n00:00:01,000 --> 00:00:02,000\nfirst\n"
                + "2\n00:00:03,000 --> 00:00:04,000\nsecond\n\n"
                + "3\n00:00:05.000 --> 00:00:06.000\nthird a\n\nthird b\n\n"
                + "4\n00:00:07,000 --> 00:00:08,000\n1984\n";
        XMLResult r = parse(srt, "x.srt", StandardCharsets.UTF_8);
        assertEquals(List.of("first", "second", "third a", "third b", "1984"), cues(r));
        assertContains("data-id=\"3\"><p>third a</p>\n<p>third b</p>\n</div>", r.xml);
        assertContains("data-start=\"00:00:05.000\"", r.xml);
        assertEquals(4, r.xml.split("<div class=\"cue\"", -1).length - 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00:05.000|00:00:05.000", "00:05.5|00:00:05.500",
            "1:02:03|01:02:03.000", "00:00:05:042|00:00:05.042", "123:00:01,000|123:00:01.000",
            "00:61.000|"})
    public void testNormalizeTime(String io) {
        String[] parts = io.split("\\|", -1);
        String expected = parts[1].isEmpty() ? null : parts[1];
        assertEquals(expected, SubtitleParser.normalizeTime(parts[0]));
    }

    @Test
    public void testCrCrLfKeepsCueIds() throws Exception {
        String srt = "1\r\r\n00:00:01,000 --> 00:00:02,000\r\r\none\r\r\n\r\r\n"
                + "2\r\r\n00:00:03,000 --> 00:00:04,000\r\r\ntwo\r\r\n";
        XMLResult r = parse(srt, "x.srt", StandardCharsets.UTF_8);
        assertEquals(List.of("one", "two"), cues(r));
        assertContains("data-id=\"2\"", r.xml);
    }

    @Test
    public void testLineReaderBreaks() throws Exception {
        // CR CR LF is one break; CR CR without LF still ends an empty line (old Mac endings)
        assertEquals(List.of("a", "b", "", "", "c", ""), lines("a\r\r\nb\r\r\rc\r\r"));
        assertEquals(List.of("a", "", "b"), lines("a\r\n\r\nb"));
    }

    private static List<String> lines(String s) throws IOException {
        SubtitleParser.LineReader reader = new SubtitleParser.LineReader(new StringReader(s));
        List<String> out = new ArrayList<>();
        for (String l = reader.next(); l != null; l = reader.next()) {
            out.add(l);
        }
        return out;
    }

    @Test
    public void testSubRipUnknownTagPairs() {
        assertEquals("hide closed pairs and show text",
                SubtitleParser.clean("<x par=5>hide closed pairs</x> and <y><z>show</z> text</Y>",
                        false));
        assertEquals("<open>left alone, a < b", SubtitleParser.clean("<open>left alone, a < b",
                false));
    }

    @Test
    public void testCrossedTagPairs() {
        assertEquals("x", SubtitleParser.clean("<p><q></p></q>x", false));
        assertEquals("<a>y</c>", SubtitleParser.clean("<a>y</c>", false));
    }

    @Test
    @Timeout(10)
    public void testLongTagLineStaysLinear() {
        int n = SubtitleParser.MAX_LINE_CHARS / 8;
        String unmatched = "<a>".repeat(n) + "</c>".repeat(n);
        assertEquals(unmatched, SubtitleParser.clean(unmatched, false));
        assertEquals("text", SubtitleParser.clean("<a>".repeat(n) + "text" + "</a>".repeat(n),
                false));
    }

    @Test
    public void testVttCueRightAfterHeader() throws Exception {
        String vtt = "WEBVTT\nLanguage: en\n00:00.000 --> 00:01.000\nHello\n\n"
                + "00:02.000 --> 00:03.000\nBye\n";
        XMLResult r = parse(vtt, "x.vtt", StandardCharsets.UTF_8);
        assertEquals(List.of("Hello", "Bye"), cues(r));
        assertEquals("en", r.metadata.get(TikaCoreProperties.LANGUAGE));
    }

    @Test
    public void testTextBeforeTimingIsNotAnId() throws Exception {
        String srt = "1\n00:00:01,000 --> 00:00:02,000\nHello\n"
                + "00:00:03,000 --> 00:00:04,000\nBye\n";
        XMLResult r = parse(srt, "x.srt", StandardCharsets.UTF_8);
        assertEquals(List.of("Hello", "Bye"), cues(r));
        assertNotContained("data-id=\"Hello\"", r.xml);

        String vtt = "WEBVTT\n\n00:01.000 --> 00:02.000\nHello\n"
                + "00:03.000 --> 00:04.000\nBye\n";
        r = parse(vtt, "x.vtt", StandardCharsets.UTF_8);
        assertEquals(List.of("Hello", "Bye"), cues(r));
        assertNotContained("data-id", r.xml);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n", "\r", "\r\r\n"})
    public void testLineEndingsAndMagic(String eol) throws Exception {
        String srt = ("1\n00:00:01,000 --> 00:00:02,000\none\n\n"
                + "2\n00:00:03,000 --> 00:00:04,000\ntwo\n").replace("\n", eol);
        XMLResult r = parse(srt, null, StandardCharsets.UTF_8);
        assertTrue(r.metadata.get(HttpHeaders.CONTENT_TYPE).startsWith("application/x-subrip"),
                r.metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(List.of("one", "two"), cues(r));

        String vtt = ("WEBVTT\n\n00:01.000 --> 00:02.000\nuno\n").replace("\n", eol);
        r = parse(vtt, null, StandardCharsets.UTF_8);
        assertTrue(r.metadata.get(HttpHeaders.CONTENT_TYPE).startsWith("text/vtt"),
                r.metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(List.of("uno"), cues(r));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WEBVTT\n", "WEBVTT\r\n", "WEBVTT - a title\nKind: captions\n",
            "WEBVTT\ttitle\r\n", "WEBVTT FILE\n"})
    public void testVttMagic(String header) throws Exception {
        String vtt = header + "\n00:01.000 --> 00:02.000\nuno\n";
        for (Charset cs : List.of(StandardCharsets.UTF_8, StandardCharsets.UTF_16LE,
                StandardCharsets.UTF_16BE)) {
            for (String bom : List.of("", "﻿")) {
                if (bom.isEmpty() && cs != StandardCharsets.UTF_8) {
                    continue;
                }
                XMLResult r = parse(bom + vtt, null, cs);
                String ct = r.metadata.get(HttpHeaders.CONTENT_TYPE);
                assertTrue(ct.startsWith("text/vtt"), cs + " bom=" + !bom.isEmpty() + ": " + ct);
                assertEquals(List.of("uno"), cues(r), cs + " bom=" + !bom.isEmpty());
            }
        }
    }

    @Test
    public void testNotVtt() throws Exception {
        XMLResult r = parse("WEBVTTS are fun\n\n00:01.000 --> 00:02.000\nuno\n", null,
                StandardCharsets.UTF_8);
        assertTrue(r.metadata.get(HttpHeaders.CONTENT_TYPE).startsWith("text/plain"),
                r.metadata.get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testBomAndLegacyCharset() throws Exception {
        String vtt = "\uFEFFWEBVTT\n\n00:01.000 --> 00:02.000\nnaïve café\n";
        assertEquals(List.of("naïve café"), cues(parse(vtt, "x.vtt", StandardCharsets.UTF_8)));

        String srt = "1\n00:00:01,000 --> 00:00:02,000\n"
                + "Привет, как дела? Это проверка русских субтитров.\n\n"
                + "2\n00:00:03,000 --> 00:00:04,000\nВсё хорошо, спасибо большое.\n";
        XMLResult r = parse(srt, "x.srt", Charset.forName("windows-1251"));
        assertEquals(List.of("Привет, как дела? Это проверка русских субтитров.",
                "Всё хорошо, спасибо большое."), cues(r));
    }

    @Test
    public void testLongLineIsSplitNotBuffered() throws Exception {
        String longText = "a".repeat(SubtitleParser.MAX_LINE_CHARS * 3 + 5);
        SubtitleParser.LineReader lines =
                new SubtitleParser.LineReader(new StringReader(longText + "\nb"));
        int total = 0;
        String line;
        int pieces = 0;
        while ((line = lines.next()) != null && !line.equals("b")) {
            assertTrue(line.length() <= SubtitleParser.MAX_LINE_CHARS);
            total += line.length();
            pieces++;
        }
        assertEquals(longText.length(), total);
        assertEquals(4, pieces);
        assertEquals("b", line);
        assertNull(lines.next());
    }

    @Test
    public void testNoCues() throws Exception {
        XMLResult r = parse("WEBVTT\n\nSTYLE\n::cue {}\n", "x.vtt", StandardCharsets.UTF_8);
        assertEquals(List.of(), cues(r));
        assertNotContained("<div", r.xml);
    }

    @Test
    public void testInvalidTimingLineIsText() throws Exception {
        // minutes 61 and 62 are out of range, so normalizeTime returns null for
        // both endpoints: the line is cue-shaped but is not a cue, and the text
        // after it must not be captured by an attribute-less cue div.
        String vtt = "WEBVTT\n\n00:61.000 --> 00:62.000\nafter the bad timing\n\n"
                + "00:01.000 --> 00:02.000\nreal cue\n";
        XMLResult r = parse(vtt, "x.vtt", StandardCharsets.UTF_8);
        // the malformed timing line degrades to cue text; the real cue keeps
        // its timing attributes and no attribute-less cue div is emitted
        assertEquals(List.of("00:61.000 --&gt; 00:62.000\nafter the bad timing", "real cue"),
                cues(r));
        assertTrue(r.xml.contains("data-start=\"00:00:01.000\""), r.xml);
        assertEquals(1, r.xml.split("<div class=\"cue\" data-start", -1).length - 1);
    }
}
