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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aligns the blocks of two extracts one to one by token overlap. Every A block also gets a
 * spread: how many B blocks hold a real share of its tokens (2 or more means B split it),
 * and the reverse for B (B merged several A blocks).
 */
public final class BlockMatcher {

    /** Tokens in more B blocks than this are too common to find candidates with. */
    private static final int MAX_DOCUMENT_FREQUENCY = 50;
    private static final int CANDIDATES_PER_BLOCK = 8;
    private static final double SPREAD_SHARE = 0.2;

    public static final class Alignment {
        /** B index matched to each A block, or -1. */
        final int[] bOfA;
        final int[] aOfB;
        final double[] diceOfA;
        final int[] spreadA;
        final int[] spreadB;

        Alignment(int a, int b) {
            bOfA = new int[a];
            aOfB = new int[b];
            diceOfA = new double[a];
            spreadA = new int[a];
            spreadB = new int[b];
            Arrays.fill(bOfA, -1);
            Arrays.fill(aOfB, -1);
        }

        int matched() {
            int n = 0;
            for (int x : bOfA) {
                if (x >= 0) {
                    n++;
                }
            }
            return n;
        }
    }

    private static final class Candidate {
        final int a;
        final int b;
        final double dice;
        /** Distance between the two blocks' relative positions; breaks ties between copies. */
        final double distance;

        Candidate(int a, int b, double dice, double distance) {
            this.a = a;
            this.b = b;
            this.dice = dice;
            this.distance = distance;
        }
    }

    private BlockMatcher() {
    }

    public static Alignment align(List<Block> as, List<Block> bs, double minDice) {
        Alignment alignment = new Alignment(as.size(), bs.size());
        Map<String, List<Integer>> postings = new HashMap<>();
        for (Block b : bs) {
            for (String t : b.counts().keySet()) {
                postings.computeIfAbsent(t, k -> new ArrayList<>()).add(b.index);
            }
        }
        List<Candidate> candidates = new ArrayList<>();
        List<Map<Integer, Integer>> overlapsByB = new ArrayList<>(bs.size());
        for (int i = 0; i < bs.size(); i++) {
            overlapsByB.add(new HashMap<>());
        }
        for (Block a : as) {
            Map<Integer, Integer> overlap = new HashMap<>();
            for (Map.Entry<String, Integer> e : a.counts().entrySet()) {
                List<Integer> ids = postings.get(e.getKey());
                if (ids == null || ids.size() > MAX_DOCUMENT_FREQUENCY) {
                    continue;
                }
                for (int bId : ids) {
                    int shared = Math.min(e.getValue(), bs.get(bId).counts().get(e.getKey()));
                    overlap.merge(bId, shared, Integer::sum);
                }
            }
            int spreadFloor = Math.max(2, (int) Math.ceil(SPREAD_SHARE * a.size()));
            List<Map.Entry<Integer, Integer>> top = new ArrayList<>(overlap.entrySet());
            top.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));
            for (Map.Entry<Integer, Integer> e : top) {
                if (e.getValue() >= spreadFloor) {
                    alignment.spreadA[a.index]++;
                }
                overlapsByB.get(e.getKey()).put(a.index, e.getValue());
            }
            for (int k = 0; k < Math.min(CANDIDATES_PER_BLOCK, top.size()); k++) {
                Block b = bs.get(top.get(k).getKey());
                double dice = dice(a, b);
                if (dice >= minDice) {
                    double distance = Math.abs((double) a.index / as.size()
                            - (double) b.index / bs.size());
                    candidates.add(new Candidate(a.index, b.index, dice, distance));
                }
            }
        }
        for (Block b : bs) {
            int spreadFloor = Math.max(2, (int) Math.ceil(SPREAD_SHARE * b.size()));
            for (int shared : overlapsByB.get(b.index).values()) {
                if (shared >= spreadFloor) {
                    alignment.spreadB[b.index]++;
                }
            }
        }
        candidates.sort((x, y) -> x.dice != y.dice ? Double.compare(y.dice, x.dice) :
                Double.compare(x.distance, y.distance));
        for (Candidate c : candidates) {
            if (alignment.bOfA[c.a] < 0 && alignment.aOfB[c.b] < 0) {
                alignment.bOfA[c.a] = c.b;
                alignment.aOfB[c.b] = c.a;
                alignment.diceOfA[c.a] = c.dice;
            }
        }
        return alignment;
    }

    /** Multiset Dice coefficient of two blocks' tokens. */
    static double dice(Block a, Block b) {
        if (a.size() == 0 || b.size() == 0) {
            return 0;
        }
        Map<String, Integer> small = a.counts().size() <= b.counts().size() ? a.counts() : b.counts();
        Map<String, Integer> large = small == a.counts() ? b.counts() : a.counts();
        int shared = 0;
        for (Map.Entry<String, Integer> e : small.entrySet()) {
            Integer other = large.get(e.getKey());
            if (other != null) {
                shared += Math.min(e.getValue(), other);
            }
        }
        return 2.0 * shared / (a.size() + b.size());
    }
}
