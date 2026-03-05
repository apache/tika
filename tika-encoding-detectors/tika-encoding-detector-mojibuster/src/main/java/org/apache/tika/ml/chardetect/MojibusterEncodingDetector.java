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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.LinearModel;
import org.apache.tika.parser.ParseContext;

/**
 * Tika {@link EncodingDetector} backed by a two-stage statistical pipeline.
 *
 * <h3>Detection pipeline</h3>
 * <ol>
 *   <li><strong>Structural gates</strong> ({@link StructuralEncodingRules}) —
 *       cheap deterministic checks before any model call:
 *       ASCII, ISO-2022 family, and UTF-8 grammar exclusion.</li>
 *   <li><strong>General model</strong> ({@link LinearModel}, 2048 buckets,
 *       ~23 labels) — covers all common charsets plus an {@code "EBCDIC"}
 *       routing label for the EBCDIC family.</li>
 *   <li><strong>EBCDIC sub-model</strong> ({@link LinearModel}, 1024 buckets,
 *       5 labels) — invoked only when the general model returns {@code "EBCDIC"};
 *       discriminates IBM420-ltr/rtl, IBM424-ltr/rtl, and IBM500.  Callers
 *       never see the routing label; they always receive a specific variant.</li>
 * </ol>
 *
 * <p>Both models are bundled in the jar.  The two-model approach lets the common
 * path run a smaller weight matrix while giving EBCDIC detection its own focused
 * label space — improving IBM424 ltr/rtl accuracy without bloating the general
 * model.</p>
 *
 * <p>This class is thread-safe: models are loaded once at construction time and
 * feature extraction is stateless.</p>
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
     * Fast structural gates applied before the general model call:
     * ISO-2022 family, pure-ASCII → UTF-8, and UTF-8 grammar exclusion.
     * EBCDIC discrimination is handled by the sub-model, not structural rules.
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

    /** Default general-model resource path on the classpath. */
    public static final String DEFAULT_MODEL_RESOURCE =
            "/org/apache/tika/ml/chardetect/chardetect.bin";

    /**
     * EBCDIC sub-model resource path.  Loaded alongside the general model;
     * invoked only when the general model's top label is {@code "EBCDIC"}.
     */
    public static final String EBCDIC_MODEL_RESOURCE =
            "/org/apache/tika/ml/chardetect/chardetect-ebcdic.bin";

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
     *   <li>{@code EBCDIC} → {@code IBM500}
     *       (routing label for the two-model EBCDIC pipeline; the general model
     *       emits this when it detects EBCDIC-family bytes without committing to a
     *       specific variant.  IBM500 is used as the decode-as charset so the label
     *       resolves to a valid {@link Charset}, but in production the routing layer
     *       always sends "EBCDIC" results to the EBCDIC sub-model and overrides this
     *       placeholder with the sub-model's specific result.)</li>
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
        m.put("EBCDIC",       "IBM500");
        // Training filenames use hyphens (UTF-16-LE) but Java requires no hyphen (UTF-16LE)
        m.put("UTF-16-LE", "UTF-16LE");
        m.put("UTF-16-BE", "UTF-16BE");
        m.put("UTF-32-LE", "UTF-32LE");
        m.put("UTF-32-BE", "UTF-32BE");
        LABEL_TO_JAVA_NAME = Collections.unmodifiableMap(m);
    }

    /** Default number of bytes read from the stream for detection. */
    public static final int MAX_PROBE_BYTES = 4096;

    /**
     * JSON-deserializable configuration for {@link MojibusterEncodingDetector}.
     *
     * <p>Example JSON:
     * <pre>
     * { "maxProbeBytes": 1024 }
     * </pre>
     */
    public static class Config {
        /** Maximum bytes to read per detection call. Must be &gt; 0. */
        public int maxProbeBytes = MAX_PROBE_BYTES;
    }

    /**
     * All charsets whose logit is within this many points of the maximum are
     * included as candidates. Linear confidence within the window ensures that
     * plausible alternatives surface for downstream arbitration (e.g. CharSoup)
     * without the exponential compression that softmax would apply.
     */
    public static final float LOGIT_GAP = 5.0f;

    private final LinearModel model;
    private final LinearModel ebcdicModel;
    private final ByteNgramFeatureExtractor extractor;
    private final ByteNgramFeatureExtractor ebcdicExtractor;
    private final EnumSet<Rule> enabledRules;
    private final int maxProbeBytes;

    /**
     * Load both models from their default classpath locations with all rules enabled
     * and default configuration.
     *
     * @throws IOException if either model resource is missing or unreadable
     */
    public MojibusterEncodingDetector() throws IOException {
        this(new Config());
    }

    /**
     * Load both models from their default classpath locations using the supplied
     * {@link Config}.
     *
     * @param config configuration (e.g. custom {@code maxProbeBytes})
     * @throws IOException if either model resource is missing or unreadable
     */
    public MojibusterEncodingDetector(Config config) throws IOException {
        this(LinearModel.loadFromClasspath(DEFAULT_MODEL_RESOURCE),
             LinearModel.loadFromClasspath(EBCDIC_MODEL_RESOURCE),
             EnumSet.allOf(Rule.class),
             config.maxProbeBytes);
    }

    /**
     * Load both models from their default classpath locations using JSON configuration.
     * Requires Jackson on the classpath.
     *
     * <p>Example JSON: {@code { "maxProbeBytes": 1024 }}
     *
     * @param jsonConfig JSON configuration
     * @throws IOException if either model resource is missing or unreadable
     */
    public MojibusterEncodingDetector(JsonConfig jsonConfig) throws IOException {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    /**
     * Load the general model from a custom path on disk; the EBCDIC sub-model
     * is still loaded from its default classpath location.  Useful for
     * evaluation runs with different general-model candidates.
     *
     * @param modelPath path to the general model binary on disk
     * @throws IOException if either model is missing or unreadable
     */
    public MojibusterEncodingDetector(java.nio.file.Path modelPath) throws IOException {
        this(LinearModel.loadFromPath(modelPath),
             LinearModel.loadFromClasspath(EBCDIC_MODEL_RESOURCE),
             EnumSet.allOf(Rule.class),
             MAX_PROBE_BYTES);
    }

    private MojibusterEncodingDetector(LinearModel model, LinearModel ebcdicModel,
                                        EnumSet<Rule> rules, int maxProbeBytes) {
        this.model           = model;
        this.ebcdicModel     = ebcdicModel;
        this.extractor       = new ByteNgramFeatureExtractor(model.getNumBuckets());
        this.ebcdicExtractor = new ByteNgramFeatureExtractor(ebcdicModel.getNumBuckets());
        this.enabledRules    = rules.isEmpty() ? EnumSet.noneOf(Rule.class) : EnumSet.copyOf(rules);
        this.maxProbeBytes   = maxProbeBytes;
    }

    /**
     * Return a new detector that shares both models but reads at most
     * {@code probeBytes} bytes per stream.  Values smaller than the default
     * ({@value #MAX_PROBE_BYTES}) trade accuracy for speed; larger values may
     * improve accuracy on documents where the encoding is not established in the
     * first few kilobytes.
     *
     * @param probeBytes maximum bytes to read per detection call; must be &gt; 0
     */
    public MojibusterEncodingDetector withMaxProbeBytes(int probeBytes) {
        if (probeBytes <= 0) {
            throw new IllegalArgumentException("probeBytes must be > 0, got: " + probeBytes);
        }
        return new MojibusterEncodingDetector(this.model, this.ebcdicModel,
                                               this.enabledRules, probeBytes);
    }

    /**
     * Return a new detector that shares both models but runs only the specified
     * rules.  Use {@code EnumSet.noneOf(Rule.class)} for statistical-only mode.
     * Neither model is reloaded; this is cheap to call.
     */
    public MojibusterEncodingDetector withRules(EnumSet<Rule> rules) {
        return new MojibusterEncodingDetector(this.model, this.ebcdicModel,
                                               rules, this.maxProbeBytes);
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream input, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        if (input == null) {
            return Collections.emptyList();
        }

        byte[] probe = readProbe(input, maxProbeBytes);
        probe = stripBom(probe);
        if (probe.length == 0) {
            return singleResult(StandardCharsets.UTF_8.name(), EncodingResult.CONFIDENCE_DEFINITIVE, Integer.MAX_VALUE);
        }

        return detectAll(probe, Integer.MAX_VALUE);
    }

    /**
     * Structural gates: deterministic early exits before the general model runs.
     * Only bulletproof, zero-false-positive checks belong here.
     * EBCDIC discrimination is intentionally absent — it is handled by the sub-model.
     */
    /**
     * Applies structural encoding rules that produce CONFIDENCE_DEFINITIVE results.
     * Returns non-null only when a byte-level pattern unambiguously identifies the charset
     * (e.g. ISO-2022 escape sequences, sparse valid UTF-8 multibyte sequences).
     * Pure ASCII is deliberately excluded here — ASCII is compatible with virtually all
     * single-byte encodings, so it is NOT definitive. Use {@link #applyAsciiHeuristic}
     * for the ASCII case.
     */
    private Charset applyDefinitiveStructuralRules(byte[] probe) {
        // ISO-2022 before ASCII: all three variants are 7-bit so checkAscii fires first.
        Charset iso2022 = StructuralEncodingRules.detectIso2022(probe);
        if (iso2022 != null) {
            return iso2022;
        }
        // Sparse high-byte UTF-8: structurally valid UTF-8 with few non-ASCII bytes (< 5%).
        // CJK and single-byte encodings with real content would have many more high bytes.
        // We accept both DEFINITIVE_UTF8 and AMBIGUOUS from checkUtf8 (which counts only lead
        // bytes for its ratio, so files with few 2-byte sequences may appear AMBIGUOUS even
        // though they have valid multibyte sequences). NOT_UTF8 is excluded because it means
        // at least one byte is structurally invalid UTF-8.
        if (probe.length >= 16
                && StructuralEncodingRules.checkUtf8(probe) != StructuralEncodingRules.Utf8Result.NOT_UTF8) {
            int highCount = 0;
            for (byte b : probe) {
                if ((b & 0xFF) >= 0x80) {
                    highCount++;
                }
            }
            if (highCount > 0 && (float) highCount / probe.length < 0.05f) {
                return StandardCharsets.UTF_8;
            }
        }
        return null;
    }

    /**
     * Returns UTF-8 if the probe is pure 7-bit ASCII (no bytes ≥ 0x80, no null bytes).
     * ASCII is a strict subset of UTF-8 and of every single-byte encoding, so this is
     * a heuristic only — the confidence returned by the caller must be below
     * CONFIDENCE_DEFINITIVE to allow downstream detectors (e.g. HTML meta charset) to
     * override it without ambiguity.
     */
    private static boolean isPureAscii(byte[] probe) {
        return StructuralEncodingRules.checkAscii(probe) && !hasNullBytes(probe);
    }

    private static byte[] stripBom(byte[] probe) {
        if (probe.length >= 4
                && (probe[0] & 0xFF) == 0x00 && (probe[1] & 0xFF) == 0x00
                && (probe[2] & 0xFF) == 0xFE && (probe[3] & 0xFF) == 0xFF) {
            return Arrays.copyOfRange(probe, 4, probe.length); // UTF-32 BE
        }
        if (probe.length >= 4
                && (probe[0] & 0xFF) == 0xFF && (probe[1] & 0xFF) == 0xFE
                && (probe[2] & 0xFF) == 0x00 && (probe[3] & 0xFF) == 0x00) {
            return Arrays.copyOfRange(probe, 4, probe.length); // UTF-32 LE
        }
        if (probe.length >= 3
                && (probe[0] & 0xFF) == 0xEF && (probe[1] & 0xFF) == 0xBB
                && (probe[2] & 0xFF) == 0xBF) {
            return Arrays.copyOfRange(probe, 3, probe.length); // UTF-8
        }
        if (probe.length >= 2
                && (probe[0] & 0xFF) == 0xFE && (probe[1] & 0xFF) == 0xFF) {
            return Arrays.copyOfRange(probe, 2, probe.length); // UTF-16 BE
        }
        if (probe.length >= 2
                && (probe[0] & 0xFF) == 0xFF && (probe[1] & 0xFF) == 0xFE) {
            return Arrays.copyOfRange(probe, 2, probe.length); // UTF-16 LE
        }
        return probe;
    }

    private static boolean hasNullBytes(byte[] probe) {
        for (byte b : probe) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return the top-N charset predictions as {@link EncodingResult}s in
     * descending confidence order.
     *
     * <p>The pipeline is:
     * <ol>
     *   <li>Structural gates (ISO-2022, ASCII) — definitive, no model needed.</li>
     *   <li>General model — returns one of ~23 labels or the routing label
     *       {@code "EBCDIC"}.</li>
     *   <li>If the general model returns {@code "EBCDIC"}, the EBCDIC sub-model
     *       runs on the same probe and returns the specific variant
     *       (IBM420-ltr/rtl, IBM424-ltr/rtl, IBM500).</li>
     * </ol>
     * </p>
     *
     * @param probe raw bytes to analyse
     * @param topN  maximum number of results
     * @return results sorted by confidence descending, at most {@code topN} entries
     */
    public List<EncodingResult> detectAll(byte[] probe, int topN) {
        boolean gates = enabledRules.contains(Rule.STRUCTURAL_GATES);

        if (gates) {
            // Definitive structural rules (BOM, ISO-2022, sparse UTF-8) → CONFIDENCE_DEFINITIVE.
            Charset definitive = applyDefinitiveStructuralRules(probe);
            if (definitive != null) {
                return singleResult(definitive.name(), EncodingResult.CONFIDENCE_DEFINITIVE, topN);
            }
            // ASCII heuristic: pure 7-bit ASCII is valid in virtually every single-byte
            // encoding, so we report UTF-8 with a sub-definitive confidence. This allows
            // an HTML meta-charset declaration (which IS CONFIDENCE_DEFINITIVE) to override
            // the ASCII heuristic without non-deterministic tie-breaking.
            if (isPureAscii(probe)) {
                return singleResult(StandardCharsets.UTF_8.name(), 0.75f, topN);
            }
        }

        boolean excludeUtf8 = gates
                && StructuralEncodingRules.checkUtf8(probe) == StructuralEncodingRules.Utf8Result.NOT_UTF8;

        List<EncodingResult> results = runModel(probe, excludeUtf8, topN);

        // If the general model flagged this as EBCDIC, route to the sub-model.
        if (!results.isEmpty() && "EBCDIC".equals(results.get(0).getLabel())) {
            return runEbcdicSubModel(probe, topN);
        }

        // Grammar filtering can leave the list empty on very short probes (e.g. a single
        // high byte that is a valid CJK lead but has an invalid trail byte). Fall back to
        // UTF-8 rather than returning empty and causing AutoDetectReader to throw.
        if (results.isEmpty()) {
            return singleResult(StandardCharsets.UTF_8.name(),
                    EncodingResult.CONFIDENCE_DEFINITIVE / 2, Integer.MAX_VALUE);
        }

        return results;
    }

    private List<EncodingResult> runModel(byte[] probe, boolean excludeUtf8, int topN) {
        int[] features = extractor.extract(probe);
        float[] logits = model.predictLogits(features);

        if (excludeUtf8) {
            for (int i = 0; i < logits.length; i++) {
                if ("UTF-8".equalsIgnoreCase(model.getLabel(i))) {
                    logits[i] = Float.NEGATIVE_INFINITY;
                }
            }
        }

        List<EncodingResult> results = selectByLogitGap(model, logits, topN);

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

    /** Run the EBCDIC sub-model and return its specific variant result. */
    private List<EncodingResult> runEbcdicSubModel(byte[] probe, int topN) {
        int[] features = ebcdicExtractor.extract(probe);
        float[] logits = ebcdicModel.predictLogits(features);
        return selectByLogitGap(ebcdicModel, logits, topN);
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
    private static List<EncodingResult> selectByLogitGap(LinearModel m, float[] logits, int topN) {
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
                String lbl = m.getLabel(i);
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
            } else {
                // some bad bytes within tolerance — keep model confidence
                refined.add(er);
            }
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

    private static byte[] readProbe(TikaInputStream is, int maxBytes) throws IOException {
        is.mark(maxBytes);
        try {
            byte[] buf = new byte[maxBytes];
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
        } finally {
            is.reset();
        }
    }

    public LinearModel getModel() {
        return model;
    }

    public EnumSet<Rule> getEnabledRules() {
        return EnumSet.copyOf(enabledRules.isEmpty() ? EnumSet.noneOf(Rule.class) : enabledRules);
    }

}
