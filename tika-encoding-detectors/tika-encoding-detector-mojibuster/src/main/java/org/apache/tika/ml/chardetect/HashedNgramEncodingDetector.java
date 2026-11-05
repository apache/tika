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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.detect.EncodingResult;

/**
 * Statistical stage of {@link MojibusterEncodingDetector}: runs the bundled
 * {@link HashedNgramModel} and turns its logits into margin-gated
 * {@link EncodingResult.ResultType#STATISTICAL} candidates.
 *
 * <p>Confidence is not softmax (it saturates on out-of-distribution input):
 * top-1 gets 1.0, and a runner-up within {@link #MARGIN_LOGITS} of it gets
 * {@code 1 - gap / MARGIN_LOGITS}; anything further away is not emitted.</p>
 *
 * <p>The {@code binary} class is not a charset.  When it is the top class no
 * candidate is emitted; otherwise it is skipped.</p>
 */
public final class HashedNgramEncodingDetector {

    public static final String DEFAULT_MODEL_RESOURCE =
            "/org/apache/tika/ml/chardetect/hashed-ngram.hng";

    /** Logit gap at which a runner-up stops being a candidate; calibrated on the full chain (gold v1.12 train odd half). */
    public static final float MARGIN_LOGITS = 1.0f;

    public static final int DEFAULT_TOP_K = 5;

    static final String BINARY_LABEL = "binary";

    private final HashedNgramModel model;
    /** Distinct charsets the model can emit, in first-label order. */
    private final Charset[] charsets;
    private final String[] labels;
    /** Model class index to {@link #charsets} index, or -1 for {@code binary}. */
    private final int[] classToCharset;
    private final ThreadLocal<Scratch> scratch;

    private static final class Scratch {
        final float[] logits;
        final float[] best;
        final int[] bestClass;

        Scratch(int nClasses, int nCharsets) {
            logits = new float[nClasses];
            best = new float[nCharsets];
            bestClass = new int[nCharsets];
        }
    }

    public HashedNgramEncodingDetector() throws IOException {
        this(loadDefaultModel());
    }

    public HashedNgramEncodingDetector(HashedNgramModel model) throws IOException {
        this.model = model;
        labels = model.getLabels();
        classToCharset = new int[labels.length];
        Map<String, Integer> byName = new LinkedHashMap<>();
        List<Charset> cs = new ArrayList<>();
        int binary = 0;
        for (int c = 0; c < labels.length; c++) {
            if (BINARY_LABEL.equals(labels[c])) {
                classToCharset[c] = -1;
                binary++;
                continue;
            }
            Charset charset;
            try {
                charset = Charset.forName(toJavaCharsetName(labels[c]));
            } catch (IllegalArgumentException e) {
                throw new IOException("model label \"" + labels[c] + "\" is not a charset", e);
            }
            Integer idx = byName.get(charset.name());
            if (idx == null) {
                idx = cs.size();
                byName.put(charset.name(), idx);
                cs.add(charset);
            }
            classToCharset[c] = idx;
        }
        if (binary > 1) {
            throw new IOException("model has more than one binary class");
        }
        charsets = cs.toArray(new Charset[0]);
        int nClasses = labels.length;
        int nCharsets = charsets.length;
        scratch = ThreadLocal.withInitial(() -> new Scratch(nClasses, nCharsets));
    }

    public static HashedNgramModel loadDefaultModel() throws IOException {
        InputStream in = HashedNgramEncodingDetector.class.getResourceAsStream(DEFAULT_MODEL_RESOURCE);
        if (in == null) {
            throw new IOException("charset model not found on classpath at " + DEFAULT_MODEL_RESOURCE);
        }
        try (InputStream stream = in) {
            return new HashedNgramModel(stream);
        }
    }

    public HashedNgramModel getModel() {
        return model;
    }

    /**
     * Ranked candidates for {@code probe}; empty when the model abstains (no
     * non-text byte) or its top class is {@code binary}.
     */
    public List<EncodingResult> detect(byte[] probe) {
        if (probe == null || probe.length == 0) {
            return Collections.emptyList();
        }
        Scratch s = scratch.get();
        float[] logits = s.logits;
        if (!model.logits(probe, 0, probe.length, logits)) {
            return Collections.emptyList();
        }
        int top = 0;
        for (int c = 1; c < logits.length; c++) {
            if (logits[c] > logits[top]) {
                top = c;
            }
        }
        if (classToCharset[top] < 0) {
            return Collections.emptyList();
        }
        // Merged labels (e.g. IBM420-ltr / -rtl) score, and are labelled, as their best member.
        float[] best = s.best;
        int[] bestClass = s.bestClass;
        java.util.Arrays.fill(best, Float.NEGATIVE_INFINITY);
        for (int c = 0; c < logits.length; c++) {
            int k = classToCharset[c];
            if (k >= 0 && logits[c] > best[k]) {
                best[k] = logits[c];
                bestClass[k] = c;
            }
        }
        float top1 = logits[top];
        List<EncodingResult> out = new ArrayList<>(DEFAULT_TOP_K);
        for (int rank = 0; rank < DEFAULT_TOP_K; rank++) {
            int pick = -1;
            for (int k = 0; k < best.length; k++) {
                if (best[k] != Float.NEGATIVE_INFINITY && (pick < 0 || best[k] > best[pick])) {
                    pick = k;
                }
            }
            if (pick < 0) {
                break;
            }
            float gap = top1 - best[pick];
            if (rank > 0 && gap >= MARGIN_LOGITS) {
                break;
            }
            best[pick] = Float.NEGATIVE_INFINITY;
            float conf = rank == 0 ? 1f : 1f - gap / MARGIN_LOGITS;
            out.add(new EncodingResult(charsets[pick], conf, labels[bestClass[pick]],
                    EncodingResult.ResultType.STATISTICAL));
        }
        return out;
    }

    /** Training labels that are not Java charset names. */
    static String toJavaCharsetName(String label) {
        switch (label) {
            case "IBM420-ltr":
            case "IBM420-rtl":
                return "IBM420";
            case "IBM424-ltr":
            case "IBM424-rtl":
                return "IBM424";
            case "x-mac-cyrillic":
                return "x-MacCyrillic";
            case "windows-874":
                return "x-windows-874";
            default:
                return label;
        }
    }
}
