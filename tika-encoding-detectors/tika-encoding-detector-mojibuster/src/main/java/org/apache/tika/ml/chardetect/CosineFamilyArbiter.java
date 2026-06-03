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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.detect.EncodingResult;

/**
 * Family-level guard over the NB statistical pick, defending against the
 * single-byte-&rarr;CJK collision (Cyrillic / Greek / accented-Latin content
 * whose high bytes coincide with legal GBK lead/trail pairs and accumulate
 * spurious GB18030 / Big5 likelihood under the multinomial NB).
 *
 * <p>Two complementary, model-light signals, both blind to NB:</p>
 * <ul>
 *   <li><b>high-byte cosine</b> &mdash; cosine between the probe's high-byte
 *       (&ge; 0x80) byte-bigram occupancy and each class's control-stripped
 *       high-byte profile.  Direction-based, so length/density-invariant; the
 *       ASCII quadrant is dropped so shared English text can't dominate.  When
 *       NB picks a CJK class but the cosine argmax is non-CJK (with enough
 *       high-byte evidence), the CJK pick is vetoed.</li>
 *   <li><b>GBK illegality</b> &mdash; fraction of high-byte lead bytes that do
 *       not begin a valid GBK 2-byte or GB18030 4-byte sequence.  A genuine
 *       GB18030 document is ~0% illegal; Cyrillic/Greek forced through GBK
 *       throws illegal trails.  Scoped to GB18030 only (it says nothing about
 *       Shift_JIS/EUC).</li>
 * </ul>
 *
 * <p>On veto the CJK pick is replaced by the best non-CJK candidate (by cosine
 * when evidence is sufficient, else the highest-ranked non-CJK NB candidate);
 * real-CJK picks are left untouched (cosine argmax stays CJK, illegality ~0),
 * so the guard is regression-safe for genuine CJK.</p>
 */
public final class CosineFamilyArbiter {

    /** Minimum high-byte bigram count before the cosine veto is trusted. */
    public static final int MIN_HIGH_BYTE_SUPPORT = 15;

    /** GBK-illegality fraction above which a GB18030 pick is refuted. */
    public static final double GBK_ILLEGAL_THRESHOLD = 0.02;

    private static final String GB18030 = "GB18030";

    private final String[] names;
    private final boolean[] cjk;
    private final Charset[] charsets;   // resolved JVM charset, null if unsupported
    private final int[][] bigramIds;
    private final float[][] weights;   // L2-normalized per class

    public CosineFamilyArbiter(InputStream in) throws IOException {
        try (DataInputStream dis = new DataInputStream(in)) {
            int nc = dis.readInt();
            names = new String[nc];
            cjk = new boolean[nc];
            charsets = new Charset[nc];
            bigramIds = new int[nc][];
            weights = new float[nc][];
            for (int c = 0; c < nc; c++) {
                names[c] = dis.readUTF();
                cjk[c] = isCjkName(names[c]);
                charsets[c] = resolve(names[c]);
                int nnz = dis.readInt();
                int[] ids = new int[nnz];
                float[] w = new float[nnz];
                for (int k = 0; k < nnz; k++) {
                    ids[k] = dis.readUnsignedShort();
                    w[k] = dis.readFloat();
                }
                bigramIds[c] = ids;
                weights[c] = w;
            }
        }
    }

    private static Charset resolve(String name) {
        try {
            return Charset.isSupported(name) ? Charset.forName(name) : null;
        } catch (IllegalCharsetNameException e) {
            return null;
        }
    }

    static boolean isCjkName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("gb") || n.contains("big5") || n.contains("euc")
                || n.contains("shift") || n.contains("jis") || n.contains("2022")
                || n.contains("949");
    }

    /**
     * Apply the family guard to NB's ranked candidates.  Returns {@code
     * nbResults} unchanged unless NB's top pick is CJK and a veto fires, in
     * which case a non-CJK replacement is promoted to the front.
     */
    public List<EncodingResult> arbitrate(byte[] probe, List<EncodingResult> nbResults) {
        if (nbResults == null || nbResults.isEmpty()) {
            return nbResults;
        }
        if (!isCjkName(nbResults.get(0).getCharset().name())) {
            return nbResults;
        }
        // Build high-byte bigram occupancy.
        Map<Integer, Integer> docMap = new HashMap<>();
        long support = 0;
        for (int i = 0; i + 1 < probe.length; i++) {
            int b0 = probe[i] & 0xFF;
            int b1 = probe[i + 1] & 0xFF;
            if (b0 >= 0x80 || b1 >= 0x80) {
                int bg = (b0 << 8) | b1;
                docMap.merge(bg, 1, Integer::sum);
                support++;
            }
        }
        double docNorm = 0;
        for (int v : docMap.values()) {
            docNorm += (double) v * v;
        }
        docNorm = Math.sqrt(docNorm);

        boolean gbkTop = GB18030.equals(nbResults.get(0).getCharset().name());
        double illegal = gbkIllegalRate(probe);

        int cosArg = -1;
        double bestCos = -1;
        double[] cos = new double[names.length];
        if (docNorm > 0) {
            for (int c = 0; c < names.length; c++) {
                double dot = 0;
                int[] ids = bigramIds[c];
                float[] w = weights[c];
                for (int k = 0; k < ids.length; k++) {
                    Integer dc = docMap.get(ids[k]);
                    if (dc != null) {
                        dot += w[k] * dc;
                    }
                }
                cos[c] = dot / docNorm;
                if (cos[c] > bestCos) {
                    bestCos = cos[c];
                    cosArg = c;
                }
            }
        }

        boolean veto = (gbkTop && illegal > GBK_ILLEGAL_THRESHOLD)
                || (support >= MIN_HIGH_BYTE_SUPPORT && cosArg >= 0 && !cjk[cosArg]);
        if (!veto) {
            return nbResults;
        }

        // Choose replacement: best non-CJK by cosine when evidence is
        // sufficient, else the highest-ranked non-CJK NB candidate.
        Charset replacement = null;
        if (support >= MIN_HIGH_BYTE_SUPPORT && docNorm > 0) {
            double bv = -1;
            for (int c = 0; c < names.length; c++) {
                if (!cjk[c] && charsets[c] != null && cos[c] > bv) {
                    bv = cos[c];
                    replacement = charsets[c];
                }
            }
        }
        float conf = nbResults.get(0).getConfidence();
        List<EncodingResult> out = new ArrayList<>(nbResults.size() + 1);
        if (replacement != null) {
            out.add(new EncodingResult(replacement, conf, replacement.name(),
                    EncodingResult.ResultType.STATISTICAL));
        }
        for (EncodingResult r : nbResults) {
            if (isCjkName(r.getCharset().name())) {
                continue;
            }
            if (replacement != null && r.getCharset().name().equals(replacement.name())) {
                continue;
            }
            out.add(r);
        }
        // If we couldn't form any non-CJK candidate, don't strand the caller
        // with an empty list — leave NB's result untouched.
        return out.isEmpty() ? nbResults : out;
    }

    /**
     * Fraction of high-byte lead bytes that fail to begin a valid GBK 2-byte
     * or GB18030 4-byte sequence.  0 for genuine GB18030.
     */
    static double gbkIllegalRate(byte[] b) {
        int n = b.length;
        int i = 0;
        int illegal = 0;
        int lead = 0;
        while (i < n) {
            int c = b[i] & 0xFF;
            if (c < 0x80) {
                i++;
                continue;
            }
            lead++;
            if (c >= 0x81 && c <= 0xFE && i + 1 < n) {
                int t = b[i + 1] & 0xFF;
                if (((t >= 0x40 && t <= 0x7E) || (t >= 0x80 && t <= 0xFE)) && t != 0x7F) {
                    i += 2;
                    continue;
                }
                if (t >= 0x30 && t <= 0x39 && i + 3 < n
                        && (b[i + 2] & 0xFF) >= 0x81 && (b[i + 2] & 0xFF) <= 0xFE
                        && (b[i + 3] & 0xFF) >= 0x30 && (b[i + 3] & 0xFF) <= 0x39) {
                    i += 4;
                    continue;
                }
            }
            illegal++;
            i++;
        }
        return lead == 0 ? 0 : (double) illegal / lead;
    }
}
