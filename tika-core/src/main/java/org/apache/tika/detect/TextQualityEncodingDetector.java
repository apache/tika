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

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.textquality.TextQualityResult;
import org.apache.tika.textquality.TextQualityScorer;

/**
 * An encoding detector that runs all child detectors,
 * collects their candidate charsets, and when they disagree,
 * uses {@link TextQualityScorer} to pick the charset that produces
 * the best-looking decoded text.
 *
 * <p>When all detectors agree (or only one returns a result), no
 * scoring is needed. When {@code tika-eval-lite} is not on the
 * classpath, the no-op scorer makes all scores equal and the
 * detector falls back to first-match-wins (same as
 * {@link CompositeEncodingDetector}).</p>
 *
 * <p>This is used internally by {@link DefaultEncodingDetector} when
 * a real {@link TextQualityScorer} is on the classpath.</p>
 *
 * @since Apache Tika 3.2
 */
public class TextQualityEncodingDetector implements EncodingDetector, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG =
            LoggerFactory.getLogger(TextQualityEncodingDetector.class);

    private static final int DEFAULT_READ_LIMIT = 16384;

    private final List<EncodingDetector> detectors;

    private int readLimit = DEFAULT_READ_LIMIT;

    public TextQualityEncodingDetector(List<EncodingDetector> detectors) {
        this.detectors = new ArrayList<>(detectors);
    }

    public TextQualityEncodingDetector(
            List<EncodingDetector> detectors,
            Collection<Class<? extends EncodingDetector>> excludeEncodingDetectors) {
        this.detectors = new ArrayList<>();
        for (EncodingDetector detector : detectors) {
            if (!isExcluded(excludeEncodingDetectors, detector.getClass())) {
                this.detectors.add(detector);
            }
        }
    }

    private static boolean isExcluded(
            Collection<Class<? extends EncodingDetector>> excludes,
            Class<? extends EncodingDetector> candidate) {
        for (Class<? extends EncodingDetector> e : excludes) {
            if (e.isAssignableFrom(candidate)) {
                return true;
            }
        }
        return false;
    }

    public List<EncodingDetector> getDetectors() {
        return Collections.unmodifiableList(detectors);
    }

    @Override
    public Charset detect(TikaInputStream tis, Metadata metadata,
                          ParseContext parseContext) throws IOException {
        // Collect results from all child detectors
        EncodingDetectorContext context = new EncodingDetectorContext();
        parseContext.set(EncodingDetectorContext.class, context);
        try {
            for (EncodingDetector detector : getDetectors()) {
                Charset detected = detector.detect(tis, metadata, parseContext);
                if (detected != null) {
                    context.addResult(detected, detector.getClass().getSimpleName());
                }
            }
        } finally {
            // Remove context to prevent contamination during recursive parsing
            parseContext.set(EncodingDetectorContext.class, null);
        }

        List<EncodingDetectorContext.Result> results = context.getResults();
        if (results.isEmpty()) {
            return null;
        }

        Set<Charset> uniqueCharsets = context.getUniqueCharsets();
        Charset winner;
        String winnerDetector;

        if (uniqueCharsets.size() == 1) {
            // Unanimous -- no arbitration needed
            winner = results.get(0).getCharset();
            winnerDetector = results.get(0).getDetectorName();
        } else {
            // Disagreement -- arbitrate via text quality scoring
            EncodingDetectorContext.Result best =
                    arbitrate(tis, results, uniqueCharsets);
            winner = best.getCharset();
            winnerDetector = best.getDetectorName();
        }

        metadata.set(TikaCoreProperties.DETECTED_ENCODING, winner.name());
        metadata.set(TikaCoreProperties.ENCODING_DETECTOR, winnerDetector);
        return winner;
    }

    /**
     * When child detectors disagree, read raw bytes from the stream,
     * decode with each candidate charset, strip tags, and score.
     * Returns the result with the highest text quality score.
     * Falls back to first result if scoring is unavailable (no-op scorer).
     */
    private EncodingDetectorContext.Result arbitrate(
            TikaInputStream tis,
            List<EncodingDetectorContext.Result> results,
            Set<Charset> uniqueCharsets) throws IOException {

        // Default to first result if we can't read bytes
        EncodingDetectorContext.Result firstResult = results.get(0);
        if (tis == null) {
            return firstResult;
        }

        byte[] bytes;
        try {
            tis.mark(readLimit);
            bytes = new byte[readLimit];
            int totalRead = 0;
            int bytesRead;
            while (totalRead < readLimit &&
                    (bytesRead = tis.read(bytes, totalRead,
                            readLimit - totalRead)) != -1) {
                totalRead += bytesRead;
            }
            if (totalRead == 0) {
                return firstResult;
            }
            if (totalRead < readLimit) {
                byte[] trimmed = new byte[totalRead];
                System.arraycopy(bytes, 0, trimmed, 0, totalRead);
                bytes = trimmed;
            }
        } finally {
            tis.reset();
        }

        TextQualityScorer scorer = TextQualityScorer.getDefault();

        double bestScore = Double.NEGATIVE_INFINITY;
        EncodingDetectorContext.Result bestResult = firstResult;
        boolean allEqual = true;
        double firstScore = Double.NaN;

        for (Charset candidate : uniqueCharsets) {
            String decoded = decode(bytes, candidate);
            String stripped = stripTags(decoded);
            TextQualityResult tqr = scorer.score(stripped);
            // Use NEGATIVE_INFINITY for texts that produce no scorable bigrams
            // (e.g., all replacement characters from invalid decoding).
            // A score of 0.0 with 0 bigrams means "no text to score", which
            // should not beat a real (negative) score from valid text.
            double score = (tqr.getBigramCount() == 0)
                    ? Double.NEGATIVE_INFINITY : tqr.getScore();

            if (Double.isNaN(firstScore)) {
                firstScore = score;
            } else if (score != firstScore) {
                allEqual = false;
            }

            // Find the first result that uses this charset, for detector name
            EncodingDetectorContext.Result resultForCharset = null;
            for (EncodingDetectorContext.Result r : results) {
                if (r.getCharset().equals(candidate)) {
                    resultForCharset = r;
                    break;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestResult = resultForCharset;
            }

            LOG.debug("Charset {} (from {}): score={}, bigrams={}",
                    candidate.name(),
                    resultForCharset != null ? resultForCharset.getDetectorName() : "?",
                    score, tqr.getBigramCount());
        }

        // If all scores are equal (no-op scorer), fall back to first result
        if (allEqual) {
            return firstResult;
        }

        LOG.debug("Arbitration winner: {} (from {}, score={})",
                bestResult.getCharset().name(),
                bestResult.getDetectorName(), bestScore);
        return bestResult;
    }

    /**
     * Decode bytes using the given charset, replacing malformed/unmappable
     * characters rather than throwing.
     */
    static String decode(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        CharBuffer cb = CharBuffer.allocate(bytes.length * 2);
        decoder.decode(ByteBuffer.wrap(bytes), cb, true);
        decoder.flush(cb);
        cb.flip();
        return cb.toString();
    }

    /**
     * Simple tag stripping: removes &lt;...&gt; sequences so that
     * HTML/XML tag names and attributes don't pollute bigram scoring.
     */
    static String stripTags(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean inTag = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public int getReadLimit() {
        return readLimit;
    }

    public void setReadLimit(int readLimit) {
        this.readLimit = readLimit;
    }
}
