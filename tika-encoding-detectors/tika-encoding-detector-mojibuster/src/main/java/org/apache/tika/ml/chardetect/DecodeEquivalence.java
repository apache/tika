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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cheap byte-wise decode-equivalence check for single-byte charsets.
 *
 * <p>For single-byte codepages, the mapping from byte value (0x00..0xFF) to
 * Unicode codepoint is a fixed table.  Two charsets decode a probe
 * byte-for-byte identically iff their byte-to-char tables agree on every
 * byte value that appears in the probe.  ASCII bytes (below {@code 0x80})
 * map identically in every Latin-family codepage and are skipped; the check
 * reduces to "do these charsets agree on every high byte present in this
 * probe?"</p>
 *
 * <p>Cost: {@code O(probe.length)} per call in the worst case, typically
 * short-circuits on the first disagreement.  Byte-to-char tables are
 * computed lazily on first use and cached for process lifetime.</p>
 *
 * <p>This is the inference-time counterpart to the broader
 * {@link CharsetConfusables#POTENTIAL_DECODE_EQUIV_FAMILIES} declaration —
 * families enumerate which pairs are <em>potentially</em> byte-identical;
 * this class decides whether they are <em>actually</em> byte-identical on a
 * specific probe.</p>
 */
public final class DecodeEquivalence {

    /** Per-charset byte-to-char tables, lazily populated. */
    private static final Map<String, char[]> TABLE_CACHE = new ConcurrentHashMap<>();

    private DecodeEquivalence() {
    }

    /**
     * Returns {@code true} if decoding {@code probe} under charsets {@code a}
     * and {@code b} produces bit-identical character sequences.  Only the
     * high-byte positions (bytes {@code >= 0x80}) are compared; all Latin-family
     * charsets agree on ASCII.
     *
     * <p>Returns {@code false} (and caches nothing) if either charset's byte
     * table cannot be resolved (e.g. stateful, multi-byte, or JVM-unsupported).
     * Callers should restrict invocation to single-byte charsets, typically
     * via {@link CharsetConfusables#potentialDecodeEquivPeersOf(String)}.</p>
     */
    public static boolean byteIdenticalOnProbe(byte[] probe, Charset a, Charset b) {
        if (a.equals(b)) {
            return true;
        }
        char[] tableA = tableFor(a);
        char[] tableB = tableFor(b);
        if (tableA == null || tableB == null) {
            return false;
        }
        for (int i = 0; i < probe.length; i++) {
            int v = probe[i] & 0xFF;
            if (v < 0x80) {
                continue;  // ASCII agrees in every Latin-family SBCS
            }
            if (tableA[v] != tableB[v]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a 256-element byte-to-char table for a single-byte charset, or
     * {@code null} if the charset is not single-byte or is unresolvable on
     * this JVM.  The table is cached across calls.
     *
     * <p>"Single-byte" is verified by decoding all 256 possible byte values
     * and requiring exactly one char of output per input byte (or the
     * replacement char on unmapped positions — still one char).  Multi-byte
     * charsets (Shift_JIS, UTF-8, …) produce variable-length output and are
     * excluded.</p>
     */
    static char[] tableFor(Charset cs) {
        char[] cached = TABLE_CACHE.get(cs.name());
        if (cached != null) {
            return cached;
        }
        char[] built = buildTable(cs);
        if (built != null) {
            TABLE_CACHE.put(cs.name(), built);
        }
        return built;
    }

    private static char[] buildTable(Charset cs) {
        try {
            CharsetDecoder dec = cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .replaceWith("\uFFFD");
            char[] table = new char[256];
            byte[] one = new byte[1];
            for (int v = 0; v < 256; v++) {
                one[0] = (byte) v;
                CharBuffer out = CharBuffer.allocate(4);
                ByteBuffer in = ByteBuffer.wrap(one);
                dec.reset();
                CoderResult cr = dec.decode(in, out, true);
                if (cr.isError()) {
                    return null;
                }
                dec.decode(ByteBuffer.allocate(0), out, true);
                dec.flush(out);
                out.flip();
                if (out.remaining() != 1) {
                    // Multi-byte / stateful charset — not a single-byte table.
                    return null;
                }
                table[v] = out.get();
            }
            return table;
        } catch (Exception e) {
            return null;
        }
    }
}
