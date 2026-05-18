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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.quality.TextQualityComparison;

/**
 * Latin SBCS sibling discrimination.  Mojibuster correctly emits windows-1252
 * at high confidence for these inputs; the chain's tournament still picks the
 * wrong sibling (IBM850 / IBM852 / x-MacRoman).  This test exercises the
 * {@link JunkDetector#compare} math in isolation, so a failure here means the
 * model lacks signal for the sibling case, and a pass means the bug is in
 * {@link JunkFilterEncodingDetector}'s arbitration rather than in JunkDetector
 * itself.
 *
 * <p>Probe strings are synthesized Western European prose using phrases the
 * production-loss inspection ({@code 20260518-junk-charsets.md}) verified are
 * present in the failing corpus files; no corpus content is checked in.
 */
public class LatinSiblingComparisonTest {

    private static JunkDetector detector;

    @BeforeAll
    static void loadModel() throws Exception {
        detector = JunkDetector.loadFromClasspath();
    }

    private static final String[] WRONG_CHARSETS = {"IBM850", "IBM852", "x-MacRoman"};

    /** Synthesized probes — each is real Western European prose that survives
     *  a windows-1252 round-trip (no 0x81/0x8D/0x8F/0x90/0x9D unassigned-byte
     *  hazards) and contains the discriminating letters from the loss table. */
    private static final Probe[] PROBES = {
            new Probe("finnish",
                    "Talven ensimmäinen pakkasaamu Hausjärven jäällä oli häikäisevän kirkas. "
                            + "Lapset ihmettelevät, miksi vesi muuttuu yhtäkkiä kovaksi, ja äiti selittää "
                            + "miten lämpötila vaikuttaa järven pintaan koko talven ajan."),
            new Probe("french-euro",
                    "Le spécialiste propose une consultation à 115 € pour les nouveaux clients. "
                            + "Après un premier échange, il prépare un devis détaillé qui précise les "
                            + "différentes étapes du projet et les délais associés."),
            new Probe("german-umlauts",
                    "Für Anfänger empfehlen wir den Grundkurs, in dem die wichtigsten Regeln erklärt werden. "
                            + "Sie können jederzeit Fragen stellen, und unsere Lehrkräfte gehen gerne auf alle "
                            + "Schwierigkeiten ein. Die nächste Stunde beginnt am Montag um neun Uhr."),
            new Probe("portuguese",
                    "As sprites fêmeas do novo jogo trazem habilidades especiais que não existiam na versão "
                            + "anterior. Cada personagem possui uma história própria e o jogador pode escolher "
                            + "o estilo de combate que prefere antes de iniciar a primeira missão."),
            new Probe("spanish-acutes",
                    "Para obtener más información sobre el tamaño del archivo, consulte la sección "
                            + "correspondiente en la dirección indicada y éntrela en el formulario. "
                            + "Si tiene dudas, envíe un correo electrónico al equipo de soporte técnico."),
            new Probe("spanish-names",
                    "José Canalda escribió una novela de ficción y fantasía ambientada en Alcalá, "
                            + "donde un grupo de jóvenes investiga una serie de fenómenos extraños. "
                            + "La obra recibió críticas muy favorables en revistas especializadas."),
            new Probe("spanish-guillemets",
                    "Garcilaso de la Vega, conocido como »El Inca«, escribió sobre la historia de los "
                            + "pueblos andinos antes y después de la conquista. Sus textos combinan testimonio "
                            + "personal con relatos transmitidos por la tradición oral."),
    };

    @Test
    void junkDetectorPicksWindows1252OverLatinSiblings() {
        runMatrix("clean prose", probe -> probe.text);
    }

    private void runMatrix(String label, java.util.function.Function<Probe, String> shaper) {
        List<String> failures = new ArrayList<>();
        List<String> passes = new ArrayList<>();

        Charset win1252 = Charset.forName("windows-1252");

        for (Probe probe : PROBES) {
            String shaped = shaper.apply(probe);
            byte[] bytes = shaped.getBytes(win1252);
            String asWin1252 = new String(bytes, win1252);

            for (String wrong : WRONG_CHARSETS) {
                Charset wrongCs = Charset.forName(wrong);
                String asWrong = new String(bytes, wrongCs);

                TextQualityComparison cmp = detector.compare(
                        "windows-1252", asWin1252, wrong, asWrong);

                String tag = String.format("%-20s vs %-12s", probe.name, wrong);
                if ("windows-1252".equals(cmp.winner())) {
                    passes.add(String.format("PASS %s  delta=%.3f", tag, cmp.delta()));
                } else {
                    failures.add(String.format("FAIL %s  winner=%-12s delta=%.3f",
                            tag, cmp.winner(), cmp.delta()));
                }
            }
        }

        System.out.println("\n=== Latin SBCS sibling comparison: " + label + " ===");
        passes.forEach(System.out::println);
        failures.forEach(System.out::println);
        System.out.printf("%d pass, %d fail (of %d cells)%n",
                passes.size(), failures.size(), passes.size() + failures.size());

        assertEquals(0, failures.size(),
                "JunkDetector should pick windows-1252 over every Latin SBCS sibling "
                        + "for " + label + " input.  Failures above indicate the model lacks "
                        + "signal for the sibling case under this input shape.");
    }

    private static final class Probe {
        final String name;
        final String text;
        Probe(String name, String text) {
            this.name = name;
            this.text = text;
        }
    }
}
