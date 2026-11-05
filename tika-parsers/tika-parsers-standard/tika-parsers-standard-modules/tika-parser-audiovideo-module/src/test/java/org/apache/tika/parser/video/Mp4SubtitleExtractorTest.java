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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import org.apache.commons.io.function.IOSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.EmbeddedLimitReachedException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.mp4.MP4Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class Mp4SubtitleExtractorTest extends TikaTest {

    private static final int ENG = (5 << 10) | (14 << 5) | 7;

    /** One embedded document the extractor handed over. */
    private record Emitted(Metadata metadata, String body) {
    }

    private static final class Capture implements EmbeddedDocumentExtractor {
        final List<Emitted> docs = new ArrayList<>();
        final List<String> asked = new ArrayList<>();
        final Predicate<String> accept;

        Capture() {
            this(name -> true);
        }

        Capture(Predicate<String> accept) {
            this.accept = accept;
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata, ParseContext parseContext) {
            String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            asked.add(name);
            return accept.test(name);
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
        return extract(mp4, container, new Capture());
    }

    private static List<Emitted> extract(byte[] mp4, Metadata container, Capture capture)
            throws Exception {
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, capture);
        XHTMLContentHandler xhtml =
                new XHTMLContentHandler(new DefaultHandler(), container, context);
        xhtml.startDocument();
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            SubtitleTracks.readMp4(tis, container, context).emit(xhtml);
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
        assertTrue(Arrays.asList(list.get(0).getValues(TikaCoreProperties.TIKA_PARSED_BY))
                .contains(MP4Parser.class.getName()), list.get(0).toString());
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
        Metadata m = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(mp4))) {
            new MP4Parser().parse(tis, new BodyContentHandler(-1), m, new ParseContext());
            assertFalse(tis.hasFile(), "the stream was spooled");
        }
        assertTrue(Arrays.stream(m.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .noneMatch(w -> w.contains("subtitles")), m.toString());
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
                .contains("decoded bytes"), container.toString());
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
            SubtitleTracks budget = new SubtitleTracks(new Metadata(), new ParseContext());
            assertEquals("", Mp4SubtitleExtractor.sampleText(ch, 0, 0xFFFF + 2, budget, false));
            assertEquals(2, ch.read);
            assertEquals("hi",
                    Mp4SubtitleExtractor.sampleText(ch, 0xFFFF + 2, 0xFFFF + 2, budget, false));
            assertEquals(2 + 4, ch.read);
        }
    }

    @Test
    public void testQuickTimeTextFallsBackToMacRoman() throws Exception {
        // 0x8E is é in MacRoman and invalid as UTF-8
        byte[] mac = Builder.sample(new byte[]{'C', 'a', 'f', (byte) 0x8E});
        byte[] utf8 = Builder.sample("Café".getBytes(StandardCharsets.UTF_8));
        assertEquals("Café", sampleText(mac, true));
        assertEquals("Caf\uFFFD", sampleText(mac, false), "tx3g is UTF-8 by spec");
        assertEquals("Café", sampleText(utf8, true));

        // through a track: only a QuickTime text sample entry falls back
        byte[] mp4 = new Builder().build(true);
        mp4[indexOf(mp4, ascii("& co")) + 3] = (byte) 0x8E;
        assertTrue(extract(mp4, new Metadata()).get(0).body().contains("&amp; c\uFFFD"));
        System.arraycopy(ascii("text"), 0, mp4, indexOf(mp4, ascii("tx3g")), 4);
        assertTrue(extract(mp4, new Metadata()).get(0).body().contains("&amp; cé"),
                "QuickTime text");
    }

    private static String sampleText(byte[] sample, boolean macText) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(sample);
                SeekableByteChannel ch = tis.getSeekableByteChannel()) {
            return Mp4SubtitleExtractor.sampleText(ch, 0, sample.length,
                    new SubtitleTracks(new Metadata(), new ParseContext()), macText);
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
    @ValueSource(booleans = {false, true})
    public void testCorruptedBytes(boolean fragmented) throws Exception {
        byte[] good = fragmented ? fragmented(5, 1) : new Builder().build(true);
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
                extract(bad, new Metadata());
            } catch (RuntimeException | Error e) {
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


    @Test
    public void testByteArrayOverSpoolLimit() throws Exception {
        // already in memory, so random access is free at any size
        byte[] small = new Builder().build(true);
        int free = 65 * 1024 * 1024;
        byte[] mp4 = Arrays.copyOf(small, small.length + free);
        System.arraycopy(u32(free), 0, mp4, small.length, 4);
        System.arraycopy(ascii("free"), 0, mp4, small.length + 4, 4);
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, "x.mp4");
        List<Metadata> list = getRecursiveMetadata(TikaInputStream.get(mp4),
                AUTO_DETECT_PARSER, m, new ParseContext(), true);
        assertEquals(2, list.size());
        assertEquals("track-2.vtt", list.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testFileInputOverSpoolLimit(@TempDir Path tmp) throws Exception {
        // the production path: a file input of any size; a sparse 100 MB tail proves it
        byte[] mp4 = new Builder().build(true);
        Path p = Files.createTempFile(tmp, "subs", ".mp4");
        long free = 100L * 1024 * 1024;
        try (RandomAccessFile f = new RandomAccessFile(p.toFile(), "rw")) {
            f.write(mp4);
            f.write(u32(free));
            f.write(ascii("free"));
            f.setLength(mp4.length + free);
        }
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, "x.mp4");
        List<Metadata> list = getRecursiveMetadata(TikaInputStream.get(p),
                AUTO_DETECT_PARSER, m, new ParseContext(), true);
        assertEquals(2, list.size());
        assertEquals("track-2.vtt", list.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testDeclaredLengthIsNotTrusted() throws Exception {
        // a Content-Length hint may lie, so even an honest one does not get a stream buffered
        byte[] mp4 = new Builder().build(true);
        Metadata m = new Metadata();
        m.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(mp4.length));
        List<Metadata> list = getRecursiveMetadata(
                TikaInputStream.get(new ByteArrayInputStream(mp4), m),
                new MP4Parser(), m, new ParseContext(), true);
        assertEquals(1, list.size());
        assertTrue(Arrays.stream(list.get(0).getValues(
                TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .noneMatch(w -> w.contains("subtitles")), list.get(0).toString());
    }

    @Test
    public void testEmptySamplesAreChargedPerFile() throws Exception {
        // two tracks of 600k empty samples: each read costs, though nothing decodes
        int n = 600_000;
        byte[] ftyp = box("ftyp", ascii("isom"), u32(0), ascii("isom"));
        byte[] mdat = box("mdat", new byte[2 * n + 16]);
        long dataStart = ftyp.length + 8L;
        int[] sizes = new int[n];
        Arrays.fill(sizes, 2);
        byte[] t2 = Builder.trak(2, "sbtl", ENG, new long[][]{{n, 1}}, new int[]{1}, n, sizes,
                -1, new long[]{dataStart}, false, null);
        byte[] t3 = Builder.trak(3, "sbtl", ENG, new long[][]{{n, 1}}, new int[]{1}, n, sizes,
                -1, new long[]{dataStart}, false, null);
        Metadata container = new Metadata();
        assertEquals(0, extract(cat(ftyp, mdat, box("moov", box("mvhd", new byte[100]), t2, t3)),
                container).size());
        assertTrue(String.valueOf(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .contains("subtitle samples"), container.toString());
    }

    @Test
    public void testWriteLimitKeepsContainerMetadata() throws Exception {
        // tracks are emitted after the metadata walk, so a limit hit on them costs nothing else
        byte[] mp4 = new Builder().build(true);
        int mvhd = indexOf(mp4, ascii("mvhd")) + 4;
        ByteBuffer.wrap(mp4).putInt(mvhd + 12, 1000).putInt(mvhd + 16, 5000);
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, "x.mp4");
        ParseContext context = new ParseContext();
        context.set(Parser.class, AUTO_DETECT_PARSER);
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            assertThrows(WriteLimitReachedException.class, () ->
                    AUTO_DETECT_PARSER.parse(tis, new BodyContentHandler(5), m, context));
        }
        assertEquals("5.0", m.get(XMPDM.DURATION), m.toString());
    }

    @Test
    public void testUnsupportedSampleEntryIsSkippedSilently() throws Exception {
        byte[] mp4 = new Builder().build(true);
        // the subtitle track's tx3g entry becomes wvtt: a valid track Tika does not decode
        int i = indexOf(mp4, ascii("tx3g"));
        System.arraycopy(ascii("wvtt"), 0, mp4, i, 4);
        Metadata container = new Metadata();
        assertEquals(0, extract(mp4, container).size());
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING),
                container.toString());
    }

    private static byte[] withoutTextTrack() throws IOException {
        byte[] mp4 = new Builder().build(true);
        System.arraycopy(ascii("vide"), 0, mp4, indexOf(mp4, ascii("sbtl")), 4);
        return mp4;
    }

    private static byte[] withDuration(byte[] mp4) {
        int mvhd = indexOf(mp4, ascii("mvhd")) + 4;
        ByteBuffer.wrap(mp4).putInt(mvhd + 12, 1000).putInt(mvhd + 16, 5000);
        return mp4;
    }

    private static Metadata parseWithLimits(byte[] mp4, EmbeddedLimits limits) throws Exception {
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, "x.mp4");
        ParseContext context = new ParseContext();
        context.set(EmbeddedLimits.class, limits);
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            AUTO_DETECT_PARSER.parse(tis, new BodyContentHandler(-1), m, context);
        }
        return m;
    }

    @Test
    public void testNoTextTrackReportsNoEmbeddedLimit() throws Exception {
        Metadata m = parseWithLimits(withoutTextTrack(), new EmbeddedLimits(0, false, -1, false));
        assertNull(m.get(TikaCoreProperties.EMBEDDED_DEPTH_LIMIT_REACHED), m.toString());
        // a real track at the limit is reported
        m = parseWithLimits(new Builder().build(true), new EmbeddedLimits(0, false, -1, false));
        assertEquals("true", m.get(TikaCoreProperties.EMBEDDED_DEPTH_LIMIT_REACHED), m.toString());
    }

    @Test
    public void testThrowOnLimitThrowsAfterContainerMetadata() throws Exception {
        byte[] mp4 = withDuration(new Builder().build(true));
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, "x.mp4");
        ParseContext context = new ParseContext();
        context.set(EmbeddedLimits.class, new EmbeddedLimits(0, true, -1, false));
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            assertThrows(EmbeddedLimitReachedException.class, () ->
                    AUTO_DETECT_PARSER.parse(tis, new BodyContentHandler(-1), m, context));
        }
        assertEquals("5.0", m.get(XMPDM.DURATION), m.toString());
    }

    @Test
    public void testExtractorJudgesEachTrackByItsOwnName() throws Exception {
        Capture refuseAll = new Capture(name -> false);
        assertEquals(0, extract(new Builder().build(true), new Metadata(), refuseAll).size());
        assertEquals(List.of("track-2.vtt"), refuseAll.asked);
        Capture onlyTrack2 = new Capture(name -> name.equals("track-2.vtt"));
        assertEquals(1, extract(new Builder().build(true), new Metadata(), onlyTrack2).size());
    }

    @Test
    public void testZeroLengthSamplesAreChargedAndReported() throws Exception {
        int n = SubtitleTracks.MAX_SAMPLES + 10;
        byte[] ftyp = box("ftyp", ascii("isom"), u32(0), ascii("isom"));
        byte[] mdat = box("mdat", new byte[16]);
        byte[] t2 = Builder.trak(2, "sbtl", ENG, new long[][]{{n, 1}}, new int[]{1}, n,
                new int[n], -1, new long[]{ftyp.length + 8L}, false, null);
        Metadata container = new Metadata();
        assertEquals(0, extract(cat(ftyp, mdat, box("moov", box("mvhd", new byte[100]), t2)),
                container).size());
        assertTrue(String.valueOf(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .contains("subtitle samples"), container.toString());
    }

    private int tracksFromReopenable(byte[] mp4, CacheMemoryBudget budget)
            throws Exception {
        IOSupplier<InputStream> opener = () -> new ByteArrayInputStream(mp4);
        try (TemporaryResources tmp = new TemporaryResources();
                TikaInputStream tis = TikaInputStream.get(opener, tmp, null)) {
            if (budget != null) {
                tis.enableRewind(budget);
            }
            return getRecursiveMetadata(tis, new MP4Parser(), new Metadata(), new ParseContext(),
                    true).size() - 1;
        }
    }

    @Test
    public void testReopenableSourceIsRetainedWithinBudget() throws Exception {
        // an archive entry: under the 1 MB floor it is retained; above it only with a budget
        byte[] small = new Builder().build(true);
        assertEquals(1, tracksFromReopenable(small, null));
        byte[] big = cat(small, box("free", new byte[2 * 1024 * 1024]));
        assertEquals(0, tracksFromReopenable(big, null));
        assertEquals(1, tracksFromReopenable(big, new CacheMemoryBudget(64L * 1024 * 1024)));
    }

    @Test
    public void testNulOnlyLineDoesNotEndTheCue() {
        assertEquals("a\nb", SubtitleTrack.cueText("a\n\u0000\nb"));
        assertEquals("a\nb", SubtitleTrack.cueText("a\n \u0000 \nb"));
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new AssertionError("not found");
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
