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


/**
 * Feature extractor for raw bytes for charset detection, using FNV-1a hashing
 * into a fixed-width bucket array.
 *
 * <h3>Features emitted</h3>
 * <ul>
 *   <li><strong>Unigrams</strong>: every byte {@code b} where
 *       {@code (b & 0xFF) >= 0x80}. These directly encode the high-byte
 *       frequency distribution that distinguishes single-byte encodings
 *       (KOI8-R vs Windows-1251 vs ISO-8859-2, etc.).</li>
 *   <li><strong>Bigrams</strong>: consecutive pairs {@code (b[i], b[i+1])}
 *       where {@code (b[i] & 0xFF) >= 0x80}. Anchoring on a high first byte
 *       captures multi-byte character structure (Big5, Shift-JIS, GBK,
 *       EUC-* lead/trail pairs) while automatically excluding ASCII-ASCII
 *       pairs produced by HTML tag markup — those bytes are all below 0x80
 *       and carry no charset signal.</li>
 * </ul>
 *
 * <h3>Why the high-byte filter matters</h3>
 * <p>Training data is clean text (no HTML tags). Inference data is often raw
 * HTML (many ASCII tag bytes). Without the filter, the model would see a
 * different byte distribution at inference time than at training time. By
 * ignoring bytes below 0x80 entirely, HTML tags are invisible to both the
 * training and inference feature computation — no stripping needed.</p>
 *
 * <h3>No salting needed</h3>
 * <p>Unigrams hash values {@code 0x0080–0x00FF}; bigrams anchored on a high
 * first byte produce values {@code 0x8000–0xFFFF}. These ranges do not
 * overlap, so unigrams and bigrams naturally occupy different regions of the
 * hash space without an explicit salt.</p>
 */
public class ByteNgramFeatureExtractor implements FeatureExtractor<byte[]> {

    private static final int FNV_PRIME  = 0x01000193;
    private static final int FNV_OFFSET = 0x811c9dc5;

    private final int numBuckets;

    /**
     * @param numBuckets number of hash buckets (feature-vector dimension).
     *                   2048 is a good default: large enough to limit collisions
     *                   across the tens of thousands of active multi-byte bigrams,
     *                   small enough that the model stays compact.
     */
    public ByteNgramFeatureExtractor(int numBuckets) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("numBuckets must be positive: " + numBuckets);
        }
        this.numBuckets = numBuckets;
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
        for (int i = from; i < to; i++) {
            int bi = b[i] & 0xFF;
            if (bi < 0x80) {
                continue; // ASCII — no charset signal, skip
            }

            // Unigram: hash the single high byte
            int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
            counts[bucket(h)]++;

            // Bigram: anchor on this high byte, pair with whatever follows
            if (i + 1 < to) {
                int bi1 = b[i + 1] & 0xFF;
                h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                h = (h ^ bi1) * FNV_PRIME;
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

    /**
     * Returns the fraction of bytes in {@code input} that are below 0x80 and
     * therefore contribute <em>no features</em> to this extractor.
     *
     * <p>This is the byte-level analogue of the word-level OOV ("out of
     * vocabulary") rate used in language-detection evaluation: a high ratio
     * means the sample is essentially pure ASCII and the model has nothing to
     * distinguish it from any other encoding.</p>
     *
     * <p>Thresholds used by the training-data pipeline to filter out low-signal
     * chunks ({@code build_charset_training.py}):
     * <ul>
     *   <li>CJK multibyte encodings: OOV &gt; 0.80 (i.e. high-byte ratio &lt; 0.20)</li>
     *   <li>SBCS / other legacy encodings: OOV &gt; 0.98 (high-byte ratio &lt; 0.02)</li>
     *   <li>ASCII / ISO-2022 / UTF-16 / UTF-32: exempt (by design)</li>
     * </ul>
     *
     * @return value in [0.0, 1.0]; 1.0 means all bytes are ASCII (fully OOV),
     *         0.0 means all bytes are high bytes.
     */
    public static double oovRate(byte[] input) {
        if (input == null || input.length == 0) {
            return 1.0;
        }
        int ascii = 0;
        for (byte b : input) {
            if ((b & 0xFF) < 0x80) {
                ascii++;
            }
        }
        return (double) ascii / input.length;
    }
}
