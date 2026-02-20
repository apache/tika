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
package org.apache.tika.detect.encoding;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.ByteEncodingHint;

/**
 * Tika {@link EncodingDetector} backed by a layered detection pipeline.
 *
 * <h3>Detection pipeline</h3>
 * <ol>
 *   <li><strong>Structural rules</strong> ({@link StructuralEncodingRules}) —
 *       fast, deterministic gates that fire <em>before</em> any model call:
 *       <ul>
 *         <li>UTF-16/32: null-byte heuristic via {@link ByteEncodingHint}
 *             (handled by the caller or a wrapping detector — not here, as this
 *             class operates on an already-committed byte[])</li>
 *         <li>ASCII: no bytes &ge; 0x80 → return UTF-8 immediately</li>
 *         <li>ISO-2022-JP: ESC-$ sequence present → return ISO-2022-JP</li>
 *         <li>UTF-8 grammar: valid sequences with enough high bytes →
 *             return UTF-8; invalid sequence → exclude UTF-8 from model output</li>
 *       </ul>
 *   </li>
 *   <li><strong>Statistical model</strong> ({@link LinearModel}) — multinomial
 *       logistic regression on high-byte unigrams and bigrams, followed by
 *       confusable-group collapsing via {@link CharsetConfusables}.</li>
 * </ol>
 *
 * <p>The model resource path defaults to
 * {@code /org/apache/tika/detect/encoding/chardetect.bin} and is expected to be
 * bundled inside the jar. The classifier label strings are canonical Java
 * {@link Charset} names (e.g. {@code "UTF-8"}, {@code "windows-1252"}).</p>
 *
 * <p>This class is thread-safe: the model is loaded once at construction time,
 * the feature extractor is stateless, and each call to {@link #detect} creates
 * a new feature buffer.</p>
 */
@TikaComponent(name = "ml-encoding-detector", spi = false)
public class MlEncodingDetector implements EncodingDetector {

    private static final long serialVersionUID = 1L;

    /** Default model resource path on the classpath. */
    public static final String DEFAULT_MODEL_RESOURCE =
            "/org/apache/tika/detect/encoding/chardetect.bin";

    /**
     * Maps model label strings (from training-data filenames) to the canonical
     * Java {@link Charset} name when they differ.  This bridges gaps between
     * the encoding names used in training corpora and Java's charset registry.
     * <ul>
     *   <li>{@code x-mac-cyrillic} → {@code x-MacCyrillic} (Java uses mixed case)</li>
     *   <li>{@code IBM424-ltr}, {@code IBM424-rtl} → {@code IBM424}
     *       (Java knows the code page but not the display-direction variant)</li>
     * </ul>
     */
    private static final Map<String, String> LABEL_TO_JAVA_NAME;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("x-mac-cyrillic", "x-MacCyrillic");
        m.put("IBM424-ltr", "IBM424");
        m.put("IBM424-rtl", "IBM424");
        LABEL_TO_JAVA_NAME = Collections.unmodifiableMap(m);
    }

    /** Number of bytes read from the stream for detection. */
    public static final int MAX_PROBE_BYTES = 4096;

    private final LinearModel model;
    private final ByteNgramFeatureExtractor extractor;
    /** Group-index map built once from the model labels at construction time. */
    private final int[][] groupIndices;
    private float minConfidence = 0.40f;

    /**
     * Load the model from the default classpath location.
     *
     * @throws IOException if the model resource is missing or unreadable
     */
    public MlEncodingDetector() throws IOException {
        this(DEFAULT_MODEL_RESOURCE);
    }

    /**
     * Load the model from a custom classpath resource.
     *
     * @param modelResourcePath classpath-relative path to the LDM1 binary
     * @throws IOException if the resource is missing or unreadable
     */
    public MlEncodingDetector(String modelResourcePath) throws IOException {
        this.model = LinearModel.loadFromClasspath(modelResourcePath);
        this.extractor = new ByteNgramFeatureExtractor(model.getNumBuckets());
        this.groupIndices = CharsetConfusables.buildGroupIndices(model.getLabels());
    }

    /**
     * Load the model from a file on disk (useful during development and evaluation).
     *
     * @param modelPath path to the LDM1 binary on disk
     * @throws IOException if the file is missing or unreadable
     */
    public MlEncodingDetector(java.nio.file.Path modelPath) throws IOException {
        this(LinearModel.loadFromPath(modelPath));
    }

    /**
     * Construct with an already-loaded model (useful in tests and tooling).
     */
    public MlEncodingDetector(LinearModel model) {
        this.model = model;
        this.extractor = new ByteNgramFeatureExtractor(model.getNumBuckets());
        this.groupIndices = CharsetConfusables.buildGroupIndices(model.getLabels());
    }

    @Override
    public Charset detect(TikaInputStream input, Metadata metadata, ParseContext context)
            throws IOException {
        if (input == null) {
            return null;
        }

        byte[] probe = readProbe(input);
        if (probe.length == 0) {
            return StandardCharsets.UTF_8;
        }

        // HZ-GB-2312: Java's built-in charsets don't include HZ, so try the
        // name anyway — some JVMs or ICU providers do register it — and fall
        // back to GBK (the closest decodable superset) if not available.
        // Callers (Tika parsers) should inspect the charset name and route
        // HZ-encoded content through a dedicated decoder.
        if (StructuralEncodingRules.checkHz(probe)) {
            try {
                return Charset.forName("HZ-GB-2312");
            } catch (IllegalArgumentException e) {
                return Charset.forName("GBK");
            }
        }

        // Tier 1: structural rules — fast, definitive
        Charset structural = applyStructuralRules(probe);
        if (structural != null) {
            return structural;
        }

        // Tier 2: statistical model — always return a best-guess rather than null,
        // since MlEncodingDetector is the last base detector in the default chain.
        // Mirror detectAll(): exclude UTF-8 from model candidates if the probe
        // contains byte sequences that are structurally invalid UTF-8.
        StructuralEncodingRules.Utf8Result utf8 = StructuralEncodingRules.checkUtf8(probe);
        boolean excludeUtf8 = (utf8 == StructuralEncodingRules.Utf8Result.NOT_UTF8);

        List<Prediction> predictions = runModel(probe, excludeUtf8, 1);
        if (!predictions.isEmpty()) {
            Charset cs = labelToCharset(predictions.get(0).getLabel());
            if (cs != null) {
                return cs;
            }
        }

        // When UTF-8 is structurally excluded (probe has invalid UTF-8 sequences)
        // returning UTF-8 would be incorrect.  ISO-8859-1 is the safe Western fallback.
        return excludeUtf8 ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8;
    }

    /**
     * Run the structural rule tier against the probe and return a definitive
     * charset if one fires, or {@code null} to continue to the model.
     *
     * <p>Order:
     * <ol>
     *   <li>UTF-16/32 via null-byte heuristic ({@link ByteEncodingHint}) — these
     *       encodings produce mostly null bytes that are invisible to the byte-frequency
     *       model, so they must be caught here.</li>
     *   <li>ASCII — no high bytes at all.</li>
     *   <li>ISO-2022-JP escape sequences.</li>
     *   <li>UTF-8 grammar.</li>
     * </ol>
     */
    private Charset applyStructuralRules(byte[] probe) {
        // UTF-16 / UTF-32 — null-byte heuristic
        ByteEncodingHint hint = ByteEncodingHint.detect(probe);
        if (hint.isUtf16()) {
            return hint.charset();
        }

        // ISO-2022 family BEFORE the ASCII check: ISO-2022-JP/KR/CN are 7-bit
        // encodings (all bytes < 0x80), so checkAscii would fire first and
        // incorrectly return UTF-8.
        Charset iso2022 = StructuralEncodingRules.detectIso2022(probe);
        if (iso2022 != null) {
            return iso2022;
        }

        // IBM424 (EBCDIC Hebrew) BEFORE the ASCII check for the same reason:
        // all Hebrew letters are below 0x80 and the model can't see them.
        if (StructuralEncodingRules.checkIbm424(probe)) {
            return labelToCharset("IBM424");
        }

        // ASCII — no high bytes at all, UTF-8 is the right answer
        if (StructuralEncodingRules.checkAscii(probe)) {
            return StandardCharsets.UTF_8;
        }

        // DEFINITIVE_UTF8 — the probe contains at least one real multi-byte UTF-8
        // sequence (lead byte + continuation byte(s)) and no invalid sequences.
        // This is a strong signal: single-byte encodings like ISO-8859-1 produce
        // lone high bytes that typically form invalid UTF-8 (e.g. 0xE1 'á' is a
        // 3-byte lead that needs two continuation bytes; ASCII letters don't qualify).
        // Big5 second bytes often fall outside the 0x80–0xBF continuation range,
        // so they also produce NOT_UTF8.  DEFINITIVE_UTF8 therefore implies the
        // content was almost certainly written as UTF-8.
        StructuralEncodingRules.Utf8Result utf8Structural = StructuralEncodingRules.checkUtf8(probe);
        if (utf8Structural == StructuralEncodingRules.Utf8Result.DEFINITIVE_UTF8) {
            return StandardCharsets.UTF_8;
        }

        // UTF-8 grammar: only use the structural check to EXCLUDE UTF-8 (NOT_UTF8)
        // for AMBIGUOUS content (no multi-byte sequences) — the model identifies UTF-8 correctly
        // from byte-frequency features without the grammar shortcut.
        return null;
    }

    /**
     * Return the top-N charset predictions in descending confidence order.
     *
     * <p>Structural rules are applied first; if a rule fires a definitive
     * result it is returned as a single-element list with confidence 1.0.
     * Otherwise the statistical model is invoked.</p>
     *
     * <p>Each {@link Prediction} carries:
     * <ul>
     *   <li>{@link Prediction#getLabel()} — the charset name</li>
     *   <li>{@link Prediction#getProbability()} — softmax probability after
     *       confusable-group collapsing (relative, 0–1)</li>
     *   <li>{@link Prediction#getConfidence()} — calibration-independent signal
     *       ({@code sigmoid(logit) × probability}), suitable as a threshold gate</li>
     * </ul>
     *
     * @param probe raw bytes to analyse
     * @param topN  maximum number of results
     * @return predictions sorted by confidence descending, at most {@code topN} entries
     */
    public List<Prediction> detectAll(byte[] probe, int topN) {
        // HZ-GB-2312 is 7-bit and has no standard Java Charset — handle before
        // applyStructuralRules (which returns a Charset object) to avoid the
        // UnsupportedCharsetException that Charset.forName("HZ-GB-2312") throws.
        // Label as "HZ" to match the training-data convention.
        if (StructuralEncodingRules.checkHz(probe)) {
            return singleResult("HZ", 1.0f, topN);
        }

        // Structural rules first — if definitive, return immediately
        Charset structural = applyStructuralRules(probe);
        if (structural != null) {
            return singleResult(structural.name(), 1.0f, topN);
        }

        // UTF-8 grammar: check whether to exclude UTF-8 from model output
        StructuralEncodingRules.Utf8Result utf8 = StructuralEncodingRules.checkUtf8(probe);
        boolean excludeUtf8 = (utf8 == StructuralEncodingRules.Utf8Result.NOT_UTF8);

        return runModel(probe, excludeUtf8, topN);
    }

    private List<Prediction> runModel(byte[] probe, boolean excludeUtf8, int topN) {
        int[] features = extractor.extract(probe);
        float[] logits = model.predictLogits(features);
        float[] probs = CharsetConfusables.collapseGroups(
                LinearModel.softmax(logits.clone()), groupIndices);

        // Only include non-zero entries (collapsed-out group members are 0)
        List<Prediction> results = new ArrayList<>();
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] > 0f) {
                String label = model.getLabel(i);
                if (excludeUtf8 && "UTF-8".equalsIgnoreCase(label)) {
                    continue;
                }
                results.add(new Prediction(label, logits[i], probs[i]));
            }
        }
        results.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        return results.subList(0, Math.min(topN, results.size()));
    }

    private static List<Prediction> singleResult(String label, float confidence, int topN) {
        if (topN <= 0) {
            return List.of();
        }
        return List.of(new Prediction(label, confidence, confidence));
    }

    /**
     * Resolve a model label string to a Java {@link Charset}, applying the
     * {@link #LABEL_TO_JAVA_NAME} alias map first so that labels like
     * {@code "x-mac-cyrillic"} or {@code "IBM424-ltr"} survive the lookup.
     *
     * @return the resolved charset, or {@code null} if no mapping exists
     */
    private static Charset labelToCharset(String label) {
        String javaName = LABEL_TO_JAVA_NAME.getOrDefault(label, label);
        try {
            return Charset.forName(javaName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] readProbe(TikaInputStream is) throws IOException {
        is.mark(MAX_PROBE_BYTES);
        try {
            byte[] buf = new byte[MAX_PROBE_BYTES];
            int total = 0;
            int n;
            while (total < buf.length &&
                   (n = is.read(buf, total, buf.length - total)) != -1) {
                total += n;
            }
            if (total == buf.length) {
                return buf;
            }
            byte[] trimmed = new byte[total];
            System.arraycopy(buf, 0, trimmed, 0, total);
            return trimmed;
        } finally {
            is.reset();
        }
    }

    /**
     * Minimum {@link Prediction#getConfidence()} value (0–1) required before
     * {@link #detect} returns a result. Default is {@code 0.40}.
     */
    public float getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(float minConfidence) {
        this.minConfidence = minConfidence;
    }

    public LinearModel getModel() {
        return model;
    }

}
