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
    public void testSingleByte() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        // A single byte produces no bigrams
        int[] counts = ext.extract(new byte[]{0x41});
        int total = 0;
        for (int c : counts) {
            total += c;
        }
        assertEquals(0, total);
    }

    @Test
    public void testTwoBytesProduceOneBigram() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract(new byte[]{0x41, 0x42});
        int total = 0;
        for (int c : counts) {
            total += c;
        }
        assertEquals(1, total);
    }

    @Test
    public void testDifferentInputsProduceDifferentFeatures() {
        ByteNgramFeatureExtractor ext = new ByteNgramFeatureExtractor(NUM_BUCKETS);
        // UTF-8 "hello" vs ISO-8859-1 high bytes — should produce different feature vectors
        byte[] ascii = "hello world".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] highBytes = new byte[]{(byte) 0xE0, (byte) 0xE1, (byte) 0xE2,
                (byte) 0xE3, (byte) 0xE4, (byte) 0xE5};
        int[] f1 = ext.extract(ascii);
        int[] f2 = ext.extract(highBytes);
        boolean different = false;
        for (int i = 0; i < NUM_BUCKETS; i++) {
            if (f1[i] != f2[i]) {
                different = true;
                break;
            }
        }
        assertNotEquals(false, different, "Different byte sequences should produce different features");
    }

    @Test
    public void testTrigramExtractor() {
        ByteNgramFeatureExtractor biOnly = new ByteNgramFeatureExtractor(NUM_BUCKETS, false);
        ByteNgramFeatureExtractor biTri = new ByteNgramFeatureExtractor(NUM_BUCKETS, true);
        byte[] input = "hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int biTotal = sum(biOnly.extract(input));
        int biTriTotal = sum(biTri.extract(input));
        // bigram-only: n-1 = 4 bigrams
        // bigram+trigram: 4 bigrams + 3 trigrams = 7
        assertEquals(4, biTotal);
        assertEquals(7, biTriTotal);
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
