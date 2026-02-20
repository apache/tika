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
     * @return number of hash buckets (feature vector size)
     */
    int getNumBuckets();
}
