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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.apache.tika.detect.CharsetSupersets;
import org.apache.tika.ml.junkdetect.HtmlContentCleaner;
import org.apache.tika.ml.junkdetect.JunkFilterEncodingDetector;

/**
 * The web-page pair sources of the pairwise head.  A pair is (correct decode, wrong decode) of
 * the same bytes; all decodes go through {@link CharsetSupersets#decodeAs} and
 * {@link HtmlContentCleaner#clean}, as in the junk filter.
 */
final class PairSources {

    /** Charsets a page of each script is re-encoded into for real-decision pairs. */
    static final Map<String, List<String>> REENCODE = Map.of(
            "LATIN", List.of("windows-1252", "ISO-8859-1", "ISO-8859-15", "windows-1250", "ISO-8859-2",
                    "windows-1257", "ISO-8859-13", "windows-1254", "ISO-8859-9", "ISO-8859-16",
                    "windows-1258", "x-MacRoman", "IBM850", "IBM852"),
            "CYRILLIC", List.of("windows-1251", "KOI8-R", "KOI8-U", "IBM866", "ISO-8859-5", "x-MacCyrillic"),
            "GREEK", List.of("windows-1253", "ISO-8859-7"),
            "ARABIC", List.of("windows-1256", "ISO-8859-6"),
            "HEBREW", List.of("windows-1255", "ISO-8859-8"),
            "THAI", List.of("x-windows-874", "TIS-620"));

    /** What authors saving into a narrower charset get: plain ASCII punctuation. */
    static final Map<Integer, String> ASCII_SUB = Map.of(0x201C, "\"", 0x201D, "\"", 0x201E, "\"",
            0x2018, "'", 0x2019, "'", 0x201A, "'", 0x2013, "-", 0x2014, "-", 0x2026, "...");

    private static final Pattern META =
            Pattern.compile("(?i)(<meta[^>]*charset\\s*=\\s*[\"']?)([A-Za-z0-9_:.-]+)");

    private PairSources() {
    }

    /** Pairs of the real-decision sample, split into fit and held-out select pages. */
    static final class RealDecision {
        final List<HashedFeatures.Vec> fit;
        final List<HashedFeatures.Vec> select;
        final int pages;
        final int variants;
        final int errors;

        RealDecision(List<HashedFeatures.Vec> fit, List<HashedFeatures.Vec> select, int pages,
                     int variants, int errors) {
            this.fit = fit;
            this.select = select;
            this.pages = pages;
            this.variants = variants;
            this.errors = errors;
        }
    }

    /**
     * Real-decision pairs: each sample page as it is, plus its text re-encoded into every
     * charset of its script family that encodes its letters (and UTF-8), with the meta charset
     * rewritten or removed.  Every variant goes through the chain; its pairs are the correct
     * decode of the probe against each other candidate the junk filter would weigh.
     *
     * @param sample tab-separated: doc path relative to {@code docs}, label
     */
    static RealDecision realDecision(Path sample, Path docs, ChainProbe chain, int log2Size,
                                     int maxN, int clip) throws IOException {
        List<String> rows = Files.readAllLines(sample, StandardCharsets.UTF_8);
        AtomicInteger variants = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        boolean[] isSelect = new boolean[rows.size()];
        List<List<HashedFeatures.Vec>> per = inOrder(rows.size(), i -> {
            String[] c = rows.get(i).split("\t", -1);
            isSelect[i] = isSelect(c[0]);
            List<HashedFeatures.Vec> out = new ArrayList<>();
            try {
                byte[] orig = Files.readAllBytes(docs.resolve(c[0]));
                String label = c[1];
                String text = new String(orig, decodeAs(label));
                List<byte[]> bytes = new ArrayList<>();
                List<String> truths = new ArrayList<>();
                bytes.add(orig);
                truths.add(label);
                Random r = new Random(Long.parseLong(sha1("junk-select:" + c[0]).substring(0, 12), 16));
                List<String> targets = new ArrayList<>(REENCODE.getOrDefault(dominantScript(text), List.of()));
                if (!label.equals("UTF-8")) {
                    targets.add("UTF-8");
                }
                for (String t : targets) {
                    if (t.equals(label)) {
                        continue;
                    }
                    Charset cs = Charset.forName(t);
                    if (!lettersEncodable(text, cs)) {
                        continue;
                    }
                    boolean drop = r.nextBoolean();
                    bytes.add(encodeWithNcr(rewriteMeta(text, drop ? null : t), cs));
                    truths.add(t);
                }
                for (int v = 0; v < bytes.size(); v++) {
                    variants.incrementAndGet();
                    JunkFilterEncodingDetector.Candidates cand = chain.candidates(bytes.get(v));
                    if (cand == null) {
                        continue;
                    }
                    byte[] probe = cand.getProbe();
                    String good = clean(probe, truths.get(v), clip);
                    HashedFeatures.Counts fg = HashedFeatures.counts(good, log2Size, maxN);
                    for (Charset other : cand.getBandCharsets()) {
                        String bad = clean(probe, other.name(), clip);
                        if (!bad.equals(good)) {
                            HashedFeatures.Vec p = HashedFeatures.pairDiff(fg,
                                    HashedFeatures.counts(bad, log2Size, maxN));
                            if (p != null) {
                                out.add(p);
                            }
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                errors.incrementAndGet();
            }
            return out;
        });
        List<HashedFeatures.Vec> fit = new ArrayList<>();
        List<HashedFeatures.Vec> select = new ArrayList<>();
        for (int i = 0; i < per.size(); i++) {
            (isSelect[i] ? select : fit).addAll(per.get(i));
        }
        return new RealDecision(fit, select, rows.size(), variants.get(), errors.get());
    }

    /** Gold pages read whole under each fixed decoder outside the label set. */
    static List<HashedFeatures.Vec> goldDecoderPairs(List<GoldPage> pages, List<String> decoders,
                                                     int log2Size, int maxN, int clip) {
        return flatten(inOrder(pages.size(), i -> {
            GoldPage g = pages.get(i);
            List<HashedFeatures.Vec> out = new ArrayList<>();
            byte[] b = read(g.path);
            Set<String> labels = new HashSet<>(g.labels);
            String good = clip(HtmlContentCleaner.clean(new String(b, decodeAs(g.labels.get(0)))), clip);
            HashedFeatures.Counts fg = HashedFeatures.counts(good, log2Size, maxN);
            for (String d : decoders) {
                if (labels.contains(d)) {
                    continue;
                }
                String bad = clip(HtmlContentCleaner.clean(new String(b, decodeAs(d))), clip);
                if (!bad.equals(good)) {
                    HashedFeatures.Vec p = HashedFeatures.pairDiff(fg, HashedFeatures.counts(bad, log2Size, maxN));
                    if (p != null) {
                        out.add(p);
                    }
                }
            }
            return out;
        }));
    }

    /** Gold pages through the chain: the label's decode of the probe against each other candidate. */
    static List<HashedFeatures.Vec> goldChainPairs(List<GoldPage> pages, ChainProbe chain,
                                                   int log2Size, int maxN, int clip) {
        return flatten(inOrder(pages.size(), i -> {
            GoldPage g = pages.get(i);
            List<HashedFeatures.Vec> out = new ArrayList<>();
            JunkFilterEncodingDetector.Candidates cand;
            try {
                cand = chain.candidates(read(g.path));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (cand == null || cand.getTexts().size() < 2) {
                return out;
            }
            byte[] probe = cand.getProbe();
            Set<String> good = goodTexts(probe, g.labels);
            String goodText = clip(HtmlContentCleaner.clean(new String(probe, decodeAs(g.labels.get(0)))), clip);
            HashedFeatures.Counts fg = HashedFeatures.counts(goodText, log2Size, maxN);
            for (String t : cand.getTexts().values()) {
                if (!good.contains(t)) {
                    HashedFeatures.Vec p = HashedFeatures.pairDiff(fg,
                            HashedFeatures.counts(clip(t, clip), log2Size, maxN));
                    if (p != null) {
                        out.add(p);
                    }
                }
            }
            return out;
        }));
    }

    /** Cleaned decodes of {@code probe} under each label: any of them is a correct answer. */
    static Set<String> goodTexts(byte[] probe, List<String> labels) {
        Set<String> good = new HashSet<>();
        for (String l : labels) {
            good.add(HtmlContentCleaner.clean(new String(probe, decodeAs(l))));
        }
        return good;
    }

    static Charset decodeAs(String name) {
        return CharsetSupersets.decodeAs(Charset.forName(name));
    }

    static String clean(byte[] probe, String charset, int clip) {
        return clip(HtmlContentCleaner.clean(new String(probe, decodeAs(charset))), clip);
    }

    static String clip(String s, int clip) {
        if (s.length() <= clip) {
            return s;
        }
        int end = Character.isHighSurrogate(s.charAt(clip - 1)) ? clip - 1 : clip;
        return s.substring(0, end);
    }

    static byte[] read(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Held out from fitting: one page in {@link HashedTrainingConfig#REAL_DECISION_SELECT_MODULUS}. */
    static boolean isSelect(String relPath) {
        return Long.parseLong(sha1("junk-select:" + relPath).substring(0, 8), 16)
                % HashedTrainingConfig.REAL_DECISION_SELECT_MODULUS == 0;
    }

    static String rewriteMeta(String html, String charset) {
        Matcher m = META.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + (charset == null ? "x-removed" : charset)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Encodes {@code s} in {@code cs}; what it can't encode becomes ASCII punctuation or a numeric reference. */
    static byte[] encodeWithNcr(String s, Charset cs) {
        CharsetEncoder enc = cs.newEncoder();
        StringBuilder chunk = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            if (enc.canEncode(ch)) {
                chunk.append(ch);
            } else if (ASCII_SUB.containsKey(cp)) {
                chunk.append(ASCII_SUB.get(cp));
            } else {
                chunk.append("&#").append(cp).append(';');
            }
        }
        return chunk.toString().getBytes(cs);
    }

    /** At least 20 non-ASCII letters, 99.5% of them encodable in {@code cs}. */
    static boolean lettersEncodable(String s, Charset cs) {
        CharsetEncoder enc = cs.newEncoder();
        int n = 0;
        int ok = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 0x80 || !Character.isLetter(cp)) {
                continue;
            }
            n++;
            if (enc.canEncode(new String(Character.toChars(cp)))) {
                ok++;
            }
        }
        return n >= 20 && ok >= 0.995 * n;
    }

    /** Script with the most non-ASCII letters, or {@code NONE}. */
    static String dominantScript(String s) {
        Map<Character.UnicodeScript, Integer> m = new HashMap<>();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x80 && Character.isLetter(cp)) {
                m.merge(Character.UnicodeScript.of(cp), 1, Integer::sum);
            }
        }
        Character.UnicodeScript best = null;
        int bestN = 0;
        for (Character.UnicodeScript sc : Character.UnicodeScript.values()) {
            Integer n = m.get(sc);
            if (n != null && n > bestN) {
                bestN = n;
                best = sc;
            }
        }
        return best == null ? "NONE" : best.name();
    }

    static String sha1(String s) {
        return sha1Hex(s.getBytes(StandardCharsets.UTF_8));
    }

    static String sha1Hex(byte[] b) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(b);
            StringBuilder sb = new StringBuilder(40);
            for (byte x : d) {
                sb.append(String.format(Locale.ROOT, "%02x", x));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Runs {@code f} over 0..n-1 in parallel and returns the results in index order. */
    static <T> List<T> inOrder(int n, IntFunction<T> f) {
        Object[] out = new Object[n];
        IntStream.range(0, n).parallel().forEach(i -> out[i] = f.apply(i));
        @SuppressWarnings("unchecked")
        List<T> r = (List<T>) Arrays.asList(out);
        return r;
    }

    static <T> List<T> flatten(List<List<T>> lists) {
        List<T> out = new ArrayList<>();
        for (List<T> l : lists) {
            out.addAll(l);
        }
        return out;
    }
}
