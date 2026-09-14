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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The structural comparison of one extract pair. A is the reference (the stripper, or a
 * label); B is the candidate.
 * <ul>
 * <li>order: Kendall tau over the matched blocks of at least {@link #MIN_ORDER_TOKENS}
 * tokens whose B partner is body text (not artifact or untagged), and the share of
 * consecutive such blocks whose B partners are in the same order</li>
 * <li>segmentation: A blocks that B spread over several blocks (splits), B blocks that
 * cover several A blocks (merges)</li>
 * <li>word boundaries: tokens of one side that the other side writes as several tokens
 * (glued in A, glued in B)</li>
 * <li>placement: the share of B's tokens in artifact and untagged divs</li>
 * </ul>
 */
public final class StructureScores {

    static final String[] KINDS = {"p", "h", "li", "cell", "other"};
    private static final int MAX_GLUE_CANDIDATES = 5000;
    /** Matched blocks shorter than this do not vote on order: tiny blocks align by chance. */
    static final int MIN_ORDER_TOKENS = 3;

    String file;
    String group;
    int blocksA;
    int blocksB;
    int tokensA;
    int tokensB;
    final int[] kindsA = new int[Block.Kind.values().length];
    final int[] kindsB = new int[Block.Kind.values().length];
    int matched;
    double matchedShareA;
    double matchedShareB;
    double tau;
    double adjacent;
    int splitsA;
    int mergesB;
    int gluedInA;
    int gluedInB;
    double artifactShareA;
    double artifactShareB;
    double untaggedShareA;
    double untaggedShareB;

    public static StructureScores compute(String file, String group, List<Block> as,
                                          List<Block> bs, double minDice) {
        StructureScores s = new StructureScores();
        s.file = file;
        s.group = group;
        s.blocksA = as.size();
        s.blocksB = bs.size();
        for (Block a : as) {
            s.tokensA += a.size();
            s.kindsA[a.kind.ordinal()]++;
        }
        for (Block b : bs) {
            s.tokensB += b.size();
            s.kindsB[b.kind.ordinal()]++;
        }
        BlockMatcher.Alignment alignment = BlockMatcher.align(as, bs, minDice);
        s.matched = alignment.matched();
        int matchedTokensA = 0;
        int matchedTokensB = 0;
        List<Integer> bOrder = new ArrayList<>();
        for (Block a : as) {
            int b = alignment.bOfA[a.index];
            if (b >= 0) {
                matchedTokensA += a.size();
                matchedTokensB += bs.get(b).size();
                // artifact and untagged text is set apart on purpose; it does not vote on order
                Block partner = bs.get(b);
                if (a.size() >= MIN_ORDER_TOKENS && !partner.artifact && !partner.untagged) {
                    bOrder.add(b);
                }
            }
            if (alignment.spreadA[a.index] >= 2) {
                s.splitsA++;
            }
        }
        for (Block b : bs) {
            if (alignment.spreadB[b.index] >= 2) {
                s.mergesB++;
            }
        }
        s.matchedShareA = s.tokensA == 0 ? 0 : (double) matchedTokensA / s.tokensA;
        s.matchedShareB = s.tokensB == 0 ? 0 : (double) matchedTokensB / s.tokensB;
        int[] order = bOrder.stream().mapToInt(Integer::intValue).toArray();
        s.tau = kendallTau(order);
        s.adjacent = adjacentAgreement(order);
        Map<String, Integer> onlyA = residual(as, bs);
        Map<String, Integer> onlyB = residual(bs, as);
        s.gluedInA = glued(onlyA, onlyB.keySet());
        s.gluedInB = glued(onlyB, onlyA.keySet());
        s.artifactShareA = share(as, true);
        s.artifactShareB = share(bs, true);
        s.untaggedShareA = share(as, false);
        s.untaggedShareB = share(bs, false);
        return s;
    }

    /**
     * Kendall tau of a sequence against its sorted order: 1 when the B partners of the A
     * blocks appear in A's order, -1 when fully reversed. 1 with fewer than two items.
     */
    static double kendallTau(int[] order) {
        int n = order.length;
        if (n < 2) {
            return 1.0;
        }
        long inversions = inversions(order.clone(), new int[n], 0, n - 1);
        double pairs = (double) n * (n - 1) / 2;
        return 1.0 - 2.0 * inversions / pairs;
    }

    private static long inversions(int[] a, int[] tmp, int lo, int hi) {
        if (lo >= hi) {
            return 0;
        }
        int mid = (lo + hi) >>> 1;
        long count = inversions(a, tmp, lo, mid) + inversions(a, tmp, mid + 1, hi);
        int i = lo;
        int j = mid + 1;
        int k = lo;
        while (i <= mid && j <= hi) {
            if (a[i] <= a[j]) {
                tmp[k++] = a[i++];
            } else {
                count += mid - i + 1;
                tmp[k++] = a[j++];
            }
        }
        while (i <= mid) {
            tmp[k++] = a[i++];
        }
        while (j <= hi) {
            tmp[k++] = a[j++];
        }
        System.arraycopy(tmp, lo, a, lo, hi - lo + 1);
        return count;
    }

    /** Share of consecutive matched pairs whose B partners come in the same order. */
    static double adjacentAgreement(int[] order) {
        if (order.length < 2) {
            return 1.0;
        }
        int agree = 0;
        for (int i = 1; i < order.length; i++) {
            if (order[i] > order[i - 1]) {
                agree++;
            }
        }
        return (double) agree / (order.length - 1);
    }

    /** Tokens of {@code these} left after removing {@code those}, as a multiset. */
    static Map<String, Integer> residual(List<Block> these, List<Block> those) {
        Map<String, Integer> counts = new HashMap<>();
        for (Block b : these) {
            for (String t : b.tokens) {
                counts.merge(t, 1, Integer::sum);
            }
        }
        for (Block b : those) {
            for (String t : b.tokens) {
                counts.computeIfPresent(t, (k, v) -> v > 1 ? v - 1 : null);
            }
        }
        return counts;
    }

    /**
     * Tokens in {@code only} that are a concatenation of two or more tokens in
     * {@code parts}: one side wrote them as one word, the other as several.
     */
    static int glued(Map<String, Integer> only, Set<String> parts) {
        if (parts.isEmpty()) {
            return 0;
        }
        int maxPart = 0;
        for (String p : parts) {
            maxPart = Math.max(maxPart, p.length());
        }
        int found = 0;
        int examined = 0;
        for (Map.Entry<String, Integer> e : only.entrySet()) {
            if (examined++ >= MAX_GLUE_CANDIDATES) {
                break;
            }
            String token = e.getKey();
            if (token.length() >= 4 && segments(token, parts, maxPart) >= 2) {
                found += e.getValue();
            }
        }
        return found;
    }

    /** Fewest parts that concatenate to the token, or 0 if none do. */
    private static int segments(String token, Set<String> parts, int maxPart) {
        int n = token.length();
        int[] best = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            best[i] = Integer.MAX_VALUE;
            for (int len = 1; len <= Math.min(maxPart, i); len++) {
                if (best[i - len] != Integer.MAX_VALUE
                        && parts.contains(token.substring(i - len, i))) {
                    best[i] = Math.min(best[i], best[i - len] + 1);
                }
            }
        }
        return best[n] == Integer.MAX_VALUE ? 0 : best[n];
    }

    private static double share(List<Block> blocks, boolean artifact) {
        int total = 0;
        int in = 0;
        for (Block b : blocks) {
            total += b.size();
            if (artifact ? b.artifact : b.untagged) {
                in += b.size();
            }
        }
        return total == 0 ? 0 : (double) in / total;
    }

    static String tsvHeader() {
        StringBuilder sb = new StringBuilder("file\tgroup\tblocks_a\tblocks_b\ttokens_a\ttokens_b");
        for (String k : KINDS) {
            sb.append('\t').append(k).append("_a\t").append(k).append("_b");
        }
        sb.append("\tmatched\tmatched_share_a\tmatched_share_b\ttau\tadjacent\tsplits_a\t"
                + "merges_b\tglued_in_a\tglued_in_b\tartifact_share_a\tartifact_share_b\t"
                + "untagged_share_a\tuntagged_share_b");
        return sb.toString();
    }

    String tsvLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(file).append('\t').append(group).append('\t').append(blocksA).append('\t')
                .append(blocksB).append('\t').append(tokensA).append('\t').append(tokensB);
        for (int i = 0; i < KINDS.length; i++) {
            sb.append('\t').append(kindsA[i]).append('\t').append(kindsB[i]);
        }
        sb.append('\t').append(matched).append('\t').append(f(matchedShareA)).append('\t')
                .append(f(matchedShareB)).append('\t').append(f(tau)).append('\t')
                .append(f(adjacent)).append('\t').append(splitsA).append('\t').append(mergesB)
                .append('\t').append(gluedInA).append('\t').append(gluedInB).append('\t')
                .append(f(artifactShareA)).append('\t').append(f(artifactShareB)).append('\t')
                .append(f(untaggedShareA)).append('\t').append(f(untaggedShareB));
        return sb.toString();
    }

    static String f(double d) {
        return String.format(Locale.ROOT, "%.3f", d);
    }
}
