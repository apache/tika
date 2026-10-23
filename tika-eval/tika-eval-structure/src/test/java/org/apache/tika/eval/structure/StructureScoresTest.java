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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class StructureScoresTest {

    private static List<Block> blocks(String... texts) {
        List<Block> out = new ArrayList<>();
        for (String t : texts) {
            out.add(new Block(out.size(), 0, Block.Kind.P, false, false,
                    BlockExtractor.tokenize(t)));
        }
        return out;
    }

    @Test
    public void testRepeatedBlocksMatchInOrder() {
        // a page printed twice: copies pair with the copy at the same position
        List<Block> a = blocks(PARAS[0], PARAS[1], PARAS[0], PARAS[1]);
        StructureScores s = StructureScores.compute("f", "g", a, blocks(PARAS[0], PARAS[1],
                PARAS[0], PARAS[1]), 0.5);
        assertEquals(4, s.matched);
        assertEquals(1.0, s.tau, 1e-9);
    }

    private static final String[] PARAS = {
            "the quick brown fox jumps over the lazy dog",
            "pack my box with five dozen liquor jugs",
            "sphinx of black quartz judge my vow",
            "how vexingly quick daft zebras jump"};

    @Test
    public void testKendallTau() {
        assertEquals(1.0, StructureScores.kendallTau(new int[]{0, 1, 2, 3}), 1e-9);
        assertEquals(-1.0, StructureScores.kendallTau(new int[]{3, 2, 1, 0}), 1e-9);
        assertEquals(1.0 - 2.0 / 3, StructureScores.kendallTau(new int[]{0, 2, 1}), 1e-9);
        assertEquals(1.0, StructureScores.kendallTau(new int[]{7}), 1e-9);
        assertEquals(0.5, StructureScores.adjacentAgreement(new int[]{0, 2, 1}), 1e-9);
    }

    @Test
    public void testSameOrder() {
        StructureScores s = StructureScores.compute("f", "g", blocks(PARAS), blocks(PARAS), 0.5);
        assertEquals(4, s.matched);
        assertEquals(1.0, s.tau, 1e-9);
        assertEquals(1.0, s.adjacent, 1e-9);
        assertEquals(1.0, s.matchedShareA, 1e-9);
        assertEquals(0, s.splitsA);
        assertEquals(0, s.mergesB);
        assertEquals(0, s.gluedInA + s.gluedInB);
    }

    @Test
    public void testReversedOrder() {
        StructureScores s = StructureScores.compute("f", "g", blocks(PARAS),
                blocks(PARAS[3], PARAS[2], PARAS[1], PARAS[0]), 0.5);
        assertEquals(4, s.matched);
        assertEquals(-1.0, s.tau, 1e-9);
        assertEquals(0.0, s.adjacent, 1e-9);
    }

    @Test
    public void testSplitAndMerge() {
        // B splits the first paragraph in two and merges the last two
        List<Block> b = blocks("the quick brown fox", "jumps over the lazy dog", PARAS[1],
                PARAS[2] + " " + PARAS[3]);
        StructureScores s = StructureScores.compute("f", "g", blocks(PARAS), b, 0.5);
        assertEquals(1, s.splitsA);
        assertEquals(1, s.mergesB);
        // a split block still matches its larger half; a merged block matches one of its parts
        assertEquals(3, s.matched);
        assertEquals(1.0, s.tau, 1e-9);
    }

    @Test
    public void testArtifactsDoNotVoteOnOrder() {
        List<Block> a = blocks(PARAS[0], PARAS[1], PARAS[2]);
        List<Block> b = new ArrayList<>();
        b.add(new Block(0, 0, Block.Kind.P, false, false, BlockExtractor.tokenize(PARAS[1])));
        b.add(new Block(1, 0, Block.Kind.P, false, false, BlockExtractor.tokenize(PARAS[2])));
        // the first paragraph became a running header, moved to the page's end
        b.add(new Block(2, 0, Block.Kind.P, true, false, BlockExtractor.tokenize(PARAS[0])));
        StructureScores s = StructureScores.compute("f", "g", a, b, 0.5);
        assertEquals(3, s.matched);
        assertEquals(1.0, s.tau, 1e-9);
        assertEquals(1.0, s.adjacent, 1e-9);
    }

    @Test
    public void testGluedTokens() {
        Map<String, Integer> onlyA = Map.of("scotclothes", 2, "unrelated", 1, "abcd", 1);
        assertEquals(2, StructureScores.glued(onlyA, Set.of("scot", "clothes", "ab", "x")));
        assertEquals(0, StructureScores.glued(Map.of("scot", 1), Set.of("scotclothes")));
        StructureScores s = StructureScores.compute("f", "g",
                blocks("we sell scotclothes here"), blocks("we sell scot clothes here"), 0.5);
        assertEquals(1, s.gluedInA);
        assertEquals(0, s.gluedInB);
    }
}
