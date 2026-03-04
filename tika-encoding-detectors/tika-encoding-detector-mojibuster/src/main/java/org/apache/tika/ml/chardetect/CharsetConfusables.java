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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Charset relationships used for lenient (lenient) evaluation of charset detectors.
 * <p>
 * Two kinds of relationship are modelled:
 *
 * <h3>Symmetric confusable groups</h3>
 * <p>
 * A pair (or larger set) of charsets is <em>symmetrically confusable</em> when
 * a significant fraction of real-world byte sequences are identical under all
 * members, making it unreasonable to penalise a detector for choosing either
 * direction. Examples:
 * <ul>
 *   <li>ISO-8859-1 / ISO-8859-15 / windows-1252 — differ only in 8 code points
 *       and the C1 control range (0x80–0x9F)</li>
 *   <li>UTF-16-LE / UTF-16-BE without a BOM</li>
 *   <li>IBM424-ltr / IBM424-rtl — same code page, differ only in text-reversal
 *       convention</li>
 *   <li>KOI8-R / KOI8-U — share all Cyrillic letters, differ only in four
 *       Ukrainian characters</li>
 * </ul>
 *
 * <h3>Superset / subset chains</h3>
 * <p>
 * Some charsets stand in a strict superset/subset relationship:
 * <ul>
 *   <li>GB2312 ⊂ GBK ⊂ GB18030</li>
 *   <li>Big5 ⊂ Big5-HKSCS</li>
 * </ul>
 * <p>
 * For these, the relationship is <em>directional</em>:
 * <ul>
 *   <li>Predicting the <strong>superset</strong> when the true charset is the
 *       <strong>subset</strong> is always safe — the superset decoder handles
 *       all subset byte sequences correctly (e.g. predicting GB18030 for a
 *       GB2312 file).</li>
 *   <li>Predicting the <strong>subset</strong> when the true charset is the
 *       <strong>superset</strong> is <em>not</em> safe — the subset decoder
 *       may corrupt characters that exist only in the superset (e.g. predicting
 *       GB2312 for a file that uses GB18030-only characters).</li>
 * </ul>
 *
 * <h3>Summary</h3>
 * <p>
 * Use {@link #isLenientMatch(String, String)} for evaluation: it returns
 * {@code true} when the prediction is acceptable given the above rules.
 * Use {@link #GROUPS} + {@link #buildGroupIndices} +
 * {@link #collapseGroups} for inference-time probability pooling (direction
 * does not matter when merging probability mass).
 * <p>
 * This class is kept in sync with {@code CONFUSABLE_GROUPS} and
 * {@code SUPERSET_OF} in {@code build_charset_training.py}.
 */
public final class CharsetConfusables {

    /**
     * All confusable groups (both symmetric and superset chains), used for
     * probability collapsing during inference via {@link #collapseGroups}.
     * Direction does not matter here — we just want to pool probability mass
     * among related charsets and award it to the highest-scoring member.
     */
    public static final List<Set<String>> GROUPS;

    /**
     * Symmetric-only confusable groups. Both directions are equally acceptable
     * as lenient matches (neither charset is "safer" than the other).
     */
    public static final List<Set<String>> SYMMETRIC_GROUPS;

    /**
     * Maps each ISO-8859-X charset to its Windows-12XX equivalent.
     *
     * <p>When bytes in the C1 range ({@code 0x80–0x9F}) are present in a
     * probe, the ISO encoding is ruled out — those byte values are C1 control
     * characters in every ISO-8859-X standard and never appear in real text.
     * The corresponding Windows-12XX encoding uses that range for printable
     * characters (smart quotes, Euro sign, em-dash, …) and is the correct
     * attribution.</p>
     *
     * <p>Note that ISO-8859-15 maps to windows-1252 because ISO-8859-15 also
     * leaves 0x80–0x9F as C1 controls; its differences from ISO-8859-1 are
     * all in the 0xA0–0xFF region.</p>
     */
    public static final Map<String, String> ISO_TO_WINDOWS;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("ISO-8859-1",  "windows-1252");
        m.put("ISO-8859-15", "windows-1252");
        m.put("ISO-8859-2",  "windows-1250");
        m.put("ISO-8859-4",  "windows-1257");
        m.put("ISO-8859-5",  "windows-1251");
        m.put("ISO-8859-6",  "windows-1256");
        m.put("ISO-8859-7",  "windows-1253");
        m.put("ISO-8859-8",  "windows-1255");
        m.put("ISO-8859-9",  "windows-1254");
        m.put("ISO-8859-13", "windows-1257");
        ISO_TO_WINDOWS = Collections.unmodifiableMap(m);
    }

    /**
     * Directional superset relationships: key is a charset, value is its
     * immediate superset. A chain is expressed as successive entries:
     * <pre>
     *   GB2312 → GBK → GB18030
     *   Big5   → Big5-HKSCS
     * </pre>
     * Predicting any ancestor of {@code actual} in this map is a lenient match.
     * Predicting any descendant is <em>not</em> a lenient match.
     */
    public static final Map<String, String> SUPERSET_OF;

    private static final Map<String, Set<String>> SYMMETRIC_PEER_MAP;

    static {
        // ----------------------------------------------------------------
        // Symmetric groups
        // ----------------------------------------------------------------
        List<Set<String>> sym = Arrays.asList(
                // Western European: differ only in 8 code points + C1 range
                group("ISO-8859-1", "ISO-8859-15", "windows-1252"),

                // Central / Eastern European
                group("ISO-8859-2", "windows-1250"),

                // Cyrillic: differ only in C1 range
                group("ISO-8859-5", "windows-1251"),

                // KOI8 family: share all Cyrillic letters
                group("KOI8-R", "KOI8-U"),

                // Arabic
                group("ISO-8859-6", "windows-1256"),

                // Greek
                group("ISO-8859-7", "windows-1253"),

                // Hebrew
                group("ISO-8859-8", "windows-1255"),

                // Turkish / Latin-5
                group("ISO-8859-9", "windows-1254"),

                // Baltic
                group("ISO-8859-4", "ISO-8859-13", "windows-1257"),

                // UTF-16 without BOM: byte-for-byte indistinguishable
                group("UTF-16-LE", "UTF-16-BE"),

                // Hebrew EBCDIC: same IBM424 code page, differ only in text-reversal
                // convention.  Java's Charset registry has "IBM424" but not the
                // directional variants, so our detector returns IBM424 for both.
                group("IBM424-ltr", "IBM424-rtl", "IBM424"),

                // Arabic EBCDIC: same IBM420 code page, differ in reversal convention.
                // Java's Charset registry has "IBM420" but not the directional variants.
                group("IBM420-ltr", "IBM420-rtl", "IBM420"),

                // windows-874 is the training label; Java's canonical name for the
                // same charset is "x-windows-874".  Both refer to the Thai Windows
                // code page (superset of TIS-620).
                group("windows-874", "x-windows-874")
        );
        SYMMETRIC_GROUPS = Collections.unmodifiableList(sym);

        // ----------------------------------------------------------------
        // Directional superset chains
        // ----------------------------------------------------------------
        Map<String, String> supersetOf = new HashMap<>();
        // Simplified Chinese: GB2312 ⊂ GBK ⊂ GB18030
        supersetOf.put("GB2312", "GBK");
        supersetOf.put("GBK", "GB18030");
        // Traditional Chinese: Big5 ⊂ Big5-HKSCS
        supersetOf.put("Big5", "Big5-HKSCS");
        // US-ASCII ⊂ UTF-8: every UTF-8 decoder handles ASCII bytes correctly,
        // so predicting UTF-8 for a US-ASCII file is always safe.
        supersetOf.put("US-ASCII", "UTF-8");
        SUPERSET_OF = Collections.unmodifiableMap(supersetOf);

        // ----------------------------------------------------------------
        // GROUPS = symmetric + one flat group per superset chain
        // (used only for probability collapsing — direction irrelevant here)
        // ----------------------------------------------------------------
        List<Set<String>> all = new ArrayList<>(sym);
        all.add(group("GB2312", "GBK", "GB18030")); // flattened chain
        all.add(group("Big5", "Big5-HKSCS"));
        GROUPS = Collections.unmodifiableList(all);

        // ----------------------------------------------------------------
        // Symmetric peer map for fast lookup
        // ----------------------------------------------------------------
        Map<String, Set<String>> peerMap = new HashMap<>();
        for (Set<String> g : SYMMETRIC_GROUPS) {
            for (String cs : g) {
                Set<String> peers = new HashSet<>(g);
                peers.remove(cs);
                peerMap.put(cs, Collections.unmodifiableSet(peers));
            }
        }
        SYMMETRIC_PEER_MAP = Collections.unmodifiableMap(peerMap);
    }

    private CharsetConfusables() {
    }

    /**
     * Return {@code true} if predicting {@code predicted} when the true charset
     * is {@code actual} is an acceptable ("lenient") result.
     * <p>
     * <strong>Symmetric groups:</strong> both directions are acceptable (e.g.
     * predicting ISO-8859-1 for windows-1252 or vice versa).<br>
     * <strong>Superset chains:</strong> only predicting a <em>superset</em> of
     * {@code actual} is acceptable (e.g. predicting GB18030 for GB2312), because
     * the superset decoder can always handle the subset's byte sequences.
     * Predicting a <em>subset</em> is not acceptable because the subset decoder
     * may corrupt extended characters.
     */
    public static boolean isLenientMatch(String actual, String predicted) {
        if (actual.equals(predicted)) {
            return true;
        }
        // Symmetric group?
        Set<String> symPeers = SYMMETRIC_PEER_MAP.get(actual);
        if (symPeers != null && symPeers.contains(predicted)) {
            return true;
        }
        // Superset chain: walk ancestors of actual to see if predicted is one
        String ancestor = SUPERSET_OF.get(actual);
        while (ancestor != null) {
            if (ancestor.equals(predicted)) {
                return true;
            }
            ancestor = SUPERSET_OF.get(ancestor);
        }
        return false;
    }

    /**
     * Return the set of charsets that are symmetrically confusable with
     * {@code charset}, not including {@code charset} itself.
     * Returns an empty set for charsets not in any symmetric group.
     */
    public static Set<String> symmetricPeersOf(String charset) {
        return SYMMETRIC_PEER_MAP.getOrDefault(charset, Collections.emptySet());
    }

    /**
     * Build a per-class group-index array from a label array (e.g. from a
     * {@link org.apache.tika.ml.LinearModel}), using {@link #GROUPS} (both
     * symmetric and superset chains) for probability collapsing in inference.
     *
     * @param labels class labels in model order
     * @return {@code int[numClasses][]} group-index map
     */
    public static int[][] buildGroupIndices(String[] labels) {
        Map<String, Integer> labelIdx = new HashMap<>();
        for (int i = 0; i < labels.length; i++) {
            labelIdx.put(labels[i], i);
        }

        int[][] result = new int[labels.length][];
        boolean[] assigned = new boolean[labels.length];

        for (Set<String> group : GROUPS) {
            List<Integer> members = new ArrayList<>();
            for (String cs : group) {
                Integer idx = labelIdx.get(cs);
                if (idx != null) {
                    members.add(idx);
                }
            }
            if (members.size() >= 2) {
                int[] arr = members.stream().mapToInt(Integer::intValue).toArray();
                for (int idx : arr) {
                    result[idx] = arr;
                    assigned[idx] = true;
                }
            }
        }
        for (int i = 0; i < result.length; i++) {
            if (!assigned[i]) {
                result[i] = new int[]{i};
            }
        }
        return result;
    }

    /**
     * Collapse confusable group probabilities: within each group, sum all
     * members' probabilities and assign the total to the highest-scoring
     * member; the other members get 0.
     *
     * @param probs        raw softmax output (not modified)
     * @param groupIndices result of {@link #buildGroupIndices(String[])}
     * @return new probability array with group probabilities collapsed
     */
    public static float[] collapseGroups(float[] probs, int[][] groupIndices) {
        float[] collapsed = Arrays.copyOf(probs, probs.length);
        boolean[] visited = new boolean[probs.length];

        for (int i = 0; i < probs.length; i++) {
            if (visited[i] || groupIndices[i].length <= 1) {
                continue;
            }
            int[] group = groupIndices[i];
            float sum = 0f;
            int best = group[0];
            for (int idx : group) {
                sum += probs[idx];
                if (probs[idx] > probs[best]) {
                    best = idx;
                }
                visited[idx] = true;
            }
            for (int idx : group) {
                collapsed[idx] = (idx == best) ? sum : 0f;
            }
        }
        return collapsed;
    }

    private static Set<String> group(String... members) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(members)));
    }
}
