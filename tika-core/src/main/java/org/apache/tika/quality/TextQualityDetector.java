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
package org.apache.tika.quality;

/**
 * Scores a string for text quality and arbitrates between two candidate strings.
 *
 * <p>Implementations are expected to be immutable and thread-safe after construction.
 *
 * <p>Implementations are registered via the standard Java {@link java.util.ServiceLoader}
 * mechanism: place the fully-qualified class name in
 * {@code META-INF/services/org.apache.tika.quality.TextQualityDetector}.
 *
 * <p>Typical usage:
 * <pre>{@code
 * TextQualityDetector detector = ServiceLoader.load(TextQualityDetector.class)
 *         .findFirst().orElseThrow();
 *
 * // Score a string
 * TextQualityScore score = detector.score(text);
 * if (score.getZScore() < -2.0) { ... flag or re-process ... }
 *
 * // Arbitrate between two charset decodings
 * TextQualityComparison cmp = detector.compare("cp1252", decodedAsCp1252,
 *                                               "cp1251", decodedAsCp1251);
 * String winner = cmp.winner();  // "A" or "B"
 * }</pre>
 */
public interface TextQualityDetector {

    /**
     * Scores the given string for text quality.
     *
     * @param text the string to score; must not be null
     * @return a {@link TextQualityScore}; check {@link TextQualityScore#isUnknown()}
     *         if the input is empty or the script is not covered by the model
     */
    TextQualityScore score(String text);

    /**
     * Compares two candidate strings and returns which is higher-quality (cleaner text).
     *
     * <p>A common use case is charset-decoding arbitration: given raw bytes decoded
     * via two different charsets, pass each decoded string here with a human-readable
     * label (e.g. the charset name) and the detector will pick the one that looks
     * more like natural language.
     *
     * @param labelA     human-readable label for candidate A (e.g. {@code "cp1252"})
     * @param candidateA first candidate string
     * @param labelB     human-readable label for candidate B (e.g. {@code "cp1251"})
     * @param candidateB second candidate string
     * @return a {@link TextQualityComparison} with the winning label and confidence delta
     */
    TextQualityComparison compare(String labelA, String candidateA,
                                  String labelB, String candidateB);
}
