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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.tika.ml.junkdetect.HashedArbitration;
import org.apache.tika.ml.junkdetect.HashedJunkModel;
import org.apache.tika.ml.junkdetect.HtmlContentCleaner;
import org.apache.tika.ml.junkdetect.JunkFilterEncodingDetector;

/**
 * Trains a {@link HashedJunkModel} from train data only.
 *
 * <ul>
 *   <li><b>Pairwise head:</b> logistic regression with no bias on L1-normalized differences of
 *       n-gram counts between a correct and a wrong decode, AdaGrad.  Pairs: Wikipedia units
 *       against synthetic wrong decodes; gold train pages under fixed decoders; gold pages
 *       through the real detector chain (the candidates the junk filter weighs); re-encoded
 *       web pages through the chain.  Gold pages of the even half only.</li>
 *   <li><b>Deferral threshold:</b> a second pairwise model is fitted on the odd half, and the
 *       threshold is swept on the even half with {@link HashedArbitration} (cross-fit).</li>
 *   <li><b>Pointwise head:</b> logistic regression with a bias on n-gram-normalized counts,
 *       clean text (Wikipedia, clean even-half gold pages) against one synthetic junk version
 *       of each.</li>
 * </ul>
 *
 * <p>The odd half is never fitted by the bundled model; it is left for scoring.  Every choice
 * lives in {@link HashedTrainingConfig}; the data locations come from
 * {@code train-hashed-junk-model.sh}.
 */
public final class TrainHashedJunkModel {

    /** Where the training data lives. */
    static final class Inputs {
        final Path corpus;
        final Path built;
        final Path gold;
        final Path sample;
        final Path sampleDocs;

        Inputs(Path corpus, Path built, Path gold, Path sample, Path sampleDocs) {
            this.corpus = corpus;
            this.built = built;
            this.gold = gold;
            this.sample = sample;
            this.sampleDocs = sampleDocs;
        }
    }

    /** Training sizes; {@link #production()} is {@link HashedTrainingConfig}. */
    static final class Params {
        int log2Size = HashedTrainingConfig.LOG2_SIZE;
        int maxN = HashedTrainingConfig.MAX_N;
        int clip = HashedTrainingConfig.CLIP_CHARS;
        int wikiSentences = HashedTrainingConfig.WIKI_SENTENCES_PER_LANGUAGE;
        int wikiDocs = HashedTrainingConfig.WIKI_DOCS_PER_LANGUAGE;
        int wikiDocChars = HashedTrainingConfig.WIKI_DOC_CHARS;
        int pairEpochs = HashedTrainingConfig.PAIR_EPOCHS;
        int pointEpochs = HashedTrainingConfig.POINT_EPOCHS;
        double maxMissingLanguage = HashedTrainingConfig.MAX_MISSING_LANGUAGE;

        static Params production() {
            return new Params();
        }
    }

    /** What a run produced. */
    static final class Result {
        HashedJunkModel model;
        double deferThreshold;
        final Map<String, String> report = new LinkedHashMap<>();
        final List<Deferral> deferrals = new ArrayList<>();
    }

    private TrainHashedJunkModel() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> a = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (!args[i].startsWith("--")) {
                usage("unexpected argument " + args[i]);
            }
            a.put(args[i].substring(2), args[i + 1]);
        }
        if (args.length % 2 != 0) {
            usage("odd number of arguments");
        }
        for (String k : new String[]{"corpus", "built", "gold", "sample", "sample-docs", "output"}) {
            if (!a.containsKey(k)) {
                usage("missing --" + k);
            }
        }
        for (String k : a.keySet()) {
            if (!Arrays.asList("corpus", "built", "gold", "sample", "sample-docs", "output",
                    "git-commit").contains(k)) {
                usage("unknown option --" + k + " (training choices live in HashedTrainingConfig)");
            }
        }
        Inputs in = new Inputs(Paths.get(a.get("corpus")), Paths.get(a.get("built")),
                Paths.get(a.get("gold")), Paths.get(a.get("sample")), Paths.get(a.get("sample-docs")));
        Path out = Paths.get(a.get("output"));
        Result r = train(in, Params.production(), System.out);
        try (OutputStream os = Files.newOutputStream(out)) {
            r.model.write(os);
        }
        Path sweep = out.resolveSibling(out.getFileName() + ".defer-sweep.tsv");
        List<String> rows = new ArrayList<>();
        rows.add("path\tlabel\tchampion\tanchor\tlogit\tdeferring");
        Path goldDir = in.gold.toAbsolutePath().getParent();
        for (Deferral d : r.deferrals) {
            rows.add(String.join("\t", goldDir.relativize(d.page.path.toAbsolutePath()).toString(),
                    String.join(",", d.page.labels), d.champion, d.anchor, fmt(d.logit),
                    d.anchorRight ? "fixes" : "breaks"));
        }
        Files.write(sweep, rows, StandardCharsets.UTF_8);
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("format", "JUNKHNG1");
        extra.put("gold", TrainingProvenance.shortPath(in.gold));
        extra.put("gold_sha256", TrainingProvenance.sha256(in.gold));
        extra.put("real_decision_sample", TrainingProvenance.shortPath(in.sample));
        extra.put("real_decision_sample_sha256", TrainingProvenance.sha256(in.sample));
        extra.put("source_corpus_dir", TrainingProvenance.shortPath(in.corpus));
        extra.putAll(r.report);
        TrainingProvenance.writeModelProvenance(out, in.built, args, a.get("git-commit"),
                List.of(HashedTrainingConfig.class), extra);
        System.out.println("Wrote " + out + " and " + out + TrainingProvenance.PROVENANCE_SUFFIX);
    }

    private static void usage(String err) {
        System.err.println("ERROR: " + err);
        System.err.println("Usage: TrainHashedJunkModel --corpus <dir> --built <dir> --gold <halves.tsv>"
                + " --sample <sample.tsv> --sample-docs <dir> --output <model> [--git-commit <sha>]");
        System.exit(2);
    }

    static Result train(Inputs in, Params p, PrintStream log) throws IOException {
        Result res = new Result();
        long t0 = System.currentTimeMillis();

        // ---- Wikipedia units
        Map<Long, String> lang = WikipediaUnits.languageMap(in.corpus);
        List<WikipediaUnits.Unit> pairUnits = WikipediaUnits.units(in.built, "train", p.wikiSentences,
                p.wikiDocs, p.wikiDocChars, lang, HashedTrainingConfig.WIKI_PAIR_SEED);
        List<WikipediaUnits.Unit> pointUnits = WikipediaUnits.units(in.built, "train", p.wikiSentences,
                p.wikiDocs, p.wikiDocChars, lang, HashedTrainingConfig.WIKI_POINT_SEED);
        lang = null;
        double missing = missingLanguage(pairUnits);
        res.report.put("wiki_units", String.valueOf(pairUnits.size()));
        res.report.put("wiki_units_without_language", fmt(missing));
        log.printf(Locale.ROOT, "wikipedia units %d (%.4f without a language) [%ds]%n", pairUnits.size(),
                missing, secs(t0));
        if (missing > p.maxMissingLanguage) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "%.2f%% of units have no language; the built data and the corpus disagree on"
                            + " normalization or line parsing", 100 * missing));
        }
        List<HashedFeatures.Vec> wikiPairs = PairSources.flatten(PairSources.inOrder(pairUnits.size(), i -> {
            WikipediaUnits.Unit u = pairUnits.get(i);
            Random r = new Random(WikipediaUnits.hash(u.text));
            HashedFeatures.Counts g = HashedFeatures.counts(u.text, p.log2Size, p.maxN);
            List<HashedFeatures.Vec> out = new ArrayList<>();
            for (String wd : JunkFamilies.wrongDecodes(u.text, u.script, r, HashedTrainingConfig.WIKI_WRONG_DECODES)) {
                HashedFeatures.Vec v = HashedFeatures.pairDiff(g, HashedFeatures.counts(wd, p.log2Size, p.maxN));
                if (v != null) {
                    out.add(v);
                }
            }
            return out;
        }));
        res.report.put("pairs_wiki", String.valueOf(wikiPairs.size()));

        // ---- gold and the chain
        List<GoldPage> gold = GoldPage.read(in.gold);
        List<GoldPage> even = new ArrayList<>();
        List<GoldPage> odd = new ArrayList<>();
        for (GoldPage g : gold) {
            ("even".equals(g.half) ? even : odd).add(g);
        }
        res.report.put("gold_pages_even", String.valueOf(even.size()));
        res.report.put("gold_pages_odd", String.valueOf(odd.size()));
        ChainProbe chain = new ChainProbe();
        res.report.put("chain", chain.describe());
        PairSources.RealDecision real = PairSources.realDecision(in.sample, in.sampleDocs, chain,
                p.log2Size, p.maxN, p.clip);
        res.report.put("real_decision_pages", String.valueOf(real.pages));
        res.report.put("real_decision_variants", String.valueOf(real.variants));
        res.report.put("real_decision_errors", String.valueOf(real.errors));
        res.report.put("pairs_real_decision_fit", String.valueOf(real.fit.size()));
        res.report.put("pairs_real_decision_select", String.valueOf(real.select.size()));
        log.printf(Locale.ROOT, "real-decision pages %d, variants %d, errors %d, pairs fit %d select %d [%ds]%n",
                real.pages, real.variants, real.errors, real.fit.size(), real.select.size(), secs(t0));

        // ---- cross-fit model (odd half) -> deferral threshold, swept on the even half
        float[] wSwap = trainPairwise(pairs(wikiPairs, odd, chain, real, p, res.report, "odd"),
                real.select, p, log, "cross-fit (odd)");
        Quantized qSwap = quantize(wSwap);
        wSwap = null;
        HashedJunkModel swap = HashedJunkModel.create(p.log2Size, p.maxN, qSwap.scale, qSwap.q, 0f,
                1f, 0f, new byte[1 << p.log2Size], Set.of());
        double t = sweepThreshold(swap, even, chain, res, log);
        res.deferThreshold = t;
        log.printf(Locale.ROOT, "deferral threshold %.2f [%ds]%n", t, secs(t0));

        // ---- bundled pairwise head (even half)
        float[] wPair = trainPairwise(pairs(wikiPairs, even, chain, real, p, res.report, "even"),
                real.select, p, log, "model (even)");
        wikiPairs = null;
        Quantized qPair = quantize(wPair);
        wPair = null;

        // ---- pointwise head
        float[] wPoint = trainPointwise(pointUnits, even, p, res.report, log);
        int size = 1 << p.log2Size;
        float bias = wPoint[size];
        Quantized qPoint = quantize(Arrays.copyOf(wPoint, size));
        wPoint = null;

        Set<Character.UnicodeScript> scripts = trainedScripts(in.built);
        res.report.put("trained_scripts", scripts.toString());
        res.model = HashedJunkModel.create(p.log2Size, p.maxN, qPair.scale, qPair.q, (float) t,
                qPoint.scale, bias, qPoint.q, scripts);
        checkAgreement(res.model, qPair, qPoint, bias, pointUnits, p);
        res.report.put("deferral_threshold", fmt(t));
        res.report.put("train_seconds", String.valueOf(secs(t0)));
        log.printf(Locale.ROOT, "done [%ds]%n", secs(t0));
        return res;
    }

    private static List<HashedFeatures.Vec> pairs(List<HashedFeatures.Vec> wiki, List<GoldPage> fit,
                                                  ChainProbe chain, PairSources.RealDecision real,
                                                  Params p, Map<String, String> report, String half) {
        List<HashedFeatures.Vec> dec = PairSources.goldDecoderPairs(fit, HashedTrainingConfig.GOLD_DECODERS,
                p.log2Size, p.maxN, p.clip);
        List<HashedFeatures.Vec> ch = PairSources.goldChainPairs(fit, chain, p.log2Size, p.maxN, p.clip);
        report.put("pairs_gold_decoders_" + half, String.valueOf(dec.size()));
        report.put("pairs_gold_chain_" + half, String.valueOf(ch.size()));
        List<HashedFeatures.Vec> all = new ArrayList<>(wiki.size() + 2 * dec.size() + 4 * ch.size() + real.fit.size());
        all.addAll(wiki);
        repeat(all, dec, HashedTrainingConfig.GOLD_PAIR_WEIGHT);
        repeat(all, ch, HashedTrainingConfig.CHAIN_PAIR_WEIGHT);
        repeat(all, real.fit, HashedTrainingConfig.REAL_DECISION_PAIR_WEIGHT);
        return all;
    }

    private static void repeat(List<HashedFeatures.Vec> into, List<HashedFeatures.Vec> from, int times) {
        for (HashedFeatures.Vec v : from) {
            for (int r = 0; r < times; r++) {
                into.add(v);
            }
        }
    }

    /** AdaGrad on log(1 + exp(-w·x)) with L2; no bias, so the model is antisymmetric. */
    static float[] trainPairwise(List<HashedFeatures.Vec> pairs, List<HashedFeatures.Vec> select, Params p,
                                 PrintStream log, String name) {
        int size = 1 << p.log2Size;
        float[] w = new float[size];
        float[] g2 = new float[size];
        double lr = HashedTrainingConfig.PAIR_LEARNING_RATE;
        double l2 = HashedTrainingConfig.PAIR_L2;
        List<HashedFeatures.Vec> order = new ArrayList<>(pairs);
        Random rnd = new Random(HashedTrainingConfig.PAIR_SEED);
        for (int ep = 0; ep < p.pairEpochs; ep++) {
            Collections.shuffle(order, rnd);
            double loss = 0;
            long wrong = 0;
            for (HashedFeatures.Vec v : order) {
                double m = v.dot(w);
                if (m <= 0) {
                    wrong++;
                }
                double sg = 1.0 / (1.0 + Math.exp(m));
                loss += Math.log1p(Math.exp(-m));
                for (int q = 0; q < v.idx.length; q++) {
                    int k = v.idx[q];
                    double grad = -sg * v.val[q] + l2 * w[k];
                    g2[k] += (float) (grad * grad);
                    w[k] -= (float) (lr * grad / Math.sqrt(g2[k] + 1e-8));
                }
            }
            long ok = 0;
            for (HashedFeatures.Vec v : select) {
                if (v.dot(w) > 0) {
                    ok++;
                }
            }
            log.printf(Locale.ROOT, "%s epoch %d: pairs %d loss %.4f trainErr %.4f selectAcc %.4f%n", name, ep,
                    order.size(), loss / Math.max(1, order.size()), wrong / (double) Math.max(1, order.size()),
                    select.isEmpty() ? Double.NaN : ok / (double) select.size());
        }
        return w;
    }

    /** Clean examples (label 1) and one junk version of each (label 0); weights then the bias. */
    static float[] trainPointwise(List<WikipediaUnits.Unit> units, List<GoldPage> fit, Params p,
                                  Map<String, String> report, PrintStream log) {
        List<String[]> clean = new ArrayList<>();
        for (WikipediaUnits.Unit u : units) {
            clean.add(new String[]{u.script, u.text});
        }
        List<String> pages = PairSources.inOrder(fit.size(), i -> {
            GoldPage g = fit.get(i);
            if (!g.isClean()) {
                return null;
            }
            String t = PairSources.clip(HtmlContentCleaner.clean(new String(PairSources.read(g.path),
                    PairSources.decodeAs(g.labels.get(0)))), p.clip);
            return t.codePointCount(0, t.length()) >= HashedTrainingConfig.POINT_MIN_PAGE_CODEPOINTS ? t : null;
        });
        int webPages = 0;
        for (String t : pages) {
            if (t != null) {
                clean.add(new String[]{"WEB", t});
                webPages++;
            }
        }
        report.put("point_clean_wiki", String.valueOf(units.size()));
        report.put("point_clean_web", String.valueOf(webPages));
        List<HashedFeatures.Vec[]> ex = PairSources.inOrder(clean.size(), i -> {
            String[] c = clean.get(i);
            Random r = new Random(WikipediaUnits.hash(c[1]) ^ 0x5EEDL);
            HashedFeatures.Vec pos = HashedFeatures.pointVec(HashedFeatures.counts(c[1], p.log2Size, p.maxN));
            String fam = JunkFamilies.TRAIN_FAMILIES.get(r.nextInt(JunkFamilies.TRAIN_FAMILIES.size()));
            String j = JunkFamilies.corrupt(c[1], fam, c[0], r);
            HashedFeatures.Vec neg = j == null || j.equals(c[1]) ? null
                    : HashedFeatures.pointVec(HashedFeatures.counts(j, p.log2Size, p.maxN));
            return new HashedFeatures.Vec[]{pos, neg};
        });
        List<HashedFeatures.Vec> xs = new ArrayList<>();
        List<Boolean> ys = new ArrayList<>();
        for (HashedFeatures.Vec[] e : ex) {
            xs.add(e[0]);
            ys.add(true);
            if (e[1] != null) {
                xs.add(e[1]);
                ys.add(false);
            }
        }
        damagedExamples(fit, p, report, xs, ys);
        report.put("point_examples", String.valueOf(xs.size()));
        int size = 1 << p.log2Size;
        float[] w = new float[size + 1];
        float[] g2 = new float[size + 1];
        double lr = HashedTrainingConfig.POINT_LEARNING_RATE;
        double l2 = HashedTrainingConfig.POINT_L2;
        Integer[] order = new Integer[xs.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        List<Integer> ord = Arrays.asList(order);
        Random rnd = new Random(HashedTrainingConfig.POINT_SEED);
        for (int ep = 0; ep < p.pointEpochs; ep++) {
            Collections.shuffle(ord, rnd);
            double loss = 0;
            for (int i : ord) {
                HashedFeatures.Vec v = xs.get(i);
                boolean y = ys.get(i);
                double pr = 1 / (1 + Math.exp(-(w[size] + v.dot(w))));
                loss -= y ? Math.log(pr + 1e-12) : Math.log(1 - pr + 1e-12);
                double gr = pr - (y ? 1 : 0);
                for (int q = 0; q < v.idx.length; q++) {
                    int k = v.idx[q];
                    double gk = gr * v.val[q] + l2 * w[k];
                    g2[k] += (float) (gk * gk);
                    w[k] -= (float) (lr * gk / Math.sqrt(g2[k] + 1e-8));
                }
                g2[size] += (float) (gr * gr);
                w[size] -= (float) (lr * gr / Math.sqrt(g2[size] + 1e-8));
            }
            log.printf(Locale.ROOT, "pointwise epoch %d: examples %d loss %.4f bias %.3f%n", ep, xs.size(),
                    loss / Math.max(1, xs.size()), w[size]);
        }
        return w;
    }

    /**
     * Real damage from gold spans ({@link HashedTrainingConfig#DAMAGED_SPAN_WEIGHT}): padded spans as
     * junk, the rest of the page as clean.  Pages whose visible text no longer hashes to their
     * {@code vis_sha1} are skipped and counted: their spans point at other text.
     */
    static void damagedExamples(List<GoldPage> fit, Params p, Map<String, String> report,
                                List<HashedFeatures.Vec> xs, List<Boolean> ys) {
        int weight = HashedTrainingConfig.DAMAGED_SPAN_WEIGHT;
        int pad = HashedTrainingConfig.DAMAGED_SPAN_PAD;
        int[] stale = new int[1];
        List<List<Object[]>> per = PairSources.inOrder(fit.size(), i -> {
            GoldPage g = fit.get(i);
            List<Object[]> out = new ArrayList<>();
            if (weight == 0 || g.damageSpans.length == 0
                    || !HashedTrainingConfig.DAMAGED_SPAN_BASES.contains(g.spanBasis)) {
                return out;
            }
            String text = HtmlContentCleaner.clean(new String(PairSources.read(g.path),
                    PairSources.decodeAs(g.labels.get(0))));
            if (!PairSources.sha1Hex(text.getBytes(StandardCharsets.UTF_8)).equals(g.visSha1)) {
                synchronized (stale) {
                    stale[0]++;
                }
                return out;
            }
            int[] cps = text.codePoints().toArray();
            boolean[] bad = new boolean[cps.length];
            for (int[] s : g.damageSpans) {
                int lo = Math.max(0, s[0] - pad);
                int hi = Math.min(cps.length, s[1] + pad);
                if (lo < hi) {
                    out.add(new Object[]{new String(cps, lo, hi - lo), false});
                    Arrays.fill(bad, lo, hi, true);
                }
            }
            StringBuilder rest = new StringBuilder();
            for (int k = 0; k < cps.length && rest.length() < p.clip; k++) {
                rest.appendCodePoint(bad[k] ? ' ' : cps[k]);
            }
            String r = rest.toString().trim();
            if (r.codePointCount(0, r.length()) >= HashedTrainingConfig.POINT_MIN_PAGE_CODEPOINTS) {
                out.add(new Object[]{r, true});
            }
            return out;
        });
        int junk = 0;
        int clean = 0;
        for (List<Object[]> l : per) {
            for (Object[] e : l) {
                HashedFeatures.Vec v = HashedFeatures.pointVec(HashedFeatures.counts((String) e[0], p.log2Size, p.maxN));
                for (int r = 0; r < weight; r++) {
                    xs.add(v);
                    ys.add((Boolean) e[1]);
                }
                if ((Boolean) e[1]) {
                    clean++;
                } else {
                    junk++;
                }
            }
        }
        report.put("point_damaged_windows", String.valueOf(junk));
        report.put("point_damaged_clean_rest", String.valueOf(clean));
        report.put("point_damaged_stale_pages", String.valueOf(stale[0]));
    }

    /** A cross-fit page where deferring changes correctness: the champion or the anchor is right, not both. */
    static final class Deferral {
        final GoldPage page;
        final String champion;
        final String anchor;
        final double logit;
        final boolean anchorRight;

        Deferral(GoldPage page, String champion, String anchor, double logit, boolean anchorRight) {
            this.page = page;
            this.champion = champion;
            this.anchor = anchor;
            this.logit = logit;
            this.anchorRight = anchorRight;
        }
    }

    /**
     * Cross-fit sweep: runs {@code swap} over {@code pages} as the junk filter would and keeps the
     * pages where deferring to the anchor changes correctness; the threshold comes from
     * {@link #chooseThreshold}.
     */
    static double sweepThreshold(HashedJunkModel swap, List<GoldPage> pages, ChainProbe chain,
                                 Result res, PrintStream log) {
        List<Deferral> rec = PairSources.inOrder(pages.size(), i -> {
            GoldPage g = pages.get(i);
            JunkFilterEncodingDetector.Candidates c;
            try {
                c = chain.candidates(PairSources.read(g.path));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (c == null || c.getRule() != JunkFilterEncodingDetector.Candidates.Rule.MODEL) {
                return null;
            }
            HashedArbitration.Decision d = HashedArbitration.decide(swap, c);
            if (d.getAnchor() == null || d.getAnchor().equals(d.getChampion())) {
                return null;
            }
            Set<String> good = PairSources.goodTexts(c.getProbe(), g.labels);
            boolean champOk = good.contains(c.getTexts().get(d.getChampion()));
            boolean anchorOk = good.contains(c.getTexts().get(d.getAnchor()));
            return champOk == anchorOk ? null
                    : new Deferral(g, d.getChampion().name(), d.getAnchor().name(), d.getLogit(), anchorOk);
        });
        List<double[]> pts = new ArrayList<>();
        for (Deferral d : rec) {
            if (d != null) {
                res.deferrals.add(d);
                pts.add(new double[]{d.logit, d.anchorRight ? 1 : -1});
            }
        }
        StringBuilder curve = new StringBuilder();
        int steps = (int) Math.round(HashedTrainingConfig.DEFER_MAX / HashedTrainingConfig.DEFER_STEP);
        for (int s = 1; s <= steps; s++) {
            double t = s * HashedTrainingConfig.DEFER_STEP;
            int[] fb = count(pts, 0, t);
            curve.append(String.format(Locale.ROOT, "%s%.2f:+%d/-%d", curve.length() == 0 ? "" : " ", t, fb[0], fb[1]));
        }
        double t = chooseThreshold(pts);
        int[] at = count(pts, 0, t);
        res.report.put("defer_sweep_pages", String.valueOf(pages.size()));
        res.report.put("defer_sweep_decisive_pages", String.valueOf(pts.size()));
        res.report.put("defer_sweep_curve", curve.toString());
        res.report.put("defer_sweep_at_threshold", "+" + at[0] + "/-" + at[1]);
        log.println("deferral sweep (threshold:+fixed/-broken) " + curve);
        return t;
    }

    /**
     * Raises the threshold one {@link HashedTrainingConfig#DEFER_BAND} at a time while the pages
     * whose margin falls in the new band are fixed at least
     * {@link HashedTrainingConfig#DEFER_MIN_RATIO} times as often as broken; stops at the first band
     * that is not (an empty band included).  Maximizing the net instead chases label noise: past the
     * first bands, extra deferrals fix about as many pages as they break.
     *
     * @param points {logit, +1 if deferring fixes the page, -1 if it breaks it}
     */
    static double chooseThreshold(List<double[]> points) {
        double t = 0;
        double band = HashedTrainingConfig.DEFER_BAND;
        for (double hi = band; hi <= HashedTrainingConfig.DEFER_MAX + 1e-9; hi += band) {
            int[] fb = count(points, hi - band, hi);
            if (fb[0] <= fb[1] || fb[0] < HashedTrainingConfig.DEFER_MIN_RATIO * fb[1]) {
                break;
            }
            t = hi;
        }
        return t;
    }

    /** {fixed, broken} among points with lo <= logit < hi. */
    private static int[] count(List<double[]> points, double lo, double hi) {
        int[] fb = new int[2];
        for (double[] p : points) {
            if (p[0] >= lo && p[0] < hi) {
                fb[p[1] > 0 ? 0 : 1]++;
            }
        }
        return fb;
    }

    /** int8 weights and their scale: {@link HashedTrainingConfig#QUANT_QUANTILE} of |w| maps to 127. */
    static final class Quantized {
        final byte[] q;
        final float scale;

        Quantized(byte[] q, float scale) {
            this.q = q;
            this.scale = scale;
        }
    }

    static Quantized quantize(float[] w) {
        float[] s = new float[w.length];
        for (int i = 0; i < w.length; i++) {
            s[i] = Math.abs(w[i]);
        }
        Arrays.sort(s);
        float clip = s[Math.min(s.length - 1, (int) (s.length * HashedTrainingConfig.QUANT_QUANTILE))];
        if (!(clip > 0)) {
            throw new IllegalStateException("all weights are zero; nothing was learned");
        }
        float scale = clip / 127f;
        byte[] q = new byte[w.length];
        for (int i = 0; i < w.length; i++) {
            q[i] = (byte) Math.round(Math.max(-clip, Math.min(clip, w[i])) / scale);
        }
        return new Quantized(q, scale);
    }

    /**
     * Fails unless the written model scores like the trainer's own features under the same
     * quantized weights: the trainer and the runtime must see the same buckets.
     */
    static void checkAgreement(HashedJunkModel m, Quantized pair, Quantized point, float bias,
                               List<WikipediaUnits.Unit> units, Params p) {
        float[] wp = dequantize(pair);
        float[] wq = dequantize(point);
        int n = Math.min(500, units.size());
        double worst = 0;
        for (int i = 0; i < n; i++) {
            String text = units.get(i).text;
            HashedFeatures.Counts c = HashedFeatures.counts(text, p.log2Size, p.maxN);
            double mine = bias + HashedFeatures.pointVec(c).dot(wq);
            worst = Math.max(worst, Math.abs(mine - m.score(text).getLogit()));
            String bad = new String(text.getBytes(StandardCharsets.UTF_8), Charset.forName("windows-1252"));
            HashedFeatures.Vec d = HashedFeatures.pairDiff(c, HashedFeatures.counts(bad, p.log2Size, p.maxN));
            if (d != null) {
                worst = Math.max(worst, Math.abs(d.dot(wp) - m.pairwiseLogit(text, bad)));
            }
        }
        if (worst > 1e-3) {
            throw new IllegalStateException("trainer and runtime disagree by " + worst
                    + "; the trainer's features are not the model's");
        }
    }

    private static float[] dequantize(Quantized q) {
        float[] w = new float[q.q.length];
        for (int i = 0; i < w.length; i++) {
            w[i] = q.q[i] * q.scale;
        }
        return w;
    }

    /** Scripts with a built train file; their letters count as covered. */
    static Set<Character.UnicodeScript> trainedScripts(Path built) throws IOException {
        Set<Character.UnicodeScript> s = EnumSet.noneOf(Character.UnicodeScript.class);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(built, "*.train.gz")) {
            for (Path f : ds) {
                String name = f.getFileName().toString().replace(".train.gz", "").toUpperCase(Locale.ROOT);
                try {
                    s.add(Character.UnicodeScript.valueOf(name));
                } catch (IllegalArgumentException e) {
                    throw new IOException("built file " + f + " is not named after a Unicode script", e);
                }
            }
        }
        return s;
    }

    static double missingLanguage(List<WikipediaUnits.Unit> units) {
        long miss = 0;
        for (WikipediaUnits.Unit u : units) {
            if (WikipediaUnits.NO_LANGUAGE.equals(u.language)) {
                miss++;
            }
        }
        return units.isEmpty() ? 0 : miss / (double) units.size();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static long secs(long t0) {
        return (System.currentTimeMillis() - t0) / 1000;
    }
}
