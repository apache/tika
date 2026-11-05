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
package org.apache.tika.ml.junkdetect.tools;

import org.apache.tika.ml.junkdetect.HashedJunkModel;

/**
 * Sparse feature vectors over {@link HashedJunkModel#featureBuckets}, so the trainer sees exactly
 * the buckets the runtime scores.
 */
final class HashedFeatures {

    private HashedFeatures() {
    }

    /** Sparse vector: bucket ids ascending, one value each. */
    static final class Vec {
        final int[] idx;
        final float[] val;

        Vec(int[] idx, float[] val) {
            this.idx = idx;
            this.val = val;
        }

        double dot(float[] w) {
            double s = 0;
            for (int i = 0; i < idx.length; i++) {
                s += w[idx[i]] * val[i];
            }
            return s;
        }
    }

    /** Distinct buckets ascending and their counts. */
    static final class Counts {
        final int[] idx;
        final int[] cnt;
        final long total;

        Counts(int[] idx, int[] cnt, long total) {
            this.idx = idx;
            this.cnt = cnt;
            this.total = total;
        }
    }

    static Counts counts(String text, int log2Size, int maxN) {
        int[] b = HashedJunkModel.featureBuckets(text, log2Size, maxN);
        int distinct = 0;
        for (int i = 0; i < b.length; i++) {
            if (i == 0 || b[i] != b[i - 1]) {
                distinct++;
            }
        }
        int[] idx = new int[distinct];
        int[] cnt = new int[distinct];
        int k = -1;
        for (int i = 0; i < b.length; i++) {
            if (i == 0 || b[i] != b[i - 1]) {
                idx[++k] = b[i];
            }
            cnt[k]++;
        }
        return new Counts(idx, cnt, b.length);
    }

    /** {@code a - b}, divided by its L1 norm; {@code null} when the two bags are equal. */
    static Vec pairDiff(Counts a, Counts b) {
        int[] idx = new int[a.idx.length + b.idx.length];
        int[] d = new int[idx.length];
        int n = 0;
        long l1 = 0;
        int i = 0;
        int j = 0;
        while (i < a.idx.length || j < b.idx.length) {
            int ka = i < a.idx.length ? a.idx[i] : Integer.MAX_VALUE;
            int kb = j < b.idx.length ? b.idx[j] : Integer.MAX_VALUE;
            int k = Math.min(ka, kb);
            int v = 0;
            if (ka == k) {
                v += a.cnt[i++];
            }
            if (kb == k) {
                v -= b.cnt[j++];
            }
            if (v != 0) {
                idx[n] = k;
                d[n++] = v;
                l1 += Math.abs(v);
            }
        }
        if (n == 0) {
            return null;
        }
        int[] oi = new int[n];
        float[] ov = new float[n];
        for (int q = 0; q < n; q++) {
            oi[q] = idx[q];
            ov[q] = (float) ((double) d[q] / l1);
        }
        return new Vec(oi, ov);
    }

    /** Counts divided by the n-gram total: the pointwise head's input. */
    static Vec pointVec(Counts c) {
        float[] v = new float[c.idx.length];
        double tot = Math.max(1, c.total);
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (c.cnt[i] / tot);
        }
        return new Vec(c.idx.clone(), v);
    }
}
