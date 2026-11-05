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
package org.apache.tika.parser.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.mkv.MatroskaParser;
import org.apache.tika.sax.XHTMLContentHandler;

public class MkvSubtitleExtractorTest extends TikaTest {

    // 1-byte blocks over a 4 KB stripped header that decode past the 256 MB budget
    private static final int BLOCKS_PAST_BUDGET =
            (int) (SubtitleTracks.MAX_DECODED_BYTES / MkvSubtitleExtractor.MAX_COMP_SETTINGS_BYTES)
                    + 10;
    private static final byte[] UNKNOWN = {0x01, -1, -1, -1, -1, -1, -1, -1};


    @TempDir
    Path tmp;

    private record Emitted(Metadata metadata, String body) {
    }

    private static final class Capture implements EmbeddedDocumentExtractor {
        final List<Emitted> docs = new ArrayList<>();

        @Override
        public boolean shouldParseEmbedded(Metadata metadata, ParseContext parseContext) {
            return true;
        }

        @Override
        public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                  Metadata metadata, ParseContext parseContext, boolean outputHtml)
                throws IOException {
            docs.add(new Emitted(metadata,
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    /** Extracts from a file-backed stream ({@code fromFile}) or a plain one. */
    private List<Emitted> extract(byte[] mkv, boolean fromFile, Metadata container)
            throws Exception {
        Capture capture = new Capture();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, capture);
        XHTMLContentHandler xhtml =
                new XHTMLContentHandler(new DefaultHandler(), container, context);
        xhtml.startDocument();
        TikaInputStream tis;
        if (fromFile) {
            Path p = Files.createTempFile(tmp, "t", ".mkv");
            Files.write(p, mkv);
            tis = TikaInputStream.get(p);
        } else {
            tis = TikaInputStream.get(new ByteArrayInputStream(mkv));
        }
        try (tis) {
            SubtitleTracks.readMatroska(tis, container, context).emit(xhtml);
        }
        xhtml.endDocument();
        return capture.docs;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testTracks(boolean fromFile) throws Exception {
        Metadata container = new Metadata();
        List<Emitted> docs = extract(sample(), fromFile, container);
        assertEquals(4, docs.size(), docs.toString());

        assertEquals("WEBVTT\n"
                + "\n00:00:01.500 --> 00:00:03.000\nBonjour le monde\n"
                + "\n00:00:05.000 --> 00:00:06.000\nDeuxième\n", docs.get(0).body());
        Metadata fr = docs.get(0).metadata();
        assertEquals("track-2.vtt", fr.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("fre", fr.get(TikaCoreProperties.LANGUAGE));
        assertEquals("French SDH", fr.get(TikaCoreProperties.TITLE));
        assertEquals("text/vtt; charset=UTF-8", fr.get(HttpHeaders.CONTENT_TYPE));

        // zlib-compressed ASS; text after the eighth comma keeps its own commas
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello, world\nline two\n",
                docs.get(1).body());
        // Language absent: the spec default
        assertEquals("eng", docs.get(1).metadata().get(TikaCoreProperties.LANGUAGE));

        // header stripping; a SimpleBlock with nothing after it on its track ends where it starts
        assertEquals("WEBVTT\n\n00:00:02.000 --> 00:00:02.000\nHello stripped\n",
                docs.get(2).body());

        assertEquals("WEBVTT\n\n00:00:03.000 --> 00:00:04.000\nHi &amp; bye\n",
                docs.get(3).body());
        assertEquals("de", docs.get(3).metadata().get(TikaCoreProperties.LANGUAGE));
        // the encrypted and laced tracks lose text, and say so; the PGS track is just skipped
        String[] warnings = container.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertEquals(2, warnings.length, String.join("\n", warnings));
        assertTrue(warnings[0].contains("track 7: encrypted; skipped"), warnings[0]);
        assertTrue(warnings[1].contains("laced blocks not supported"), warnings[1]);
    }

    @Test
    public void testThroughAutoDetect() throws Exception {
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, "x.mkv");
        List<Metadata> list = getRecursiveMetadata(TikaInputStream.get(sample()),
                AUTO_DETECT_PARSER, m, new ParseContext(), true);
        assertEquals(5, list.size());
        assertTrue(list.get(0).get(HttpHeaders.CONTENT_TYPE).contains("matroska"),
                list.get(0).get(HttpHeaders.CONTENT_TYPE));

        MatroskaParser off = new MatroskaParser();
        off.setExtractSubtitles(false);
        assertEquals(1, getRecursiveMetadata(TikaInputStream.get(sample()), off, new Metadata(),
                new ParseContext(), true).size());
    }

    @Test
    public void testConfigTurnsSubtitlesOff() throws Exception {
        Parser parser = TikaLoader
                .load(getConfigPath(MkvSubtitleExtractorTest.class,
                        "tika-config-mkv-no-subtitles.json"))
                .loadParsers();
        Metadata m = new Metadata();
        // the loaded parser does not detect; route it as AutoDetectParser would
        m.set(HttpHeaders.CONTENT_TYPE, "video/x-matroska");
        List<Metadata> list = getRecursiveMetadata(TikaInputStream.get(sample()), parser, m,
                new ParseContext(), true);
        assertEquals(1, list.size());
        assertTrue(Arrays.asList(list.get(0).getValues(TikaCoreProperties.TIKA_PARSED_BY))
                .contains(MatroskaParser.class.getName()));
    }

    @Test
    public void testTruncatedKeepsEarlierCues() throws Exception {
        byte[] full = sample();
        byte[] cut = Arrays.copyOf(full, full.length - 20);
        Metadata container = new Metadata();
        List<Emitted> docs = extract(cut, true, container);
        assertTrue(docs.get(0).body().contains("Bonjour"), docs.toString());
    }

    @Test
    public void testCorruptedBytes() throws Exception {
        byte[] good = sample();
        // -Dtika.test.seed=<seed> replays a failure
        long seed = Long.getLong("tika.test.seed", new Random().nextLong());
        Random r = new Random(seed);
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> corrupt(good, r, seed),
                () -> "seed=" + seed);
    }

    private void corrupt(byte[] good, Random r, long seed) throws Exception {
        for (int i = 0; i < 2000; i++) {
            byte[] bad = good.clone();
            int flips = 1 + r.nextInt(8);
            for (int f = 0; f < flips; f++) {
                bad[r.nextInt(bad.length)] = (byte) r.nextInt(256);
            }
            if (r.nextInt(4) == 0) {
                bad = Arrays.copyOf(bad, r.nextInt(bad.length));
            }
            try {
                extract(bad, r.nextBoolean(), new Metadata());
            } catch (RuntimeException | Error e) {
                throw new AssertionError("seed=" + seed + " iteration=" + i, e);
            }
        }
    }

    @Test
    public void testCueWithoutDurationEndsAtNextCue() throws Exception {
        byte[] ebml = el(0x1A45DFA3, el(0x4282, ascii("matroska")));
        byte[] tracks = el(0x1654AE6B,
                track(1, "S_TEXT/UTF8", null, null, null, null),
                el(0xAE, el(0xD7, uint(2)), el(0x83, uint(0x11)), el(0x86, ascii("S_TEXT/UTF8")),
                        el(0x23E383, uint(1_500_000_000L))));
        byte[] cluster = el(0x1F43B675, el(0xE7, uint(0)),
                el(0xA3, block(1, 0, utf8("Press {Enter} {\\an8}now"))),
                el(0xA3, block(2, 1000, utf8("default"))),
                el(0xA3, block(1, 2000, utf8("next"))));
        List<Emitted> docs = extract(cat(ebml, el(0x18538067, tracks, cluster)), true,
                new Metadata());
        assertEquals(2, docs.size(), docs.toString());
        // SRT keeps literal braces; only {\...} override tags are dropped
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:02.000\nPress {Enter} now\n"
                + "\n00:00:02.000 --> 00:00:02.000\nnext\n", docs.get(0).body());
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:02.500\ndefault\n", docs.get(1).body());
    }

    @Test
    public void testAssText() {
        assertEquals("a, b\nc", MkvSubtitleExtractor.assText("0,0,Default,,0,0,0,,{\\b1}a, b\\Nc"));
        assertEquals("", MkvSubtitleExtractor.assText("0,0,Default"));
    }

    @Test
    public void testBlankStrippedHeaderStillCharged() throws Exception {
        // 4 KB of spaces as the stripped header: every 1-byte block decodes to 4 KB of blanks
        byte[] spaces = new byte[MkvSubtitleExtractor.MAX_COMP_SETTINGS_BYTES];
        Arrays.fill(spaces, (byte) ' ');
        byte[] ebml = el(0x1A45DFA3, el(0x4282, ascii("matroska")));
        byte[] tracks = el(0x1654AE6B, track(1, "S_TEXT/UTF8", null, null, null,
                el(0x6240, el(0x5034, el(0x4254, uint(3)), el(0x4255, spaces)))));
        ByteArrayOutputStream blocks = new ByteArrayOutputStream();
        for (int i = 0; i < BLOCKS_PAST_BUDGET; i++) {
            blocks.writeBytes(el(0xA3, block(1, i, utf8(" "))));
        }
        byte[] cluster = el(0x1F43B675, el(0xE7, uint(0)), blocks.toByteArray());
        byte[] mkv = cat(ebml, el(0x18538067, tracks, cluster));
        Metadata container = new Metadata();
        assertEquals(0, extract(mkv, true, container).size());
        assertTrue(String.valueOf(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .contains("decoded bytes"), container.toString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testFilteredAwayTextStillCharged(boolean grouped) throws Exception {
        // an ASS track whose 4 KB stripped header has no fields: every 1-byte block decodes
        // 4 KB that the filter drops; SimpleBlocks, or Blocks all in one BlockGroup
        byte[] header = new byte[MkvSubtitleExtractor.MAX_COMP_SETTINGS_BYTES];
        Arrays.fill(header, (byte) 'x');
        byte[] ebml = el(0x1A45DFA3, el(0x4282, ascii("matroska")));
        byte[] tracks = el(0x1654AE6B, track(1, "S_TEXT/ASS", null, null, null,
                el(0x6240, el(0x5034, el(0x4254, uint(3)), el(0x4255, header)))));
        ByteArrayOutputStream blocks = new ByteArrayOutputStream();
        for (int i = 0; i < BLOCKS_PAST_BUDGET; i++) {
            blocks.writeBytes(grouped ? el(0xA1, block(1, i, utf8("x")))
                    : el(0xA3, block(1, i, utf8("x"))));
        }
        byte[] body = grouped ? el(0xA0, blocks.toByteArray()) : blocks.toByteArray();
        byte[] cluster = el(0x1F43B675, el(0xE7, uint(0)), body);
        Metadata container = new Metadata();
        assertEquals(0, extract(cat(ebml, el(0x18538067, tracks, cluster)), true, container)
                .size());
        assertTrue(String.valueOf(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .contains("decoded bytes"), container.toString());
    }

    /**
     * EBML header, then a Segment and first Cluster of unknown size (as live muxers write
     * them), a second Cluster of known size. Tracks: 1 video, 2 SRT (fre, named), 3 ASS
     * zlib, 4 SRT header-stripped, 5 WebVTT (BCP 47 "de"), 6 PGS, 7 encrypted SRT.
     */
    static byte[] sample() throws IOException {
        byte[] ebml = el(0x1A45DFA3, el(0x4282, ascii("matroska")));
        byte[] info = el(0x1549A966, el(0x2AD7B1, uint(1_000_000)));
        byte[] tracks = el(0x1654AE6B,
                track(1, "V_VP9", null, null, null, null),
                track(2, "S_TEXT/UTF8", "fre", null, "French SDH", null),
                track(3, "S_TEXT/ASS", null, null, null,
                        el(0x6240, el(0x5034, el(0x4254, uint(0))))),
                track(4, "S_TEXT/UTF8", "eng", null, null,
                        el(0x6240, el(0x5034, el(0x4254, uint(3)),
                                el(0x4255, ascii("Hello "))))),
                track(5, "S_TEXT/WEBVTT", "ger", "de", null, null),
                track(6, "S_HDMV/PGS", null, null, null, null),
                track(7, "S_TEXT/UTF8", null, null, null,
                        el(0x6240, el(0x5033, uint(1)))));
        byte[] cluster1 = cat(
                id(0x1F43B675), new byte[]{0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                el(0xE7, uint(1000)),
                el(0xA3, block(1, 0, new byte[5000])),
                el(0xA0, el(0xA1, block(2, 500, utf8("<i>Bonjour</i> {\\an8}le monde"))),
                        el(0x9B, uint(1500))),
                el(0xA0, el(0x9B, uint(1000)),
                        el(0xA1, block(3, 0, zlib("1,0,Default,,0,0,0,,Hello, world\\Nline two")))),
                el(0xA3, block(4, 1000, utf8("stripped"))),
                el(0xA0, el(0xA1, block(5, 2000, utf8("<v Bob>Hi &amp; <b>bye</b>"))),
                        el(0x9B, uint(1000))),
                el(0xA0, el(0xA1, block(6, 0, new byte[100])), el(0x9B, uint(1000))),
                el(0xA0, el(0xA1, block(7, 0, utf8("secret"))), el(0x9B, uint(1000))),
                el(0xA0, el(0xA1, laced(2, 0, utf8("laced"))), el(0x9B, uint(1000))));
        byte[] cluster2 = el(0x1F43B675, el(0xE7, uint(5000)),
                el(0xA0, el(0xA1, block(2, 0, utf8("Deuxième"))), el(0x9B, uint(1000))));
        byte[] segment = cat(id(0x18538067), new byte[]{0x01, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                info, tracks, cluster1, cluster2);
        return cat(ebml, segment);
    }


    @Test
    public void testNoTextTrackStopsAtFirstCluster() throws Exception {
        // a video-only stream: nothing past the first Cluster header may be read
        byte[] ebml = el(0x1A45DFA3, el(0x4282, ascii("matroska")));
        byte[] tracks = el(0x1654AE6B, track(1, "V_VP9", null, null, null, null));
        ByteArrayOutputStream video = new ByteArrayOutputStream();
        for (int i = 0; i < 1000; i++) {
            video.writeBytes(el(0xA3, block(1, i, new byte[1000])));
        }
        byte[] head = cat(ebml, id(0x18538067), UNKNOWN, tracks, id(0x1F43B675), UNKNOWN);
        byte[] mkv = cat(head, el(0xE7, uint(0)), video.toByteArray());
        long[] read = {0};
        InputStream counting = new FilterInputStream(new ByteArrayInputStream(mkv)) {
            @Override
            public int read() throws IOException {
                int b = super.read();
                read[0] += b < 0 ? 0 : 1;
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int n = super.read(b, off, len);
                read[0] += Math.max(n, 0);
                return n;
            }
        };
        Metadata container = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(counting)) {
            SubtitleTracks.readMatroska(tis, container, new ParseContext());
        }
        assertTrue(read[0] <= head.length + 8192, "read " + read[0] + " of " + mkv.length);
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testTracksAfterFirstClusterIsReported() throws Exception {
        byte[] tracks = el(0x1654AE6B, track(2, "S_TEXT/UTF8", "eng", null, null, null));
        byte[] cluster = el(0x1F43B675, el(0xE7, uint(0)),
                el(0xA0, el(0xA1, block(2, 0, utf8("late"))), el(0x9B, uint(1000))));
        byte[] mkv = cat(el(0x1A45DFA3, el(0x4282, ascii("matroska"))),
                el(0x18538067, cluster, tracks));
        Metadata container = new Metadata();
        assertEquals(0, extract(mkv, false, container).size());
        assertTrue(String.valueOf(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .contains("subtitles: Matroska Tracks after the first Cluster"),
                container.toString());
    }

    @Test
    public void testDamageIsReportedAsSubtitles() throws Exception {
        byte[] mkv = sample();
        Metadata container = new Metadata();
        extract(Arrays.copyOf(mkv, mkv.length - 3), false, container);
        String[] warnings = container.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertTrue(Arrays.stream(warnings).allMatch(w -> w.contains("subtitles: ")),
                String.join("\n", warnings));
    }

    @Test
    public void testDamagedZlibIsCharged() throws Exception {
        // each block inflates to nearly 1 MB before its checksum fails: the work is charged
        byte[] plain = new byte[MkvSubtitleExtractor.MAX_BLOCK_BYTES - 1];
        Arrays.fill(plain, (byte) 'a');
        Deflater d = new Deflater(9);
        d.setInput(plain);
        d.finish();
        byte[] buf = new byte[1 << 16];
        int n = d.deflate(buf);
        d.end();
        byte[] bad = Arrays.copyOf(buf, n);
        bad[n - 1] ^= 0x55;
        byte[] ebml = el(0x1A45DFA3, el(0x4282, ascii("matroska")));
        byte[] tracks = el(0x1654AE6B, track(1, "S_TEXT/UTF8", null, null, null,
                el(0x6240, el(0x5034, el(0x4254, uint(0))))));
        ByteArrayOutputStream blocks = new ByteArrayOutputStream();
        int count = (int) (SubtitleTracks.MAX_DECODED_BYTES / plain.length) + 10;
        for (int i = 0; i < count; i++) {
            blocks.writeBytes(el(0xA3, block(1, 0, bad)));
        }
        byte[] cluster = el(0x1F43B675, el(0xE7, uint(0)), blocks.toByteArray());
        Metadata container = new Metadata();
        assertEquals(0, extract(cat(ebml, el(0x18538067, tracks, cluster)), true, container)
                .size());
        String warnings = String.join("\n",
                container.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertTrue(warnings.contains("decoded bytes"), warnings);
        assertTrue(warnings.contains("damaged zlib block"), warnings);
    }

    @Test
    public void testWebmVttDropsIdentifierAndSettings() throws Exception {
        byte[] ebml = el(0x1A45DFA3, el(0x4282, ascii("webm")));
        byte[] tracks = el(0x1654AE6B, track(1, "D_WEBVTT/SUBTITLES", null, null, null, null));
        byte[] cluster = el(0x1F43B675, el(0xE7, uint(0)),
                el(0xA0, el(0xA1, block(1, 0, utf8("cue-1\nline:0 align:start\nHi <b>there</b>"))),
                        el(0x9B, uint(1000))),
                el(0xA0, el(0xA1, block(1, 2000, utf8("\n\nSecond"))), el(0x9B, uint(500))));
        List<Emitted> docs = extract(cat(ebml, el(0x18538067, tracks, cluster)), true,
                new Metadata());
        assertEquals(1, docs.size());
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nHi there\n"
                + "\n00:00:02.000 --> 00:00:02.500\nSecond\n", docs.get(0).body());
    }

    @Test
    public void testHeaderStringsAreCapped() throws Exception {
        byte[] name = new byte[1024 * 1024];
        Arrays.fill(name, (byte) 'n');
        byte[] header = new byte[MkvSubtitleExtractor.MAX_COMP_SETTINGS_BYTES + 1];
        byte[] ebml = el(0x1A45DFA3, el(0x4282, ascii("matroska")));
        byte[] tracks = el(0x1654AE6B,
                track(1, "S_TEXT/UTF8", null, null, new String(name, StandardCharsets.US_ASCII),
                        null),
                track(2, "S_TEXT/UTF8", null, null, null,
                        el(0x6240, el(0x5034, el(0x4254, uint(3)), el(0x4255, header)))));
        byte[] cluster = el(0x1F43B675, el(0xE7, uint(0)),
                el(0xA3, block(1, 0, utf8("one"))), el(0xA3, block(2, 0, utf8("two"))));
        Metadata container = new Metadata();
        List<Emitted> docs = extract(cat(ebml, el(0x18538067, tracks, cluster)), true, container);
        assertEquals(1, docs.size());
        assertEquals(MkvSubtitleExtractor.MAX_NAME_BYTES,
                docs.get(0).metadata().get(TikaCoreProperties.TITLE).length());
        assertTrue(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING)
                .contains("track 2: stripped header over"), container.toString());
    }

    @Test
    public void testEncodingScopedToCodecPrivateIsIgnored() throws Exception {
        // ContentEncodingScope 2: zlib applies to CodecPrivate only; frames are plain
        byte[] ebml = el(0x1A45DFA3, el(0x4282, ascii("matroska")));
        byte[] tracks = el(0x1654AE6B, track(1, "S_TEXT/UTF8", null, null, null,
                el(0x6240, el(0x5032, uint(2)), el(0x5034, el(0x4254, uint(0))))));
        byte[] cluster = el(0x1F43B675, el(0xE7, uint(0)), el(0xA3, block(1, 0, utf8("plain"))));
        List<Emitted> docs = extract(cat(ebml, el(0x18538067, tracks, cluster)), true,
                new Metadata());
        assertEquals(1, docs.size());
        assertTrue(docs.get(0).body().contains("plain"), docs.get(0).body());
    }

    private static byte[] track(int number, String codec, String lang, String bcp47,
                                String name, byte[] encoding) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(el(0xD7, uint(number)));
        out.writeBytes(el(0x83, uint(codec.startsWith("V_") ? 1 : 0x11)));
        out.writeBytes(el(0x86, ascii(codec)));
        if (lang != null) {
            out.writeBytes(el(0x22B59C, ascii(lang)));
        }
        if (bcp47 != null) {
            out.writeBytes(el(0x22B59D, ascii(bcp47)));
        }
        if (name != null) {
            out.writeBytes(el(0x536E, utf8(name)));
        }
        if (encoding != null) {
            out.writeBytes(el(0x6D80, encoding));
        }
        return el(0xAE, out.toByteArray());
    }

    private static byte[] block(int track, int relative, byte[] data) {
        return cat(new byte[]{(byte) (0x80 | track), (byte) (relative >> 8), (byte) relative, 0},
                data);
    }

    private static byte[] laced(int track, int relative, byte[] data) {
        byte[] b = block(track, relative, data);
        b[3] = 0x02;
        return b;
    }

    private static byte[] zlib(String s) {
        Deflater d = new Deflater();
        d.setInput(utf8(s));
        d.finish();
        byte[] buf = new byte[1024];
        int n = d.deflate(buf);
        d.end();
        return Arrays.copyOf(buf, n);
    }

    private static byte[] el(int id, byte[]... parts) {
        byte[] body = cat(parts);
        return cat(id(id), size(body.length), body);
    }

    private static byte[] id(int id) {
        int len = id > 0xFFFFFF ? 4 : id > 0xFFFF ? 3 : id > 0xFF ? 2 : 1;
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) {
            b[i] = (byte) (id >> (8 * (len - 1 - i)));
        }
        return b;
    }

    // always the 8-byte form, which is valid for any size
    private static byte[] size(long n) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) {
            b[i] = (byte) (n >> (8 * (7 - i)));
        }
        b[0] = 0x01;
        return b;
    }

    private static byte[] uint(long v) {
        return new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
