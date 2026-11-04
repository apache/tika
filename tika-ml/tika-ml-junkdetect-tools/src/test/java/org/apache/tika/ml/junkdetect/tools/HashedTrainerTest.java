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
package org.apache.tika.ml.junkdetect.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.ml.junkdetect.HashedJunkModel;

public class HashedTrainerTest {

    private static TrainHashedJunkModel.Params small() {
        TrainHashedJunkModel.Params p = new TrainHashedJunkModel.Params();
        p.log2Size = 16;
        p.pairEpochs = 3;
        p.pointEpochs = 3;
        return p;
    }

    /** The trainer's features under quantized weights score exactly as the runtime model. */
    @Test
    public void trainerFeaturesAreTheRuntimeFeatures() {
        Random r = new Random(1);
        float[] w = new float[1 << 16];
        float[] v = new float[1 << 16];
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) r.nextGaussian();
            v[i] = (float) r.nextGaussian();
        }
        TrainHashedJunkModel.Quantized qp = TrainHashedJunkModel.quantize(w);
        TrainHashedJunkModel.Quantized qv = TrainHashedJunkModel.quantize(v);
        HashedJunkModel m = HashedJunkModel.create(16, 3, qp.scale, qp.q, 0f, qv.scale, 0.25f, qv.q, Set.of());
        float[] dp = new float[w.length];
        float[] dv = new float[w.length];
        for (int i = 0; i < w.length; i++) {
            dp[i] = qp.q[i] * qp.scale;
            dv[i] = qv.q[i] * qv.scale;
        }
        for (int i = 0; i < 100; i++) {
            String a = HashedTrainingFixture.sentence(HashedTrainingFixture.FR, r) + " ＡＢ­  12";
            String b = new String(a.getBytes(StandardCharsets.UTF_8), Charset.forName("windows-1252"));
            HashedFeatures.Counts ca = HashedFeatures.counts(a, 16, 3);
            assertEquals(m.pairwiseLogit(a, b), HashedFeatures.pairDiff(ca, HashedFeatures.counts(b, 16, 3)).dot(dp), 1e-5);
            assertEquals(m.score(a).getLogit(), 0.25 + HashedFeatures.pointVec(ca).dot(dv), 1e-5);
        }
        assertNull(HashedFeatures.pairDiff(HashedFeatures.counts("same", 16, 3), HashedFeatures.counts("same", 16, 3)));
    }

    @Test
    public void quantizationClipsAtTheQuantileAndRejectsAllZero() {
        float[] w = new float[20000];
        w[0] = 1000f;
        w[1] = -2f;
        w[2] = 1f;
        for (int i = 3; i < w.length; i++) {
            w[i] = 2f;
        }
        TrainHashedJunkModel.Quantized q = TrainHashedJunkModel.quantize(w);
        assertEquals(2f / 127, q.scale, 1e-7);
        assertEquals(127, q.q[0]);
        assertEquals(-127, q.q[1]);
        assertEquals(64, q.q[2]);
        assertThrows(IllegalStateException.class, () -> TrainHashedJunkModel.quantize(new float[10]));
    }

    private static List<double[]> points(double logit, int fixes, int breaks) {
        List<double[]> p = new java.util.ArrayList<>();
        for (int i = 0; i < fixes; i++) {
            p.add(new double[]{logit, 1});
        }
        for (int i = 0; i < breaks; i++) {
            p.add(new double[]{logit, -1});
        }
        return p;
    }

    /** The band rule stops where extra deferrals stop clearly paying: the 11-04 curve gives 1.0, not 5.0. */
    @Test
    public void thresholdGrowsWhileEachBandFixesTwiceWhatItBreaks() {
        List<double[]> curve = new java.util.ArrayList<>();
        curve.addAll(points(0.5, 20, 4));   // (0,1]  +20/-4
        curve.addAll(points(1.5, 7, 5));    // (1,2]  +7/-5
        curve.addAll(points(4.5, 16, 11));  // the rest of the 11-04 curve up to 5.0
        assertEquals(1.0, TrainHashedJunkModel.chooseThreshold(curve));
        assertEquals(0.0, TrainHashedJunkModel.chooseThreshold(points(0.5, 3, 2)), "3 fixes for 2 breaks is under 2:1");
        assertEquals(0.0, TrainHashedJunkModel.chooseThreshold(points(1.5, 9, 0)), "an empty first band stops");
        List<double[]> all = new java.util.ArrayList<>();
        for (int b = 0; b < 10; b++) {
            all.addAll(points(b + 0.5, 3, 1));
        }
        assertEquals(HashedTrainingConfig.DEFER_MAX, TrainHashedJunkModel.chooseThreshold(all));
        assertEquals(0.0, TrainHashedJunkModel.chooseThreshold(List.of()));
    }

    /** Sentences rewritten by the builder (space runs, escapes, width forms) keep their language. */
    @Test
    public void everyBuiltSentenceFindsItsLanguage(@TempDir Path dir) throws IOException {
        HashedTrainingFixture f = new HashedTrainingFixture(dir);
        Map<Long, String> lang = WikipediaUnits.languageMap(f.corpus);
        List<WikipediaUnits.Unit> units = WikipediaUnits.units(f.built, "train", 1000, 50, 2000, lang, 1);
        assertTrue(units.size() > 800, "units " + units.size());
        assertEquals(0.0, TrainHashedJunkModel.missingLanguage(units));
        for (WikipediaUnits.Unit u : units) {
            assertEquals(u.script.equals("CYRILLIC") ? "rus" : "fra", u.language);
        }
    }

    @Test
    public void malformedDamageSpansAreRefused(@TempDir Path dir) throws IOException {
        Path g = dir.resolve("halves.tsv");
        Files.write(g, List.of("path\tsplit\tlabel\thalf\ttext_quality\tdamage_spans",
                "a.html\ttrain\tUTF-8\teven\tdamaged\t12-7"));
        IOException e = assertThrows(IOException.class, () -> GoldPage.read(g));
        assertTrue(e.getMessage().contains("bad damage span"), e.getMessage());
    }

    @Test
    public void goldRowsOutsideTrainAreRefused(@TempDir Path dir) throws IOException {
        Path g = dir.resolve("halves.tsv");
        Files.write(g, List.of("path\tsplit\tlabel\thalf\ttext_quality", "a.html\tdev\tUTF-8\teven\tclean"));
        IOException e = assertThrows(IOException.class, () -> GoldPage.read(g));
        assertTrue(e.getMessage().contains("train data only"), e.getMessage());
    }

    @Test
    public void reencodingKeepsWhatTheCharsetCannotHold() {
        Charset koi = Charset.forName("KOI8-R");
        assertEquals("Ж \"x\" &#8364; -", new String(PairSources.encodeWithNcr("Ж “x” € —", koi), koi));
        assertEquals("<meta charset=\"KOI8-R\"><META CHARSET=KOI8-R>",
                PairSources.rewriteMeta("<meta charset=\"utf-8\"><META CHARSET=windows-1251>", "KOI8-R"));
        assertEquals("<meta charset=x-removed>", PairSources.rewriteMeta("<meta charset=utf-8>", null));
        assertEquals(PairSources.isSelect("00/abc"), PairSources.isSelect("00/abc"));
    }

    @Test
    public void chainPairsComeFromTheJunkFiltersCandidates(@TempDir Path dir) throws IOException {
        HashedTrainingFixture f = new HashedTrainingFixture(dir);
        List<GoldPage> gold = GoldPage.read(f.gold);
        assertEquals(24, gold.size());
        ChainProbe chain = new ChainProbe();
        assertTrue(chain.describe().contains("HtmlEncodingDetector"), chain.describe());
        assertTrue(chain.describe().contains("MojibusterEncodingDetector"), chain.describe());
        List<HashedFeatures.Vec> pairs = PairSources.goldChainPairs(gold, chain, 16, 3, 6000);
        assertTrue(!pairs.isEmpty(), "no chain pairs");
        for (HashedFeatures.Vec v : pairs) {
            double l1 = 0;
            for (float x : v.val) {
                l1 += Math.abs(x);
            }
            assertEquals(1.0, l1, 1e-4);
        }
    }

    @Test
    public void trainsAModelThatPrefersTheRealText(@TempDir Path dir) throws IOException {
        HashedTrainingFixture f = new HashedTrainingFixture(dir);
        TrainHashedJunkModel.Inputs in = new TrainHashedJunkModel.Inputs(f.corpus, f.built, f.gold, f.sample,
                f.sampleDocs);
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        TrainHashedJunkModel.Result r = TrainHashedJunkModel.train(in, small(),
                new PrintStream(log, true, StandardCharsets.UTF_8));
        HashedJunkModel m = r.model;
        assertNotNull(m);
        assertTrue(r.deferThreshold >= 0 && r.deferThreshold <= HashedTrainingConfig.DEFER_MAX);
        assertEquals((float) r.deferThreshold, m.getDeferThreshold());
        assertEquals(Set.of(Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.LATIN), m.getTrainedScripts());
        for (String k : new String[]{"pairs_wiki", "pairs_gold_chain_even", "pairs_gold_decoders_odd",
                "pairs_real_decision_fit", "defer_sweep_curve", "point_clean_web", "chain"}) {
            assertTrue(r.report.containsKey(k), k + " missing from " + r.report);
        }
        assertEquals("10", r.report.get("point_clean_web"), "damaged even pages are not clean examples");
        assertEquals("1", r.report.get("point_damaged_windows"), "one fresh span on an even page");
        assertEquals("1", r.report.get("point_damaged_clean_rest"));
        assertEquals("1", r.report.get("point_damaged_stale_pages"), "the stale page's spans are skipped");
        assertEquals(r.report.get("defer_sweep_decisive_pages"), String.valueOf(r.deferrals.size()));
        for (TrainHashedJunkModel.Deferral d : r.deferrals) {
            assertTrue(!d.champion.equals(d.anchor) && d.logit >= 0, d.champion + " " + d.anchor + " " + d.logit);
        }

        Random rnd = new Random(99);
        int right = 0;
        for (int i = 0; i < 50; i++) {
            String ru = HashedTrainingFixture.sentence(HashedTrainingFixture.RU, rnd);
            String moji = new String(ru.getBytes(Charset.forName("windows-1251")), Charset.forName("windows-1252"));
            right += m.compare(ru, moji) > 0 ? 1 : 0;
        }
        assertTrue(right >= 48, "right " + right + "/50\n" + log);
        String fr = HashedTrainingFixture.sentence(HashedTrainingFixture.FR, rnd);
        assertTrue(m.score(fr).getLogit() > m.score(fr.replace(' ', '�')).getLogit());

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        m.write(bo);
        HashedJunkModel back = HashedJunkModel.load(new ByteArrayInputStream(bo.toByteArray()));
        assertEquals(m.compare(fr, fr + "Ã©"), back.compare(fr, fr + "Ã©"));
    }
}
