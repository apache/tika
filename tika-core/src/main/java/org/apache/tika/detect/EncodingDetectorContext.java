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
package org.apache.tika.detect;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Context object that collects encoding detection results from base detectors.
 * Stored in {@link org.apache.tika.parser.ParseContext} by
 * {@link CompositeEncodingDetector} so that a {@link MetaEncodingDetector}
 * can see all candidates and arbitrate. Removed after detection to prevent
 * contamination during recursive parsing.
 *
 * <p>Each base detector contributes a ranked {@link List} of
 * {@link EncodingResult}s. The context exposes the top result from each
 * detector as the primary signal, and provides access to all candidates
 * for richer arbitration strategies.</p>
 *
 * @since Apache Tika 3.2
 */
public class EncodingDetectorContext {

    private final List<Result> results = new ArrayList<>();
    private String arbitrationInfo;

    /**
     * Record the ranked results from a child detector.
     *
     * @param encodingResults ranked results, highest confidence first; must not be empty
     * @param detectorName    simple class name of the detector
     */
    public void addResult(List<EncodingResult> encodingResults, String detectorName) {
        if (encodingResults != null && !encodingResults.isEmpty()) {
            results.add(new Result(encodingResults, detectorName));
        }
    }

    /**
     * @return unmodifiable list of all per-detector results in detection order
     */
    public List<Result> getResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns the unique charsets from ALL results of every detector,
     * in detection order (top result first within each detector).
     *
     * <p>Using all candidates rather than just each detector's top-1 is
     * important when a single detector returns a ranked list (e.g., Mojibuster
     * on a short probe returns [windows-1252, windows-1250, Shift-JIS]). If
     * only the top-1 were used, CharSoup would see a single charset and
     * return "unanimous" without ever attempting arbitration.</p>
     */
    public Set<Charset> getUniqueCharsets() {
        Set<Charset> charsets = new LinkedHashSet<>();
        for (Result r : results) {
            for (EncodingResult er : r.getEncodingResults()) {
                charsets.add(er.getCharset());
            }
        }
        return charsets;
    }

    /**
     * Returns the highest confidence seen for the given charset across all
     * detector results (not just top results). Useful for arbitrators that
     * want to propagate the base detector's confidence for the winning charset.
     */
    public float getTopConfidenceFor(Charset charset) {
        float best = 0f;
        for (Result r : results) {
            for (EncodingResult er : r.getEncodingResults()) {
                if (er.getCharset().equals(charset) && er.getConfidence() > best) {
                    best = er.getConfidence();
                }
            }
        }
        return best;
    }

    /**
     * Set by the meta detector to describe how it reached its decision.
     * Values: "unanimous", "scored", "no-stream", "empty-stream", etc.
     */
    public void setArbitrationInfo(String info) {
        this.arbitrationInfo = info;
    }

    public String getArbitrationInfo() {
        return arbitrationInfo;
    }

    /**
     * A single detector's contribution: its ranked list of candidates and its name.
     */
    public static class Result {
        private final List<EncodingResult> encodingResults;
        private final String detectorName;

        public Result(List<EncodingResult> encodingResults, String detectorName) {
            this.encodingResults = Collections.unmodifiableList(
                    new ArrayList<>(encodingResults));
            this.detectorName = detectorName;
        }

        /**
         * All ranked results from this detector, highest confidence first.
         */
        public List<EncodingResult> getEncodingResults() {
            return encodingResults;
        }

        /**
         * The top-ranked charset from this detector.
         */
        public Charset getCharset() {
            return encodingResults.get(0).getCharset();
        }

        /**
         * The confidence of the top-ranked result from this detector.
         */
        public float getConfidence() {
            return encodingResults.get(0).getConfidence();
        }

        /**
         * The {@link EncodingResult.ResultType} of the top-ranked result from this detector.
         */
        public EncodingResult.ResultType getResultType() {
            return encodingResults.get(0).getResultType();
        }

        public String getDetectorName() {
            return detectorName;
        }

        @Override
        public String toString() {
            return detectorName + "=" + encodingResults.get(0);
        }
    }
}
