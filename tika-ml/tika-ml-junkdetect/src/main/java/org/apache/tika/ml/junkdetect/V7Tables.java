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
package org.apache.tika.ml.junkdetect;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Carrier for one script's v7 F1 tables.
 *
 * <p>The v6 design used a single global codepoint-bigram hash + Bloom
 * filter shared across all scripts.  We measured that this ceiling
 * limits accuracy: enlarging one script's training data (e.g. HAN) hurts
 * the other scripts' z-scores because they share the global hash.  v7
 * gives each script its own pair of tables.
 *
 * <p>Per-script layout:
 *
 * <ul>
 *   <li>{@code codepointIndex} — sorted, ascending {@code int[]} of every
 *       codepoint that appears as either side of a kept bigram for this
 *       script.  Codepoint → dense index is a binary search; index →
 *       codepoint is direct array access.  Typical sizes: ~7K-15K for HAN,
 *       ~200-500 for most other scripts.
 *   <li>{@code bigramKeys} / {@code bigramValues} — parallel arrays
 *       implementing an open-addressed hash table with linear probing.
 *       Each key is a 32-bit value {@code (idxA << 16) | idxB}; key {@code
 *       -1} means "empty slot."  Indices are bounded at 16 bits (65535),
 *       which is comfortably above the largest per-script codepoint count
 *       we observe.
 *   <li>{@code unigramTable} — {@code byte[numCodepoints]}, quantized
 *       unigram log-probabilities indexed by the same codepoint→index map.
 *   <li>{@code bigramQuantMin/Max}, {@code unigramQuantMin/Max} —
 *       per-quantization ranges; dequantize by
 *       {@code min + (b/255) * (max - min)}.
 *   <li>{@code unigramFallbackLogProb} — log-prob assigned when a
 *       codepoint is not in {@code codepointIndex} at all.  Set to the
 *       script's most-pessimistic unigram value (its quantization min) so
 *       absent codepoints don't accidentally score above legitimately-rare
 *       ones.
 *   <li>{@code backoffAlpha} — multiplier on the unigram-backoff
 *       independence sum, copied from v6.
 * </ul>
 *
 * <p>Membership semantics: no Bloom filter.  The empty-slot sentinel is
 * the membership oracle — a pair is "seen" iff binary-search finds both
 * codepoints in the index AND a probe sequence hits a matching key before
 * an empty slot.  Lookups are therefore exact; there is no false-positive
 * backoff path as there is in v6.
 *
 * <p>Fields are package-private so the
 * {@link org.apache.tika.ml.junkdetect.tools.TrainJunkModel} trainer can
 * construct instances directly without going through accessors.
 */
public final class V7Tables {

    /** Reserved value in {@link #bigramKeys} marking an unoccupied slot. */
    public static final int EMPTY_KEY = -1;

    final int[] codepointIndex;
    final int[] bigramKeys;
    final byte[] bigramValues;
    final byte[] unigramTable;
    final float bigramQuantMin;
    final float bigramQuantMax;
    final float unigramQuantMin;
    final float unigramQuantMax;
    final float unigramFallbackLogProb;
    final float backoffAlpha;

    public V7Tables(int[] codepointIndex,
                    int[] bigramKeys, byte[] bigramValues,
                    byte[] unigramTable,
                    float bigramQuantMin, float bigramQuantMax,
                    float unigramQuantMin, float unigramQuantMax,
                    float unigramFallbackLogProb,
                    float backoffAlpha) {
        if (bigramKeys.length != bigramValues.length) {
            throw new IllegalArgumentException(
                    "bigramKeys and bigramValues must have equal length: "
                    + bigramKeys.length + " vs " + bigramValues.length);
        }
        if (unigramTable.length != codepointIndex.length) {
            throw new IllegalArgumentException(
                    "unigramTable.length must equal codepointIndex.length: "
                    + unigramTable.length + " vs " + codepointIndex.length);
        }
        this.codepointIndex = codepointIndex;
        this.bigramKeys = bigramKeys;
        this.bigramValues = bigramValues;
        this.unigramTable = unigramTable;
        this.bigramQuantMin = bigramQuantMin;
        this.bigramQuantMax = bigramQuantMax;
        this.unigramQuantMin = unigramQuantMin;
        this.unigramQuantMax = unigramQuantMax;
        this.unigramFallbackLogProb = unigramFallbackLogProb;
        this.backoffAlpha = backoffAlpha;
    }

    /**
     * Serialises this script's F1 tables.  Read back via
     * {@link #readFrom(DataInputStream)}.
     */
    public void writeTo(DataOutputStream dos) throws IOException {
        dos.writeFloat(backoffAlpha);

        // Codepoint index.
        dos.writeInt(codepointIndex.length);
        ByteBuffer cpBuf = ByteBuffer.allocate(codepointIndex.length * 4)
                .order(ByteOrder.BIG_ENDIAN);
        cpBuf.asIntBuffer().put(codepointIndex);
        dos.write(cpBuf.array());

        // Bigram open-addressing table (keys + values).
        dos.writeInt(bigramKeys.length);
        dos.writeFloat(bigramQuantMin);
        dos.writeFloat(bigramQuantMax);
        ByteBuffer keyBuf = ByteBuffer.allocate(bigramKeys.length * 4)
                .order(ByteOrder.BIG_ENDIAN);
        keyBuf.asIntBuffer().put(bigramKeys);
        dos.write(keyBuf.array());
        dos.write(bigramValues);

        // Unigram table.
        dos.writeFloat(unigramQuantMin);
        dos.writeFloat(unigramQuantMax);
        dos.writeFloat(unigramFallbackLogProb);
        dos.write(unigramTable);
    }

    /** Inverse of {@link #writeTo(DataOutputStream)}. */
    public static V7Tables readFrom(DataInputStream dis) throws IOException {
        float backoffAlpha = dis.readFloat();

        int cpCount = dis.readInt();
        byte[] cpBytes = dis.readNBytes(cpCount * 4);
        int[] codepoints = new int[cpCount];
        ByteBuffer.wrap(cpBytes).order(ByteOrder.BIG_ENDIAN).asIntBuffer().get(codepoints);

        int slots = dis.readInt();
        float bMin = dis.readFloat();
        float bMax = dis.readFloat();
        byte[] keyBytes = dis.readNBytes(slots * 4);
        int[] keys = new int[slots];
        ByteBuffer.wrap(keyBytes).order(ByteOrder.BIG_ENDIAN).asIntBuffer().get(keys);
        byte[] values = dis.readNBytes(slots);

        float uMin = dis.readFloat();
        float uMax = dis.readFloat();
        float uFallback = dis.readFloat();
        byte[] unigramTable = dis.readNBytes(cpCount);

        return new V7Tables(codepoints, keys, values, unigramTable,
                bMin, bMax, uMin, uMax, uFallback, backoffAlpha);
    }

    /**
     * Returns a one-line summary for trainer progress output.
     */
    public String statsString() {
        return String.format(
                "  cp_index=%d, bigram_slots=%d (load≈%.2f), "
                + "bigram_range=[%.3f, %.3f], unigram_range=[%.3f, %.3f]",
                codepointIndex.length, bigramKeys.length,
                occupiedSlots() / (double) Math.max(1, bigramKeys.length),
                bigramQuantMin, bigramQuantMax,
                unigramQuantMin, unigramQuantMax);
    }

    private int occupiedSlots() {
        int n = 0;
        for (int k : bigramKeys) {
            if (k != EMPTY_KEY) n++;
        }
        return n;
    }

    /** Number of codepoints in this script's index.  Diagnostic. */
    public int codepointCount() {
        return codepointIndex.length;
    }

    /** Number of bigram-table slots (capacity).  Diagnostic. */
    public int bigramSlots() {
        return bigramKeys.length;
    }
}
