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
package org.apache.tika.langdetect.opennlp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.util.normalizer.CharSequenceNormalizer;
import opennlp.tools.util.normalizer.EmojiCharSequenceNormalizer;
import opennlp.tools.util.normalizer.NumberCharSequenceNormalizer;
import opennlp.tools.util.normalizer.ShrinkCharSequenceNormalizer;
import opennlp.tools.util.normalizer.TwitterCharSequenceNormalizer;

import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

/**
 * <p>
 * This is based on OpenNLP's language detector.  However,
 * we've built our own ProbingLanguageDetector and our own language
 * models.
 * </p>
 * To build our model, we followed OpenNLP's lead by using the
 * (<a href="https://wortschatz.uni-leipzig.de/en/download">Leipzig corpus</a>)
 * as gathered and preprocessed
 * (
 * <a href="https://svn.apache.org/repos/bigdata/opennlp/trunk/leipzig/">big-data corpus</a>
 * ). We removed azj, plt, sun
 * and zsm because our models couldn't sufficiently well distinguish
 * them from related languages. We removed cmn in favor of the
 * finer-grained zho-trad and zho-simp.
 * <p>
 * We then added the following languages from <a href="http://data.statmt.org/cc-100/">cc-100</a>:
 * ben-rom (Bengali Romanized), ful, gla, gug, hau, hin-rom, ibo, ful, linm
 * mya-zaw, nso, orm, quz, roh, srd, ssw, tam-rom, tel-rom, tsn, urd-rom,
 * wol, yor.
 * <p>
 * We ran our own train/devtest/test code because OpenNLPs required
 * more sentences/data than were available for some languages.
 * <p>
 * Please open an issue on our JIRA if we made mistakes and/or had
 * misunderstandings in our design choices or if you need to have other
 * languages added.
 * <p>
 * Citations for the cc-100 corpus:
 * <p>
 * Unsupervised Cross-lingual Representation Learning at Scale, Alexis Conneau,
 * Kartikay Khandelwal, Naman Goyal, Vishrav Chaudhary, Guillaume Wenzek,
 * Francisco Guzmán, Edouard Grave, Myle Ott, Luke Zettlemoyer, Veselin Stoyanov,
 * Proceedings of the 58th Annual Meeting of the Association for Computational
 * Linguistics (ACL), p. 8440-8451, July 2020, pdf, bib.
 * <p>
 * CCNet: Extracting High Quality Monolingual Datasets from Web Crawl Data,
 * Guillaume Wenzek, Marie-Anne Lachaux, Alexis Conneau, Vishrav Chaudhary,
 * Francisco Guzmán, Armand Joulin, Edouard Grave, Proceedings of the 12th
 * Language Resources and Evaluation Conference (LREC), p. 4003-4012,
 * May 2020, pdf, bib.
 */
public class OpenNLPDetector extends LanguageDetector {

    static LanguageDetectorModel LANG_MODEL;

    static {
        try {
            loadBuiltInModels();
        } catch (IOException e) {
            throw new RuntimeException("Can't find built-in language models");
        }
    }

    private final ProbingLanguageDetector detector =
            new ProbingLanguageDetector(LANG_MODEL, getNormalizers());
    private final StringBuilder buffer = new StringBuilder();

    public OpenNLPDetector() {

    }

    static void loadBuiltInModels() throws IOException {
        try (InputStream is = OpenNLPDetector.class
                .getResourceAsStream("/opennlp-langdetect-20210413.bin")) {
            LANG_MODEL = new LanguageDetectorModel(is);
        }
    }

    private static CharSequenceNormalizer[] getNormalizers() {
        return new CharSequenceNormalizer[]{TikaUrlCharSequenceNormalizer.getInstance(),
                AlphaIdeographSequenceNormalizer.getInstance(),
                EmojiCharSequenceNormalizer.getInstance(),
                TwitterCharSequenceNormalizer.getInstance(),
                NumberCharSequenceNormalizer.getInstance(),
                ShrinkCharSequenceNormalizer.getInstance()};
    }

    private static LanguageConfidence getConfidence(double confidence) {
        //COMPLETELY heuristic
        if (confidence > 0.9) {
            return LanguageConfidence.HIGH;
        } else if (confidence > 0.85) {
            return LanguageConfidence.MEDIUM;
        } else if (confidence > 0.20) {
            return LanguageConfidence.LOW;
        }
        return LanguageConfidence.NONE;
    }

    /**
     * No-op. Models are loaded statically.
     *
     * @return
     * @throws IOException
     */
    @Override
    public LanguageDetector loadModels() throws IOException {
        return new OpenNLPDetector();
    }

    /**
     * NOT SUPPORTED. Throws {@link UnsupportedOperationException}
     *
     * @param languages list of target languages.
     * @return
     * @throws IOException
     */
    @Override
    public LanguageDetector loadModels(Set<String> languages) throws IOException {
        throw new UnsupportedOperationException(
                "This lang detector doesn't allow subsetting models");
    }

    @Override
    public boolean hasModel(String language) {
        for (String lang : detector.getSupportedLanguages()) {
            if (language.equals(lang)) {
                return true;
            }
        }
        return false;
    }

    /**
     * NOT YET SUPPORTED. Throws {@link UnsupportedOperationException}
     *
     * @param languageProbabilities Map from language to probability
     * @return
     * @throws IOException
     */
    @Override
    public LanguageDetector setPriors(Map<String, Float> languageProbabilities) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        buffer.setLength(0);
    }

    /**
     * This will buffer up to {@link #setMaxLength(int)} and then
     * ignore the rest of the text.
     *
     * @param cbuf Character buffer
     * @param off  Offset into cbuf to first character in the run of text
     * @param len  Number of characters in the run of text.
     */
    @Override
    public void addText(char[] cbuf, int off, int len) {
        int buffLen = buffer.length();
        int newLen = Math.min(len, detector.getMaxLength() - buffLen);
        if (len <= 0) {
            return;
        }
        buffer.append(cbuf, off, newLen);
    }

    @Override
    public List<LanguageResult> detectAll() {
        Language[] langs = detector.predictLanguages(buffer.toString());
        List<LanguageResult> results = new ArrayList<>();
        for (Language lang : langs) {
            LanguageResult r =
                    new LanguageResult(lang.getLang(), getConfidence(lang.getConfidence()),
                            (float) lang.getConfidence());
            results.add(r);
        }
        return results;
    }

    public void setMaxLength(int maxLength) {
        detector.setMaxLength(maxLength);
    }

    public String[] getSupportedLanguages() {
        return detector.getSupportedLanguages();
    }

    private static class TikaUrlCharSequenceNormalizer implements CharSequenceNormalizer {
        //use this custom copy/paste of opennlp to avoid long, long hang with mail_regex
        //TIKA-2777
        private static final Pattern URL_REGEX =
                Pattern.compile("https?://[-_.?&~;+=/#0-9A-Za-z]{10,10000}");
        private static final Pattern MAIL_REGEX =
                Pattern.compile("[-_.0-9A-Za-z]{1,100}@[-_0-9A-Za-z]{1,100}[-_.0-9A-Za-z]{1,100}");
        private static final TikaUrlCharSequenceNormalizer INSTANCE =
                new TikaUrlCharSequenceNormalizer();

        private TikaUrlCharSequenceNormalizer() {
        }

        public static TikaUrlCharSequenceNormalizer getInstance() {
            return INSTANCE;
        }

        @Override
        public CharSequence normalize(CharSequence charSequence) {
            String modified = URL_REGEX.matcher(charSequence).replaceAll(" ");
            return MAIL_REGEX.matcher(modified).replaceAll(" ");
        }
    }

    private static class AlphaIdeographSequenceNormalizer implements CharSequenceNormalizer {
        private static final Pattern REGEX =
                Pattern.compile("[^\\p{IsAlphabetic}\\p{IsIdeographic}]+");
        private static final AlphaIdeographSequenceNormalizer INSTANCE =
                new AlphaIdeographSequenceNormalizer();

        private AlphaIdeographSequenceNormalizer() {
        }

        public static AlphaIdeographSequenceNormalizer getInstance() {
            return INSTANCE;
        }

        @Override
        public CharSequence normalize(CharSequence charSequence) {
            return REGEX.matcher(charSequence).replaceAll(" ");
        }
    }
}
