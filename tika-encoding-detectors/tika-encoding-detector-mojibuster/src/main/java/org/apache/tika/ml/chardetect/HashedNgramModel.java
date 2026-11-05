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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Hashed byte n-gram charset model (format {@code HNG2}).
 *
 * <p>Features, all looked up as int8 embedding rows with a per-row float scale:</p>
 * <ul>
 *   <li>byte n-grams of each order that contain at least one non-text byte (outside
 *       printable ASCII, TAB, LF, CR); unigrams and dense bigrams index directly,
 *       wider orders are hashed;</li>
 *   <li>column channels over the first {@code colwin} bytes (all bytes): byte at even
 *       offset, byte at odd offset.  These carry UTF-16/UTF-32 alignment, which byte
 *       n-grams cannot see.</li>
 * </ul>
 * <p>Each channel is mean- and max-pooled, a log-count feature is appended, then one ReLU
 * hidden layer gives raw per-class logits (no softmax).</p>
 *
 * <p>Thread-safe: the tables are read-only and scratch space is per thread.</p>
 */
public final class HashedNgramModel {

    private static final int MAGIC_HNG2 = 0x484E4732;
    private static final long GOLD = 0x9E3779B97F4A7C15L;
    private static final int POOL_MEAN = 1;
    private static final int POOL_MAX = 2;
    private static final int POOL_BOTH = 3;
    /** Bounds header fields read from the model file. */
    private static final int MAX_BITS = 24;
    private static final int MAX_DIM = 1024;
    private static final int MAX_CLASSES = 4096;
    /** Probe bytes whose non-text positions are gathered at a time. */
    private static final int BLOCK = 4096;
    private static final int MAX_ORDER = 8;
    /** Non-text bytes whose windows are always scored before the early-stop check. */
    private static final int CHECKPOINT_NON_TEXT = 4096;
    /** Logit lead of the top class at the checkpoint that stops full scoring. */
    private static final float EARLY_STOP_MARGIN = 2f;
    /** Widest channel whose rows are tracked for max-pool dedup (2^16 bits = 8 KB per thread). */
    private static final int MAX_SEEN_BITS = 16;

    /** 1 for a byte outside printable ASCII, TAB, LF and CR. */
    private static final int[] NON_TEXT = new int[256];

    static {
        Arrays.fill(NON_TEXT, 1);
        for (int b = 0x20; b < 0x7F; b++) {
            NON_TEXT[b] = 0;
        }
        NON_TEXT['\t'] = 0;
        NON_TEXT['\n'] = 0;
        NON_TEXT['\r'] = 0;
    }

    private final String[] labels;
    private final int nOrders;
    private final int maxN;
    private final int asciiOrders;
    private final int hidden;
    private final int inputDim;
    private final int width;
    private final int pool;
    private final int np;
    private final int nCols;
    private final int colwin;
    private final boolean countFeature;
    private final int[] order;
    private final int[] bits;
    private final int[] dim;
    private final int[] base;
    private final int[] colKind;
    private final int[] colBits;
    private final float[][] rowScale;
    private final byte[][] emb;
    /** Hidden weights transposed to [input][hidden]. */
    private final float[] w1t;
    private final float[] b1;
    /** Output weights transposed to [hidden][class]. */
    private final float[] w2t;
    private final float[] b2;
    /** Per channel, first bit of its row set in {@link Workspace#seen}, or -1 if too wide to track. */
    private final int[] seenBase;
    private final int seenWords;
    private final ThreadLocal<Workspace> workspace;

    public HashedNgramModel(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new BufferedInputStream(in));
        int magic = d.readInt();
        if (magic != MAGIC_HNG2) {
            throw new IOException(String.format(java.util.Locale.ROOT,
                    "Bad magic 0x%08X, expected 0x%08X (HNG2)", magic, MAGIC_HNG2));
        }
        int nc = checked(d.readInt(), 1, MAX_CLASSES, "classes");
        labels = new String[nc];
        for (int i = 0; i < nc; i++) {
            labels[i] = d.readUTF();
        }
        nOrders = checked(d.readInt(), 0, 8, "orders");
        int ncols = 0;
        // Orders and column channels share one feature layout: [orders..., columns...].
        int[] tmpOrder = new int[nOrders];
        int[] tmpBits = new int[nOrders];
        int[] tmpDim = new int[nOrders];
        float[][] tmpScale = new float[nOrders][];
        byte[][] tmpEmb = new byte[nOrders][];
        int mx = 0;
        for (int o = 0; o < nOrders; o++) {
            tmpOrder[o] = checked(d.readInt(), 1, MAX_ORDER, "order");
            tmpBits[o] = checked(d.readInt(), 1, MAX_BITS, "bits");
            tmpDim[o] = checked(d.readInt(), 1, MAX_DIM, "dim");
            if (o > 0 && tmpOrder[o] < tmpOrder[o - 1]) {
                throw new IOException("orders must be ascending");
            }
            mx = Math.max(mx, tmpOrder[o]);
            int rows = 1 << tmpBits[o];
            tmpScale[o] = readFloats(d, rows);
            tmpEmb[o] = new byte[rows * tmpDim[o]];
            d.readFully(tmpEmb[o]);
        }
        maxN = mx;
        ncols = checked(d.readInt(), 0, 8, "column channels");
        nCols = ncols;
        int total = nOrders + nCols;
        order = new int[nOrders];
        System.arraycopy(tmpOrder, 0, order, 0, nOrders);
        bits = new int[total];
        dim = new int[total];
        base = new int[total];
        rowScale = new float[total][];
        emb = new byte[total][];
        colKind = new int[nCols];
        colBits = new int[nCols];
        int off = 0;
        for (int o = 0; o < nOrders; o++) {
            bits[o] = tmpBits[o];
            dim[o] = tmpDim[o];
            base[o] = off;
            off += dim[o];
            rowScale[o] = tmpScale[o];
            emb[o] = tmpEmb[o];
        }
        for (int c = 0; c < nCols; c++) {
            int f = nOrders + c;
            colKind[c] = checked(d.readInt(), 0, 1, "column kind");
            colBits[c] = checked(d.readInt(), 8, 8, "column bits");
            bits[f] = colBits[c];
            dim[f] = checked(d.readInt(), 1, MAX_DIM, "dim");
            base[f] = off;
            off += dim[f];
            int rows = 1 << bits[f];
            rowScale[f] = readFloats(d, rows);
            emb[f] = new byte[rows * dim[f]];
            d.readFully(emb[f]);
        }
        colwin = checked(d.readInt(), 0, 1 << 20, "column window");
        asciiOrders = checked(d.readInt(), 0, 8, "ascii orders");
        countFeature = d.readInt() != 0;
        pool = checked(d.readInt(), POOL_MEAN, POOL_BOTH, "pool");
        np = pool == POOL_BOTH ? 2 : 1;
        hidden = checked(d.readInt(), 1, MAX_DIM, "hidden");
        width = off;
        inputDim = np * off + (countFeature ? 1 : 0);
        w1t = transpose(readFloats(d, hidden * inputDim), hidden, inputDim);
        b1 = readFloats(d, hidden);
        w2t = transpose(readFloats(d, nc * hidden), nc, hidden);
        b2 = readFloats(d, nc);
        if (d.read() != -1) {
            throw new IOException("trailing bytes after model");
        }
        seenBase = new int[total];
        int bitsUsed = 0;
        for (int f = 0; f < total; f++) {
            if (bits[f] <= MAX_SEEN_BITS) {
                seenBase[f] = bitsUsed;
                bitsUsed += Math.max(64, 1 << bits[f]);
            } else {
                seenBase[f] = -1;
            }
        }
        seenWords = bitsUsed / 64;
        workspace = ThreadLocal.withInitial(Workspace::new);
    }

    private static int checked(int v, int min, int max, String what) throws IOException {
        if (v < min || v > max) {
            throw new IOException("model " + what + " out of range: " + v);
        }
        return v;
    }

    private static float[] readFloats(DataInputStream d, int k) throws IOException {
        byte[] b = new byte[4 * k];
        d.readFully(b);
        float[] f = new float[k];
        ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(f);
        return f;
    }

    private static float[] transpose(float[] m, int rows, int cols) {
        float[] t = new float[m.length];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                t[c * rows + r] = m[r * cols + c];
            }
        }
        return t;
    }

    public String[] getLabels() {
        return labels.clone();
    }

    public int getNumClasses() {
        return labels.length;
    }

    private final class Workspace {
        final float[] acc = new float[width];
        final float[] max = new float[width];
        final float[] z = new float[inputDim];
        final float[] h = new float[hidden];
        final int[] count = new int[nOrders + nCols];
        final int[] nonText = new int[BLOCK + 2 * MAX_ORDER];
        final int[] prevStart = new int[nOrders];
        final long[] seen = new long[seenWords];
        final boolean[] seenByte = new boolean[256];
        int scored;
    }

    private static int bucket(long v, int n, int bits) {
        if (n == 1 || (n == 2 && bits >= 16)) {
            return (int) v;
        }
        return (int) (((v + n * 0x100000000000L) * GOLD) >>> (64 - bits));
    }

    /**
     * Writes raw logits for {@code p[from, to)} into {@code out} (length {@link #getNumClasses()}).
     *
     * <p>Windows around the first {@code CHECKPOINT_NON_TEXT} non-text bytes are always scored.
     * If the probe goes on and the logits there already lead by {@code EARLY_STOP_MARGIN}, the
     * rest only adds windows around byte values not seen yet; otherwise it is scored in full.</p>
     *
     * @return {@code false}, leaving {@code out} untouched, when the input has no non-text byte
     *         and the model scores only windows holding one (abstain)
     */
    public boolean logits(byte[] p, int from, int to, float[] out) {
        return logits(p, from, to, out, CHECKPOINT_NON_TEXT);
    }

    /** As {@link #logits(byte[], int, int, float[])} with the early-stop checkpoint given. */
    boolean logits(byte[] p, int from, int to, float[] out, int checkpoint) {
        Workspace ws = workspace.get();
        Arrays.fill(ws.seen, 0L);
        Arrays.fill(ws.acc, 0f);
        Arrays.fill(ws.max, Float.NEGATIVE_INFINITY);
        Arrays.fill(ws.count, 0);
        Arrays.fill(ws.prevStart, from - 1);
        ws.scored = 0;

        int i = scoreNonText(p, from, to, checkpoint, ws);
        int nonText = ws.scored;
        if (nonText == 0 && asciiOrders == 0) {
            return false;
        }
        scoreColumns(p, from, to, ws);
        // With asciiOrders > 0 the checkpoint logits would lack those orders (scored last).
        if (i < to && asciiOrders == 0 && leadsAtCheckpoint(ws, nonText, out)) {
            nonText += scoreNewByteValues(p, from, i, to, ws);
        } else if (i < to) {
            scoreNonText(p, i, to, Integer.MAX_VALUE, ws);
            nonText = ws.scored;
        }
        for (int o = 0; o < nOrders; o++) {
            if (order[o] <= asciiOrders) {
                ws.count[o] = scoreAllWindows(o, p, from, to, ws.acc, ws.max, ws.seen);
            }
        }
        dense(ws, nonText, out);
        return true;
    }

    private void scoreColumns(byte[] p, int from, int to, Workspace ws) {
        int end = Math.min(to, from + colwin);
        for (int c = 0; c < nCols; c++) {
            int f = nOrders + c;
            byte[] e = emb[f];
            float[] sc = rowScale[f];
            int dm = dim[f];
            int ob = base[f];
            int sb = seenBase[f];
            int n = 0;
            for (int j = from + colKind[c]; j < end; j += 2) {
                accumulate(e, sc, p[j] & 0xFF, dm, ws.acc, ws.max, ob, ws.seen, sb);
                n++;
            }
            ws.count[f] = n;
        }
    }

    /**
     * Scores the windows around non-text bytes of {@code p[i, to)} until {@code limit} of them
     * have been scored in all; returns where it stopped.
     */
    private int scoreNonText(byte[] p, int i, int to, int limit, Workspace ws) {
        int[] nt = ws.nonText;
        // Windows are visited in ascending start order per channel, as a single sliding pass
        // would, so each pooled sum sees the same float additions in the same order.
        while (i < to && ws.scored < limit) {
            int b1 = Math.min(to, i + BLOCK);
            int room = limit - ws.scored;
            int k = 0;
            // Branch-free: dense non-ASCII text mixes in spaces and punctuation unpredictably.
            for (; i < b1 && k < room; i++) {
                nt[k] = i;
                k += NON_TEXT[p[i] & 0xFF];
            }
            ws.scored += k;
            scoreNonTextWindows(p, to, nt, k, ws);
        }
        return i;
    }

    /**
     * Scores {@code p[i, to)} only where a byte value first occurs (a sibling charset often
     * differs in one rare byte, which may first appear late); returns its non-text byte count.
     */
    private int scoreNewByteValues(byte[] p, int from, int i, int to, Workspace ws) {
        boolean[] seenByte = ws.seenByte;
        Arrays.fill(seenByte, false);
        for (int j = from; j < i; j++) {
            seenByte[p[j] & 0xFF] = true;
        }
        int[] nt = ws.nonText;
        int nonText = 0;
        int pushed = i - 1;
        while (i < to) {
            int b1 = Math.min(to, i + BLOCK);
            int k = 0;
            for (; i < b1; i++) {
                int b = p[i] & 0xFF;
                nonText += NON_TEXT[b];
                if (!seenByte[b]) {
                    seenByte[b] = true;
                    // A new text byte value counts through the non-text bytes beside it.
                    boolean isNonText = NON_TEXT[b] != 0;
                    int lo = Math.max(pushed + 1, isNonText ? i : i - maxN + 1);
                    int hi = isNonText ? i : Math.min(to - 1, i + maxN - 1);
                    for (int j = lo; j <= hi; j++) {
                        if (NON_TEXT[p[j] & 0xFF] != 0) {
                            nt[k++] = j;
                            pushed = j;
                        }
                    }
                }
            }
            scoreNonTextWindows(p, to, nt, k, ws);
        }
        return nonText;
    }

    /** Whether the top class already leads by {@code EARLY_STOP_MARGIN}; uses {@code out} as scratch. */
    private boolean leadsAtCheckpoint(Workspace ws, int nonText, float[] out) {
        dense(ws, nonText, out);
        float top = Float.NEGATIVE_INFINITY;
        float second = Float.NEGATIVE_INFINITY;
        for (float x : out) {
            if (x > top) {
                second = top;
                top = x;
            } else if (x > second) {
                second = x;
            }
        }
        return top - second >= EARLY_STOP_MARGIN;
    }

    /** Pools the channels and runs the dense layers into {@code out}. */
    private void dense(Workspace ws, int nonText, float[] out) {
        float[] acc = ws.acc;
        float[] mx = ws.max;
        int[] count = ws.count;
        float[] z = ws.z;
        Arrays.fill(z, 0f);
        for (int f = 0; f < nOrders + nCols; f++) {
            if (count[f] == 0) {
                continue;
            }
            int dm = dim[f];
            int b0 = base[f];
            float inv = 1f / count[f];
            int zo = np * b0;
            for (int j = 0; j < dm; j++) {
                if (pool != POOL_MAX) {
                    z[zo + j] = acc[b0 + j] * inv;
                }
                if (pool != POOL_MEAN) {
                    z[zo + (pool == POOL_BOTH ? dm : 0) + j] = mx[b0 + j];
                }
            }
        }
        if (countFeature) {
            z[inputDim - 1] = (float) (Math.log1p(nonText) / 4.0);
        }
        // Dense layers loop over the output unit innermost; each unit still sums its inputs in
        // index order, so results match the row-major dot products exactly.
        float[] h = ws.h;
        System.arraycopy(b1, 0, h, 0, hidden);
        for (int j = 0; j < inputDim; j++) {
            float zj = z[j];
            int wo = j * hidden;
            for (int u = 0; u < hidden; u++) {
                h[u] += w1t[wo + u] * zj;
            }
        }
        for (int u = 0; u < hidden; u++) {
            h[u] = h[u] > 0 ? h[u] : 0;
        }
        int nc = labels.length;
        System.arraycopy(b2, 0, out, 0, nc);
        for (int j = 0; j < hidden; j++) {
            float hj = h[j];
            int wo = j * nc;
            for (int c = 0; c < nc; c++) {
                out[c] += w2t[wo + c] * hj;
            }
        }
    }

    private void scoreNonTextWindows(byte[] p, int to, int[] nt, int k, Workspace ws) {
        for (int o = 0; o < nOrders; o++) {
            if (order[o] > asciiOrders) {
                ws.count[o] += scoreNonTextWindows(o, p, to, nt, k, ws.prevStart, ws.acc, ws.max, ws.seen);
            }
        }
    }

    /** Scores each not-yet-scored order-{@code o} window holding one of {@code nt[0, k)}. */
    private int scoreNonTextWindows(int o, byte[] p, int to, int[] nt, int k, int[] prevStart,
                                    float[] acc, float[] mx, long[] seen) {
        int n = order[o];
        int bt = bits[o];
        byte[] e = emb[o];
        float[] sc = rowScale[o];
        int dm = dim[o];
        int ob = base[o];
        int sb = seenBase[o];
        int last = prevStart[o];
        int scored = 0;
        for (int t = 0; t < k; t++) {
            int q = nt[t];
            int s1 = Math.min(q, to - n);
            for (int s = Math.max(last + 1, q - n + 1); s <= s1; s++) {
                accumulate(e, sc, bucket(window(p, s, n), n, bt), dm, acc, mx, ob, seen, sb);
                scored++;
                last = s;
            }
        }
        prevStart[o] = last;
        return scored;
    }

    private int scoreAllWindows(int o, byte[] p, int from, int to, float[] acc, float[] mx,
                                long[] seen) {
        int n = order[o];
        int bt = bits[o];
        byte[] e = emb[o];
        float[] sc = rowScale[o];
        int dm = dim[o];
        int ob = base[o];
        int sb = seenBase[o];
        int scored = 0;
        for (int s = from; s <= to - n; s++) {
            accumulate(e, sc, bucket(window(p, s, n), n, bt), dm, acc, mx, ob, seen, sb);
            scored++;
        }
        return scored;
    }

    /** Bytes {@code p[s, s + n)}, first byte lowest. */
    private static long window(byte[] p, int s, int n) {
        long v = 0;
        for (int k = 0; k < n; k++) {
            v |= ((long) (p[s + k] & 0xFF)) << (8 * k);
        }
        return v;
    }

    /**
     * Adds embedding row {@code row} to the channel's sum and max.  A row already seen cannot
     * raise the max, so with {@code sb >= 0} repeats skip it; the sum still takes every row in order.
     */
    private static void accumulate(byte[] e, float[] sc, int row, int dm, float[] acc, float[] mx,
                                   int ob, long[] seen, int sb) {
        float s = sc[row];
        int eo = row * dm;
        if (sb >= 0) {
            int bit = sb + row;
            long m = 1L << bit;
            if ((seen[bit >>> 6] & m) != 0) {
                for (int j = 0; j < dm; j++) {
                    acc[ob + j] += s * e[eo + j];
                }
                return;
            }
            seen[bit >>> 6] |= m;
        }
        for (int j = 0; j < dm; j++) {
            float x = s * e[eo + j];
            acc[ob + j] += x;
            if (x > mx[ob + j]) {
                mx[ob + j] = x;
            }
        }
    }
}
