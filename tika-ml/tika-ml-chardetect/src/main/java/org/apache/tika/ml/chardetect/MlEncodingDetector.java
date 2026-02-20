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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.LinearModel;
import org.apache.tika.ml.Prediction;
import org.apache.tika.parser.ParseContext;

/**
 * Tika {@link EncodingDetector} backed by a {@link LinearModel} trained on
 * byte n-gram features extracted with {@link ByteNgramFeatureExtractor}.
 * <p>
 * The model resource path defaults to
 * {@code /org/apache/tika/ml/chardetect/chardetect.bin} and is expected to be
 * bundled inside the jar. The classifier label strings are canonical Java
 * {@link Charset} names (e.g. {@code "UTF-8"}, {@code "windows-1252"}).
 * <p>
 * Detection strategy:
 * <ol>
 *   <li>Read up to {@value #MAX_PROBE_BYTES} bytes from the stream.
 *   <li>Extract byte-bigram features with {@link ByteNgramFeatureExtractor}.
 *   <li>Run {@link LinearModel#predict} to obtain class probabilities.
 *   <li>Return the top-1 charset as long as its probability exceeds
 *       {@link #getMinConfidence()}.
 * </ol>
 * <p>
 * This class is thread-safe: the model is loaded once at construction time,
 * the feature extractor is stateless (no mutable fields), and each call to
 * {@link #detect} creates a new feature buffer.
 */
public class MlEncodingDetector implements EncodingDetector {

    private static final long serialVersionUID = 1L;

    /** Default model resource path on the classpath. */
    public static final String DEFAULT_MODEL_RESOURCE =
            "/org/apache/tika/ml/chardetect/chardetect.bin";

    /** Number of bytes read from the stream for detection. */
    public static final int MAX_PROBE_BYTES = 4096;

    private static final int DEFAULT_NUM_BUCKETS = 65536;

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
        this.extractor = new ByteNgramFeatureExtractor(model.getNumBuckets(), true);
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
        this.extractor = new ByteNgramFeatureExtractor(model.getNumBuckets(), true);
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
            return null;
        }

        List<Prediction> predictions = detectAll(probe, 1);
        if (predictions.isEmpty() || predictions.get(0).getConfidence() < minConfidence) {
            return null;
        }

        try {
            return Charset.forName(predictions.get(0).getLabel());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Return the top-N charset predictions in descending confidence order.
     * <p>
     * Each {@link Prediction} carries:
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
        int[] features = extractor.extract(probe);
        float[] logits = model.predictLogits(features);
        float[] probs = CharsetConfusables.collapseGroups(
                LinearModel.softmax(logits.clone()), groupIndices);

        // Only include non-zero entries (collapsed-out group members are 0)
        List<Prediction> results = new ArrayList<>();
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] > 0f) {
                results.add(new Prediction(model.getLabel(i), logits[i], probs[i]));
            }
        }
        results.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        return results.subList(0, Math.min(topN, results.size()));
    }

    private static byte[] readProbe(TikaInputStream is) throws IOException {
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

