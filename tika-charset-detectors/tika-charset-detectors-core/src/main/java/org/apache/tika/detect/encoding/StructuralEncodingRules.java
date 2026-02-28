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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Fast, rule-based encoding checks that run before the statistical model.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>{@link #checkAscii}: no bytes &gt;= 0x80 → UTF-8 (ASCII is a subset)</li>
 *   <li>{@link #detectIso2022}: ISO-2022 escape sequences present → ISO-2022-JP,
 *       ISO-2022-KR, or ISO-2022-CN depending on the designation sequence</li>
 *   <li>{@link #checkUtf8}: validate UTF-8 multi-byte grammar; returns a
 *       {@link Utf8Result} indicating whether the bytes are definitively UTF-8,
 *       definitively not UTF-8, or ambiguous (pass to model).</li>
 * </ol>
 *
 * <p>UTF-16/32 detection is handled upstream by
 * {@link org.apache.tika.utils.ByteEncodingHint} and is not repeated here.</p>
 *
 * <p>IBM424 (EBCDIC Hebrew) is detected via {@link #checkIbm424}: the Hebrew
 * letters in this code page occupy bytes 0x41–0x6A, which fall entirely below
 * the 0x80 threshold used by the statistical model's feature extractor.  The
 * EBCDIC space (0x40) vs ASCII space (0x20) frequency ratio provides a cheap
 * first-pass EBCDIC gate before the Hebrew letter frequencies are checked.</p>
 *
 * <p>All methods are stateless and safe to call from multiple threads.</p>
 */
public final class StructuralEncodingRules {

    private StructuralEncodingRules() {}

    /** ISO-2022 ESC byte. */
    private static final int ESC = 0x1B;

    /**
     * Ratio of valid high bytes required to call UTF-8 "definitive".
     * If the sample has more than {@value #MIN_HIGH_BYTE_RATIO_FOR_UTF8} of its
     * bytes in multi-byte sequences and they all parse correctly, we trust UTF-8.
     */
    private static final double MIN_HIGH_BYTE_RATIO_FOR_UTF8 = 0.01; // at least 1%

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code bytes} contains no bytes with value
     * &gt;= 0x80 (i.e. pure 7-bit ASCII, which is a strict subset of UTF-8).
     */
    public static boolean checkAscii(byte[] bytes) {
        return checkAscii(bytes, 0, bytes.length);
    }

    public static boolean checkAscii(byte[] bytes, int offset, int length) {
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            if ((bytes[i] & 0xFF) >= 0x80) {
                return false;
            }
        }
        return true;
    }

    /**
     * Detects ISO-2022-JP, ISO-2022-KR, and ISO-2022-CN by scanning for their
     * characteristic ESC designation sequences.
     *
     * <p>All three share the {@code ESC $} ({@code 0x1B 0x24}) prefix, so we
     * must read further to distinguish them:</p>
     * <pre>
     *   ISO-2022-JP:  ESC $ B  (JIS X 0208-1983)
     *                 ESC $ @  (JIS X 0208-1978)
     *                 ESC $ ( D  (JIS X 0212 supplementary)
     *   ISO-2022-KR:  ESC $ ) C
     *   ISO-2022-CN:  ESC $ ) A  (GB2312)
     *                 ESC $ ) G  (CNS 11643 plane 1)
     *                 ESC $ * H  (CNS 11643 plane 2)
     * </pre>
     *
     * <p>If {@code ESC $} is found but no recognised third byte follows (or the
     * buffer is too short), ISO-2022-JP is returned as the most common default.</p>
     *
     * @return the detected ISO-2022 charset, or {@code null} if no ISO-2022
     *         escape sequence is found
     */
    public static Charset detectIso2022(byte[] bytes) {
        return detectIso2022(bytes, 0, bytes.length);
    }

    public static Charset detectIso2022(byte[] bytes, int offset, int length) {
        int end = offset + length;
        for (int i = offset; i < end - 1; i++) {
            if ((bytes[i] & 0xFF) != ESC) {
                continue;
            }
            int b1 = bytes[i + 1] & 0xFF;

            if (b1 == 0x24) { // ESC $
                // Need at least one more byte to classify
                if (i + 2 >= end) {
                    return Charset.forName("ISO-2022-JP"); // ESC $ alone → JP default
                }
                int b2 = bytes[i + 2] & 0xFF;
                switch (b2) {
                    case 0x42: // ESC $ B  — JIS X 0208-1983
                    case 0x40: // ESC $ @  — JIS X 0208-1978
                        return Charset.forName("ISO-2022-JP");
                    case 0x28: // ESC $ (  — JIS X 0212 (JP supplementary)
                        return Charset.forName("ISO-2022-JP");
                    case 0x29: // ESC $ )  — KR or CN depending on b3
                        if (i + 3 >= end) {
                            return Charset.forName("ISO-2022-JP"); // can't tell, JP most common
                        }
                        int b3paren = bytes[i + 3] & 0xFF;
                        if (b3paren == 0x43) return Charset.forName("ISO-2022-KR"); // ESC $ ) C
                        if (b3paren == 0x41 || b3paren == 0x47) return Charset.forName("ISO-2022-CN");
                        return Charset.forName("ISO-2022-JP");
                    case 0x2A: // ESC $ *  — CNS 11643 plane 2
                        return Charset.forName("ISO-2022-CN");
                    default:
                        return Charset.forName("ISO-2022-JP"); // unknown designation → JP default
                }
            }

            if (b1 == 0x28) { // ESC (  — single-byte designation (e.g. ASCII restore)
                // These appear in all ISO-2022 variants and don't help distinguish
                continue;
            }
        }
        return null; // no ISO-2022 escape found
    }

    /**
     * Returns {@code true} if HZ-GB-2312 switching sequences are present.
     *
     * <p>HZ is a 7-bit encoding: it uses {@code ~\{} ({@code 0x7E 0x7B}) to
     * enter two-byte GB2312 mode and {@code ~\}} ({@code 0x7E 0x7D}) to return
     * to ASCII mode. Like ISO-2022, all bytes are below 0x80, so the model
     * would see no features and must be bypassed with this structural check.</p>
     */
    public static boolean checkHz(byte[] bytes) {
        return checkHz(bytes, 0, bytes.length);
    }

    public static boolean checkHz(byte[] bytes, int offset, int length) {
        int end = offset + length - 1;
        for (int i = offset; i < end; i++) {
            if ((bytes[i] & 0xFF) == 0x7E) {
                int next = bytes[i + 1] & 0xFF;
                if (next == 0x7B || next == 0x7D) { // ~{ or ~}
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Detects IBM424 (EBCDIC Hebrew) by examining the sub-0x80 byte landscape.
     *
     * <h3>Why this is needed</h3>
     * <p>In EBCDIC, the space character is {@code 0x40} (not {@code 0x20} as in
     * ASCII).  In IBM424 specifically, the 22 Hebrew base letters plus their five
     * final forms occupy three byte clusters entirely below {@code 0x80}:</p>
     * <pre>
     *   0x41–0x49  alef … tet      (9 letters)
     *   0x51–0x59  yod  … samekh   (9 letters)
     *   0x62–0x6A  ayin … tav      (9 letters + final-pe, tsadi, etc.)
     * </pre>
     * <p>The statistical model ignores all bytes below {@code 0x80}, so these
     * letters are invisible to it.  This structural rule detects them directly.</p>
     *
     * <h3>Algorithm</h3>
     * <ol>
     *   <li><b>EBCDIC gate:</b> byte {@code 0x40} (EBCDIC space) must appear
     *       significantly more often than {@code 0x20} (ASCII space).  In normal
     *       Latin text {@code 0x40} is the rare {@code @} character; in any EBCDIC
     *       text it is the word separator and appears at ~10–20% of bytes.</li>
     *   <li><b>Hebrew letter gate:</b> the combined frequency of bytes in the
     *       three Hebrew clusters above must exceed {@value #IBM424_HEBREW_THRESHOLD}
     *       of the sample length.  Genuine Hebrew text has ~65% of its
     *       printable characters in these ranges.  ASCII text with the same byte
     *       values (upper-case A–I, Q–Y, lower-case b–j) stays well below this
     *       threshold in practice.</li>
     * </ol>
     *
     * @return {@code true} if the byte stream is almost certainly IBM424
     */
    public static boolean checkIbm424(byte[] bytes) {
        return checkIbm424(bytes, 0, bytes.length);
    }

    public static boolean checkIbm424(byte[] bytes, int offset, int length) {
        if (length < 8) {
            return false;
        }
        int sample = Math.min(length, 4096);
        int end = offset + sample;

        int ebcdicSpace = 0; // 0x40 — EBCDIC word separator
        int asciiSpace  = 0; // 0x20 — ASCII word separator
        int hebrewBytes = 0; // 0x41-0x49, 0x51-0x59, 0x62-0x6A

        for (int i = offset; i < end; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0x40) {
                ebcdicSpace++;
            } else if (b == 0x20) {
                asciiSpace++;
            } else if ((b >= 0x41 && b <= 0x49)
                    || (b >= 0x51 && b <= 0x59)
                    || (b >= 0x62 && b <= 0x6A)) {
                hebrewBytes++;
            }
        }

        // Gate 1: 0x40 must dominate over 0x20 (EBCDIC vs ASCII whitespace).
        // We require 0x40 to be at least 3× as frequent as 0x20, and appear
        // at least 3% of the sample (rules out near-empty / binary content).
        boolean ebcdicLikely = ebcdicSpace >= sample * 0.03
                && ebcdicSpace > asciiSpace * 3;

        // Gate 2: Hebrew letter density must exceed the threshold.
        boolean hebrewDense = hebrewBytes > sample * IBM424_HEBREW_THRESHOLD;

        return ebcdicLikely && hebrewDense;
    }

    /**
     * Minimum fraction of bytes in IBM424 Hebrew letter positions (0x41–0x49,
     * 0x51–0x59, 0x62–0x6A) required to confirm IBM424.  Set conservatively to
     * avoid false positives on ASCII text where those same byte values are
     * upper-/lower-case Latin letters.
     */
    private static final double IBM424_HEBREW_THRESHOLD = 0.12;

    /** @deprecated Use {@link #detectIso2022} which distinguishes JP/KR/CN. */
    @Deprecated
    public static boolean checkIso2022Jp(byte[] bytes) {
        return detectIso2022(bytes) != null;
    }

    /**
     * Validates the UTF-8 byte grammar of the sample and returns one of three
     * outcomes:
     * <ul>
     *   <li>{@link Utf8Result#DEFINITIVE_UTF8}: all multi-byte sequences are
     *       valid <em>and</em> the sample contains enough high bytes to be
     *       informative. Use UTF-8.</li>
     *   <li>{@link Utf8Result#NOT_UTF8}: at least one invalid byte sequence was
     *       found. Remove UTF-8 from the candidate set.</li>
     *   <li>{@link Utf8Result#AMBIGUOUS}: the sample is structurally valid UTF-8
     *       but contains very few high bytes (almost pure ASCII), so validity is
     *       uninformative. Pass to the model.</li>
     * </ul>
     */
    public static Utf8Result checkUtf8(byte[] bytes) {
        return checkUtf8(bytes, 0, bytes.length);
    }

    public static Utf8Result checkUtf8(byte[] bytes, int offset, int length) {
        int highByteCount = 0;
        int i = offset;
        int end = offset + length;

        while (i < end) {
            int b = bytes[i] & 0xFF;

            if (b < 0x80) {
                i++;
                continue;
            }

            highByteCount++;

            // Determine expected continuation count from the lead byte
            int seqLen;
            if (b >= 0xF8) {
                // 5-/6-byte sequences are not valid Unicode
                return Utf8Result.NOT_UTF8;
            } else if (b >= 0xF0) {
                seqLen = 4;
            } else if (b >= 0xE0) {
                seqLen = 3;
            } else if (b >= 0xC0) {
                seqLen = 2;
            } else {
                // 0x80–0xBF is a continuation byte without a lead → invalid
                return Utf8Result.NOT_UTF8;
            }

            // Overlong 2-byte sequence (C0 or C1 lead)
            if (seqLen == 2 && b <= 0xC1) {
                return Utf8Result.NOT_UTF8;
            }

            // Check that the right number of continuation bytes follow
            for (int k = 1; k < seqLen; k++) {
                if (i + k >= end) {
                    // Truncated sequence at end of sample — treat as ambiguous
                    // (the sample just ran out, not necessarily bad data)
                    break;
                }
                int cb = bytes[i + k] & 0xFF;
                if (cb < 0x80 || cb > 0xBF) {
                    return Utf8Result.NOT_UTF8;
                }
            }

            // Validate scalar value ranges for 3- and 4-byte sequences
            if (seqLen == 3) {
                int cp = ((b & 0x0F) << 12)
                        | ((i + 1 < end ? bytes[i + 1] & 0xFF : 0) & 0x3F) << 6
                        | ((i + 2 < end ? bytes[i + 2] & 0xFF : 0) & 0x3F);
                // Overlong encoding (< U+0800) or surrogate pair range
                if (cp < 0x0800 || (cp >= 0xD800 && cp <= 0xDFFF)) {
                    return Utf8Result.NOT_UTF8;
                }
            } else if (seqLen == 4) {
                int cp = ((b & 0x07) << 18)
                        | ((i + 1 < end ? bytes[i + 1] & 0xFF : 0) & 0x3F) << 12
                        | ((i + 2 < end ? bytes[i + 2] & 0xFF : 0) & 0x3F) << 6
                        | ((i + 3 < end ? bytes[i + 3] & 0xFF : 0) & 0x3F);
                // Overlong or above U+10FFFF
                if (cp < 0x10000 || cp > 0x10FFFF) {
                    return Utf8Result.NOT_UTF8;
                }
            }

            i += seqLen;
        }

        // Grammar is valid. Was there enough evidence?
        double highRatio = length > 0 ? (double) highByteCount / length : 0.0;
        if (highRatio >= MIN_HIGH_BYTE_RATIO_FOR_UTF8) {
            return Utf8Result.DEFINITIVE_UTF8;
        }
        return Utf8Result.AMBIGUOUS;
    }

    // -----------------------------------------------------------------------
    //  Result type
    // -----------------------------------------------------------------------

    /**
     * Outcome of the UTF-8 structural check.
     */
    public enum Utf8Result {
        /** Sample is structurally valid UTF-8 with enough high bytes to be sure. */
        DEFINITIVE_UTF8,
        /** Sample contains at least one invalid UTF-8 sequence. */
        NOT_UTF8,
        /**
         * Sample is structurally valid but nearly all-ASCII. Cannot confirm or
         * deny; pass to the statistical model.
         */
        AMBIGUOUS;

        public boolean isDefinitive() {
            return this != AMBIGUOUS;
        }

        public Charset toCharset() {
            if (this == DEFINITIVE_UTF8) {
                return StandardCharsets.UTF_8;
            }
            throw new IllegalStateException("Only DEFINITIVE_UTF8 has a Charset: " + this);
        }
    }
}
