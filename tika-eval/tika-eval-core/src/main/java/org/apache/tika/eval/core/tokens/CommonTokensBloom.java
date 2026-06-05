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
package org.apache.tika.eval.core.tokens;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.digest.MurmurHash3;
import org.apache.commons.collections4.bloomfilter.BitMapExtractor;
import org.apache.commons.collections4.bloomfilter.BloomFilter;
import org.apache.commons.collections4.bloomfilter.EnhancedDoubleHasher;
import org.apache.commons.collections4.bloomfilter.Hasher;
import org.apache.commons.collections4.bloomfilter.Shape;
import org.apache.commons.collections4.bloomfilter.SimpleBloomFilter;

/**
 * Shared serialization, sizing and hashing for the per-language "common tokens" Bloom filters.
 * <p>
 * The common-token lists are only ever queried for membership ("is this token one of the N
 * most common tokens in language X?"), so they are shipped as one Bloom filter per language
 * instead of the raw token lists. This is dramatically smaller (the filter is sized by the
 * token <em>count</em>, not the token text) at the cost of a small, bounded false-positive
 * rate: a token that is not common may occasionally be reported as common. There are never
 * false negatives. For the OOV/common-token ratio this feeds, that bias is negligible.
 * <p>
 * Both the runtime loader ({@link CommonTokenCountManager}) and the offline generator use the
 * methods here so the hashing and on-disk format can never drift apart. The format is:
 * <pre>
 *   int  magic   (0x54424631, "TBF1")
 *   int  k       (number of hash functions)
 *   int  m       (number of bits)
 *   int  nLongs  (length of the bit-map array)
 *   long[nLongs] bit map (big-endian)
 * </pre>
 */
public final class CommonTokensBloom {

    /**
     * Target false-positive rate for the generated filters. See the class comment for why a
     * non-zero rate is acceptable here. Changing this requires regenerating the shipped
     * {@code common_tokens_bloom/} resources.
     * <p>
     * 0.0001 (1 in 10,000) keeps the OOV metric close to a literal lookup list: the candidate
     * filter already removes numeric/short/HTML false positives, and this rate makes a residual
     * collision on a genuine word ~10x rarer, which matters for repetitive junk docs where a
     * single dominant token can swing occurrence-weighted OOV. Costs ~2 MB over 0.001.
     */
    public static final double DEFAULT_FPP = 0.0001;

    private static final int MAGIC = 0x54424631; // "TBF1"

    private CommonTokensBloom() {
    }

    /**
     * @param numTokens number of tokens that will be added to the filter
     * @return a {@link Shape} sized for {@code numTokens} at {@link #DEFAULT_FPP}
     */
    public static Shape shapeFor(int numTokens) {
        return Shape.fromNP(Math.max(1, numTokens), DEFAULT_FPP);
    }

    /**
     * Deterministic hasher for a token, used identically at generation and query time.
     */
    public static Hasher hasher(String token) {
        long[] h = MurmurHash3.hash128x64(token.getBytes(StandardCharsets.UTF_8));
        return new EnhancedDoubleHasher(h[0], h[1]);
    }

    /**
     * @return {@code true} if {@code token} might be in {@code filter} (subject to the
     * filter's false-positive rate); {@code false} means it is definitely not present.
     */
    public static boolean mightContain(BloomFilter filter, String token) {
        return filter.contains(hasher(token).indices(filter.getShape()));
    }

    /**
     * Writes {@code filter} to {@code os} in the format documented on this class. Does not
     * close the stream.
     */
    public static void write(OutputStream os, BloomFilter filter) throws IOException {
        Shape shape = filter.getShape();
        long[] bits = filter.asBitMapArray();
        DataOutputStream d = new DataOutputStream(os);
        d.writeInt(MAGIC);
        d.writeInt(shape.getNumberOfHashFunctions());
        d.writeInt(shape.getNumberOfBits());
        d.writeInt(bits.length);
        for (long b : bits) {
            d.writeLong(b);
        }
        d.flush();
    }

    /**
     * Reads a filter written by {@link #write}. Does not close the stream.
     */
    public static BloomFilter read(InputStream is) throws IOException {
        DataInputStream d = new DataInputStream(is);
        int magic = d.readInt();
        if (magic != MAGIC) {
            throw new IOException(
                    "Not a common-tokens Bloom filter (bad magic: 0x" + Integer.toHexString(magic) +
                            ")");
        }
        int k = d.readInt();
        int m = d.readInt();
        int nLongs = d.readInt();
        long[] bits = new long[nLongs];
        for (int i = 0; i < nLongs; i++) {
            bits[i] = d.readLong();
        }
        SimpleBloomFilter filter = new SimpleBloomFilter(Shape.fromKM(k, m));
        filter.merge(BitMapExtractor.fromBitMapArray(bits));
        return filter;
    }
}
