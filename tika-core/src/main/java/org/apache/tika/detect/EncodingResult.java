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
 * A charset detection result pairing a {@link Charset} with a confidence score
 * and a {@link ResultType} indicating the nature of the evidence.
 *
 * <h3>Result types</h3>
 * <ul>
 *   <li>{@link ResultType#DECLARATIVE} — the document explicitly stated its
 *       encoding (BOM, HTML {@code <meta charset>}).  These are authoritative
 *       claims about author intent and get preference over inferred results
 *       <em>when consistent with the actual bytes</em>.</li>
 *   <li>{@link ResultType#STRUCTURAL} — byte-grammar proof (ISO-2022 escape
 *       sequences, UTF-8 multibyte validation).  The encoding is proven by the
 *       byte structure itself, independent of any declaration.</li>
 *   <li>{@link ResultType#STATISTICAL} — probabilistic inference from a
 *       statistical model.  The {@code confidence} float is meaningful here
 *       for ranking among candidates; for DECLARATIVE and STRUCTURAL results
 *       it is conventionally {@code 1.0} but carries no additional information.</li>
 * </ul>
 *
 * @since Apache Tika 4.0
 */
public class EncodingResult {

    /**
     * The nature of the evidence that produced this result.
     */
    public enum ResultType {
        /**
         * The document explicitly declared its encoding (BOM, HTML meta charset).
         * Authoritative about author intent; preferred over inferred results when
         * consistent with the actual bytes.
         */
        DECLARATIVE,
        /**
         * The encoding is proven by byte-grammar structure (ISO-2022 escape
         * sequences, UTF-8 multibyte validation).  Not a guess — the byte
         * patterns are only valid in this encoding.
         */
        STRUCTURAL,
        /**
         * Probabilistic inference from a statistical model.  The confidence
         * float is meaningful for ranking among candidates.
         */
        STATISTICAL
    }

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
    private final ResultType resultType;

    /**
     * Constructs a STATISTICAL result. Existing detectors that do not yet
     * classify their evidence type default to statistical (probabilistic)
     * treatment, which is the safe, arbitratable assumption.
     *
     * @param charset    the detected charset; must not be {@code null}
     * @param confidence detection confidence in {@code [0.0, 1.0]}
     */
    public EncodingResult(Charset charset, float confidence) {
        this(charset, confidence, charset.name(), ResultType.STATISTICAL);
    }

    /**
     * Constructs a STATISTICAL result with a detector-specific label.
     *
     * @param charset    the detected charset; must not be {@code null}
     * @param confidence detection confidence in {@code [0.0, 1.0]}
     * @param label      the detector's original label (e.g. {@code "IBM420-ltr"});
     *                   if {@code null}, defaults to {@code charset.name()}
     */
    public EncodingResult(Charset charset, float confidence, String label) {
        this(charset, confidence, label, ResultType.STATISTICAL);
    }

    /**
     * Constructs a result with an explicit {@link ResultType}.
     *
     * @param charset    the detected charset; must not be {@code null}
     * @param confidence detection confidence in {@code [0.0, 1.0]}
     * @param label      the detector's original label; if {@code null},
     *                   defaults to {@code charset.name()}
     * @param resultType the nature of the evidence; must not be {@code null}
     */
    public EncodingResult(Charset charset, float confidence, String label,
                          ResultType resultType) {
        if (charset == null) {
            throw new IllegalArgumentException("charset must not be null");
        }
        if (resultType == null) {
            throw new IllegalArgumentException("resultType must not be null");
        }
        this.charset = charset;
        this.confidence = Math.max(0f, Math.min(1f, confidence));
        this.label = (label != null) ? label : charset.name();
        this.resultType = resultType;
    }

    public Charset getCharset() {
        return charset;
    }

    /**
     * Detection confidence in {@code [0.0, 1.0]}.  Meaningful for ranking
     * among {@link ResultType#STATISTICAL} candidates.  For
     * {@link ResultType#DECLARATIVE} and {@link ResultType#STRUCTURAL} results
     * the value is conventionally {@code 1.0} but carries no additional
     * information beyond the type itself.
     */
    public float getConfidence() {
        return confidence;
    }

    /**
     * The nature of the evidence that produced this result.
     *
     * @see ResultType
     */
    public ResultType getResultType() {
        return resultType;
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
        return lbl + "@" + String.format(java.util.Locale.ROOT, "%.2f", confidence)
                + "[" + resultType + "]";
    }
}
