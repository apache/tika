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
package org.apache.tika.ml.chardetect.tools;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * How text is written in a legacy charset, shared by the training and eval generators so the
 * two can't drift.  Each code point is kept if the charset encodes it, else written the way that
 * charset's users wrote it (see {@link #partialCompose}), else dropped and counted.
 */
public final class CharsetTextEncoding {

    private static final String VIETNAMESE_TONES = "̣̀́̃̉";

    /**
     * De-facto letter substitutions in legacy charsets that lack the correct letter: Persian/Urdu
     * ی→ي and ک→ك in windows-1256; Romanian comma-below ș ț → cedilla ş ţ in windows-1250 and
     * ISO-8859-2. Real usage, not encodability, decides what the text looked like.
     */
    public static final Map<String, Map<Integer, Integer>> SUBST = new HashMap<>();

    static {
        Map<Integer, Integer> persian = Map.of(0x06CC, 0x064A, 0x06A9, 0x0643);
        SUBST.put("fas", persian);
        SUBST.put("urd", persian);
        SUBST.put("ron", Map.of(0x0218, 0x015E, 0x0219, 0x015F, 0x021A, 0x0162, 0x021B, 0x0163));
    }

    private CharsetTextEncoding() {
    }

    public static String substitute(String text, Map<Integer, Integer> map) {
        if (map == null) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> sb.appendCodePoint(map.getOrDefault(cp, cp)));
        return sb.toString();
    }

    /** The language's substitution map if {@code cs} lacks any original letter of it, else null. */
    public static Map<Integer, Integer> substitutionFor(String lang, Charset cs) {
        Map<Integer, Integer> m = SUBST.get(lang);
        if (m == null) {
            return null;
        }
        CharsetEncoder enc = cs.newEncoder();
        for (int cp : m.keySet()) {
            if (!enc.canEncode(new String(Character.toChars(cp)))) {
                return m;
            }
        }
        return null;
    }

    /**
     * The encodable form of {@code text}.  {@code dropped[0]} counts dropped code points and
     * {@code dropped[1]} the letters among them; callers should reject any dropped letter, since
     * a sample missing the hard letters is not how the charset's text looks.
     */
    public static String encodable(String text, CharsetEncoder enc, int[] dropped) {
        StringBuilder kept = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String c = new String(Character.toChars(cp));
            String alt;
            if (enc.canEncode(c)) {
                kept.append(c);
            } else if ((alt = partialCompose(c, enc)) != null) {
                kept.append(alt);
            } else {
                dropped[0]++;
                if (Character.isLetter(cp)) {
                    dropped[1]++;
                }
            }
            i += Character.charCount(cp);
        }
        return kept.toString();
    }

    /**
     * How a charset without the precomposed letter writes it: composed head plus combining
     * tail (ế -> ê + U+0301), or, for windows-1258, the NFC base with the Vietnamese tone marks
     * split off (ộ -> ô + U+0323; NFD orders the dot below before the circumflex).
     */
    static String partialCompose(String c, CharsetEncoder enc) {
        String d = Normalizer.normalize(c, Normalizer.Form.NFD);
        for (int k = d.length() - 1; k >= 1; k--) {
            String head = Normalizer.normalize(d.substring(0, k), Normalizer.Form.NFC);
            String tail = d.substring(k);
            if (enc.canEncode(head) && enc.canEncode(tail)) {
                return head + tail;
            }
        }
        StringBuilder base = new StringBuilder(), tone = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            (VIETNAMESE_TONES.indexOf(d.charAt(i)) >= 0 ? tone : base).append(d.charAt(i));
        }
        String split = Normalizer.normalize(base, Normalizer.Form.NFC) + tone;
        return tone.length() > 0 && enc.canEncode(split) ? split : null;
    }
}
