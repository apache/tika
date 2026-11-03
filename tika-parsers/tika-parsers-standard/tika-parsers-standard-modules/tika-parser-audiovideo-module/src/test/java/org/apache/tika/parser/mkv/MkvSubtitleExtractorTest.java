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
package org.apache.tika.parser.mkv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

public class MkvSubtitleExtractorTest extends TikaTest {

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
            new MkvSubtitleExtractor().extract(tis, container, xhtml, context);
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

        // header stripping; SimpleBlock has no duration
        assertEquals("WEBVTT\n\n00:00:02.000 --> 00:00:02.000\nHello stripped\n",
                docs.get(2).body());

        assertEquals("WEBVTT\n\n00:00:03.000 --> 00:00:04.000\nHi &amp; bye\n",
                docs.get(3).body());
        assertEquals("de", docs.get(3).metadata().get(TikaCoreProperties.LANGUAGE));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
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
    public void testTruncatedKeepsEarlierCues() throws Exception {
        byte[] full = sample();
        byte[] cut = Arrays.copyOf(full, full.length - 20);
        Metadata container = new Metadata();
        List<Emitted> docs = extract(cut, true, container);
        assertTrue(docs.get(0).body().contains("Bonjour"), docs.toString());
    }

    @Test
    @Timeout(60)
    public void testCorruptedBytes() throws Exception {
        byte[] good = sample();
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
                extract(bad, r.nextBoolean(), new Metadata());
            } catch (RuntimeException e) {
                throw new AssertionError("seed=" + seed + " iteration=" + i, e);
            }
        }
    }

    @Test
    public void testAssText() {
        assertEquals("a, b\nc", MkvSubtitleExtractor.assText("0,0,Default,,0,0,0,,{\\b1}a, b\\Nc"));
        assertEquals("", MkvSubtitleExtractor.assText("0,0,Default"));
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
