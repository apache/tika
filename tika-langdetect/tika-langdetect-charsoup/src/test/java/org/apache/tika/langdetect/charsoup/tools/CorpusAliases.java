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
package org.apache.tika.langdetect.charsoup.tools;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Corpus directory name → canonical language code mappings shared by all
 * training tools ({@link PrepareCorpus}, {@link TrainGenerativeLanguageModel},
 * and the common-tokens builder).
 *
 * <p>Some Wikipedia corpus dumps are named with an alternate ISO 639 code
 * (e.g. {@code swa} for Swahili macro-language, ISO 639-1 {@code sw}) while
 * the discriminative model uses the more specific ISO 639-3 code ({@code swh}).
 * These mappings ensure that every tool reads from the correct corpus directory
 * and registers the language under the same canonical code that appears in the
 * trained model.
 *
 * <p>When the corpus directory uses an alias that maps to a canonical code,
 * the data is read from the alias directory but trained/registered under the
 * canonical code.
 */
public final class CorpusAliases {

    /**
     * Maps corpus directory names to the canonical language code used by the
     * discriminative model.  Keys are the names that actually appear as
     * directory entries in the corpus; values are the codes stored in the
     * trained model's label array.
     *
     * <p>Only entries where {@code key != value} are listed — if a language
     * has the same directory name and model label, no entry is needed.
     *
     * <p><b>Sync note:</b> {@code CommonTokenGenerator} in tika-eval-core
     * maintains a copy of this map. Since test-scope classes cannot be shared
     * across modules, both copies must be kept identical by hand.
     */
    public static final Map<String, String> LANG_MERGE_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("azj", "aze");
        m.put("ekk", "est");
        m.put("pes", "fas");
        m.put("zsm", "msa");
        m.put("nor", "nob");
        m.put("plt", "mlg");
        m.put("cmn", "zho");
        m.put("lvs", "lav");
        m.put("gug", "grn");
        m.put("quz", "que");
        m.put("swa", "swh");
        m.put("yid", "ydd");
        m.put("zza", "diq");
        LANG_MERGE_MAP = Collections.unmodifiableMap(m);
    }

    /** Returns the canonical language code for a corpus directory name. */
    public static String canonical(String dirName) {
        return LANG_MERGE_MAP.getOrDefault(dirName, dirName);
    }

    private CorpusAliases() {}
}
