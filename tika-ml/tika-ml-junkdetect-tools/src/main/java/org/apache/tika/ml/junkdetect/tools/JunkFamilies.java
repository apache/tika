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
package org.apache.tika.ml.junkdetect.tools;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.tika.ml.junkdetect.HtmlContentCleaner;

/** Synthetic junk for training: wrong decodes of clean text, and the pointwise junk families. */
final class JunkFamilies {

    /** Pointwise training families, one drawn per clean example. */
    static final List<String> TRAIN_FAMILIES = Collections.unmodifiableList(List.of(
            "wrong-decode", "word-shuffle", "char-shuffle", "inject", "random-latin1"));

    /** Plausible source charsets per script, for wrong decodes. */
    static final Map<String, List<String>> SCRIPT_CHARSETS = Map.of(
            "LATIN", List.of("windows-1252", "windows-1250", "windows-1257", "windows-1254",
                    "windows-1258", "ISO-8859-2", "ISO-8859-15", "ISO-8859-16", "x-MacRoman", "IBM850"),
            "CYRILLIC", List.of("windows-1251", "KOI8-R", "KOI8-U", "IBM866", "ISO-8859-5", "x-MacCyrillic"),
            "GREEK", List.of("windows-1253", "ISO-8859-7"),
            "ARABIC", List.of("windows-1256", "ISO-8859-6"),
            "HEBREW", List.of("windows-1255", "ISO-8859-8"),
            "THAI", List.of("x-windows-874"),
            "HAN", List.of("GBK", "Big5", "Shift_JIS", "EUC-JP"),
            "HANGUL", List.of("EUC-KR"));

    /** Decoders every text is also misread with. */
    static final List<String> CROSS = List.of("UTF-8", "windows-1252", "windows-1250", "windows-1251",
            "GBK", "Shift_JIS", "windows-1253");

    /**
     * The other ASCII-compatible labels the charset model can return; {@link #EXTRA_DECODES} of
     * these are added per text, so every candidate the chain offers appears as a wrong decode
     * (Shift_JIS tables were lost to x-MacCyrillic, which no CJK text was misread with) without
     * thinning the common pairs above.
     */
    static final List<String> EXTRA = List.of("windows-1254", "windows-1255", "windows-1256",
            "windows-1257", "windows-1258", "x-windows-874", "ISO-8859-2", "ISO-8859-3", "ISO-8859-5",
            "ISO-8859-6", "ISO-8859-7", "ISO-8859-16", "KOI8-R", "KOI8-U", "IBM437", "IBM850", "IBM852",
            "IBM855", "IBM866", "x-MacRoman", "x-MacCyrillic", "GB18030", "Big5-HKSCS", "x-EUC-TW",
            "EUC-JP", "x-windows-949");

    static final int EXTRA_DECODES = 2;

    /**
     * UTF-16 misreads per text, both directions: its BOM-less UTF-16 bytes read as windows-1252
     * (what Mojibuster offers against a lone UTF-16 pick: every other char NUL or a C0 control) and
     * its single-byte/UTF-8 bytes read as UTF-16 (ideograph soup).
     */
    static final int WIDE_DECODES = 1;

    private static final List<Charset> WIDE = List.of(StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE);

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private JunkFamilies() {
    }

    /**
     * Misreadings of {@code text}: encode it in one plausible source charset (UTF-8 or a
     * charset of its script that can encode it), decode with every other candidate, keep those
     * that differ; shuffled, at most {@code max}, plus up to {@link #EXTRA_DECODES} from
     * {@link #EXTRA}.
     */
    static List<String> wrongDecodes(String text, String script, Random r, int max) {
        List<String> family = SCRIPT_CHARSETS.getOrDefault(script, List.of());
        List<String> sources = new ArrayList<>();
        sources.add("UTF-8");
        for (String c : family) {
            if (Charset.forName(c).newEncoder().canEncode(text)) {
                sources.add(c);
            }
        }
        String source = sources.get(r.nextInt(sources.size()));
        byte[] b = text.getBytes(Charset.forName(source));
        LinkedHashSet<String> decoders = new LinkedHashSet<>(family);
        decoders.addAll(CROSS);
        decoders.remove(source);
        List<String> out = misreads(b, text, decoders, r, max);
        LinkedHashSet<String> extra = new LinkedHashSet<>(EXTRA);
        extra.removeAll(decoders);
        extra.remove(source);
        out.addAll(misreads(b, text, extra, r, EXTRA_DECODES));
        out.addAll(wideMisreads(b, text, r));
        return out;
    }

    /** Cleaned as the runtime cleans a candidate decode (NUL -> U+FFFD), so train and inference match. */
    private static List<String> wideMisreads(byte[] b, String text, Random r) {
        List<String> out = new ArrayList<>();
        for (Charset wide : WIDE) {
            out.add(HtmlContentCleaner.clean(new String(text.getBytes(wide), WINDOWS_1252)));
            out.add(HtmlContentCleaner.clean(new String(b, wide)));
        }
        out.removeIf(m -> m == null || m.isEmpty() || m.equals(text));
        Collections.shuffle(out, r);
        return out.size() > WIDE_DECODES ? new ArrayList<>(out.subList(0, WIDE_DECODES)) : out;
    }

    private static List<String> misreads(byte[] b, String text, Iterable<String> decoders, Random r, int max) {
        List<String> out = new ArrayList<>();
        for (String d : decoders) {
            String m = new String(b, Charset.forName(d));
            if (!m.equals(text)) {
                out.add(m);
            }
        }
        Collections.shuffle(out, r);
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** One junk version of {@code text} from {@code family}; {@code null} if none applies. */
    static String corrupt(String text, String family, String script, Random r) {
        switch (family) {
            case "wrong-decode": {
                List<String> w = wrongDecodes(text, script, r, 8);
                return w.isEmpty() ? null : w.get(r.nextInt(w.size()));
            }
            case "word-shuffle": {
                List<String> w = new ArrayList<>(Arrays.asList(text.split(" ")));
                Collections.shuffle(w, r);
                return String.join(" ", w);
            }
            case "char-shuffle": {
                StringBuilder sb = new StringBuilder();
                for (String w : text.split(" ")) {
                    List<Integer> cps = new ArrayList<>();
                    w.codePoints().forEach(cps::add);
                    Collections.shuffle(cps, r);
                    for (int cp : cps) {
                        sb.appendCodePoint(cp);
                    }
                    sb.append(' ');
                }
                return sb.toString().trim();
            }
            case "inject": {
                // U+FFFD or a C0 control (U+0001-U+0008) after about one character in twelve
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < text.length(); i++) {
                    sb.append(text.charAt(i));
                    if (r.nextInt(12) == 0) {
                        sb.append(r.nextBoolean() ? '�' : (char) (1 + r.nextInt(8)));
                    }
                }
                return sb.toString();
            }
            case "random-latin1": {
                byte[] b = new byte[Math.max(20, text.length())];
                r.nextBytes(b);
                return new String(b, StandardCharsets.ISO_8859_1);
            }
            default:
                throw new IllegalArgumentException(family);
        }
    }
}
