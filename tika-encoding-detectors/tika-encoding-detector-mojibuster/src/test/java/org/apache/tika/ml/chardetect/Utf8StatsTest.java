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
package org.apache.tika.ml.chardetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.ml.chardetect.StructuralEncodingRules.Utf8Result;
import org.apache.tika.ml.chardetect.StructuralEncodingRules.Utf8Stats;

/**
 * Pins the single-pass {@link StructuralEncodingRules#utf8Stats} contract —
 * including the edges where the pre-consolidation {@code checkUtf8} and
 * {@code countUtf8Errors} deliberately differed (a provably-bad truncated
 * tail is NOT_UTF8 but not an error <em>event</em>).
 */
public class Utf8StatsTest {

    private static Utf8Stats stats(int... unsignedBytes) {
        byte[] b = new byte[unsignedBytes.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) unsignedBytes[i];
        }
        return StructuralEncodingRules.utf8Stats(b);
    }

    @Test
    public void pureAsciiIsAmbiguousWithNoCounts() {
        Utf8Stats s = StructuralEncodingRules.utf8Stats(
                "plain ascii only".getBytes(StandardCharsets.US_ASCII));
        assertEquals(0, s.errors());
        assertEquals(0, s.sequences());
        assertEquals(Utf8Result.AMBIGUOUS, s.toResult());
    }

    @Test
    public void completeSequencesAreLikelyAndCounted() {
        Utf8Stats s = StructuralEncodingRules.utf8Stats(
                "héllo wörld 中文 😀".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, s.errors());
        assertEquals(5, s.sequences()); // é ö 中 文 😀
        assertEquals(Utf8Result.LIKELY_UTF8, s.toResult());
    }

    @Test
    public void truncatedCleanLeadAtEndIsAmbiguousNotError() {
        // lone C3 at probe-end: valid-so-far, no structural evidence
        Utf8Stats s = stats('a', 'b', 0xC3);
        assertEquals(0, s.errors());
        assertEquals(0, s.sequences());
        assertFalse(s.truncatedTailInvalid());
        assertEquals(Utf8Result.AMBIGUOUS, s.toResult());
    }

    @Test
    public void provablyBadTruncatedTailIsNotUtf8ButNotAnErrorEvent() {
        // E0 41 at probe-end: 3-byte lead + non-continuation — cannot be UTF-8,
        // but per U+FFFD-event semantics it is not counted as an error.
        Utf8Stats s = stats('a', 0xE0, 0x41);
        assertEquals(0, s.errors());
        assertTrue(s.truncatedTailInvalid());
        assertEquals(Utf8Result.NOT_UTF8, s.toResult());
    }

    @Test
    public void classicErrorEventsEachCountOnce() {
        // F8 lead + orphan continuation + overlong C0 + bad-continuation seq
        Utf8Stats s = stats(0xF8, 'a', 0x80, 'b', 0xC0, 'c', 0xE0, 0x41, 0x41, 'd');
        assertEquals(4, s.errors());
        assertEquals(0, s.sequences());
        assertEquals(Utf8Result.NOT_UTF8, s.toResult());
    }

    @Test
    public void surrogateAndOverlongThreeByteAreErrors() {
        // ED A0 80 = U+D800 surrogate; E0 80 80 = overlong (cp < 0x0800)
        Utf8Stats s = stats(0xED, 0xA0, 0x80, 0xE0, 0x80, 0x80);
        assertEquals(2, s.errors());
        assertEquals(0, s.sequences());
    }

    @Test
    public void mixedErrorsAndSequencesTallyIndependently() {
        // one stray legacy byte before genuine multi-byte content (TIKA-4810 shape)
        byte[] bengali = "সেমি".getBytes(StandardCharsets.UTF_8);
        byte[] probe = new byte[1 + bengali.length];
        probe[0] = (byte) 0xA9;
        System.arraycopy(bengali, 0, probe, 1, bengali.length);
        Utf8Stats s = StructuralEncodingRules.utf8Stats(probe);
        assertEquals(1, s.errors());
        assertEquals(4, s.sequences());
        assertEquals(Utf8Result.NOT_UTF8, s.toResult());
    }

    @Test
    public void sequenceLengthIsGrammarOnly() {
        // grammar-only by design: overlong E0 80 80 still reports length 3
        byte[] overlong = {(byte) 0xE0, (byte) 0x80, (byte) 0x80};
        assertEquals(3, StructuralEncodingRules.utf8SequenceLength(overlong, 0));
        byte[] twoByte = "é".getBytes(StandardCharsets.UTF_8);
        assertEquals(2, StructuralEncodingRules.utf8SequenceLength(twoByte, 0));
        byte[] c0Lead = {(byte) 0xC0, (byte) 0x80};
        assertEquals(0, StructuralEncodingRules.utf8SequenceLength(c0Lead, 0));
        byte[] truncated = {(byte) 0xE0, (byte) 0xA6};
        assertEquals(0, StructuralEncodingRules.utf8SequenceLength(truncated, 0));
    }
}
