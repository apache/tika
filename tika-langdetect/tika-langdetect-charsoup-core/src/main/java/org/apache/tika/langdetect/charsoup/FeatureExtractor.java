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
package org.apache.tika.langdetect.charsoup;

/**
 * Common interface for feature extractors used by the bigram language detector.
 * Implementations must share the same preprocessing pipeline
 * ({@link CharSoupFeatureExtractor#preprocess(String)}) but may differ in how
 * they extract and hash features from the preprocessed text.
 */
public interface FeatureExtractor {

    /**
     * Full preprocessing + feature extraction pipeline.
     *
     * @param rawText raw input text (may be {@code null})
     * @return int array of size {@link #getNumBuckets()} with feature counts
     */
    int[] extract(String rawText);

    /**
     * Extract into caller-supplied buffer (zeroed first).
     *
     * @param rawText raw input text (may be {@code null})
     * @param counts  pre-allocated int array of size {@link #getNumBuckets()} (will be zeroed)
     */
    void extract(String rawText, int[] counts);

    /**
     * Extract from already-preprocessed text.
     *
     * @param preprocessedText text already passed through
     *                         {@link CharSoupFeatureExtractor#preprocess(String)}
     * @return int array of size {@link #getNumBuckets()} with feature counts
     */
    int[] extractFromPreprocessed(String preprocessedText);

    /**
     * Extract from already-preprocessed text into a caller-supplied buffer.
     *
     * @param preprocessedText text already passed through
     *                         {@link CharSoupFeatureExtractor#preprocess(String)}
     * @param counts           pre-allocated int array of size {@link #getNumBuckets()}
     * @param clear            if {@code true}, zero the array before extracting;
     *                         if {@code false}, accumulate on top of existing counts
     */
    void extractFromPreprocessed(String preprocessedText, int[] counts, boolean clear);

    /**
     * Extract features into {@code counts} and return the total n-gram emission count.
     * <p>
     * The count is the raw number of individual n-gram tokens processed before bucket
     * hashing.  It is a script-neutral measure of how much signal the input carries:
     * whitespace-only input yields 0; ~200 chars of typical Latin or CJK prose yields
     * roughly 400.  This is the right threshold variable for length-gated confusables
     * because it is insensitive to padding spaces or punctuation-heavy inputs, and it
     * naturally accounts for the higher feature density of CJK text vs. Latin text.
     * <p>
     * The default implementation sums the feature vector after extraction, which is
     * correct because every emission does {@code counts[bucket]++}; the sum therefore
     * equals the total emission count regardless of hash collisions.
     *
     * @param rawText raw input text (may be {@code null})
     * @param counts  pre-allocated int array of size {@link #getNumBuckets()} (will be zeroed)
     * @return total n-gram emission count (≥ 0)
     */
    default int extractAndCount(String rawText, int[] counts) {
        extract(rawText, counts);
        int n = 0;
        for (int c : counts) {
            n += c;
        }
        return n;
    }

    /**
     * @return number of hash buckets (feature vector size)
     */
    int getNumBuckets();
}
