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
import java.util.List;

/**
 * Every choice that defines a hashed junk model ({@link TrainHashedJunkModel}).  Change a value
 * here and commit it with the model it produced; the provenance file records them all.
 */
public final class HashedTrainingConfig {

    /** Buckets per head, as log2. */
    public static final int LOG2_SIZE = 21;

    /** Longest codepoint n-gram. */
    public static final int MAX_N = 3;

    /** Characters of a training text kept (pages are clipped, sentences are short anyway). */
    public static final int CLIP_CHARS = 6000;

    // ----------------------------------------------------------- Wikipedia sampling (train split)

    /** Sentences per (script, language). */
    public static final int WIKI_SENTENCES_PER_LANGUAGE = 1000;

    /** Concatenated ~{@link #WIKI_DOC_CHARS}-character documents per (script, language). */
    public static final int WIKI_DOCS_PER_LANGUAGE = 50;

    public static final int WIKI_DOC_CHARS = 2000;

    /** Shuffle seeds of the two Wikipedia samples: pairwise units, pointwise units. */
    public static final long WIKI_PAIR_SEED = 1;

    public static final long WIKI_POINT_SEED = 11;

    /**
     * Largest share of Wikipedia units whose language lookup may fail.  Above it the run stops:
     * the built data and the corpus no longer agree on normalization or line parsing.
     */
    public static final double MAX_MISSING_LANGUAGE = 0.01;

    /**
     * One Wikipedia unit in this many is also trained in upper case (both heads), so all-caps
     * headings read as clean text.  Wikipedia has almost none; case folding the features
     * instead would lose the mixed-case junk signal.  0 turns it off.
     */
    public static final int UPPER_CASE_MODULUS = 10;

    /**
     * A script counts as trained (its letters are "covered") when the Wikipedia training units
     * hold at least this many of its letters.  Counted, not taken from bucket names: Japanese
     * kana live in the HAN bucket but are their own Unicode scripts.
     */
    public static final int MIN_TRAINED_SCRIPT_LETTERS = 2000;

    /** Synthetic wrong decodes paired with each Wikipedia unit. */
    public static final int WIKI_WRONG_DECODES = 4;

    // ----------------------------------------------------------- pairwise head

    /** Repeats of each pair: gold pages under fixed decoders, chain candidates, re-encoded pages. */
    public static final int GOLD_PAIR_WEIGHT = 2;

    public static final int CHAIN_PAIR_WEIGHT = 4;

    public static final int REAL_DECISION_PAIR_WEIGHT = 1;

    /** One page in this many of the real-decision sample is held out to monitor the fit. */
    public static final int REAL_DECISION_SELECT_MODULUS = 10;

    public static final int PAIR_EPOCHS = 6;

    public static final double PAIR_LEARNING_RATE = 0.5;

    public static final double PAIR_L2 = 1e-7;

    public static final long PAIR_SEED = 42;

    /** Decoders every gold page is also read under, to make wrong-decode pairs. */
    public static final List<String> GOLD_DECODERS = Collections.unmodifiableList(List.of(
            "windows-1252", "windows-1250", "windows-1257", "windows-1254", "ISO-8859-2",
            "ISO-8859-15", "ISO-8859-16", "x-MacRoman", "windows-1251", "KOI8-R", "IBM866",
            "windows-1253", "ISO-8859-7", "windows-1256", "windows-1255", "x-windows-874",
            "GB18030", "Big5-HKSCS", "windows-31j", "x-eucJP-Open", "x-windows-949", "UTF-8"));

    // ----------------------------------------------------------- pointwise head

    public static final int POINT_EPOCHS = 5;

    public static final double POINT_LEARNING_RATE = 0.5;

    public static final double POINT_L2 = 1e-6;

    public static final long POINT_SEED = 7;

    /**
     * Real damage for the pointwise head: each damage span of a fit-half gold page, padded by
     * {@link #DAMAGED_SPAN_PAD} code points, is a junk example and the rest of the page a clean one,
     * repeated this many times.  0 turns it off.  Only spans whose basis is in
     * {@link #DAMAGED_SPAN_BASES}.
     */
    public static final int DAMAGED_SPAN_WEIGHT = 10;

    public static final int DAMAGED_SPAN_PAD = 100;

    /** {@code rule}: located mechanically (U+FFFD / control runs); {@code proposed} mojibake spans stay out. */
    public static final List<String> DAMAGED_SPAN_BASES = Collections.unmodifiableList(List.of("rule", "adjudicated"));

    /** Shortest gold page, in codepoints, used as a clean example. */
    public static final int POINT_MIN_PAGE_CODEPOINTS = 50;

    // ----------------------------------------------------------- quantization and deferral

    /** int8 scale = this quantile of |w| / 127; larger weights are clipped. */
    public static final double QUANT_QUANTILE = 0.9999;

    /** Resolution of the reported deferral curve. */
    public static final double DEFER_STEP = 0.25;

    /** The threshold grows one band at a time, never past the max. */
    public static final double DEFER_BAND = 1.0;

    public static final double DEFER_MAX = 10.0;

    /** A band is kept only if its deferrals fix at least this many pages per page they break. */
    public static final double DEFER_MIN_RATIO = 2.0;

    /**
     * ... and only if, at this normal quantile, the Wilson lower bound of the share of its pages
     * that deferring fixes is above one half: a band of a dozen pages cannot move the threshold.
     */
    public static final double DEFER_CONFIDENCE_Z = 1.96;

    private HashedTrainingConfig() {
    }
}
