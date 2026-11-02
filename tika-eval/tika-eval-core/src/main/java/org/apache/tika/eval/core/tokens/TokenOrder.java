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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Finds text that moved between two token sequences of the same document.
 * <p>
 * Anchors are trigrams (bigrams when trigrams give too few) that occur exactly once in
 * each sequence. The longest increasing subsequence of the anchors' B positions, taken in
 * A order, is the text that kept its order; the other anchors moved. A run of moved
 * anchors that stays adjacent in both sequences is a moved segment, and one of at least
 * {@link #MIN_RUN} anchors is reported. The counts are absolute, so a single sentence
 * moved in a long document is reported, while edits that do not move text create no
 * runs.
 */
public final class TokenOrder {

    /** Below this many anchors the sequences are not scored. */
    public static final int MIN_ANCHORS = 8;
    /** A moved segment must hold at least this many anchors; shorter runs are chance. */
    public static final int MIN_RUN = 3;
    /** Adjacent moved anchors may skip this many B positions (edits inside the segment). */
    private static final int GAP = 4;
    private static final int MAX_SAMPLE_TOKENS = 20;

    private TokenOrder() {
    }

    /**
     * @return null when the sequences share fewer than {@link #MIN_ANCHORS} anchors
     */
    public static Result compare(String[] a, String[] b) {
        Map<String, Integer> ids = new HashMap<>();
        int[] ia = toIds(a, ids);
        int[] ib = toIds(b, ids);
        int n = 3;
        int[][] anchors = anchors(ia, ib, n);
        if (anchors[0].length < MIN_ANCHORS) {
            n = 2;
            anchors = anchors(ia, ib, n);
        }
        if (anchors[0].length < MIN_ANCHORS) {
            return null;
        }
        return segments(a, anchors[0], anchors[1], n);
    }

    private static int[] toIds(String[] tokens, Map<String, Integer> ids) {
        int[] out = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            out[i] = ids.computeIfAbsent(tokens[i], k -> ids.size());
        }
        return out;
    }

    private static long[] grams(int[] ids, int n) {
        long[] g = new long[Math.max(0, ids.length - n + 1)];
        for (int i = 0; i < g.length; i++) {
            long h = 0;
            for (int j = 0; j < n; j++) {
                h = h * 0x9E3779B97F4A7C15L + ids[i + j] + 1;
            }
            g[i] = h;
        }
        return g;
    }

    /** Position of each gram that occurs once, -1 for repeated grams. */
    private static Map<Long, Integer> uniquePositions(long[] grams) {
        Map<Long, Integer> pos = new HashMap<>(grams.length * 2);
        for (int i = 0; i < grams.length; i++) {
            pos.merge(grams[i], i, (old, cur) -> -1);
        }
        return pos;
    }

    /** {A positions, B positions} of the anchors, in A order. */
    private static int[][] anchors(int[] a, int[] b, int n) {
        long[] ga = grams(a, n);
        Map<Long, Integer> posA = uniquePositions(ga);
        Map<Long, Integer> posB = uniquePositions(grams(b, n));
        int[] pa = new int[ga.length];
        int[] pb = new int[ga.length];
        int k = 0;
        for (int i = 0; i < ga.length; i++) {
            Integer j = posB.get(ga[i]);
            if (j != null && j >= 0 && posA.get(ga[i]) == i) {
                pa[k] = i;
                pb[k++] = j;
            }
        }
        return new int[][]{Arrays.copyOf(pa, k), Arrays.copyOf(pb, k)};
    }

    /** Marks the members of one longest strictly increasing subsequence. */
    static boolean[] lis(int[] seq) {
        int[] tails = new int[seq.length];
        int[] tailIdx = new int[seq.length];
        int[] prev = new int[seq.length];
        int len = 0;
        for (int i = 0; i < seq.length; i++) {
            int lo = 0;
            int hi = len;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (tails[mid] < seq[i]) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            prev[i] = lo > 0 ? tailIdx[lo - 1] : -1;
            tails[lo] = seq[i];
            tailIdx[lo] = i;
            if (lo == len) {
                len++;
            }
        }
        boolean[] keep = new boolean[seq.length];
        for (int i = len > 0 ? tailIdx[len - 1] : -1; i >= 0; i = prev[i]) {
            keep[i] = true;
        }
        return keep;
    }

    private static Result segments(String[] a, int[] pa, int[] pb, int n) {
        boolean[] keep = lis(pb);
        Result r = new Result();
        r.anchors = pa.length;
        r.ngram = n;
        int runStart = -1;
        int runLength = 0;
        for (int i = 0; i <= pa.length; i++) {
            boolean grows = i < pa.length && !keep[i] && runLength > 0
                    && pb[i] - pb[i - 1] > 0 && pb[i] - pb[i - 1] <= GAP;
            if (grows) {
                runLength++;
                continue;
            }
            if (runLength >= MIN_RUN) {
                int end = pa[runStart + runLength - 1] + n;
                r.movedSegments++;
                r.movedTokens += end - pa[runStart];
                if (runLength > r.longestRun) {
                    r.longestRun = runLength;
                    r.sample = sample(a, pa[runStart], end);
                }
            }
            if (i < pa.length && !keep[i]) {
                runStart = i;
                runLength = 1;
            } else {
                runLength = 0;
            }
        }
        return r;
    }

    private static String sample(String[] a, int start, int end) {
        StringJoiner sj = new StringJoiner(" ");
        for (int i = start; i < Math.min(end, start + MAX_SAMPLE_TOKENS); i++) {
            sj.add(a[i]);
        }
        return sj.toString();
    }

    public static final class Result {
        int anchors;
        int ngram;
        int movedSegments;
        int movedTokens;
        int longestRun;
        String sample;

        public int getAnchors() {
            return anchors;
        }

        /** 3, or 2 when trigrams gave too few anchors. */
        public int getNgram() {
            return ngram;
        }

        /** Moved segments of at least {@link #MIN_RUN} anchors. */
        public int getMovedSegments() {
            return movedSegments;
        }

        /** A tokens covered by the moved segments. */
        public int getMovedTokens() {
            return movedTokens;
        }

        /** Anchors in the longest moved segment; 0 when none reached {@link #MIN_RUN}. */
        public int getLongestRun() {
            return longestRun;
        }

        /** Leading A tokens of the longest moved segment, or null. */
        public String getSample() {
            return sample;
        }
    }
}
