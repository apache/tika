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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.LinearModel;
import org.apache.tika.parser.ParseContext;

/**
 * UTF-16 specialist detector of the mixture-of-experts charset detection
 * architecture.  Uses a tiny dense-feature maxent model paired with
 * {@link Utf16ColumnFeatureExtractor} to produce a column-asymmetry-based
 * judgment of UTF-16-LE vs UTF-16-BE.
 *
 * <h3>HTML-immune by construction</h3>
 *
 * <p>The feature set the model consumes (12 per-column byte-range counts)
 * captures the 2-byte alignment asymmetry that UTF-16 content produces and
 * HTML content cannot — HTML has no 2-byte alignment, so any byte range
 * appears with equal expected frequency at even vs odd positions.  No
 * amount of HTML markup can fire this specialist.  See
 * {@link Utf16ColumnFeatureExtractor} for the detailed argument.</p>
 *
 * <h3>Stage 1 of the MoE migration</h3>
 *
 * <p>Runs alongside the existing {@code MojibusterEncodingDetector}
 * rather than replacing any piece of it.  Emits a single
 * {@link EncodingResult.ResultType#STATISTICAL} candidate for the meta
 * arbiter ({@code JunkFilterEncodingDetector}) to weigh against the other
 * detectors in the chain.  The existing {@code WideUnicodeDetector}-based
 * structural UTF-16 detection inside Mojibuster is not removed yet — both
 * can operate in parallel during Stage 1 validation.</p>
 *
 * <h3>Model loading</h3>
 *
 * <p>The default constructor loads a trained model from the classpath at
 * {@link #DEFAULT_MODEL_RESOURCE}.  If the resource is absent or
 * malformed, construction throws {@link IOException} — the detector
 * never operates in a no-op state because silent no-ops produce wrong
 * answers without any indication that something's wrong.  Deploy the
 * detector only when a trained model is bundled; remove it from the
 * chain otherwise.</p>
 *
 * <h3>Probe size</h3>
 *
 * <p>Reads up to {@link #MAX_PROBE_BYTES} bytes.  UTF-16 column-asymmetry
 * signal stabilises quickly — even ~100 bytes is usually enough for a
 * strong call.  Default 512 is generous.</p>
 */
@TikaComponent(spi = false)
public class Utf16SpecialistEncodingDetector
        implements EncodingDetector, StatisticalSpecialist {

    private static final Logger LOG =
            LoggerFactory.getLogger(Utf16SpecialistEncodingDetector.class);

    /**
     * Default classpath resource for the trained UTF-16 specialist model.
     * Missing resource → detector is a noop (logged once at construction).
     */
    public static final String DEFAULT_MODEL_RESOURCE =
            "/org/apache/tika/ml/chardetect/utf16-specialist.bin";

    /** Default number of probe bytes read. */
    public static final int MAX_PROBE_BYTES = 512;

    /**
     * Minimum raw-logit margin (winner − loser) required to return a
     * candidate via the standalone {@link #detect} path.
     */
    private static final float MIN_LOGIT_MARGIN = 1.0f;

    /**
     * Minimum probe length in bytes to attempt UTF-16 classification.
     * Column-asymmetry features on 2-6 byte probes are dominated by
     * noise — one stray null at even position pushes LE features hard.
     * 8 bytes (4 pairs) matches the old structural {@code WideUnicodeDetector}
     * threshold and is enough for the learned asymmetry boundary to separate
     * real UTF-16 Latin ("a\0b\0c\0d\0") from coincidence.
     */
    private static final int MIN_PROBE_BYTES = 8;


    /**
     * Maximum confidence emitted on {@code STATISTICAL} results.  Kept
     * below 1.0 so the meta arbiter never mistakes a model output for a
     * {@code DECLARATIVE} / {@code STRUCTURAL} result.
     */
    private static final float MAX_STATISTICAL_CONFIDENCE = 0.99f;

    private final LinearModel model;
    private final Utf16ColumnFeatureExtractor extractor;
    private final int maxProbeBytes;

    /**
     * Load the model from the default classpath location.
     *
     * @throws IOException if the model resource is missing or malformed —
     *                     the detector does not operate in a no-op state.
     */
    public Utf16SpecialistEncodingDetector() throws IOException {
        this(loadModel(DEFAULT_MODEL_RESOURCE), MAX_PROBE_BYTES);
    }

    /**
     * {@link java.util.ServiceLoader}-compatible provider method.  Wraps
     * the checked {@link IOException} from the no-arg constructor in a
     * {@link java.util.ServiceConfigurationError} so the arbiter can catch
     * it and skip a specialist whose model is not bundled — without
     * hiding the cause.
     */
    public static Utf16SpecialistEncodingDetector provider() {
        try {
            return new Utf16SpecialistEncodingDetector();
        } catch (IOException e) {
            throw new java.util.ServiceConfigurationError(
                    "UTF-16 specialist model not available: " + e.getMessage(), e);
        }
    }

    /**
     * Package-visible constructor for tests.
     */
    Utf16SpecialistEncodingDetector(LinearModel model, int maxProbeBytes) {
        if (model == null) {
            throw new IllegalArgumentException(
                    "UTF-16 specialist model is required; pass a valid "
                            + "LinearModel or use the classpath-loading constructor");
        }
        validateModel(model);
        this.model = model;
        this.extractor = new Utf16ColumnFeatureExtractor();
        this.maxProbeBytes = maxProbeBytes;
    }

    private static LinearModel loadModel(String resourcePath) throws IOException {
        try (InputStream is =
                     Utf16SpecialistEncodingDetector.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException(
                        "UTF-16 specialist model resource not found at "
                                + resourcePath + ".  The specialist must be trained "
                                + "and the model file bundled on the classpath before "
                                + "this detector can be instantiated.  Either bundle "
                                + "the trained model or remove this detector from the "
                                + "encoding-detector chain.");
            }
            return LinearModel.load(is);
        }
    }

    private static void validateModel(LinearModel model) {
        if (model.getNumBuckets() != Utf16ColumnFeatureExtractor.NUM_FEATURES) {
            throw new IllegalArgumentException(
                    "UTF-16 specialist model has " + model.getNumBuckets()
                            + " buckets but extractor expects "
                            + Utf16ColumnFeatureExtractor.NUM_FEATURES);
        }
        if (model.getNumClasses() != 2) {
            throw new IllegalArgumentException(
                    "UTF-16 specialist model must have exactly 2 classes "
                            + "(UTF-16-LE, UTF-16-BE), found "
                            + model.getNumClasses());
        }
    }

    /**
     * Specialist name used in {@link SpecialistOutput} for provenance.
     */
    public static final String SPECIALIST_NAME = "utf16";

    @Override
    public String getName() {
        return SPECIALIST_NAME;
    }

    /**
     * {@link StatisticalSpecialist} entry point: raw per-class logits,
     * or {@code null} for a probe too short to evaluate (fewer than 2
     * bytes) or missing a model.  Returning {@code null} declines to
     * contribute; an all-low logit vector would muddy the combiner.
     *
     * <p>Unlike {@link #detect}, this method does not apply a margin
     * threshold — downstream pooling sees raw logits for both classes.</p>
     */
    @Override
    public SpecialistOutput score(byte[] probe) {
        // score() returns raw logits for the MoE combiner; MIN_PROBE_BYTES
        // applies only to the standalone detect() path where we emit a
        // charset decision.  The combiner is responsible for deciding
        // whether the margin is large enough to trust on short probes.
        if (probe == null || probe.length < 2) {
            return null;
        }
        int len = Math.min(probe.length, maxProbeBytes);
        int[] features = extractor.extract(probe, 0, len);
        float[] logits = model.predictCalibratedLogits(features);
        Map<String, Float> classLogits = new LinkedHashMap<>(2);
        for (int c = 0; c < logits.length; c++) {
            classLogits.put(model.getLabel(c), logits[c]);
        }
        return new SpecialistOutput(SPECIALIST_NAME, classLogits);
    }

    /**
     * Convenience: mark/reset the stream, read a probe, and score it.
     * Returns {@code null} if the probe is too short.
     */
    public SpecialistOutput score(TikaInputStream tis) throws IOException {
        byte[] probe = readProbe(tis);
        return score(probe);
    }

    /**
     * @deprecated use {@link #score(byte[])}. Kept for existing tests.
     */
    @Deprecated
    public SpecialistOutput scoreBytes(byte[] probe) {
        return score(probe);
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        return detect(readProbe(tis));
    }

    /**
     * Byte-array entry point for callers that already hold a probe
     * (e.g. {@code MojibusterEncodingDetector}'s pipeline).  Returns an
     * empty list for probes below {@link #MIN_PROBE_BYTES} or when the
     * winning class has margin &lt; {@link #MIN_LOGIT_MARGIN}.
     */
    public List<EncodingResult> detect(byte[] probe) {
        if (probe == null || probe.length < MIN_PROBE_BYTES) {
            return Collections.emptyList();
        }
        int len = Math.min(probe.length, maxProbeBytes);
        int[] features = extractor.extract(probe, 0, len);
        float[] logits = model.predictLogits(features);

        int winnerIdx = 0;
        int loserIdx = 1;
        if (logits[1] > logits[0]) {
            winnerIdx = 1;
            loserIdx = 0;
        }
        float margin = logits[winnerIdx] - logits[loserIdx];
        if (margin < MIN_LOGIT_MARGIN) {
            // No confident winner — probe is either not UTF-16 or too
            // ambiguous between LE and BE.
            return Collections.emptyList();
        }

        String label = model.getLabel(winnerIdx);
        Charset charset;
        try {
            charset = Charset.forName(toJavaCharsetName(label));
        } catch (Exception e) {
            LOG.debug("Unknown charset from UTF-16 model label '{}'", label, e);
            return Collections.emptyList();
        }
        float confidence = confidenceFromMargin(margin);
        return List.of(new EncodingResult(charset, confidence, label,
                EncodingResult.ResultType.STATISTICAL));
    }

    private byte[] readProbe(TikaInputStream tis) throws IOException {
        tis.mark(maxProbeBytes);
        byte[] buf = new byte[maxProbeBytes];
        try {
            int n = IOUtils.read(tis, buf);
            if (n < buf.length) {
                byte[] trimmed = new byte[n];
                System.arraycopy(buf, 0, trimmed, 0, n);
                return trimmed;
            }
            return buf;
        } finally {
            tis.reset();
        }
    }

    /**
     * Map training-label charset names (e.g. {@code "UTF-16-LE"} with
     * hyphens) to Java's canonical charset names ({@code "UTF-16LE"} no
     * hyphen).  Mirrors the mapping in {@code MojibusterEncodingDetector}.
     */
    private static String toJavaCharsetName(String label) {
        switch (label) {
            case "UTF-16-LE":
                return "UTF-16LE";
            case "UTF-16-BE":
                return "UTF-16BE";
            default:
                return label;
        }
    }

    /**
     * Map a raw-logit margin to a 0..{@link #MAX_STATISTICAL_CONFIDENCE}
     * confidence via a sigmoid-like squash.  The specific function is a
     * tunable mapping — what matters is that larger margins produce higher
     * confidences and the output stays in the valid range.
     */
    private static float confidenceFromMargin(float margin) {
        // Sigmoid centred at 0: f(0) = 0.5, f(large) -> 1.0.
        // We'll steer f so that margin=1 maps to ~0.73, margin=5 maps to ~0.99.
        float s = (float) (1.0 / (1.0 + Math.exp(-margin)));
        return Math.min(s, MAX_STATISTICAL_CONFIDENCE);
    }

}
