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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.ml.chardetect.HtmlByteStripper;
import org.apache.tika.ml.junkdetect.JunkDetector;
import org.apache.tika.quality.TextQualityScore;

/**
 * Diagnostic: replicate JunkDetector.buildScriptRuns exactly on a fixture
 * and print every run.  Helps explain why score() returns UNKNOWN.
 *
 * <p>Usage:
 * <pre>
 *   ./mvnw exec:java -pl tika-ml/tika-ml-junkdetect \
 *     -Dexec.mainClass=org.apache.tika.ml.junkdetect.tools.DebugScriptRuns \
 *     -Dexec.args="--file ~/data/regression/.../AIT5... --charset GB18030 --bytes 1024"
 * </pre>
 */
public class DebugScriptRuns {

    // Mirror of JunkDetector.SCRIPT_MODEL_FALLBACK — keep in sync if production changes.
    private static final Map<String, String> SCRIPT_MODEL_FALLBACK = Map.of(
            "HIRAGANA", "HAN",
            "KATAKANA", "HAN");

    public static void main(String[] args) throws IOException {
        Path file = null;
        String charset = "GB18030";
        int probeBytes = 1024;
        boolean strip = true;
        boolean expand = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--file":
                    file = Paths.get(expandHome(args[++i]));
                    break;
                case "--charset":
                    charset = args[++i];
                    break;
                case "--bytes":
                    probeBytes = Integer.parseInt(args[++i]);
                    break;
                case "--no-strip":
                    strip = false;
                    break;
                case "--no-expand":
                    expand = false;
                    break;
                default:
                    System.err.println("unknown: " + args[i]);
                    System.exit(1);
            }
        }
        if (file == null) {
            System.err.println("Required: --file <path>");
            System.exit(1);
        }
        byte[] raw = Files.readAllBytes(file);
        byte[] forDecode = raw;
        if (strip) {
            byte[] dst = new byte[raw.length];
            HtmlByteStripper.Result r = HtmlByteStripper.strip(raw, 0, raw.length, dst, 0);
            if (r.tagCount > 0 && r.length > 0) {
                forDecode = Arrays.copyOf(dst, r.length);
            }
            System.err.println("After strip: " + forDecode.length + " bytes (was " + raw.length + ")");
        }
        if (forDecode.length > probeBytes) {
            forDecode = Arrays.copyOf(forDecode, probeBytes);
        }
        System.err.println("Probe: " + forDecode.length + " bytes decoded as " + charset);

        String decoded = new String(forDecode, Charset.forName(charset));
        if (expand) {
            decoded = expandEntities(decoded);
        }
        System.err.println("Decoded codepoints: " + decoded.codePointCount(0, decoded.length()));

        List<Run> runs = buildScriptRuns(decoded);
        System.err.println("Built " + runs.size() + " script runs.");

        // Mirror JunkDetector.scoreText filter and report what would be scored.
        JunkDetector detector = JunkDetector.loadFromClasspath();
        java.util.Set<String> modeled = detector.knownScripts();

        TreeMap<String, int[]> totals = new TreeMap<>(); // script -> {chars, bytes, runs, modeled?}
        int totalScored = 0;
        int totalSkippedShort = 0;
        int totalSkippedUnmodeled = 0;
        long totalBytesScored = 0;

        for (Run r : runs) {
            byte[] runUtf8 = r.text.getBytes(StandardCharsets.UTF_8);
            boolean isModeled = modeled.contains(r.script);
            boolean longEnough = runUtf8.length >= 2;
            totals.merge(r.script, new int[]{r.text.codePointCount(0, r.text.length()),
                            runUtf8.length, 1, isModeled ? 1 : 0},
                    (a, b) -> new int[]{a[0] + b[0], a[1] + b[1], a[2] + b[2], a[3]});
            if (!isModeled) {
                totalSkippedUnmodeled++;
            } else if (!longEnough) {
                totalSkippedShort++;
            } else {
                totalScored++;
                totalBytesScored += runUtf8.length;
            }
        }

        System.out.println("Script roll-up (script: cps, utf8_bytes, runs, modeled):");
        for (Map.Entry<String, int[]> e : totals.entrySet()) {
            int[] v = e.getValue();
            System.out.printf("  %-15s cps=%-5d bytes=%-6d runs=%-4d modeled=%s%n",
                    e.getKey(), v[0], v[1], v[2], v[3] == 1 ? "Y" : "N");
        }
        System.out.println();
        System.out.println("Scoring filter outcome:");
        System.out.println("  runs scored:           " + totalScored);
        System.out.println("  runs skipped (short):  " + totalSkippedShort);
        System.out.println("  runs skipped (unmod):  " + totalSkippedUnmodeled);
        System.out.println("  total bytes scored:    " + totalBytesScored);

        // The bug: computeF1MeanLogP returns NaN when String.length() < 2.
        // String.length() counts UTF-16 code units, but the outer filter uses
        // UTF-8 bytes.  A single CJK char = 1 UTF-16 unit but 3 UTF-8 bytes,
        // so it passes the outer filter and produces NaN inside.
        int nanCausing = 0;
        for (Run r : runs) {
            byte[] u = r.text.getBytes(StandardCharsets.UTF_8);
            if (u.length >= 2 && r.text.length() < 2 && modeled.contains(r.script)) {
                nanCausing++;
            }
        }
        System.out.println();
        System.out.println("NaN-causing runs (utf8≥2 but utf16<2, modeled): " + nanCausing);

        TextQualityScore score = detector.score(decoded);
        System.out.println("  detector.score() z:    "
                + (score.isUnknown() ? "UNKNOWN(" + score.getDominantScript() + ")"
                : String.format("%.3f (script=%s)", score.getZScore(), score.getDominantScript())));

        // Print the longest 10 runs so we can see what's actually in there.
        System.out.println();
        System.out.println("Longest 10 runs:");
        runs.sort((a, b) -> Integer.compare(b.text.length(), a.text.length()));
        for (int i = 0; i < Math.min(10, runs.size()); i++) {
            Run r = runs.get(i);
            byte[] u = r.text.getBytes(StandardCharsets.UTF_8);
            String preview = r.text.length() > 30
                    ? r.text.substring(0, 30) + "…" : r.text;
            preview = preview.replace("\n", "\\n").replace("\r", "\\r");
            System.out.printf("  %-15s cps=%-4d bytes=%-4d preview=%s%n",
                    r.script, r.text.codePointCount(0, r.text.length()), u.length, preview);
        }
    }

    // Exact mirror of JunkDetector.buildScriptRuns (private, copied here for diagnosis).
    private static List<Run> buildScriptRuns(String text) {
        List<Run> runs = new ArrayList<>();
        String currentScript = null;
        StringBuilder currentText = new StringBuilder();
        StringBuilder leadingCommon = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            if (s == Character.UnicodeScript.COMMON
                    || s == Character.UnicodeScript.INHERITED
                    || s == Character.UnicodeScript.UNKNOWN) {
                if (currentScript != null) {
                    currentText.appendCodePoint(cp);
                } else {
                    leadingCommon.appendCodePoint(cp);
                }
                continue;
            }
            String scriptName = SCRIPT_MODEL_FALLBACK.getOrDefault(s.name(), s.name());
            if (!scriptName.equals(currentScript)) {
                if (currentScript != null && currentText.length() > 0) {
                    runs.add(new Run(currentScript, currentText.toString()));
                }
                currentScript = scriptName;
                currentText = new StringBuilder();
                if (leadingCommon.length() > 0) {
                    currentText.append(leadingCommon);
                    leadingCommon.setLength(0);
                }
            }
            currentText.appendCodePoint(cp);
        }
        if (currentScript != null && currentText.length() > 0) {
            runs.add(new Run(currentScript, currentText.toString()));
        }
        return runs;
    }

    private static final class Run {
        final String script;
        final String text;
        Run(String s, String t) {
            this.script = s;
            this.text = t;
        }
    }

    private static final Pattern NUM_DEC = Pattern.compile("&#(\\d{1,7});");
    private static final Pattern NUM_HEX = Pattern.compile("&#[xX]([0-9a-fA-F]{1,6});");
    private static final Pattern NAMED =
            Pattern.compile("&(amp|lt|gt|quot|apos|nbsp|copy|reg);");

    private static String expandEntities(String in) {
        String s = NUM_DEC.matcher(in).replaceAll(mr -> {
            try {
                int cp = Integer.parseInt(mr.group(1));
                if (cp >= 0 && cp <= 0x10FFFF) {
                    return Matcher.quoteReplacement(new String(Character.toChars(cp)));
                }
            } catch (NumberFormatException ignored) {
                // leave unchanged
            }
            return Matcher.quoteReplacement(mr.group());
        });
        s = NUM_HEX.matcher(s).replaceAll(mr -> {
            try {
                int cp = Integer.parseInt(mr.group(1), 16);
                if (cp >= 0 && cp <= 0x10FFFF) {
                    return Matcher.quoteReplacement(new String(Character.toChars(cp)));
                }
            } catch (NumberFormatException ignored) {
                // leave unchanged
            }
            return Matcher.quoteReplacement(mr.group());
        });
        s = NAMED.matcher(s).replaceAll(mr -> {
            switch (mr.group(1)) {
                case "amp":  return "&";
                case "lt":   return "<";
                case "gt":   return ">";
                case "quot": return "\"";
                case "apos": return "'";
                case "nbsp": return " ";
                case "copy": return "©";
                case "reg":  return "®";
                default:     return Matcher.quoteReplacement(mr.group());
            }
        });
        return s;
    }

    private static String expandHome(String s) {
        return s.startsWith("~/") ? System.getProperty("user.home") + s.substring(1) : s;
    }
}
