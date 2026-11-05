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

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * The hashed model's charset decision over the junk filter's candidates.  The champion is the
 * decode with the highest {@link HashedJunkModel#pairValue}.  When its
 * {@link HashedJunkModel#pairwiseLogit} over the best decode with different text is below the
 * deferral threshold, the structural anchor wins, else the declared one, else the champion.
 *
 * <p>The trainer picks the model's threshold with this class, so the threshold is calibrated
 * by the code that applies it.
 */
public final class HashedArbitration {

    private HashedArbitration() {
    }

    public static Decision decide(HashedJunkModel model,
                                  JunkFilterEncodingDetector.Candidates candidates) {
        return decide(model, candidates.getTexts(), candidates.getStructural(),
                candidates.getDeclared());
    }

    /**
     * @param texts cleaned decode per charset, in candidate order (ties go to the first)
     * @param structural the structural anchor, or {@code null}
     * @param declared the declared anchor, or {@code null}
     */
    public static Decision decide(HashedJunkModel model, Map<Charset, String> texts,
                                  Charset structural, Charset declared) {
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("no candidates");
        }
        // features once per distinct text: candidates often share a decode
        Map<String, HashedJunkModel.Features> feats = new HashMap<>();
        Charset champion = null;
        String championText = null;
        double best = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Charset, String> e : texts.entrySet()) {
            double v = model.pairValue(feats.computeIfAbsent(e.getValue(), model::features));
            if (champion == null || v > best) {
                best = v;
                champion = e.getKey();
                championText = e.getValue();
            }
        }
        String runnerText = null;
        double runner = Double.NEGATIVE_INFINITY;
        for (String t : texts.values()) {
            if (!t.equals(championText)) {
                double v = model.pairValue(feats.get(t));
                if (runnerText == null || v > runner) {
                    runner = v;
                    runnerText = t;
                }
            }
        }
        double logit = Double.NaN;
        long difference = 0;
        if (runnerText != null) {
            double[] ld = model.pairwiseLogitAndDifference(feats.get(championText), feats.get(runnerText));
            logit = ld[0];
            difference = (long) ld[1];
        }
        return new Decision(champion, logit, difference, structural != null ? structural : declared,
                model.getDeferThreshold());
    }

    /** The champion, its margin, and the anchor it yields to on a thin margin. */
    public static final class Decision {
        private final Charset champion;
        private final double logit;
        private final long difference;
        private final Charset anchor;
        private final double threshold;

        Decision(Charset champion, double logit, long difference, Charset anchor, double threshold) {
            this.champion = champion;
            this.logit = logit;
            this.difference = difference;
            this.anchor = anchor;
            this.threshold = threshold;
        }

        public Charset getChampion() {
            return champion;
        }

        /** Champion over the runner-up; NaN when every candidate decodes to the same text. */
        public double getLogit() {
            return logit;
        }

        /** N-grams the champion and the runner-up do not share ({@link HashedJunkModel#pairwiseDifference}). */
        public long getDifference() {
            return difference;
        }

        /** Structural anchor, else declared, else {@code null}. */
        public Charset getAnchor() {
            return anchor;
        }

        /** The pick under the model's own deferral threshold. */
        public Charset pick() {
            return pick(threshold);
        }

        /** The pick under {@code deferThreshold}: the anchor when the margin is thinner. */
        public Charset pick(double deferThreshold) {
            return anchor != null && logit < deferThreshold ? anchor : champion;
        }

        public boolean defers(double deferThreshold) {
            return !pick(deferThreshold).equals(champion);
        }
    }
}
