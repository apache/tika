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
package org.apache.tika.detect.image;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Randomized boundary test for {@link RawTiffDetector}'s directory walk.
 * <p>
 * The detector runs ahead of every parser, and {@code Detector.detect} declares
 * only {@link java.io.IOException}: anything else it throws aborts detection for
 * the document and the remaining detectors never run. So the invariant is simply
 * that no throwable escapes, whatever the directories say.
 * <p>
 * Inputs are well-formed TIFF and BigTIFF headers whose offsets, counts and
 * entry values are drawn from the arithmetic boundaries -- 0, the 32 and 64 bit
 * maxima, the prefix limit, and their neighbours -- since that is where the
 * offset handling goes wrong rather than in random bytes. The seed is random per
 * run and reported on failure.
 */
public class RawTiffDetectorFuzzTest {

    private static final int TRIALS = 20000;

    /**
     * Offsets and values worth trying: adding an entry size to one of the large
     * ones overflows, which is the arithmetic under test.
     */
    private static final long[] BOUNDARIES = {
            0L, 1L, 8L, 16L, 0xFFFFL, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL, 0x100000000L,
            Long.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE - 7, Long.MAX_VALUE - 8,
            Long.MAX_VALUE - 20, Long.MIN_VALUE, -1L,
            RawTiffDetector.MAX_PREFIX_LENGTH, RawTiffDetector.MAX_PREFIX_LENGTH - 1,
            RawTiffDetector.MAX_PREFIX_LENGTH + 1};

    private static final int[] TAGS =
            {0x00FE, 0x0102, 0x0103, 0x0106, 0x010F, 0x014A, 0xC612};
    private static final int[] TYPES = {2, 3, 4, 13, 16, 18};

    @Test
    public void testBoundaryOffsets() {
        long seed = new Random().nextLong();
        Random rng = new Random(seed);
        for (int trial = 0; trial < TRIALS; trial++) {
            byte[] tiff = randomTiff(rng);
            try {
                RawTiffDetector.detect(tiff, tiff.length);
            } catch (Throwable t) {
                fail("detect threw " + t + " -- seed=" + seed + " trial=" + trial
                        + " bytes=" + hex(tiff), t);
            }
        }
    }

    private static byte[] randomTiff(Random rng) {
        boolean bigTiff = rng.nextInt(4) != 0;
        byte[] b = new byte[24 + rng.nextInt(400)];
        rng.nextBytes(b);
        b[0] = 'I';
        b[1] = 'I';
        put(b, 2, bigTiff ? 43 : 42, 2);
        int header;
        if (bigTiff) {
            put(b, 4, 8, 2);
            put(b, 6, 0, 2);
            put(b, 8, boundary(rng), 8);
            header = 16;
        } else {
            put(b, 4, boundary(rng), 4);
            header = 8;
        }
        if (rng.nextBoolean()) {
            //also point the header at a directory that is really there, so the
            //entry values get walked rather than rejected at the first offset
            put(b, bigTiff ? 8 : 4, header, bigTiff ? 8 : 4);
            fillDirectory(b, header, bigTiff, rng);
        }
        return b;
    }

    private static void fillDirectory(byte[] b, int at, boolean bigTiff, Random rng) {
        int countSize = bigTiff ? 8 : 2;
        int entrySize = bigTiff ? 20 : 12;
        int offsetSize = bigTiff ? 8 : 4;
        int numEntries = rng.nextInt(6);
        put(b, at, numEntries, countSize);
        int p = at + countSize;
        for (int i = 0; i < numEntries && p + entrySize <= b.length; i++) {
            put(b, p, TAGS[rng.nextInt(TAGS.length)], 2);
            put(b, p + 2, TYPES[rng.nextInt(TYPES.length)], 2);
            put(b, p + 4, boundary(rng), offsetSize);
            put(b, p + 4 + offsetSize, boundary(rng), offsetSize);
            p += entrySize;
        }
        if (p + offsetSize <= b.length) {
            put(b, p, boundary(rng), offsetSize);
        }
    }

    private static long boundary(Random rng) {
        return BOUNDARIES[rng.nextInt(BOUNDARIES.length)];
    }

    private static void put(byte[] b, int off, long value, int width) {
        for (int i = 0; i < width && off + i < b.length; i++) {
            b[off + i] = (byte) ((value >>> (8 * i)) & 0xFF);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(b.length, 64); i++) {
            sb.append(String.format(Locale.ROOT, "%02X", b[i]));
        }
        return sb.toString();
    }
}
