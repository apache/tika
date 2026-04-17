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

/**
 * Generic feature extractor that maps an input of type {@code T} to a
 * fixed-length integer feature vector suitable for a {@link LinearModel}.
 *
 * @param <T> the raw input type (e.g. {@code String} for text, {@code byte[]}
 *            for raw bytes)
 */
public interface FeatureExtractor<T> {

    /**
     * Extract features from the given input.
     *
     * @param input raw input (may be {@code null})
     * @return int array of length {@link #getNumBuckets()} with feature counts
     */
    int[] extract(T input);

    /**
     * @return number of hash buckets (feature-vector dimension)
     */
    int getNumBuckets();

    /**
     * Sparse extraction into caller-owned reusable buffers: populates
     * {@code dense} with feature counts, writes the indices of non-zero
     * entries into {@code touched}, and returns how many indices were
     * written.  Callers are responsible for clearing the touched entries
     * of {@code dense} before reuse.
     *
     * <p>Default implementation delegates to {@link #extract}.  Extractors
     * that can do better (avoid allocating the full dense vector, or scan
     * the input only once) should override.</p>
     */
    default int extractSparseInto(T input, int[] dense, int[] touched) {
        int[] features = extract(input);
        int n = 0;
        for (int i = 0; i < features.length; i++) {
            if (features[i] != 0) {
                dense[i] = features[i];
                touched[n++] = i;
            }
        }
        return n;
    }
}
