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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class HashedArbitrationTest {

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Charset W1252 = Charset.forName("windows-1252");
    private static final Charset L1 = StandardCharsets.ISO_8859_1;

    private final HashedJunkModel model = HashedJunkModelTest.testModel();

    @Test
    public void championIsTheHighestPairValueAndTheLogitIsOverTheRunnerUp() {
        Map<Charset, String> texts = new LinkedHashMap<>();
        texts.put(UTF8, "Άρθρα του Ευρωπαϊκού");
        texts.put(W1252, "¶ρθρα του Ευρωπαϊκού");
        texts.put(L1, "Ά ρθρα");
        HashedArbitration.Decision d = HashedArbitration.decide(model, texts, null, null);
        Charset best = null;
        for (Map.Entry<Charset, String> e : texts.entrySet()) {
            if (best == null || model.pairValue(e.getValue()) > model.pairValue(texts.get(best))) {
                best = e.getKey();
            }
        }
        assertEquals(best, d.getChampion());
        double runner = Double.NEGATIVE_INFINITY;
        String runnerText = null;
        for (String t : texts.values()) {
            if (!t.equals(texts.get(best)) && model.pairValue(t) > runner) {
                runner = model.pairValue(t);
                runnerText = t;
            }
        }
        assertEquals(model.pairwiseLogit(texts.get(best), runnerText), d.getLogit(), 0);
        assertTrue(d.getLogit() >= 0);
        assertNull(d.getAnchor());
        assertEquals(best, d.pick(Double.MAX_VALUE));
    }

    @Test
    public void thinMarginDefersToTheStructuralAnchorThenTheDeclaredOne() {
        Map<Charset, String> texts = new LinkedHashMap<>();
        texts.put(UTF8, "café");
        texts.put(W1252, "cafÃ©");
        HashedArbitration.Decision d = HashedArbitration.decide(model, texts, null, null);
        Charset loser = d.getChampion().equals(UTF8) ? W1252 : UTF8;
        HashedArbitration.Decision s = HashedArbitration.decide(model, texts, loser, d.getChampion());
        assertEquals(loser, s.getAnchor());
        assertEquals(loser, s.pick(d.getLogit() + 1e-6));
        assertEquals(d.getChampion(), s.pick(d.getLogit()));
        assertTrue(s.defers(d.getLogit() + 1));
        assertEquals(loser, HashedArbitration.decide(model, texts, null, loser).getAnchor());
        // the model's own threshold
        assertEquals(s.pick(model.getDeferThreshold()), s.pick());
    }

    @Test
    public void identicalTextsHaveNoMarginAndNeverDefer() {
        Map<Charset, String> texts = new LinkedHashMap<>();
        texts.put(W1252, "plain");
        texts.put(L1, "plain");
        HashedArbitration.Decision d = HashedArbitration.decide(model, texts, L1, null);
        assertEquals(W1252, d.getChampion());
        assertTrue(Double.isNaN(d.getLogit()));
        assertEquals(W1252, d.pick(Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> HashedArbitration.decide(model, new LinkedHashMap<>(), null, null));
    }
}
