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

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Diagnostic for the win-1252-vs-MacRoman / IBM850 / windows-1250 /
 * windows-1257 / etc. confusion problem.
 *
 * <p>Reads {@code <charset>.bin.gz} files from a training directory,
 * counts per-byte and per-bigram frequencies for the requested classes,
 * then for each pair (A, B) prints the top-K bytes and bigrams by
 * Kullback-Leibler-divergence contribution:</p>
 *
 * <pre>
 *   KL_contrib(b) = (p_A(b) - p_B(b)) * log(p_A(b) / p_B(b))
 * </pre>
 *
 * <p>The bytes / bigrams with the largest contributions are the ones
 * whose frequencies differ most between A and B — i.e., the bytes /
 * bigrams the NB classifier should rely on to tell A from B.</p>
 *
 * <p>Annotates each output line with:</p>
 * <ul>
 *   <li>the byte's hex value</li>
 *   <li>what Unicode character the byte (or bigram) decodes to under
 *       each charset — to see whether the discriminative bytes are
 *       letters, punctuation, smart quotes, controls, etc.</li>
 *   <li>per-class probability of seeing this byte</li>
 * </ul>
 */
public final class DiagnoseDiscrimination {

    private static final int BIGRAM_SPACE = 65536;

    private DiagnoseDiscrimination() {
    }

    public static void main(String[] args) throws IOException {
        Path dataDir = null;
        List<String> classes = new ArrayList<>();
        int topK = 20;
        int maxSamplesPerClass = 50_000;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--classes":
                    classes = Arrays.asList(args[++i].split(","));
                    break;
                case "--top-k":
                    topK = Integer.parseInt(args[++i]);
                    break;
                case "--max-samples-per-class":
                    maxSamplesPerClass = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (dataDir == null || classes.isEmpty()) {
            System.err.println("Usage: DiagnoseDiscrimination --data <dir>"
                    + " --classes c1,c2,c3 [--top-k 20]"
                    + " [--max-samples-per-class N]");
            System.exit(1);
        }

        Map<String, long[]> byteCounts = new LinkedHashMap<>();
        Map<String, long[]> bigramCounts = new LinkedHashMap<>();
        Map<String, Long> byteTotals = new LinkedHashMap<>();
        Map<String, Long> bigramTotals = new LinkedHashMap<>();

        for (String label : classes) {
            Path f = dataDir.resolve(label + ".bin.gz");
            if (!Files.exists(f)) {
                System.err.println("Missing: " + f);
                continue;
            }
            long[] bc = new long[256];
            long[] bgc = new long[BIGRAM_SPACE];
            long bt = 0, bgt = 0;
            int sampled = 0;
            try (InputStream fis = new FileInputStream(f.toFile());
                 GZIPInputStream gis = new GZIPInputStream(fis);
                 DataInputStream dis = new DataInputStream(gis)) {
                while (sampled < maxSamplesPerClass) {
                    int len;
                    try {
                        len = dis.readUnsignedShort();
                    } catch (java.io.EOFException eof) {
                        break;
                    }
                    byte[] s = new byte[len];
                    dis.readFully(s);
                    for (int i = 0; i < s.length; i++) {
                        bc[s[i] & 0xFF]++;
                        bt++;
                    }
                    for (int i = 0; i + 1 < s.length; i++) {
                        int bg = ((s[i] & 0xFF) << 8) | (s[i + 1] & 0xFF);
                        bgc[bg]++;
                        bgt++;
                    }
                    sampled++;
                }
            }
            byteCounts.put(label, bc);
            bigramCounts.put(label, bgc);
            byteTotals.put(label, bt);
            bigramTotals.put(label, bgt);
            System.out.printf(Locale.ROOT, "loaded %-15s %,d samples  %,d bytes  %,d bigrams%n",
                    label, sampled, bt, bgt);
        }

        System.out.println();
        for (int i = 0; i < classes.size(); i++) {
            for (int j = i + 1; j < classes.size(); j++) {
                String a = classes.get(i);
                String b = classes.get(j);
                if (!byteCounts.containsKey(a) || !byteCounts.containsKey(b)) {
                    continue;
                }
                System.out.println("=========================================");
                System.out.printf(Locale.ROOT, " %s vs %s%n", a, b);
                System.out.println("=========================================");
                System.out.println();
                printTopBytes(a, b,
                        byteCounts.get(a), byteCounts.get(b),
                        byteTotals.get(a), byteTotals.get(b),
                        topK);
                System.out.println();
                printTopBigrams(a, b,
                        bigramCounts.get(a), bigramCounts.get(b),
                        bigramTotals.get(a), bigramTotals.get(b),
                        topK);
                System.out.println();
                printExpectedContribution(a, b,
                        bigramCounts.get(a), bigramCounts.get(b),
                        bigramTotals.get(a), bigramTotals.get(b),
                        topK);
                System.out.println();
            }
        }
    }

    private static void printTopBytes(String a, String b,
                                      long[] ca, long[] cb,
                                      long ta, long tb, int topK) {
        Charset csA = safeCharset(a);
        Charset csB = safeCharset(b);
        double[] kl = new double[256];
        for (int x = 0; x < 256; x++) {
            double pa = (ca[x] + 0.5) / (ta + 128.0);
            double pb = (cb[x] + 0.5) / (tb + 128.0);
            kl[x] = (pa - pb) * Math.log(pa / pb);
        }
        Integer[] idx = new Integer[256];
        for (int x = 0; x < 256; x++) idx[x] = x;
        Arrays.sort(idx, (x, y) -> Double.compare(kl[y], kl[x]));

        System.out.printf(Locale.ROOT,
                "  Top-%d discriminative single bytes (by KL contribution):%n", topK);
        System.out.printf(Locale.ROOT,
                "  %-6s  %-6s  %-12s  %-12s  %-15s  %-15s  %-8s%n",
                "byte", "high?", "p(" + a + ")", "p(" + b + ")", "decode(" + a + ")",
                "decode(" + b + ")", "KL");
        for (int k = 0; k < topK; k++) {
            int x = idx[k];
            double pa = (ca[x] + 0.5) / (ta + 128.0);
            double pb = (cb[x] + 0.5) / (tb + 128.0);
            String dA = decode(csA, new byte[]{(byte) x});
            String dB = decode(csB, new byte[]{(byte) x});
            String alpha = "";
            if (x >= 0x80) {
                boolean alphaA = !dA.isEmpty() && Character.isLetter(dA.codePointAt(0));
                boolean alphaB = !dB.isEmpty() && Character.isLetter(dB.codePointAt(0));
                alpha = (alphaA ? "A" : "-") + (alphaB ? "B" : "-");
            }
            System.out.printf(Locale.ROOT,
                    "  0x%02X    %-6s  %-12.6f  %-12.6f  %-15s  %-15s  %-8.4f  %s%n",
                    x, (x >= 0x80) ? "HI" : "",
                    pa, pb, prettify(dA), prettify(dB), kl[x], alpha);
        }
    }

    private static void printTopBigrams(String a, String b,
                                        long[] ca, long[] cb,
                                        long ta, long tb, int topK) {
        Charset csA = safeCharset(a);
        Charset csB = safeCharset(b);
        double[] kl = new double[BIGRAM_SPACE];
        for (int x = 0; x < BIGRAM_SPACE; x++) {
            if (ca[x] == 0 && cb[x] == 0) continue;
            double pa = (ca[x] + 0.5) / (ta + BIGRAM_SPACE * 0.5);
            double pb = (cb[x] + 0.5) / (tb + BIGRAM_SPACE * 0.5);
            kl[x] = (pa - pb) * Math.log(pa / pb);
        }
        Integer[] idx = new Integer[BIGRAM_SPACE];
        for (int x = 0; x < BIGRAM_SPACE; x++) idx[x] = x;
        Arrays.sort(idx, (x, y) -> Double.compare(kl[y], kl[x]));

        System.out.printf(Locale.ROOT,
                "  Top-%d discriminative bigrams (by KL contribution):%n", topK);
        System.out.printf(Locale.ROOT,
                "  %-9s  %-9s  %-12s  %-12s  %-15s  %-15s%n",
                "bigram", "high?", "p(" + a + ")", "p(" + b + ")",
                "decode(" + a + ")", "decode(" + b + ")");
        for (int k = 0; k < topK; k++) {
            int bg = idx[k];
            int b0 = (bg >>> 8) & 0xFF;
            int b1 = bg & 0xFF;
            double pa = (ca[bg] + 0.5) / (ta + BIGRAM_SPACE * 0.5);
            double pb = (cb[bg] + 0.5) / (tb + BIGRAM_SPACE * 0.5);
            byte[] bytes = new byte[]{(byte) b0, (byte) b1};
            String hi = ((b0 >= 0x80) ? "H" : "-") + ((b1 >= 0x80) ? "H" : "-");
            System.out.printf(Locale.ROOT,
                    "  %02X %02X     %-9s  %-12.6f  %-12.6f  %-15s  %-15s  KL=%.4f%n",
                    b0, b1, hi, pa, pb,
                    prettify(decode(csA, bytes)),
                    prettify(decode(csB, bytes)),
                    kl[bg]);
        }
    }

    /**
     * For each bigram, compute the SIGNED expected contribution to a
     * probe-from-A's score-margin vs B:
     *
     *     contrib(bg) = p_A(bg) * (log p_A(bg) − log p_B(bg))
     *
     * Then aggregate over byte-pair categories (ASCII-only, single-high,
     * double-high, whitespace-anchored, control-anchored) so we can see
     * whether the model's score on a typical A-probe is being driven by
     * "real" high-byte discriminators or by dumb high-frequency ASCII
     * bigrams whose per-class probability happens to be slightly
     * skewed.  Also prints the top-K SIGNED contributors in each
     * direction.
     *
     * <p>Negative entries are "if probe is class A, this bigram pulls
     * the score toward B" — exactly the bigrams that would silently
     * bias the decision the wrong way.</p>
     */
    private static void printExpectedContribution(String a, String b,
                                                  long[] ca, long[] cb,
                                                  long ta, long tb, int topK) {
        Charset csA = safeCharset(a);
        Charset csB = safeCharset(b);
        double[] contrib = new double[BIGRAM_SPACE];
        double asciiSum = 0, singleHiSum = 0, doubleHiSum = 0;
        double wsAnchoredSum = 0, ctrlAnchoredSum = 0;
        double asciiAbs = 0, singleHiAbs = 0, doubleHiAbs = 0;
        for (int bg = 0; bg < BIGRAM_SPACE; bg++) {
            int b0 = (bg >>> 8) & 0xFF;
            int b1 = bg & 0xFF;
            double pa = (ca[bg] + 0.5) / (ta + BIGRAM_SPACE * 0.5);
            double pb = (cb[bg] + 0.5) / (tb + BIGRAM_SPACE * 0.5);
            double c = pa * (Math.log(pa) - Math.log(pb));
            contrib[bg] = c;
            int hi = ((b0 >= 0x80) ? 1 : 0) + ((b1 >= 0x80) ? 1 : 0);
            boolean ws0 = isWhitespaceByte(b0);
            boolean ws1 = isWhitespaceByte(b1);
            boolean ctrl0 = b0 < 0x20 && !ws0;
            boolean ctrl1 = b1 < 0x20 && !ws1;
            if (hi == 0) {
                asciiSum += c;
                asciiAbs += Math.abs(c);
            } else if (hi == 1) {
                singleHiSum += c;
                singleHiAbs += Math.abs(c);
            } else {
                doubleHiSum += c;
                doubleHiAbs += Math.abs(c);
            }
            if (ws0 || ws1) wsAnchoredSum += c;
            if (ctrl0 || ctrl1) ctrlAnchoredSum += c;
        }

        System.out.printf(Locale.ROOT,
                "  Expected per-probe-bigram score contribution (probe-from-%s, vs %s):%n", a, b);
        System.out.printf(Locale.ROOT,
                "  Positive = pushes toward %s (correct).  Negative = pushes toward %s.%n%n", a, b);
        System.out.printf(Locale.ROOT,
                "  Category                       sum       abs-sum  net-direction%n");
        printCat("ASCII pairs (both < 0x80)", asciiSum, asciiAbs, a, b);
        printCat("Single-high-byte bigrams ", singleHiSum, singleHiAbs, a, b);
        printCat("Double-high-byte bigrams ", doubleHiSum, doubleHiAbs, a, b);
        printCat("Whitespace-anchored      ", wsAnchoredSum, 0.0, a, b);
        printCat("Control-anchored (<0x20) ", ctrlAnchoredSum, 0.0, a, b);
        double total = asciiSum + singleHiSum + doubleHiSum;
        System.out.printf(Locale.ROOT,
                "  TOTAL (KL(%s||%s)):           %+.6f%n", a, b, total);

        // Top-K NEGATIVE contributors: bigrams pulling toward B on A-probes.
        Integer[] idx = new Integer[BIGRAM_SPACE];
        for (int x = 0; x < BIGRAM_SPACE; x++) idx[x] = x;
        Arrays.sort(idx, (x, y) -> Double.compare(contrib[x], contrib[y]));
        System.out.printf(Locale.ROOT,
                "%n  Top-%d bigrams PULLING TOWARD %s on a typical %s probe:%n", topK, b, a);
        System.out.printf(Locale.ROOT,
                "  %-9s  %-9s  %-15s  %-15s  %-12s%n",
                "bigram", "hi?", "decode(" + a + ")", "decode(" + b + ")", "contrib");
        for (int k = 0; k < topK; k++) {
            int bg = idx[k];
            if (contrib[bg] >= 0) break;
            int b0 = (bg >>> 8) & 0xFF;
            int b1 = bg & 0xFF;
            String hi = ((b0 >= 0x80) ? "H" : "-") + ((b1 >= 0x80) ? "H" : "-");
            System.out.printf(Locale.ROOT,
                    "  %02X %02X     %-9s  %-15s  %-15s  %+.6f%n",
                    b0, b1, hi,
                    prettify(decode(csA, new byte[]{(byte)b0, (byte)b1})),
                    prettify(decode(csB, new byte[]{(byte)b0, (byte)b1})),
                    contrib[bg]);
        }
    }

    private static void printCat(String label, double signedSum,
                                 double absSum, String a, String b) {
        if (absSum > 0) {
            System.out.printf(Locale.ROOT,
                    "  %-30s  %+.6f  %.6f  %s%n",
                    label, signedSum, absSum, signedSum > 0 ? "→ " + a : "→ " + b);
        } else {
            System.out.printf(Locale.ROOT,
                    "  %-30s  %+.6f             %s%n",
                    label, signedSum, signedSum > 0 ? "→ " + a : "→ " + b);
        }
    }

    private static boolean isWhitespaceByte(int b) {
        return b == 0x09 || b == 0x0A || b == 0x0B || b == 0x0C
                || b == 0x0D || b == 0x20;
    }

    private static Charset safeCharset(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static String decode(Charset cs, byte[] bytes) {
        if (cs == null) return "?";
        try {
            return new String(bytes, cs);
        } catch (Exception e) {
            return "?";
        }
    }

    private static String prettify(String s) {
        if (s == null || s.isEmpty()) return "<empty>";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp < 0x20 || cp == 0x7F) {
                sb.append(String.format("\\x%02X", cp));
            } else if (cp == 0xFFFD) {
                sb.append("<FFFD>");
            } else {
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}
