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
package org.apache.tika.eval.core.tokens;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TokenOrderTest {

    private static String[] words(Random r, int n) {
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            out[i] = "w" + r.nextInt(3000);
        }
        return out;
    }

    /** Moves tokens [from, from + len) to position 0. */
    private static String[] moveToFront(String[] a, int from, int len) {
        List<String> out = new ArrayList<>(Arrays.asList(a).subList(from, from + len));
        out.addAll(Arrays.asList(a).subList(0, from));
        out.addAll(Arrays.asList(a).subList(from + len, a.length));
        return out.toArray(new String[0]);
    }

    @Test
    public void testUnchanged() {
        String[] a = words(new Random(1), 2000);
        TokenOrder.Result r = TokenOrder.compare(a, a.clone());
        assertEquals(0, r.getMovedSegments());
        assertEquals(0, r.getLongestRun());
        assertNull(r.getSample());
        assertEquals(3, r.getNgram());
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 40})
    public void testOneSpanMovedEndToFront(int len) {
        String[] a = words(new Random(2), 2000);
        TokenOrder.Result r = TokenOrder.compare(a, moveToFront(a, 1900, len));
        assertEquals(1, r.getMovedSegments());
        assertEquals(len, r.getMovedTokens());
        assertEquals(String.join(" ", Arrays.copyOfRange(a, 1900, 1900 + Math.min(len, 20))),
                r.getSample());
    }

    @Test
    public void testTwoTokenMoveIsBelowTheFloor() {
        String[] a = words(new Random(3), 2000);
        assertEquals(0, TokenOrder.compare(a, moveToFront(a, 1900, 4)).getMovedSegments());
    }

    @Test
    public void testDeletionsAndInsertionsAreNotMoves() {
        for (long seed = 0; seed < 200; seed++) {
            Random r = new Random(seed);
            String[] a = words(r, 200 + r.nextInt(3000));
            List<String> b = new ArrayList<>();
            for (String t : a) {
                if (r.nextInt(10) > 0) {
                    b.add(t);
                }
            }
            b.addAll(r.nextInt(b.size()), Arrays.asList(words(r, 30)));
            TokenOrder.Result res = TokenOrder.compare(a, b.toArray(new String[0]));
            assertEquals(0, res.getMovedSegments(), "seed=" + seed);
        }
    }

    @Test
    public void testRepetitiveTextIsNotScored() {
        String[] a = new String[500];
        for (int i = 0; i < a.length; i++) {
            a[i] = i % 2 == 0 ? "page" : "header";
        }
        assertNull(TokenOrder.compare(a, a.clone()));
    }

    @Test
    public void testLis() {
        boolean[] keep = TokenOrder.lis(new int[]{0, 1, 9, 10, 2, 3, 4});
        assertArrayEquals(new boolean[]{true, true, false, false, true, true, true}, keep);
    }
}
