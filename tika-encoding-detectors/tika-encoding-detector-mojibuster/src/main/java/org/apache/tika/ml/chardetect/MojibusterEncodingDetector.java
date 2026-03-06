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
 * Tika {@link EncodingDetector} backed by a single statistical model with
 * structural pre- and post-processing rules.
 *
 * <h3>Detection pipeline</h3>
 * <ol>
 *   <li><strong>Structural gates</strong> ({@link StructuralEncodingRules}) —
 *       cheap deterministic checks before the model: ASCII, ISO-2022 family,
 *       and UTF-8 grammar exclusion.</li>
 *   <li><strong>Statistical model</strong> ({@link LinearModel}) — covers all
 *       charsets as direct labels, including EBCDIC variants (IBM420-ltr/rtl,
 *       IBM424-ltr/rtl, IBM500, IBM1047) alongside CJK, Latin, and other
 *       single-byte encodings.</li>
 *   <li><strong>Post-model rules</strong> — CJK grammar walkers, ISO→Windows
 *       C1-byte upgrade, GB18030 four-byte upgrade.</li>
 * </ol>
 *
 * <p>This class is thread-safe: the model is loaded once at construction time
 * and feature extraction is stateless.</p>
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
        GB_FOUR_BYTE_UPGRADE,
        /**
         * Upgrade an ISO-8859-X result to its Windows-12XX equivalent when the
         * probe contains at least one CRLF pair ({@code 0x0D 0x0A}) but no C1
         * bytes ({@code 0x80–0x9F}).
         *
         * <p>Files originating on Windows use CRLF line endings.  The presence
         * of a {@code \r\n} pair in a probe that is otherwise 7-bit ASCII (or
         * has only high bytes above {@code 0x9F}) is weak evidence of Windows
         * origin and therefore of a Windows code page.  {@link Rule#ISO_TO_WINDOWS}
         * already handles the C1-byte case definitively; this rule covers the
         * weaker case where C1 bytes have not been seen but CRLF line endings
         * suggest Windows origin.  A bare {@code 0x0D} (old Mac Classic CR-only
         * line ending) does <em>not</em> trigger this rule.  Mirrors the legacy
         * {@code UniversalEncodingListener.report()} heuristic.</p>
         */
        CRLF_TO_WINDOWS
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
    private final ByteNgramFeatureExtractor extractor;
    private final EnumSet<Rule> enabledRules;
    private final int maxProbeBytes;

    /**
     * Load the model from its default classpath location with all rules enabled
     * and default configuration.
     *
     * @throws IOException if the model resource is missing or unreadable
     */
    public MojibusterEncodingDetector() throws IOException {
        this(new Config());
    }

    /**
     * Load the model from its default classpath location using the supplied
     * {@link Config}.
     *
     * @param config configuration (e.g. custom {@code maxProbeBytes})
     * @throws IOException if the model resource is missing or unreadable
     */
    public MojibusterEncodingDetector(Config config) throws IOException {
        this(LinearModel.loadFromClasspath(DEFAULT_MODEL_RESOURCE),
             EnumSet.allOf(Rule.class),
             config.maxProbeBytes);
    }

    /**
     * Load the model from its default classpath location using JSON configuration.
     * Requires Jackson on the classpath.
     *
     * <p>Example JSON: {@code { "maxProbeBytes": 1024 }}
     *
     * @param jsonConfig JSON configuration
     * @throws IOException if the model resource is missing or unreadable
     */
    public MojibusterEncodingDetector(JsonConfig jsonConfig) throws IOException {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    /**
     * Load the model from a custom path on disk.  Useful for evaluation runs
     * with different model candidates.
     *
     * @param modelPath path to the model binary on disk
     * @throws IOException if the model is missing or unreadable
     */
    public MojibusterEncodingDetector(java.nio.file.Path modelPath) throws IOException {
        this(LinearModel.loadFromPath(modelPath),
             EnumSet.allOf(Rule.class),
             MAX_PROBE_BYTES);
    }

    private MojibusterEncodingDetector(LinearModel model, EnumSet<Rule> rules, int maxProbeBytes) {
        this.model        = model;
        this.extractor    = new ByteNgramFeatureExtractor(model.getNumBuckets());
        this.enabledRules = rules.isEmpty() ? EnumSet.noneOf(Rule.class) : EnumSet.copyOf(rules);
        this.maxProbeBytes = maxProbeBytes;
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
        return new MojibusterEncodingDetector(this.model, this.enabledRules, probeBytes);
    }

    /**
     * Return a new detector that shares the model but runs only the specified
     * rules.  Use {@code EnumSet.noneOf(Rule.class)} for statistical-only mode.
     * The model is not reloaded; this is cheap to call.
     */
    public MojibusterEncodingDetector withRules(EnumSet<Rule> rules) {
        return new MojibusterEncodingDetector(this.model, rules, this.maxProbeBytes);
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream input, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        if (input == null) {
            return Collections.emptyList();
        }

        byte[] probe = readProbe(input, maxProbeBytes);
        // Strip BOM bytes before feature extraction. BOMs are excluded from training
        // data; stripping ensures consistent model inference. BOM detection is handled
        // by BOMDetector (which runs earlier in the chain and returns DECLARATIVE), and
        // CharSoup strips BOMs from raw bytes before language scoring so a BOM-detected
        // charset is not disadvantaged against other candidates.
        probe = stripBom(probe);
        // An empty probe (e.g. empty file, or a file that was only a BOM) falls
        // through to detectAll where isPureAscii returns true for a zero-length
        // array, yielding windows-1252 as the default.
        return detectAll(probe, Integer.MAX_VALUE);
    }

    /**
     * Applies structural encoding rules that produce {@link EncodingResult.ResultType#STRUCTURAL}
     * results. Returns non-null only when a byte-level pattern unambiguously identifies the
     * charset (ISO-2022 escape sequences, valid UTF-8 grammar).
     *
     * <p>Pure ASCII is deliberately excluded — ASCII is compatible with virtually all
     * single-byte encodings and is not structurally definitive.</p>
     */
    private Charset applyStructuralRules(byte[] probe) {
        // ISO-2022 before ASCII: all three variants are 7-bit so checkAscii fires first.
        Charset iso2022 = StructuralEncodingRules.detectIso2022(probe);
        if (iso2022 != null) {
            return iso2022;
        }
        // UTF-8 structural check.  DEFINITIVE_UTF8 means every multi-byte sequence is
        // valid *and* at least 1 % of bytes are high — this is genuine UTF-8 regardless
        // of whether the high-byte ratio is sparse or dense.  The previous < 5 % threshold
        // caused dense UTF-8 (e.g. text with many accented Latin characters) to fall through
        // to the model, where EBCDIC labels could outscore UTF-8 on short probes.
        if (probe.length >= 16) {
            StructuralEncodingRules.Utf8Result utf8 = StructuralEncodingRules.checkUtf8(probe);
            if (utf8 == StructuralEncodingRules.Utf8Result.DEFINITIVE_UTF8) {
                return StandardCharsets.UTF_8;
            }
            // AMBIGUOUS: < 1 % high bytes but all sequences structurally valid.
            // Even a single valid multi-byte sequence in an otherwise-ASCII probe is
            // strong evidence of UTF-8; single-byte encodings would produce invalid
            // sequences at any meaningful high-byte density.
            if (utf8 == StructuralEncodingRules.Utf8Result.AMBIGUOUS) {
                for (byte b : probe) {
                    if ((b & 0xFF) >= 0x80) {
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns true if the probe is pure 7-bit ASCII (no bytes ≥ 0x80, no null bytes).
     * ASCII is compatible with virtually every single-byte encoding, so this is a
     * heuristic — we report US-ASCII to honestly reflect what the probe showed.
     * CharSoup will upgrade to a declared encoding (e.g. ISO-8859-15) when the document
     * contains an explicit declaration consistent with the ASCII bytes.
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
     *   <li>Statistical model — covers all charsets including EBCDIC variants
     *       (IBM420-ltr/rtl, IBM424-ltr/rtl, IBM500, IBM1047) as direct labels.</li>
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
            // Structural rules: byte-grammar proof (ISO-2022, sparse UTF-8).
            Charset structural = applyStructuralRules(probe);
            if (structural != null) {
                return singleResult(structural.name(), 1.0f,
                        EncodingResult.ResultType.STRUCTURAL, topN);
            }
            // Pure ASCII: no high bytes seen in the probe. We default to windows-1252 —
            // the WHATWG-canonical "Western Latin, I saw only ASCII bytes" encoding.
            // HTML5 explicitly defines ISO-8859-1 as an alias for windows-1252, making
            // windows-1252 the right default: it is the correct superset, it avoids the
            // ambiguity between ISO-8859-1 and windows-1252 in the 0x80–0x9F range, and
            // it keeps the no-hint path consistent with the HTML-spec path (where a stated
            // "charset=iso-8859-1" is normalized to windows-1252 by StandardHtmlEncodingDetector).
            // CharSoup will further upgrade to any compatible DECLARATIVE encoding
            // (e.g. an HTML meta charset=UTF-8) when one is present and consistent.
            if (isPureAscii(probe)) {
                return singleResult("windows-1252", 0.5f,
                        EncodingResult.ResultType.STATISTICAL, topN);
            }
        }

        boolean excludeUtf8 = gates
                && StructuralEncodingRules.checkUtf8(probe) == StructuralEncodingRules.Utf8Result.NOT_UTF8;

        List<EncodingResult> results = runModel(probe, excludeUtf8, topN);

        // If the model had no evidence (probe too short or all tokens filtered), fall back to
        // windows-1252 at very low confidence rather than returning empty and letting
        // AutoDetectReader throw. CharSoup will override this with any DECLARATIVE hint.
        if (results.isEmpty()) {
            return singleResult("windows-1252", 0.1f, EncodingResult.ResultType.STATISTICAL, topN);
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
        // On short probes a single spurious candidate can dominate the logit gap window
        // (e.g. x-EUC-TW scoring 58 on a 9-byte Shift_JIS filename while Shift_JIS scores 17),
        // leaving the correct charset with no chance to be evaluated by grammar or CharSoup.
        // For long probes, a single dominant candidate means genuine model confidence — trust it.
        if (probe.length < SHORT_PROBE_THRESHOLD && results.size() < MIN_CANDIDATES) {
            results = selectAtLeast(model, logits, MIN_CANDIDATES);
        }

        if (enabledRules.contains(Rule.ISO_TO_WINDOWS) && StructuralEncodingRules.hasC1Bytes(probe)) {
            results = upgradeIsoToWindows(results);
        }
        // CRLF_TO_WINDOWS: when C1 bytes were absent (ISO_TO_WINDOWS didn't fire) but
        // CRLF pairs suggest Windows line endings, apply the same ISO→Windows upgrade as
        // weak evidence of Windows file origin. If ISO_TO_WINDOWS already fired, the
        // results are already Windows-12XX and upgradeIsoToWindows is a no-op.
        // Bare CR (old Mac Classic line endings) does NOT trigger this rule.
        if (enabledRules.contains(Rule.CRLF_TO_WINDOWS) && StructuralEncodingRules.hasCrlfBytes(probe)) {
            results = upgradeIsoToWindows(results);
        }
        if (enabledRules.contains(Rule.CJK_GRAMMAR)) {
            results = refineCjkResults(probe, results);
        }
        if (enabledRules.contains(Rule.GB_FOUR_BYTE_UPGRADE)
                && StructuralEncodingRules.hasGb18030FourByteSequence(probe)) {
            results = upgradeGbToGb18030(results);
        }
        // Trim to topN after all rules have fired, not before.
        return results.subList(0, Math.min(topN, results.size()));
    }

    /**
     * Maximum confidence assigned to a STATISTICAL model result.  Kept strictly
     * below 1.0 so that statistical results are never mistaken for STRUCTURAL or
     * DECLARATIVE evidence by downstream arbitrators (e.g. CharSoupEncodingDetector).
     * The top result from the logit-gap window always maps to this value.
     */
    private static final float MAX_STATISTICAL_CONFIDENCE = 0.99f;

    /**
     * Minimum number of candidates always passed to downstream rules and CharSoup,
     * regardless of the logit gap.  When the top candidate has a very high logit
     * and all others fall outside the gap window, CharSoup would see only one option
     * and cannot arbitrate.  Ensuring at least this many candidates lets grammar rules
     * and language signal correct a dominant-but-wrong top candidate.
     */
    /**
     * Probes shorter than this (in bytes) may produce a single dominant but wrong
     * candidate (the logit gap window collapses to 1 result). Below this threshold,
     * {@link #selectAtLeast} supplements the gap-window results up to {@link #MIN_CANDIDATES}
     * so that grammar rules and CharSoup have real alternatives to evaluate.
     */
    private static final int SHORT_PROBE_THRESHOLD = 50;

    /** Minimum candidates guaranteed to downstream rules on short probes. */
    private static final int MIN_CANDIDATES = 8;

    /**
     * Same as {@link #selectByLogitGap} but guarantees at least {@code minN} results
     * by extending the window to include the next-best candidates by raw logit rank.
     */
    private static List<EncodingResult> selectAtLeast(LinearModel m, float[] logits, int minN) {
        // Collect all candidates with a valid charset, sorted by logit descending.
        List<int[]> all = new ArrayList<>();
        for (int i = 0; i < logits.length; i++) {
            if (labelToCharset(m.getLabel(i)) != null) {
                all.add(new int[]{i});
            }
        }
        all.sort((a, b) -> Float.compare(logits[b[0]], logits[a[0]]));

        float maxLogit = all.isEmpty() ? 0f : logits[all.get(0)[0]];
        List<EncodingResult> results = new ArrayList<>(minN);
        for (int rank = 0; rank < Math.min(minN, all.size()); rank++) {
            int i = all.get(rank)[0];
            String lbl = m.getLabel(i);
            Charset cs = labelToCharset(lbl);
            // Confidence scaled relative to the max logit so ordering is preserved.
            float conf = maxLogit > 0
                    ? Math.max(0f, logits[i] / maxLogit) * MAX_STATISTICAL_CONFIDENCE
                    : 0f;
            results.add(new EncodingResult(cs, conf, lbl, EncodingResult.ResultType.STATISTICAL));
        }
        return results;
    }

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
                // Scale to [0, MAX_STATISTICAL_CONFIDENCE] so no statistical result
                // reaches 1.0, keeping the range unambiguously below STRUCTURAL/DECLARATIVE.
                float conf = ((logits[i] - floor) / LOGIT_GAP) * MAX_STATISTICAL_CONFIDENCE;
                String lbl = m.getLabel(i);
                Charset cs = labelToCharset(lbl);
                if (cs != null) {
                    results.add(new EncodingResult(cs, conf, lbl,
                            EncodingResult.ResultType.STATISTICAL));
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

        // Grammar-filter CJK charsets: drop those that produce invalid byte sequences
        // (score == 0 means the grammar walker found bad bytes — the model was wrong).
        // Charsets that pass grammar keep their model confidence unchanged so that
        // all candidates remain on the same sigmoid scale for CharSoup to compare.
        // Non-CJK charsets pass through unchanged.
        List<EncodingResult> refined = new ArrayList<>(results.size());
        for (EncodingResult er : results) {
            if (!CjkEncodingRules.isCjk(er.getCharset())) {
                refined.add(er);
                continue;
            }
            int score = CjkEncodingRules.match(probe, er.getCharset());
            if (score == 0) {
                // grammar rejects this charset entirely — drop it
                continue;
            }
            // Grammar passes: keep the model's sigmoid confidence so everything
            // is on the same scale when CharSoup compares candidates.
            refined.add(er);
        }
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

    private static List<EncodingResult> singleResult(String label, float confidence,
                                                      EncodingResult.ResultType type, int topN) {
        if (topN <= 0) {
            return Collections.emptyList();
        }
        Charset cs = labelToCharset(label);
        if (cs == null) {
            return Collections.emptyList();
        }
        return List.of(new EncodingResult(cs, confidence, label, type));
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
