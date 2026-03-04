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
package org.apache.tika.ml.chardetect;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.LinearModel;
import org.apache.tika.parser.ParseContext;

/**
 * Tika {@link EncodingDetector} backed by a layered detection pipeline.
 *
 * <h3>Detection pipeline</h3>
 * <ol>
 *   <li><strong>Structural rules</strong> ({@link StructuralEncodingRules}) —
 *       fast, deterministic gates that fire <em>before</em> any model call:
 *       <ul>
 *         <li>UTF-16/32: null-byte heuristic via {@link ByteEncodingHint}
 *             (handled by the caller or a wrapping detector — not here, as this
 *             class operates on an already-committed byte[])</li>
 *         <li>ASCII: no bytes &ge; 0x80 → return UTF-8 immediately</li>
 *         <li>ISO-2022-JP: ESC-$ sequence present → return ISO-2022-JP</li>
 *         <li>UTF-8 grammar: valid sequences with enough high bytes →
 *             return UTF-8; invalid sequence → exclude UTF-8 from model output</li>
 *       </ul>
 *   </li>
 *   <li><strong>Statistical model</strong> ({@link LinearModel}) — multinomial
 *       logistic regression on high-byte unigrams and bigrams, followed by
 *       confusable-group collapsing via {@link CharsetConfusables}.</li>
 * </ol>
 *
 * <p>The model resource path defaults to
 * {@code /org/apache/tika/ml/chardetect/chardetect.bin} and is expected to be
 * bundled inside the jar. The classifier label strings are canonical Java
 * {@link Charset} names (e.g. {@code "UTF-8"}, {@code "windows-1252"}).</p>
 *
 * <p>This class is thread-safe: the model is loaded once at construction time,
 * the feature extractor is stateless, and each call to {@link #detect} creates
 * a new feature buffer.</p>
 */
@TikaComponent(name = "mojibuster-encoding-detector")
public class MojibusterEncodingDetector implements EncodingDetector {

    /**
     * Post-processing rules that can be individually enabled or disabled.
     * All rules are enabled by default; disable subsets via {@link #withRules}
     * to run ablation studies.
     */
    public enum Rule {
        /**
         * Fast structural gates applied before the model call: HZ escape
         * detection, ISO-2022 family, IBM424 EBCDIC Hebrew, IBM500 EBCDIC
         * International, pure-ASCII → UTF-8, and UTF-8 grammar exclusion.
         */
        STRUCTURAL_GATES,
        /**
         * Upgrade ISO-8859-X results to the corresponding Windows-12XX charset
         * when the probe contains C1 bytes (0x80–0x9F).
         */
        ISO_TO_WINDOWS,
        /**
         * Refine CJK candidates nominated by the model with grammar walkers
         * (Shift_JIS, EUC-JP, EUC-KR, Big5, GB18030).
         */
        CJK_GRAMMAR,
        /**
         * Upgrade a GBK or GB2312 result to GB18030 when the probe contains at
         * least one GB18030-specific 4-byte sequence ([0x81–0xFE][0x30–0x39][0x81–0xFE][0x30–0x39]).
         * Such sequences are impossible in GBK/GB2312 (whose trail bytes are
         * 0x40–0xFE), so their presence is definitive proof that a GB18030 codec
         * is required to avoid replacement characters.
         */
        GB_FOUR_BYTE_UPGRADE
    }

    private static final long serialVersionUID = 1L;

    /** Default model resource path on the classpath. */
    public static final String DEFAULT_MODEL_RESOURCE =
            "/org/apache/tika/ml/chardetect/chardetect.bin";

    /**
     * Maps model label strings (from training-data filenames) to the canonical
     * Java {@link Charset} name when they differ.  This bridges gaps between
     * the encoding names used in training corpora and Java's charset registry.
     * <ul>
     *   <li>{@code x-mac-cyrillic} → {@code x-MacCyrillic} (Java uses mixed case)</li>
     *   <li>{@code IBM424-ltr}, {@code IBM424-rtl} → {@code IBM424}
     *       (Java knows the code page but not the display-direction variant)</li>
     *   <li>{@code IBM420-ltr}, {@code IBM420-rtl} → {@code IBM420}
     *       (same rationale as IBM424)</li>
     *   <li>{@code windows-874} → {@code x-windows-874}
     *       (Java's canonical name for the Thai Windows code page)</li>
     * </ul>
     */
    private static final Map<String, String> LABEL_TO_JAVA_NAME;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("x-mac-cyrillic", "x-MacCyrillic");
        m.put("IBM424-ltr", "IBM424");
        m.put("IBM424-rtl", "IBM424");
        m.put("IBM420-ltr", "IBM420");
        m.put("IBM420-rtl", "IBM420");
        m.put("windows-874",  "x-windows-874");
        LABEL_TO_JAVA_NAME = Collections.unmodifiableMap(m);
    }

    /** Number of bytes read from the stream for detection. */
    public static final int MAX_PROBE_BYTES = 4096;

    /**
     * All charsets whose logit is within this many points of the maximum are
     * included as candidates. Linear confidence within the window ensures that
     * plausible alternatives surface for downstream arbitration (e.g. CharSoup)
     * without the exponential compression that softmax would apply.
     */
    public static final float LOGIT_GAP = 5.0f;

    /**
     * Short probes (below this threshold) with a cleanly-validating CJK charset
     * suppress non-CJK candidates. CJK grammar provides genuine structural
     * evidence that outweighs n-gram statistics on very short inputs.
     */
    public static final int SHORT_PROBE_THRESHOLD = 64;

    private final LinearModel model;
    private final ByteNgramFeatureExtractor extractor;
    private final EnumSet<Rule> enabledRules;

    /**
     * Load the model from the default classpath location with all rules enabled.
     *
     * @throws IOException if the model resource is missing or unreadable
     */
    public MojibusterEncodingDetector() throws IOException {
        this(DEFAULT_MODEL_RESOURCE);
    }

    /**
     * Load the model from a custom classpath resource with all rules enabled.
     *
     * @param modelResourcePath classpath-relative path to the LDM1 binary
     * @throws IOException if the resource is missing or unreadable
     */
    public MojibusterEncodingDetector(String modelResourcePath) throws IOException {
        this(LinearModel.loadFromClasspath(modelResourcePath));
    }

    /**
     * Load the model from a file on disk with all rules enabled.
     *
     * @param modelPath path to the LDM1 binary on disk
     * @throws IOException if the file is missing or unreadable
     */
    public MojibusterEncodingDetector(java.nio.file.Path modelPath) throws IOException {
        this(LinearModel.loadFromPath(modelPath));
    }

    /**
     * Construct with an already-loaded model and all rules enabled.
     */
    public MojibusterEncodingDetector(LinearModel model) {
        this(model, EnumSet.allOf(Rule.class));
    }

    private MojibusterEncodingDetector(LinearModel model, EnumSet<Rule> rules) {
        this.model = model;
        this.extractor = new ByteNgramFeatureExtractor(model.getNumBuckets());
        this.enabledRules = rules.isEmpty() ? EnumSet.noneOf(Rule.class) : EnumSet.copyOf(rules);
    }

    /**
     * Return a new detector that shares this model but runs only the specified
     * rules.  Use {@code EnumSet.noneOf(Rule.class)} for statistical-only mode.
     * The model is not reloaded; this is cheap to call.
     */
    public MojibusterEncodingDetector withRules(EnumSet<Rule> rules) {
        return new MojibusterEncodingDetector(this.model, rules);
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream input, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        if (input == null) {
            return Collections.emptyList();
        }

        // If WideUnicodeDetector has already claimed this stream (valid or illegal
        // surrogates), defer entirely — the byte-ngram model was not trained on
        // null-heavy UTF-16/32 data and would produce noise.
        EncodingDetectorContext encodingContext =
                parseContext.get(EncodingDetectorContext.class);
        if (encodingContext != null && encodingContext.getWideUnicodeSignal() != null) {
            return Collections.emptyList();
        }

        byte[] probe = readProbe(input);
        if (probe.length == 0) {
            return Collections.emptyList();
        }

        return detectAll(probe, Integer.MAX_VALUE);
    }

    /**
     * Run the structural rule tier against the probe and return a definitive
     * charset if one fires, or {@code null} to continue to the model.
     *
     * <p>Order:
     * <ol>
     *   <li>ISO-2022 family BEFORE ASCII: ISO-2022-JP/KR/CN are 7-bit
     *       encodings, so checkAscii would fire first and return UTF-8.</li>
     *   <li>IBM424 (EBCDIC Hebrew) BEFORE IBM500: both share the EBCDIC space
     *       gate; IBM424's Hebrew-byte check distinguishes it first.</li>
     *   <li>IBM500 (EBCDIC International) BEFORE ASCII: IBM500 has high bytes
     *       for every Latin letter so checkAscii would never fire anyway, but
     *       placing it here keeps the EBCDIC family together.</li>
     *   <li>ASCII — no high bytes at all → UTF-8.</li>
     * </ol>
     * Note: UTF-16/32 detection is handled by
     * {@link org.apache.tika.detect.WideUnicodeDetector}, which writes a
     * {@link WideUnicodeSignal} into the {@link EncodingDetectorContext} before
     * this detector runs.
     */
    /**
     * Structural gates that return a definitive result without running the model.
     * IBM424 is intentionally excluded: it is EBCDIC-Hebrew but has two directional
     * variants (ltr/rtl) that differ in byte ordering.  The structural rule can
     * confirm "EBCDIC Hebrew", but only the model can pick the direction.  IBM424
     * is therefore handled separately in {@link #detectAll}.
     */
    private Charset applyStructuralRules(byte[] probe) {
        Charset iso2022 = StructuralEncodingRules.detectIso2022(probe);
        if (iso2022 != null) {
            return iso2022;
        }

        if (StructuralEncodingRules.checkIbm500(probe)) {
            return labelToCharset("IBM500");
        }

        if (StructuralEncodingRules.checkAscii(probe)) {
            return StandardCharsets.UTF_8;
        }

        // UTF-8 grammar: only use the structural check to EXCLUDE UTF-8 (NOT_UTF8).
        // DEFINITIVE_UTF8 is not used as an early exit because valid UTF-8 grammar
        // is a necessary but not sufficient condition — Big5, ISO-8859-1, and other
        // encodings can produce byte sequences that satisfy UTF-8 grammar by
        // coincidence, causing false positives.
        return null;
    }

    /**
     * Return the top-N charset predictions as {@link EncodingResult}s in
     * descending confidence order.
     *
     * <p>Structural rules are applied first; if a rule fires definitively, a
     * single result (confidence 1.0) is returned.  IBM424 (EBCDIC Hebrew) is
     * a special case: the structural gate confirms the EBCDIC-Hebrew byte
     * pattern, but the ltr/rtl direction is determined by the model.  If the
     * model's top result is not an IBM424 variant (very rare), we fall back to
     * the generic {@code "IBM424"} label.</p>
     *
     * @param probe raw bytes to analyse
     * @param topN  maximum number of results
     * @return results sorted by confidence descending, at most {@code topN} entries
     */
    public List<EncodingResult> detectAll(byte[] probe, int topN) {
        boolean gates = enabledRules.contains(Rule.STRUCTURAL_GATES);

        if (gates) {
            Charset structural = applyStructuralRules(probe);
            if (structural != null) {
                return singleResult(structural.name(), EncodingResult.CONFIDENCE_DEFINITIVE, topN);
            }
        }

        // IBM424 (EBCDIC Hebrew): structural gate confirms the encoding family when it
        // fires, but the ltr/rtl direction is left to the model.  IBM424 is never
        // excluded — the gate misses ~10-20% of samples (low Hebrew density, numeric
        // content), and hard exclusion on those cases forces wrong answers.
        boolean ibm424Confirmed = gates && StructuralEncodingRules.checkIbm424(probe);

        StructuralEncodingRules.Utf8Result utf8 = StructuralEncodingRules.checkUtf8(probe);
        boolean excludeUtf8 = gates && (utf8 == StructuralEncodingRules.Utf8Result.NOT_UTF8);
        // IBM500: exclude from the model only when IBM424 is structurally confirmed.
        // IBM424 (all letters < 0x80) and IBM500 (all letters >= 0x80) are byte-range
        // disjoint, so a confirmed IBM424 probe cannot be IBM500.  Excluding IBM500 in
        // that case prevents the model from leaking probability to IBM500 on ambiguous
        // IBM424-ltr probes.  When neither gate fires we leave IBM500 in the model so
        // it can score the ~10% of IBM500 samples whose Latin density is too low to
        // trigger checkIbm500's early exit.
        boolean excludeIbm500 = ibm424Confirmed;

        List<EncodingResult> results = runModel(probe, excludeUtf8, false, excludeIbm500, topN);

        // If the IBM424 structural gate fired but the model picked something other than
        // an IBM424 variant (very rare edge case), force the generic IBM424 label.
        if (ibm424Confirmed
                && (results.isEmpty() || !results.get(0).getLabel().startsWith("IBM424"))) {
            return singleResult("IBM424", EncodingResult.CONFIDENCE_DEFINITIVE, topN);
        }

        return results;
    }

    private List<EncodingResult> runModel(byte[] probe, boolean excludeUtf8,
                                          boolean excludeIbm424, boolean excludeIbm500,
                                          int topN) {
        int[] features = extractor.extract(probe);
        float[] logits = model.predictLogits(features);

        // Suppress structurally excluded labels before any scoring so they
        // don't distort softmax or logit-gap comparisons.
        for (int i = 0; i < logits.length; i++) {
            String label = model.getLabel(i);
            if (excludeIbm424 && label.startsWith("IBM424")) {
                logits[i] = Float.NEGATIVE_INFINITY;
            } else if (excludeIbm500 && "IBM500".equals(label)) {
                logits[i] = Float.NEGATIVE_INFINITY;
            } else if (excludeUtf8 && "UTF-8".equalsIgnoreCase(label)) {
                logits[i] = Float.NEGATIVE_INFINITY;
            }
        }

        List<EncodingResult> results = selectByLogitGap(logits, topN);

        if (enabledRules.contains(Rule.ISO_TO_WINDOWS) && StructuralEncodingRules.hasC1Bytes(probe)) {
            results = upgradeIsoToWindows(results);
        }
        if (enabledRules.contains(Rule.CJK_GRAMMAR)) {
            results = refineCjkResults(probe, results);
        }
        if (enabledRules.contains(Rule.GB_FOUR_BYTE_UPGRADE)
                && StructuralEncodingRules.hasGb18030FourByteSequence(probe)) {
            results = upgradeGbToGb18030(results);
        }
        return results.subList(0, Math.min(topN, results.size()));
    }

    /**
     * Candidate selection: collect every charset whose logit is within
     * {@link #LOGIT_GAP} points of the maximum, assigning confidence linearly
     * within the window (1.0 at the top, 0.0 at the floor).
     *
     * <p>Softmax is intentionally avoided: it exponentially amplifies small logit
     * differences, collapsing genuine ambiguity into a false 99%/1% split and
     * hiding plausible alternatives that downstream arbitrators (e.g. CharSoup)
     * should evaluate. Linear confidence within the gap preserves the signal.</p>
     */
    private List<EncodingResult> selectByLogitGap(float[] logits, int topN) {
        float maxLogit = Float.NEGATIVE_INFINITY;
        for (float l : logits) {
            if (l > maxLogit) {
                maxLogit = l;
            }
        }
        float floor = maxLogit - LOGIT_GAP;
        List<EncodingResult> results = new ArrayList<>();
        for (int i = 0; i < logits.length; i++) {
            if (logits[i] >= floor) {
                float conf = (logits[i] - floor) / LOGIT_GAP;
                String lbl = model.getLabel(i);
                Charset cs = labelToCharset(lbl);
                if (cs != null) {
                    results.add(new EncodingResult(cs, conf, lbl));
                }
            }
        }
        results.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        return results.subList(0, Math.min(topN, results.size()));
    }

    /**
     * When the probe contains C1 bytes (0x80–0x9F), any ISO-8859-X result is
     * invalid — those bytes are C1 control codes in every ISO-8859-X standard.
     * This method replaces each such result with its Windows-12XX equivalent
     * from {@link CharsetConfusables#ISO_TO_WINDOWS}, preserving confidence.
     *
     * <p>This is a post-{@code collapseGroups} correction: the model has
     * already identified the right script family (Western European, Cyrillic,
     * Arabic, …); we are only fixing the ISO-vs-Windows attribution within
     * that family.</p>
     */
    /**
     * For each CJK encoding in {@code results}, runs the corresponding grammar
     * walker and adjusts the result:
     * <ul>
     *   <li>Grammar score 0 (bad byte sequences) → drop the result; the model
     *       was wrong about this encoding</li>
     *   <li>Grammar score &gt; 10 (enough valid multi-byte chars) → replace
     *       model confidence with grammar confidence</li>
     *   <li>Grammar score 10 (valid but too few chars to be sure) → keep
     *       model confidence; grammar cannot add information</li>
     * </ul>
     * Results are re-sorted after adjustment since grammar scores may reorder
     * candidates.  Non-CJK results pass through unchanged.
     */
    /**
     * Refines CJK candidates using grammar walkers, and on short probes drops
     * single-byte alternatives when a CJK charset validates cleanly.
     *
     * <p><b>Short-probe promotion rule</b> (probe &lt; {@link #SHORT_PROBE_THRESHOLD}):
     * if any CJK charset scores ≥ {@link CjkEncodingRules#CLEAN_SHORT_PROBE_CONFIDENCE}
     * (bad == 0, at least one valid 2-byte pair), all non-CJK results are removed.
     * Single-byte encodings have no structural constraints — they silently accept
     * any byte sequence. A CJK parser that consumes all high bytes without errors
     * is providing genuine structural evidence, and that evidence should win over
     * the byte-ngram model's single-byte predictions on short probes.</p>
     *
     * <p><b>Long-probe rule</b>: grammar score replaces model confidence when the
     * grammar has enough signal (score &gt; 10 in the old convention, which is now
     * always satisfied for bad==0 probes via the new confidence formula).</p>
     */
    private static List<EncodingResult> refineCjkResults(byte[] probe,
                                                          List<EncodingResult> results) {
        boolean hasCjk = false;
        for (EncodingResult er : results) {
            if (CjkEncodingRules.isCjk(er.getCharset())) {
                hasCjk = true;
                break;
            }
        }
        if (!hasCjk) {
            return results;
        }

        // Score every CJK charset in the result list.
        List<EncodingResult> refined = new ArrayList<>(results.size());
        boolean anyCleanCjk = false;
        for (EncodingResult er : results) {
            if (!CjkEncodingRules.isCjk(er.getCharset())) {
                refined.add(er);
                continue;
            }
            int score = CjkEncodingRules.match(probe, er.getCharset());
            if (score == 0) {
                // grammar rejects this charset — drop entirely
            } else if (score >= CjkEncodingRules.CLEAN_SHORT_PROBE_CONFIDENCE) {
                // structurally clean (bad == 0) — use grammar confidence
                refined.add(new EncodingResult(er.getCharset(), score / 100f));
                anyCleanCjk = true;
            } else {
                // some bad bytes within tolerance — keep model confidence
                refined.add(er);
            }
        }

        // Short-probe promotion: if at least one CJK charset validated cleanly
        // (bad == 0), remove all single-byte alternatives. On a short probe the
        // model's n-gram statistics favour common Western charsets simply because
        // Latin text dominates the training corpus; valid CJK grammar is the
        // stronger signal here.
        if (probe.length < SHORT_PROBE_THRESHOLD && anyCleanCjk) {
            refined.removeIf(er -> !CjkEncodingRules.isCjk(er.getCharset()));
        }

        refined.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        return refined;
    }

    /**
     * When a GB18030-specific 4-byte sequence has been found in the probe,
     * any GBK or GB2312 result is upgraded to GB18030. The confidence is
     * preserved; only the charset label changes.
     */
    private static List<EncodingResult> upgradeGbToGb18030(List<EncodingResult> results) {
        Charset gb18030 = labelToCharset("GB18030");
        if (gb18030 == null) {
            return results;
        }
        List<EncodingResult> upgraded = new ArrayList<>(results.size());
        for (EncodingResult er : results) {
            String name = er.getCharset().name();
            if ("GBK".equalsIgnoreCase(name) || "GB2312".equalsIgnoreCase(name)) {
                // Preserve the original detected label; only the decode charset changes.
                upgraded.add(new EncodingResult(gb18030, er.getConfidence(), er.getLabel()));
            } else {
                upgraded.add(er);
            }
        }
        return upgraded;
    }

    private static List<EncodingResult> upgradeIsoToWindows(List<EncodingResult> results) {
        List<EncodingResult> upgraded = new ArrayList<>(results.size());
        for (EncodingResult er : results) {
            String windowsName = CharsetConfusables.ISO_TO_WINDOWS.get(er.getCharset().name());
            if (windowsName != null) {
                Charset windowsCs = labelToCharset(windowsName);
                // Preserve the original detected label (e.g. "ISO-8859-1") so callers
                // can see what was detected; getCharset() returns the superset to decode with.
                upgraded.add(new EncodingResult(
                        windowsCs != null ? windowsCs : er.getCharset(),
                        er.getConfidence(),
                        er.getLabel()));
            } else {
                upgraded.add(er);
            }
        }
        return upgraded;
    }

    private static List<EncodingResult> singleResult(String label, float confidence, int topN) {
        if (topN <= 0) {
            return Collections.emptyList();
        }
        Charset cs = labelToCharset(label);
        if (cs == null) {
            return Collections.emptyList();
        }
        return List.of(new EncodingResult(cs, confidence, label));
    }

    /**
     * Resolve a model label string to a Java {@link Charset}, applying the
     * {@link #LABEL_TO_JAVA_NAME} alias map first so that labels like
     * {@code "x-mac-cyrillic"} or {@code "IBM424-ltr"} survive the lookup.
     *
     * @return the resolved charset, or {@code null} if no mapping exists
     */
    public static Charset labelToCharset(String label) {
        String javaName = LABEL_TO_JAVA_NAME.getOrDefault(label, label);
        try {
            return Charset.forName(javaName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] readProbe(TikaInputStream is) throws IOException {
        byte[] buf = new byte[MAX_PROBE_BYTES];
        int total = 0;
        int n;
        while (total < buf.length &&
               (n = is.read(buf, total, buf.length - total)) != -1) {
            total += n;
        }
        if (total == buf.length) {
            return buf;
        }
        byte[] trimmed = new byte[total];
        System.arraycopy(buf, 0, trimmed, 0, total);
        return trimmed;
    }

    public LinearModel getModel() {
        return model;
    }

    public EnumSet<Rule> getEnabledRules() {
        return EnumSet.copyOf(enabledRules.isEmpty() ? EnumSet.noneOf(Rule.class) : enabledRules);
    }

}
