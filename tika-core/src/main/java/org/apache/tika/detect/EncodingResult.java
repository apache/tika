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

/**
 * A charset detection result pairing a {@link Charset} with a confidence score.
 *
 * <p>Confidence is in the range {@code [0.0, 1.0]}. A score of {@code 1.0}
 * indicates a definitive structural detection (e.g. UTF-16/32 from null-byte
 * patterns, or a declared {@code charset} attribute in an HTML meta tag) that
 * requires no further arbitration. Lower scores reflect statistical estimates
 * where arbitration by a {@link MetaEncodingDetector} may improve accuracy.</p>
 *
 * @since Apache Tika 4.0
 */
public class EncodingResult {

    /** Confidence value indicating a definitive, structural detection. */
    public static final float CONFIDENCE_DEFINITIVE = 1.0f;

    private final Charset charset;
    private final float confidence;
    /**
     * The detector's original label for this result.  Usually identical to
     * {@code charset.name()}, but may differ when the detector uses training
     * labels that are finer-grained than the Java charset registry (e.g.
     * {@code "IBM420-ltr"} / {@code "IBM420-rtl"} both map to Java's
     * {@code "IBM420"}, and {@code "windows-874"} maps to Java's canonical
     * {@code "x-windows-874"}).  Preserved so that evaluation tooling and
     * callers that care about sub-charset properties can access the original
     * prediction without going through {@code Charset.name()}.
     */
    private final String label;

    /**
     * @param charset    the detected charset; must not be {@code null}
     * @param confidence detection confidence in {@code [0.0, 1.0]}
     */
    public EncodingResult(Charset charset, float confidence) {
        this(charset, confidence, charset.name());
    }

    /**
     * @param charset    the detected charset; must not be {@code null}
     * @param confidence detection confidence in {@code [0.0, 1.0]}
     * @param label      the detector's original label (e.g. {@code "IBM420-ltr"});
     *                   if {@code null}, defaults to {@code charset.name()}
     */
    public EncodingResult(Charset charset, float confidence, String label) {
        if (charset == null) {
            throw new IllegalArgumentException("charset must not be null");
        }
        this.charset = charset;
        this.confidence = Math.max(0f, Math.min(1f, confidence));
        this.label = (label != null) ? label : charset.name();
    }

    public Charset getCharset() {
        return charset;
    }

    /**
     * Detection confidence in {@code [0.0, 1.0]}.
     * {@code 1.0} means definitive; lower values invite arbitration.
     */
    public float getConfidence() {
        return confidence;
    }

    /**
     * The detector's original label for this result.  Usually identical to
     * {@link #getCharset()}{@code .name()}, but preserved when the detector
     * uses finer-grained labels than the Java charset registry supports (e.g.
     * {@code "IBM420-ltr"}, {@code "IBM420-rtl"}, {@code "windows-874"}).
     */
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        String cs = charset.name();
        String lbl = label.equals(cs) ? cs : label + "(" + cs + ")";
        return lbl + "@" + String.format(java.util.Locale.ROOT, "%.2f", confidence);
    }
}
