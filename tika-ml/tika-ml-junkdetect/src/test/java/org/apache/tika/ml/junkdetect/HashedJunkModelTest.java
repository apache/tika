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
package org.apache.tika.ml.junkdetect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class HashedJunkModelTest {

    private static final int LOG2 = 16;
    private static final float PAIR_SCALE = 0.01f;
    private static final float DEFER = 1.0f;
    private static final float POINT_SCALE = 0.02f;
    private static final float BIAS = -0.5f;

    /** Deterministic test model: java.util.Random is specified, so the weights are stable. */
    static HashedJunkModel testModel() {
        Random r = new Random(5027);
        byte[] pair = new byte[1 << LOG2];
        byte[] point = new byte[1 << LOG2];
        r.nextBytes(pair);
        r.nextBytes(point);
        return HashedJunkModel.create(LOG2, 3, PAIR_SCALE, pair, DEFER, POINT_SCALE, BIAS, point,
                List.of(Character.UnicodeScript.LATIN, Character.UnicodeScript.GREEK));
    }

    // ---------------------------------------------------------------- golden vectors

    @Test
    public void bucketsArePinned() {
        // any change to normalization, folding or hashing changes these
        assertArrayEquals(new int[]{7137, 9493, 14860, 22940, 26304, 30247, 30619, 35412, 38620, 40147,
            52516, 60873}, testModel().buckets("Ab 1"));
    }

    @Test
    public void scoresArePinned() {
        HashedJunkModel m = testModel();
        assertEquals(GOLDEN_COMPARE, m.compare("Άρθρα για την Ελλάδα", "¶ρθρα για την Ελλάδα"), 1e-9);
        assertEquals(GOLDEN_PAIR_LOGIT,
                m.pairwiseLogit("Άρθρα για την Ελλάδα", "¶ρθρα για την Ελλάδα"), 1e-9);
        assertEquals(GOLDEN_POINT, m.score("Le ministère a été créé à Paris en 1998.").getLogit(), 1e-9);
    }

    @Test
    public void formatBytesArePinned() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        testModel().write(bos);
        byte[] payload;
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            payload = in.readAllBytes();
        }
        assertEquals(GOLDEN_PAYLOAD_SHA256,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)));
    }

    // ---------------------------------------------------------------- normalization

    @Test
    public void normalizeFoldsWidthFormsAndSoftHyphens() {
        assertEquals("ABC!~", HashedJunkModel.normalize("ＡＢＣ！～"));
        assertEquals("ガ", HashedJunkModel.normalize("ｶﾞ"));
        assertEquals("einzigartig", HashedJunkModel.normalize("ein­zig­ar­tig"));
        assertEquals("￥100", HashedJunkModel.normalize("￥100"));
        assertEquals("½ Ⅱ", HashedJunkModel.normalize("½ Ⅱ"));
        assertEquals("é", HashedJunkModel.normalize("e­́"));
    }

    @Test
    public void normalizeIsIdempotent() {
        Random r = new Random(11);
        int[] pool = {'a', 'e', ' ', '́', '­', 'Ａ', 'ｶ', 'ﾞ', 'ﾡ',
                '￥', 'ᄀ', 'ᅡ', 'カ', '゙', 0x1F600, 0xD800};
        for (int t = 0; t < 2000; t++) {
            StringBuilder sb = new StringBuilder();
            for (int i = r.nextInt(12); i > 0; i--) {
                sb.appendCodePoint(pool[r.nextInt(pool.length)]);
            }
            String once = HashedJunkModel.normalize(sb.toString());
            assertEquals(once, HashedJunkModel.normalize(once), "seed 11, case " + t);
        }
    }

    // ---------------------------------------------------------------- contract

    @Test
    public void staticFeaturesAreTheModelsFeatures() {
        HashedJunkModel m = testModel();
        Random r = new Random(11);
        for (int i = 0; i < 200; i++) {
            String t = randomText(r);
            assertArrayEquals(m.buckets(t), HashedJunkModel.featureBuckets(t, LOG2, 3), t);
        }
        assertThrows(IllegalArgumentException.class, () -> HashedJunkModel.featureBuckets("a", 15, 3));
        assertThrows(IllegalArgumentException.class, () -> HashedJunkModel.featureBuckets("a", LOG2, 5));
    }

    @Test
    public void compareIsTheDifferenceOfPairValues() {
        HashedJunkModel m = testModel();
        Random r = new Random(12);
        for (int i = 0; i < 200; i++) {
            String a = randomText(r);
            String b = randomText(r);
            assertEquals(m.compare(a, b), m.pairValue(a) - m.pairValue(b), 1e-9);
            // the logit is the comparison averaged over the n-grams the two texts don't share
            long diff = m.pairwiseDifference(a, b);
            assertEquals(m.compare(a, b), m.pairwiseLogit(a, b) * diff, 1e-6);
            assertEquals(diff, m.pairwiseDifference(b, a));
        }
        assertEquals(0, m.pairwiseDifference("same text", "same text"));
        // one changed character: 1-3-grams through it on each side
        assertEquals(12, m.pairwiseDifference("Preis 5 \u20ac pro", "Preis 5 \u00a4 pro"));
    }

    @Test
    public void compareIsAntisymmetricAndZeroOnIdentity() {
        HashedJunkModel m = testModel();
        Random r = new Random(7);
        for (int t = 0; t < 500; t++) {
            String a = randomText(r);
            String b = randomText(r);
            assertEquals(m.compare(a, b), -m.compare(b, a), 0.0, "seed 7, case " + t);
            assertEquals(m.pairwiseLogit(a, b), -m.pairwiseLogit(b, a), 1e-12, "seed 7, case " + t);
            assertEquals(0.0, m.compare(a, a), 0.0);
            assertEquals(0.0, m.pairwiseLogit(a, a), 0.0);
        }
    }

    @Test
    public void coverageIsZeroForUnmodeledScriptAndNaNWithoutLetters() {
        HashedJunkModel m = testModel();
        HashedJunkModel.Score cyr = m.score("Привет, как дела у вас сегодня");
        assertEquals(0.0, cyr.getCoverage(), 0.0);
        assertEquals(Character.UnicodeScript.CYRILLIC, cyr.getDominantScript());
        HashedJunkModel.Score none = m.score("12 34 !! ?? 56");
        assertTrue(Double.isNaN(none.getCoverage()));
        assertNull(none.getDominantScript());
        assertEquals(1.0, m.score("Ελλάδα and Greece").getCoverage(), 0.0);
    }

    @Test
    public void windowStatsAreNaNWithoutAQualifyingWindow() {
        HashedJunkModel.Score s = testModel().score("short text");
        assertTrue(Double.isNaN(s.getWindowMin()));
        assertTrue(Double.isNaN(s.getWindowFracBelow0()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\uD800", "\uDC00x\uD800", "󰀀",
            "\u0000\u0001\u0085�", "﻿"})
    public void scoresAreFiniteForOddInput(String text) {
        HashedJunkModel m = testModel();
        assertTrue(Double.isFinite(m.score(text).getLogit()));
        assertTrue(Double.isFinite(m.compare(text, "x")));
        assertTrue(Double.isFinite(m.pairwiseLogit(text, "x")));
    }

    @Test
    public void scoresAreFiniteForLargeInput() {
        Random r = new Random(3);
        StringBuilder sb = new StringBuilder(1 << 20);
        while (sb.length() < 1 << 20) {
            sb.appendCodePoint(r.nextInt(0x30000));
        }
        HashedJunkModel.Score s = testModel().score(sb.toString());
        assertTrue(Double.isFinite(s.getLogit()));
    }

    // ---------------------------------------------------------------- loader

    @Test
    public void roundTrip() throws Exception {
        HashedJunkModel m = testModel();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        m.write(bos);
        HashedJunkModel back = HashedJunkModel.load(new ByteArrayInputStream(bos.toByteArray()));
        String a = "Ünïcödé text, 123";
        String b = "Ãœnã¯cã¶dã© text, 123";
        assertEquals(m.compare(a, b), back.compare(a, b), 0.0);
        assertEquals(m.score(a).getLogit(), back.score(a).getLogit(), 0.0);
        assertEquals(m.getDeferThreshold(), back.getDeferThreshold(), 0.0);
        assertEquals(m.getTrainedScripts(), back.getTrainedScripts());
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        back.write(again);
        assertArrayEquals(gunzip(bos.toByteArray()), gunzip(again.toByteArray()));
    }

    @Test
    public void loaderAcceptsTheRawWriter() throws Exception {
        HashedJunkModel.load(new ByteArrayInputStream(new Raw().bytes()));
    }

    @Test
    public void loaderRejectsBadFields() {
        assertRejected(r -> r.magic = "JUNKHNG0");
        assertRejected(r -> r.version = 2);
        assertRejected(r -> r.log2 = 15);
        assertRejected(r -> r.log2 = 25);
        assertRejected(r -> r.maxN = 0);
        assertRejected(r -> r.maxN = 5);
        assertRejected(r -> r.pairScale = 0f);
        assertRejected(r -> r.pairScale = Float.NaN);
        assertRejected(r -> r.pointScale = Float.POSITIVE_INFINITY);
        assertRejected(r -> r.defer = -0.1f);
        assertRejected(r -> r.defer = Float.NaN);
        assertRejected(r -> r.bias = Float.NaN);
        assertRejected(r -> r.scriptCount = -1);
        assertRejected(r -> r.scriptCount = 100_000);
        assertRejected(r -> r.script = "NOT_A_SCRIPT");
        assertRejected(r -> r.trailing = true);
        assertRejected(r -> r.truncate = 1);
        assertRejected(r -> r.truncate = 5000);
    }

    @Test
    public void createRejectsWrongArrayLength() {
        assertThrows(IllegalArgumentException.class, () -> HashedJunkModel.create(LOG2, 3, 1f,
                new byte[10], 0f, 1f, 0f, new byte[1 << LOG2], List.of()));
        assertThrows(IllegalArgumentException.class, () -> HashedJunkModel.create(LOG2, 9, 1f,
                new byte[1 << LOG2], 0f, 1f, 0f, new byte[1 << LOG2], List.of()));
    }

    private static void assertRejected(Consumer<Raw> corruption) {
        Raw raw = new Raw();
        corruption.accept(raw);
        assertThrows(IOException.class,
                () -> HashedJunkModel.load(new ByteArrayInputStream(raw.bytes())));
    }

    /** Writes the format field by field so each can be corrupted independently. */
    private static final class Raw {
        String magic = "JUNKHNG1";
        int version = 1;
        int log2 = LOG2;
        int maxN = 3;
        float pairScale = 1f;
        float defer = 0.5f;
        float pointScale = 1f;
        float bias = 0f;
        int scriptCount = 1;
        String script = "LATIN";
        boolean trailing;
        int truncate;

        byte[] bytes() throws IOException {
            ByteArrayOutputStream plain = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(plain);
            out.write(magic.getBytes(StandardCharsets.US_ASCII));
            out.writeByte(version);
            out.writeByte(log2);
            out.writeByte(maxN);
            int size = log2 >= 0 && log2 <= 24 ? 1 << log2 : 0;
            out.writeFloat(pairScale);
            out.write(new byte[size]);
            out.writeFloat(defer);
            out.writeFloat(pointScale);
            out.writeFloat(bias);
            out.write(new byte[size]);
            out.writeInt(scriptCount);
            if (scriptCount == 1) {
                out.writeUTF(script);
            }
            if (trailing) {
                out.writeByte(0);
            }
            byte[] p = plain.toByteArray();
            p = Arrays.copyOf(p, p.length - truncate);
            ByteArrayOutputStream gz = new ByteArrayOutputStream();
            try (GZIPOutputStream g = new GZIPOutputStream(gz)) {
                g.write(p);
            }
            return gz.toByteArray();
        }
    }

    private static byte[] gunzip(byte[] b) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(b))) {
            return in.readAllBytes();
        }
    }

    private static String randomText(Random r) {
        int[] pool = {'a', 'b', 'é', 'ß', ' ', '1', 'Ж', 'λ', '中', '�', '­', 'Ａ'};
        StringBuilder sb = new StringBuilder();
        for (int i = r.nextInt(40); i > 0; i--) {
            sb.appendCodePoint(pool[r.nextInt(pool.length)]);
        }
        return sb.toString();
    }

    // computed once; the scorer matched the research prototype to 3e-8 on the same texts
    private static final double GOLDEN_COMPARE = 2.1699999514967203;
    private static final double GOLDEN_PAIR_LOGIT = 0.21699999514967203;
    private static final double GOLDEN_POINT = -0.6989380486507332;
    private static final String GOLDEN_PAYLOAD_SHA256 =
            "a32efad795536285571d02fa3a4550665bb52c4c9fa34a8f83f2754e0b47f97f";
}
