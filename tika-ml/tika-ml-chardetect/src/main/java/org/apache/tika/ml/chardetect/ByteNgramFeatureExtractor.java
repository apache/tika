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

import org.apache.tika.ml.FeatureExtractor;

/**
 * Feature extractor for raw bytes that hashes overlapping byte bigrams and
 * trigrams into a fixed-width integer bucket array using FNV-1a.
 * <p>
 * This extractor is the byte-domain counterpart of
 * {@code ScriptAwareFeatureExtractor}: it operates on {@code byte[]} rather
 * than {@code String} and is designed for use with a {@link org.apache.tika.ml.LinearModel}
 * trained to predict the character encoding of a byte sequence.
 * <p>
 * Feature hashing:
 * <ul>
 *   <li>Byte bigrams  (pairs of consecutive bytes)
 *   <li>Byte trigrams (triples of consecutive bytes), enabled when
 *       {@code useTrigrams == true}
 * </ul>
 * Both bigram and trigram hashes are folded into {@code numBuckets} using a
 * non-negative modulus so that every bucket index is in {@code [0, numBuckets)}.
 */
public class ByteNgramFeatureExtractor implements FeatureExtractor<byte[]> {

    private static final int FNV_PRIME = 0x01000193;
    private static final int FNV_OFFSET = 0x811c9dc5;

    private final int numBuckets;
    private final boolean useTrigrams;

    /**
     * @param numBuckets  number of hash buckets (feature-vector dimension)
     * @param useTrigrams if {@code true}, byte trigrams are added to bigrams
     */
    public ByteNgramFeatureExtractor(int numBuckets, boolean useTrigrams) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("numBuckets must be positive: " + numBuckets);
        }
        this.numBuckets = numBuckets;
        this.useTrigrams = useTrigrams;
    }

    /**
     * Convenience constructor with bigrams only.
     */
    public ByteNgramFeatureExtractor(int numBuckets) {
        this(numBuckets, false);
    }

    @Override
    public int[] extract(byte[] input) {
        int[] counts = new int[numBuckets];
        if (input == null || input.length == 0) {
            return counts;
        }
        extractInto(input, 0, input.length, counts);
        return counts;
    }

    /**
     * Extract from a sub-range of a byte array.
     */
    public int[] extract(byte[] input, int offset, int length) {
        int[] counts = new int[numBuckets];
        if (input == null || length == 0) {
            return counts;
        }
        extractInto(input, offset, offset + length, counts);
        return counts;
    }

    private void extractInto(byte[] b, int from, int to, int[] counts) {
        int end = to - 1;
        for (int i = from; i < end; i++) {
            int bi = b[i] & 0xFF;
            int bi1 = b[i + 1] & 0xFF;

            // Byte bigram hash
            int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
            h = (h ^ bi1) * FNV_PRIME;
            counts[bucket(h)]++;

            if (useTrigrams && i + 2 < to) {
                int bi2 = b[i + 2] & 0xFF;
                h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                h = (h ^ bi1) * FNV_PRIME;
                h = (h ^ bi2) * FNV_PRIME;
                counts[bucket(h)]++;
            }
        }
    }

    private int bucket(int hash) {
        return (hash & 0x7fffffff) % numBuckets;
    }

    @Override
    public int getNumBuckets() {
        return numBuckets;
    }

    public boolean isUseTrigrams() {
        return useTrigrams;
    }
}
