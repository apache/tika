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
package org.apache.tika.parser.microsoft.rtf.jflex;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.utils.CharsetUtils;

/**
 * Shared charset maps for RTF parsing. Maps RTF {@code \fcharsetN} and
 * {@code \ansicpgN} values to Java {@link Charset} instances.
 *
 * <p>Extracted from the original {@code TextExtractor} so both the JFlex-based
 * parser and decapsulator can reuse them.</p>
 */
public final class RTFCharsetMaps {

    public static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    /**
     * Maps {@code \fcharsetN} values to Java charsets.
     * The RTF font table uses these to declare per-font character encodings.
     */
    public static final Map<Integer, Charset> FCHARSET_MAP;

    /**
     * Maps {@code \ansicpgN} values to Java charsets.
     * This is the global ANSI code page declared in the RTF header.
     */
    public static final Map<Integer, Charset> ANSICPG_MAP;

    static {
        Map<Integer, Charset> fcharset = new HashMap<>();

        fcharset.put(0, WINDOWS_1252);                   // ANSI
        // charset 1 = Default, charset 2 = Symbol

        fcharset.put(77, getCharset("MacRoman"));        // Mac Roman
        fcharset.put(78, getCharset("Shift_JIS"));       // Mac Shift Jis
        fcharset.put(79, getCharset("ms949"));            // Mac Hangul
        fcharset.put(80, getCharset("GB2312"));           // Mac GB2312
        fcharset.put(81, getCharset("Big5"));             // Mac Big5
        fcharset.put(82, getCharset("johab"));            // Mac Johab (old)
        fcharset.put(83, getCharset("MacHebrew"));        // Mac Hebrew
        fcharset.put(84, getCharset("MacArabic"));        // Mac Arabic
        fcharset.put(85, getCharset("MacGreek"));         // Mac Greek
        fcharset.put(86, getCharset("MacTurkish"));       // Mac Turkish
        fcharset.put(87, getCharset("MacThai"));          // Mac Thai
        fcharset.put(88, getCharset("cp1250"));           // Mac East Europe
        fcharset.put(89, getCharset("cp1251"));           // Mac Russian

        fcharset.put(128, getCharset("MS932"));           // Shift JIS
        fcharset.put(129, getCharset("ms949"));           // Hangul
        fcharset.put(130, getCharset("ms1361"));          // Johab
        fcharset.put(134, getCharset("ms936"));           // GB2312
        fcharset.put(136, getCharset("ms950"));           // Big5
        fcharset.put(161, getCharset("cp1253"));          // Greek
        fcharset.put(162, getCharset("cp1254"));          // Turkish
        fcharset.put(163, getCharset("cp1258"));          // Vietnamese
        fcharset.put(177, getCharset("cp1255"));          // Hebrew
        fcharset.put(178, getCharset("cp1256"));          // Arabic
        fcharset.put(186, getCharset("cp1257"));          // Baltic

        fcharset.put(204, getCharset("cp1251"));          // Russian
        fcharset.put(222, getCharset("ms874"));           // Thai
        fcharset.put(238, getCharset("cp1250"));          // Eastern European
        fcharset.put(254, getCharset("cp437"));           // PC 437
        fcharset.put(255, getCharset("cp850"));           // OEM

        FCHARSET_MAP = Collections.unmodifiableMap(fcharset);
    }

    static {
        Map<Integer, Charset> ansicpg = new HashMap<>();

        ansicpg.put(437, getCharset("CP437"));            // US IBM
        ansicpg.put(708, getCharset("ISO-8859-6"));       // Arabic (ASMO 708)
        ansicpg.put(709, getCharset("windows-709"));      // Arabic (ASMO 449+)
        ansicpg.put(710, getCharset("windows-710"));      // Arabic (transparent)
        ansicpg.put(711, getCharset("windows-711"));      // Arabic (Nafitha)
        ansicpg.put(720, getCharset("windows-720"));      // Arabic (transparent ASMO)
        ansicpg.put(819, getCharset("CP819"));            // Windows 3.1 (US/Western)
        ansicpg.put(850, getCharset("CP850"));            // IBM Multilingual
        ansicpg.put(852, getCharset("CP852"));            // Eastern European
        ansicpg.put(860, getCharset("CP860"));            // Portuguese
        ansicpg.put(862, getCharset("CP862"));            // Hebrew
        ansicpg.put(863, getCharset("CP863"));            // French Canadian
        ansicpg.put(864, getCharset("CP864"));            // Arabic
        ansicpg.put(865, getCharset("CP865"));            // Norwegian
        ansicpg.put(866, getCharset("CP866"));            // Soviet Union
        ansicpg.put(874, getCharset("MS874"));            // Thai
        ansicpg.put(932, getCharset("MS932"));            // Japanese
        ansicpg.put(936, getCharset("MS936"));            // Simplified Chinese
        ansicpg.put(949, getCharset("CP949"));            // Korean
        ansicpg.put(950, getCharset("CP950"));            // Traditional Chinese
        ansicpg.put(1250, getCharset("CP1250"));          // Eastern European
        ansicpg.put(1251, getCharset("CP1251"));          // Cyrillic
        ansicpg.put(1252, getCharset("CP1252"));          // Western European
        ansicpg.put(1253, getCharset("CP1253"));          // Greek
        ansicpg.put(1254, getCharset("CP1254"));          // Turkish
        ansicpg.put(1255, getCharset("CP1255"));          // Hebrew
        ansicpg.put(1256, getCharset("CP1256"));          // Arabic
        ansicpg.put(1257, getCharset("CP1257"));          // Baltic
        ansicpg.put(1258, getCharset("CP1258"));          // Vietnamese
        ansicpg.put(1361, getCharset("x-Johab"));         // Johab
        ansicpg.put(10000, getCharset("MacRoman"));       // Mac Roman
        ansicpg.put(10001, getCharset("Shift_JIS"));      // Mac Japan
        ansicpg.put(10004, getCharset("MacArabic"));      // Mac Arabic
        ansicpg.put(10005, getCharset("MacHebrew"));      // Mac Hebrew
        ansicpg.put(10006, getCharset("MacGreek"));       // Mac Greek
        ansicpg.put(10007, getCharset("MacCyrillic"));    // Mac Cyrillic
        ansicpg.put(10029, getCharset("x-MacCentralEurope")); // Mac Latin2
        ansicpg.put(10081, getCharset("MacTurkish"));     // Mac Turkish
        ansicpg.put(57002, getCharset("x-ISCII91"));      // Devanagari
        ansicpg.put(57003, getCharset("windows-57003"));  // Bengali
        ansicpg.put(57004, getCharset("windows-57004"));  // Tamil
        ansicpg.put(57005, getCharset("windows-57005"));  // Telugu
        ansicpg.put(57006, getCharset("windows-57006"));  // Assamese
        ansicpg.put(57007, getCharset("windows-57007"));  // Oriya
        ansicpg.put(57008, getCharset("windows-57008"));  // Kannada
        ansicpg.put(57009, getCharset("windows-57009"));  // Malayalam
        ansicpg.put(57010, getCharset("windows-57010"));  // Gujarati
        ansicpg.put(57011, getCharset("windows-57011"));  // Punjabi

        ANSICPG_MAP = Collections.unmodifiableMap(ansicpg);
    }

    private RTFCharsetMaps() {
    }

    /**
     * Resolve a charset by name, falling back to US-ASCII if unavailable.
     */
    static Charset getCharset(String name) {
        try {
            return CharsetUtils.forName(name);
        } catch (IllegalArgumentException e) {
            return StandardCharsets.US_ASCII;
        }
    }

    /**
     * Resolve an ANSI code page number to a Java Charset.
     * Tries the ANSICPG_MAP first, then falls back to {@code windows-N} and {@code cpN}.
     * Returns {@code WINDOWS_1252} if nothing matches.
     */
    public static Charset resolveCodePage(int cpNumber) {
        Charset cs = ANSICPG_MAP.get(cpNumber);
        if (cs != null) {
            return cs;
        }
        try {
            return Charset.forName("windows-" + cpNumber);
        } catch (Exception e) {
            try {
                return Charset.forName("cp" + cpNumber);
            } catch (Exception e2) {
                return WINDOWS_1252;
            }
        }
    }
}
