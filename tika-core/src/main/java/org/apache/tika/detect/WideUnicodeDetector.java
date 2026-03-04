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
import java.util.Collections;
import java.util.List;

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
 *   <li><em>Block-prefix range</em> — CJK Unified (0x4E–0x9F), Hangul
 *       (0xAC–0xD7), CJK Compatibility Ideographs (0xF9–0xFA), and Halfwidth
 *       and Fullwidth Forms (0xFF, e.g. Chinese fullwidth punctuation ，！？):
 *       where the variety ratio alone may not be decisive with limited samples.
 *       The constrained column must have no bytes in the surrogate/PUA zone
 *       (0xD8–0xF8); the glyph column must have at least one byte ≥ 0xD8;
 *       and non-null bytes in the constrained column must all be ≥ 0x20.</li>
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
     * Boundary of the UTF-16 surrogate range. Bytes ≥ this value in the glyph
     * (high-diversity) column confirm it is not the block-prefix column: the
     * block-prefix column of any well-formed BMP document stays below 0xD8 for
     * the most common scripts, and the glyph column overflows above it for CJK
     * low bytes and other diverse content.
     */
    private static final int BMP_BLOCK_PREFIX_MAX = 0xD8;

    /**
     * Lower bound of the re-allowed block-prefix bytes above the surrogate zone.
     * Bytes in [{@code BMP_BLOCK_PREFIX_MAX}, {@code BLOCK_PREFIX_EXCLUSION_MAX})
     * — i.e. 0xD8–0xF8 — are excluded from the block-prefix column:
     * 0xD8–0xDF are UTF-16 surrogates (handled separately by the surrogate
     * validator) and 0xE0–0xF8 are Private Use Area high bytes unlikely to
     * appear as legitimate block prefixes in ordinary text.
     * Bytes from {@code BLOCK_PREFIX_EXCLUSION_MAX} onward are re-allowed:
     * <ul>
     *   <li>0xF9–0xFA: CJK Compatibility Ideographs (U+F900–U+FAFF)</li>
     *   <li>0xFF: Halfwidth and Fullwidth Forms (U+FF00–U+FFEF) — extremely
     *       common in Chinese text as fullwidth punctuation (，！？：；（）…)</li>
     * </ul>
     */
    private static final int BLOCK_PREFIX_EXCLUSION_MAX = 0xF9;

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
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        if (tis == null || !tis.markSupported()) {
            return Collections.emptyList();
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
            return Collections.emptyList();
        }

        WideUnicodeSignal signal = detectSignal(buf);
        if (signal == null) {
            return Collections.emptyList();
        }

        // Write the signal into the per-detection context so downstream detectors
        // (e.g. ML models not trained on null-heavy UTF-16/32 data) can defer
        // without re-examining the bytes.  We use EncodingDetectorContext rather
        // than ParseContext directly so the signal does not outlive this single
        // detection pass and does not bleed into embedded-document parses.
        EncodingDetectorContext encodingContext =
                parseContext.get(EncodingDetectorContext.class);
        if (encodingContext != null) {
            encodingContext.setWideUnicodeSignal(signal);
        }

        if (signal.getKind() == WideUnicodeSignal.Kind.ILLEGAL_SURROGATES) {
            return Collections.emptyList();
        }
        // Structural heuristic, not a BOM read — use high but not absolute confidence
        // so that language-scoring arbitration can override if the decoded text is garbage.
        return List.of(new EncodingResult(signal.getCharset(), 0.95f));
    }

    /**
     * Full detection returning a {@link WideUnicodeSignal}, distinguishing
     * "valid detection", "illegal surrogates", and "not wide Unicode" (null).
     */
    private static WideUnicodeSignal detectSignal(byte[] bytes) {
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
            return WideUnicodeSignal.valid(utf32);
        }
        return tryUtf16Signal(bytes, sampleLen);
    }

    /**
     * Detect the wide Unicode encoding of a raw byte array.
     * Any leading BOM is stripped before analysis so that the fixed-width
     * group alignment is preserved. Callers do not need to strip the BOM
     * themselves.
     *
     * <p>Returns {@code null} for both "not wide Unicode" and
     * "illegal surrogates". Use
     * {@link #detect(TikaInputStream, Metadata, ParseContext)} with a live
     * {@link ParseContext} containing an {@link EncodingDetectorContext} if
     * you need to distinguish those two cases via {@link WideUnicodeSignal}.</p>
     *
     * @param bytes raw content bytes, with or without a leading BOM
     * @return detected charset, or {@code null} if no wide Unicode structure
     *         is found or if surrogates are illegal
     */
    public static Charset detectEncoding(byte[] bytes) {
        WideUnicodeSignal signal = detectSignal(bytes);
        if (signal == null || signal.isIllegalSurrogates()) {
            return null;
        }
        return signal.getCharset();
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
     * UTF-16 detection via three phases, returning a {@link WideUnicodeSignal}
     * that distinguishes a valid detection, illegal surrogates, and no match.
     *
     * <p>Surrogate validation: in UTF-16BE the even byte is the high byte of
     * each code unit; in UTF-16LE the odd byte is the high byte. A high surrogate
     * (0xD8–0xDB) must be immediately followed by a low surrogate (0xDC–0xDF).
     * A lone low surrogate, or a high surrogate not followed by a low surrogate,
     * marks that byte order as invalid. When a structural phase would have fired
     * but validity failed, {@link WideUnicodeSignal#illegalSurrogates()} is
     * returned so downstream detectors know not to attempt decoding.</p>
     */
    private static WideUnicodeSignal tryUtf16Signal(byte[] bytes, int sampleLen) {
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
        if (highOdd && !highEven) {
            return invalidLe ? WideUnicodeSignal.illegalSurrogates()
                             : WideUnicodeSignal.valid(StandardCharsets.UTF_16LE);
        }
        if (highEven && !highOdd) {
            return invalidBe ? WideUnicodeSignal.illegalSurrogates()
                             : WideUnicodeSignal.valid(StandardCharsets.UTF_16BE);
        }

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

        if ((double) uniqueEven / uniqueOdd >= UTF16_VARIETY_RATIO
                && uniqueOdd <= constrainedMax
                && mostCommon(countsOdd) != 0) {
            return invalidLe ? WideUnicodeSignal.illegalSurrogates()
                             : WideUnicodeSignal.valid(StandardCharsets.UTF_16LE);
        }
        if ((double) uniqueOdd / uniqueEven >= UTF16_VARIETY_RATIO
                && uniqueEven <= constrainedMax
                && mostCommon(countsEven) != 0) {
            return invalidBe ? WideUnicodeSignal.illegalSurrogates()
                             : WideUnicodeSignal.valid(StandardCharsets.UTF_16BE);
        }

        // Phase 3: block-prefix range (CJK, Hangul, Fullwidth, CJK Compat)
        //
        // Two separate range checks are used — one for each role:
        //
        //   isBlockPrefixColumn(col) — RELAXED — identifies the block-prefix
        //                 (constrained) column.  Requires no bytes in the
        //                 surrogate/PUA exclusion zone [0xD8, 0xF9).  Allows
        //                 0xF9–0xFF so that CJK Compatibility Ideographs and
        //                 Fullwidth Forms (0xFF) are accepted.  Chinese text
        //                 routinely mixes fullwidth punctuation (U+FF0C ，,
        //                 U+FF01 ！, …) with CJK Unified Ideographs; the 0xFF
        //                 high byte would break the old strict "all < 0xD8"
        //                 check even though the stream is perfectly valid UTF-16.
        //
        //   !allInRange(col, 0xD8) — STRICT — identifies the glyph-index
        //                 (high-diversity) column.  At least one byte ≥ 0xD8
        //                 must be present; this is the natural overflow from
        //                 CJK low bytes (0xD8–0xFF for characters like 以=0xE5,
        //                 们=0xEC, 说=0xF4) and provides orientation so we can
        //                 tell which column is which.
        //
        //   nonNullAllAbove — no non-null byte below 0x20 in the block-prefix
        //                 column.  Binary formats inject control tokens here
        //                 (ISOBMFF 0x01/0x02, LZ4 0x04/0x15); CJK and Hangul
        //                 block-prefix bytes are always ≥ 0x4E/0xAC, and
        //                 Fullwidth Forms are 0xFF.
        //
        //   uniqueGlyph > uniquePrefix — orientation guard (see Phase 2 note).
        boolean oddInRange   = allInRange(countsOdd,  BMP_BLOCK_PREFIX_MAX);
        boolean evenInRange  = allInRange(countsEven, BMP_BLOCK_PREFIX_MAX);
        boolean oddIsPrefix  = isBlockPrefixColumn(countsOdd);
        boolean evenIsPrefix = isBlockPrefixColumn(countsEven);
        if (oddIsPrefix && !evenInRange && uniqueEven > uniqueOdd
                && nonNullAllAbove(countsOdd, 0x20)) {
            return invalidLe ? WideUnicodeSignal.illegalSurrogates()
                             : WideUnicodeSignal.valid(StandardCharsets.UTF_16LE);
        }
        if (evenIsPrefix && !oddInRange && uniqueOdd > uniqueEven
                && nonNullAllAbove(countsEven, 0x20)) {
            return invalidBe ? WideUnicodeSignal.illegalSurrogates()
                             : WideUnicodeSignal.valid(StandardCharsets.UTF_16BE);
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
     * Returns {@code true} if {@code counts} is consistent with a UTF-16
     * block-prefix column: no bytes in the surrogate/PUA exclusion zone
     * [{@link #BMP_BLOCK_PREFIX_MAX}, {@link #BLOCK_PREFIX_EXCLUSION_MAX})
     * (i.e. 0xD8–0xF8).
     *
     * <p>This is a relaxed alternative to {@link #allInRange(int[], int)}
     * for Phase 3's constrained-column check. It accepts high block-prefix
     * bytes used in CJK-family documents — 0xF9–0xFA (CJK Compatibility
     * Ideographs) and 0xFF (Halfwidth and Fullwidth Forms) — while still
     * rejecting surrogates (0xD8–0xDF) and Private Use Area bytes (0xE0–0xF8)
     * that should not appear as standalone block prefixes.</p>
     */
    private static boolean isBlockPrefixColumn(int[] counts) {
        for (int v = BMP_BLOCK_PREFIX_MAX; v < BLOCK_PREFIX_EXCLUSION_MAX; v++) {
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
