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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

import org.apache.tika.detect.CharsetSupersets;

/**
 * Structural false-CJK veto: measures how badly a probe fails to decode under a
 * legacy multi-byte CJK charset, robustly against embedded UTF-8.
 *
 * <p>A Latin/Cyrillic/garbage page mis-detected as a legacy CJK charset decodes
 * with many malformed/unmappable sequences; real CJK decodes cleanly.  Two
 * corrections make the rate meaningful (see the findings doc):
 * <ol>
 *   <li>decode under the <em>vendor superset</em> ({@link CharsetSupersets}) so
 *       real vendor-extension chars aren't counted as failures;</li>
 *   <li><strong>discount embedded UTF-8</strong> — mixed-encoding pages (legacy
 *       CJK body + UTF-8 widgets) would otherwise inflate the rate.  Post-discount,
 *       real CJK (pure or mixed) is ≤1.6% while genuine false-CJK stays ≥5.3%.</li>
 * </ol>
 *
 * <p>The discount is done by a <em>UTF-8-aware single pass</em>, NOT by physically
 * stripping UTF-8 runs: a real legacy-CJK char can coincidentally match UTF-8
 * grammar (e.g. Shift_JIS kanji with lead 0xE0–0xEA), and physically removing it
 * would misalign the stream and manufacture failures on genuine CJK.  Instead we
 * walk the bytes, skip positions that begin a valid UTF-8 sequence, and decode the
 * legacy charset in place everywhere else — so real CJK is never misaligned and
 * the rate errs toward <em>not</em> vetoing.
 *
 * <p>Does NOT catch the legal-but-wrong class (Latin bytes that form <em>valid</em>
 * CJK at ~0 failure) — that's the typicality layer's job.
 */
public final class CjkDecodeValidator {

    private CjkDecodeValidator() {
    }

    /** Minimum legacy (non-UTF-8) high bytes required before the rate is trusted. */
    public static final int MIN_HIGH_BYTES = 30;

    /**
     * Failure rate of {@code bytes} under {@code cjkCharset}'s vendor superset,
     * counting only legacy high bytes (embedded UTF-8 is skipped, not counted).
     *
     * <p>Special case: if every high byte is a valid UTF-8 sequence (i.e.,
     * {@code nHigh == 0}) and there are at least {@link #MIN_HIGH_BYTES} UTF-8
     * multi-byte sequences, the probe is pure UTF-8 — no legacy CJK content at
     * all.  In that case {@code 1.0} is returned to trigger the CJK veto.
     * Real legacy CJK encodings (Shift_JIS, Big5, EUC-JP, GB18030 …) always
     * have lead bytes in 0x81–0x9F or 0xF5–0xFF that are not valid UTF-8 starts,
     * so {@code nHigh > 0} for any genuine CJK document.
     *
     * @return failures / legacy-high-bytes, {@code 1.0} when the probe is pure
     *         UTF-8 (nHigh==0, nUTF8seqs&ge;{@link #MIN_HIGH_BYTES}), or
     *         {@code -1.0} when there is too little evidence either way
     *         (legacy high bytes &lt; {@link #MIN_HIGH_BYTES} and not pure UTF-8)
     */
    public static double strippedFailureRate(byte[] bytes, Charset cjkCharset) {
        Charset decodeAs = CharsetSupersets.decodeAs(cjkCharset);
        CharsetDecoder dec = decodeAs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer one = CharBuffer.allocate(1);
        int i = 0;
        int n = bytes.length;
        int fail = 0;
        int nHigh = 0;
        int nUtf8Seqs = 0;
        while (i < n) {
            int x = bytes[i] & 0xFF;
            if (x < 0x80) {
                i++;
                continue;
            }
            int ulen = utf8SequenceLength(bytes, i);
            if (ulen > 0) {
                nUtf8Seqs++;
                i += ulen; // embedded UTF-8 — not legacy content, skip
                continue;
            }
            nHigh++;
            dec.reset();
            one.clear();
            ByteBuffer in = ByteBuffer.wrap(bytes, i, Math.min(4, n - i));
            CoderResult r = dec.decode(in, one, true);
            if (r.isError()) {
                fail++;
                i++;
            } else {
                int consumed = in.position() - i;
                i += Math.max(1, consumed);
            }
        }
        if (nHigh < MIN_HIGH_BYTES) {
            // Pure UTF-8: no legacy high bytes at all but enough UTF-8 sequences
            // to be confident.  Return 1.0 so the CJK veto fires.
            if (nHigh == 0 && nUtf8Seqs >= MIN_HIGH_BYTES) {
                return 1.0;
            }
            return -1.0;
        }
        return (double) fail / nHigh;
    }

    /** True for the legacy multi-byte CJK charsets this veto applies to (the
     *  decode-failure signal is meaningful only for these; ISO-2022 is handled
     *  structurally and single-byte charsets don't apply). */
    public static boolean appliesTo(String charsetName) {
        String name = charsetName.toLowerCase(Locale.ROOT);
        if (name.contains("2022")) {
            return false; // escape-based, structural
        }
        return name.contains("gb") || name.contains("big5") || name.contains("euc")
                || name.contains("shift") || name.contains("jis") || name.contains("949");
    }

    /** Length (2/3/4) of a valid UTF-8 multi-byte sequence starting at {@code i},
     *  or 0 if none.  Lead-byte ranges exclude overlong 2-byte (C0/C1) and
     *  out-of-range (≥F5) leads; continuations must be 0x80–0xBF. */
    static int utf8SequenceLength(byte[] b, int i) {
        int x = b[i] & 0xFF;
        int len;
        if (x >= 0xC2 && x <= 0xDF) {
            len = 2;
        } else if (x >= 0xE0 && x <= 0xEF) {
            len = 3;
        } else if (x >= 0xF0 && x <= 0xF4) {
            len = 4;
        } else {
            return 0;
        }
        if (i + len > b.length) {
            return 0;
        }
        for (int k = 1; k < len; k++) {
            int c = b[i + k] & 0xFF;
            if (c < 0x80 || c > 0xBF) {
                return 0;
            }
        }
        return len;
    }
}
