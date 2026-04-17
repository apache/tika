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
 * Feature extractor for the UTF-16 specialist of the mixture-of-experts
 * charset detector.  Produces a small, dense, position-aware feature vector
 * that is <strong>immune to HTML markup by construction</strong>: features
 * capture the 2-byte alignment asymmetry that UTF-16 content produces and
 * HTML content (which has no 2-byte alignment) cannot.
 *
 * <h3>Feature vector</h3>
 *
 * <p>12 dense integer features: byte counts across six byte-value ranges,
 * split by column (even-offset vs odd-offset in the probe).  Indexing:</p>
 *
 * <table>
 * <tr><th>Index</th><th>Feature</th></tr>
 * <tr><td>0</td><td>count_even(0x00)</td></tr>
 * <tr><td>1</td><td>count_odd(0x00)</td></tr>
 * <tr><td>2</td><td>count_even(0x01-0x1F, excluding 0x09/0x0A/0x0D)</td></tr>
 * <tr><td>3</td><td>count_odd(0x01-0x1F, excluding 0x09/0x0A/0x0D)</td></tr>
 * <tr><td>4</td><td>count_even(0x20-0x7E, plus 0x09, 0x0A, 0x0D)</td></tr>
 * <tr><td>5</td><td>count_odd(0x20-0x7E, plus 0x09, 0x0A, 0x0D)</td></tr>
 * <tr><td>6</td><td>count_even(0x7F)</td></tr>
 * <tr><td>7</td><td>count_odd(0x7F)</td></tr>
 * <tr><td>8</td><td>count_even(0x80-0x9F)</td></tr>
 * <tr><td>9</td><td>count_odd(0x80-0x9F)</td></tr>
 * <tr><td>10</td><td>count_even(0xA0-0xFF)</td></tr>
 * <tr><td>11</td><td>count_odd(0xA0-0xFF)</td></tr>
 * </table>
 *
 * <h3>Why this is HTML-immune</h3>
 *
 * <p>HTML has no 2-byte alignment — tags are variable-length ({@code <br>}
 * is 4 bytes, {@code <div>} is 5, {@code </span>} is 7), entities and
 * whitespace are arbitrary.  Under random byte-offset content, any byte
 * range has equal expected frequency at even vs odd positions.  The
 * maxent model pairing this extractor learns weights that reward column
 * asymmetry: HTML produces near-zero asymmetry on every range →
 * near-zero contribution to every UTF-16 class logit.</p>
 *
 * <p>UTF-16 has strict 2-byte alignment by definition.  The "high byte" of
 * every codepoint lands in one column, the "low byte" in the other.  This
 * alignment cannot be faked by non-UTF-16 content without deliberately
 * constructing 2-byte-aligned patterns, which organic text content never
 * does.</p>
 *
 * <h3>Why raw counts instead of asymmetry ratios</h3>
 *
 * <p>The maxent model learns asymmetry weights naturally from raw counts:
 * a positive weight on {@code count_even(X)} paired with a negative weight
 * on {@code count_odd(X)} produces a dot-product proportional to
 * {@code count_even(X) - count_odd(X)}, which IS the asymmetry signal up
 * to normalization.  Explicit asymmetry features would add redundancy
 * without adding information.</p>
 *
 * <h3>What it doesn't do</h3>
 *
 * <ul>
 *   <li>No UTF-32 detection.  UTF-32 stays structural (4-byte alignment
 *       check) and doesn't need a statistical model.</li>
 *   <li>No discrimination between UTF-16 content languages (Japanese vs
 *       Chinese vs Korean).  CharSoup's language scoring handles that
 *       after decoding.  The UTF-16 specialist returns only
 *       {@code UTF-16-LE} or {@code UTF-16-BE}.</li>
 *   <li>No BOM handling — the caller is responsible for stripping BOM
 *       before feeding bytes to this extractor.</li>
 * </ul>
 *
 * @see org.apache.tika.ml.LinearModel
 */
public class Utf16ColumnFeatureExtractor implements FeatureExtractor<byte[]> {

    /** Number of byte-value ranges tracked. */
    public static final int NUM_RANGES = 6;

    /** Number of columns (even-offset vs odd-offset). */
    public static final int NUM_COLUMNS = 2;

    /** Total feature-vector dimension: ranges * columns. */
    public static final int NUM_FEATURES = NUM_RANGES * NUM_COLUMNS;

    /**
     * Precomputed byte-to-range-index lookup.  Populated at class init.
     * Ranges chosen to cover all UTF-16 high-byte distributions:
     * <ul>
     *   <li>Range 0 — 0x00: null column (UTF-16 Latin signal)</li>
     *   <li>Range 1 — 0x01-0x1F excluding 0x09/0x0A/0x0D: C0 controls
     *       (non-Latin BMP scripts have their high byte here: Cyrillic
     *       0x04, Greek 0x03, Hebrew 0x05, Arabic 0x06, Thai 0x0E)</li>
     *   <li>Range 2 — 0x20-0x7E + 0x09/0x0A/0x0D: printable ASCII + common
     *       whitespace (UTF-16 Latin text column + CJK low bytes + HTML
     *       content)</li>
     *   <li>Range 3 — 0x7F: DEL (rare)</li>
     *   <li>Range 4 — 0x80-0x9F: C1 controls; UTF-16 CJK high byte for
     *       codepoints U+8000-U+9FFF.  <strong>HTML never emits these
     *       bytes</strong> — a crucial HTML-uncontaminable signal.</li>
     *   <li>Range 5 — 0xA0-0xFF: extended Latin high bytes, CJK
     *       codepoints U+A000+.</li>
     * </ul>
     */
    private static final int[] RANGE_OF_BYTE = new int[256];

    static {
        for (int b = 0; b < 256; b++) {
            if (b == 0x00) {
                RANGE_OF_BYTE[b] = 0;
            } else if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) {
                RANGE_OF_BYTE[b] = 1;
            } else if (b <= 0x7E) {  // includes 0x09, 0x0A, 0x0D (not in range 1) and 0x20-0x7E
                RANGE_OF_BYTE[b] = 2;
            } else if (b == 0x7F) {
                RANGE_OF_BYTE[b] = 3;
            } else if (b <= 0x9F) {
                RANGE_OF_BYTE[b] = 4;
            } else {
                RANGE_OF_BYTE[b] = 5;
            }
        }
    }

    @Override
    public int[] extract(byte[] input) {
        int[] counts = new int[NUM_FEATURES];
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
        int[] counts = new int[NUM_FEATURES];
        if (input == null || length == 0) {
            return counts;
        }
        extractInto(input, offset, offset + length, counts);
        return counts;
    }

    /**
     * Sparse extraction into caller-owned, reusable buffers.  For this
     * small dense vector, "sparse" just means "write non-zero feature
     * indices into {@code touched}".  Buckets with zero count are not
     * listed.
     *
     * @param input   raw bytes
     * @param dense   scratch buffer of length {@link #NUM_FEATURES},
     *                all-zeros on entry; caller clears used entries afterwards
     * @param touched buffer receiving indices of non-zero features
     * @return number of entries written into {@code touched}
     */
    public int extractSparseInto(byte[] input, int[] dense, int[] touched) {
        if (input == null || input.length == 0) {
            return 0;
        }
        extractInto(input, 0, input.length, dense);
        int n = 0;
        for (int i = 0; i < NUM_FEATURES; i++) {
            if (dense[i] != 0) {
                touched[n++] = i;
            }
        }
        return n;
    }

    private static void extractInto(byte[] b, int from, int to, int[] counts) {
        for (int i = from; i < to; i++) {
            int v = b[i] & 0xFF;
            int range = RANGE_OF_BYTE[v];
            int column = (i - from) & 1;  // 0 = even offset within probe, 1 = odd
            counts[range * NUM_COLUMNS + column]++;
        }
    }

    @Override
    public int getNumBuckets() {
        return NUM_FEATURES;
    }

    /** Human-readable label for feature index {@code i} (for debugging). */
    public static String featureLabel(int i) {
        if (i < 0 || i >= NUM_FEATURES) {
            return "(invalid: " + i + ")";
        }
        int range = i / NUM_COLUMNS;
        int column = i % NUM_COLUMNS;
        String rangeName;
        switch (range) {
            case 0:
                rangeName = "0x00";
                break;
            case 1:
                rangeName = "0x01-1F-nws";
                break;
            case 2:
                rangeName = "0x20-7E+tab/lf/cr";
                break;
            case 3:
                rangeName = "0x7F";
                break;
            case 4:
                rangeName = "0x80-9F";
                break;
            case 5:
                rangeName = "0xA0-FF";
                break;
            default:
                rangeName = "?";
                break;
        }
        String columnName = (column == 0) ? "even" : "odd";
        return "count_" + columnName + "(" + rangeName + ")";
    }

    @Override
    public String toString() {
        return "Utf16ColumnFeatureExtractor{features=" + NUM_FEATURES + "}";
    }
}
