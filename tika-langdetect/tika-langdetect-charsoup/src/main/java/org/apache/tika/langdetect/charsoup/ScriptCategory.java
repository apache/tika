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
 * Coarse Unicode script categories for language detection.
 * <p>
 * The full {@link Character.UnicodeScript} enum has ~160 values, far more
 * granularity than needed. This class maps scripts into a small set of
 * categories that matter for language detection:
 * <ul>
 *   <li>Scripts that cover multiple confusable languages (Latin, Cyrillic, Arabic)</li>
 *   <li>CJK scripts that need special n-gram treatment (Han, Hiragana, Katakana, Hangul)</li>
 *   <li>Major Indic and Southeast Asian scripts</li>
 *   <li>Everything else bucketed into OTHER</li>
 * </ul>
 * <p>
 * The category ID (0–15) is used as a salt byte in feature hashing, ensuring
 * that characters from different scripts never collide in the bucket space.
 * </p>
 */
public final class ScriptCategory {

    public static final int LATIN = 0;
    public static final int CYRILLIC = 1;
    public static final int ARABIC = 2;
    public static final int HAN = 3;
    public static final int HANGUL = 4;
    public static final int HIRAGANA = 5;
    public static final int KATAKANA = 6;
    public static final int DEVANAGARI = 7;
    public static final int THAI = 8;
    public static final int GREEK = 9;
    public static final int HEBREW = 10;
    public static final int BENGALI = 11;
    public static final int GEORGIAN = 12;
    public static final int ARMENIAN = 13;
    public static final int ETHIOPIC = 14;
    public static final int OTHER = 15;

    /** Number of distinct categories. */
    public static final int COUNT = 16;

    private static final String[] NAMES = {
            "LATIN", "CYRILLIC", "ARABIC", "HAN", "HANGUL",
            "HIRAGANA", "KATAKANA", "DEVANAGARI", "THAI", "GREEK",
            "HEBREW", "BENGALI", "GEORGIAN", "ARMENIAN", "ETHIOPIC", "OTHER"
    };

    private ScriptCategory() {
        // utility class
    }

    /**
     * Map a codepoint to its coarse script category.
     * <p>
     * Uses a fast-path for ASCII (Latin) before falling through to
     * {@link Character.UnicodeScript#of(int)}.
     *
     * @param cp a Unicode codepoint (should already be lowercased)
     * @return category ID in [0, {@link #COUNT})
     */
    public static int of(int cp) {
        // Fast path: ASCII is Latin
        if (cp < 0x0080) {
            return LATIN;
        }
        Character.UnicodeScript us = Character.UnicodeScript.of(cp);
        return fromUnicodeScript(us);
    }

    /**
     * Map a {@link Character.UnicodeScript} to a category.
     */
    static int fromUnicodeScript(Character.UnicodeScript us) {
        if (us == Character.UnicodeScript.LATIN) return LATIN;
        if (us == Character.UnicodeScript.CYRILLIC) return CYRILLIC;
        if (us == Character.UnicodeScript.ARABIC) return ARABIC;
        if (us == Character.UnicodeScript.HAN) return HAN;
        if (us == Character.UnicodeScript.HANGUL) return HANGUL;
        if (us == Character.UnicodeScript.HIRAGANA) return HIRAGANA;
        if (us == Character.UnicodeScript.KATAKANA) return KATAKANA;
        if (us == Character.UnicodeScript.DEVANAGARI) return DEVANAGARI;
        if (us == Character.UnicodeScript.THAI) return THAI;
        if (us == Character.UnicodeScript.GREEK) return GREEK;
        if (us == Character.UnicodeScript.HEBREW) return HEBREW;
        if (us == Character.UnicodeScript.BENGALI) return BENGALI;
        if (us == Character.UnicodeScript.GEORGIAN) return GEORGIAN;
        if (us == Character.UnicodeScript.ARMENIAN) return ARMENIAN;
        if (us == Character.UnicodeScript.ETHIOPIC) return ETHIOPIC;
        return OTHER;
    }

    /**
     * Human-readable name of a category.
     */
    public static String name(int category) {
        if (category >= 0 && category < NAMES.length) {
            return NAMES[category];
        }
        return "UNKNOWN(" + category + ")";
    }
}
