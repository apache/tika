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
package org.apache.tika.eval.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Markdown summary of a run: overall, per group, and the files with the worst order. */
final class SummaryWriter {

    /** Below this share of matched tokens the order score says nothing. */
    static final double MIN_MATCHED_SHARE = 0.5;
    private static final int MIN_GROUP_DOCS = 3;
    private static final int WORST = 30;

    private SummaryWriter() {
    }

    static String summarize(List<StructureScores> all, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("Files: ").append(all.size()).append("; scored for order (matched token share >= ")
                .append(StructureScores.f(MIN_MATCHED_SHARE)).append("): ")
                .append(scoreable(all).size()).append("\n\n");
        sb.append("| group | files | tau mean | tau median | tau < 0.9 | adjacent mean | "
                + "split rate | merge rate | glued in A | glued in B | artifact share B | "
                + "untagged share B | blocks A | blocks B | p A | p B | h B | li B | cells B |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        sb.append(row("all", all));
        Map<String, List<StructureScores>> byGroup = new LinkedHashMap<>();
        for (StructureScores s : all) {
            byGroup.computeIfAbsent(s.group, g -> new ArrayList<>()).add(s);
        }
        List<Map.Entry<String, List<StructureScores>>> groups = new ArrayList<>(byGroup.entrySet());
        groups.sort((x, y) -> Integer.compare(y.getValue().size(), x.getValue().size()));
        for (Map.Entry<String, List<StructureScores>> e : groups) {
            if (e.getValue().size() >= MIN_GROUP_DOCS) {
                sb.append(row(e.getKey(), e.getValue()));
            }
        }
        sb.append("\nWorst order (file, group, tau, adjacent, matched share A, splits, merges):\n\n");
        List<StructureScores> worst = scoreable(all);
        worst.sort(Comparator.comparingDouble(s -> s.tau));
        for (StructureScores s : worst.subList(0, Math.min(WORST, worst.size()))) {
            sb.append("- ").append(s.file).append(" | ").append(s.group).append(" | ")
                    .append(StructureScores.f(s.tau)).append(" | ")
                    .append(StructureScores.f(s.adjacent)).append(" | ")
                    .append(StructureScores.f(s.matchedShareA)).append(" | ").append(s.splitsA)
                    .append(" | ").append(s.mergesB).append('\n');
        }
        return sb.toString();
    }

    private static List<StructureScores> scoreable(List<StructureScores> all) {
        List<StructureScores> out = new ArrayList<>();
        for (StructureScores s : all) {
            if (s.matchedShareA >= MIN_MATCHED_SHARE) {
                out.add(s);
            }
        }
        return out;
    }

    private static String row(String name, List<StructureScores> rows) {
        List<StructureScores> scoreable = scoreable(rows);
        long blocksA = 0;
        long blocksB = 0;
        long splits = 0;
        long merges = 0;
        long gluedA = 0;
        long gluedB = 0;
        long pA = 0;
        long pB = 0;
        long hB = 0;
        long liB = 0;
        long cellB = 0;
        for (StructureScores s : rows) {
            blocksA += s.blocksA;
            blocksB += s.blocksB;
            splits += s.splitsA;
            merges += s.mergesB;
            gluedA += s.gluedInA;
            gluedB += s.gluedInB;
            pA += s.kindsA[Block.Kind.P.ordinal()];
            pB += s.kindsB[Block.Kind.P.ordinal()];
            hB += s.kindsB[Block.Kind.H.ordinal()];
            liB += s.kindsB[Block.Kind.LI.ordinal()];
            cellB += s.kindsB[Block.Kind.CELL.ordinal()];
        }
        long low = scoreable.stream().filter(s -> s.tau < 0.9).count();
        return "| " + name + " | " + rows.size() + " | " + f(mean(scoreable, s -> s.tau)) + " | "
                + f(median(scoreable, s -> s.tau)) + " | " + low + " | "
                + f(mean(scoreable, s -> s.adjacent)) + " | "
                + f(blocksA == 0 ? 0 : (double) splits / blocksA) + " | "
                + f(blocksB == 0 ? 0 : (double) merges / blocksB) + " | " + gluedA + " | " + gluedB
                + " | " + f(mean(rows, s -> s.artifactShareB)) + " | "
                + f(mean(rows, s -> s.untaggedShareB)) + " | " + blocksA + " | " + blocksB + " | "
                + pA + " | " + pB + " | " + hB + " | " + liB + " | " + cellB + " |\n";
    }

    private static double mean(List<StructureScores> rows, ToDoubleFunction<StructureScores> f) {
        if (rows.isEmpty()) {
            return Double.NaN;
        }
        double sum = 0;
        for (StructureScores s : rows) {
            sum += f.applyAsDouble(s);
        }
        return sum / rows.size();
    }

    private static double median(List<StructureScores> rows, ToDoubleFunction<StructureScores> f) {
        if (rows.isEmpty()) {
            return Double.NaN;
        }
        double[] v = rows.stream().mapToDouble(f).sorted().toArray();
        return v[v.length / 2];
    }

    private static String f(double d) {
        return Double.isNaN(d) ? "-" : StructureScores.f(d);
    }
}
