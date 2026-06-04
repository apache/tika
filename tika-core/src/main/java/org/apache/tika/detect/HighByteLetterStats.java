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
package org.apache.tika.detect;

import java.nio.charset.Charset;

/**
 * High-byte decode-quality statistics shared by the charset detectors.
 *
 * <p>Used to disambiguate single-byte <em>Latin</em> charset siblings
 * (windows-1252 vs IBM850 / x-MacRoman / ISO-8859-x), where a wrong decode maps
 * high bytes to box-drawing / symbols while the right one maps them to accented
 * letters.  The cased-letter count reads that boundary; the byte-bigram
 * typicality models cannot (both decodes look like typical Latin, and on
 * COMMON-dominated docs the discriminating bytes are diluted to noise).</p>
 *
 * <p><b>Latin-only.</b> {@link #countCasedHighByteLetters} counts Lu/Ll/Lt,
 * which also covers Cyrillic/Greek cased letters and would be polluted by a
 * non-Latin SBCS; and it excludes Lo, so a CJK decode (every ideograph is Lo)
 * cannot win on "letters".  Callers must restrict the comparison to Latin SBCS
 * candidates.</p>
 */
public final class HighByteLetterStats {

    private HighByteLetterStats() {
    }

    /** Count of bytes &ge; 0x80 in the probe. */
    public static int countHighBytes(byte[] probe) {
        if (probe == null) {
            return 0;
        }
        int n = 0;
        for (byte b : probe) {
            if ((b & 0xFF) >= 0x80) {
                n++;
            }
        }
        return n;
    }

    /**
     * Decode {@code probe} under {@code cs} and count codepoints &ge; 0x80 that
     * are Unicode cased letters (Lu/Ll/Lt).  Excludes the ordinal / superscript
     * indicators ª (U+00AA), º (U+00BA), ⁿ (U+207F): MacRoman's 0xBB/0xBC are
     * ª/º while windows-1252's 0xBB is » (punctuation), so without the exclusion
     * MacRoman's letter count would beat windows-1252's wherever » appears.
     * Lo (CJK / other-letter) is excluded by counting cased categories only.
     */
    public static int countCasedHighByteLetters(byte[] probe, Charset cs) {
        if (probe == null) {
            return 0;
        }
        String decoded;
        try {
            decoded = new String(probe, cs);
        } catch (Exception e) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < decoded.length(); ) {
            int cp = decoded.codePointAt(i);
            if (cp >= 0x80 && isCasedLatinishLetter(cp)) {
                count++;
            }
            i += Character.charCount(cp);
        }
        return count;
    }

    private static boolean isCasedLatinishLetter(int cp) {
        if (cp == 0x00AA || cp == 0x00BA || cp == 0x207F) {
            return false; // ª, º, ⁿ — ordinal / superscript indicators
        }
        int type = Character.getType(cp);
        return type == Character.UPPERCASE_LETTER
                || type == Character.LOWERCASE_LETTER
                || type == Character.TITLECASE_LETTER;
    }
}
