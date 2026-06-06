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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Candidate charset-agnostic features for use in the Phase-2 feature study
 * (see plan {@code wild-roaming-whale}).  All features are pure functions of a
 * decoded {@code String} so they can be computed by the eval harness without
 * touching the trained bigram model.  Intended to migrate into
 * {@link JunkDetector}'s feature vector after the study identifies the
 * non-redundant subset.
 *
 * <p>Also hosts the {@link StripMode} enum and {@link #strip} entry point.
 * The current production behaviour
 * ({@code JunkFilterEncodingDetector.stripCommonCodepoints}) corresponds to
 * {@link StripMode#ALL_COMMON} — it strips every COMMON / INHERITED /
 * UNKNOWN codepoint before scoring.  The user observation in the plan was
 * that this is too aggressive: it removes pilcrows and other punctuation
 * marks that are themselves mojibake fingerprints.  The other modes let the
 * eval measure that empirically.
 */
public final class TextQualityFeatures {

    private TextQualityFeatures() {
    }

    // -----------------------------------------------------------------------
    // Memoized Unicode-script lookup
    // -----------------------------------------------------------------------

    /** Cached {@code UnicodeScript.values()} so an ordinal-&gt;enum lookup never
     *  re-allocates the values array. */
    private static final Character.UnicodeScript[] SCRIPT_VALUES =
            Character.UnicodeScript.values();

    /**
     * Memoized {@link Character.UnicodeScript#of(int)} for the BMP.  Scoring a
     * document classifies every codepoint's script ~5 times (z4/z7/z8/z9 plus the
     * z1 bigram bucketing), and {@code UnicodeScript.of} is a binary search over
     * the script-range table (measured ~12 ns/cp, 10-20x {@code Character.getType}).
     * The result is a pure function of the codepoint for a given JVM, so cache it:
     * BMP codepoints (&gt;99% of text) become an O(1) array lookup after first
     * sight, shared across every call site and every document.  Slot 0 means "not
     * yet computed"; otherwise {@code ordinal + 1}.  The fill is a benign data race
     * — every writer stores the same deterministic value and {@code short} writes
     * do not tear.
     */
    private static final short[] BMP_SCRIPT_CACHE = new short[0x10000];

    /**
     * Script of {@code codePoint}, memoized for the BMP — identical result to
     * {@link Character.UnicodeScript#of(int)} (the same singleton enum constant).
     */
    static Character.UnicodeScript scriptOf(int codePoint) {
        if (codePoint >= 0 && codePoint < 0x10000) {
            short v = BMP_SCRIPT_CACHE[codePoint];
            if (v != 0) {
                return SCRIPT_VALUES[v - 1];
            }
            Character.UnicodeScript s = Character.UnicodeScript.of(codePoint);
            BMP_SCRIPT_CACHE[codePoint] = (short) (s.ordinal() + 1);
            return s;
        }
        return Character.UnicodeScript.of(codePoint);
    }

    // -----------------------------------------------------------------------
    // Strip modes
    // -----------------------------------------------------------------------

    public enum StripMode {
        /** No stripping — pass text through unchanged. */
        NONE,
        /**
         * Strip only Unicode whitespace (per
         * {@link Character#isWhitespace(int)}).  Keeps punctuation, digits,
         * Latin-1 Supplement symbols (¶ © ÷ etc.) — the signals that
         * distinguish mojibake from clean text.
         */
        WHITESPACE,
        /**
         * Strip whitespace plus control characters and format characters
         * (general categories Cc, Cf).  Still keeps printable punctuation.
         */
        WHITESPACE_CONTROL,
        /**
         * Production behaviour today: strip every COMMON, INHERITED, and
         * UNKNOWN-script codepoint.  Removes everything in the BMP that is
         * not script-tagged — including printable punctuation, digits, and
         * Latin-1 supplement symbols.
         */
        ALL_COMMON
    }

    public static String strip(String text, StripMode mode) {
        if (text == null || text.isEmpty() || mode == StripMode.NONE) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (shouldStrip(cp, mode)) {
                continue;
            }
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    private static boolean shouldStrip(int cp, StripMode mode) {
        switch (mode) {
            case WHITESPACE:
                return Character.isWhitespace(cp);
            case WHITESPACE_CONTROL: {
                if (Character.isWhitespace(cp)) {
                    return true;
                }
                int type = Character.getType(cp);
                return type == Character.CONTROL || type == Character.FORMAT;
            }
            case ALL_COMMON: {
                Character.UnicodeScript s = scriptOf(cp);
                return s == Character.UnicodeScript.COMMON
                        || s == Character.UnicodeScript.INHERITED
                        || s == Character.UnicodeScript.UNKNOWN;
            }
            default:
                return false;
        }
    }

    // -----------------------------------------------------------------------
    // Candidate features (z5..z9 of the plan)
    // -----------------------------------------------------------------------

    /**
     * z6: fraction of codepoints that are letters
     * ({@link Character#isLetter(int)}).  Polish {@code ciśnienia} ≈ 1.0;
     * {@code ci¶nienia} &lt; 1.0 because {@code ¶} is not a letter.
     */
    public static double alphabeticRatio(String text) {
        if (text == null || text.isEmpty()) {
            return Double.NaN;
        }
        int total = 0;
        int letters = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            total++;
            if (Character.isLetter(cp)) {
                letters++;
            }
        }
        return total == 0 ? Double.NaN : (double) letters / total;
    }

    /**
     * z5: fraction of adjacent codepoint pairs where both codepoints are
     * letters in the same script cluster.  Script cluster groups
     * HAN + HIRAGANA + KATAKANA + HANGUL + BOPOMOFO (CJK) into one cluster
     * so Japanese mixed text and Korean Hanja text count as same-cluster
     * pairs; all other scripts are their own cluster.
     *
     * <p>Polish {@code ciśnienia} → 1.0 (every adjacent pair is two LATIN
     * letters).  {@code ci¶nienia} → 0.75 (the two pairs involving {@code ¶}
     * fail the both-letters test).
     */
    public static double letterPairDensity(String text) {
        if (text == null || text.length() < 2) {
            return Double.NaN;
        }
        int[] cps = text.codePoints().toArray();
        if (cps.length < 2) {
            return Double.NaN;
        }
        int pairs = 0;
        int matches = 0;
        for (int i = 0; i + 1 < cps.length; i++) {
            int a = cps[i];
            int b = cps[i + 1];
            pairs++;
            if (Character.isLetter(a) && Character.isLetter(b)
                    && sameScriptCluster(a, b)) {
                matches++;
            }
        }
        return pairs == 0 ? Double.NaN : (double) matches / pairs;
    }

    /**
     * z7: Shannon entropy (in bits) of the distribution of distinct
     * codepoints in the high-byte range (cp &gt;= 0x80).  Clean text uses a
     * small alphabet there; CJK-as-Latin mojibake (the {@code Ã†Â…} storm)
     * fans out across many distinct codepoints, raising entropy.  Returns
     * 0 if no high-byte codepoints are present.
     */
    public static double highByteEntropy(String text) {
        if (text == null || text.isEmpty()) {
            return Double.NaN;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        int total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x80) {
                counts.merge(cp, 1, Integer::sum);
                total++;
            }
        }
        if (total == 0) {
            return 0.0;
        }
        double h = 0;
        for (int c : counts.values()) {
            double p = (double) c / total;
            h -= p * (Math.log(p) / Math.log(2));
        }
        return h;
    }

    /**
     * z6 raw input: fraction of codepoints that are "anomaly indicators" —
     * codepoints that shouldn't appear in correctly-decoded natural text.
     * Direct decode-failure signal — wrong encodings produce these in bulk
     * (Java's CharsetDecoder emits U+FFFD per malformed byte; ISO-8859-X
     * misreads windows-1252 high bytes as C1 controls; PDF cmap failures
     * emit private-use codepoints; etc.).
     *
     * <p>Anomaly set:
     * <ul>
     *   <li>U+FFFD (REPLACEMENT CHARACTER) — the direct decode-failure marker
     *   <li>Anomalous Cc: {@code 0x01-0x08, 0x0B, 0x0C, 0x0E-0x1F, 0x7F}
     *       (matching z3's byte-level anomaly definition, at codepoint level)
     *   <li>C1 control codepoints: {@code U+0080-U+009F} — the
     *       ISO-8859-X-misdecodes-windows-1252 signal
     *   <li>Private use area: {@code U+E000-U+F8FF}, plus planes 15-16 PUA —
     *       the PDF cmap-failure signal
     * </ul>
     *
     * <p>Continuous (not a binary threshold) so the JunkDetector combiner LR
     * can learn a proportional weight on it.  Excluded: 0x00 (NUL — can
     * occur legitimately in some text streams; matches z3's exclusion);
     * 0x09/0x0A/0x0D/0x20 (legitimate whitespace); Cf format chars (ZWJ etc.
     * have legitimate linguistic uses); Cn unassigned (rare in practice).
     */
    public static double replacementRatio(String text) {
        if (text == null || text.isEmpty()) {
            return Double.NaN;
        }
        int denom = 0;     // high bytes + sub-0x80 anomalies = codepoints that CAN be decode failures
        int anomaly = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            boolean anom = isAnomalyCodepoint(cp);
            if (anom) {
                anomaly++;
            }
            if (cp >= 0x80 || anom) {
                denom++;
            }
        }
        // Denominator is non-ASCII (+ sub-0x80 control anomalies), NOT total
        // codepoints: anomalies only arise from undecodable HIGH bytes, so this is
        // the fraction "of the bytes that COULD fail, how many did" — undiluted by
        // ASCII.  PURE ratio (no smoothing, no min-denom cliff): a page whose few
        // non-ASCII chars all failed (1 FFFD / 1 non-ASCII = 1.0) is a strong wrong-
        // charset signal and must register fully, while a content-rich page with a
        // few stray FFFD has a naturally-tiny ratio (1/500 ≈ 0).  This is the signal
        // that distinguishes Latin wrong-charset (ratio→1) from CJK mixed-encoding
        // (ratio→0.06).  All-ASCII (denom 0) → 0 (clean).
        return denom == 0 ? 0.0 : (double) anomaly / denom;
    }

    /** True if {@code cp} is in the z6 anomaly set: U+FFFD, anomalous Cc
     *  (matching z3 byte-level definition), C1 controls, or private use. */
    static boolean isAnomalyCodepoint(int cp) {
        if (cp == 0xFFFD) {
            return true;
        }
        // Anomalous Cc (excludes 0x00, 0x09, 0x0A, 0x0D — match z3)
        if ((cp >= 0x01 && cp <= 0x08)
                || cp == 0x0B || cp == 0x0C
                || (cp >= 0x0E && cp <= 0x1F)
                || cp == 0x7F) {
            return true;
        }
        // C1 controls — the ISO-8859-X-misreads-windows-1252 signal
        if (cp >= 0x0080 && cp <= 0x009F) {
            return true;
        }
        // Private use area (BMP)
        if (cp >= 0xE000 && cp <= 0xF8FF) {
            return true;
        }
        // Supplementary PUA (planes 15 and 16)
        if (cp >= 0xF0000 && cp <= 0x10FFFD) {
            return true;
        }
        return false;
    }

    /**
     * Raw count of U+FFFD codepoints.  Kept for diagnostics — the per-record
     * eval TSV emits both the count (for easy spot-checking of "how bad was
     * this decode") and the ratio (the trainable feature).
     */
    public static int replacementCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xFFFD) {
                n++;
            }
        }
        return n;
    }

    /**
     * z10: fraction of codepoints that are combining or spacing marks
     * (Unicode general categories Mn, Mc, Me).  Real Vietnamese / Indic /
     * Thai / Arabic text uses combining marks heavily (Vietnamese ~30 %);
     * mojibake from re-decoding precomposed scripts as Latin-1 has zero.
     * Companion / corrective signal to {@link #alphabeticRatio}, which is
     * backwards on Vietnamese cohorts because marks aren't letters.
     * Works on a single 5-codepoint word.
     */
    public static double combiningMarkRatio(String text) {
        if (text == null || text.isEmpty()) {
            return Double.NaN;
        }
        int total = 0;
        int marks = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            total++;
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                marks++;
            }
        }
        return total == 0 ? Double.NaN : (double) marks / total;
    }

    /**
     * z11: fraction of adjacent codepoint pairs where the first is a
     * letter and the second is a combining or spacing mark.  Bigram-shaped
     * companion to {@link #combiningMarkRatio} — direct positive signal
     * for "letter wearing decoration," which is what correct Vietnamese /
     * Indic / Thai decoding produces and Latin-1 mojibake of those does
     * not.
     */
    public static double letterAdjacentToMarkRatio(String text) {
        if (text == null || text.length() < 2) {
            return Double.NaN;
        }
        int[] cps = text.codePoints().toArray();
        if (cps.length < 2) {
            return Double.NaN;
        }
        int pairs = 0;
        int hits = 0;
        for (int i = 0; i + 1 < cps.length; i++) {
            pairs++;
            int a = cps[i];
            int b = cps[i + 1];
            if (Character.isLetter(a)) {
                int type = Character.getType(b);
                if (type == Character.NON_SPACING_MARK
                        || type == Character.COMBINING_SPACING_MARK
                        || type == Character.ENCLOSING_MARK) {
                    hits++;
                }
            }
        }
        return pairs == 0 ? Double.NaN : (double) hits / pairs;
    }

    /**
     * Fraction of codepoints assigned to a "real" script (i.e. not in
     * COMMON / INHERITED / UNKNOWN).  Pure-whitespace, pure-digit, and
     * pure-punctuation text score 0; mostly-letter text scores near 1.
     *
     * <p>Used by JunkDetector's "no scoreable script" fallback classifier
     * (the "NONE" model) to distinguish "real text in an unmodeled
     * script" (high density, low fragmentation → modestly positive
     * signal) from "all-whitespace / digit-only content" (zero density
     * → strong negative signal in JunkDetector's bigram-based judgment,
     * mild signal for general-purpose junk filtering).
     *
     * <p><strong>U+FFFD is excluded</strong> from both numerator and
     * denominator: it is a decode-failure marker scored by the dedicated
     * replacement-char feature (z6), so counting it here too would (a) double-
     * count FFFD and (b) re-create the FFFD-drag when this feature dominates —
     * a permissive wrong decode (few FFFD) would out-score a correct mixed-
     * encoding decode (many FFFD from undecodable widget bytes).  This measures
     * the composition of the *decodable* content only.
     */
    public static double scriptDensity(String text) {
        if (text == null || text.isEmpty()) {
            return Double.NaN;
        }
        int total = 0;
        int scripted = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xFFFD) {
                continue;
            }
            total++;
            Character.UnicodeScript s = scriptOf(cp);
            if (s != Character.UnicodeScript.COMMON
                    && s != Character.UnicodeScript.INHERITED
                    && s != Character.UnicodeScript.UNKNOWN) {
                scripted++;
            }
        }
        return total == 0 ? Double.NaN : (double) scripted / total;
    }

    /**
     * Fragmentation of script-bearing codepoints across distinct scripts:
     * {@code 1 - longest_same_script_run_length / total_script_codepoints}.
     * Coherent one-script text scores 0 (no fragmentation); script-salad
     * mojibake (many tiny runs across multiple scripts) approaches 1.
     *
     * <p>Combined with {@link #scriptDensity}, distinguishes the four
     * "no-scoreable-script" failure modes:
     * <ul>
     *   <li>All-whitespace / pure-digit: density 0, fragmentation 0
     *       (no scripted codepoints at all).</li>
     *   <li>Real Gothic / unmodeled-but-coherent script: density 1,
     *       fragmentation 0 (one long run).</li>
     *   <li>Script-salad mojibake: density &gt; 0.5, fragmentation
     *       &gt; 0.7 (many short runs across many scripts).</li>
     *   <li>Real multilingual text (e.g. Japanese with romaji): density
     *       1, fragmentation 0.3-0.5 (a handful of long runs).</li>
     * </ul>
     *
     * <p>Returns 0 when text has no script-bearing codepoints (so the
     * caller can rely on {@link #scriptDensity} to discriminate the
     * "no-content" case separately).
     */
    public static double scriptFragmentation(String text) {
        if (text == null || text.isEmpty()) {
            return Double.NaN;
        }
        int totalScripted = 0;
        int longestRun = 0;
        int currentRun = 0;
        String currentScript = null;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript s = scriptOf(cp);
            if (s == Character.UnicodeScript.COMMON
                    || s == Character.UnicodeScript.INHERITED
                    || s == Character.UnicodeScript.UNKNOWN) {
                continue;
            }
            totalScripted++;
            String name = s.name();
            if (name.equals(currentScript)) {
                currentRun++;
            } else {
                if (currentRun > longestRun) {
                    longestRun = currentRun;
                }
                currentScript = name;
                currentRun = 1;
            }
        }
        if (currentRun > longestRun) {
            longestRun = currentRun;
        }
        if (totalScripted == 0) {
            return 0.0; // no scripted content → no fragmentation signal
        }
        return 1.0 - (double) longestRun / totalScripted;
    }

    /**
     * z9: script-alternation ratio — observed transitions over expected
     * transitions under a random-shuffle null.
     *
     * <p>Formally: for a sequence of n non-COMMON codepoints with script
     * proportions {@code p_1, ..., p_k}, the expected number of
     * (transition between different scripts) under random shuffling is
     * {@code (n - 1) * (1 - sum(p_i^2))} — the second factor is
     * Gini-Simpson diversity (probability two random positions differ
     * in script).  This is the Wald-Wolfowitz runs-test statistic
     * generalised to k categories.
     *
     * <p>Returns {@code observed_transitions / expected_transitions}:
     * <ul>
     *   <li>≈ 1 — scripts randomly interleaved (the mojibake signature
     *       when accents are scattered through Latin text — each accent
     *       becomes a singleton Han run, looking random)</li>
     *   <li>&lt; 1 — clumped (normal: words/phrases stay in one script;
     *       English document with embedded Chinese phrase scores 0.05-0.3)</li>
     *   <li>&gt; 1 — more alternating than chance (pathological:
     *       "HLHLHL" patterns)</li>
     * </ul>
     *
     * <p>Length- and proportion-invariant by construction.  COMMON /
     * INHERITED / UNKNOWN codepoints are ignored to keep whitespace
     * and punctuation from dominating the signal in normal text.
     *
     * <p>Returns 0 for single-script documents (no diversity possible).
     */
    public static double scriptAlternationRatio(String text) {
        if (text == null || text.isEmpty()) {
            return Double.NaN;
        }
        // Pass 1: count codepoints per non-COMMON script.
        java.util.Map<String, Integer> scriptCounts = new java.util.HashMap<>();
        int totalScripted = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript s = scriptOf(cp);
            if (s == Character.UnicodeScript.COMMON
                    || s == Character.UnicodeScript.INHERITED
                    || s == Character.UnicodeScript.UNKNOWN) {
                continue;
            }
            scriptCounts.merge(s.name(), 1, Integer::sum);
            totalScripted++;
        }
        if (scriptCounts.size() <= 1 || totalScripted < 2) {
            return 0.0; // single script (or too short) → no alternation possible
        }

        // Gini-Simpson diversity = 1 - sum(p_i^2)
        double sumPiSq = 0;
        for (int c : scriptCounts.values()) {
            double p = (double) c / totalScripted;
            sumPiSq += p * p;
        }
        double expectedTransitions = (totalScripted - 1) * (1.0 - sumPiSq);
        if (expectedTransitions <= 0) {
            return 0.0;
        }

        // Pass 2: count observed transitions between distinct non-COMMON scripts.
        int observedTransitions = 0;
        String prevScript = null;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript s = scriptOf(cp);
            if (s == Character.UnicodeScript.COMMON
                    || s == Character.UnicodeScript.INHERITED
                    || s == Character.UnicodeScript.UNKNOWN) {
                continue;
            }
            String name = s.name();
            if (prevScript != null && !prevScript.equals(name)) {
                observedTransitions++;
            }
            prevScript = name;
        }

        return observedTransitions / expectedTransitions;
    }

    /**
     * Candidate feature (not currently in the classifier): fraction of
     * whitespace-delimited tokens whose letter codepoints all belong to
     * the same script cluster.  Mojibake often produces tokens with
     * mixed-script letters (Latin + Cyrillic + Greek in one "word").
     * Tokens with zero letters are excluded from both numerator and
     * denominator.
     */
    public static double perWordScriptPurity(String text) {
        if (text == null || text.isEmpty()) {
            return Double.NaN;
        }
        int tokens = 0;
        int pureTokens = 0;
        int len = text.length();
        int i = 0;
        while (i < len) {
            // skip whitespace
            int cp = text.codePointAt(i);
            if (Character.isWhitespace(cp)) {
                i += Character.charCount(cp);
                continue;
            }
            // accumulate a token
            int tokenStart = i;
            Set<String> clusters = new HashSet<>();
            int letters = 0;
            while (i < len) {
                int c = text.codePointAt(i);
                if (Character.isWhitespace(c)) {
                    break;
                }
                if (Character.isLetter(c)) {
                    letters++;
                    clusters.add(scriptClusterOf(c));
                }
                i += Character.charCount(c);
            }
            if (letters > 0) {
                tokens++;
                if (clusters.size() == 1) {
                    pureTokens++;
                }
            }
            // tokenStart unused; loop continues with i past the token
            if (i == tokenStart) {
                // safety: never advance past end without consuming
                break;
            }
        }
        return tokens == 0 ? Double.NaN : (double) pureTokens / tokens;
    }

    // -----------------------------------------------------------------------
    // Script-cluster helper (CJK grouped; others stand alone)
    // -----------------------------------------------------------------------

    static boolean sameScriptCluster(int cpA, int cpB) {
        return scriptClusterOf(cpA).equals(scriptClusterOf(cpB));
    }

    private static String scriptClusterOf(int cp) {
        Character.UnicodeScript s = scriptOf(cp);
        switch (s) {
            case HAN:
            case HIRAGANA:
            case KATAKANA:
            case HANGUL:
            case BOPOMOFO:
                return "CJK";
            default:
                return s.name();
        }
    }
}
