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

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Carrier for the global Feature 1 (codepoint-bigram hash + Bloom filter +
 * unigram-backoff) tables used by the v6 junk-detector model format.
 *
 * <p>Instances are created by
 * {@link org.apache.tika.ml.junkdetect.tools.TrainJunkModel#trainCodepointHashTables}
 * and consumed by {@link JunkDetector#computeF1MeanLogP}.  Separating this
 * type from {@link JunkDetector} lets the trainer import it directly without
 * reaching into the inference class, and keeps the fields package-private so
 * they are not part of the public API.
 */
public final class F1Tables {

    final byte[] bigramHash;
    final int bigramBuckets;
    final float bigramQuantMin;
    final float bigramQuantMax;
    final byte[] unigramHash;
    final int unigramBuckets;
    final float unigramQuantMin;
    final float unigramQuantMax;
    final long[] bloomBits;
    final int bloomBitCount;
    final int bloomK;
    final int fnvSeed;
    final float backoffAlpha;

    public F1Tables(byte[] bigramHash, int bigramBuckets,
                    float bigramQuantMin, float bigramQuantMax,
                    byte[] unigramHash, int unigramBuckets,
                    float unigramQuantMin, float unigramQuantMax,
                    long[] bloomBits, int bloomBitCount, int bloomK,
                    int fnvSeed, float backoffAlpha) {
        this.bigramHash = bigramHash;
        this.bigramBuckets = bigramBuckets;
        this.bigramQuantMin = bigramQuantMin;
        this.bigramQuantMax = bigramQuantMax;
        this.unigramHash = unigramHash;
        this.unigramBuckets = unigramBuckets;
        this.unigramQuantMin = unigramQuantMin;
        this.unigramQuantMax = unigramQuantMax;
        this.bloomBits = bloomBits;
        this.bloomBitCount = bloomBitCount;
        this.bloomK = bloomK;
        this.fnvSeed = fnvSeed;
        this.backoffAlpha = backoffAlpha;
    }

    /**
     * Serializes the global F1 section to {@code dos} in the v6 model binary
     * format.  Called by
     * {@link org.apache.tika.ml.junkdetect.tools.TrainJunkModel#saveModelV6}.
     */
    public void writeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(fnvSeed);
        dos.writeFloat(backoffAlpha);
        dos.writeInt(bigramBuckets);
        dos.writeFloat(bigramQuantMin);
        dos.writeFloat(bigramQuantMax);
        dos.write(bigramHash);
        dos.writeInt(unigramBuckets);
        dos.writeFloat(unigramQuantMin);
        dos.writeFloat(unigramQuantMax);
        dos.write(unigramHash);
        dos.writeInt(bloomBitCount);
        dos.writeByte(bloomK);
        ByteBuffer bloomBuf = ByteBuffer.allocate(bloomBitCount / 8)
                .order(ByteOrder.BIG_ENDIAN);
        for (long w : bloomBits) {
            bloomBuf.putLong(w);
        }
        dos.write(bloomBuf.array());
    }

    /**
     * Returns a human-readable summary of the quantization ranges, for
     * trainer progress output.
     */
    public String statsString() {
        return String.format(
                "  bigram quant range: [%.3f, %.3f]%n  unigram quant range: [%.3f, %.3f]%n",
                bigramQuantMin, bigramQuantMax, unigramQuantMin, unigramQuantMax);
    }
}
