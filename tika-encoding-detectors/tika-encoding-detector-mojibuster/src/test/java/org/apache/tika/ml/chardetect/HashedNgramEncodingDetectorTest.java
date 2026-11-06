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
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.detect.EncodingResult;

/** The binary veto: text-like UTF-8 never gets the {@code binary} class. */
public class HashedNgramEncodingDetectorTest {

    private static HashedNgramModel model;
    private static HashedNgramEncodingDetector detector;

    @BeforeAll
    static void load() throws Exception {
        model = HashedNgramEncodingDetector.loadDefaultModel();
        detector = new HashedNgramEncodingDetector(model);
    }

    @Test
    public void greekCropCutMidCharacterIsUtf8() {
        assertUtf8NotBinary(crop("Η Ελλάδα είναι χώρα της νοτιοανατολικής Ευρώπης.", 32));
    }

    @Test
    public void japaneseCropCutMidCharacterIsUtf8() {
        assertUtf8NotBinary(crop("日本は東アジアに位置する島国であり、首都は東京である。", 16));
    }

    @Test
    public void binaryWithNulsStaysBinary() {
        Random r = new Random(42);
        byte[] probe = new byte[64];
        r.nextBytes(probe);
        for (int i = 0; i < probe.length; i += 4) {
            probe[i] = 0;
        }
        assertEquals("binary", rawTop(probe));
        assertTrue(detector.detect(probe).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0x01, 0x0B, 0x1F})
    public void utf8WithDisallowedControlMayBeBinary(int control) {
        byte[] probe = crop("Η Ελλάδα είναι χώρα της νοτιοανατολικής Ευρώπης.", 32);
        probe[probe.length - 1] = (byte) control;
        assertEquals("binary", rawTop(probe));
        assertTrue(detector.detect(probe).isEmpty());
    }

    @Test
    public void textLikeRule() {
        assertTrue(textLike(new byte[0]));
        assertTrue(textLike("a\t\n\f\r\u001b\u007f b".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(textLike(new byte[] {'a', (byte) 0xE4, (byte) 0xB8, (byte) 0xAD}));
        // valid prefixes cut at the end
        assertTrue(textLike(new byte[] {'a', (byte) 0xE4}));
        assertTrue(textLike(new byte[] {'a', (byte) 0xE4, (byte) 0xB8}));
        assertTrue(textLike(new byte[] {'a', (byte) 0xF0, (byte) 0x9F, (byte) 0x98}));
        assertFalse(textLike(new byte[] {'a', 0x00, 'b'}));
        assertFalse(textLike(new byte[] {'a', 0x0B, 'b'}));
        // a probe starting mid-character is not text-like: only the tail may be cut
        assertFalse(textLike(new byte[] {(byte) 0xB8, (byte) 0xAD, 'a'}));
        // truncated tails whose bytes so far are already invalid
        assertFalse(textLike(new byte[] {'a', (byte) 0xE0, (byte) 0x80}));
        assertFalse(textLike(new byte[] {'a', (byte) 0xED, (byte) 0xA0}));
        assertFalse(textLike(new byte[] {'a', (byte) 0xF4, (byte) 0x90}));
        // overlong, invalid lead, cut sequence followed by more bytes
        assertFalse(textLike(new byte[] {(byte) 0xC0, (byte) 0xAF}));
        assertFalse(textLike(new byte[] {(byte) 0xF5, (byte) 0x80}));
        assertFalse(textLike(new byte[] {(byte) 0xE4, 'a', (byte) 0xE4, (byte) 0xB8}));
    }

    private static void assertUtf8NotBinary(byte[] probe) {
        assertEquals("binary", rawTop(probe), "probe no longer exercises the veto");
        List<EncodingResult> results = detector.detect(probe);
        assertFalse(results.isEmpty());
        assertEquals(StandardCharsets.UTF_8, results.get(0).getCharset(), results.toString());
        assertEquals(1f, results.get(0).getConfidence());
    }

    /** First {@code len} bytes of {@code text}, required to end inside a multi-byte character. */
    private static byte[] crop(String text, int len) {
        byte[] full = text.getBytes(StandardCharsets.UTF_8);
        byte[] probe = Arrays.copyOf(full, len);
        assertTrue((full[len] & 0xC0) == 0x80, "crop must cut a character");
        return probe;
    }

    private static String rawTop(byte[] probe) {
        float[] logits = new float[model.getNumClasses()];
        assertTrue(model.logits(probe, 0, probe.length, logits));
        int top = 0;
        for (int c = 1; c < logits.length; c++) {
            if (logits[c] > logits[top]) {
                top = c;
            }
        }
        return model.getLabels()[top];
    }

    private static boolean textLike(byte[] b) {
        return HashedNgramEncodingDetector.isTextLikeUtf8(b);
    }
}
