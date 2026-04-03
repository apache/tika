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
package org.apache.tika.langdetect.charsoup.tools;

import java.util.Map;
import java.util.Set;

/**
 * Normalizes FLORES-200 language codes to the canonical codes used by our
 * training pipeline.
 *
 * <p>FLORES-200 uses {@code lang_Script} codes (e.g. {@code zho_Hans},
 * {@code arb_Arab}). Normalization is two steps:
 * <ol>
 *   <li>Strip the script suffix — {@code zho_Hans} → {@code zho} — unless
 *       the code is in {@link #KEEP_SCRIPT_SUFFIX}, where the script variant
 *       is a genuinely different language from our training data's default
 *       script for that code.</li>
 *   <li>Remap FLORES-specific codes to our canonical codes via
 *       {@link #CODE_REMAP} (e.g. {@code arb} → {@code ara},
 *       {@code cmn} → {@code zho}).</li>
 * </ol>
 */
public final class FloresNorm {

    /**
     * FLORES codes where the script suffix must be kept because the
     * script-suffixed variant is a different language from our training data:
     * <ul>
     *   <li>{@code ace_Arab} — Acehnese in Jawi; our {@code ace} is Latin-script</li>
     *   <li>{@code arb_Latn} — Romanized Arabic; distinct from Arabic-script {@code arb}</li>
     *   <li>{@code bjn_Arab} — Banjar in Jawi; our {@code bjn} is Latin-script</li>
     *   <li>{@code kas_Deva} — Kashmiri in Devanagari; primary written form is Nastaliq</li>
     *   <li>{@code knc_Latn} — Central Kanuri in Latin; traditional script is Arabic</li>
     *   <li>{@code min_Arab} — Minangkabau in Jawi; our {@code min} is Latin-script</li>
     *   <li>{@code taq_Tfng} — Tamasheq in Tifinagh; digital text predominantly Latin</li>
     * </ul>
     * {@code zho_Hans} and {@code zho_Hant} both normalize to {@code zho}.
     */
    public static final Set<String> KEEP_SCRIPT_SUFFIX = Set.of(
            "ace_Arab",
            "arb_Latn",
            "bjn_Arab",
            "kas_Deva",
            "knc_Latn",
            "min_Arab",
            "taq_Tfng"
    );

    /**
     * Maps FLORES base codes to the canonical codes used in our model.
     * Only entries where the FLORES code differs from our canonical code
     * are listed.
     */
    public static final Map<String, String> CODE_REMAP = Map.ofEntries(
            Map.entry("arb", "ara"),   // Modern Standard Arabic → Arabic
            Map.entry("pes", "fas"),   // Western Persian → Farsi
            Map.entry("zsm", "msa"),   // Standard Malay → Malay
            Map.entry("lvs", "lav"),   // Standard Latvian → Latvian
            Map.entry("azj", "aze"),   // North Azerbaijani → Azerbaijani
            Map.entry("ekk", "est"),   // Standard Estonian → Estonian
            Map.entry("npi", "nep"),   // Nepali (individual) → Nepali
            Map.entry("als", "sqi"),   // Tosk Albanian → Albanian
            Map.entry("ory", "ori"),   // Odia → Oriya
            Map.entry("nor", "nob"),   // Norwegian → Bokmål
            Map.entry("cmn", "zho"),   // Mandarin → Chinese
            Map.entry("swa", "swh"),   // Swahili (macrolanguage) → Swahili
            Map.entry("yid", "ydd"),   // Yiddish → Eastern Yiddish
            Map.entry("gug", "grn"),   // Paraguayan Guaraní → Guaraní
            Map.entry("quz", "que"),   // Cusco Quechua → Quechua
            Map.entry("plt", "mlg"),   // Plateau Malagasy → Malagasy
            Map.entry("pbt", "pus"),   // Southern Pashto → Pashto
            Map.entry("uzn", "uzb"),   // Northern Uzbek → Uzbek
            Map.entry("kmr", "kur"),   // Kurmanji Kurdish → Kurdish
            Map.entry("khk", "mon")    // Khalkha Mongolian → Mongolian
    );

    /**
     * Normalize a FLORES-200 language code to our canonical model code.
     * Strips the script suffix then applies {@link #CODE_REMAP}.
     */
    public static String normalize(String floresCode) {
        String base = KEEP_SCRIPT_SUFFIX.contains(floresCode)
                ? floresCode
                : stripScript(floresCode);
        return CODE_REMAP.getOrDefault(base, base);
    }

    private static String stripScript(String code) {
        int underscore = code.indexOf('_');
        return underscore >= 0 ? code.substring(0, underscore) : code;
    }

    private FloresNorm() {}
}
