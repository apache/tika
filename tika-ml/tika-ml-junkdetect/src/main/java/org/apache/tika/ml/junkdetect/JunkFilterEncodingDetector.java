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
import java.nio.charset.StandardCharsets;
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
import org.apache.tika.detect.BOMDetector;
import org.apache.tika.detect.CharsetSupersets;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingProbeCache;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.chardetect.AdaptiveProbe;
import org.apache.tika.ml.chardetect.StructuralEncodingRules;
import org.apache.tika.parser.ParseContext;

/**
 * A {@link MetaEncodingDetector} that arbitrates the charset candidates of the
 * {@link org.apache.tika.detect.CompositeEncodingDetector} chain by which decode of the
 * probe reads as real text.
 *
 * <p>It reads the probe, keeps the declared and structural anchors plus the strongest
 * statistical candidates, and decodes and cleans ({@link HtmlContentCleaner}) each.  Two
 * rules decide without a model: a declared charset whose decode equals another candidate's
 * wins (unless a structural proof contradicts it), and identical decodes abstain.  Otherwise
 * {@link HashedArbitration} decides with the bundled {@link HashedJunkModel}: the decode with
 * the highest pairwise value wins, unless its margin over the runner-up is below the model's
 * deferral threshold, in which case the structural anchor, else the declared one, wins.
 *
 * <p>{@link #candidates} reports what the filter weighs for a stream, for trainers and
 * diagnostics.  When the model cannot be loaded the filter is a no-op and the composite falls
 * back to its default ordering.
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

    /** {@code null} when the bundled model failed to load: the filter is then a no-op. */
    private final HashedJunkModel model;

    private int readLimit = DEFAULT_READ_LIMIT;

    public JunkFilterEncodingDetector() {
        // A load failure is logged and leaves the filter a no-op rather than failing detection.
        HashedJunkModel m = null;
        try {
            m = JunkDetector.loadFromClasspath().getModel();
        } catch (Throwable t) {
            LOG.warn("Failed to load the junk model; JunkFilterEncodingDetector "
                    + "will operate as a no-op: {}", t.toString());
        }
        this.model = m;
    }

    /** With an explicit model; {@code null} gives a no-op filter whose {@link #candidates} still works. */
    public JunkFilterEncodingDetector(HashedJunkModel model) {
        this.model = model;
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
        if (model == null) {
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
        Candidates prepared = prepare(bytes, context);
        switch (prepared.getRule()) {
            case TOO_FEW_TEXTS:
                LOG.trace("junk-filter <=1 usable candidate, abstaining");
                return Collections.emptyList();
            case PREFER_STRUCTURAL:
                context.setArbitrationInfo("junk-filter-prefer-structural");
                LOG.trace("junk-filter -> {} (structural proof overrides the declaration)",
                        prepared.getRuleCharset().name());
                return List.of(new EncodingResult(prepared.getRuleCharset(),
                        context.getTopConfidenceFor(prepared.getRuleCharset())));
            case PREFER_DECLARATIVE:
                context.setArbitrationInfo("junk-filter-prefer-declarative");
                LOG.trace("junk-filter -> {} (declarative with equivalent decode)",
                        prepared.getRuleCharset().name());
                return result(prepared.getRuleCharset(), bytes, context);
            case IDENTICAL_DECODINGS: {
                // Text cannot tell them apart.  A validated BOM is then the only discriminating
                // evidence (it also tells the reader to drop the BOM bytes); else the legacy-page
                // frequency prior; else the composite's default ordering.
                Map<Charset, String> texts = prepared.getTexts();
                Charset bom = bomHinted(context, texts);
                if (bom != null) {
                    context.setArbitrationInfo("junk-filter-identical-decodings-bom");
                    return List.of(new EncodingResult(bom, context.getTopConfidenceFor(bom)));
                }
                Charset frequent = mostFrequentLegacy(texts.keySet());
                if (frequent != null) {
                    context.setArbitrationInfo("junk-filter-identical-decodings-frequency");
                    return List.of(new EncodingResult(frequent, context.getTopConfidenceFor(frequent)));
                }
                context.setArbitrationInfo("junk-filter-identical-decodings");
                LOG.trace("junk-filter all decodings identical, deferring");
                return Collections.emptyList();
            }
            default:
                break;
        }

        HashedArbitration.Decision decision = HashedArbitration.decide(model, prepared);
        Charset pick = decision.pick();
        boolean deferred = !pick.equals(decision.getChampion());
        context.setArbitrationInfo(deferred ? "junk-filter-hashed-deferred" : "junk-filter-hashed");
        LOG.trace("junk-filter -> {} (champion {}, margin {}, anchor {})", pick.name(),
                decision.getChampion().name(), decision.getLogit(), decision.getAnchor());
        return result(pick, bytes, context);
    }

    /** {@code pick}, or its windows-125x sibling when the probe's C1 bytes prove it (see {@link #windowsSibling}). */
    private static List<EncodingResult> result(Charset pick, byte[] bytes, EncodingDetectorContext context) {
        float confidence = context.getTopConfidenceFor(pick);
        Charset sibling = windowsSibling(pick, bytes);
        if (sibling != null) {
            context.setArbitrationInfo(context.getArbitrationInfo() + "-c1-sibling");
            return List.of(new EncodingResult(sibling, confidence));
        }
        return List.of(new EncodingResult(pick, confidence));
    }

    private static final Map<String, String> WINDOWS_SIBLING = Map.of(
            "ISO-8859-1", "windows-1252", "ISO-8859-15", "windows-1252", "ISO-8859-2", "windows-1250",
            "ISO-8859-7", "windows-1253", "ISO-8859-8", "windows-1255", "ISO-8859-9", "windows-1254",
            "ISO-8859-13", "windows-1257");

    /**
     * The windows-125x sibling of an ISO-8859 {@code pick}, or {@code null}. It applies when the two
     * cleaned decodes differ only where the ISO side has a C1 control (0x80-0x9F) that the sibling
     * prints: such a byte is punctuation typed in windows-125x (… “ ” €), never ISO-8859 text, and
     * a declaration does not outweigh it (gold rule). A letter difference means the text decides,
     * so the pick stands. A C1 byte after a high byte is likely a UTF-8 continuation (Ã + 0x9F for
     * ß), which is mojibake, not evidence.
     */
    static Charset windowsSibling(Charset pick, byte[] bytes) {
        String name = WINDOWS_SIBLING.get(pick.name());
        if (name == null || !hasC1Evidence(bytes)) {
            return null;
        }
        Charset windows;
        try {
            windows = Charset.forName(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String iso = HtmlContentCleaner.clean(new String(bytes, pick));
        String win = HtmlContentCleaner.clean(new String(bytes, windows));
        if (iso.length() != win.length()) {
            return null;
        }
        boolean differs = false;
        for (int i = 0; i < iso.length(); i++) {
            char a = iso.charAt(i);
            char b = win.charAt(i);
            if (a == b) {
                continue;
            }
            if (a < 0x80 || a > 0x9F || b == '�' || Character.isISOControl(b)) {
                return null;
            }
            differs = true;
        }
        return differs ? windows : null;
    }

    /** A 0x80-0x9F byte that follows an ASCII byte (or starts the probe). */
    private static boolean hasC1Evidence(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            if (v >= 0x80 && v <= 0x9F && (i == 0 || (bytes[i - 1] & 0xFF) < 0x80)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCjkCharset(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.contains("gb") || n.contains("big5") || n.contains("euc")
                || n.contains("shift") || n.contains("jis") || n.contains("2022")
                || n.contains("949");
    }

    /**
     * What this filter arbitrates for {@code tis}: the probe, the charsets the candidate band keeps,
     * their cleaned decodes, the anchors, and the rule (if any) that decides before the quality
     * model.  For trainers and diagnostics; it reads the probe {@link #detect} reads, from the
     * context's probe cache when there is one.
     *
     * @return {@code null} without a stream or context, or for an empty probe
     */
    public Candidates candidates(TikaInputStream tis, EncodingDetectorContext context)
            throws IOException {
        if (tis == null || context == null) {
            return null;
        }
        byte[] bytes = readProbe(tis, context);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return prepare(stripBomBytes(bytes), context);
    }

    private static Candidates prepare(byte[] bytes, EncodingDetectorContext context) {
        // Decode each candidate, then HtmlContentCleaner.clean — the same
        // tag-strip + entity-expand the trainer applies, so train and
        // inference match.  Entity expansion is load-bearing: numeric refs
        // become codepoints whose cross-script transitions expose mojibake
        // under a wrong decoding (AIT5 case).
        Map<Charset, String> texts = new LinkedHashMap<>();
        // Dedup: charsets that decode the raw probe to the identical string
        // (e.g. GB18030/GBK, x-windows-949/EUC-KR on non-extension content)
        // share one clean() call — the cleaned result is identical by
        // construction, so this is quality-neutral, purely a work saving.
        Map<String, String> cleanedByRaw = new HashMap<>();
        Set<Charset> band = bandFilter(context, context.getUniqueCharsets());
        for (Charset cs : band) {
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
                texts.put(cs, decoded);
            }
        }
        Charset structural = firstAnchor(context, texts, EncodingResult.ResultType.STRUCTURAL);
        Charset declaredAnchor = firstAnchor(context, texts, EncodingResult.ResultType.DECLARATIVE);
        Candidates.Rule rule = Candidates.Rule.MODEL;
        Charset ruleCharset = null;
        if (texts.size() <= 1) {
            rule = Candidates.Rule.TOO_FEW_TEXTS;
        } else {
            // When a DECLARATIVE candidate decodes byte-identically to at least
            // one other candidate, honour the declaration — text quality cannot
            // distinguish equivalent decodings, and a downstream divergent byte
            // (e.g. 0xA4 = € in ISO-8859-15, ¤ in windows-1252) will then be
            // decoded as the author intended.
            Charset declared = pickDeclarativeWithEquivalentDecode(context, texts);
            if (declared != null) {
                // A STRUCTURAL proof (valid multi-byte UTF-8, ISO-2022) outranks a
                // DECLARATIVE self-claim on conflict.  The equivalent-decode shortcut
                // compares tag-stripped text, so a doc whose only high bytes live in
                // markup (a <meta> attribute, an <o:..> tag) ties on visible text and
                // would honour a declaration the bytes contradict.  Defer to the proof
                // (CJK-declaration guard in #overridesDeclared).
                Charset proof = conflictingStructural(context, texts, declared);
                if (proof != null && overridesDeclared(proof, declared, bytes)) {
                    rule = Candidates.Rule.PREFER_STRUCTURAL;
                    ruleCharset = proof;
                } else {
                    rule = Candidates.Rule.PREFER_DECLARATIVE;
                    ruleCharset = declared;
                }
            } else if (allDecodingsIdentical(texts)) {
                // No DECLARATIVE candidate and text quality cannot tell them apart.
                rule = Candidates.Rule.IDENTICAL_DECODINGS;
            }
        }
        return new Candidates(bytes, band, texts, structural, declaredAnchor, rule, ruleCharset);
    }

    /** First result of {@code type}, in detector order, whose charset produced a usable decode. */
    private static Charset firstAnchor(EncodingDetectorContext context, Map<Charset, String> texts,
                                       EncodingResult.ResultType type) {
        for (EncodingDetectorContext.Result r : context.getResults()) {
            for (EncodingResult er : r.getEncodingResults()) {
                if (er.getResultType() == type && texts.containsKey(er.getCharset())) {
                    return er.getCharset();
                }
            }
        }
        return null;
    }

    /** What {@link #candidates} reports: the junk filter's view of one probe. */
    public static final class Candidates {

        /** What decides the charset for these candidates. */
        public enum Rule {
            /** The quality model arbitrates. */
            MODEL,
            /** At most one candidate decoded to usable text: the filter abstains. */
            TOO_FEW_TEXTS,
            /** A declared charset decodes like another candidate: the declaration wins. */
            PREFER_DECLARATIVE,
            /** As {@link #PREFER_DECLARATIVE}, but a structural proof contradicts the declaration. */
            PREFER_STRUCTURAL,
            /** Every candidate decodes to the same text and none is declared: the filter abstains. */
            IDENTICAL_DECODINGS
        }

        private final byte[] probe;
        private final List<Charset> band;
        private final Map<Charset, String> texts;
        private final Charset structural;
        private final Charset declared;
        private final Rule rule;
        private final Charset ruleCharset;

        Candidates(byte[] probe, Set<Charset> band, Map<Charset, String> texts, Charset structural,
                   Charset declared, Rule rule, Charset ruleCharset) {
            this.probe = probe;
            this.band = Collections.unmodifiableList(new ArrayList<>(band));
            this.texts = Collections.unmodifiableMap(texts);
            this.structural = structural;
            this.declared = declared;
            this.rule = rule;
            this.ruleCharset = ruleCharset;
        }

        /** The probe bytes the decodes come from, after any byte-order mark. */
        public byte[] getProbe() {
            return probe.clone();
        }

        /** Every charset the candidate band keeps, including those that failed to decode. */
        public List<Charset> getBandCharsets() {
            return band;
        }

        /** Cleaned decode of each band charset that produced text, in band order. */
        public Map<Charset, String> getTexts() {
            return texts;
        }

        /** First STRUCTURAL result with a decode, or {@code null}. */
        public Charset getStructural() {
            return structural;
        }

        /** First DECLARATIVE result with a decode, or {@code null}. */
        public Charset getDeclared() {
            return declared;
        }

        public Rule getRule() {
            return rule;
        }

        /** The charset a {@code PREFER_*} rule picks; {@code null} for the other rules. */
        public Charset getRuleCharset() {
            return ruleCharset;
        }
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

    /** A decoded STRUCTURAL candidate other than the declared charset — evidence
     *  that can outrank the declaration; {@code null} if none. */
    private static Charset conflictingStructural(EncodingDetectorContext context,
            Map<Charset, String> candidates, Charset declared) {
        for (EncodingDetectorContext.Result r : context.getResults()) {
            for (EncodingResult er : r.getEncodingResults()) {
                if (er.getResultType() != EncodingResult.ResultType.STRUCTURAL) {
                    continue;
                }
                Charset cs = er.getCharset();
                if (!cs.equals(declared) && candidates.containsKey(cs)) {
                    return cs;
                }
            }
        }
        return null;
    }

    /** Whether a conflicting STRUCTURAL proof overrides the declaration.  UTF-8 vs
     *  a variable-length CJK declaration is ambiguous on a short probe (a 2-byte CJK
     *  pair can fake a 2-byte UTF-8 char, never a wide one) → require a wide
     *  sequence; all other proofs are unambiguous. */
    private static boolean overridesDeclared(Charset structural, Charset declared,
            byte[] bytes) {
        if (StandardCharsets.UTF_8.equals(structural) && isCjkCharset(declared.name())) {
            return StructuralEncodingRules.hasWideUtf8Sequence(bytes);
        }
        return true;
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
        int bomLen = BOMDetector.bomLength(bytes);
        if (bomLen == 0) {
            return bytes;
        }
        return Arrays.copyOfRange(bytes, bomLen, bytes.length);
    }

    private static final String BOM_DETECTOR = BOMDetector.class.getSimpleName();

    /**
     * {@link BOMDetector}'s result.  The context records detectors by simple class name
     * (see {@code CompositeEncodingDetector}); a BOM is a hint, not a vote of the statistical layer.
     */
    private static boolean isBomResult(EncodingDetectorContext.Result r) {
        return BOM_DETECTOR.equals(r.getDetectorName());
    }

    /** The candidate {@link BOMDetector} reported, if it survived decoding. */
    private static Charset bomHinted(EncodingDetectorContext context, Map<Charset, String> candidates) {
        for (EncodingDetectorContext.Result r : context.getResults()) {
            if (isBomResult(r) && candidates.containsKey(r.getCharset())) {
                return r.getCharset();
            }
        }
        return null;
    }

    private static final Map<String, Long> LEGACY_FREQUENCY = loadLegacyFrequency();

    private static Map<String, Long> loadLegacyFrequency() {
        Map<String, Long> m = new HashMap<>();
        try (java.io.InputStream in = JunkFilterEncodingDetector.class
                .getResourceAsStream("legacy-charset-frequency.txt")) {
            if (in == null) {
                throw new IllegalStateException("legacy-charset-frequency.txt missing");
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\t");
                m.put(f[0], Long.parseLong(f[1].trim()));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return m;
    }

    /**
     * Among candidates that decode identically, the most frequent on legacy-charset web pages: with
     * identical text the bytes carry no evidence, so the prior decides. Null when a Unicode charset
     * is tied (the frequency table covers legacy pages only) or nothing is more frequent than the rest.
     */
    private static Charset mostFrequentLegacy(Set<Charset> candidates) {
        Charset best = null;
        long bestCount = 0;
        boolean tie = false;
        for (Charset cs : candidates) {
            if (cs.name().startsWith("UTF-") || cs.name().equals("US-ASCII")) {
                return null;
            }
            Long count = LEGACY_FREQUENCY.get(cs.name());
            if (count == null) {
                count = LEGACY_FREQUENCY.getOrDefault(CharsetSupersets.jdkDecodeAs(cs).name(), 0L);
            }
            if (count > bestCount) {
                best = cs;
                bestCount = count;
                tie = false;
            } else if (count == bestCount) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

}
