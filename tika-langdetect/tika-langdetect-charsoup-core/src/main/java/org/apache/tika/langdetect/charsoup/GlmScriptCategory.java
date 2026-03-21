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

/**
 * Fine-grained Unicode script categories for the generative language model.
 *
 * <p>Unlike {@link ScriptCategory}, this class:
 * <ul>
 *   <li>Covers all scripts present in the 204-language training set.</li>
 *   <li>Has <b>no OTHER catch-all</b> — {@link #of(int)} returns {@code -1}
 *       for unrecognized scripts, which are simply ignored rather than
 *       bucketed together.</li>
 *   <li>Is independent of {@link ScriptCategory#COUNT}, so the discriminative
 *       model is unaffected by changes here.</li>
 * </ul>
 *
 * <p>The normalized script distribution (proportion of letters per script)
 * provides a strong signal for detecting charset errors and garbled text:
 * genuine Japanese text is ~40% Hiragana, ~20% Katakana, ~30% CJK, while
 * mojibake produces random codepoints with a very different distribution.
 */
public final class GlmScriptCategory {

    // ---- Shared with ScriptCategory (same IDs for first 15) ----

    public static final int LATIN              =  0;
    public static final int CYRILLIC           =  1;
    public static final int ARABIC             =  2;
    public static final int HAN                =  3;
    public static final int HANGUL             =  4;
    public static final int HIRAGANA           =  5;
    public static final int KATAKANA           =  6;
    public static final int DEVANAGARI         =  7;
    public static final int THAI               =  8;
    public static final int GREEK              =  9;
    public static final int HEBREW             = 10;
    public static final int BENGALI            = 11;  // also Assamese
    public static final int GEORGIAN           = 12;  // also Mingrelian
    public static final int ARMENIAN           = 13;
    public static final int ETHIOPIC           = 14;  // Amharic, Tigrinya

    // ---- Scripts covered in our model but previously in OTHER ----

    public static final int MYANMAR            = 15;  // Burmese
    public static final int TIBETAN            = 16;
    public static final int KHMER              = 17;
    public static final int TAMIL              = 18;
    public static final int TELUGU             = 19;
    public static final int KANNADA            = 20;
    public static final int MALAYALAM          = 21;
    public static final int GUJARATI           = 22;
    public static final int GURMUKHI           = 23;  // Punjabi
    public static final int ORIYA              = 24;  // Odia
    public static final int SINHALA            = 25;
    public static final int LAO                = 26;
    public static final int NKO                = 27;
    public static final int THAANA             = 28;  // Dhivehi/Maldivian
    public static final int OL_CHIKI           = 29;  // Santali

    // ---- CJK sub-blocks (finer-grained Han) ----

    public static final int HAN_EXT_A          = 30;  // U+3400–U+4DBF
    public static final int HAN_EXT_B          = 31;  // U+20000+ (rare extensions)
    public static final int HAN_COMPAT         = 32;  // U+F900–U+FAFF
    public static final int BOPOMOFO          = 33;  // Traditional Chinese phonetic

    /** Total number of categories. No OTHER catch-all. */
    public static final int COUNT = 34;

    private static final String[] NAMES = {
            "LATIN", "CYRILLIC", "ARABIC", "HAN", "HANGUL",
            "HIRAGANA", "KATAKANA", "DEVANAGARI", "THAI", "GREEK",
            "HEBREW", "BENGALI", "GEORGIAN", "ARMENIAN", "ETHIOPIC",
            "MYANMAR", "TIBETAN", "KHMER",
            "TAMIL", "TELUGU", "KANNADA", "MALAYALAM",
            "GUJARATI", "GURMUKHI", "ORIYA", "SINHALA",
            "LAO", "NKO", "THAANA", "OL_CHIKI",
            "HAN_EXT_A", "HAN_EXT_B", "HAN_COMPAT", "BOPOMOFO"
    };

    private GlmScriptCategory() {}

    /**
     * Map a codepoint to its fine-grained script category.
     *
     * @param cp a Unicode codepoint (should already be lowercased)
     * @return category ID in [0, {@link #COUNT}), or {@code -1} if the
     *         script is not covered (caller should skip, not bucket)
     */
    public static int of(int cp) {
        // Fast path: ASCII is Latin
        if (cp < 0x0080) {
            return LATIN;
        }

        // Bopomofo — check before UnicodeScript dispatch
        if ((cp >= 0x3100 && cp <= 0x312F) || (cp >= 0x31A0 && cp <= 0x31BF)) {
            return BOPOMOFO;
        }

        Character.UnicodeScript us = Character.UnicodeScript.of(cp);

        if (us == Character.UnicodeScript.HAN) {
            return hanSubBlock(cp);
        }

        return fromUnicodeScript(us);
    }

    private static int hanSubBlock(int cp) {
        if (cp >= 0x3400 && cp <= 0x4DBF) return HAN_EXT_A;
        if (cp >= 0xF900 && cp <= 0xFAFF) return HAN_COMPAT;
        if (cp >= 0x20000)                 return HAN_EXT_B;
        return HAN;
    }

    private static int fromUnicodeScript(Character.UnicodeScript us) {
        switch (us) {
            case LATIN:              return LATIN;
            case CYRILLIC:           return CYRILLIC;
            case ARABIC:             return ARABIC;
            case HANGUL:             return HANGUL;
            case HIRAGANA:           return HIRAGANA;
            case KATAKANA:           return KATAKANA;
            case DEVANAGARI:         return DEVANAGARI;
            case THAI:               return THAI;
            case GREEK:              return GREEK;
            case HEBREW:             return HEBREW;
            case BENGALI:            return BENGALI;
            case GEORGIAN:           return GEORGIAN;
            case ARMENIAN:           return ARMENIAN;
            case ETHIOPIC:           return ETHIOPIC;
            case MYANMAR:            return MYANMAR;
            case TIBETAN:            return TIBETAN;
            case KHMER:              return KHMER;
            case TAMIL:              return TAMIL;
            case TELUGU:             return TELUGU;
            case KANNADA:            return KANNADA;
            case MALAYALAM:          return MALAYALAM;
            case GUJARATI:           return GUJARATI;
            case GURMUKHI:           return GURMUKHI;
            case ORIYA:              return ORIYA;
            case SINHALA:            return SINHALA;
            case LAO:                return LAO;
            case NKO:                return NKO;
            case THAANA:             return THAANA;
            case OL_CHIKI:           return OL_CHIKI;
            default:                 return -1;  // unrecognized — caller skips
        }
    }

    /** Human-readable name for a category index. */
    public static String name(int category) {
        if (category >= 0 && category < NAMES.length) {
            return NAMES[category];
        }
        return "UNKNOWN(" + category + ")";
    }
}
