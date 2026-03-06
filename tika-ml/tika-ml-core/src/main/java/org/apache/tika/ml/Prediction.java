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
package org.apache.tika.ml;

import java.util.Locale;

/**
 * The result of a single-label classification from a {@link LinearModel}.
 * <p>
 * Two scores are exposed:
 * <ul>
 *   <li>{@link #getProbability()} — softmax probability of this label
 *       relative to all other labels (0–1). Reflects the model's relative
 *       preference for this label over alternatives. Higher is better, but the
 *       magnitude is N-dependent: 0.60 in an 80-class model is very strong.</li>
 *   <li>{@link #getConfidence()} — a calibration-independent signal (0–1)
 *       computed as {@code sigmoid(logit) × probability}. The sigmoid factor
 *       captures absolute model activation: a large negative logit (the model
 *       has no evidence for this class) suppresses confidence even when the
 *       label happens to win the softmax race by a slim margin.</li>
 * </ul>
 * <p>
 * Use {@link #getProbability()} when comparing candidates from the same
 * prediction run. Use {@link #getConfidence()} when deciding whether to trust
 * a prediction at all (e.g. as a threshold gate).
 */
public final class Prediction {

    private final String label;
    private final double probability;
    private final double confidence;

    /**
     * Construct a prediction from a raw logit and its softmax probability.
     *
     * @param label       the class label (e.g. language tag or charset name)
     * @param logit       raw pre-softmax score for this class
     * @param probability softmax probability for this class (0–1)
     */
    public Prediction(String label, float logit, float probability) {
        this.label = label;
        this.probability = probability;
        double sig = 1.0 / (1.0 + Math.exp(-logit));
        this.confidence = sig * probability;
    }

    /**
     * The predicted class label (e.g. {@code "eng"}, {@code "UTF-8"}).
     */
    public String getLabel() {
        return label;
    }

    /**
     * Softmax probability of this label (0–1), relative to all other labels.
     * Suitable for ranking candidates within a single prediction run.
     */
    public double getProbability() {
        return probability;
    }

    /**
     * Calibration-independent confidence (0–1).
     * Computed as {@code sigmoid(logit) × probability}.
     * <p>
     * Accounts for absolute model activation: if the winning logit is very
     * negative (the model has no strong evidence for any class), confidence
     * is suppressed even when the softmax winner has a comfortable margin.
     * Suitable as a threshold gate for deciding whether to trust the result.
     */
    public double getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "%s(prob=%.3f, conf=%.3f)", label, probability, confidence);
    }
}
