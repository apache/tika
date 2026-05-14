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

/**
 * Static codepoint-range → bucket-index lookup table used by Feature 2
 * (block-transition log-probability).  Replaces
 * {@link Character.UnicodeBlock#of(int)} so that the model's block
 * semantics are fully decoupled from the JVM's Unicode-data release —
 * training on one JDK and serving on another produces identical scores
 * by construction.
 *
 * <p>The 338 named blocks are a snapshot from JDK 25's
 * {@link Character.UnicodeBlock} (Unicode 16.x).  Codepoints in gaps
 * between named blocks resolve to the {@link #UNASSIGNED} bucket
 * ({@value #UNASSIGNED}).  The total bucket count is
 * {@link #bucketCount()} = 339.
 *
 * <p>If the block list is ever updated, bump {@link #SCHEME_VERSION} —
 * the model file's {@code block_scheme_version} byte must match.  This
 * forces a clean retrain rather than silent re-mapping.
 *
 * <p>Lookup cost: O(log N) binary search.  Thread-safe, immutable.
 */
public final class UnicodeBlockRanges {

    /**
     * Bumped whenever the static range table below changes.  A model
     * trained against scheme version X cannot be served by code at
     * version Y ≠ X — the loader rejects the mismatch.
     */
    public static final int SCHEME_VERSION = 1;

    /** Bucket index returned for codepoints in no named block. */
    public static final int UNASSIGNED = 338;

    /**
     * Sorted by {@code start_cp}.  Each row: {@code {start, end_inclusive, bucket_id}}.
     * Bucket ids are 0..337 — the {@link #UNASSIGNED} bucket has id 338
     * and is implicit (returned when binary search finds no matching range).
     *
     * <p>Generated from JDK 25 {@code Character.UnicodeBlock.of(cp)} for
     * every codepoint in [0, 0x10FFFF].
     */
    private static final int[][] RANGES = {
            {0x0000, 0x007F, 0},   // BASIC_LATIN
            {0x0080, 0x00FF, 1},   // LATIN_1_SUPPLEMENT
            {0x0100, 0x017F, 2},   // LATIN_EXTENDED_A
            {0x0180, 0x024F, 3},   // LATIN_EXTENDED_B
            {0x0250, 0x02AF, 4},   // IPA_EXTENSIONS
            {0x02B0, 0x02FF, 5},   // SPACING_MODIFIER_LETTERS
            {0x0300, 0x036F, 6},   // COMBINING_DIACRITICAL_MARKS
            {0x0370, 0x03FF, 7},   // GREEK
            {0x0400, 0x04FF, 8},   // CYRILLIC
            {0x0500, 0x052F, 9},   // CYRILLIC_SUPPLEMENTARY
            {0x0530, 0x058F, 10},   // ARMENIAN
            {0x0590, 0x05FF, 11},   // HEBREW
            {0x0600, 0x06FF, 12},   // ARABIC
            {0x0700, 0x074F, 13},   // SYRIAC
            {0x0750, 0x077F, 14},   // ARABIC_SUPPLEMENT
            {0x0780, 0x07BF, 15},   // THAANA
            {0x07C0, 0x07FF, 16},   // NKO
            {0x0800, 0x083F, 17},   // SAMARITAN
            {0x0840, 0x085F, 18},   // MANDAIC
            {0x0860, 0x086F, 19},   // SYRIAC_SUPPLEMENT
            {0x0870, 0x089F, 20},   // ARABIC_EXTENDED_B
            {0x08A0, 0x08FF, 21},   // ARABIC_EXTENDED_A
            {0x0900, 0x097F, 22},   // DEVANAGARI
            {0x0980, 0x09FF, 23},   // BENGALI
            {0x0A00, 0x0A7F, 24},   // GURMUKHI
            {0x0A80, 0x0AFF, 25},   // GUJARATI
            {0x0B00, 0x0B7F, 26},   // ORIYA
            {0x0B80, 0x0BFF, 27},   // TAMIL
            {0x0C00, 0x0C7F, 28},   // TELUGU
            {0x0C80, 0x0CFF, 29},   // KANNADA
            {0x0D00, 0x0D7F, 30},   // MALAYALAM
            {0x0D80, 0x0DFF, 31},   // SINHALA
            {0x0E00, 0x0E7F, 32},   // THAI
            {0x0E80, 0x0EFF, 33},   // LAO
            {0x0F00, 0x0FFF, 34},   // TIBETAN
            {0x1000, 0x109F, 35},   // MYANMAR
            {0x10A0, 0x10FF, 36},   // GEORGIAN
            {0x1100, 0x11FF, 37},   // HANGUL_JAMO
            {0x1200, 0x137F, 38},   // ETHIOPIC
            {0x1380, 0x139F, 39},   // ETHIOPIC_SUPPLEMENT
            {0x13A0, 0x13FF, 40},   // CHEROKEE
            {0x1400, 0x167F, 41},   // UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS
            {0x1680, 0x169F, 42},   // OGHAM
            {0x16A0, 0x16FF, 43},   // RUNIC
            {0x1700, 0x171F, 44},   // TAGALOG
            {0x1720, 0x173F, 45},   // HANUNOO
            {0x1740, 0x175F, 46},   // BUHID
            {0x1760, 0x177F, 47},   // TAGBANWA
            {0x1780, 0x17FF, 48},   // KHMER
            {0x1800, 0x18AF, 49},   // MONGOLIAN
            {0x18B0, 0x18FF, 50},   // UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED
            {0x1900, 0x194F, 51},   // LIMBU
            {0x1950, 0x197F, 52},   // TAI_LE
            {0x1980, 0x19DF, 53},   // NEW_TAI_LUE
            {0x19E0, 0x19FF, 54},   // KHMER_SYMBOLS
            {0x1A00, 0x1A1F, 55},   // BUGINESE
            {0x1A20, 0x1AAF, 56},   // TAI_THAM
            {0x1AB0, 0x1AFF, 57},   // COMBINING_DIACRITICAL_MARKS_EXTENDED
            {0x1B00, 0x1B7F, 58},   // BALINESE
            {0x1B80, 0x1BBF, 59},   // SUNDANESE
            {0x1BC0, 0x1BFF, 60},   // BATAK
            {0x1C00, 0x1C4F, 61},   // LEPCHA
            {0x1C50, 0x1C7F, 62},   // OL_CHIKI
            {0x1C80, 0x1C8F, 63},   // CYRILLIC_EXTENDED_C
            {0x1C90, 0x1CBF, 64},   // GEORGIAN_EXTENDED
            {0x1CC0, 0x1CCF, 65},   // SUNDANESE_SUPPLEMENT
            {0x1CD0, 0x1CFF, 66},   // VEDIC_EXTENSIONS
            {0x1D00, 0x1D7F, 67},   // PHONETIC_EXTENSIONS
            {0x1D80, 0x1DBF, 68},   // PHONETIC_EXTENSIONS_SUPPLEMENT
            {0x1DC0, 0x1DFF, 69},   // COMBINING_DIACRITICAL_MARKS_SUPPLEMENT
            {0x1E00, 0x1EFF, 70},   // LATIN_EXTENDED_ADDITIONAL
            {0x1F00, 0x1FFF, 71},   // GREEK_EXTENDED
            {0x2000, 0x206F, 72},   // GENERAL_PUNCTUATION
            {0x2070, 0x209F, 73},   // SUPERSCRIPTS_AND_SUBSCRIPTS
            {0x20A0, 0x20CF, 74},   // CURRENCY_SYMBOLS
            {0x20D0, 0x20FF, 75},   // COMBINING_MARKS_FOR_SYMBOLS
            {0x2100, 0x214F, 76},   // LETTERLIKE_SYMBOLS
            {0x2150, 0x218F, 77},   // NUMBER_FORMS
            {0x2190, 0x21FF, 78},   // ARROWS
            {0x2200, 0x22FF, 79},   // MATHEMATICAL_OPERATORS
            {0x2300, 0x23FF, 80},   // MISCELLANEOUS_TECHNICAL
            {0x2400, 0x243F, 81},   // CONTROL_PICTURES
            {0x2440, 0x245F, 82},   // OPTICAL_CHARACTER_RECOGNITION
            {0x2460, 0x24FF, 83},   // ENCLOSED_ALPHANUMERICS
            {0x2500, 0x257F, 84},   // BOX_DRAWING
            {0x2580, 0x259F, 85},   // BLOCK_ELEMENTS
            {0x25A0, 0x25FF, 86},   // GEOMETRIC_SHAPES
            {0x2600, 0x26FF, 87},   // MISCELLANEOUS_SYMBOLS
            {0x2700, 0x27BF, 88},   // DINGBATS
            {0x27C0, 0x27EF, 89},   // MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A
            {0x27F0, 0x27FF, 90},   // SUPPLEMENTAL_ARROWS_A
            {0x2800, 0x28FF, 91},   // BRAILLE_PATTERNS
            {0x2900, 0x297F, 92},   // SUPPLEMENTAL_ARROWS_B
            {0x2980, 0x29FF, 93},   // MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B
            {0x2A00, 0x2AFF, 94},   // SUPPLEMENTAL_MATHEMATICAL_OPERATORS
            {0x2B00, 0x2BFF, 95},   // MISCELLANEOUS_SYMBOLS_AND_ARROWS
            {0x2C00, 0x2C5F, 96},   // GLAGOLITIC
            {0x2C60, 0x2C7F, 97},   // LATIN_EXTENDED_C
            {0x2C80, 0x2CFF, 98},   // COPTIC
            {0x2D00, 0x2D2F, 99},   // GEORGIAN_SUPPLEMENT
            {0x2D30, 0x2D7F, 100},   // TIFINAGH
            {0x2D80, 0x2DDF, 101},   // ETHIOPIC_EXTENDED
            {0x2DE0, 0x2DFF, 102},   // CYRILLIC_EXTENDED_A
            {0x2E00, 0x2E7F, 103},   // SUPPLEMENTAL_PUNCTUATION
            {0x2E80, 0x2EFF, 104},   // CJK_RADICALS_SUPPLEMENT
            {0x2F00, 0x2FDF, 105},   // KANGXI_RADICALS
            {0x2FF0, 0x2FFF, 106},   // IDEOGRAPHIC_DESCRIPTION_CHARACTERS
            {0x3000, 0x303F, 107},   // CJK_SYMBOLS_AND_PUNCTUATION
            {0x3040, 0x309F, 108},   // HIRAGANA
            {0x30A0, 0x30FF, 109},   // KATAKANA
            {0x3100, 0x312F, 110},   // BOPOMOFO
            {0x3130, 0x318F, 111},   // HANGUL_COMPATIBILITY_JAMO
            {0x3190, 0x319F, 112},   // KANBUN
            {0x31A0, 0x31BF, 113},   // BOPOMOFO_EXTENDED
            {0x31C0, 0x31EF, 114},   // CJK_STROKES
            {0x31F0, 0x31FF, 115},   // KATAKANA_PHONETIC_EXTENSIONS
            {0x3200, 0x32FF, 116},   // ENCLOSED_CJK_LETTERS_AND_MONTHS
            {0x3300, 0x33FF, 117},   // CJK_COMPATIBILITY
            {0x3400, 0x4DBF, 118},   // CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            {0x4DC0, 0x4DFF, 119},   // YIJING_HEXAGRAM_SYMBOLS
            {0x4E00, 0x9FFF, 120},   // CJK_UNIFIED_IDEOGRAPHS
            {0xA000, 0xA48F, 121},   // YI_SYLLABLES
            {0xA490, 0xA4CF, 122},   // YI_RADICALS
            {0xA4D0, 0xA4FF, 123},   // LISU
            {0xA500, 0xA63F, 124},   // VAI
            {0xA640, 0xA69F, 125},   // CYRILLIC_EXTENDED_B
            {0xA6A0, 0xA6FF, 126},   // BAMUM
            {0xA700, 0xA71F, 127},   // MODIFIER_TONE_LETTERS
            {0xA720, 0xA7FF, 128},   // LATIN_EXTENDED_D
            {0xA800, 0xA82F, 129},   // SYLOTI_NAGRI
            {0xA830, 0xA83F, 130},   // COMMON_INDIC_NUMBER_FORMS
            {0xA840, 0xA87F, 131},   // PHAGS_PA
            {0xA880, 0xA8DF, 132},   // SAURASHTRA
            {0xA8E0, 0xA8FF, 133},   // DEVANAGARI_EXTENDED
            {0xA900, 0xA92F, 134},   // KAYAH_LI
            {0xA930, 0xA95F, 135},   // REJANG
            {0xA960, 0xA97F, 136},   // HANGUL_JAMO_EXTENDED_A
            {0xA980, 0xA9DF, 137},   // JAVANESE
            {0xA9E0, 0xA9FF, 138},   // MYANMAR_EXTENDED_B
            {0xAA00, 0xAA5F, 139},   // CHAM
            {0xAA60, 0xAA7F, 140},   // MYANMAR_EXTENDED_A
            {0xAA80, 0xAADF, 141},   // TAI_VIET
            {0xAAE0, 0xAAFF, 142},   // MEETEI_MAYEK_EXTENSIONS
            {0xAB00, 0xAB2F, 143},   // ETHIOPIC_EXTENDED_A
            {0xAB30, 0xAB6F, 144},   // LATIN_EXTENDED_E
            {0xAB70, 0xABBF, 145},   // CHEROKEE_SUPPLEMENT
            {0xABC0, 0xABFF, 146},   // MEETEI_MAYEK
            {0xAC00, 0xD7AF, 147},   // HANGUL_SYLLABLES
            {0xD7B0, 0xD7FF, 148},   // HANGUL_JAMO_EXTENDED_B
            {0xD800, 0xDB7F, 149},   // HIGH_SURROGATES
            {0xDB80, 0xDBFF, 150},   // HIGH_PRIVATE_USE_SURROGATES
            {0xDC00, 0xDFFF, 151},   // LOW_SURROGATES
            {0xE000, 0xF8FF, 152},   // PRIVATE_USE_AREA
            {0xF900, 0xFAFF, 153},   // CJK_COMPATIBILITY_IDEOGRAPHS
            {0xFB00, 0xFB4F, 154},   // ALPHABETIC_PRESENTATION_FORMS
            {0xFB50, 0xFDFF, 155},   // ARABIC_PRESENTATION_FORMS_A
            {0xFE00, 0xFE0F, 156},   // VARIATION_SELECTORS
            {0xFE10, 0xFE1F, 157},   // VERTICAL_FORMS
            {0xFE20, 0xFE2F, 158},   // COMBINING_HALF_MARKS
            {0xFE30, 0xFE4F, 159},   // CJK_COMPATIBILITY_FORMS
            {0xFE50, 0xFE6F, 160},   // SMALL_FORM_VARIANTS
            {0xFE70, 0xFEFF, 161},   // ARABIC_PRESENTATION_FORMS_B
            {0xFF00, 0xFFEF, 162},   // HALFWIDTH_AND_FULLWIDTH_FORMS
            {0xFFF0, 0xFFFF, 163},   // SPECIALS
            {0x10000, 0x1007F, 164},   // LINEAR_B_SYLLABARY
            {0x10080, 0x100FF, 165},   // LINEAR_B_IDEOGRAMS
            {0x10100, 0x1013F, 166},   // AEGEAN_NUMBERS
            {0x10140, 0x1018F, 167},   // ANCIENT_GREEK_NUMBERS
            {0x10190, 0x101CF, 168},   // ANCIENT_SYMBOLS
            {0x101D0, 0x101FF, 169},   // PHAISTOS_DISC
            {0x10280, 0x1029F, 170},   // LYCIAN
            {0x102A0, 0x102DF, 171},   // CARIAN
            {0x102E0, 0x102FF, 172},   // COPTIC_EPACT_NUMBERS
            {0x10300, 0x1032F, 173},   // OLD_ITALIC
            {0x10330, 0x1034F, 174},   // GOTHIC
            {0x10350, 0x1037F, 175},   // OLD_PERMIC
            {0x10380, 0x1039F, 176},   // UGARITIC
            {0x103A0, 0x103DF, 177},   // OLD_PERSIAN
            {0x10400, 0x1044F, 178},   // DESERET
            {0x10450, 0x1047F, 179},   // SHAVIAN
            {0x10480, 0x104AF, 180},   // OSMANYA
            {0x104B0, 0x104FF, 181},   // OSAGE
            {0x10500, 0x1052F, 182},   // ELBASAN
            {0x10530, 0x1056F, 183},   // CAUCASIAN_ALBANIAN
            {0x10570, 0x105BF, 184},   // VITHKUQI
            {0x105C0, 0x105FF, 185},   // TODHRI
            {0x10600, 0x1077F, 186},   // LINEAR_A
            {0x10780, 0x107BF, 187},   // LATIN_EXTENDED_F
            {0x10800, 0x1083F, 188},   // CYPRIOT_SYLLABARY
            {0x10840, 0x1085F, 189},   // IMPERIAL_ARAMAIC
            {0x10860, 0x1087F, 190},   // PALMYRENE
            {0x10880, 0x108AF, 191},   // NABATAEAN
            {0x108E0, 0x108FF, 192},   // HATRAN
            {0x10900, 0x1091F, 193},   // PHOENICIAN
            {0x10920, 0x1093F, 194},   // LYDIAN
            {0x10980, 0x1099F, 195},   // MEROITIC_HIEROGLYPHS
            {0x109A0, 0x109FF, 196},   // MEROITIC_CURSIVE
            {0x10A00, 0x10A5F, 197},   // KHAROSHTHI
            {0x10A60, 0x10A7F, 198},   // OLD_SOUTH_ARABIAN
            {0x10A80, 0x10A9F, 199},   // OLD_NORTH_ARABIAN
            {0x10AC0, 0x10AFF, 200},   // MANICHAEAN
            {0x10B00, 0x10B3F, 201},   // AVESTAN
            {0x10B40, 0x10B5F, 202},   // INSCRIPTIONAL_PARTHIAN
            {0x10B60, 0x10B7F, 203},   // INSCRIPTIONAL_PAHLAVI
            {0x10B80, 0x10BAF, 204},   // PSALTER_PAHLAVI
            {0x10C00, 0x10C4F, 205},   // OLD_TURKIC
            {0x10C80, 0x10CFF, 206},   // OLD_HUNGARIAN
            {0x10D00, 0x10D3F, 207},   // HANIFI_ROHINGYA
            {0x10D40, 0x10D8F, 208},   // GARAY
            {0x10E60, 0x10E7F, 209},   // RUMI_NUMERAL_SYMBOLS
            {0x10E80, 0x10EBF, 210},   // YEZIDI
            {0x10EC0, 0x10EFF, 211},   // ARABIC_EXTENDED_C
            {0x10F00, 0x10F2F, 212},   // OLD_SOGDIAN
            {0x10F30, 0x10F6F, 213},   // SOGDIAN
            {0x10F70, 0x10FAF, 214},   // OLD_UYGHUR
            {0x10FB0, 0x10FDF, 215},   // CHORASMIAN
            {0x10FE0, 0x10FFF, 216},   // ELYMAIC
            {0x11000, 0x1107F, 217},   // BRAHMI
            {0x11080, 0x110CF, 218},   // KAITHI
            {0x110D0, 0x110FF, 219},   // SORA_SOMPENG
            {0x11100, 0x1114F, 220},   // CHAKMA
            {0x11150, 0x1117F, 221},   // MAHAJANI
            {0x11180, 0x111DF, 222},   // SHARADA
            {0x111E0, 0x111FF, 223},   // SINHALA_ARCHAIC_NUMBERS
            {0x11200, 0x1124F, 224},   // KHOJKI
            {0x11280, 0x112AF, 225},   // MULTANI
            {0x112B0, 0x112FF, 226},   // KHUDAWADI
            {0x11300, 0x1137F, 227},   // GRANTHA
            {0x11380, 0x113FF, 228},   // TULU_TIGALARI
            {0x11400, 0x1147F, 229},   // NEWA
            {0x11480, 0x114DF, 230},   // TIRHUTA
            {0x11580, 0x115FF, 231},   // SIDDHAM
            {0x11600, 0x1165F, 232},   // MODI
            {0x11660, 0x1167F, 233},   // MONGOLIAN_SUPPLEMENT
            {0x11680, 0x116CF, 234},   // TAKRI
            {0x116D0, 0x116FF, 235},   // MYANMAR_EXTENDED_C
            {0x11700, 0x1174F, 236},   // AHOM
            {0x11800, 0x1184F, 237},   // DOGRA
            {0x118A0, 0x118FF, 238},   // WARANG_CITI
            {0x11900, 0x1195F, 239},   // DIVES_AKURU
            {0x119A0, 0x119FF, 240},   // NANDINAGARI
            {0x11A00, 0x11A4F, 241},   // ZANABAZAR_SQUARE
            {0x11A50, 0x11AAF, 242},   // SOYOMBO
            {0x11AB0, 0x11ABF, 243},   // UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED_A
            {0x11AC0, 0x11AFF, 244},   // PAU_CIN_HAU
            {0x11B00, 0x11B5F, 245},   // DEVANAGARI_EXTENDED_A
            {0x11BC0, 0x11BFF, 246},   // SUNUWAR
            {0x11C00, 0x11C6F, 247},   // BHAIKSUKI
            {0x11C70, 0x11CBF, 248},   // MARCHEN
            {0x11D00, 0x11D5F, 249},   // MASARAM_GONDI
            {0x11D60, 0x11DAF, 250},   // GUNJALA_GONDI
            {0x11EE0, 0x11EFF, 251},   // MAKASAR
            {0x11F00, 0x11F5F, 252},   // KAWI
            {0x11FB0, 0x11FBF, 253},   // LISU_SUPPLEMENT
            {0x11FC0, 0x11FFF, 254},   // TAMIL_SUPPLEMENT
            {0x12000, 0x123FF, 255},   // CUNEIFORM
            {0x12400, 0x1247F, 256},   // CUNEIFORM_NUMBERS_AND_PUNCTUATION
            {0x12480, 0x1254F, 257},   // EARLY_DYNASTIC_CUNEIFORM
            {0x12F90, 0x12FFF, 258},   // CYPRO_MINOAN
            {0x13000, 0x1342F, 259},   // EGYPTIAN_HIEROGLYPHS
            {0x13430, 0x1345F, 260},   // EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS
            {0x13460, 0x143FF, 261},   // EGYPTIAN_HIEROGLYPHS_EXTENDED_A
            {0x14400, 0x1467F, 262},   // ANATOLIAN_HIEROGLYPHS
            {0x16100, 0x1613F, 263},   // GURUNG_KHEMA
            {0x16800, 0x16A3F, 264},   // BAMUM_SUPPLEMENT
            {0x16A40, 0x16A6F, 265},   // MRO
            {0x16A70, 0x16ACF, 266},   // TANGSA
            {0x16AD0, 0x16AFF, 267},   // BASSA_VAH
            {0x16B00, 0x16B8F, 268},   // PAHAWH_HMONG
            {0x16D40, 0x16D7F, 269},   // KIRAT_RAI
            {0x16E40, 0x16E9F, 270},   // MEDEFAIDRIN
            {0x16F00, 0x16F9F, 271},   // MIAO
            {0x16FE0, 0x16FFF, 272},   // IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION
            {0x17000, 0x187FF, 273},   // TANGUT
            {0x18800, 0x18AFF, 274},   // TANGUT_COMPONENTS
            {0x18B00, 0x18CFF, 275},   // KHITAN_SMALL_SCRIPT
            {0x18D00, 0x18D7F, 276},   // TANGUT_SUPPLEMENT
            {0x1AFF0, 0x1AFFF, 277},   // KANA_EXTENDED_B
            {0x1B000, 0x1B0FF, 278},   // KANA_SUPPLEMENT
            {0x1B100, 0x1B12F, 279},   // KANA_EXTENDED_A
            {0x1B130, 0x1B16F, 280},   // SMALL_KANA_EXTENSION
            {0x1B170, 0x1B2FF, 281},   // NUSHU
            {0x1BC00, 0x1BC9F, 282},   // DUPLOYAN
            {0x1BCA0, 0x1BCAF, 283},   // SHORTHAND_FORMAT_CONTROLS
            {0x1CC00, 0x1CEBF, 284},   // SYMBOLS_FOR_LEGACY_COMPUTING_SUPPLEMENT
            {0x1CF00, 0x1CFCF, 285},   // ZNAMENNY_MUSICAL_NOTATION
            {0x1D000, 0x1D0FF, 286},   // BYZANTINE_MUSICAL_SYMBOLS
            {0x1D100, 0x1D1FF, 287},   // MUSICAL_SYMBOLS
            {0x1D200, 0x1D24F, 288},   // ANCIENT_GREEK_MUSICAL_NOTATION
            {0x1D2C0, 0x1D2DF, 289},   // KAKTOVIK_NUMERALS
            {0x1D2E0, 0x1D2FF, 290},   // MAYAN_NUMERALS
            {0x1D300, 0x1D35F, 291},   // TAI_XUAN_JING_SYMBOLS
            {0x1D360, 0x1D37F, 292},   // COUNTING_ROD_NUMERALS
            {0x1D400, 0x1D7FF, 293},   // MATHEMATICAL_ALPHANUMERIC_SYMBOLS
            {0x1D800, 0x1DAAF, 294},   // SUTTON_SIGNWRITING
            {0x1DF00, 0x1DFFF, 295},   // LATIN_EXTENDED_G
            {0x1E000, 0x1E02F, 296},   // GLAGOLITIC_SUPPLEMENT
            {0x1E030, 0x1E08F, 297},   // CYRILLIC_EXTENDED_D
            {0x1E100, 0x1E14F, 298},   // NYIAKENG_PUACHUE_HMONG
            {0x1E290, 0x1E2BF, 299},   // TOTO
            {0x1E2C0, 0x1E2FF, 300},   // WANCHO
            {0x1E4D0, 0x1E4FF, 301},   // NAG_MUNDARI
            {0x1E5D0, 0x1E5FF, 302},   // OL_ONAL
            {0x1E7E0, 0x1E7FF, 303},   // ETHIOPIC_EXTENDED_B
            {0x1E800, 0x1E8DF, 304},   // MENDE_KIKAKUI
            {0x1E900, 0x1E95F, 305},   // ADLAM
            {0x1EC70, 0x1ECBF, 306},   // INDIC_SIYAQ_NUMBERS
            {0x1ED00, 0x1ED4F, 307},   // OTTOMAN_SIYAQ_NUMBERS
            {0x1EE00, 0x1EEFF, 308},   // ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS
            {0x1F000, 0x1F02F, 309},   // MAHJONG_TILES
            {0x1F030, 0x1F09F, 310},   // DOMINO_TILES
            {0x1F0A0, 0x1F0FF, 311},   // PLAYING_CARDS
            {0x1F100, 0x1F1FF, 312},   // ENCLOSED_ALPHANUMERIC_SUPPLEMENT
            {0x1F200, 0x1F2FF, 313},   // ENCLOSED_IDEOGRAPHIC_SUPPLEMENT
            {0x1F300, 0x1F5FF, 314},   // MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS
            {0x1F600, 0x1F64F, 315},   // EMOTICONS
            {0x1F650, 0x1F67F, 316},   // ORNAMENTAL_DINGBATS
            {0x1F680, 0x1F6FF, 317},   // TRANSPORT_AND_MAP_SYMBOLS
            {0x1F700, 0x1F77F, 318},   // ALCHEMICAL_SYMBOLS
            {0x1F780, 0x1F7FF, 319},   // GEOMETRIC_SHAPES_EXTENDED
            {0x1F800, 0x1F8FF, 320},   // SUPPLEMENTAL_ARROWS_C
            {0x1F900, 0x1F9FF, 321},   // SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS
            {0x1FA00, 0x1FA6F, 322},   // CHESS_SYMBOLS
            {0x1FA70, 0x1FAFF, 323},   // SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A
            {0x1FB00, 0x1FBFF, 324},   // SYMBOLS_FOR_LEGACY_COMPUTING
            {0x20000, 0x2A6DF, 325},   // CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            {0x2A700, 0x2B73F, 326},   // CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
            {0x2B740, 0x2B81F, 327},   // CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
            {0x2B820, 0x2CEAF, 328},   // CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E
            {0x2CEB0, 0x2EBEF, 329},   // CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F
            {0x2EBF0, 0x2EE5F, 330},   // CJK_UNIFIED_IDEOGRAPHS_EXTENSION_I
            {0x2F800, 0x2FA1F, 331},   // CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
            {0x30000, 0x3134F, 332},   // CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G
            {0x31350, 0x323AF, 333},   // CJK_UNIFIED_IDEOGRAPHS_EXTENSION_H
            {0xE0000, 0xE007F, 334},   // TAGS
            {0xE0100, 0xE01EF, 335},   // VARIATION_SELECTORS_SUPPLEMENT
            {0xF0000, 0xFFFFF, 336},   // SUPPLEMENTARY_PRIVATE_USE_AREA_A
            {0x100000, 0x10FFFF, 337},   // SUPPLEMENTARY_PRIVATE_USE_AREA_B
    };

    /** Cached start_cp array for binary search. */
    private static final int[] STARTS;
    static {
        STARTS = new int[RANGES.length];
        for (int i = 0; i < RANGES.length; i++) {
            STARTS[i] = RANGES[i][0];
        }
    }

    private UnicodeBlockRanges() {
        // utility class
    }

    /** Total number of buckets (named blocks + 1 unassigned). */
    public static int bucketCount() {
        return RANGES.length + 1;
    }

    /**
     * Returns the bucket id for the given codepoint, or {@link #UNASSIGNED}
     * if the codepoint falls outside every named block range.
     *
     * <p>Binary search over the sorted-by-{@code start_cp} range list:
     * O(log N) where N = {@value #UNASSIGNED} (the number of named blocks).
     */
    public static int bucketOf(int cp) {
        // Binary search: find largest STARTS[i] <= cp
        int lo = 0;
        int hi = STARTS.length - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (STARTS[mid] <= cp) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (found < 0) {
            return UNASSIGNED;
        }
        // RANGES[found] is the candidate.  Confirm cp is within end_inclusive.
        return cp <= RANGES[found][1] ? RANGES[found][2] : UNASSIGNED;
    }
}
