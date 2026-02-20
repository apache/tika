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
package org.apache.tika.detect.encoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class ByteNgramFeatureExtractorTest {

    private static final int NUM_BUCKETS = 1024;

    @Test
    public void testNullInput() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract((byte[]) null);
        assertEquals(NUM_BUCKETS, counts.length);
        for (int c : counts) {
            assertEquals(0, c);
        }
    }

    @Test
    public void testEmptyInput() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract(new byte[0]);
        assertEquals(NUM_BUCKETS, counts.length);
        for (int c : counts) {
            assertEquals(0, c);
        }
    }

    @Test
    public void testAsciiOnlyProducesNoFeatures() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        // All bytes < 0x80 are skipped — HTML tags, ASCII text, etc. produce nothing
        byte[] ascii = "hello world".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(0, sum(ext.extract(ascii)));
    }

    @Test
    public void testSingleHighByteProducesOneUnigram() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        // One high byte → one unigram, no bigram (no following byte)
        int[] counts = ext.extract(new byte[]{(byte) 0xE0});
        assertEquals(1, sum(counts));
    }

    @Test
    public void testTwoHighBytesProduceUnigramAndBigram() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        // 0xE0 → unigram; (0xE0, 0xE1) → bigram; 0xE1 → unigram  = 3 features
        int[] counts = ext.extract(new byte[]{(byte) 0xE0, (byte) 0xE1});
        assertEquals(3, sum(counts));
    }

    @Test
    public void testHighByteFollowedByAsciiProducesUnigramAndBigram() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        // 0xE0 → unigram; (0xE0, 0x41) → bigram; 0x41 is ASCII so no further features = 2
        int[] counts = ext.extract(new byte[]{(byte) 0xE0, 0x41});
        assertEquals(2, sum(counts));
    }

    @Test
    public void testAsciiFollowedByHighByteProducesUnigramAndBigram() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        // 0x41 skipped; 0xE0 → unigram; no following byte → 1 feature
        int[] counts = ext.extract(new byte[]{0x41, (byte) 0xE0});
        assertEquals(1, sum(counts));
    }

    @Test
    public void testDifferentHighBytesProduceDifferentFeatures() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        byte[] latin = new byte[]{(byte) 0xE0, (byte) 0xE1, (byte) 0xE2};
        byte[] cyrillic = new byte[]{(byte) 0xD0, (byte) 0xD1, (byte) 0xD2};
        int[] f1 = ext.extract(latin);
        int[] f2 = ext.extract(cyrillic);
        boolean different = false;
        for (int i = 0; i < NUM_BUCKETS; i++) {
            if (f1[i] != f2[i]) {
                different = true;
                break;
            }
        }
        assertNotEquals(false, different, "Different high-byte sequences should produce different features");
    }

    @Test
    public void testNumBuckets() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(512);
        assertEquals(512, ext.getNumBuckets());
        int[] counts = ext.extract(new byte[]{1, 2, 3, 4});
        assertEquals(512, counts.length);
    }

    private static int sum(int[] arr) {
        int s = 0;
        for (int v : arr) {
            s += v;
        }
        return s;
    }
}
