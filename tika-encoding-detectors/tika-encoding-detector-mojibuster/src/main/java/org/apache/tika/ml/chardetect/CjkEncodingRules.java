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
import java.util.Set;

/**
 * Grammar walkers for CJK multi-byte encodings, used to refine and validate
 * the statistical model's output.
 *
 * <h3>Approach</h3>
 * <p>The byte-ngram model is reliable at distinguishing CJK from non-CJK content
 * but can confuse related CJK encodings — most critically Shift_JIS vs EUC-JP,
 * where both are Japanese and language-scoring arbitration cannot help.
 * These grammar walkers provide a hard structural check: if the model nominates
 * a CJK encoding, the walker either confirms it (good byte sequences), weakly
 * confirms it (too few multi-byte chars to be sure), or rejects it outright
 * (invalid byte sequences).</p>
 *
 * <h3>Invocation</h3>
 * <p>Walkers are only invoked when the statistical model has already placed a
 * CJK encoding in its output — never unconditionally on every probe.  This
 * avoids false positives on Latin/Arabic/Cyrillic content where some byte
 * values happen to fall in CJK lead-byte ranges.</p>
 *
 * <h3>Confidence scoring</h3>
 * <p>Returns a score on a 0–100 scale (matching ICU4J's convention) so the
 * calling code can decide uniformly:</p>
 * <ul>
 *   <li>0 — invalid byte sequences detected; reject this encoding</li>
 *   <li>10 — valid grammar but very few multi-byte chars; too little evidence
 *       to prefer the grammar score over the model's</li>
 *   <li>11–100 — enough valid multi-byte chars to trust the grammar score</li>
 * </ul>
 *
 * <h3>Common character tables</h3>
 * <p>ICU4J uses hardcoded frequency tables (sorted int[] of common two-byte
 * values) to boost confidence on short probes.  We omit these for now and
 * derive them from the MadLAD corpus if short-probe confidence proves
 * insufficient in practice.  See charset-detection-design.adoc.</p>
 *
 * <p>The grammar-walking approach and confidence formula are inspired by
 * ICU4J's {@code CharsetRecog_mbcs} class (unicode-org/icu).</p>
 */
public final class CjkEncodingRules {

    /**
     * Canonical Java charset names that this class handles.
     * Used by {@link #isCjk(Charset)} to gate walker invocation.
     */
    private static final Set<String> CJK_NAMES = Set.of(
            "Shift_JIS", "windows-31j",
            "EUC-JP",
            "EUC-KR",
            "x-EUC-TW",
            "Big5", "Big5-HKSCS",
            "GB18030", "GBK", "GB2312"
    );

    private CjkEncodingRules() {}

    /**
     * Returns {@code true} if {@code cs} is one of the CJK encodings handled
     * by this class.
     */
    public static boolean isCjk(Charset cs) {
        return CJK_NAMES.contains(cs.name());
    }

    /**
     * Run the grammar walker for {@code cs} against {@code probe} and return
     * a confidence score on the 0–100 scale.
     *
     * <p>Returns {@code -1} if {@code cs} is not a CJK encoding handled here
     * (callers should check {@link #isCjk(Charset)} first).</p>
     */
    public static int match(byte[] probe, Charset cs) {
        switch (cs.name()) {
            case "Shift_JIS":
            case "windows-31j":
                return matchSjis(probe);
            case "EUC-JP":
                return matchEucJp(probe);
            case "EUC-KR":
                return matchEucKr(probe);
            case "x-EUC-TW":
                return matchEucTw(probe);
            case "Big5":
            case "Big5-HKSCS":
                return matchBig5(probe);
            case "GB18030":
            case "GBK":
            case "GB2312":
                return matchGb18030(probe);
            default:
                return -1;
        }
    }

    // -----------------------------------------------------------------------
    // Shift_JIS / windows-31j (CP932)
    // -----------------------------------------------------------------------

    /**
     * Grammar walker for Shift_JIS / CP932.
     *
     * <p>Byte categories:</p>
     * <ul>
     *   <li>{@code 0x00–0x7F}: single-byte ASCII</li>
     *   <li>{@code 0xA1–0xDF}: single-byte half-width katakana</li>
     *   <li>{@code 0x81–0x9F}, {@code 0xE0–0xFC}: lead byte of a double-byte
     *       character; trail byte must be {@code 0x40–0x7F} or
     *       {@code 0x80–0xFF}</li>
     *   <li>All other values ({@code 0x80}, {@code 0xA0}, {@code 0xFD–0xFF}):
     *       invalid</li>
     * </ul>
     */
    private static int matchSjis(byte[] probe) {
        int doubleByte = 0;
        int bad = 0;
        int i = 0;
        while (i < probe.length) {
            int b = probe[i++] & 0xFF;

            if (b <= 0x7F || (b >= 0xA1 && b <= 0xDF)) {
                continue; // single byte
            }

            if ((b >= 0x81 && b <= 0x9F) || (b >= 0xE0 && b <= 0xFC)) {
                if (i >= probe.length) break;
                int trail = probe[i++] & 0xFF;
                if ((trail >= 0x40 && trail <= 0x7F) || (trail >= 0x80 && trail <= 0xFF)) {
                    doubleByte++;
                } else {
                    bad++;
                }
            } else {
                bad++; // 0x80, 0xA0, 0xFD–0xFF
            }

            if (bail(bad, doubleByte)) break;
        }
        return confidence(doubleByte, bad);
    }

    // -----------------------------------------------------------------------
    // EUC-JP
    // -----------------------------------------------------------------------

    /**
     * Grammar walker for EUC-JP.
     *
     * <p>Byte categories:</p>
     * <ul>
     *   <li>{@code 0x00–0x8D}: single byte</li>
     *   <li>{@code 0xA1–0xFE}: lead of a two-byte char; trail ≥ {@code 0xA1}</li>
     *   <li>{@code 0x8E}: SS2 (half-width katakana); second byte ≥ {@code 0xA1}</li>
     *   <li>{@code 0x8F}: SS3; second and third bytes both ≥ {@code 0xA1}</li>
     *   <li>{@code 0x8E–0x90}, {@code 0xFF}: invalid</li>
     * </ul>
     */
    private static int matchEucJp(byte[] probe) {
        return matchEuc(probe);
    }

    // -----------------------------------------------------------------------
    // EUC-KR
    // -----------------------------------------------------------------------

    /**
     * Grammar walker for EUC-KR.
     * EUC-KR uses the same two-byte structure as EUC-JP but without the SS2/SS3
     * extensions; the shared EUC walker handles both correctly because SS2/SS3
     * sequences that validate grammatically are simply counted as double-byte
     * characters.
     */
    private static int matchEucKr(byte[] probe) {
        return matchEuc(probe);
    }

    /**
     * Shared EUC grammar walker (covers EUC-JP and EUC-KR).
     */
    private static int matchEuc(byte[] probe) {
        int doubleByte = 0;
        int bad = 0;
        int i = 0;
        while (i < probe.length) {
            int b = probe[i++] & 0xFF;

            if (b <= 0x8D) {
                continue; // single byte
            }

            if (b >= 0xA1 && b <= 0xFE) {
                // two-byte char
                if (i >= probe.length) break;
                int trail = probe[i++] & 0xFF;
                if (trail >= 0xA1) {
                    doubleByte++;
                } else {
                    bad++;
                }
            } else if (b == 0x8E) {
                // SS2: two bytes total
                if (i >= probe.length) break;
                int trail = probe[i++] & 0xFF;
                if (trail >= 0xA1) {
                    doubleByte++;
                } else {
                    bad++;
                }
            } else if (b == 0x8F) {
                // SS3: three bytes total
                if (i + 1 >= probe.length) break;
                int b2 = probe[i++] & 0xFF;
                int b3 = probe[i++] & 0xFF;
                if (b2 >= 0xA1 && b3 >= 0xA1) {
                    doubleByte++;
                } else {
                    bad++;
                }
            } else {
                bad++; // 0x8E–0x9F or 0xFF
            }

            if (bail(bad, doubleByte)) break;
        }
        return confidence(doubleByte, bad);
    }

    // -----------------------------------------------------------------------
    // EUC-TW (CNS 11643)
    // -----------------------------------------------------------------------

    /**
     * Grammar walker for EUC-TW (CNS 11643).
     *
     * <p>Byte categories:</p>
     * <ul>
     *   <li>{@code 0x00–0x7F}: single byte (ASCII)</li>
     *   <li>{@code 0xA1–0xFE}: lead of a two-byte char; trail {@code 0xA1–0xFE}</li>
     *   <li>{@code 0x8E}: SS2 prefix for CNS planes 2–16; followed by a plane
     *       designator ({@code 0xA1–0xB0}), then two bytes in
     *       {@code 0xA1–0xFE} (4 bytes total)</li>
     *   <li>{@code 0x80–0x8D}, {@code 0x8F–0xA0}, {@code 0xFF}: invalid</li>
     * </ul>
     */
    private static int matchEucTw(byte[] probe) {
        int doubleByte = 0;
        int bad = 0;
        int i = 0;
        while (i < probe.length) {
            int b = probe[i++] & 0xFF;

            if (b <= 0x7F) {
                continue;
            }

            if (b >= 0xA1 && b <= 0xFE) {
                if (i >= probe.length) break;
                int trail = probe[i++] & 0xFF;
                if (trail >= 0xA1 && trail <= 0xFE) {
                    doubleByte++;
                } else {
                    bad++;
                }
            } else if (b == 0x8E) {
                // SS2: plane designator + two data bytes (4 bytes total)
                if (i + 2 >= probe.length) break;
                int plane = probe[i++] & 0xFF;
                int b3 = probe[i++] & 0xFF;
                int b4 = probe[i++] & 0xFF;
                if (plane >= 0xA1 && plane <= 0xB0
                        && b3 >= 0xA1 && b3 <= 0xFE
                        && b4 >= 0xA1 && b4 <= 0xFE) {
                    doubleByte++;
                } else {
                    bad++;
                }
            } else {
                bad++; // 0x80–0x8D, 0x8F–0xA0, 0xFF
            }

            if (bail(bad, doubleByte)) break;
        }
        return confidence(doubleByte, bad);
    }

    // -----------------------------------------------------------------------
    // Big5 / Big5-HKSCS
    // -----------------------------------------------------------------------

    /**
     * Grammar walker for Big5 / Big5-HKSCS.
     *
     * <p>Byte categories:</p>
     * <ul>
     *   <li>{@code 0x00–0x7F}, {@code 0xFF}: single byte</li>
     *   <li>{@code 0x81–0xFE}: lead byte; trail must not be
     *       {@code < 0x40}, {@code 0x7F}, or {@code 0xFF}</li>
     * </ul>
     */
    private static int matchBig5(byte[] probe) {
        int doubleByte = 0;
        int bad = 0;
        int i = 0;
        while (i < probe.length) {
            int b = probe[i++] & 0xFF;

            if (b <= 0x7F || b == 0xFF) {
                continue; // single byte
            }

            if (b >= 0x81 && b <= 0xFE) {
                if (i >= probe.length) break;
                int trail = probe[i++] & 0xFF;
                if (trail < 0x40 || trail == 0x7F || trail == 0xFF) {
                    bad++;
                } else {
                    doubleByte++;
                }
            } else {
                bad++; // 0x80
            }

            if (bail(bad, doubleByte)) break;
        }
        return confidence(doubleByte, bad);
    }

    // -----------------------------------------------------------------------
    // GB18030 / GBK / GB2312
    // -----------------------------------------------------------------------

    /**
     * Grammar walker for GB18030 (superset of GBK and GB2312).
     *
     * <p>Byte categories:</p>
     * <ul>
     *   <li>{@code 0x00–0x80}: single byte</li>
     *   <li>{@code 0x81–0xFE}: lead byte of either a two-byte sequence
     *       (trail {@code 0x40–0x7E} or {@code 0x80–0xFE}) or a four-byte
     *       sequence (second {@code 0x30–0x39}, third {@code 0x81–0xFE},
     *       fourth {@code 0x30–0x39})</li>
     * </ul>
     */
    private static int matchGb18030(byte[] probe) {
        int doubleByte = 0;
        int bad = 0;
        int i = 0;
        while (i < probe.length) {
            int b = probe[i++] & 0xFF;

            if (b <= 0x80) {
                continue; // single byte
            }

            if (b >= 0x81 && b <= 0xFE) {
                if (i >= probe.length) break;
                int b2 = probe[i] & 0xFF;

                if ((b2 >= 0x40 && b2 <= 0x7E) || (b2 >= 0x80 && b2 <= 0xFE)) {
                    // two-byte char
                    i++;
                    doubleByte++;
                } else if (b2 >= 0x30 && b2 <= 0x39) {
                    // four-byte char
                    i++;
                    if (i + 1 >= probe.length) break;
                    int b3 = probe[i++] & 0xFF;
                    int b4 = probe[i++] & 0xFF;
                    if (b3 >= 0x81 && b3 <= 0xFE && b4 >= 0x30 && b4 <= 0x39) {
                        doubleByte++;
                    } else {
                        bad++;
                    }
                } else {
                    bad++;
                }
            } else {
                bad++;
            }

            if (bail(bad, doubleByte)) break;
        }
        return confidence(doubleByte, bad);
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    /**
     * Early-exit condition: bail when there are at least 2 bad characters
     * and bad characters make up more than 1/5 of double-byte characters.
     * Mirrors ICU4J's {@code CharsetRecog_mbcs.match()} bail condition.
     */
    private static boolean bail(int bad, int doubleByte) {
        return bad >= 2 && bad * 5 >= doubleByte;
    }

    /**
     * Minimum confidence returned when all high bytes form valid CJK sequences
     * (bad == 0) but the sample is short (doubleByte ≤ 10). A score at or above
     * this value means "structurally clean" and is used by
     * {@link MojibusterEncodingDetector#refineCjkResults} to prefer the CJK
     * charset over single-byte alternatives on short probes.
     *
     * <p>Single-byte encodings have no structural constraints — they decode any
     * byte sequence without errors. A CJK multi-byte parser that consumes all
     * high bytes into valid 2-byte sequences IS providing structural evidence
     * even when the sample contains only 1–2 double-byte characters.</p>
     */
    public static final int CLEAN_SHORT_PROBE_CONFIDENCE = 40;

    /**
     * Confidence formula, inspired by ICU4J's {@code CharsetRecog_mbcs.match()}.
     *
     * <ul>
     *   <li>0 double-byte chars → 0 (no CJK evidence at all)</li>
     *   <li>bad == 0: all high bytes are valid CJK sequences →
     *       {@code min(100, 30 + doubleByte * 10)}.
     *       Gives 40 for 1 double-byte char, 50 for 2, 100 for 7+.
     *       These scores are ≥ {@link #CLEAN_SHORT_PROBE_CONFIDENCE}, signalling
     *       "structurally clean" to the caller even on very short probes.</li>
     *   <li>Too many bad chars relative to good → 0 (wrong encoding)</li>
     *   <li>bad > 0 within tolerance → {@code 30 + doubleByte - 20 * bad},
     *       capped at 100</li>
     * </ul>
     */
    static int confidence(int doubleByte, int bad) {
        if (doubleByte == 0) {
            return 0;
        }
        if (bad == 0) {
            // All high bytes consumed as valid sequences — structurally clean.
            // Scale with sample size but never return the old floor of 10.
            return Math.min(100, 30 + doubleByte * 10);
        }
        if (doubleByte < 20 * bad) {
            return 0;
        }
        return Math.min(100, 30 + doubleByte - 20 * bad);
    }
}
