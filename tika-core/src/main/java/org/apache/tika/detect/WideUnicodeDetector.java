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
package org.apache.tika.detect;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * An {@link EncodingDetector} that identifies UTF-16 LE/BE and UTF-32 LE/BE
 * purely from structural byte-position patterns — no BOM reliance.
 *
 * <h3>Detection strategy</h3>
 *
 * <p><strong>UTF-32 (null-position check):</strong> For BMP codepoints
 * (U+0000–U+FFFF, covering all major scripts), UTF-32BE always produces
 * {@code 0x00} at byte positions 0 and 1 within each 4-byte group;
 * UTF-32LE always produces {@code 0x00} at positions 2 and 3. UTF-32 is
 * tested before UTF-16 because Latin UTF-32 also triggers the UTF-16
 * null-column check.</p>
 *
 * <p><strong>UTF-16 (three-phase):</strong>
 * <ol>
 *   <li><em>Null-column</em> — Latin/ASCII content: one byte column
 *       (even or odd positions) has a high {@code 0x00} rate.</li>
 *   <li><em>Variety-ratio</em> — scripts with a narrow block-prefix byte
 *       (Arabic 0x06, Hebrew 0x05, Greek 0x03, Devanagari 0x09, …): the
 *       glyph-index column has at least 2× as many distinct values as the
 *       block-prefix column.</li>
 *   <li><em>Block-prefix range</em> — CJK (0x4E–0x9F, 82 values) and
 *       Hangul (0xAC–0xD7, 44 values) where the variety ratio alone may not
 *       be decisive with limited samples: if all values in one column fall
 *       below the surrogate boundary (0xD8) and the other column has more
 *       distinct values, the constrained column is carrying Unicode
 *       block-prefix bytes.</li>
 * </ol>
 * </p>
 *
 * <h3>BOM handling</h3>
 * <p>Any BOM at the start of the stream is stripped <em>before</em> analysis
 * to preserve fixed-width group alignment. A 3-byte UTF-8 BOM would otherwise
 * shift every subsequent byte position by 3, breaking both UTF-16 pair
 * alignment and UTF-32 4-byte group alignment. The BOM bytes are not used to
 * infer the encoding — only the content after them is examined.</p>
 *
 * <h3>What this class does NOT do</h3>
 * <ul>
 *   <li>UTF-8 detection — UTF-8 is variable-width and self-describing; use
 *       grammar validation instead.</li>
 *   <li>Single-byte or multi-byte Asian encoding detection — left to
 *       statistical detectors (Universal, ICU4J).</li>
 * </ul>
 *
 * @since Apache Tika 3.2
 */
public class WideUnicodeDetector implements EncodingDetector {

    /**
     * Maximum bytes read from the stream per call to {@link #detect}.
     * Must be at least as large as {@link #SAMPLE_LIMIT} plus the longest
     * possible BOM (4 bytes for UTF-32).
     */
    private static final int STREAM_READ_LIMIT = 516;

    /**
     * Maximum content bytes analysed (after BOM stripping). Must be a
     * multiple of 4 so that UTF-32 group alignment is preserved.
     */
    private static final int SAMPLE_LIMIT = 512;

    /**
     * Null-column threshold for UTF-16 Latin detection: the null rate in the
     * constrained column must exceed {@code 1 / NULL_THRESHOLD_DENOM}.
     *
     * <p>Set to 4 (threshold {@literal >} 25%) rather than 10 (10%) for two
     * reasons discovered during corpus analysis:
     * <ul>
     *   <li>OLE2 compound documents (.doc, .xls, .msg) have ~12–15% null at
     *       the odd-byte column from 2-byte little-endian header integers; at
     *       the 10% threshold they were wrongly detected as UTF-16LE.</li>
     *   <li>Small bzip2 frames have ~20–25% null at the even-byte column from
     *       block-header zeros; same false-positive risk.</li>
     * </ul>
     * Real Latin UTF-16 text has {@literal >} 90% null in the null column,
     * so raising the threshold to 25% has no effect on legitimate detections.
     * </p>
     */
    private static final int NULL_THRESHOLD_DENOM = 4;

    /**
     * UTF-32 BMP null threshold: at least 90% of 4-byte groups must have both
     * structural positions equal to {@code 0x00}, allowing up to 10% non-BMP
     * codepoints (emoji, historic scripts, etc.).
     */
    private static final double UTF32_NULL_THRESHOLD = 0.90;

    /**
     * Minimum fraction of 4-byte groups whose content byte (position 3 for
     * UTF-32BE, position 0 for UTF-32LE) must be non-zero. Guards against
     * false-positive detection on nearly-null binary data. Set to 0.80 to
     * tolerate CJK characters whose low byte happens to be 0x00 (e.g.
     * U+4E00, U+5000, …), which represent roughly 0.4% of common CJK chars.
     */
    private static final double UTF32_CONTENT_NONZERO_MIN = 0.80;

    /**
     * Minimum ratio of distinct values between the glyph-index column and the
     * block-prefix column for the variety-ratio check to fire.
     */
    private static final double UTF16_VARIETY_RATIO = 2.0;

    /**
     * The constrained column must have fewer than this fraction of pairs as
     * distinct values. Guards against firing on uniformly random data.
     */
    private static final double UTF16_CONSTRAINED_MAX_RATIO = 0.40;

    /**
     * Upper bound (exclusive) for the UTF-16 block-prefix range check.
     * Set to 0xD8 — the start of the UTF-16 surrogate range — which covers
     * every assigned BMP script: Latin, Greek, Cyrillic, Arabic, Hebrew,
     * Devanagari, CJK (0x4E–0x9F), Yi (0xA0–0xA4), Hangul (0xAC–0xD7).
     */
    private static final int BMP_BLOCK_PREFIX_MAX = 0xD8;

    /**
     * Known BOM sequences, longest first so that the 4-byte UTF-32 BOMs are
     * matched before the 2-byte UTF-16 BOMs that share their prefix.
     */
    private static final byte[][] BOMS = {
            {(byte) 0x00, (byte) 0x00, (byte) 0xFE, (byte) 0xFF}, // UTF-32BE
            {(byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x00}, // UTF-32LE
            {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},               // UTF-8
            {(byte) 0xFE, (byte) 0xFF},                             // UTF-16BE
            {(byte) 0xFF, (byte) 0xFE},                             // UTF-16LE
    };

    @Override
    public Charset detect(TikaInputStream tis, Metadata metadata,
                          ParseContext context) throws IOException {
        if (tis == null || !tis.markSupported()) {
            return null;
        }

        tis.mark(STREAM_READ_LIMIT);
        byte[] buf;
        try {
            buf = tis.readNBytes(STREAM_READ_LIMIT);
        } finally {
            tis.reset();
        }

        // 12 = longest BOM (4 bytes) + minimum analysable content (8 bytes)
        if (buf.length < 12) {
            return null;
        }

        return detectEncoding(buf);
    }

    /**
     * Detect the wide Unicode encoding of a raw byte array.
     * Any leading BOM is stripped before analysis so that the fixed-width
     * group alignment is preserved. Callers do not need to strip the BOM
     * themselves.
     *
     * @param bytes raw content bytes, with or without a leading BOM
     * @return detected charset, or {@code null} if no wide Unicode structure
     *         is found
     */
    public static Charset detectEncoding(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return null;
        }

        bytes = skipBom(bytes);

        int sampleLen = (Math.min(bytes.length, SAMPLE_LIMIT) / 4) * 4;
        if (sampleLen < 8) {
            return null;
        }

        Charset utf32 = tryUtf32(bytes, sampleLen);
        if (utf32 != null) {
            return utf32;
        }
        return tryUtf16(bytes, sampleLen);
    }

    /**
     * Strips any leading BOM from {@code bytes}. If no BOM is found the
     * original array is returned unchanged (no copy).
     */
    public static byte[] skipBom(byte[] bytes) {
        for (byte[] bom : BOMS) {
            if (startsWith(bytes, bom)) {
                return Arrays.copyOfRange(bytes, bom.length, bytes.length);
            }
        }
        return bytes;
    }

    /**
     * UTF-32 detection via null-position signature, structural validity, and
     * content-position non-zero check.
     *
     * <p>Structural validity: each 4-byte group is checked against the Unicode
     * codepoint range (U+0000–U+10FFFF) and the surrogate exclusion zone
     * (U+D800–U+DFFF). A single invalid group rules out that byte order.</p>
     *
     * <p>Content-position check: the byte carrying the actual character value
     * (position 3 for BE, position 0 for LE) must be non-zero in at least
     * {@value #UTF32_CONTENT_NONZERO_MIN} of groups. This rejects nearly-null
     * binary data that satisfies the structural-zero check by accident.</p>
     */
    private static Charset tryUtf32(byte[] bytes, int sampleLen) {
        int groups = sampleLen / 4;
        int bothZeroAt01 = 0;
        int bothZeroAt23 = 0;
        int[] countsPos0 = new int[256];
        int[] countsPos3 = new int[256];
        boolean invalidBe = false;
        boolean invalidLe = false;

        for (int g = 0; g < groups; g++) {
            int b0 = bytes[g * 4]     & 0xFF;
            int b1 = bytes[g * 4 + 1] & 0xFF;
            int b2 = bytes[g * 4 + 2] & 0xFF;
            int b3 = bytes[g * 4 + 3] & 0xFF;

            if (b0 == 0 && b1 == 0) bothZeroAt01++;
            if (b2 == 0 && b3 == 0) bothZeroAt23++;
            countsPos0[b0]++;
            countsPos3[b3]++;

            // UTF-32BE validity: codepoint = (b0<<24)|(b1<<16)|(b2<<8)|b3
            // Valid range: 0x000000–0x10FFFF, excluding surrogates 0xD800–0xDFFF
            if (!invalidBe) {
                if (b0 != 0 || b1 > 0x10 || (b1 == 0 && 0xD8 <= b2 && b2 <= 0xDF)) {
                    invalidBe = true;
                }
            }

            // UTF-32LE validity: codepoint = (b3<<24)|(b2<<16)|(b1<<8)|b0
            if (!invalidLe) {
                if (b3 != 0 || b2 > 0x10 || (b2 == 0 && 0xD8 <= b1 && b1 <= 0xDF)) {
                    invalidLe = true;
                }
            }
        }

        // UTF-32BE: structural zeros at positions 0,1 + validity + content non-zero
        if (!invalidBe
                && (double) bothZeroAt01 / groups >= UTF32_NULL_THRESHOLD
                && countUnique(countsPos3) >= 2
                && (double) (groups - countsPos3[0]) / groups >= UTF32_CONTENT_NONZERO_MIN) {
            return Charset.forName("UTF-32BE");
        }

        // UTF-32LE: structural zeros at positions 2,3 + validity + content non-zero
        if (!invalidLe
                && (double) bothZeroAt23 / groups >= UTF32_NULL_THRESHOLD
                && countUnique(countsPos0) >= 2
                && (double) (groups - countsPos0[0]) / groups >= UTF32_CONTENT_NONZERO_MIN) {
            return Charset.forName("UTF-32LE");
        }

        return null;
    }

    /**
     * UTF-16 detection via three phases, with surrogate-pair sequence validation
     * running in parallel to rule out structurally impossible byte sequences.
     *
     * <p>Surrogate validation: in UTF-16BE the even byte is the high byte of
     * each code unit; in UTF-16LE the odd byte is the high byte. A high surrogate
     * (0xD8–0xDB) must be immediately followed by a low surrogate (0xDC–0xDF).
     * A lone low surrogate, or a high surrogate not followed by a low surrogate,
     * marks that byte order as invalid.</p>
     */
    private static Charset tryUtf16(byte[] bytes, int sampleLen) {
        int pairs = sampleLen / 2;
        int nullsAtEven = 0;
        int nullsAtOdd  = 0;
        int[] countsEven = new int[256];
        int[] countsOdd  = new int[256];

        // Surrogate-pair state: true = we just saw a high surrogate and expect a low
        boolean awaitLowBe = false; // UTF-16BE: high byte is even
        boolean awaitLowLe = false; // UTF-16LE: high byte is odd
        boolean invalidBe  = false;
        boolean invalidLe  = false;

        for (int p = 0; p < pairs; p++) {
            int even = bytes[p * 2]     & 0xFF;
            int odd  = bytes[p * 2 + 1] & 0xFF;

            if (even == 0) nullsAtEven++;
            if (odd  == 0) nullsAtOdd++;
            countsEven[even]++;
            countsOdd[odd]++;

            // UTF-16BE surrogate validation (high byte = even)
            if (!invalidBe) {
                if (awaitLowBe) {
                    if (0xDC <= even && even <= 0xDF) {
                        awaitLowBe = false; // valid low surrogate completes pair
                    } else {
                        invalidBe = true;   // expected low surrogate, got something else
                    }
                } else {
                    if (0xD8 <= even && even <= 0xDB) {
                        awaitLowBe = true;  // high surrogate — expect low next
                    } else if (0xDC <= even && even <= 0xDF) {
                        invalidBe = true;   // lone low surrogate
                    }
                }
            }

            // UTF-16LE surrogate validation (high byte = odd)
            if (!invalidLe) {
                if (awaitLowLe) {
                    if (0xDC <= odd && odd <= 0xDF) {
                        awaitLowLe = false;
                    } else {
                        invalidLe = true;
                    }
                } else {
                    if (0xD8 <= odd && odd <= 0xDB) {
                        awaitLowLe = true;
                    } else if (0xDC <= odd && odd <= 0xDF) {
                        invalidLe = true;
                    }
                }
            }
        }

        // An unmatched high surrogate at end of sample is also invalid
        if (awaitLowBe) invalidBe = true;
        if (awaitLowLe) invalidLe = true;

        // Phase 1: null-column (Latin/ASCII)
        boolean highEven = nullsAtEven * NULL_THRESHOLD_DENOM > pairs;
        boolean highOdd  = nullsAtOdd  * NULL_THRESHOLD_DENOM > pairs;
        if (highOdd  && !highEven && !invalidLe) return StandardCharsets.UTF_16LE;
        if (highEven && !highOdd  && !invalidBe) return StandardCharsets.UTF_16BE;

        // Phase 2: variety-ratio (Arabic, Hebrew, Greek, Devanagari, …)
        //
        // Extra guard: the constrained (block-prefix) column must NOT be
        // dominated by 0x00. Real script prefixes are non-null constants
        // (Arabic 0x06, Hebrew 0x05, Greek 0x03, Devanagari 0x09, …).
        // When 0x00 is the dominant byte in the constrained column we are
        // likely looking at binary data with sparse high bytes (e.g. MIPS
        // big-endian code where R-type opcodes place 0x00 at byte 0 of
        // every instruction), not a Unicode block prefix.
        int uniqueEven = countUnique(countsEven);
        int uniqueOdd  = countUnique(countsOdd);
        double constrainedMax = pairs * UTF16_CONSTRAINED_MAX_RATIO;

        if (!invalidLe && (double) uniqueEven / uniqueOdd >= UTF16_VARIETY_RATIO
                && uniqueOdd <= constrainedMax
                && mostCommon(countsOdd) != 0) {
            return StandardCharsets.UTF_16LE;
        }
        if (!invalidBe && (double) uniqueOdd / uniqueEven >= UTF16_VARIETY_RATIO
                && uniqueEven <= constrainedMax
                && mostCommon(countsEven) != 0) {
            return StandardCharsets.UTF_16BE;
        }

        // Phase 3: block-prefix range (CJK, Hangul — wide block-prefix ranges)
        //
        // We require:
        //   oddInRange  — all odd-position bytes < 0xD8 (below the surrogate
        //                 boundary).  This is the high-byte (block-prefix)
        //                 column for UTF-16LE.
        //   !evenInRange — at least one even-position byte ≥ 0xD8 (the glyph-
        //                 index column overflows the surrogate boundary, which
        //                 is expected for CJK low bytes like 0xE5, 0xEF, 0xF4).
        //   nonNullAllAbove — no non-null byte below 0x20 in the constrained
        //                 column.  Binary formats inject control tokens here
        //                 (ISOBMFF 0x01/0x02, LZ4 0x04/0x15); CJK and Hangul
        //                 block-prefix bytes are always ≥ 0x4E/0xAC.
        //
        // We also require uniqueEven > uniqueOdd for LE (uniqueOdd > uniqueEven
        // for BE).  This is not about discriminating from random binary — the
        // scenario where "12 vs 11 unique values, all below 0xD8" could cause
        // a false positive is already prevented by !evenInRange (which requires
        // at least one byte ≥ 0xD8 in the even column, contradicting "all
        // below 0xD8").  The real purpose is orientation: it prevents Latin
        // UTF-16BE data (whose odd column is full of diverse ASCII content
        // bytes and whose even column is dominated by 0x00) from being
        // misidentified as UTF-16LE when a stray byte ≥ 0xD8 lands in the
        // even column (e.g. an injected or corrupted surrogate byte).  In that
        // pathological case uniqueEven ≈ 2 << uniqueOdd, so the check fails
        // cleanly.  For legitimate CJK and Hangul the glyph-index column
        // always has at least as many distinct values as the block-prefix
        // column, so a gap of 1 is enough.
        boolean oddInRange  = allInRange(countsOdd,  BMP_BLOCK_PREFIX_MAX);
        boolean evenInRange = allInRange(countsEven, BMP_BLOCK_PREFIX_MAX);
        if (!invalidLe && oddInRange  && !evenInRange && uniqueEven > uniqueOdd
                && nonNullAllAbove(countsOdd,  0x20)) {
            return StandardCharsets.UTF_16LE;
        }
        if (!invalidBe && evenInRange && !oddInRange  && uniqueOdd  > uniqueEven
                && nonNullAllAbove(countsEven, 0x20)) {
            return StandardCharsets.UTF_16BE;
        }

        return null;
    }

    private static int countUnique(int[] counts) {
        int n = 0;
        for (int c : counts) if (c > 0) n++;
        return n;
    }

    private static boolean allInRange(int[] counts, int maxExclusive) {
        for (int v = maxExclusive; v < counts.length; v++) {
            if (counts[v] > 0) return false;
        }
        return true;
    }

    /**
     * Returns the byte value (0–255) that appears most frequently in
     * {@code counts}. Ties are broken in favour of the lower value.
     */
    private static int mostCommon(int[] counts) {
        int best = 0;
        for (int v = 1; v < counts.length; v++) {
            if (counts[v] > counts[best]) {
                best = v;
            }
        }
        return best;
    }

    /**
     * Returns {@code true} if every <em>non-null</em> byte value in
     * {@code counts} is {@literal >=} {@code minInclusive}.
     *
     * <p>Null bytes ({@code 0x00}) are explicitly allowed: in a CJK or Hangul
     * UTF-16 document the block-prefix (high-byte) column contains {@code 0x00}
     * for any ASCII character in the text (spaces, punctuation, Latin product
     * names, …). Those null values are harmless — the {@code allInRange} check
     * already bounds the column below {@code 0xD8}.</p>
     *
     * <p>What we actually reject are <em>non-zero</em> control bytes such as
     * {@code 0x01}–{@code 0x1F}: these appear in binary format tokens (ISOBMFF
     * version/flags fields at {@code 0x01}–{@code 0x02}, LZ4 frame tokens at
     * {@code 0x04} and {@code 0x15}, …) but never as Unicode block prefixes
     * for scripts above Basic Latin.</p>
     */
    private static boolean nonNullAllAbove(int[] counts, int minInclusive) {
        for (int v = 1; v < minInclusive; v++) { // start at 1, skip null
            if (counts[v] > 0) return false;
        }
        return true;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }
}
