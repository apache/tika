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
package org.apache.tika.langdetect.charsoup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.detect.encoding.LinearModel;

/**
 * Defines symmetric confusable groups for language detection.
 * <p>
 * Languages within the same group are treated as lenient matches during
 * evaluation: predicting any member of a group when the true label is another
 * member is not penalised as a hard error. This reflects genuine linguistic
 * ambiguity — e.g. Bosnian and Croatian are often indistinguishable on short
 * text regardless of model quality.
 * <p>
 * At inference time the same groups are used to collapse probability mass:
 * all probability within a group is summed and assigned to the top-scoring
 * member, so the model reports group-level confidence rather than a noisy
 * choice within closely related languages.
 * <p>
 * All groups are symmetric (no directional superset relationships). Unlike
 * charset detection, there is no meaningful "superset" language concept here.
 */
public final class LanguageConfusables {

    /**
     * Symmetric confusable groups. Each inner array is an unordered set of
     * ISO 639-3 tags whose members are mutually confusable.
     */
    public static final String[][] GROUPS = {
            {"nob", "nno", "nor", "dan"},        // Scandinavian: Norwegian variants + Danish
            {"hrv", "srp", "bos", "hbs"},        // South Slavic: Croatian, Serbian, Bosnian
            {"msa", "zlm", "zsm", "ind"},        // Malay / Indonesian cluster
            {"ara", "arz", "acm", "apc"},        // Arabic varieties
            {"fas", "pes", "prs"},               // Persian / Dari
            {"zho", "cmn"},                      // Generic Chinese / Mandarin — same tag in practice
            {"aze", "azj"},                      // Azerbaijani variants
            {"ekk", "est"},                      // Estonian variants
            {"lvs", "lav"},                      // Latvian variants
            {"plt", "mlg"},                      // Malagasy variants
            {"khk", "mon"},                      // Mongolian variants
            {"ydd", "yid"},                      // Yiddish variants
            {"sme", "smi"},                      // Sami variants
            {"sqi", "als"},                      // Albanian / Tosk Albanian
            {"tat", "bak"},                      // Tatar / Bashkir
            {"ita", "vec"},                      // Italian / Venetian
            {"spa", "arg", "ast"},               // Spanish / Aragonese / Asturian
            {"por", "glg"},                      // Portuguese / Galician
            {"ces", "slk"},                      // Czech / Slovak
            {"bel", "rus", "ukr"},               // East Slavic (closely related, not just script)
    };

    // Precomputed: label → set of confusable labels (excluding self)
    private static final Map<String, String[]> CONFUSABLE_MAP = buildMap();

    private static Map<String, String[]> buildMap() {
        Map<String, String[]> map = new HashMap<>();
        for (String[] group : GROUPS) {
            for (String lang : group) {
                String[] others = Arrays.stream(group)
                        .filter(l -> !l.equals(lang))
                        .toArray(String[]::new);
                map.put(lang, others);
            }
        }
        return map;
    }

    private LanguageConfusables() {
    }

    /**
     * Returns {@code true} if predicting {@code predicted} when the true
     * label is {@code actual} should be considered a lenient match.
     * This is true when both labels belong to the same confusable group,
     * or when {@code actual.equals(predicted)}.
     *
     * @param actual    true language tag (ISO 639-3)
     * @param predicted model's predicted language tag
     * @return true if the prediction is a strict or lenient match
     */
    public static boolean isLenientMatch(String actual, String predicted) {
        if (actual.equals(predicted)) {
            return true;
        }
        String[] confusables = CONFUSABLE_MAP.get(actual);
        if (confusables == null) {
            return false;
        }
        for (String c : confusables) {
            if (c.equals(predicted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build a mapping from each class index in the model to the array of all
     * class indices in its confusable group (including itself). Classes not in
     * any group map to a singleton containing only themselves (no-op during
     * probability collapsing).
     *
     * @param model the loaded language model
     * @return per-class group index arrays, length {@code model.getNumClasses()}
     */
    public static int[][] buildGroupIndices(LinearModel model) {
        Map<String, Integer> labelIdx = new HashMap<>();
        for (int i = 0; i < model.getNumClasses(); i++) {
            labelIdx.put(model.getLabel(i), i);
        }

        int[][] result = new int[model.getNumClasses()][];
        boolean[] assigned = new boolean[model.getNumClasses()];

        for (String[] group : GROUPS) {
            List<Integer> members = new ArrayList<>();
            for (String lang : group) {
                Integer idx = labelIdx.get(lang);
                if (idx != null) {
                    members.add(idx);
                }
            }
            if (members.size() >= 2) {
                int[] memberArr = members.stream().mapToInt(Integer::intValue).toArray();
                for (int idx : memberArr) {
                    result[idx] = memberArr;
                    assigned[idx] = true;
                }
            }
        }

        // Singletons: classes not in any active group
        for (int i = 0; i < result.length; i++) {
            if (!assigned[i]) {
                result[i] = new int[]{i};
            }
        }
        return result;
    }

    /**
     * Collapse confusable group probabilities: sum each group's probabilities
     * and assign the total to the highest-scoring member. Other members get 0.
     * Returns a new array; the input is not modified.
     *
     * @param probs        softmax probability array (length = numClasses)
     * @param groupIndices output of {@link #buildGroupIndices(LinearModel)}
     * @return new probability array with group mass collapsed to top scorer
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
}
