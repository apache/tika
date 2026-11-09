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

import java.nio.charset.Charset;

/**
 * Structural analysis for UTF-32 LE/BE, plus UTF-16 surrogate validity
 * flags. This is an internal component of {@code MojibusterEncodingDetector}'s
 * pipeline — not a standalone {@code EncodingDetector}. Requires upstream
 * BOM stripping.
 *
 * <h3>UTF-32</h3>
 * <p>Every 4-byte group is decoded as a 32-bit integer in both BE and LE
 * order and checked for Unicode validity (0x000000–0x10FFFF, excluding
 * surrogates). The valid range is only 0.004% of the 32-bit space, so
 * non-UTF-32 data almost always produces out-of-range values immediately.
 * Inspired by ICU4J's {@code CharsetRecog_UTF_32}.</p>
 *
 * <h3>UTF-16 surrogate validation</h3>
 * <p>UTF-16 positive detection is the statistical model's job
 * ({@link HashedNgramEncodingDetector}).  This class only performs surrogate-
 * invalidity validation: {@link Result#invalidUtf16Be} and
 * {@link Result#invalidUtf16Le} carry whether the probe contains
 * structurally impossible UTF-16 surrogate sequences under each
 * endianness, so callers can suppress the model's UTF-16 labels
 * when the bytes cannot be valid UTF-16.  {@link Result#invalidUtf32Be} and
 * {@link Result#invalidUtf32Le} do the same for the model's UTF-32 labels.  A high surrogate in the last
 * code unit of the probe is a pair cut by the probe boundary, not an error.</p>
 *
 * <p>All methods are stateless and safe to call from multiple threads.</p>
 */
final class WideUnicodeDetector {

    private WideUnicodeDetector() {}

    /**
     * Result of wide-Unicode structural analysis. Contains a positive detection
     * (if one was made) plus invalidity flags that can be used to suppress
     * model predictions even when no positive detection fires.
     */
    static final class Result {
        /** Positively detected charset, or {@code null} if none. */
        public final Charset charset;
        /** True if the probe contains invalid UTF-16-BE surrogate sequences. */
        public final boolean invalidUtf16Be;
        /** True if the probe contains invalid UTF-16-LE surrogate sequences. */
        public final boolean invalidUtf16Le;
        /** True if some 4-byte group is not a code point under UTF-32BE. */
        public final boolean invalidUtf32Be;
        /** True if some 4-byte group is not a code point under UTF-32LE. */
        public final boolean invalidUtf32Le;

        private Result(Charset charset, boolean invalidUtf16Be, boolean invalidUtf16Le,
                       boolean invalidUtf32Be, boolean invalidUtf32Le) {
            this.charset = charset;
            this.invalidUtf16Be = invalidUtf16Be;
            this.invalidUtf16Le = invalidUtf16Le;
            this.invalidUtf32Be = invalidUtf32Be;
            this.invalidUtf32Le = invalidUtf32Le;
        }

        static final Result EMPTY = new Result(null, false, false, false, false);
    }

    /**
     * Attempt to detect UTF-32 or UTF-16 from structural byte patterns.
     * Also reports UTF-16 invalidity for use as a model suppression signal.
     *
     * @param bytes  BOM-stripped probe bytes
     * @param offset start of the region to analyse
     * @param length number of bytes to analyse
     * @return result with detected charset and invalidity flags
     */
    static Result analyze(byte[] bytes, int offset, int length) {
        if (bytes == null || length < 8) {
            return Result.EMPTY;
        }

        // UTF-32 must be tested before UTF-16: Latin UTF-32 also triggers the
        // UTF-16 null-column check (every other stride-2 pair is (0x00, 0x00)).
        int[] utf32 = utf32Invalid(bytes, offset, length);
        boolean invalidBe = utf32[0] > 0;
        boolean invalidLe = utf32[1] > 0;
        if (!invalidBe && utf32[2] >= UTF32_MIN_VALID) {
            return new Result(Charset.forName("UTF-32BE"), false, false, false, invalidLe);
        }
        if (!invalidLe && utf32[2] >= UTF32_MIN_VALID) {
            return new Result(Charset.forName("UTF-32LE"), false, false, invalidBe, false);
        }
        Result utf16 = tryUtf16(bytes, offset, length);
        return new Result(null, utf16.invalidUtf16Be, utf16.invalidUtf16Le, invalidBe, invalidLe);
    }

    /** Convenience overload for a full array. */
    static Result analyze(byte[] bytes) {
        return bytes == null ? Result.EMPTY : analyze(bytes, 0, bytes.length);
    }

    /**
     * Convenience method that returns only the detected charset.
     * Use {@link #analyze} when you also need the invalidity flags.
     */
    static Charset detect(byte[] bytes) {
        return analyze(bytes).charset;
    }

    // -----------------------------------------------------------------------
    //  UTF-32
    // -----------------------------------------------------------------------

    /**
     * Minimum number of valid codepoints required for detection. At 8 bytes
     * we get exactly 2 groups; the probability that 2 random 4-byte values
     * both fall in 0x000000–0x10FFFF (excluding surrogates) is ~1.6e-11,
     * so 2 is safe against false positives on real-world byte data.
     */
    private static final int UTF32_MIN_VALID = 2;

    /**
     * Counts the 4-byte groups that are not a Unicode codepoint
     * (U+0000–U+10FFFF, excluding surrogates U+D800–U+DFFF) under each order.
     *
     * <p>Inspired by ICU4J's {@code CharsetRecog_UTF_32}: the valid codepoint
     * range is tiny (0x000000–0x10FFFF) relative to the 32-bit value space,
     * so random data almost always produces out-of-range values that
     * immediately disqualify it. No null-byte density threshold is needed,
     * which means non-BMP content (emoji, historic scripts) works perfectly.</p>
     */
    /** {invalid BE groups, invalid LE groups, groups} over the first 512 bytes. */
    private static int[] utf32Invalid(byte[] bytes, int offset, int length) {
        int sampleLen = (Math.min(length, 512) / 4) * 4;
        int groups = sampleLen / 4;
        int invalidBe = 0;
        int invalidLe = 0;

        for (int g = 0; g < groups; g++) {
            int base = offset + g * 4;
            int b0 = bytes[base] & 0xFF;
            int b1 = bytes[base + 1] & 0xFF;
            int b2 = bytes[base + 2] & 0xFF;
            int b3 = bytes[base + 3] & 0xFF;

            int cpBe = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
            if (!(cpBe >= 0 && cpBe <= 0x10FFFF && (cpBe < 0xD800 || cpBe > 0xDFFF))) {
                invalidBe++;
            }

            int cpLe = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
            if (!(cpLe >= 0 && cpLe <= 0x10FFFF && (cpLe < 0xD800 || cpLe > 0xDFFF))) {
                invalidLe++;
            }
        }
        return new int[] {invalidBe, invalidLe, groups};
    }

    // -----------------------------------------------------------------------
    //  UTF-16
    // -----------------------------------------------------------------------

    /**
     * Surrogate-validation scan over {@code length} bytes starting at
     * {@code offset}.  Does not attempt UTF-16 positive detection.  Returns only
     * surrogate-invalidity flags under each endianness, used by
     * {@code MojibusterEncodingDetector} to suppress UTF-16 labels from
     * the main statistical model on probes that cannot be valid UTF-16.
     */
    private static Result tryUtf16(byte[] bytes, int offset, int length) {
        int sampleLen = (Math.min(length, 512) / 2) * 2;
        if (sampleLen < 8) {
            return Result.EMPTY;
        }
        int pairs = sampleLen / 2;

        boolean awaitLowBe = false, awaitLowLe = false;
        boolean invalidBe = false, invalidLe = false;

        for (int p = 0; p < pairs; p++) {
            int even = bytes[offset + p * 2] & 0xFF;
            int odd = bytes[offset + p * 2 + 1] & 0xFF;

            if (!invalidBe) {
                if (awaitLowBe) {
                    if (even >= 0xDC && even <= 0xDF) {
                        awaitLowBe = false;
                    } else {
                        invalidBe = true;
                    }
                } else {
                    if (even >= 0xD8 && even <= 0xDB) {
                        awaitLowBe = true;
                    } else if (even >= 0xDC && even <= 0xDF) {
                        invalidBe = true;
                    }
                }
            }

            if (!invalidLe) {
                if (awaitLowLe) {
                    if (odd >= 0xDC && odd <= 0xDF) {
                        awaitLowLe = false;
                    } else {
                        invalidLe = true;
                    }
                } else {
                    if (odd >= 0xD8 && odd <= 0xDB) {
                        awaitLowLe = true;
                    } else if (odd >= 0xDC && odd <= 0xDF) {
                        invalidLe = true;
                    }
                }
            }
        }
        // A high surrogate ending the sample is checked against the next unit; at the
        // probe end the pair was cut, which is no evidence either way.
        int next = offset + sampleLen;
        if (next + 1 < offset + length) {
            int even = bytes[next] & 0xFF;
            int odd = bytes[next + 1] & 0xFF;
            if (awaitLowBe && !(even >= 0xDC && even <= 0xDF)) {
                invalidBe = true;
            }
            if (awaitLowLe && !(odd >= 0xDC && odd <= 0xDF)) {
                invalidLe = true;
            }
        }

        return new Result(null, invalidBe, invalidLe, false, false);
    }

}
