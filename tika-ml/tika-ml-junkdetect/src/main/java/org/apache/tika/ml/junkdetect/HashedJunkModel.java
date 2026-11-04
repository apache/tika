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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Hashed codepoint n-gram text-quality model with two heads over one feature extractor.
 *
 * <ul>
 *   <li><b>Pairwise</b> ({@link #compare}, {@link #pairwiseLogit}): which of two decodes of the
 *       same bytes is the real text.  Only differences between texts are meaningful.</li>
 *   <li><b>Pointwise</b> ({@link #score}): an absolute languageness logit, positive = clean,
 *       plus the worst 200-codepoint window.</li>
 * </ul>
 *
 * <p>Features: {@link #normalize} the text, map whitespace to one space and digits to
 * {@code 0}, pad with a space at each end, then hash every codepoint 1..maxN-gram into
 * {@code 2^log2Size} buckets (a unigram that is the space is skipped).  The hash constants are
 * part of the format; changing any step needs a new format version.
 *
 * <p>File format (gzip, big-endian): magic {@code JUNKHNG1}, version byte, log2Size byte,
 * maxN byte; pairwise scale (float), {@code 2^log2Size} int8 weights, deferral threshold
 * (float); pointwise scale (float), bias (float), {@code 2^log2Size} int8 weights; trained
 * script count (int) and {@link Character.UnicodeScript} names (modified UTF-8).  No other
 * format is read.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class HashedJunkModel {

    static final byte[] MAGIC = "JUNKHNG1".getBytes(StandardCharsets.US_ASCII);
    static final int VERSION = 1;
    static final int MIN_LOG2_SIZE = 16;
    static final int MAX_LOG2_SIZE = 24;
    static final int MIN_N = 1;
    static final int MAX_N = 4;
    static final int WINDOW = 200;
    static final int WINDOW_STEP = 100;
    static final int WINDOW_MIN_LETTERS = 20;

    private static final long SEED = 0x9E3779B97F4A7C15L;
    private static final long MIX = 0xBF58476D1CE4E5B9L;
    private static final long FINAL = 0x94D049BB133111EBL;

    private final int log2Size;
    private final int mask;
    private final int maxN;
    private final float pairScale;
    private final byte[] pairWeights;
    private final float deferThreshold;
    private final float pointScale;
    private final float pointBias;
    private final byte[] pointWeights;
    private final Set<Character.UnicodeScript> scripts;

    private HashedJunkModel(int log2Size, int maxN, float pairScale, byte[] pairWeights,
                            float deferThreshold, float pointScale, float pointBias,
                            byte[] pointWeights, Set<Character.UnicodeScript> scripts) {
        this.log2Size = log2Size;
        this.mask = (1 << log2Size) - 1;
        this.maxN = maxN;
        this.pairScale = pairScale;
        this.pairWeights = pairWeights;
        this.deferThreshold = deferThreshold;
        this.pointScale = pointScale;
        this.pointBias = pointBias;
        this.pointWeights = pointWeights;
        this.scripts = scripts;
    }

    /**
     * Builds a model from trained, quantized weights.  The arrays are copied.
     *
     * @throws IllegalArgumentException if any value is outside what the format allows
     */
    public static HashedJunkModel create(int log2Size, int maxN, float pairScale,
                                         byte[] pairWeights, float deferThreshold,
                                         float pointScale, float pointBias,
                                         byte[] pointWeights,
                                         Collection<Character.UnicodeScript> trainedScripts) {
        String err = validate(log2Size, maxN, pairScale, deferThreshold, pointScale, pointBias);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        int size = 1 << log2Size;
        if (pairWeights.length != size || pointWeights.length != size) {
            throw new IllegalArgumentException("weight arrays must have length " + size);
        }
        Set<Character.UnicodeScript> s = EnumSet.noneOf(Character.UnicodeScript.class);
        s.addAll(trainedScripts);
        return new HashedJunkModel(log2Size, maxN, pairScale, pairWeights.clone(),
                deferThreshold, pointScale, pointBias, pointWeights.clone(),
                Collections.unmodifiableSet(s));
    }

    private static String validate(int log2Size, int maxN, float pairScale,
                                   float deferThreshold, float pointScale, float pointBias) {
        if (log2Size < MIN_LOG2_SIZE || log2Size > MAX_LOG2_SIZE) {
            return "log2Size " + log2Size + " not in [" + MIN_LOG2_SIZE + ", " + MAX_LOG2_SIZE + "]";
        }
        if (maxN < MIN_N || maxN > MAX_N) {
            return "maxN " + maxN + " not in [" + MIN_N + ", " + MAX_N + "]";
        }
        if (!(pairScale > 0) || Float.isInfinite(pairScale)
                || !(pointScale > 0) || Float.isInfinite(pointScale)) {
            return "scales must be finite and positive";
        }
        if (!(deferThreshold >= 0) || Float.isInfinite(deferThreshold)) {
            return "deferral threshold must be finite and non-negative";
        }
        if (!Float.isFinite(pointBias)) {
            return "pointwise bias must be finite";
        }
        return null;
    }

    public static HashedJunkModel load(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        }
    }

    /** Reads a model; rejects anything but this exact format, including trailing bytes. */
    public static HashedJunkModel load(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(raw), 1 << 16));
        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("not a hashed junk model (bad magic)");
        }
        int version = in.readUnsignedByte();
        if (version != VERSION) {
            throw new IOException("unsupported hashed junk model version " + version);
        }
        int log2Size = in.readUnsignedByte();
        int maxN = in.readUnsignedByte();
        if (log2Size < MIN_LOG2_SIZE || log2Size > MAX_LOG2_SIZE) {
            throw new IOException("log2Size " + log2Size + " out of range");
        }
        float pairScale = in.readFloat();
        byte[] pair = new byte[1 << log2Size];
        in.readFully(pair);
        float defer = in.readFloat();
        float pointScale = in.readFloat();
        float bias = in.readFloat();
        byte[] point = new byte[1 << log2Size];
        in.readFully(point);
        int nScripts = in.readInt();
        if (nScripts < 0 || nScripts > Character.UnicodeScript.values().length) {
            throw new IOException("bad trained-script count " + nScripts);
        }
        Set<Character.UnicodeScript> s = EnumSet.noneOf(Character.UnicodeScript.class);
        for (int i = 0; i < nScripts; i++) {
            String name = in.readUTF();
            try {
                s.add(Character.UnicodeScript.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new IOException("unknown script " + name, e);
            }
        }
        if (in.read() != -1) {
            throw new IOException("trailing bytes after hashed junk model");
        }
        String err = validate(log2Size, maxN, pairScale, defer, pointScale, bias);
        if (err != null) {
            throw new IOException(err);
        }
        return new HashedJunkModel(log2Size, maxN, pairScale, pair, defer, pointScale, bias,
                point, Collections.unmodifiableSet(s));
    }

    /** Writes this model in the format {@link #load(InputStream)} reads.  Does not close. */
    public void write(OutputStream raw) throws IOException {
        GZIPOutputStream gz = new GZIPOutputStream(raw, 1 << 16);
        DataOutputStream out = new DataOutputStream(gz);
        out.write(MAGIC);
        out.writeByte(VERSION);
        out.writeByte(log2Size);
        out.writeByte(maxN);
        out.writeFloat(pairScale);
        out.write(pairWeights);
        out.writeFloat(deferThreshold);
        out.writeFloat(pointScale);
        out.writeFloat(pointBias);
        out.write(pointWeights);
        out.writeInt(scripts.size());
        for (Character.UnicodeScript s : scripts) {
            out.writeUTF(s.name());
        }
        out.flush();
        gz.finish();
    }

    /**
     * The normalization shared by the corpus builder, the trainer and the scorer: fullwidth
     * ASCII to ASCII, halfwidth katakana/hangul runs through NFKC (which composes voiced
     * marks), soft hyphens removed, then NFC.  Other width and compatibility forms such as
     * {@code ￥} are kept.  Idempotent.
     */
    public static String normalize(String text) {
        return Normalizer.normalize(fold(text), Normalizer.Form.NFC);
    }

    private static String fold(String t) {
        int first = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '­' || (c >= '！' && c <= 'ￜ')) {
                first = i;
                break;
            }
        }
        if (first < 0) {
            return t;
        }
        StringBuilder sb = new StringBuilder(t.length());
        sb.append(t, 0, first);
        for (int i = first; i < t.length(); ) {
            char c = t.charAt(i);
            if (c == '­') {
                i++;
            } else if (c >= '！' && c <= '～') {
                sb.append((char) (c - 0xFEE0));
                i++;
            } else if (c >= '｡' && c <= 'ￜ') {
                int j = i;
                while (j < t.length() && t.charAt(j) >= '｡' && t.charAt(j) <= 'ￜ') {
                    j++;
                }
                sb.append(Normalizer.normalize(t.substring(i, j), Normalizer.Form.NFKC));
                i = j;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** Normalized, space-folded, digit-folded, space-padded codepoints; letters flagged. */
    private static final class Units {
        final int[] cps;
        final boolean[] letter;
        final int n;

        Units(String text) {
            String t = normalize(text);
            int len = t.length();
            int[] c = new int[len + 2];
            boolean[] l = new boolean[len + 2];
            int k = 0;
            c[k++] = ' ';
            for (int i = 0; i < len; ) {
                int cp = t.codePointAt(i);
                i += Character.charCount(cp);
                boolean isLetter = Character.isLetter(cp);
                if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                    cp = ' ';
                } else if (Character.isDigit(cp)) {
                    cp = '0';
                }
                if (cp == ' ' && c[k - 1] == ' ') {
                    continue;
                }
                l[k] = isLetter;
                c[k++] = cp;
            }
            if (c[k - 1] != ' ') {
                c[k++] = ' ';
            }
            this.cps = c;
            this.letter = l;
            this.n = k;
        }
    }

    private void forEachBucket(Units u, BucketSink sink) {
        forEachBucket(u, mask, maxN, sink);
    }

    /** Calls {@code sink} with the bucket of every n-gram starting at each position, in order. */
    private static void forEachBucket(Units u, int mask, int maxN, BucketSink sink) {
        int[] cps = u.cps;
        int n = u.n;
        for (int i = 0; i < n; i++) {
            long h = SEED;
            for (int k = 0; k < maxN && i + k < n; k++) {
                h = (h ^ cps[i + k]) * MIX;
                h ^= h >>> 31;
                if (k == 0 && cps[i] == ' ') {
                    continue;
                }
                sink.accept(i, (int) (((h ^ (k + 1)) * FINAL) >>> 40) & mask);
            }
        }
    }

    @FunctionalInterface
    private interface BucketSink {
        void accept(int position, int bucket);
    }

    int[] buckets(String text) {
        return featureBuckets(text, log2Size, maxN);
    }

    /**
     * Sorted bucket ids of every n-gram in {@code text}, with repeats: the features a model of
     * this size and n-gram order sees.  For trainers; a model's weights index these buckets.
     */
    public static int[] featureBuckets(String text, int log2Size, int maxN) {
        if (log2Size < MIN_LOG2_SIZE || log2Size > MAX_LOG2_SIZE || maxN < MIN_N || maxN > MAX_N) {
            throw new IllegalArgumentException("log2Size " + log2Size + " / maxN " + maxN);
        }
        Units u = new Units(text);
        int[] out = new int[Math.max(1, u.n * maxN)];
        int[] size = new int[1];
        forEachBucket(u, (1 << log2Size) - 1, maxN, (pos, b) -> out[size[0]++] = b);
        int[] r = Arrays.copyOf(out, size[0]);
        Arrays.sort(r);
        return r;
    }

    private long pairSum(String text) {
        long[] sum = new long[1];
        forEachBucket(new Units(text), (pos, b) -> sum[0] += pairWeights[b]);
        return sum[0];
    }

    /**
     * Pairwise value of one text: the scaled sum of its n-gram weights.  Only differences between
     * decodes of the same bytes mean anything; {@link #compare} is the difference of two values.
     */
    public double pairValue(String text) {
        return pairScale * (double) pairSum(text);
    }

    /**
     * Pairwise value of {@code a} minus that of {@code b}: positive when {@code a} looks like
     * the real text.  Exactly antisymmetric; 0 for identical texts.  Not a probability.
     */
    public double compare(String a, String b) {
        return pairScale * (double) (pairSum(a) - pairSum(b));
    }

    /**
     * The trained pairwise logit: {@link #compare} divided by the L1 norm of the two texts'
     * bucket-count difference.  0 when the texts hash to the same bag of buckets.
     */
    public double pairwiseLogit(String a, String b) {
        int[] x = buckets(a);
        int[] y = buckets(b);
        long dot = 0;
        long l1 = 0;
        int i = 0;
        int j = 0;
        while (i < x.length || j < y.length) {
            int bx = i < x.length ? x[i] : Integer.MAX_VALUE;
            int by = j < y.length ? y[j] : Integer.MAX_VALUE;
            int bucket = Math.min(bx, by);
            long d = 0;
            while (i < x.length && x[i] == bucket) {
                d++;
                i++;
            }
            while (j < y.length && y[j] == bucket) {
                d--;
                j++;
            }
            dot += d * pairWeights[bucket];
            l1 += Math.abs(d);
        }
        return l1 == 0 ? 0 : pairScale * (double) dot / l1;
    }

    /** Pairwise-logit margin below which the junk filter defers to a declared anchor. */
    public float getDeferThreshold() {
        return deferThreshold;
    }

    public int getMaxN() {
        return maxN;
    }

    public int getLog2Size() {
        return log2Size;
    }

    public Set<Character.UnicodeScript> getTrainedScripts() {
        return scripts;
    }

    /** Pointwise score of {@code text}; never null, finite logit for any input. */
    public Score score(String text) {
        Units u = new Units(text);
        int n = u.n;
        long[] cs = new long[n + 1];
        int[] cc = new int[n + 1];
        int[] cl = new int[n + 1];
        forEachBucket(u, (pos, b) -> {
            cs[pos + 1] += pointWeights[b];
            cc[pos + 1]++;
        });
        for (int i = 0; i < n; i++) {
            cs[i + 1] += cs[i];
            cc[i + 1] += cc[i];
            cl[i + 1] = cl[i] + (u.letter[i] ? 1 : 0);
        }
        double logit = cc[n] == 0 ? pointBias : pointBias + pointScale * (double) cs[n] / cc[n];
        double min = Double.NaN;
        int windows = 0;
        int below = 0;
        for (int st = 0; st < n; st += WINDOW_STEP) {
            int en = Math.min(n, st + WINDOW);
            if (cl[en] - cl[st] >= WINDOW_MIN_LETTERS && cc[en] > cc[st]) {
                double v = pointBias + pointScale * (double) (cs[en] - cs[st]) / (cc[en] - cc[st]);
                windows++;
                if (v < 0) {
                    below++;
                }
                min = Double.isNaN(min) ? v : Math.min(min, v);
            }
            if (en == n) {
                break;
            }
        }
        Map<Character.UnicodeScript, int[]> perScript = new EnumMap<>(Character.UnicodeScript.class);
        int letters = 0;
        int covered = 0;
        for (int i = 0; i < n; i++) {
            if (!u.letter[i]) {
                continue;
            }
            Character.UnicodeScript s = Character.UnicodeScript.of(u.cps[i]);
            if (s == Character.UnicodeScript.COMMON || s == Character.UnicodeScript.INHERITED
                    || s == Character.UnicodeScript.UNKNOWN) {
                continue;
            }
            letters++;
            if (scripts.contains(s)) {
                covered++;
            }
            perScript.computeIfAbsent(s, k -> new int[1])[0]++;
        }
        Character.UnicodeScript dominant = null;
        int best = 0;
        for (Map.Entry<Character.UnicodeScript, int[]> e : perScript.entrySet()) {
            if (e.getValue()[0] > best) {
                best = e.getValue()[0];
                dominant = e.getKey();
            }
        }
        return new Score(logit, min, windows == 0 ? Double.NaN : below / (double) windows,
                letters == 0 ? Double.NaN : covered / (double) letters, dominant);
    }

    /** Result of {@link #score}. */
    public static final class Score {
        private final double logit;
        private final double windowMin;
        private final double windowFracBelow0;
        private final double coverage;
        private final Character.UnicodeScript dominantScript;

        Score(double logit, double windowMin, double windowFracBelow0, double coverage,
              Character.UnicodeScript dominantScript) {
            this.logit = logit;
            this.windowMin = windowMin;
            this.windowFracBelow0 = windowFracBelow0;
            this.coverage = coverage;
            this.dominantScript = dominantScript;
        }

        /** Pointwise logit over the whole text; positive = clean. */
        public double getLogit() {
            return logit;
        }

        /**
         * Lowest logit over windows of 200 feature codepoints (step 100) holding at least 20
         * letters; NaN when there is none.
         */
        public double getWindowMin() {
            return windowMin;
        }

        /** Share of qualifying windows scoring below 0; NaN when there is none. */
        public double getWindowFracBelow0() {
            return windowFracBelow0;
        }

        /** Share of script-bearing letters whose script was trained; NaN with no such letter. */
        public double getCoverage() {
            return coverage;
        }

        /** Script with the most letters, or null with no script-bearing letter. */
        public Character.UnicodeScript getDominantScript() {
            return dominantScript;
        }
    }
}
