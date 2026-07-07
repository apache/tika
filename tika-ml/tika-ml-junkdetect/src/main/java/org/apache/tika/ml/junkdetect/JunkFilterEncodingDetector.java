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
package org.apache.tika.ml.junkdetect;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.detect.CharsetSupersets;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingProbeCache;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.HighByteLetterStats;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.chardetect.AdaptiveProbe;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.quality.TextQualityDetector;

/**
 * A {@link MetaEncodingDetector} that arbitrates charset candidates by
 * asking a {@link TextQualityDetector} which decoded candidate looks
 * most like natural text.
 *
 * <p>Each base {@link org.apache.tika.detect.EncodingDetector} in the
 * {@link org.apache.tika.detect.CompositeEncodingDetector} chain emits
 * candidates into the {@link EncodingDetectorContext}.  This meta detector
 * then reads the raw probe bytes, decodes them under each unique candidate
 * charset, scores each decode with {@link TextQualityDetector#score}, and
 * picks the highest-scoring candidate (the score is cross-script comparable,
 * so a plain argmax suffices).  BOM-declared, meta-tag-declared, structural,
 * and statistical candidates all compete on the same footing — quality of the
 * resulting decode is the sole criterion at this layer.
 *
 * <p>The {@link TextQualityDetector} implementation is discovered via
 * {@link ServiceLoader}.  When no implementation is on the classpath,
 * this detector becomes a no-op: {@link #detect} returns an empty list
 * and {@link org.apache.tika.detect.CompositeEncodingDetector} falls
 * back to its default confidence-based ordering.
 *
 * @since Apache Tika 4.0.0 (TIKA-4720)
 */
@TikaComponent(spi = false, name = "junk-filter-encoding-detector")
public class JunkFilterEncodingDetector implements MetaEncodingDetector {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG =
            LoggerFactory.getLogger(JunkFilterEncodingDetector.class);

    /** How many probe bytes to read for decoding candidates.  Matches the
     * default read limit used by the charset base detectors. */
    private static final int DEFAULT_READ_LIMIT = 16384;

    /** A STATISTICAL candidate at or below this confidence carries no real
     *  signal — it's the "I don't know" level (matches Mojibuster's
     *  windows-1252 fallback confidence).  When the statistical layer offers
     *  nothing above this, the junk-filter defers to a DECLARATIVE/STRUCTURAL
     *  anchor instead of arbitrating near-identical decodes by quality. */
    private static final float NO_INFO_CONFIDENCE = 0.1f;

    // Adaptive candidate band (TIKA speed lever).  The tournament only needs
    // NB's top-2 statistical candidates plus any lower-ranked candidate whose
    // confidence is still at least MIN_TAIL_CONFIDENCE (an absolute floor, not
    // a band relative to the top); deeper, low-confidence candidates are clearly
    // dominated and almost never win (measured: a 0.5 floor retains ~98-99% of
    // selected winners, ~20% smaller pool).  Anchors (DECLARATIVE, STRUCTURAL)
    // are always kept regardless of confidence.  Quality impact is validated by
    // a full common-token/OOV eval, NOT assumed.
    private static final int ALWAYS_KEEP_TOP_N = 2;
    private static final float MIN_TAIL_CONFIDENCE = 0.5f;

    /** Cached quality detector.  {@code null} if none is on the classpath. */
    private final TextQualityDetector qualityDetector;

    private int readLimit = DEFAULT_READ_LIMIT;

    public JunkFilterEncodingDetector() {
        // JunkDetector is hardcoded rather than ServiceLoader-discovered so
        // construction never silently leaves this meta detector as a no-op.
        // A model-load failure (e.g. block-table dimension mismatch across
        // JVM Unicode versions) is logged and the detector becomes a no-op.
        TextQualityDetector q = null;
        try {
            q = JunkDetector.loadFromClasspath();
        } catch (Throwable t) {
            LOG.warn("Failed to load JunkDetector; JunkFilterEncodingDetector "
                    + "will operate as a no-op: {}", t.toString());
        }
        this.qualityDetector = q;
    }

    /** Test-only / deterministic-wiring constructor. */
    public JunkFilterEncodingDetector(TextQualityDetector qualityDetector) {
        this.qualityDetector = qualityDetector;
    }

    public int getReadLimit() {
        return readLimit;
    }

    public void setReadLimit(int readLimit) {
        this.readLimit = readLimit;
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        if (qualityDetector == null) {
            return Collections.emptyList();
        }
        if (parseContext == null) {
            return Collections.emptyList();
        }
        EncodingDetectorContext context = parseContext.get(EncodingDetectorContext.class);
        if (context == null || context.getResults().isEmpty()) {
            return Collections.emptyList();
        }

        Set<Charset> uniqueCharsets = context.getUniqueCharsets();
        if (uniqueCharsets.size() <= 1) {
            // Nothing to arbitrate.  Let the composite's default ordering
            // pick the single candidate.
            return Collections.emptyList();
        }

        if (tis == null) {
            context.setArbitrationInfo("junk-filter-no-stream");
            return Collections.emptyList();
        }

        byte[] bytes = readProbe(tis, context);
        if (bytes == null || bytes.length == 0) {
            context.setArbitrationInfo("junk-filter-empty-stream");
            return Collections.emptyList();
        }
        bytes = stripBomBytes(bytes);

        // Decode each candidate, then HtmlContentCleaner.clean — the same
        // tag-strip + entity-expand TrainJunkModel applies, so train and
        // inference match.  Entity expansion is load-bearing: numeric refs
        // become codepoints whose cross-script transitions expose mojibake
        // under a wrong decoding (AIT5 case).
        Map<Charset, String> candidates = new LinkedHashMap<>();
        // Dedup: charsets that decode the raw probe to the identical string
        // (e.g. GB18030/GBK, x-windows-949/EUC-KR on non-extension content)
        // share one clean() call — the cleaned result is identical by
        // construction, so this is quality-neutral, purely a work saving.
        Map<String, String> cleanedByRaw = new HashMap<>();
        Set<Charset> candidateCharsets = bandFilter(context, uniqueCharsets);
        for (Charset cs : candidateCharsets) {
            String raw = safeDecode(bytes, cs);
            if (raw == null || raw.isEmpty()) {
                LOG.trace("junk-filter decode {} -> null/empty", cs.name());
                continue;
            }
            String decoded = cleanedByRaw.get(raw);
            if (decoded == null) {
                decoded = HtmlContentCleaner.clean(raw);
                cleanedByRaw.put(raw, decoded);
            }
            if (decoded != null && !decoded.isEmpty()) {
                candidates.put(cs, decoded);
            }
        }
        if (candidates.size() <= 1) {
            // One or zero candidates produced usable text; nothing to compare.
            LOG.trace("junk-filter <=1 usable candidate, abstaining");
            return Collections.emptyList();
        }
        // When a DECLARATIVE candidate decodes byte-identically to at least
        // one other candidate, honour the declaration — text quality cannot
        // distinguish equivalent decodings, and a downstream divergent byte
        // (e.g. 0xA4 = € in ISO-8859-15, ¤ in windows-1252) will then be
        // decoded as the author intended.
        Charset declared = pickDeclarativeWithEquivalentDecode(context, candidates);
        if (declared != null) {
            float conf = context.getTopConfidenceFor(declared);
            context.setArbitrationInfo("junk-filter-prefer-declarative");
            LOG.trace("junk-filter -> {} (declarative with equivalent decode)",
                    declared.name());
            return List.of(new EncodingResult(declared, conf));
        }
        if (allDecodingsIdentical(candidates)) {
            // All decodings are byte-identical but no DECLARATIVE candidate
            // is present.  Text quality cannot distinguish them; defer to
            // the composite's default ordering.
            context.setArbitrationInfo("junk-filter-identical-decodings");
            LOG.trace("junk-filter all decodings identical, deferring");
            return Collections.emptyList();
        }

        // Calibrated-rescale argmax.  Score each candidate once with the
        // quality detector, rescale per-script to a [junk≈0, clean≈1]
        // common scale, then pick the highest.  The rescaling is what
        // makes cross-script comparisons sound — without it, the more
        // permissive HAN/LATIN classifiers can out-score the stricter
        // HANGUL/ARABIC/HEBREW ones on equal-quality text and arbitrate
        // wrong (the Korean→Chinese case).
        //
        // Operates on raw decoded candidates — the strip-COMMON step that
        // used to live here was removed once γ (whitespace-bigram skip)
        // and NFC normalization landed inside JunkDetector itself.  Those
        // address the same Masada-style whitespace-storm root cause for
        // every caller of JunkDetector and avoid the train/inference
        // distribution divergence that the strip introduced.
        // The JunkDetector logit is cross-script comparable (z1 calibrated per
        // script, z2..z9 global), so the base decision is a plain argmax of the
        // raw score.  BUT the score is an ABSOLUTE per-decode quality, dominated
        // by shared content (whitespace/digits identical across decodes); on a
        // COMMON-dominated doc the discriminating bytes are diluted and the top
        // candidates differ only by noise.  The quality signal is STATISTICAL-
        // grade evidence, so it may override a higher-evidence anchor
        // (DECLARATIVE author intent, or STRUCTURAL byte-grammar proof) only when
        // it beats that anchor's score by OVERRIDE_MARGIN; otherwise we defer to
        // the anchor.  This is honest low-confidence behaviour, not a tie-break:
        // where the model has real signal (e.g. UTF-8 over garbage UTF-16, Δ≫1)
        // it still overrides freely.
        Charset champion = null;
        double championZ = Double.NEGATIVE_INFINITY;
        Map<Charset, Double> scoreByCharset = new LinkedHashMap<>();
        // Whole-text z (the champion + anchor metric), deduped by decoded text.
        Map<String, Float> wholeZByText = new HashMap<>();
        for (Map.Entry<Charset, String> entry : candidates.entrySet()) {
            String text = entry.getValue();
            Float wholeZ = wholeZByText.get(text);
            if (wholeZ == null) {
                org.apache.tika.quality.TextQualityScore sc = qualityDetector.score(text);
                wholeZ = sc.isUnknown() ? Float.NEGATIVE_INFINITY : sc.getZScore();
                wholeZByText.put(text, wholeZ);
            }
            double z = wholeZ;
            scoreByCharset.put(entry.getKey(), z);
            if (z > championZ) {
                championZ = z;
                champion = entry.getKey();
            }
        }
        if (champion == null) {
            // Every candidate scored UNKNOWN (no modelable script) — the filter
            // has no opinion, so keep the first (highest-confidence) candidate.
            champion = candidates.keySet().iterator().next();
        }

        // CJK-vs-non-CJK family gate.  The whole-text z coin-flips on the
        // CJK/non-CJK BOUNDARY for COMMON-dominated docs (markup/digits/punct
        // decode identically and swamp the few discriminating high bytes),
        // producing false-CJK and real-CJK demotion.  The script-letter "diff" z
        // (codepoints >= 0x80 that are letters/ideographs — the high bytes where
        // candidate decodes actually differ) reads that boundary cleanly, so use
        // it to decide ONLY the family; within a family the whole-text champion
        // stands (Latin-vs-Latin etc. untouched — a blanket diff-score regressed).
        //
        // DEMOTE-ONLY and CJK-champion-only: the gate fires only to demote a CJK
        // champion to non-CJK (the false-CJK fix).  The reverse (promote non-CJK
        // -> CJK) is NOT done: measured at 29k, the diff z reliably says "this CJK
        // pick is really non-CJK" (OOV improves on every such flip) but UNreliably
        // the reverse (the junk model over-rates ideograph mojibake vs sparse
        // Latin letters); the promote direction is also unnecessary — genuine CJK
        // is html-meta-declared upstream.  Because the gate can only act when the
        // champion is CJK, the second "diff" score per candidate is needed ONLY
        // then — compute it lazily and skip it entirely for the common non-CJK
        // champion (halving the score() calls there).
        if (isCjkCharset(champion.name())) {
            double bestCjkDiff = Double.NEGATIVE_INFINITY;
            double bestNonCjkDiff = Double.NEGATIVE_INFINITY;
            Map<String, Float> diffZByText = new HashMap<>();
            for (Map.Entry<Charset, String> entry : candidates.entrySet()) {
                String text = entry.getValue();
                Float diffZ = diffZByText.get(text);
                if (diffZ == null) {
                    String diff = scriptLetters(text);
                    float dz = Float.NEGATIVE_INFINITY;
                    if (!diff.isEmpty()) {
                        org.apache.tika.quality.TextQualityScore d = qualityDetector.score(diff);
                        dz = d.isUnknown() ? Float.NEGATIVE_INFINITY : d.getZScore();
                    }
                    diffZ = dz;
                    diffZByText.put(text, diffZ);
                }
                double dz = diffZ;
                if (isCjkCharset(entry.getKey().name())) {
                    bestCjkDiff = Math.max(bestCjkDiff, dz);
                } else {
                    bestNonCjkDiff = Math.max(bestNonCjkDiff, dz);
                }
            }
            // Override only on a clear diff margin.
            if (bestNonCjkDiff > bestCjkDiff + FAMILY_DIFF_MARGIN) {
                Charset reFam = bestInFamily(scoreByCharset, false);
                if (reFam != null) {
                    LOG.trace("junk-filter family gate: {} (CJK) -> {} (non-CJK by diff z)",
                            champion.name(), reFam.name());
                    champion = reFam;
                }
            }
        }

        // Within-Latin letter gate (demote-only).  Sibling to the CJK gate,
        // for the other boundary the whole-text z can't see: a DOS-OEM / Mac
        // pick whose high bytes decode to box-drawing/symbols beating the
        // windows-1252 truth under COMMON-dilution.  Cased-letter count reads
        // it where typicality ties.  See {@link #applyLatinLetterGate}.
        champion = applyLatinLetterGate(bytes, champion, candidates.keySet());

        // "No-info" guard: if the statistical layer produced no confident
        // answer — no STRUCTURAL proof, and its best STATISTICAL candidate is
        // no better than Mojibuster's windows-1252 "I don't know" fallback
        // (confidence <= NO_INFO_CONFIDENCE) — then it has nothing to say, so a
        // DECLARATIVE/STRUCTURAL anchor (the author's declaration) should win
        // rather than a quality argmax over near-identical decodes.  Fires ONLY
        // when the statistical layer abstained, so it cannot cost the
        // confident-detection wins (UTF-8 recovery etc.).
        Charset anchor = bestAnchor(context, scoreByCharset);
        if (anchor != null && !anchor.equals(champion)
                && !hasConfidentNonDeclarative(context)) {
            LOG.trace("junk-filter -> {} (defer to anchor; statistical layer gave "
                            + "no confident answer, champion was {})",
                    anchor.name(), champion.name());
            context.setArbitrationInfo("junk-filter-defer-no-info");
            return List.of(new EncodingResult(anchor, context.getTopConfidenceFor(anchor)));
        }
        LOG.trace("junk-filter -> {} (argmax z={})",
                champion.name(),
                String.format(java.util.Locale.ROOT, "%.3f", championZ));

        float confidence = context.getTopConfidenceFor(champion);
        context.setArbitrationInfo("junk-filter-selected");
        return List.of(new EncodingResult(champion, confidence));
    }

    /** Minimum diff-z margin by which the other family must beat the champion's
     *  family before the family gate overrides.  Large enough to ignore the
     *  noise-level boundary ties; real CJK-vs-garbage diffs are far larger. */
    private static final double FAMILY_DIFF_MARGIN = 2.0;

    private static boolean isCjkCharset(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.contains("gb") || n.contains("big5") || n.contains("euc")
                || n.contains("shift") || n.contains("jis") || n.contains("2022")
                || n.contains("949");
    }

    /** Highest whole-text-z candidate within the requested family (CJK or not). */
    private static Charset bestInFamily(Map<Charset, Double> wholeZ, boolean cjk) {
        Charset best = null;
        double bz = Double.NEGATIVE_INFINITY;
        for (Map.Entry<Charset, Double> e : wholeZ.entrySet()) {
            if (isCjkCharset(e.getKey().name()) == cjk && e.getValue() > bz) {
                bz = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /** Script-letter "diff" content: codepoints &ge; 0x80 that are letters/
     *  ideographs — the high bytes where candidate decodes differ.  Shared ASCII
     *  and non-ASCII punctuation/symbols are dropped (they dilute toward a
     *  COMMON-dominated tie).  Used only for the CJK-vs-non-CJK family gate. */
    private static String scriptLetters(String s) {
        StringBuilder b = new StringBuilder();
        s.codePoints().forEach(c -> {
            if (c >= 0x80 && Character.isLetter(c)) {
                b.appendCodePoint(c);
            }
        });
        return b.toString();
    }

    /** Canonical {@code Charset.name()} of the WHATWG-default Latin fallback. */
    private static final String WIN1252 = "windows-1252";

    /** Latin single-byte charsets the within-Latin letter gate may arbitrate.
     *  EXCLUDES non-Latin SBCS (Cyrillic windows-1251 / ISO-8859-5, Greek
     *  -1253 / -7, Hebrew -1255 / -8, Arabic -1256 / -6, Thai) whose cased
     *  letters would pollute the count, and all multi-byte CJK (the family
     *  gate's territory). */
    private static final Set<String> LATIN_SBCS = new HashSet<>(Arrays.asList(
            "windows-1252", "windows-1250", "windows-1254", "windows-1257", "windows-1258",
            "ISO-8859-1", "ISO-8859-2", "ISO-8859-3", "ISO-8859-4", "ISO-8859-9",
            "ISO-8859-10", "ISO-8859-13", "ISO-8859-14", "ISO-8859-15", "ISO-8859-16",
            "IBM437", "IBM850", "IBM852", "IBM858", "IBM860", "IBM861", "IBM863", "IBM865",
            "x-MacRoman", "x-MacCentralEurope", "x-MacRomania", "x-MacIceland"));

    /** Probe must have at least this many high bytes for the gate to act —
     *  below it the letter gap is noise (most over-picks are sparse). */
    private static final int LATIN_GATE_MIN_HIGH_BYTES = 16;
    /** windows-1252 must win the cased-letter count by &gt; max(FLOOR, FRACTION
     *  * highBytes).  The margin lets the gate cover Central-European / DOS
     *  siblings safely — genuine CE text wins MORE letters under its true
     *  charset so the gate stays silent — without the tie-flip that forces the
     *  mojibuster Western-Latin fallback to scope itself out of those families. */
    private static final double LATIN_GATE_MARGIN_FLOOR = 6.0;
    private static final double LATIN_GATE_MARGIN_FRACTION = 0.20;

    /**
     * Within-Latin letter-plausibility gate (demote-only).  Demotes {@code
     * champion} to windows-1252 only when windows-1252 is a live candidate, both
     * are Latin SBCS, the probe is high-byte-dense, and windows-1252 decodes
     * clearly MORE cased high-byte letters than the champion — the box-drawing
     * signature, where a wrong IBM850 / x-MacRoman decode maps high bytes to
     * symbols.  The compare is directional: a genuine Central-European / DOS doc
     * wins MORE letters under its true charset, so the gate leaves it untouched.
     * Latin-scoped so it never crosses the CJK boundary (the family gate above)
     * or touches non-Latin SBCS.  Returns the (possibly demoted) charset.
     */
    static Charset applyLatinLetterGate(byte[] probe, Charset champion,
                                        Set<Charset> candidates) {
        String name = champion.name();
        if (WIN1252.equals(name) || !LATIN_SBCS.contains(name)) {
            return champion;
        }
        Charset win = null;
        for (Charset c : candidates) {
            if (WIN1252.equals(c.name())) {
                win = c;
                break;
            }
        }
        if (win == null) {
            return champion;
        }
        int high = HighByteLetterStats.countHighBytes(probe);
        if (high < LATIN_GATE_MIN_HIGH_BYTES) {
            return champion;
        }
        int winLetters = HighByteLetterStats.countCasedHighByteLetters(probe, win);
        int champLetters = HighByteLetterStats.countCasedHighByteLetters(probe, champion);
        double margin = Math.max(LATIN_GATE_MARGIN_FLOOR, LATIN_GATE_MARGIN_FRACTION * high);
        if (winLetters > champLetters + margin) {
            LOG.trace("junk-filter latin gate: {} -> windows-1252 (cased high-byte "
                    + "letters {} vs {}, high={})", name, champLetters, winLetters, high);
            return win;
        }
        return champion;
    }

    /**
     * Restrict the candidate set the tournament will decode+clean+score: keep
     * every DECLARATIVE/STRUCTURAL anchor (author intent / byte-grammar proof),
     * plus the top {@link #ALWAYS_KEEP_TOP_N} STATISTICAL candidates by
     * confidence, plus any deeper STATISTICAL candidate whose confidence is at
     * least {@link #MIN_TAIL_CONFIDENCE} (an absolute floor).  Drops the
     * dominated low-confidence tail —
     * the speed lever — without removing any anchor or NB's real contenders.
     * Returns a subset of {@code all}, preserving its iteration order.
     */
    private static Set<Charset> bandFilter(EncodingDetectorContext context, Set<Charset> all) {
        Set<Charset> anchors = new HashSet<>();
        List<EncodingResult> stats = new ArrayList<>();
        for (EncodingDetectorContext.Result r : context.getResults()) {
            for (EncodingResult er : r.getEncodingResults()) {
                EncodingResult.ResultType t = er.getResultType();
                if (t == EncodingResult.ResultType.DECLARATIVE
                        || t == EncodingResult.ResultType.STRUCTURAL) {
                    anchors.add(er.getCharset());
                } else if (t == EncodingResult.ResultType.STATISTICAL) {
                    stats.add(er);
                }
            }
        }
        stats.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        Set<Charset> keepStat = new HashSet<>();
        for (int i = 0; i < stats.size(); i++) {
            if (i < ALWAYS_KEEP_TOP_N
                    || stats.get(i).getConfidence() >= MIN_TAIL_CONFIDENCE) {
                keepStat.add(stats.get(i).getCharset());
            }
        }
        Set<Charset> kept = new LinkedHashSet<>();
        for (Charset cs : all) {
            if (anchors.contains(cs) || keepStat.contains(cs)) {
                kept.add(cs);
            }
        }
        return kept;
    }

    /**
     * True if some detector produced a confident non-declarative signal: any
     * STRUCTURAL result (byte-grammar proof), or any STATISTICAL result above
     * {@link #NO_INFO_CONFIDENCE}.  When false, the statistical layer has
     * effectively abstained (only its "I don't know" fallback), so a
     * declaration should be trusted over a quality argmax.
     */
    private static boolean hasConfidentNonDeclarative(EncodingDetectorContext context) {
        for (EncodingDetectorContext.Result r : context.getResults()) {
            for (EncodingResult er : r.getEncodingResults()) {
                EncodingResult.ResultType t = er.getResultType();
                if (t == EncodingResult.ResultType.STRUCTURAL) {
                    return true;
                }
                if (t == EncodingResult.ResultType.STATISTICAL
                        && er.getConfidence() > NO_INFO_CONFIDENCE) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Highest-scoring DECLARATIVE/STRUCTURAL candidate present in the scored
     * pool, or {@code null} if none.  This is the higher-evidence "anchor" the
     * junk-filter defers to when the statistical layer gives no confident answer
     * (see {@link #hasConfidentNonDeclarative}).
     */
    private static Charset bestAnchor(EncodingDetectorContext context,
                                      Map<Charset, Double> scoreByCharset) {
        Charset best = null;
        double bestZ = Double.NEGATIVE_INFINITY;
        for (EncodingDetectorContext.Result r : context.getResults()) {
            for (EncodingResult er : r.getEncodingResults()) {
                EncodingResult.ResultType t = er.getResultType();
                if (t != EncodingResult.ResultType.DECLARATIVE
                        && t != EncodingResult.ResultType.STRUCTURAL) {
                    continue;
                }
                Double z = scoreByCharset.get(er.getCharset());
                if (z != null && z > bestZ) {
                    bestZ = z;
                    best = er.getCharset();
                }
            }
        }
        return best;
    }

    /**
     * Return the first DECLARATIVE charset whose decoded output equals at
     * least one other candidate's, or {@code null}.
     */
    private static Charset pickDeclarativeWithEquivalentDecode(
            EncodingDetectorContext context, Map<Charset, String> candidates) {
        for (EncodingDetectorContext.Result r : context.getResults()) {
            for (EncodingResult er : r.getEncodingResults()) {
                if (er.getResultType() != EncodingResult.ResultType.DECLARATIVE) {
                    continue;
                }
                String declaredDecoded = candidates.get(er.getCharset());
                if (declaredDecoded == null) {
                    continue; // declared charset failed to decode the probe
                }
                for (Map.Entry<Charset, String> entry : candidates.entrySet()) {
                    if (!entry.getKey().equals(er.getCharset())
                            && declaredDecoded.equals(entry.getValue())) {
                        return er.getCharset();
                    }
                }
            }
        }
        return null;
    }

    private static boolean allDecodingsIdentical(Map<Charset, String> candidates) {
        String first = null;
        for (String s : candidates.values()) {
            if (first == null) {
                first = s;
            } else if (!first.equals(s)) {
                return false;
            }
        }
        return true;
    }

    private byte[] readProbe(TikaInputStream tis, EncodingDetectorContext context)
            throws IOException {
        // readLimit is the tag-stripped content target; cap raw reads at 512 KB.
        int rawCap = AdaptiveProbe.DEFAULT_RAW_CAP;
        EncodingProbeCache cache = context == null ? null : context.getProbeCache();
        if (cache != null) {
            byte[] cached = cache.get(readLimit, rawCap);
            if (cached != null) {
                return cached.length == 0 ? null : cached;
            }
        }
        byte[] probe = AdaptiveProbe.read(tis, readLimit, rawCap);
        if (cache != null) {
            cache.put(probe, readLimit, rawCap);
        }
        return probe.length == 0 ? null : probe;
    }

    private static String safeDecode(byte[] bytes, Charset charset) {
        // Score CJK candidates on their vendor superset, not the strict base
        // (which U+FFFDs vendor-extension chars and unfairly penalizes real
        // CJK). AutoDetectReader re-applies the same superset for content.
        Charset decodeAs = CharsetSupersets.decodeAs(charset);
        try {
            return new String(bytes, decodeAs);
        } catch (Exception e) {
            LOG.debug("Decode failed for {}: {}", decodeAs.name(), e.toString());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // HTML entity expansion
    //
    // Applied to every decoded candidate before quality scoring.  Resolves
    // numeric character refs (&#NNNN; / &#xHHHH;) to their target codepoints
    // and a small set of common named entities.  Malformed entities pass
    // through as literal text.  Sufficient for the AIT5-class failure
    // mode where blogspot/news pages use numeric Malayalam/Bengali entities
    // intermixed with raw UTF-8 codepoints.
    // -----------------------------------------------------------------------

    /**
     * Delegates to {@link HtmlContentCleaner#expandHtmlEntities} — the single
     * implementation shared with training.  Retained here as the historical
     * entry point used by tests and diagnostics.
     */
    static String expandHtmlEntities(String s) {
        return HtmlContentCleaner.expandHtmlEntities(s);
    }

    /**
     * Strip a leading byte-order mark, if any.  UTF-32 signatures are
     * checked before UTF-16 because the UTF-32 LE BOM ({@code FF FE 00 00})
     * starts with the UTF-16 LE BOM ({@code FF FE}).
     */
    private static byte[] stripBomBytes(byte[] bytes) {
        int bomLen = bomLength(bytes);
        if (bomLen == 0) {
            return bytes;
        }
        return Arrays.copyOfRange(bytes, bomLen, bytes.length);
    }

    private static int bomLength(byte[] b) {
        if (b.length >= 4
                && (b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x00
                && (b[2] & 0xFF) == 0xFE && (b[3] & 0xFF) == 0xFF) {
            return 4; // UTF-32BE
        }
        if (b.length >= 4
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE
                && (b[2] & 0xFF) == 0x00 && (b[3] & 0xFF) == 0x00) {
            return 4; // UTF-32LE
        }
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB
                && (b[2] & 0xFF) == 0xBF) {
            return 3; // UTF-8
        }
        if (b.length >= 2
                && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return 2; // UTF-16BE
        }
        if (b.length >= 2
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return 2; // UTF-16LE
        }
        return 0;
    }

}
