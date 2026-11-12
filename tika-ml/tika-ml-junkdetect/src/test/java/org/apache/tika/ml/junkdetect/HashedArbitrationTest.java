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
        assertEquals(model.pairwiseDifference(texts.get(best), runnerText), d.getDifference());
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

    /**
     * From a CC page declared iso-8859-3 but written in Latin-1 with UTF-8 fragments: 0xC3 is undefined
     * in ISO-8859-3, so its decode has U+FFFD the windows-1252 decode lacks. The rule is checked with the
     * pinned test model; which decode the bundled model prefers is a thin margin (|logit| < 0.3 across
     * retrains, deferral threshold 2) and belongs to the gold chain gate, not here.
     */
    @Test
    public void declarationWithReplacementCharactersIsNotAnAnchor() throws Exception {
        Charset iso3 = Charset.forName("ISO-8859-3");
        byte[] latin1 = ("Correo Electrónico. ¡Por favor, consulte la Composición Municipal y los"
                + " Trámites Oficiales. ").getBytes(L1);
        byte[] utf8 = "Información del municipio, días de atención.".getBytes(UTF8);
        byte[] page = new byte[latin1.length + utf8.length];
        System.arraycopy(latin1, 0, page, 0, latin1.length);
        System.arraycopy(utf8, 0, page, latin1.length, utf8.length);
        Map<Charset, String> texts = new LinkedHashMap<>();
        texts.put(W1252, new String(page, W1252));
        texts.put(iso3, new String(page, iso3));
        assertTrue(texts.get(iso3).indexOf('�') >= 0);

        HashedArbitration.Decision d = HashedArbitration.decide(model, texts, null, iso3);
        assertEquals(champion(texts), d.getChampion());
        // the declared decode is an anchor only when it is the champion itself
        assertEquals(d.getChampion().equals(iso3) ? iso3 : null, d.getAnchor());
        assertEquals(d.getChampion(), d.pick(Double.MAX_VALUE));

        // a champion with as many replacement characters leaves the declaration as the anchor
        Map<Charset, String> same = new LinkedHashMap<>();
        same.put(W1252, "caf� und mehr");
        same.put(iso3, "ca�s und mehr");
        HashedArbitration.Decision b = HashedArbitration.decide(model, same, null, iso3);
        if (!b.getChampion().equals(iso3)) {
            assertEquals(iso3, b.getAnchor());
        }
        // one with fewer does not: 0x81 is undefined in windows-1252, a C1 control in ISO-8859-3
        byte[] spanish = ("¡Bienvenidos al Ayuntamiento! ¿Necesita ayuda? ").getBytes(L1);
        byte[] stray = new byte[spanish.length + page.length + 1];
        System.arraycopy(spanish, 0, stray, 0, spanish.length);
        System.arraycopy(page, 0, stray, spanish.length, page.length);
        stray[stray.length - 1] = (byte) 0x81;
        Map<Charset, String> fewer = new LinkedHashMap<>();
        fewer.put(W1252, new String(stray, W1252));
        fewer.put(iso3, new String(stray, iso3));
        assertTrue(fewer.get(W1252).indexOf('�') >= 0);
        HashedArbitration.Decision f = HashedArbitration.decide(model, fewer, null, iso3);
        assertEquals(champion(fewer), f.getChampion());
        assertEquals(f.getChampion().equals(iso3) ? iso3 : null, f.getAnchor());
    }

    /** The candidate with the highest pair value, first on ties. */
    private Charset champion(Map<Charset, String> texts) {
        Charset best = null;
        for (Map.Entry<Charset, String> e : texts.entrySet()) {
            if (best == null || model.pairValue(e.getValue()) > model.pairValue(texts.get(best))) {
                best = e.getKey();
            }
        }
        return best;
    }

    @Test
    public void identicalTextsHaveNoMarginAndNeverDefer() {
        Map<Charset, String> texts = new LinkedHashMap<>();
        texts.put(W1252, "plain");
        texts.put(L1, "plain");
        HashedArbitration.Decision d = HashedArbitration.decide(model, texts, L1, null);
        assertEquals(W1252, d.getChampion());
        assertTrue(Double.isNaN(d.getLogit()));
        assertEquals(0, d.getDifference());
        assertEquals(W1252, d.pick(Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> HashedArbitration.decide(model, new LinkedHashMap<>(), null, null));
    }
}
