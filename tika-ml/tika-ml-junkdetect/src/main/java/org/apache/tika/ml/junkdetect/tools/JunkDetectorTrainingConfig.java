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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Frozen set of training-time choices that together define a junk-detector
 * model's identity.  Any change to these values produces a meaningfully
 * different model and must be reviewed in git.
 *
 * <p>Two principles drove making this a class rather than CLI flags:
 *
 * <ol>
 *   <li><b>Reproducibility.</b>  When we look back at a model file six
 *       months later we want a single commit hash that says exactly what
 *       knobs produced it, not a half-remembered shell history.
 *   <li><b>Drift prevention.</b>  CLI flags with defaults allow accidental
 *       deviation between developers ("did you remember to pass
 *       {@code --min-target-script-frac 0.05}?").  Constants in a tracked
 *       file remove that failure mode.
 * </ol>
 *
 * <p>{@link BuildJunkTrainingData} and {@link TrainJunkModel} read the
 * values here; both tools <b>refuse to start</b> if any CLI argument
 * attempts to override a config-controlled parameter, surfacing the
 * mistake at launch time rather than silently producing a non-canonical
 * model.
 *
 * <p>The constants below reflect the choices that produced the current
 * shipping model and are recorded in the corresponding training notes
 * ({@code 20260514-junk-retrain-v6.md}).  Update them by editing this
 * file and committing the change together with the new model output.
 *
 * <p>The class has no instance state; all values are exposed as
 * {@code public static final}.  This keeps callsites short and avoids
 * the temptation of passing a runtime-mutable config around.
 *
 * <p>This is not part of the public model-loading API.  The {@link
 * org.apache.tika.ml.junkdetect.JunkDetector} runtime is configuration-
 * free; once a model file is built, all of its baked-in choices travel
 * with the file's binary format.
 */
public final class JunkDetectorTrainingConfig {

    // =======================================================================
    // Corpus build (BuildJunkTrainingData)
    // =======================================================================

    /**
     * Total UTF-8 byte budget across all script groups.  Divided
     * proportionally by per-script bigram entropy after the sampling phase.
     */
    public static final long TOTAL_BUDGET_BYTES = 500_000_000L;

    /**
     * Maximum UTF-8 bytes a single language may contribute to a
     * multi-language script bucket.  Prevents one large source (e.g. {@code
     * zho} with 8 GB of MADLAD) from dominating a multi-language script
     * model.  Buckets with only one language ignore this cap and may consume
     * their full budget.  See {@link BuildJunkTrainingData} Phase 4.
     */
    public static final long PER_LANGUAGE_CAP_BYTES = 5_000_000L;

    /**
     * Sentence-level filter: minimum fraction of non-COMMON/INHERITED
     * codepoints that must belong to the script bucket's target script for a
     * sentence to be accepted.  Set low so legitimate mixed-script content
     * (Japanese kanji + kana, Korean with hanja annotations, Chinese with
     * English citations, etc.) is preserved, but enough to reject lines that
     * are essentially off-target (e.g. an English article about Gothic in
     * the GOTHIC bucket).
     */
    public static final double MIN_TARGET_SCRIPT_FRAC = 0.05;

    /** Minimum UTF-8 byte length for a sentence to pass the quality filter. */
    public static final int MIN_BYTES_PER_SENTENCE = 50;

    /** Maximum fraction of codepoints that may be ASCII punctuation/digits. */
    public static final double MAX_PUNC_FRAC = 0.30;

    /**
     * Minimum number of sentences that must land in the dev split for a
     * script to be included in the model.  Scripts below this floor have
     * insufficient data to reliably estimate calibration statistics, which
     * inflates FPR.  With {@code DEV_FRAC = 0.10} this corresponds to a
     * total-sentence floor of {@code 500 / 0.10 = 5000} per script.
     */
    public static final int MIN_DEV_SENTENCES = 500;

    /** Lines read per language to determine the language's dominant script. */
    public static final int SCRIPT_SAMPLE_LINES = 2_000;

    /**
     * UTF-8 bytes loaded per script group for bigram entropy estimation,
     * driving the entropy-proportional budget allocation.  200 KB is
     * sufficient to characterise the bigram distribution of any single
     * script.
     */
    public static final long ENTROPY_SAMPLE_BYTES = 200_000L;

    /** Random seed for sentence shuffling and other corpus-build randomness. */
    public static final int SEED = 42;

    /**
     * Script bucket names whose source data is too thin or too off-target
     * to produce reliable per-script F1 calibration.  Excluded from the
     * model entirely; the {@link
     * org.apache.tika.ml.junkdetect.JunkDetector#score(String)} routing
     * falls back to "unknown script" behavior for these scripts.
     *
     * <p>The current selection is based on a corpus audit that found these
     * scripts either had thin native source data (e.g. THAANA: 216 train
     * sentences from Maldivian), or had sources dominated by off-target
     * content (e.g. GOTHIC: 40% of lines are {@literal <}5% Gothic — the
     * Wikipedia "gothic" directory is English text about Gothic).
     *
     * <p>Three further scripts (CANADIAN_ABORIGINAL, CHEROKEE, TIFINAGH)
     * are not listed here because the {@link #MIN_TARGET_SCRIPT_FRAC}
     * filter implicitly removes them — their MADLAD sources contain
     * effectively no native-script content at the 5% threshold.  Listing
     * them here is unnecessary and would obscure the data-quality finding.
     */
    public static final Set<String> DROP_SCRIPTS =
            Collections.unmodifiableSet(new java.util.TreeSet<>(Set.of("GOTHIC", "THAANA")));

    /**
     * Per-script byte-budget overrides applied on top of the entropy-
     * proportional allocation.  Empty in the current configuration.
     *
     * <p>Under v6 the {@code HAN=60MB} experiment <em>worsened</em> every
     * non-HAN script (the global F1 hash table was the bottleneck).  Under
     * v7's per-script tables, the same experiment correctly leaves other
     * scripts untouched, but the HAN gain itself was negligible (Cohen's d
     * moved 7.26 → 7.35) — the per-script HAN model is already near its
     * data-saturation point with ~18 MB of training data.  Override left
     * empty until a more decisive HAN-coverage experiment is designed.
     */
    public static final Map<String, Long> SCRIPT_BUDGET_OVERRIDES =
            Collections.emptyMap();

    // =======================================================================
    // Model train (TrainJunkModel)
    // =======================================================================

    /**
     * Drop per-script F1 bigrams whose per-pair occurrence count (within
     * that script's training data) is below this threshold.  Set to 3 on
     * evidence that singleton and doubleton pairs are overwhelmingly OCR
     * artifacts and proper-noun noise that inflate the clean-side score
     * distribution tail without contributing signal.
     *
     * <p>Set to 1 to disable the filter (every observed pair retained).
     */
    public static final int MIN_BIGRAM_COUNT = 3;

    /**
     * Target load factor for the per-script open-addressing F1 hash
     * table.  Table capacity is sized as the smallest power of two
     * larger than {@code keptPairs / loadFactor}, giving an average of
     * 1 / (1 - loadFactor) probes per lookup.  0.5 → ~2 probes; modestly
     * wasteful in space but very cheap to probe.
     */
    public static final double OA_LOAD_FACTOR = 0.5;

    /**
     * Bit width of each codepoint's dense index within a script's F1
     * table.  Each bigram is packed as {@code (idxA << KEY_INDEX_BITS) |
     * idxB}, so each side must fit in this many bits.  16 bits supports
     * up to 65535 distinct codepoints per script, which is comfortably
     * above the largest per-script count we have measured (HAN is the
     * worst case at ~15K kept codepoints).
     */
    public static final int KEY_INDEX_BITS = 16;

    private JunkDetectorTrainingConfig() {
        // No instances.
    }
}
