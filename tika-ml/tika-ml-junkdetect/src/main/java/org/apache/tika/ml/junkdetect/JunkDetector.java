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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.apache.tika.quality.TextQualityComparison;
import org.apache.tika.quality.TextQualityDetector;
import org.apache.tika.quality.TextQualityScore;

/**
 * The bundled text-quality model: a {@link HashedJunkModel} behind {@link TextQualityDetector}.
 *
 * <ul>
 *   <li>{@link #score}: the pointwise logit (positive = clean text, negative = junk) as the
 *       z-score, its sigmoid as pClean, the dominant script, and the share of letters in
 *       scripts the model was trained on as coverage.  There is no interval estimate:
 *       ciLow = ciHigh = the logit.  The score is unknown ({@link TextQualityScore#isUnknown})
 *       for empty text, text without letters (coverage NaN) and text whose letters are all in
 *       untrained scripts (coverage 0, {@link TextQualityScore#isUnmodeled}): the model has
 *       nothing to judge there.</li>
 *   <li>{@link #compare}: which of two decodes of the same bytes is the real text, from the
 *       pairwise head; the delta is the magnitude of the trained pairwise logit.</li>
 * </ul>
 *
 * <p>{@link #getModel} exposes the worst-window statistics and the junk filter's arbitration.
 * Instances are immutable and thread-safe.
 */
public final class JunkDetector implements TextQualityDetector {

    public static final String DEFAULT_MODEL_RESOURCE = "org/apache/tika/ml/junkdetect/junkdetect.bin";

    private final HashedJunkModel model;

    public JunkDetector(HashedJunkModel model) {
        if (model == null) {
            throw new IllegalArgumentException("model");
        }
        this.model = model;
    }

    /** Loads the bundled model. */
    public static JunkDetector loadFromClasspath() throws IOException {
        InputStream is = JunkDetector.class.getClassLoader().getResourceAsStream(DEFAULT_MODEL_RESOURCE);
        if (is == null) {
            throw new IOException("Model resource not found on classpath: " + DEFAULT_MODEL_RESOURCE);
        }
        try (InputStream in = is) {
            return new JunkDetector(HashedJunkModel.load(in));
        }
    }

    public static JunkDetector loadFromPath(Path path) throws IOException {
        return new JunkDetector(HashedJunkModel.load(path));
    }

    /**
     * {@link java.util.ServiceLoader} provider hook: the SPI registration goes through
     * {@link #loadFromClasspath()}.
     *
     * @throws UncheckedIOException if the bundled model cannot be loaded
     */
    public static JunkDetector provider() {
        try {
            return loadFromClasspath();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load bundled JunkDetector model", e);
        }
    }

    public HashedJunkModel getModel() {
        return model;
    }

    @Override
    public TextQualityScore score(String text) {
        if (text == null || text.isEmpty()) {
            return new TextQualityScore(TextQualityScore.UNKNOWN, TextQualityScore.UNKNOWN,
                    TextQualityScore.UNKNOWN, TextQualityScore.UNKNOWN, null);
        }
        HashedJunkModel.Score s = model.score(text);
        String script = s.getDominantScript() == null ? null : s.getDominantScript().name();
        float coverage = (float) s.getCoverage();
        if (!(coverage > 0)) {
            return new TextQualityScore(TextQualityScore.UNKNOWN, TextQualityScore.UNKNOWN,
                    TextQualityScore.UNKNOWN, TextQualityScore.UNKNOWN, script, coverage);
        }
        float z = (float) s.getLogit();
        float p = (float) (1.0 / (1.0 + Math.exp(-s.getLogit())));
        return new TextQualityScore(z, p, z, z, script, coverage);
    }

    @Override
    public TextQualityComparison compare(String labelA, String candidateA,
                                         String labelB, String candidateB) {
        double logit = model.pairwiseLogit(candidateA, candidateB);
        return new TextQualityComparison(logit >= 0 ? labelA : labelB, (float) Math.abs(logit),
                score(candidateA), score(candidateB), labelA, labelB);
    }
}
