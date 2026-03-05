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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * A composite encoding detector that runs child detectors.
 *
 * <p>If a {@link MetaEncodingDetector} is among the children, this
 * composite switches from first-match-wins to collect-all mode:
 * all base detectors run first and their results are collected in an
 * {@link EncodingDetectorContext}, then the meta detector runs last
 * to arbitrate. Only one meta detector is supported.</p>
 *
 * <p>If no meta detector is present, the first non-empty result wins
 * (traditional behavior).</p>
 */
public class CompositeEncodingDetector implements EncodingDetector, Serializable {

    private static final long serialVersionUID = 5980683158436430252L;

    private static final Logger LOG =
            LoggerFactory.getLogger(CompositeEncodingDetector.class);

    private final List<EncodingDetector> detectors;
    private final List<EncodingDetector> baseDetectors;
    private final MetaEncodingDetector metaDetector;

    public CompositeEncodingDetector(List<EncodingDetector> detectors,
                                     Collection<Class<? extends EncodingDetector>>
                                             excludeEncodingDetectors) {
        this.detectors = new ArrayList<>();
        for (EncodingDetector encodingDetector : detectors) {
            if (!isExcluded(excludeEncodingDetectors, encodingDetector.getClass())) {
                this.detectors.add(encodingDetector);
            }
        }
        this.baseDetectors = new ArrayList<>();
        this.metaDetector = partition(this.detectors, baseDetectors);
    }

    public CompositeEncodingDetector(List<EncodingDetector> detectors) {
        this.detectors = new ArrayList<>(detectors);
        this.baseDetectors = new ArrayList<>();
        this.metaDetector = partition(this.detectors, baseDetectors);
    }

    private static MetaEncodingDetector partition(
            List<EncodingDetector> all, List<EncodingDetector> base) {
        MetaEncodingDetector meta = null;
        for (EncodingDetector d : all) {
            if (d instanceof MetaEncodingDetector) {
                if (meta == null) {
                    meta = (MetaEncodingDetector) d;
                } else {
                    LOG.warn("Multiple MetaEncodingDetectors found; ignoring {}",
                            d.getClass().getName());
                }
            } else {
                base.add(d);
            }
        }
        return meta;
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        if (metaDetector != null) {
            return detectWithMeta(tis, metadata, parseContext);
        }
        return detectFirstMatch(tis, metadata, parseContext);
    }

    /**
     * Traditional first-match-wins behavior: returns the results from the
     * first detector that produces a non-empty list.
     */
    private List<EncodingResult> detectFirstMatch(TikaInputStream tis, Metadata metadata,
                                                  ParseContext parseContext)
            throws IOException {
        for (EncodingDetector detector : getDetectors()) {
            List<EncodingResult> results = detector.detect(tis, metadata, parseContext);
            if (!results.isEmpty()) {
                Charset detected = results.get(0).getCharset();
                metadata.set(TikaCoreProperties.DETECTED_ENCODING, detected.name());
                if (!detector.getClass().getSimpleName()
                        .equals("CompositeEncodingDetector")) {
                    metadata.set(TikaCoreProperties.ENCODING_DETECTOR,
                            detector.getClass().getSimpleName());
                }
                return results;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Collect-all mode: run every base detector, populate context,
     * then let the meta detector arbitrate.
     *
     * <p>The stream is marked once before the base-detector loop.  After all
     * base detectors have run (potentially consuming the stream), it is reset
     * to that mark so the meta detector can re-read the raw bytes for
     * language-based arbitration.  Base detectors are not reset between
     * themselves — each handles its own mark/reset internally if needed.</p>
     */
    private List<EncodingResult> detectWithMeta(TikaInputStream tis, Metadata metadata,
                                                ParseContext parseContext)
            throws IOException {
        EncodingDetectorContext context = new EncodingDetectorContext();
        parseContext.set(EncodingDetectorContext.class, context);

        try {
            for (EncodingDetector detector : baseDetectors) {
                List<EncodingResult> detected = detector.detect(tis, metadata, parseContext);
                if (!detected.isEmpty()) {
                    context.addResult(detected, detector.getClass().getSimpleName());
                }
            }

            // Each base detector handles its own mark/reset, so the stream is
            // back at the start here. CharSoup handles its own mark/reset too.
            List<EncodingResult> metaResults =
                    metaDetector.detect(tis, metadata, parseContext);

            List<EncodingResult> finalResults;
            String detectorName;

            if (metaResults.isEmpty() && !context.getResults().isEmpty()) {
                // Meta detector abstained — fall back to first base detector's results
                EncodingDetectorContext.Result first = context.getResults().get(0);
                finalResults = first.getEncodingResults();
                detectorName = first.getDetectorName();
            } else if (!metaResults.isEmpty()) {
                finalResults = metaResults;
                Charset winner = metaResults.get(0).getCharset();
                // Credit the base detector that originally proposed the winning charset
                detectorName = metaDetector.getClass().getSimpleName();
                for (EncodingDetectorContext.Result r : context.getResults()) {
                    if (r.getCharset().equals(winner)) {
                        detectorName = r.getDetectorName();
                        break;
                    }
                }
            } else {
                return Collections.emptyList();
            }

            Charset detected = finalResults.get(0).getCharset();
            metadata.set(TikaCoreProperties.DETECTED_ENCODING, detected.name());
            metadata.set(TikaCoreProperties.ENCODING_DETECTOR, detectorName);
            metadata.set(TikaCoreProperties.ENCODING_DETECTION_TRACE, buildTrace(context));

            return finalResults;
        } finally {
            parseContext.set(EncodingDetectorContext.class, null);
        }
    }

    private static String buildTrace(EncodingDetectorContext context) {
        StringBuilder sb = new StringBuilder();
        for (EncodingDetectorContext.Result r : context.getResults()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(r.getDetectorName()).append("->").append(r.getCharset().name());
            if (r.getConfidence() < EncodingResult.CONFIDENCE_DEFINITIVE) {
                sb.append(String.format(java.util.Locale.ROOT, "(%.2f)", r.getConfidence()));
            }
        }
        String info = context.getArbitrationInfo();
        if (info != null) {
            sb.append(" [").append(info).append("]");
        }
        return sb.toString();
    }

    public List<EncodingDetector> getDetectors() {
        return Collections.unmodifiableList(detectors);
    }

    private boolean isExcluded(
            Collection<Class<? extends EncodingDetector>> excludeEncodingDetectors,
            Class<? extends EncodingDetector> encodingDetector) {
        return excludeEncodingDetectors.contains(encodingDetector) ||
                assignableFrom(excludeEncodingDetectors, encodingDetector);
    }

    private boolean assignableFrom(
            Collection<Class<? extends EncodingDetector>> excludeEncodingDetectors,
            Class<? extends EncodingDetector> encodingDetector) {
        for (Class<? extends EncodingDetector> e : excludeEncodingDetectors) {
            if (e.isAssignableFrom(encodingDetector)) {
                return true;
            }
        }
        return false;
    }
}
