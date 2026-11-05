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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Paired before/after comparison of two {@link ScoreCharsetEvalData} {@code --predictions} files.
 *
 * <p>Gate cell = charset × lang × variant × kind × bom × length (high-byte buckets are pooled). A cell
 * regresses when the one-sided exact McNemar test on its discordant pairs survives Benjamini–Hochberg
 * at {@code --q} across all gated cells with at least {@code --min-n} paired samples. Smaller cells are
 * reported, never gated. With {@code --scope} (scope-tiers.tsv) only trained/covered charsets are gated;
 * with {@code --usage} (usage tiers) implausible (lang, charset) cells are not gated. Exit status 1 on any
 * regression.
 *
 * <pre>
 * CompareCharsetEvalRuns --before A.tsv.gz --after B.tsv.gz --output FILE.tsv
 *     [--detector tika4x] [--min-n 50] [--q 0.05] [--scope scope-tiers.tsv] [--usage usage-tiers.tsv]
 * </pre>
 */
public class CompareCharsetEvalRuns {

    public static void main(String[] args) throws Exception {
        Path before = null, after = null, output = null, scope = null, usage = null;
        String detector = "tika4x";
        int minN = 50;
        double q = 0.05;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--before":
                    before = Path.of(args[++i]);
                    break;
                case "--after":
                    after = Path.of(args[++i]);
                    break;
                case "--output":
                    output = Path.of(args[++i]);
                    break;
                case "--detector":
                    detector = args[++i];
                    break;
                case "--min-n":
                    minN = Integer.parseInt(args[++i]);
                    break;
                case "--q":
                    q = Double.parseDouble(args[++i]);
                    break;
                case "--scope":
                    scope = Path.of(args[++i]);
                    break;
                case "--usage":
                    usage = Path.of(args[++i]);
                    break;
                default:
                    throw new IllegalArgumentException("unknown arg " + args[i]);
            }
        }
        if (before == null || after == null || output == null) {
            System.err.println("usage: CompareCharsetEvalRuns --before A --after B --output FILE.tsv");
            System.exit(2);
        }
        // charset -> scope tier; "lang\tcharset" -> usage tier
        Map<String, String> scopeTier = scope == null ? Map.of() : column(scope, 0, 1);
        Map<String, String> usageTier = usage == null ? Map.of() : column(usage, -1, 2);
        Map<String, Boolean> a = load(before, detector);
        Map<String, Integer> cellN = new TreeMap<>(), okA = new HashMap<>(), okB = new HashMap<>(),
                worse = new HashMap<>(), better = new HashMap<>();
        int unpaired = 0;
        try (BufferedReader r = reader(after)) {
            r.readLine();
            for (String line; (line = r.readLine()) != null; ) {
                String[] f = line.split("\t", -1);
                if (!f[0].equals(detector)) {
                    continue;
                }
                Boolean x = a.remove(sampleKey(f));
                if (x == null) {
                    unpaired++;
                    continue;
                }
                boolean y = f[10].equals("1");
                String cell = cellKey(f);
                cellN.merge(cell, 1, Integer::sum);
                okA.merge(cell, x ? 1 : 0, Integer::sum);
                okB.merge(cell, y ? 1 : 0, Integer::sum);
                if (x && !y) {
                    worse.merge(cell, 1, Integer::sum);
                } else if (!x && y) {
                    better.merge(cell, 1, Integer::sum);
                }
            }
        }
        unpaired += a.size();
        if (unpaired > 0) {
            System.err.println("WARNING: " + unpaired + " unpaired samples (different data or lengths?)");
        }

        List<String> gated = new ArrayList<>();
        Map<String, Double> pWorse = new HashMap<>(), pBetter = new HashMap<>();
        for (String c : cellN.keySet()) {
            int b = worse.getOrDefault(c, 0), g = better.getOrDefault(c, 0);
            pWorse.put(c, mcnemarOneSided(b, g));
            pBetter.put(c, mcnemarOneSided(g, b));
            if (cellN.get(c) >= minN && inGate(c, scopeTier, usageTier)) {
                gated.add(c);
            }
        }
        java.util.Set<String> gatedSet = new java.util.HashSet<>(gated);
        Map<String, Boolean> regress = benjaminiHochberg(gated, pWorse, q);
        Map<String, Boolean> improve = benjaminiHochberg(gated, pBetter, q);

        int nReg = 0, nImp = 0;
        long totA = 0, totB = 0, totN = 0;
        List<String> rows = new ArrayList<>(cellN.keySet());
        rows.sort(Comparator.comparingDouble(c -> delta(c, cellN, okA, okB)));
        try (Writer w = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            w.write("charset\tlang\tvariant\tkind\tbom\tlength\tn\tacc_before\tacc_after\tdelta\tworse\tbetter"
                    + "\tp_worse\tgated\tverdict\n");
            for (String c : rows) {
                int n = cellN.get(c);
                totN += n;
                totA += okA.get(c);
                totB += okB.get(c);
                String verdict = Boolean.TRUE.equals(regress.get(c)) ? "REGRESSION"
                        : Boolean.TRUE.equals(improve.get(c)) ? "improvement" : "";
                nReg += verdict.equals("REGRESSION") ? 1 : 0;
                nImp += verdict.equals("improvement") ? 1 : 0;
                w.write(String.format(Locale.ROOT, "%s\t%d\t%.4f\t%.4f\t%+.4f\t%d\t%d\t%.3g\t%s\t%s%n", c, n,
                        okA.get(c) / (double) n, okB.get(c) / (double) n, delta(c, cellN, okA, okB),
                        worse.getOrDefault(c, 0), better.getOrDefault(c, 0), pWorse.get(c),
                        gatedSet.contains(c) ? "yes" : "no", verdict));
            }
        }
        System.err.printf(Locale.ROOT, "%s: overall %.4f -> %.4f over %d paired probes; %d gated cells "
                        + "(n>=%d); %d regressions, %d improvements (BH q=%.2f)%n", detector,
                totA / (double) totN, totB / (double) totN, totN, gated.size(), minN, nReg, nImp, q);
        System.exit(nReg > 0 ? 1 : 0);
    }

    /** Gate = trained or covered charset, and a (lang, charset) cell that is not implausible. */
    private static boolean inGate(String cell, Map<String, String> scopeTier, Map<String, String> usageTier) {
        String[] f = cell.split("\t");
        String sc = scopeTier.get(f[0]);
        if (sc != null && !sc.equals("trained") && !sc.equals("covered")) {
            return false;
        }
        return !"implausible".equals(usageTier.get(f[1] + "\t" + f[0]));
    }

    /** TSV with header: key column(s) -> value column. keyCol -1 means "col0\tcol1". */
    private static Map<String, String> column(Path p, int keyCol, int valCol) throws Exception {
        Map<String, String> m = new HashMap<>();
        List<String> lines = Files.readAllLines(p);
        for (String l : lines.subList(1, lines.size())) {
            String[] f = l.split("\t", -1);
            m.put(keyCol < 0 ? f[0] + "\t" + f[1] : f[keyCol], f[valCol]);
        }
        return m;
    }

    private static double delta(String c, Map<String, Integer> n, Map<String, Integer> a,
                                Map<String, Integer> b) {
        return (b.get(c) - a.get(c)) / (double) n.get(c);
    }

    private static Map<String, Boolean> load(Path p, String detector) throws Exception {
        Map<String, Boolean> m = new HashMap<>();
        try (BufferedReader r = reader(p)) {
            r.readLine();
            for (String line; (line = r.readLine()) != null; ) {
                String[] f = line.split("\t", -1);
                if (f[0].equals(detector)) {
                    m.put(sampleKey(f), f[10].equals("1"));
                }
            }
        }
        return m;
    }

    private static BufferedReader reader(Path p) throws Exception {
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(p)),
                StandardCharsets.UTF_8));
    }

    // detector, charset, idx, lang, variant, kind, bom, length, high_bytes, pred, ok
    private static String sampleKey(String[] f) {
        return f[1] + "\t" + f[2] + "\t" + f[6] + "\t" + f[7];
    }

    private static String cellKey(String[] f) {
        return f[1] + "\t" + f[3] + "\t" + f[4] + "\t" + f[5] + "\t" + f[6] + "\t" + f[7];
    }

    /** P(X >= b) for X ~ Binomial(b + g, 1/2): exact one-sided McNemar on discordant pairs. */
    static double mcnemarOneSided(int b, int g) {
        int n = b + g;
        if (b == 0) {
            return 1.0;
        }
        double logHalfN = n * Math.log(0.5), p = 0;
        for (int k = b; k <= n; k++) {
            p += Math.exp(logChoose(n, k) + logHalfN);
        }
        return Math.min(1.0, p);
    }

    private static double logChoose(int n, int k) {
        double s = 0;
        for (int i = 1; i <= k; i++) {
            s += Math.log(n - k + i) - Math.log(i);
        }
        return s;
    }

    static Map<String, Boolean> benjaminiHochberg(List<String> cells, Map<String, Double> p, double q) {
        List<String> sorted = new ArrayList<>(cells);
        sorted.sort(Comparator.comparingDouble(p::get));
        int m = sorted.size(), cutoff = -1;
        for (int i = 0; i < m; i++) {
            if (p.get(sorted.get(i)) <= q * (i + 1) / m) {
                cutoff = i;
            }
        }
        Map<String, Boolean> out = new HashMap<>();
        for (int i = 0; i <= cutoff; i++) {
            out.put(sorted.get(i), true);
        }
        return out;
    }
}
