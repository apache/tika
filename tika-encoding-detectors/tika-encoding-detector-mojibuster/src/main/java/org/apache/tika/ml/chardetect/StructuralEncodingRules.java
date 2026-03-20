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
        int highBytes   = 0; // bytes >= 0x80

        int prev = -1;
        for (int i = offset; i < end; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0x40) {
                // In Shift_JIS, 0x40 appears only as a trail byte after a lead byte
                // (0x81–0x9F or 0xE0–0xFC). Discount it as EBCDIC space in that case.
                boolean isShiftJisTrail = (prev >= 0x81 && prev <= 0x9F)
                        || (prev >= 0xE0 && prev <= 0xFC);
                if (!isShiftJisTrail) {
                    ebcdicSpace++;
                }
            } else if (b == 0x20) {
                asciiSpace++;
            } else if ((b >= 0x41 && b <= 0x49)
                    || (b >= 0x51 && b <= 0x59)
                    || (b >= 0x62 && b <= 0x6A)) {
                hebrewBytes++;
            }
            if (b >= 0x80) {
                highBytes++;
            }
            prev = b;
        }

        // Gate 1: 0x40 must dominate over 0x20 (EBCDIC vs ASCII whitespace).
        // We require 0x40 to be at least 3× as frequent as 0x20, and appear
        // at least 3% of the sample (rules out near-empty / binary content).
        boolean ebcdicLikely = ebcdicSpace >= sample * 0.03
                && ebcdicSpace > asciiSpace * 3;

        // Gate 2: Hebrew letter density must exceed the threshold.
        boolean hebrewDense = hebrewBytes > sample * IBM424_HEBREW_THRESHOLD;

        // Gate 3: IBM424 Hebrew has ALL letters below 0x80; IBM420 Arabic EBCDIC
        // maps many Arabic letters to bytes >= 0x80 (e.g. ل=0xB1, م=0xBB, ن=0xBD).
        // If the high-byte ratio is substantial, this is IBM420, not IBM424.
        boolean lowByteCharset = highBytes < sample * IBM424_MAX_HIGH_BYTE_RATIO;

        return ebcdicLikely && hebrewDense && lowByteCharset;
    }

    /**
     * Minimum fraction of bytes in IBM424 Hebrew letter positions (0x41–0x49,
     * 0x51–0x59, 0x62–0x6A) required to confirm IBM424.  Set conservatively to
     * avoid false positives on ASCII text where those same byte values are
     * upper-/lower-case Latin letters.
     */
    private static final double IBM424_HEBREW_THRESHOLD = 0.12;

    /**
     * IBM424 (EBCDIC Hebrew) has all letter bytes below 0x80.  IBM420 (EBCDIC
     * Arabic) maps many Arabic letters to bytes >= 0x80 (lam=0xB1, meem=0xBB,
     * etc.), giving it a high-byte ratio of ~40-50%.  Reject the IBM424 gate if
     * the high-byte ratio exceeds this threshold — the probe is IBM420 or another
     * high-byte EBCDIC variant, not Hebrew.
     */
    private static final double IBM424_MAX_HIGH_BYTE_RATIO = 0.15;

    /**
     * Detects IBM500 (International EBCDIC / EBCDIC-500) by looking for the
     * combination of the EBCDIC space byte and high-byte Latin letter density.
     *
     * <h3>Why this is needed</h3>
     * <p>In IBM500 every Latin letter is encoded as a byte &ge; 0x80:</p>
     * <pre>
     *   0x81–0x89  a–i    (lowercase)
     *   0x91–0x99  j–r    (lowercase)
     *   0xA2–0xA9  s–z    (lowercase)
     *   0xC1–0xC9  A–I    (uppercase)
     *   0xD1–0xD9  J–R    (uppercase)
     *   0xE2–0xE9  S–Z    (uppercase)
     * </pre>
     * <p>At full probe length the statistical model distinguishes IBM500 from
     * IBM424 without difficulty.  At very short probes (≤ 20 bytes) the model
     * sees too few bytes to be confident and tends to confuse the two EBCDIC
     * code pages.  This structural gate fires early — before the model — using
     * the cheap EBCDIC-space dominance check followed by a Latin-letter density
     * check.</p>
     *
     * <h3>Algorithm</h3>
     * <ol>
     *   <li><b>EBCDIC gate:</b> same as {@link #checkIbm424} — byte {@code 0x40}
     *       must dominate over {@code 0x20}.  This distinguishes any EBCDIC
     *       encoding from ASCII/UTF-8/Latin-1 where {@code 0x40} is the rare
     *       {@code @} character.</li>
     *   <li><b>Latin letter density:</b> the combined frequency of bytes in the
     *       six IBM500 Latin-letter clusters above must exceed
     *       {@value #IBM500_LATIN_THRESHOLD} of the sample.  Normal Latin text
     *       has ~60–70% letter bytes; the threshold is intentionally conservative
     *       to fire reliably at 20 bytes.</li>
     * </ol>
     *
     * <p>{@link #checkIbm424} should be called first.  If it fires the probe is
     * IBM424 (Hebrew EBCDIC); only if it does not fire should this method be
     * consulted for IBM500.</p>
     *
     * @return {@code true} if the byte stream is almost certainly IBM500
     */
    public static boolean checkIbm500(byte[] bytes) {
        return checkIbm500(bytes, 0, bytes.length);
    }

    public static boolean checkIbm500(byte[] bytes, int offset, int length) {
        if (length < 8) {
            return false;
        }
        int sample = Math.min(length, 4096);
        int end = offset + sample;

        int ebcdicSpace = 0; // 0x40 — EBCDIC word separator
        int asciiSpace  = 0; // 0x20 — ASCII word separator
        int latinBytes  = 0; // bytes in the six IBM500 Latin-letter clusters

        int prev = -1;
        for (int i = offset; i < end; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0x40) {
                // Exclude Shift_JIS trail bytes (same guard as checkIbm424)
                boolean isShiftJisTrail = (prev >= 0x81 && prev <= 0x9F)
                        || (prev >= 0xE0 && prev <= 0xFC);
                if (!isShiftJisTrail) {
                    ebcdicSpace++;
                }
            } else if (b == 0x20) {
                asciiSpace++;
            } else if ((b >= 0x81 && b <= 0x89)   // a–i
                    || (b >= 0x91 && b <= 0x99)    // j–r
                    || (b >= 0xA2 && b <= 0xA9)    // s–z
                    || (b >= 0xC1 && b <= 0xC9)    // A–I
                    || (b >= 0xD1 && b <= 0xD9)    // J–R
                    || (b >= 0xE2 && b <= 0xE9)) { // S–Z
                latinBytes++;
            }
            prev = b;
        }

        // Gate 1: EBCDIC space must dominate over ASCII space.
        boolean ebcdicLikely = ebcdicSpace >= sample * 0.03
                && ebcdicSpace > asciiSpace * 3;

        // Gate 2: Latin letter density must exceed the threshold.
        boolean latinDense = latinBytes > sample * IBM500_LATIN_THRESHOLD;

        return ebcdicLikely && latinDense;
    }

    /**
     * Minimum fraction of bytes in IBM500 Latin letter positions required to
     * confirm IBM500.  Latin text has ~60–70% letter bytes; 0.25 is conservative
     * enough to fire at 20 bytes while avoiding false positives on binary data.
     */
    private static final double IBM500_LATIN_THRESHOLD = 0.25;

    /**
     * Returns {@code true} if the probe contains at least one CRLF pair
     * ({@code 0x0D 0x0A}).
     *
     * <p>Files originating on Windows use CRLF as the line separator.
     * The presence of a {@code 0x0D 0x0A} pair in a probe that is otherwise
     * 7-bit ASCII is weak evidence that the file was created on Windows and
     * therefore more likely to use a Windows code page (e.g. windows-1252)
     * than a Unix-origin ISO-8859-X encoding for any high-byte content
     * beyond the probe window.</p>
     *
     * <p>A bare {@code 0x0D} without a following {@code 0x0A} is <em>not</em>
     * counted: classic Mac OS used bare CR as its line ending, and that is a
     * different case that does not imply Windows origin.</p>
     */
    public static boolean hasCrlfBytes(byte[] bytes) {
        return hasCrlfBytes(bytes, 0, bytes.length);
    }

    public static boolean hasCrlfBytes(byte[] bytes, int offset, int length) {
        int end = offset + length;
        for (int i = offset; i < end - 1; i++) {
            if (bytes[i] == 0x0D && bytes[i + 1] == 0x0A) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the probe contains any byte in the C1 control
     * range {@code 0x80–0x9F}.
     *
     * <p>In every ISO-8859-X encoding those byte values are C1 control
     * characters that never appear in real text. In every Windows-12XX
     * encoding they are printable characters (smart quotes, Euro sign,
     * em-dash, …). Their presence is therefore definitive proof that the
     * content is <em>not</em> a valid ISO-8859-X encoding and should be
     * attributed to the corresponding Windows-12XX variant instead.</p>
     */
    public static boolean hasC1Bytes(byte[] bytes) {
        return hasC1Bytes(bytes, 0, bytes.length);
    }

    public static boolean hasC1Bytes(byte[] bytes, int offset, int length) {
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            int v = bytes[i] & 0xFF;
            if (v >= 0x80 && v <= 0x9F) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the probe contains at least one GB18030-specific
     * 4-byte sequence.
     *
     * <h3>GB18030 4-byte structure</h3>
     * <pre>
     *   Byte 1 (lead):   0x81–0xFE
     *   Byte 2 (second): 0x30–0x39  ← ASCII digits
     *   Byte 3 (third):  0x81–0xFE
     *   Byte 4 (trail):  0x30–0x39  ← ASCII digits
     * </pre>
     *
     * <p>In GBK and GB2312 all trail bytes are in {@code 0x40–0xFE}, so a digit
     * ({@code 0x30–0x39}) in the second or fourth position is impossible. A single
     * matching 4-tuple is therefore definitive proof that the content was encoded
     * with GB18030 and must be decoded with a GB18030-capable codec to avoid
     * replacement characters for the affected code points.</p>
     */
    public static boolean hasGb18030FourByteSequence(byte[] bytes) {
        return hasGb18030FourByteSequence(bytes, 0, bytes.length);
    }

    public static boolean hasGb18030FourByteSequence(byte[] bytes, int offset, int length) {
        int end = offset + length - 3;
        for (int i = offset; i < end; i++) {
            int b0 = bytes[i] & 0xFF;
            if (b0 < 0x81 || b0 > 0xFE) {
                continue;
            }
            int b1 = bytes[i + 1] & 0xFF;
            if (b1 < 0x30 || b1 > 0x39) {
                continue;
            }
            int b2 = bytes[i + 2] & 0xFF;
            if (b2 < 0x81 || b2 > 0xFE) {
                continue;
            }
            int b3 = bytes[i + 3] & 0xFF;
            if (b3 < 0x30 || b3 > 0x39) {
                continue;
            }
            return true;
        }
        return false;
    }

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
            boolean truncated = false;
            for (int k = 1; k < seqLen; k++) {
                if (i + k >= end) {
                    truncated = true;
                    break;
                }
                int cb = bytes[i + k] & 0xFF;
                if (cb < 0x80 || cb > 0xBF) {
                    return Utf8Result.NOT_UTF8;
                }
            }

            // Validate scalar value ranges for 3- and 4-byte sequences,
            // but only when the full sequence is present. Truncated sequences
            // at the end of a probe are not evidence of invalid UTF-8.
            if (!truncated) {
                if (seqLen == 3) {
                    int cp = ((b & 0x0F) << 12)
                            | ((bytes[i + 1] & 0xFF) & 0x3F) << 6
                            | ((bytes[i + 2] & 0xFF) & 0x3F);
                    if (cp < 0x0800 || (cp >= 0xD800 && cp <= 0xDFFF)) {
                        return Utf8Result.NOT_UTF8;
                    }
                } else if (seqLen == 4) {
                    int cp = ((b & 0x07) << 18)
                            | ((bytes[i + 1] & 0xFF) & 0x3F) << 12
                            | ((bytes[i + 2] & 0xFF) & 0x3F) << 6
                            | ((bytes[i + 3] & 0xFF) & 0x3F);
                    if (cp < 0x10000 || cp > 0x10FFFF) {
                        return Utf8Result.NOT_UTF8;
                    }
                }
            }

            i += seqLen;
        }

        // Grammar is valid. Even a single valid multi-byte sequence is
        // structural proof of UTF-8 — no single-byte encoding produces valid
        // lead+continuation patterns by coincidence at any meaningful rate.
        if (highByteCount > 0) {
            return Utf8Result.DEFINITIVE_UTF8;
        }
        // Zero high bytes = pure ASCII; caller handles this separately.
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
