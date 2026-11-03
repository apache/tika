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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
        Capture capture = new Capture();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, capture);
        XHTMLContentHandler xhtml =
                new XHTMLContentHandler(new DefaultHandler(), container, context);
        xhtml.startDocument();
        try (TikaInputStream tis = TikaInputStream.get(mp4)) {
            new Mp4SubtitleExtractor().extract(tis, container, xhtml, context);
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
    @Timeout(60)
    public void testCorruptedBytes() throws Exception {
        byte[] good = new Builder().build(true);
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
        assertNull(Mp4SubtitleExtractor.language(0));
        assertNull(Mp4SubtitleExtractor.language((21 << 10) | (14 << 5) | 4));
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

        private static byte[] trak(int id, String handler, int lang, long[][] stts,
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

        private static byte[] sample(byte[] text) {
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
