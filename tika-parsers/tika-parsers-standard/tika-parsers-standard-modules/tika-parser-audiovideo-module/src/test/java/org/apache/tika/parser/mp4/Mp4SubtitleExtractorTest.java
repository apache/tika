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
package org.apache.tika.parser.mp4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import org.apache.tika.sax.XHTMLContentHandler;

public class Mp4SubtitleExtractorTest extends TikaTest {

    private static final int ENG = (5 << 10) | (14 << 5) | 7;

    /** One embedded document the extractor handed over. */
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

    private static List<Emitted> extract(byte[] mp4, Metadata container) throws Exception {
        return extract(mp4, container, new Mp4SubtitleExtractor());
    }

    private static List<Emitted> extract(byte[] mp4, Metadata container,
                                         Mp4SubtitleExtractor extractor) throws Exception {
        Capture capture = new Capture();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, capture);
        XHTMLContentHandler xhtml =
                new XHTMLContentHandler(new DefaultHandler(), container, context);
        xhtml.startDocument();
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            extractor.extract(tis, container, xhtml, context);
        }
        xhtml.endDocument();
        return capture.docs;
    }

    @Test
    public void testTx3gTrack() throws Exception {
        Metadata container = new Metadata();
        List<Emitted> docs = extract(new Builder().build(true), container);
        assertEquals(1, docs.size(), "chapter track must be skipped");
        Emitted vtt = docs.get(0);
        assertEquals("WEBVTT\n"
                + "\n00:00:00.000 --> 00:00:01.000\nHello &lt;world&gt; &amp; co\n"
                + "\n00:00:01.500 --> 00:00:02.500\nline one\nline two\n"
                + "\n00:00:02.500 --> 00:00:03.500\nnaïve ☺\n", vtt.body());
        assertEquals("track-2.vtt", vtt.metadata().get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("eng", vtt.metadata().get(TikaCoreProperties.LANGUAGE));
        assertEquals("text/vtt; charset=UTF-8", vtt.metadata().get(HttpHeaders.CONTENT_TYPE));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testStcoOffsets() throws Exception {
        assertEquals(1, extract(new Builder().build(false), new Metadata()).size());
    }

    @Test
    public void testThroughMp4Parser() throws Exception {
        byte[] mp4 = new Builder().build(true);
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, "x.mp4");
        List<Metadata> list = getRecursiveMetadata(TikaInputStream.get(mp4), AUTO_DETECT_PARSER,
                m, new ParseContext(), true);
        assertEquals(2, list.size());
        assertEquals("track-2.vtt", list.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertTrue(list.get(1).get(HttpHeaders.CONTENT_TYPE).startsWith("text/vtt"));

        MP4Parser parser = new MP4Parser();
        parser.setExtractSubtitles(false);
        list = getRecursiveMetadata(TikaInputStream.get(mp4), parser, new Metadata(),
                new ParseContext(), true);
        assertEquals(1, list.size());
    }

    @Test
    public void testConfigTurnsSubtitlesOff() throws Exception {
        Parser parser = TikaLoader
                .load(getConfigPath(Mp4SubtitleExtractorTest.class,
                        "tika-config-mp4-no-subtitles.json"))
                .loadParsers();
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, "x.mp4");
        // the loaded parser does not detect; route it as AutoDetectParser would
        m.set(HttpHeaders.CONTENT_TYPE, "video/mp4");
        List<Metadata> list = getRecursiveMetadata(
                TikaInputStream.get(new Builder().build(true)), parser, m, new ParseContext(),
                true);
        assertEquals(1, list.size());
    }

    @Test
    public void testLyingTables() throws Exception {
        Builder b = new Builder();
        b.sampleCount = Integer.MAX_VALUE;
        b.stscFirstChunks = new int[]{0, 1_000_000, 1, 1_000_000, 1};
        b.firstChunkOffset = Long.MAX_VALUE - 1;
        Metadata container = new Metadata();
        List<Emitted> docs = extract(b.build(true), container);
        // chunk 1 points past the end and is skipped; chunk 2 still reads
        assertEquals(1, docs.size());
        assertTrue(docs.get(0).body().contains("line one"));
        assertTrue(!docs.get(0).body().contains("Hello"));
    }

    @Test
    public void testSteppingBackStscDoesNotRereadChunks() throws Exception {
        Builder b = new Builder();
        b.stscFirstChunks = new int[]{1, 3, 1};
        b.perChunk = 1;
        List<Emitted> docs = extract(b.build(true), new Metadata());
        assertEquals(1, docs.size());
        String body = docs.get(0).body();
        assertEquals(body.indexOf("Hello"), body.lastIndexOf("Hello"), body);
    }

    @Test
    public void testUnsizedStreamIsNotSpooled() throws Exception {
        byte[] mp4 = new Builder().build(true);
        List<Metadata> list = getRecursiveMetadata(
                TikaInputStream.get(new ByteArrayInputStream(mp4)), new MP4Parser(),
                new Metadata(), new ParseContext(), true);
        assertEquals(1, list.size());
    }

    @Test
    public void testFragmented() throws Exception {
        Metadata container = new Metadata();
        List<Emitted> docs = extract(fragmented(5, 1), container);
        assertEquals(1, docs.size());
        assertEquals("WEBVTT\n"
                + "\n00:00:05.000 --> 00:00:06.000\nfrag one\n"
                + "\n00:00:06.000 --> 00:00:07.000\nfrag two\n"
                + "\n00:00:09.000 --> 00:00:11.000\nfrag three\n", docs.get(0).body());
        assertEquals("track-2.vtt",
                docs.get(0).metadata().get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    @Timeout(20)
    public void testFragmentedHugeSampleCounts() throws Exception {
        // 200 video runs each claim 4 billion default-sized samples: only their ends are computed
        byte[] hostile = moof1(0, 0xFFFFFFFFL, 2, 2, 0);
        byte[][] parts = new byte[201][];
        parts[0] = fragmented(5, 0xFFFFFFFFL);
        Arrays.fill(parts, 1, parts.length, hostile);
        List<Emitted> docs = extract(cat(parts), new Metadata());
        assertEquals(1, docs.size());
        assertTrue(docs.get(0).body().contains("frag three"), docs.get(0).body());
    }

    @Test
    public void testStblThenFragments() throws Exception {
        // stbl holds "zero"; the fragment's traf has an explicit base and a default duration
        // after it, then a run with every per-sample field and a second run that continues
        byte[] ftyp = box("ftyp", ascii("iso6"), u32(0), ascii("iso6"));
        byte[] zero = Builder.sample("zero".getBytes(StandardCharsets.UTF_8));
        byte[] mdat0 = box("mdat", zero);
        long zeroOffset = ftyp.length + 8L;
        byte[] subs = Builder.trak(2, "sbtl", ENG, new long[][]{{1, 1000}}, new int[]{1}, 1,
                new int[]{zero.length}, -1, new long[]{zeroOffset}, false, null);
        byte[] moov = box("moov", box("mvhd", new byte[100]), subs,
                box("mvex", box("trex", u32(0), u32(2), u32(1), u32(1000), u32(0), u32(0))));
        byte[] one = Builder.sample("one".getBytes(StandardCharsets.UTF_8));
        byte[] two = Builder.sample("two".getBytes(StandardCharsets.UTF_8));
        long head = ftyp.length + mdat0.length + moov.length;
        byte[] moof = fullMoof(0, one.length, two.length);
        moof = fullMoof(head + moof.length + 8, one.length, two.length);
        byte[] mp4 = cat(ftyp, mdat0, moov, moof, box("mdat", one, two));

        List<Emitted> docs = extract(mp4, new Metadata());
        assertEquals(1, docs.size());
        assertEquals("WEBVTT\n"
                + "\n00:00:00.000 --> 00:00:01.000\nzero\n"
                + "\n00:00:01.000 --> 00:00:01.500\none\n"
                + "\n00:00:01.500 --> 00:00:04.500\ntwo\n", docs.get(0).body());
    }

    @Test
    public void testShortTfhdIsDamageNotGuesswork() throws Exception {
        // the flags promise a base offset the box does not hold: earlier cues stay, a warning
        byte[] damaged = box("moof", box("mfhd", u32(0), u32(3)),
                box("traf", box("tfhd", u32(0x000001), u32(2))));
        Metadata container = new Metadata();
        List<Emitted> docs = extract(cat(fragmented(5, 1), damaged), container);
        assertEquals(1, docs.size());
        assertTrue(docs.get(0).body().contains("frag three"), docs.get(0).body());
        assertTrue(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING)
                .contains("tfhd shorter"), container.toString());
    }

    @Test
    public void testBlankSamplesChargeTheDecodeBudget() throws Exception {
        // 5000 samples of 64 KB of spaces at one offset: blank text, but 320 MB to decode
        int n = 5000;
        byte[] ftyp = box("ftyp", ascii("isom"), u32(0), ascii("isom"));
        byte[] blank = new byte[0xFFFF];
        Arrays.fill(blank, (byte) ' ');
        byte[] mdat = box("mdat", Builder.sample(blank));
        int[] sizes = new int[n];
        Arrays.fill(sizes, blank.length + 2);
        long[] chunks = new long[n];
        Arrays.fill(chunks, ftyp.length + 8L);
        byte[] subs = Builder.trak(2, "sbtl", ENG, new long[][]{{n, 1000}}, new int[]{1}, 1,
                sizes, -1, chunks, false, null);
        Metadata container = new Metadata();
        extract(cat(ftyp, mdat, box("moov", box("mvhd", new byte[100]), subs)), container);
        assertTrue(String.valueOf(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .contains("exceeds"), container.toString());
    }

    @Test
    public void testOtherTracksDamageStaysTheirs() throws Exception {
        // a video traf with a short tfhd, then in the same moof a subtitle traf based at the
        // moof, and a later moof: both subtitle cues survive
        byte[] four = Builder.sample("frag four".getBytes(StandardCharsets.UTF_8));
        byte[] damaged = damagedThenMine(0, four.length);
        damaged = damagedThenMine(damaged.length + 8, four.length);
        byte[] five = Builder.sample("frag five".getBytes(StandardCharsets.UTF_8));
        byte[] later = moof2(0, five.length);
        later = moof2(later.length + 8, five.length);
        Metadata container = new Metadata();
        List<Emitted> docs = extract(cat(fragmented(5, 1), damaged, box("mdat", four), later,
                box("mdat", five)), container);
        assertEquals(1, docs.size());
        String body = docs.get(0).body();
        assertTrue(body.contains("frag three") && body.contains("frag four")
                && body.contains("frag five"), body);
    }

    @Test
    public void testTrafAfterDamageIsSkippedNotMisread() throws Exception {
        // our traf has no base of its own and follows a damaged video traf: its data cannot be
        // located, so it is skipped with a warning; the next moof starts fresh
        byte[] lost = Builder.sample("lost".getBytes(StandardCharsets.UTF_8));
        byte[] video = box("traf", box("tfhd", u32(0x000001), u32(1)));
        byte[] mine = box("traf", box("tfhd", u32(0), u32(2)),
                box("trun", u32(0x000200), u32(1), u32(lost.length)));
        byte[] damaged = box("moof", box("mfhd", u32(0), u32(4)), video, mine);
        byte[] five = Builder.sample("frag five".getBytes(StandardCharsets.UTF_8));
        byte[] later = moof2(0, five.length);
        later = moof2(later.length + 8, five.length);
        Metadata container = new Metadata();
        List<Emitted> docs = extract(cat(fragmented(5, 1), damaged, box("mdat", lost), later,
                box("mdat", five)), container);
        String body = docs.get(0).body();
        assertTrue(body.contains("frag five") && !body.contains("lost"), body);
        assertTrue(String.join(" ", container.getValues(
                TikaCoreProperties.TIKA_META_EXCEPTION_WARNING)).contains("follows a damaged"),
                container.toString());
    }

    private static byte[] damagedThenMine(int dataOffset, int size) {
        byte[] video = box("traf", box("tfhd", u32(0x000001), u32(1)));
        byte[] mine = box("traf", box("tfhd", u32(0x020000), u32(2)),
                box("trun", u32(0x000201), u32(1), u32(dataOffset), u32(size)));
        return box("moof", box("mfhd", u32(0), u32(4)), video, mine);
    }

    @Test
    public void testOversizedVideoRunDoesNotEndSubtitles() throws Exception {
        // a valid video trun over the 16 MB table limit, then a subtitle traf based at the moof
        byte[] four = Builder.sample("frag four".getBytes(StandardCharsets.UTF_8));
        byte[] big = box("trun", u32(0), u32(0), new byte[Mp4SubtitleExtractor.MAX_TABLE_BYTES]);
        byte[] moof = bigThenMine(big, 0, four.length);
        moof = bigThenMine(big, moof.length + 8, four.length);
        List<Emitted> docs = extract(cat(fragmented(5, 1), moof, box("mdat", four)),
                new Metadata());
        assertTrue(docs.get(0).body().contains("frag four"), docs.get(0).body());
    }

    private static byte[] bigThenMine(byte[] bigTrun, int dataOffset, int size) {
        byte[] video = box("traf", box("tfhd", u32(0x020000), u32(1)), bigTrun);
        byte[] mine = box("traf", box("tfhd", u32(0x020000), u32(2)),
                box("trun", u32(0x000201), u32(1), u32(dataOffset), u32(size)));
        return box("moof", box("mfhd", u32(0), u32(4)), video, mine);
    }

    // display-order 608 pairs of a pop-on "HELLO", one per frame (controls doubled)
    private static final int[][] HELLO = {
            {0x14, 0x20}, {0x14, 0x20}, {0x14, 0x60}, {0x14, 0x60}, {'H', 'E'}, {'L', 'L'},
            {'O', 0}, {0x14, 0x2F}, {0x14, 0x2F}, {0x14, 0x2C}, {0x14, 0x2C}};

    @Test
    public void testCaptionsFromAvcSeiInPresentationOrder() throws Exception {
        // decode order swaps frames 4 and 5 ("LL" before "HE"); ctts puts them back
        int[] decodeOrder = {0, 1, 2, 3, 5, 4, 6, 8, 7, 9, 10};
        byte[][] samples = new byte[decodeOrder.length][];
        long[][] ctts = new long[decodeOrder.length][];
        for (int k = 0; k < decodeOrder.length; k++) {
            int[] p = HELLO[decodeOrder[k]];
            samples[k] = cat(nal(seiNal(cc608(p[0], p[1]))), nal(new byte[]{0x65, (byte) 0x88}));
            ctts[k] = new long[]{1, (decodeOrder[k] - k) * 1000L + 2000};
        }
        List<Emitted> docs = extract(videoMp4("avc1", avcC(), samples, ctts, 1000), new Metadata());
        assertEquals(1, docs.size());
        assertEquals("track-1-cc1.vtt", docs.get(0).metadata().get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("WEBVTT\n\n00:00:09.000 --> 00:00:11.000\nHELLO\n", docs.get(0).body());
    }

    @Test
    public void testDoubledControlAcrossReorderedFrames() throws Exception {
        // EOC doubled on presentation frames 9 and 10 after padding, stored in decode order
        // 10, 9: it must still act once, not swap the caption straight back off the screen
        int pad = 0x80;
        int[][] display = {{0x14, 0x20}, {0x14, 0x20}, {0x14, 0x60}, {0x14, 0x60}, {'H', 'E'},
                {'L', 'L'}, {'O', 0}, {pad, pad}, {pad, pad}, {0x14, 0x2F}, {0x14, 0x2F},
                {pad, pad}, {pad, pad}, {pad, pad}, {0x14, 0x2C}, {0x14, 0x2C}};
        int[] decodeOrder = {0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 9, 11, 12, 13, 14, 15};
        byte[][] samples = new byte[decodeOrder.length][];
        long[][] ctts = new long[decodeOrder.length][];
        for (int k = 0; k < decodeOrder.length; k++) {
            int[] p = display[decodeOrder[k]];
            samples[k] = cat(nal(seiNal(cc608(p[0], p[1]))), nal(new byte[]{0x65, (byte) 0x88}));
            ctts[k] = new long[]{1, (decodeOrder[k] - k) * 1000L + 2000};
        }
        List<Emitted> docs = extract(videoMp4("avc1", avcC(), samples, ctts, 1000), new Metadata());
        assertEquals("WEBVTT\n\n00:00:11.000 --> 00:00:16.000\nHELLO\n", docs.get(0).body());
    }

    @Test
    public void testCaptionsFromC608Track() throws Exception {
        byte[][] samples = new byte[HELLO.length][];
        for (int k = 0; k < HELLO.length; k++) {
            samples[k] = box("cdat", new byte[]{(byte) parity(HELLO[k][0]),
                    (byte) parity(HELLO[k][1])});
        }
        List<Emitted> docs = extract(videoMp4("c608", new byte[0], samples, null, 1000),
                new Metadata());
        assertEquals(1, docs.size());
        assertEquals("WEBVTT\n\n00:00:07.000 --> 00:00:09.000\nHELLO\n", docs.get(0).body());
    }

    @Test
    public void testProbeFindsPaddingAndMissesShortGaps() throws Exception {
        // 1000 frames of 33 ms; "HELLO" at frames 700-710. With padding cc_data from frame 600
        // the probe window near 20 s sees caption data and the full pass finds the text.
        byte[] padded = captionedAt(600, 700);
        List<Emitted> docs = extract(padded, new Metadata());
        assertEquals(1, docs.size());
        // the full pass restarts at time zero: HELLO's EOC is frame 707, its EDM frame 709
        assertEquals("WEBVTT\n\n00:00:23.331 --> 00:00:23.397\nHELLO\n", docs.get(0).body());
        // with no padding the run falls between probe windows: only a full scan finds it
        byte[] bare = captionedAt(Integer.MAX_VALUE, 700);
        assertEquals(0, extract(bare, new Metadata()).size());
        assertEquals(1, extract(bare, new Metadata(), new Mp4SubtitleExtractor(true, true, true))
                .size());
    }

    private static byte[] captionedAt(int paddingFrom, int helloAt) throws IOException {
        byte[][] samples = new byte[1000][];
        for (int k = 0; k < samples.length; k++) {
            byte[] slice = nal(new byte[]{0x65, (byte) 0x88});
            if (k >= helloAt && k < helloAt + HELLO.length) {
                int[] p = HELLO[k - helloAt];
                samples[k] = cat(nal(seiNal(cc608(p[0], p[1]))), slice);
            } else if (k >= paddingFrom) {
                samples[k] = cat(nal(seiNal(cc608(0x80, 0x80))), slice);
            } else {
                samples[k] = slice;
            }
        }
        return videoMp4("avc1", avcC(), samples, null, 33);
    }

    @Test
    public void testCaptionsOffLeavesVideoAlone() throws Exception {
        byte[][] samples = new byte[HELLO.length][];
        for (int k = 0; k < HELLO.length; k++) {
            samples[k] = nal(seiNal(cc608(HELLO[k][0], HELLO[k][1])));
        }
        byte[] mp4 = videoMp4("avc1", avcC(), samples, null, 1000);
        assertEquals(0, extract(mp4, new Metadata(), new Mp4SubtitleExtractor(true, false, false))
                .size());
    }

    private static final String HELLO_VTT = "WEBVTT\n\n00:00:07.000 --> 00:00:09.000\nHELLO\n";

    private static byte[][] helloSamples(java.util.function.Function<int[], byte[]> sample) {
        byte[][] samples = new byte[HELLO.length][];
        for (int k = 0; k < HELLO.length; k++) {
            samples[k] = sample.apply(HELLO[k]);
        }
        return samples;
    }

    @Test
    public void testCaptionsFromHevcSei() throws Exception {
        // HEVC: 2-byte NAL headers, prefix SEI type 39 (0x4E01), slice type 1 (0x0201)
        byte[][] samples = helloSamples(p -> cat(
                nal(seiNal(new byte[]{0x4E, 0x01}, cc608(p[0], p[1]))),
                nal(new byte[]{0x02, 0x01, (byte) 0x80})));
        List<Emitted> docs = extract(videoMp4("hvc1", hvcC(), samples, null, 1000),
                new Metadata());
        assertEquals(1, docs.size());
        assertEquals(HELLO_VTT, docs.get(0).body());
    }

    @Test
    public void testNalLengthSizes() throws Exception {
        byte[][] samples = helloSamples(p -> cat(nal2(seiNal(cc608(p[0], p[1]))),
                nal2(new byte[]{0x65, (byte) 0x88})));
        assertEquals(HELLO_VTT,
                extract(videoMp4("avc1", avcC(2), samples, null, 1000), new Metadata()).get(0)
                        .body());
        // a length size of 3 is not allowed: well-formed 3-byte lengths are still not read
        byte[][] three = helloSamples(p -> cat(nal3(seiNal(cc608(p[0], p[1]))),
                nal3(new byte[]{0x65, (byte) 0x88})));
        assertEquals(0, extract(videoMp4("avc1", avcC(3), three, null, 1000), new Metadata())
                .size());
    }

    @Test
    public void testSeiAfterManyNals() throws Exception {
        // twelve 30-byte filler NALs (type 12) before the caption SEI, as on HDR keyframes:
        // past the old limit of 8, and across several header windows
        byte[] filler = new byte[30];
        filler[0] = 0x0C;
        byte[] fillers = new byte[0];
        for (int i = 0; i < 12; i++) {
            fillers = cat(fillers, nal(filler));
        }
        byte[] prefix = fillers;
        byte[][] samples = helloSamples(p -> cat(prefix, nal(seiNal(cc608(p[0], p[1]))),
                nal(new byte[]{0x65, (byte) 0x88})));
        assertEquals(HELLO_VTT, extract(videoMp4("avc1", avcC(), samples, null, 1000),
                new Metadata()).get(0).body());
    }

    @Test
    public void testSeiAfterTheSliceIsIgnored() throws Exception {
        byte[][] samples = helloSamples(p -> cat(nal(new byte[]{0x65, (byte) 0x88}),
                nal(seiNal(cc608(p[0], p[1])))));
        assertEquals(0, extract(videoMp4("avc1", avcC(), samples, null, 1000), new Metadata())
                .size());
    }

    @Test
    public void testC608FieldTwoIsCc3() throws Exception {
        byte[][] samples = helloSamples(p -> box("cdt2", new byte[]{(byte) parity(p[0]),
                (byte) parity(p[1])}));
        List<Emitted> docs = extract(videoMp4("c608", new byte[0], samples, null, 1000),
                new Metadata());
        assertEquals("track-1-cc3.vtt",
                docs.get(0).metadata().get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testCaptionsInFragments() throws Exception {
        // moov with an empty avc1 track, then one moof whose trun carries sizes, durations
        // and composition offsets for the swapped decode order
        int[] decodeOrder = {0, 1, 2, 3, 5, 4, 6, 8, 7, 9, 10};
        byte[][] samples = new byte[decodeOrder.length][];
        ByteArrayOutputStream entries = new ByteArrayOutputStream();
        for (int k = 0; k < decodeOrder.length; k++) {
            int[] p = HELLO[decodeOrder[k]];
            samples[k] = cat(nal(seiNal(cc608(p[0], p[1]))), nal(new byte[]{0x65, (byte) 0x88}));
            entries.writeBytes(u32(1000));
            entries.writeBytes(u32(samples[k].length));
            entries.writeBytes(u32((decodeOrder[k] - k) * 1000L + 2000));
        }
        byte[] empty = videoMp4("avc1", avcC(), new byte[0][], null, 1000);
        // drop the empty mdat's moov-less prefix: reuse its ftyp+mdat+moov, add mvex to moov
        byte[] mvex = box("mvex", box("trex", u32(0), u32(1), u32(1), u32(0), u32(0), u32(0)));
        byte[] head = withMvex(empty, mvex);
        byte[] moof = fragMoof(0, decodeOrder.length, entries.toByteArray());
        moof = fragMoof(moof.length + 8, decodeOrder.length, entries.toByteArray());
        List<Emitted> docs = extract(cat(head, moof, box("mdat", cat(samples))), new Metadata());
        assertEquals(1, docs.size());
        assertEquals("WEBVTT\n\n00:00:09.000 --> 00:00:11.000\nHELLO\n", docs.get(0).body());
    }

    private static byte[] fragMoof(int dataOffset, int count, byte[] entries) {
        byte[] traf = box("traf", box("tfhd", u32(0x020000), u32(1)),
                box("tfdt", u32(0), u32(0)),
                box("trun", u32(0x000B01), u32(count), u32(dataOffset), entries));
        return box("moof", box("mfhd", u32(0), u32(1)), traf);
    }

    // appends mvex to the last top-level box (moov) of an ftyp+mdat+moov file
    private static byte[] withMvex(byte[] mp4, byte[] mvex) {
        int moovAt = mp4.length;
        for (int pos = 0; pos + 8 <= mp4.length; ) {
            int size = ByteBuffer.wrap(mp4, pos, 4).getInt();
            if (new String(mp4, pos + 4, 4, StandardCharsets.US_ASCII).equals("moov")) {
                moovAt = pos;
            }
            pos += size;
        }
        byte[] moovBody = Arrays.copyOfRange(mp4, moovAt + 8, mp4.length);
        return cat(Arrays.copyOf(mp4, moovAt), box("moov", moovBody, mvex));
    }

    @Test
    public void testCaptionSettingsThroughMp4Parser() throws Exception {
        byte[][] samples = helloSamples(p -> cat(nal(seiNal(cc608(p[0], p[1]))),
                nal(new byte[]{0x65, (byte) 0x88})));
        byte[] mp4 = videoMp4("avc1", avcC(), samples, null, 1000);
        MP4Parser parser = new MP4Parser();
        assertEquals(2, getRecursiveMetadata(TikaInputStream.get(mp4), parser, new Metadata(),
                new ParseContext(), true).size());
        parser.setExtractClosedCaptions(false);
        assertEquals(1, getRecursiveMetadata(TikaInputStream.get(mp4), parser, new Metadata(),
                new ParseContext(), true).size());
        parser.setExtractClosedCaptions(true);
        parser.setExtractSubtitles(false);
        assertEquals(1, getRecursiveMetadata(TikaInputStream.get(mp4), parser, new Metadata(),
                new ParseContext(), true).size());

        Parser configured = TikaLoader
                .load(getConfigPath(Mp4SubtitleExtractorTest.class,
                        "tika-config-mp4-no-captions.json"))
                .loadParsers();
        Metadata m = new Metadata();
        m.set(HttpHeaders.CONTENT_TYPE, "video/mp4");
        assertEquals(1, getRecursiveMetadata(TikaInputStream.get(mp4), configured, m,
                new ParseContext(), true).size());
    }

    /** ftyp, mdat, then moov with one video track (timescale 1000), one sample per chunk. */
    private static byte[] videoMp4(String format, byte[] entryChildren, byte[][] samples,
                                   long[][] ctts, long delta) throws IOException {
        byte[] ftyp = box("ftyp", ascii("isom"), u32(0), ascii("isom"));
        byte[] mdat = box("mdat", cat(samples));
        long offset = ftyp.length + 8L;
        ByteArrayOutputStream stsz = new ByteArrayOutputStream();
        ByteArrayOutputStream stco = new ByteArrayOutputStream();
        for (byte[] sample : samples) {
            stsz.writeBytes(u32(sample.length));
            stco.writeBytes(u32(offset));
            offset += sample.length;
        }
        boolean visual = !format.equals("c608");
        // VisualSampleEntry fields after reserved + data_reference_index: 70 bytes
        byte[] entry = box(format, new byte[6], new byte[]{0, 1},
                visual ? new byte[70] : new byte[0], entryChildren);
        ByteArrayOutputStream cttsBody = new ByteArrayOutputStream();
        if (ctts != null) {
            for (long[] e : ctts) {
                cttsBody.writeBytes(u32(e[0]));
                cttsBody.writeBytes(u32(e[1]));
            }
        }
        byte[] stbl = box("stbl", box("stsd", u32(0), u32(1), entry),
                box("stts", u32(0), u32(1), u32(samples.length), u32(delta)),
                ctts == null ? new byte[0] : box("ctts", u32(0), u32(ctts.length),
                        cttsBody.toByteArray()),
                box("stsc", u32(0), u32(1), u32(1), u32(1), u32(1)),
                box("stsz", u32(0), u32(0), u32(samples.length), stsz.toByteArray()),
                box("stco", u32(0), u32(samples.length), stco.toByteArray()));
        byte[] tkhd = new byte[84];
        ByteBuffer.wrap(tkhd).putInt(12, 1);
        byte[] mdhd = cat(u32(0), u32(0), u32(0), u32(1000), u32(0), new byte[]{0x55, (byte) 0xC4, 0, 0});
        byte[] hdlr = cat(u32(0), u32(0), ascii(visual ? "vide" : "clcp"), new byte[13]);
        byte[] trak = box("trak", box("tkhd", tkhd),
                box("mdia", box("mdhd", mdhd), box("hdlr", hdlr), box("minf", stbl)));
        return cat(ftyp, mdat, box("moov", box("mvhd", new byte[100]), trak));
    }

    // avcC with lengthSizeMinusOne = 3
    private static byte[] avcC() {
        return avcC(4);
    }

    private static byte[] avcC(int lengthSize) {
        return box("avcC", new byte[]{1, 0x64, 0, 0x1F, (byte) (0xFC | (lengthSize - 1)),
                (byte) 0xE0, 0});
    }

    // hvcC: lengthSizeMinusOne in the low bits of byte 21
    private static byte[] hvcC() {
        byte[] p = new byte[23];
        p[0] = 1;
        p[21] = 0x03;
        return box("hvcC", p);
    }

    private static byte[] nal(byte[] body) {
        return cat(u32(body.length), body);
    }

    private static byte[] nal3(byte[] body) {
        return cat(new byte[]{0, (byte) (body.length >> 8), (byte) body.length}, body);
    }

    private static byte[] nal2(byte[] body) {
        return cat(new byte[]{(byte) (body.length >> 8), (byte) body.length}, body);
    }

    // SEI NAL with one T.35 GA94 cc_data message; emulation prevention applied
    private static byte[] seiNal(byte[] ccData) {
        return seiNal(new byte[]{0x06}, ccData);
    }

    private static byte[] seiNal(byte[] header, byte[] ccData) {
        byte[] t35 = cat(new byte[]{(byte) 0xB5, 0x00, 0x31, 'G', 'A', '9', '4', 0x03}, ccData);
        byte[] rbsp = cat(header, new byte[]{4, (byte) t35.length}, t35,
                new byte[]{(byte) 0x80});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int zeros = 0;
        for (int i = 0; i < rbsp.length; i++) {
            int v = rbsp[i] & 0xFF;
            if (zeros >= 2 && v <= 3) {
                out.write(3);
                zeros = 0;
            }
            out.write(v);
            zeros = v == 0 ? zeros + 1 : 0;
        }
        return out.toByteArray();
    }

    private static byte[] cc608(int b1, int b2) {
        return new byte[]{0x40 | 1, (byte) 0xFF, (byte) 0xFC, (byte) parity(b1),
                (byte) parity(b2)};
    }

    private static int parity(int c) {
        return Integer.bitCount(c & 0x7F) % 2 == 0 ? (c & 0x7F) | 0x80 : c & 0x7F;
    }

    private static byte[] fullMoof(long base, int size1, int size2) {
        byte[] tfhd = box("tfhd", u32(0x000009), u32(2), u64(base), u32(3000));
        // data-offset, first-sample-flags, then duration, size, flags, composition offset
        byte[] run1 = box("trun", u32(0x000F05), u32(1), u32(0), u32(0), u32(500), u32(size1),
                u32(0), u32(0));
        byte[] run2 = box("trun", u32(0x000200), u32(1), u32(size2));
        return box("moof", box("mfhd", u32(0), u32(1)), box("traf", tfhd, run1, run2));
    }

    @Test
    public void testSampleReadIsBoundedByDeclaredText() throws Exception {
        // a sample may declare 64 KB; only the 2-byte length and the text it names are read
        byte[] data = cat(Builder.sample(new byte[0]), new byte[0xFFFF],
                Builder.sample("hi".getBytes(StandardCharsets.UTF_8)), new byte[0xFFFF]);
        try (TikaInputStream tis = TikaInputStream.get(data);
                CountingChannel ch = new CountingChannel(tis.getSeekableByteChannel())) {
            assertEquals("", Mp4SubtitleExtractor.sampleText(ch, 0, 0xFFFF + 2));
            assertEquals(2, ch.read);
            assertEquals("hi", Mp4SubtitleExtractor.sampleText(ch, 0xFFFF + 2, 0xFFFF + 2));
            assertEquals(2 + 4, ch.read);
        }
    }

    private static final class CountingChannel implements SeekableByteChannel {
        private final SeekableByteChannel delegate;
        long read;

        CountingChannel(SeekableByteChannel delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            int n = delegate.read(dst);
            read += Math.max(n, 0);
            return n;
        }

        @Override
        public int write(ByteBuffer src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public SeekableByteChannel position(long p) throws IOException {
            delegate.position(p);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "fragmented", "captions"})
    @Timeout(60)
    public void testCorruptedBytes(String kind) throws Exception {
        byte[] good = kind.equals("fragmented") ? fragmented(5, 1)
                : kind.equals("captions") ? captionedAt(0, 20) : new Builder().build(true);
        long seed = new Random().nextLong();
        Random r = new Random(seed);
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
                extract(bad, new Metadata());
            } catch (IOException e) {
                // a box running past the end is an IOException; anything else fails the test
            } catch (RuntimeException e) {
                throw new AssertionError("seed=" + seed + " iteration=" + i, e);
            }
        }
    }

    @Test
    public void testLanguage() {
        assertEquals("eng", Mp4SubtitleExtractor.language(ENG));
        assertEquals("eng", Mp4SubtitleExtractor.language(0));
        assertEquals("spa", Mp4SubtitleExtractor.language(6));
        assertEquals("cym", Mp4SubtitleExtractor.language(128));
        assertEquals("aze", Mp4SubtitleExtractor.language(150));
        assertNull(Mp4SubtitleExtractor.language(95));
        assertNull(Mp4SubtitleExtractor.language(151));
        assertNull(Mp4SubtitleExtractor.language(0x7FFF));
        assertNull(Mp4SubtitleExtractor.language((21 << 10) | (14 << 5) | 4));
    }

    /**
     * ftyp, moov with empty sample tables and trex (track 2: duration 1000), then two
     * moof+mdat pairs. Fragment 1: a video traf (default-base-is-moof, {@code videoRuns}
     * default-sized 8-byte samples) then a subtitle traf with no base, so its data follows
     * the video's; tfdt v1 at {@code startSeconds}. Fragment 2: subtitle only, tfhd default
     * duration 2000, tfdt v0 at 9s.
     */
    private static byte[] fragmented(int startSeconds, long videoRuns) throws IOException {
        byte[] ftyp = box("ftyp", ascii("iso6"), u32(0), ascii("iso6"));
        byte[] video = Builder.trak(1, "vide", ENG, new long[0][], new int[0], 1, new int[0], -1,
                new long[0], false, null);
        byte[] subs = Builder.trak(2, "sbtl", ENG, new long[0][], new int[0], 1, new int[0], -1,
                new long[0], false, null);
        byte[] mvex = box("mvex", box("trex", u32(0), u32(2), u32(1), u32(1000), u32(0),
                u32(0)));
        byte[] moov = box("moov", box("mvhd", new byte[100]), video, subs, mvex);

        byte[] one = Builder.sample("frag one".getBytes(StandardCharsets.UTF_8));
        byte[] two = Builder.sample("frag two".getBytes(StandardCharsets.UTF_8));
        byte[] videoData = new byte[8];
        // moof length is fixed by its fields, so build once to measure, then for real
        byte[] moof1 = moof1(0, videoRuns, one.length, two.length, startSeconds);
        moof1 = moof1(moof1.length + 8, videoRuns, one.length, two.length, startSeconds);
        byte[] mdat1 = box("mdat", videoData, one, two);

        byte[] three = Builder.sample("frag three".getBytes(StandardCharsets.UTF_8));
        byte[] moof2 = moof2(0, three.length);
        moof2 = moof2(moof2.length + 8, three.length);
        byte[] mdat2 = box("mdat", three);
        return cat(ftyp, moov, moof1, mdat1, moof2, mdat2);
    }

    private static byte[] moof1(int dataOffset, long videoRuns, int size1, int size2,
                                int startSeconds) {
        byte[] videoTraf = box("traf",
                box("tfhd", u32(0x020010), u32(1), u32(8)),
                box("trun", u32(0x000001), u32(videoRuns), u32(dataOffset)));
        byte[] subTraf = box("traf",
                box("tfhd", u32(0), u32(2)),
                box("tfdt", u32(0x01000000), u64(startSeconds * 1000L)),
                box("trun", u32(0x000200), u32(2), u32(size1), u32(size2)));
        return box("moof", box("mfhd", u32(0), u32(1)), videoTraf, subTraf);
    }

    private static byte[] moof2(int dataOffset, int size) {
        byte[] subTraf = box("traf",
                box("tfhd", u32(0x020008), u32(2), u32(2000)),
                box("tfdt", u32(0), u32(9000)),
                box("trun", u32(0x000201), u32(1), u32(dataOffset), u32(size)));
        return box("moof", box("mfhd", u32(0), u32(2)), subTraf);
    }

    /**
     * ftyp, mdat (samples), then moov: a video-ish track 1 naming track 3 as its chapters, a
     * tx3g subtitle track 2, a tx3g chapter track 3. moov after mdat, as in a file that was
     * not made streamable.
     */
    private static final class Builder {
        int sampleCount = -1;
        int[] stscFirstChunks = {1};
        long firstChunkOffset = -1;
        int perChunk = 2;

        byte[] build(boolean co64) throws IOException {
            byte[] ftyp = box("ftyp", ascii("isom"), u32(0), ascii("isom"));
            byte[][] samples = {
                    sample("Hello <world> & co".getBytes(StandardCharsets.UTF_8)),
                    sample(new byte[0]),
                    sample("line one\n\nline two\r\n".getBytes(StandardCharsets.UTF_8)),
                    sample(cat(new byte[]{(byte) 0xFE, (byte) 0xFF},
                            "naïve ☺".getBytes(StandardCharsets.UTF_16BE)))};
            byte[] chapter = sample("Chapter 1".getBytes(StandardCharsets.UTF_8));
            byte[] mdatPayload = cat(cat(samples), chapter);
            long mdatData = ftyp.length + 8L;
            byte[] mdat = box("mdat", mdatPayload);

            long chunk1 = firstChunkOffset >= 0 ? firstChunkOffset : mdatData;
            long chunk2 = mdatData + samples[0].length + samples[1].length;
            long chapterOffset = mdatData + cat(samples).length;
            int[] sizes = new int[samples.length];
            for (int i = 0; i < samples.length; i++) {
                sizes[i] = samples[i].length;
            }
            byte[] subs = trak(2, "sbtl", ENG, new long[][]{{1, 1000}, {1, 500}, {2, 1000}},
                    stscFirstChunks, perChunk, sizes, sampleCount,
                    new long[]{chunk1, chunk2}, co64,
                    null);
            byte[] chapters = trak(3, "text", ENG, new long[][]{{1, 5000}}, new int[]{1}, 1,
                    new int[]{chapter.length}, -1, new long[]{chapterOffset}, co64, null);
            byte[] video = trak(1, "vide", ENG, new long[0][], new int[]{1}, 1, new int[0], -1,
                    new long[0], co64, box("tref", box("chap", u32(3))));
            byte[] moov = box("moov", box("mvhd", new byte[100]), video, subs, chapters);
            return cat(ftyp, mdat, moov);
        }

        static byte[] trak(int id, String handler, int lang, long[][] stts,
                                   int[] stscFirst, int perChunk, int[] sizes, int sizeCount,
                                   long[] chunks, boolean co64, byte[] tref)
                throws IOException {
            byte[] tkhd = new byte[84];
            ByteBuffer.wrap(tkhd).putInt(12, id);
            byte[] mdhd = cat(u32(0), u32(0), u32(0), u32(1000), u32(0),
                    new byte[]{(byte) (lang >> 8), (byte) lang, 0, 0});
            byte[] hdlr = cat(u32(0), u32(0), ascii(handler), new byte[13]);
            byte[] stsd = box("stsd", u32(0), u32(1),
                    box(handler.equals("vide") ? "avc1" : "tx3g", new byte[8]));
            ByteArrayOutputStream sttsBody = new ByteArrayOutputStream();
            for (long[] e : stts) {
                sttsBody.write(u32(e[0]));
                sttsBody.write(u32(e[1]));
            }
            ByteArrayOutputStream stscBody = new ByteArrayOutputStream();
            for (int f : stscFirst) {
                stscBody.write(u32(f));
                stscBody.write(u32(perChunk));
                stscBody.write(u32(1));
            }
            ByteArrayOutputStream stszBody = new ByteArrayOutputStream();
            for (int s : sizes) {
                stszBody.write(u32(s));
            }
            ByteArrayOutputStream chunkBody = new ByteArrayOutputStream();
            for (long c : chunks) {
                chunkBody.write(co64 ? u64(c) : u32(c));
            }
            byte[] stbl = box("stbl", stsd,
                    box("stts", u32(0), u32(stts.length), sttsBody.toByteArray()),
                    box("stsc", u32(0), u32(stscFirst.length), stscBody.toByteArray()),
                    box("stsz", u32(0), u32(0), u32(sizeCount >= 0 ? sizeCount : sizes.length),
                            stszBody.toByteArray()),
                    box(co64 ? "co64" : "stco", u32(0), u32(chunks.length),
                            chunkBody.toByteArray()));
            byte[] mdia = box("mdia", box("mdhd", mdhd), box("hdlr", hdlr),
                    box("minf", stbl));
            return tref == null ? box("trak", box("tkhd", tkhd), mdia)
                    : box("trak", box("tkhd", tkhd), tref, mdia);
        }

        static byte[] sample(byte[] text) {
            return cat(new byte[]{(byte) (text.length >> 8), (byte) text.length}, text);
        }
    }

    private static byte[] box(String type, byte[]... parts) {
        byte[] body = cat(parts);
        return cat(u32(8 + body.length), ascii(type), body);
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

    private static byte[] u32(long v) {
        return ByteBuffer.allocate(4).putInt((int) v).array();
    }

    private static byte[] u64(long v) {
        return ByteBuffer.allocate(8).putLong(v).array();
    }
}
