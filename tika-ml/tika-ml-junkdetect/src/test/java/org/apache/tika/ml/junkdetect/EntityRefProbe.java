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

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.ml.chardetect.HtmlByteStripper;
import org.apache.tika.quality.TextQualityScore;

/**
 * One-off probe: score a file's first 16KB under each candidate charset,
 * with and without HTML-entity-ref expansion. Run via:
 * {@code mvn -pl :tika-ml-junkdetect exec:java -Dexec.classpathScope=test
 *        -Dexec.mainClass=org.apache.tika.ml.junkdetect.EntityRefProbe
 *        -Dexec.args="<file> <charset1> [charset2] ..."}.
 */
public class EntityRefProbe {

    private static final Pattern NUM_DEC =
            Pattern.compile("&#(\\d{1,7});");
    private static final Pattern NUM_HEX =
            Pattern.compile("&#[xX]([0-9a-fA-F]{1,6});");
    // A small set of named refs likely to appear in HTML.
    private static final Pattern NAMED =
            Pattern.compile("&(amp|lt|gt|quot|apos|nbsp|copy|reg);");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: EntityRefProbe <file> <charset1> [charset2] ...");
            System.exit(2);
        }
        byte[] raw = Files.readAllBytes(Paths.get(args[0]));
        if (raw.length > 16384) {
            byte[] cap = new byte[16384];
            System.arraycopy(raw, 0, cap, 0, 16384);
            raw = cap;
        }
        // Strip HTML the same way JunkFilterEncodingDetector does.
        byte[] dst = new byte[raw.length];
        HtmlByteStripper.Result strip =
                HtmlByteStripper.strip(raw, 0, raw.length, dst, 0);
        byte[] forDecode = raw;
        if (strip.tagCount > 0 && strip.length > 0) {
            forDecode = new byte[strip.length];
            System.arraycopy(dst, 0, forDecode, 0, strip.length);
        }
        System.out.printf("input=%dB tagCount=%d stripped=%dB%n",
                raw.length, strip.tagCount, forDecode.length);

        JunkDetector jd = JunkDetector.loadFromClasspath();
        for (int i = 1; i < args.length; i++) {
            String csName = args[i];
            Charset cs = Charset.forName(csName);
            String decoded = new String(forDecode, cs);
            String expanded = expandEntities(decoded);
            String removed = removeEntities(decoded);
            TextQualityScore rawScore = jd.score(decoded);
            TextQualityScore expScore = jd.score(expanded);
            TextQualityScore remScore = jd.score(removed);
            System.out.println();
            System.out.printf("== %s ==%n", csName);
            System.out.printf("  raw       len=%-5d  %s%n", decoded.length(), rawScore);
            System.out.printf("  expanded  len=%-5d  %s%n", expanded.length(), expScore);
            System.out.printf("  removed   len=%-5d  %s%n", removed.length(), remScore);
            int sample = Math.min(180, decoded.length());
            System.out.printf("  raw      : %s…%n",
                    decoded.substring(0, sample).replace('\n', ' ').replace('\r', ' '));
            sample = Math.min(180, expanded.length());
            System.out.printf("  expanded : %s…%n",
                    expanded.substring(0, sample).replace('\n', ' ').replace('\r', ' '));
            sample = Math.min(180, removed.length());
            System.out.printf("  removed  : %s…%n",
                    removed.substring(0, sample).replace('\n', ' ').replace('\r', ' '));
        }
    }

    private static String expandEntities(String s) {
        StringBuilder out = new StringBuilder(s.length());
        Matcher mDec = NUM_DEC.matcher(s);
        StringBuilder buf = new StringBuilder();
        // Decimal numeric refs
        Matcher m = mDec;
        int last = 0;
        while (m.find()) {
            buf.append(s, last, m.start());
            try {
                int cp = Integer.parseInt(m.group(1));
                if (Character.isValidCodePoint(cp)) {
                    buf.appendCodePoint(cp);
                } else {
                    buf.append(m.group());
                }
            } catch (NumberFormatException e) {
                buf.append(m.group());
            }
            last = m.end();
        }
        buf.append(s, last, s.length());
        String pass1 = buf.toString();

        // Hex numeric refs
        buf = new StringBuilder();
        m = NUM_HEX.matcher(pass1);
        last = 0;
        while (m.find()) {
            buf.append(pass1, last, m.start());
            try {
                int cp = Integer.parseInt(m.group(1), 16);
                if (Character.isValidCodePoint(cp)) {
                    buf.appendCodePoint(cp);
                } else {
                    buf.append(m.group());
                }
            } catch (NumberFormatException e) {
                buf.append(m.group());
            }
            last = m.end();
        }
        buf.append(pass1, last, pass1.length());
        String pass2 = buf.toString();

        // A small set of named refs
        return pass2
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&copy;", "©")
                .replace("&reg;", "®");
    }

    /**
     * Replace every numeric/named entity ref with a single space. Removal
     * (rather than expansion) keeps the per-charset script signal clean —
     * expansion injects Unicode codepoints that don't come from the candidate
     * charset's bytes and can dominate the actual decoded-charset signal.
     */
    private static String removeEntities(String s) {
        String r = NUM_DEC.matcher(s).replaceAll(" ");
        r = NUM_HEX.matcher(r).replaceAll(" ");
        r = NAMED.matcher(r).replaceAll(" ");
        return r;
    }
}
