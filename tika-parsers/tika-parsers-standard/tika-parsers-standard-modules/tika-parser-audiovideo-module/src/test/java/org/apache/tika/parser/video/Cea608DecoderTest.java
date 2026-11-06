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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

public class Cea608DecoderTest {

    private static final int[] RCL = {0x14, 0x20};
    private static final int[] EOC = {0x14, 0x2F};
    private static final int[] EDM = {0x14, 0x2C};
    private static final int[] ENM = {0x14, 0x2E};
    private static final int[] CR = {0x14, 0x2D};
    private static final int[] RU2 = {0x14, 0x25};
    private static final int[] RDC = {0x14, 0x29};
    // PAC row 15, column 0
    private static final int[] ROW15 = {0x14, 0x60};

    /** An emitted track: resource name and WebVTT. */
    private record Doc(String name, String vtt) {
    }

    /** Takes every track and keeps what is emitted. */
    private static final class Capture implements EmbeddedDocumentExtractor {
        final List<Doc> docs = new ArrayList<>();

        @Override
        public boolean shouldParseEmbedded(Metadata metadata, ParseContext parseContext) {
            return true;
        }

        @Override
        public void parseEmbedded(TikaInputStream stream, ContentHandler handler,
                                  Metadata metadata, ParseContext parseContext, boolean outputHtml)
                throws IOException {
            docs.add(new Doc(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY),
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    /** Feeds control codes twice (as broadcast) and text two characters per pair. */
    private static final class Feed {
        final Capture capture = new Capture();
        final ParseContext context = new ParseContext();
        final SubtitleTracks owner;
        final Cea608Decoder decoder;
        long t;

        Feed() throws Exception {
            context.set(EmbeddedDocumentExtractor.class, capture);
            owner = new SubtitleTracks(new Metadata(), context);
            decoder = new Cea608Decoder(owner, "t-", null);
        }

        Feed control(int[] code) {
            return control(0, code, false);
        }

        Feed control(int field, int[] code, boolean channel2) {
            int b1 = channel2 ? code[0] | 0x08 : code[0];
            decoder.pair(t, field, parity(b1), parity(code[1]));
            decoder.pair(t, field, parity(b1), parity(code[1]));
            return this;
        }

        Feed text(String s) {
            return text(0, s);
        }

        Feed text(int field, String s) {
            for (int i = 0; i < s.length(); i += 2) {
                int b2 = i + 1 < s.length() ? s.charAt(i + 1) : 0;
                decoder.pair(t, field, parity(s.charAt(i)), parity(b2));
            }
            return this;
        }

        Feed at(long millis) {
            t = millis;
            return this;
        }

        String vtt(int track) throws Exception {
            return docs(0).get(track).vtt();
        }

        /** Finishes at {@code endMillis} (or the last pair's time) and emits. */
        List<Doc> docs(long endMillis) throws Exception {
            decoder.finish(endMillis);
            XHTMLContentHandler xhtml =
                    new XHTMLContentHandler(new DefaultHandler(), new Metadata(), context);
            xhtml.startDocument();
            owner.emit(xhtml);
            xhtml.endDocument();
            return capture.docs;
        }
    }

    static int parity(int c) {
        return Integer.bitCount(c) % 2 == 0 ? c | 0x80 : c;
    }

    @Test
    public void testPopOn() throws Exception {
        Feed f = new Feed().control(RCL).control(ENM).control(ROW15).text("HELLO WORLD")
                .at(1000).control(EOC).at(3000).control(EDM);
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nHELLO WORLD\n", f.vtt(0));
    }

    @Test
    public void testEnmErasesHiddenMemory() throws Exception {
        // without the erase, NEW would overwrite only the start of OLDER
        Feed f = new Feed().control(RCL).control(ROW15).text("OLDER").control(ENM).control(ROW15)
                .text("NEW").at(1000).control(EOC).at(3000).control(EDM);
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nNEW\n", f.vtt(0));
    }

    @Test
    public void testRollUpEndsAPopOnCaption() throws Exception {
        Feed f = new Feed().control(RCL).control(ROW15).text("POP").at(1000).control(EOC)
                .at(3000).control(RU2).control(ROW15).text("ROLL").at(5000).control(CR);
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nPOP\n"
                + "\n00:00:03.000 --> 00:00:05.000\nROLL\n", f.vtt(0));
    }

    @Test
    public void testRollUpLinesOnce() throws Exception {
        Feed f = new Feed().control(RU2).control(ROW15).text("LINE ONE").at(2000).control(CR)
                .at(2500).text("LINE TWO").at(4000).control(CR).at(5000).control(EDM);
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:02.000\nLINE ONE\n"
                + "\n00:00:02.500 --> 00:00:04.000\nLINE TWO\n", f.vtt(0));
    }

    @Test
    public void testPaintOn() throws Exception {
        Feed f = new Feed().control(RDC).control(ROW15).at(500).text("PAINTED").at(1500)
                .control(EDM);
        assertEquals("WEBVTT\n\n00:00:00.500 --> 00:00:01.500\nPAINTED\n", f.vtt(0));
    }

    @Test
    public void testSpecialAndExtendedCharacters() throws Exception {
        // ♪ is special 0x11 0x37; É replaces the fallback E sent before it (0x12 0x21)
        Feed f = new Feed().control(RCL).control(ROW15).control(new int[]{0x11, 0x37})
                .text("CAF").text("E").control(new int[]{0x12, 0x21}).at(1000).control(EOC)
                .at(2000).control(EDM);
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n♪CAFÉ\n", f.vtt(0));
    }

    @Test
    public void testRepeatedControlOnlyOnce() throws Exception {
        int[] bs = {0x14, 0x21};
        // a backspace sent twice, as broadcast, deletes one character
        Feed f = new Feed().control(RCL).control(ROW15).text("ABC").control(bs).at(1000)
                .control(EOC).at(2000).control(EDM);
        assertTrue(f.vtt(0).contains("\nAB\n"), f.vtt(0));
        // a third consecutive copy is a new command
        Feed g = new Feed();
        Cea608Decoder d = g.decoder;
        for (int[] p : new int[][]{RCL, ROW15}) {
            d.pair(0, 0, parity(p[0]), parity(p[1]));
        }
        d.pair(0, 0, parity('A'), parity('B'));
        d.pair(0, 0, parity('C'), parity(0));
        for (int i = 0; i < 3; i++) {
            d.pair(0, 0, parity(bs[0]), parity(bs[1]));
        }
        d.pair(1000, 0, parity(EOC[0]), parity(EOC[1]));
        d.pair(2000, 0, parity(EDM[0]), parity(EDM[1]));
        assertTrue(g.vtt(0).contains("\nA\n"), g.vtt(0));
    }

    @Test
    public void testChannelsAndXds() throws Exception {
        Feed f = new Feed();
        f.control(0, RCL, true).control(0, ROW15, true).text("SECOND").at(1000)
                .control(0, EOC, true).at(2000).control(0, EDM, true);
        // field 2: an XDS packet (start 0x01 0x03, data, end 0x0F) must not reach CC3
        f.decoder.pair(2000, 1, parity(0x01), parity(0x03));
        f.decoder.pair(2000, 1, parity('X'), parity('D'));
        f.decoder.pair(2000, 1, parity(0x0F), parity(0x1D));
        f.control(1, RCL, false).control(1, ROW15, false).text(1, "THIRD").at(3000)
                .control(1, EOC, false).at(4000).control(1, EDM, false);
        List<Doc> docs = f.docs(0);
        assertEquals(2, docs.size());
        assertTrue(docs.get(0).vtt().contains("SECOND"), docs.get(0).vtt());
        assertEquals("t-cc2.vtt", docs.get(0).name());
        assertEquals("WEBVTT\n\n00:00:03.000 --> 00:00:04.000\nTHIRD\n", docs.get(1).vtt());
        assertEquals("t-cc3.vtt", docs.get(1).name());
    }

    @Test
    public void testBadParityIsDropped() throws Exception {
        Feed f = new Feed().control(RCL).control(ROW15).text("OK");
        // 'X' with even parity
        f.decoder.pair(0, 0, 'X' | (Integer.bitCount('X') % 2 == 0 ? 0 : 0x80), parity(0));
        f.at(1000).control(EOC).at(2000).control(EDM);
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nOK\n", f.vtt(0));
    }

    @Test
    public void testRedisplayIsBoundedByTheBudget() throws Exception {
        // "A" shown, "B" loaded: each EOC (between padding) flips them, a cue each time, so
        // only the file's char budget stops it
        Feed f = new Feed();
        Cea608Decoder d = f.decoder;
        pair(d, 0, RCL);
        pair(d, 0, ROW15);
        d.pair(0, 0, parity('A'), parity(0));
        pair(d, 0, EOC);
        pair(d, 0, ROW15);
        d.pair(0, 0, parity('B'), parity(0));
        for (int i = 1; i < 1_000_000; i++) {
            d.pair(i * 40L, 0, 0x80, 0x80);
            pair(d, i * 40L, EOC);
        }
        long vtt = 0;
        for (Doc doc : f.docs(0)) {
            vtt += doc.vtt().length();
        }
        assertTrue(f.owner.isSpent());
        assertTrue(vtt <= SubtitleTracks.MAX_TOTAL_CHARS + 100, "vtt=" + vtt);
    }

    @Test
    public void testRedisplayedCaptionIsOneCue() throws Exception {
        // shown at 1 s, shown again at 2 s and 2.5 s, taken down at 3 s: one cue, 1-3 s
        Feed f = new Feed().control(RCL).control(ROW15).text("SAME").at(1000).control(EOC);
        for (long t : new long[]{2000, 2500}) {
            f.at(t).control(RCL).control(ROW15).text("SAME").control(EOC);
        }
        f.at(3000).control(EDM);
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nSAME\n", f.vtt(0));
    }

    @Test
    public void testRepeatedRollUpLinesStay() throws Exception {
        Feed f = new Feed().control(RU2).control(ROW15).text("NO.").at(1000).control(CR)
                .text("NO.").at(2000).control(CR);
        assertEquals(2, f.vtt(0).split("-->").length - 1, f.vtt(0));
    }

    @Test
    public void testRowsInOrder() throws Exception {
        // PAC row 14 (0x14 0x40) above row 15
        Feed f = new Feed().control(RCL).control(new int[]{0x14, 0x40}).text("TOP")
                .control(ROW15).text("BOTTOM").at(1000).control(EOC).at(2000).control(EDM);
        assertTrue(f.vtt(0).contains("\nTOP\nBOTTOM\n"), f.vtt(0));
    }

    @Test
    public void testTabMidRowAndDer() throws Exception {
        // tab offset 1, a mid-row code (shown as a space), then PAC indent 4 and DER
        Feed f = new Feed().control(RCL).control(ROW15).text("AB").control(new int[]{0x17, 0x21})
                .text("C").control(new int[]{0x11, 0x20}).text("DEFGH")
                .control(new int[]{0x14, 0x72}).control(new int[]{0x14, 0x24}).at(1000)
                .control(EOC).at(2000).control(EDM);
        assertTrue(f.vtt(0).contains("\nAB C\n"), f.vtt(0));
    }

    @Test
    public void testRollUpToPopOnFlushesTheLine() throws Exception {
        Feed f = new Feed().control(RU2).control(ROW15).text("ROLLING").at(1000).control(RCL)
                .control(ROW15).text("POPPED").at(2000).control(EOC).at(3000).control(EDM);
        String vtt = f.vtt(0);
        assertTrue(vtt.contains("00:00:00.000 --> 00:00:01.000\nROLLING"), vtt);
        assertTrue(vtt.contains("00:00:02.000 --> 00:00:03.000\nPOPPED"), vtt);
    }

    @Test
    public void testTextServiceLeavesCaptionsAlone() throws Exception {
        // TR hands the channel to T1; its text and backspaces must not touch the roll-up line
        Feed f = new Feed().control(RU2).control(ROW15).text("AB").control(new int[]{0x14, 0x2A})
                .text("XYZ").control(new int[]{0x14, 0x21}).control(RU2).text("CD").at(1000)
                .control(CR);
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nABCD\n", f.vtt(0));
    }

    @Test
    public void testDamagedControlIsDropped() throws Exception {
        Feed f = new Feed().control(RCL).control(ROW15).text("OK");
        // EOC with even parity on its first byte: dropped whole, not printed as '/'
        f.decoder.pair(500, 0, 0x14 | (Integer.bitCount(0x14) % 2 == 0 ? 0 : 0x80),
                parity(0x2F));
        f.at(1000).control(EOC).at(2000).control(EDM);
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nOK\n", f.vtt(0));
    }

    @Test
    public void testPaddingEndsARepeat() throws Exception {
        // BS, padding, BS: two commands, two characters gone
        Feed f = new Feed().control(RCL).control(ROW15).text("ABCD");
        pair(f.decoder, 0, new int[]{0x14, 0x21});
        f.decoder.pair(0, 0, 0x80, 0x80);
        pair(f.decoder, 0, new int[]{0x14, 0x21});
        f.at(1000).control(EOC).at(2000).control(EDM);
        assertTrue(f.vtt(0).contains("\nAB\n"), f.vtt(0));
    }

    @Test
    public void testSecondExtendedSet() throws Exception {
        Feed f = new Feed().control(RCL).control(ROW15).text("A").control(new int[]{0x13, 0x30})
                .at(1000).control(EOC).at(2000).control(EDM);
        assertTrue(f.vtt(0).contains("\nÄ\n"), f.vtt(0));
    }

    @Test
    public void testOpenCaptionEndsAtTheEnd() throws Exception {
        Feed f = new Feed().control(RCL).control(ROW15).text("LAST").at(1000).control(EOC);
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:05.000\nLAST\n",
                f.docs(5000).get(0).vtt());
    }

    @Test
    public void testJoinedMidStreamIsRollUp() throws Exception {
        // no mode command at all: characters still come out, as roll-up
        Feed f = new Feed().text("JOINED").at(1000).control(CR);
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nJOINED\n", f.vtt(0));
    }

    private static void pair(Cea608Decoder d, long t, int[] code) {
        d.pair(t, 0, parity(code[0]), parity(code[1]));
    }

    @Test
    public void testRandomSeiNeverThrows() throws Exception {
        long seed = new Random().nextLong();
        Random r = new Random(seed);
        try {
            for (int i = 0; i < 20_000; i++) {
                byte[] nal = new byte[r.nextInt(64)];
                r.nextBytes(nal);
                if (nal.length > 9 && r.nextBoolean()) {
                    // often enough a GA94 prefix to reach cc_data parsing
                    System.arraycopy(new byte[]{0x06, 4, (byte) r.nextInt(64), (byte) 0xB5, 0,
                            0x31, 'G', 'A', '9', '4'}, 0, nal, 0, 10);
                }
                CaptionData.fromSei(nal, 1, (field, b1, b2) -> { });
            }
        } catch (RuntimeException e) {
            throw new AssertionError("seed=" + seed, e);
        }
    }

    @Test
    public void testRandomBytesNeverThrow() throws Exception {
        long seed = new Random().nextLong();
        Random r = new Random(seed);
        Feed f = new Feed();
        try {
            for (int i = 0; i < 200_000; i++) {
                f.decoder.pair(i, r.nextInt(2), r.nextInt(256), r.nextInt(256));
            }
            f.docs(0);
        } catch (RuntimeException e) {
            throw new AssertionError("seed=" + seed, e);
        }
    }

    @Test
    public void testCcDataFromSei() throws Exception {
        // SEI: payloadType 4, T.35 B5 0031 "GA94" 03, cc_data with two pairs (one 708, skipped)
        ByteArrayOutputStream cc = new ByteArrayOutputStream();
        cc.write(0x40 | 2);
        cc.write(0xFF);
        cc.writeBytes(new byte[]{(byte) 0xFC, (byte) parity('H'), (byte) parity('I')});
        cc.writeBytes(new byte[]{(byte) 0xFE, 0x00, 0x00});
        ByteArrayOutputStream t35 = new ByteArrayOutputStream();
        t35.writeBytes(new byte[]{(byte) 0xB5, 0x00, 0x31, 'G', 'A', '9', '4', 0x03});
        t35.writeBytes(cc.toByteArray());
        byte[] payload = t35.toByteArray();
        ByteArrayOutputStream sei = new ByteArrayOutputStream();
        sei.write(0x06);
        sei.write(4);
        sei.write(payload.length);
        sei.writeBytes(payload);
        sei.write(0x80);
        byte[] nal = escape(sei.toByteArray());
        List<int[]> pairs = new ArrayList<>();
        int n = CaptionData.fromSei(nal, 1, (field, b1, b2) -> pairs.add(new int[]{field, b1, b2}));
        assertEquals(1, n);
        assertEquals(1, pairs.size());
        assertEquals(parity('H'), pairs.get(0)[1]);
        assertEquals(parity('I'), pairs.get(0)[2]);
    }

    @Test
    public void testUnescape() throws Exception {
        byte[] nal = {0x06, 0x00, 0x00, 0x03, 0x01, 0x00, 0x00, 0x03, 0x00, 0x03};
        assertEquals(List.of(0, 0, 1, 0, 0, 0, 3),
                toList(CaptionData.unescape(nal, 1)));
    }

    private static List<Integer> toList(byte[] b) {
        List<Integer> out = new ArrayList<>();
        for (byte x : b) {
            out.add(x & 0xFF);
        }
        return out;
    }

    // emulation prevention: 00 00 followed by 00-03 gets a 03 inserted
    private static byte[] escape(byte[] nal) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int zeros = 0;
        for (int i = 0; i < nal.length; i++) {
            int v = nal[i] & 0xFF;
            if (i > 0 && zeros >= 2 && v <= 3) {
                out.write(3);
                zeros = 0;
            }
            out.write(v);
            zeros = v == 0 ? zeros + 1 : 0;
        }
        return out.toByteArray();
    }

    @Test
    public void testBasicCharactersThatDifferFromAscii() throws Exception {
        Feed f = new Feed().control(RCL).control(ROW15).text("*\\^_`{|}~").at(1000).control(EOC)
                .at(2000).control(EDM);
        assertTrue(f.vtt(0).contains("\náéíóúç÷Ññ\n"), f.vtt(0));
    }

    @Test
    public void testPaintOnOverwriteIsItsOwnCue() throws Exception {
        // the first screen is not lost when the same cells are painted over
        Feed f = new Feed().control(RDC).control(ROW15).text("ABC").at(1000).control(ROW15)
                .text("XYZ").at(2000).control(EDM);
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nABC\n"
                + "\n00:00:01.000 --> 00:00:02.000\nXYZ\n", f.vtt(0));
    }

    @Test
    public void testPaintOnEraseEndsTheCue() throws Exception {
        int[] der = {0x14, 0x24};
        Feed f = new Feed().control(RDC).control(ROW15).text("HELLO").at(1000).control(ROW15)
                .control(der).at(1500).text("BYE").at(2500).control(EDM);
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nHELLO\n"
                + "\n00:00:01.500 --> 00:00:02.500\nBYE\n", f.vtt(0));
    }

    @Test
    public void testPaintOnDerOfNothingKeepsTheCue() throws Exception {
        int[] der = {0x14, 0x24};
        Feed f = new Feed().control(RDC).control(ROW15).text("PAINT").control(der).text("ED")
                .at(1000).control(EDM);
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nPAINTED\n", f.vtt(0));
    }

    @Test
    public void testExtendedCharacterInTheLastColumn() throws Exception {
        // the cursor stays on column 32: É replaces the E there, not the A before it
        String line = "A".repeat(31) + "E";
        Feed f = new Feed().control(RCL).control(ROW15).text(line)
                .control(new int[]{0x12, 0x21}).at(1000).control(EOC).at(2000).control(EDM);
        assertTrue(f.vtt(0).contains("\n" + "A".repeat(31) + "É\n"), f.vtt(0));
    }

    @Test
    public void testOnlyValid608PairsCount() throws Exception {
        // a 708 triplet and an invalid 608 one: nothing for the probe to find
        byte[] cc = {0x40 | 2, (byte) 0xFF, (byte) 0xFE, 0x41, 0x42, (byte) 0xF8, 0x14, 0x2C};
        List<int[]> pairs = new ArrayList<>();
        assertEquals(0, CaptionData.ccData(cc, 0, cc.length,
                (field, b1, b2) -> pairs.add(new int[]{field, b1, b2})));
        assertTrue(pairs.isEmpty());
    }

    @Test
    public void testHevcPayloadType128BeforeCaptions() throws Exception {
        // structure_of_pictures_info (type 128, one byte) is not the trailing bits
        byte[] t35 = cat(new byte[]{(byte) 0xB5, 0x00, 0x31, 'G', 'A', '9', '4', 0x03},
                new byte[]{0x40 | 1, (byte) 0xFF, (byte) 0xFC, (byte) parity('H'),
                        (byte) parity('I')});
        byte[] sei = cat(new byte[]{0x4E, 0x01, (byte) 0x80, 0x01, 0x00, 4, (byte) t35.length},
                t35, new byte[]{(byte) 0x80});
        assertEquals(1, CaptionData.fromSei(escape(sei), 2, (field, b1, b2) -> { }));
    }

    @Test
    public void testSeiWithEscapesAndSeveralMessages() throws Exception {
        // a 300-byte unregistered message (0xFF-extended size, 00 00 01 inside, so escaped)
        // before the captions, whose cc_data has an invalid and a 708 triplet among its pairs
        byte[] unregistered = new byte[300];
        unregistered[100] = 0;
        unregistered[101] = 0;
        unregistered[102] = 1;
        byte[] cc = {0x40 | 4, (byte) 0xFF, (byte) 0xFC, (byte) parity('H'), (byte) parity('I'),
                (byte) 0xF8, (byte) parity(0x14), (byte) parity(0x2C), (byte) 0xFE, 0x00, 0x00,
                (byte) 0xFD, (byte) parity('!'), (byte) 0x80};
        byte[] t35 = cat(new byte[]{(byte) 0xB5, 0x00, 0x31, 'G', 'A', '9', '4', 0x03}, cc);
        byte[] sei = cat(new byte[]{0x06, 5, (byte) 0xFF, 300 - 255}, unregistered,
                new byte[]{4, (byte) t35.length}, t35, new byte[]{(byte) 0x80});
        byte[] nal = escape(sei);
        assertTrue(nal.length > sei.length, "the fixture must carry an escape");
        List<int[]> pairs = new ArrayList<>();
        assertEquals(2, CaptionData.fromSei(nal, 1,
                (field, b1, b2) -> pairs.add(new int[]{field, b1, b2})));
        assertEquals(0, pairs.get(0)[0]);
        assertEquals(parity('H'), pairs.get(0)[1]);
        assertEquals(1, pairs.get(1)[0]);
        assertEquals(parity('!'), pairs.get(1)[1]);
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }
}
