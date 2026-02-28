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
package org.apache.tika.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A lightweight pre-detection hint about the byte-level encoding of a raw byte
 * buffer, derived purely from structural null-byte patterns — no BOM reliance.
 *
 * <h3>Purpose</h3>
 * <p>Answers one question before charset detection runs: is this UTF-16 LE,
 * UTF-16 BE, or something ASCII-compatible?  If UTF-16 is detected the encoding
 * is already known and the charset model does not need to run.  If the bytes
 * fall through to the ISO-8859-1 fallback, they are ASCII-compatible and safe
 * to pass directly to the byte-frequency charset model.</p>
 *
 * <h3>Intended pipeline</h3>
 * <pre>
 *   ByteEncodingHint hint = ByteEncodingHint.detect(rawBytes);
 *   if (hint.isUtf16()) {
 *       // encoding already known — record hint.charset() and stop
 *   } else {
 *       // ASCII-compatible — feed rawBytes to the charset model as-is
 *   }
 * </pre>
 *
 * <h3>Detection logic</h3>
 * <p>UTF-16 LE/BE are detected by counting null bytes at even and odd byte
 * positions across the first 512 bytes.  In UTF-16, ASCII characters produce
 * a strong one-sided null pattern (LE: nulls at odd positions; BE: nulls at
 * even positions).  UTF-32 is distinguished from UTF-16 because it produces
 * high null rates in <em>both</em> columns and is therefore not mistaken for
 * UTF-16.  Everything else — including UTF-8, all single-byte encodings, EBCDIC,
 * and multi-byte Asian encodings — falls back to ISO-8859-1.</p>
 *
 * <h3>What this class does NOT do</h3>
 * <ul>
 *   <li><strong>No BOM parsing.</strong>  BOMs can be missing, wrong, or
 *       accidentally prepended; they are a separate signal for the caller to
 *       layer in.</li>
 *   <li><strong>No charset model.</strong>  Distinguishing UTF-8 from Big5
 *       from Shift-JIS from KOI8-R is left to the byte-frequency model.  This
 *       class only filters out the UTF-16 case where the model is unnecessary
 *       and would be fed misleading input.</li>
 *   <li><strong>No EBCDIC detection.</strong>  EBCDIC has no null bytes in
 *       normal text; it falls through to ISO-8859-1, and a dedicated EBCDIC
 *       detector must run separately.</li>
 * </ul>
 */
public final class ByteEncodingHint {

    /** Bytes sampled for the null-byte heuristic. */
    private static final int SAMPLE_LEN = 512;

    /**
     * Threshold for the null-byte heuristic: more than this fraction of pairs
     * must have a null in the expected column for UTF-16 to be inferred.
     * 10% handles heavily CJK UTF-16 documents (where most codepoints use both
     * bytes) because HTML markup alone crosses the threshold comfortably.
     */
    private static final int NULL_THRESHOLD_DENOM = 10;

    private final Charset charset;

    private ByteEncodingHint(Charset charset) {
        this.charset = charset;
    }

    /**
     * The inferred charset.  One of {@link StandardCharsets#UTF_16LE},
     * {@link StandardCharsets#UTF_16BE}, or {@link StandardCharsets#ISO_8859_1}
     * (meaning "ASCII-compatible, run the charset model").
     */
    public Charset charset() {
        return charset;
    }

    /**
     * Returns {@code true} if the bytes were identified as UTF-16 LE or BE,
     * meaning the encoding is already known and the charset model should not run.
     */
    public boolean isUtf16() {
        return charset == StandardCharsets.UTF_16LE
                || charset == StandardCharsets.UTF_16BE;
    }

    /**
     * Analyzes up to the first 512 bytes of {@code bytes} and returns an
     * encoding hint.
     *
     * @param bytes raw content bytes; may be null or empty
     * @return a hint; never null
     */
    public static ByteEncodingHint detect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new ByteEncodingHint(StandardCharsets.ISO_8859_1);
        }

        int sampleLen = Math.min(bytes.length, SAMPLE_LEN);
        int pairs = sampleLen / 2;
        if (pairs == 0) {
            return new ByteEncodingHint(StandardCharsets.ISO_8859_1);
        }

        int nullsAtEven = 0;
        int nullsAtOdd  = 0;
        for (int k = 0; k + 1 < sampleLen; k += 2) {
            if (bytes[k]     == 0) nullsAtEven++;
            if (bytes[k + 1] == 0) nullsAtOdd++;
        }

        boolean highEven = nullsAtEven * NULL_THRESHOLD_DENOM > pairs;
        boolean highOdd  = nullsAtOdd  * NULL_THRESHOLD_DENOM > pairs;

        // Exactly one column high → UTF-16.
        // Both high → UTF-32 (falls through — we don't handle it).
        // Both low → single-byte, multi-byte Asian, or EBCDIC.
        if (highOdd  && !highEven) return new ByteEncodingHint(StandardCharsets.UTF_16LE);
        if (highEven && !highOdd)  return new ByteEncodingHint(StandardCharsets.UTF_16BE);
        return new ByteEncodingHint(StandardCharsets.ISO_8859_1);
    }
}
