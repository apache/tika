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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommonTokenCountManager {
    private static final Logger LOG = LoggerFactory.getLogger(CommonTokenCountManager.class);

    private static final Charset COMMON_TOKENS_CHARSET = StandardCharsets.UTF_8;
    private static final String TERM_FREQS = "#SUM_TERM_FREQS\t";
    private final Path commonTokensDir;
    //if we have no model or if no langid is passed in
    //make this configurable
    private final String defaultLangCode;
    Map<String, LangModel> commonTokenMap = new ConcurrentHashMap<>();
    Set<String> alreadyTriedToLoad = new HashSet<>();
    private Matcher digitsMatcher = Pattern.compile("(\\d+)").matcher("");

    public CommonTokenCountManager() {
        this(null, null);
    }

    public CommonTokenCountManager(Path commonTokensDir, String defaultLangCode) {
        if (defaultLangCode == null) {
            defaultLangCode = "";
        }
        this.defaultLangCode = defaultLangCode;
        this.commonTokensDir = commonTokensDir;
        if (!"".equals(defaultLangCode)) {
            tryToLoad(defaultLangCode);
            //if you couldn't load it, make sure to add an empty
            //set to prevent npes later
            LangModel langModel = commonTokenMap.get(defaultLangCode);
            if (langModel == null) {
                LOG.warn("No common tokens for default language: '" + defaultLangCode + "'");
                commonTokenMap.put(defaultLangCode, LangModel.EMPTY_MODEL);
            }
        } else {
            commonTokenMap.put(defaultLangCode, LangModel.EMPTY_MODEL);

        }
    }

    @Deprecated
    /**
     * @deprecated use {@link eval.textstats.CommonTokens} instead
     */ public CommonTokenResult countTokenOverlaps(String langCode, Map<String, MutableInt> tokens)
            throws IOException {
        String actualLangCode = getActualLangCode(langCode);
        int numUniqueCommonTokens = 0;
        int numCommonTokens = 0;
        int numUniqueAlphabeticTokens = 0;
        int numAlphabeticTokens = 0;
        LangModel model = commonTokenMap.get(actualLangCode);
        for (Map.Entry<String, MutableInt> e : tokens.entrySet()) {
            String token = e.getKey();
            int count = e.getValue().intValue();
            if (AlphaIdeographFilterFactory.isAlphabetic(token.toCharArray(), token.length())) {
                numAlphabeticTokens += count;
                numUniqueAlphabeticTokens++;
            }
            if (model.contains(token)) {
                numCommonTokens += count;
                numUniqueCommonTokens++;
            }

        }
        return new CommonTokenResult(actualLangCode, numUniqueCommonTokens, numCommonTokens,
                numUniqueAlphabeticTokens, numAlphabeticTokens);
    }


    public Set<String> getTokens(String lang) {
        return Collections.unmodifiableSet(
                new HashSet(commonTokenMap.get(getActualLangCode(lang)).getTokens()));
    }

    public Set<String> getLangs() {
        return commonTokenMap.keySet();
    }

    /**
     * @param lang
     * @return pair of actual language code used and a set of common
     * tokens for that language
     */
    public Pair<String, LangModel> getLangTokens(String lang) {
        String actualLangCode = getActualLangCode(lang);
        return Pair.of(actualLangCode, commonTokenMap.get(actualLangCode));
    }

    //return langcode for lang that you are actually using
    //lazily load the appropriate model
    private String getActualLangCode(String langCode) {
        if (langCode == null || "".equals(langCode)) {
            return defaultLangCode;
        }
        if (commonTokenMap.containsKey(langCode)) {
            return langCode;
        }
        tryToLoad(langCode);
        LangModel model = commonTokenMap.get(langCode);
        if (model == null) {
            return defaultLangCode;
        }
        return langCode;

    }

    public void close() throws IOException {
        commonTokenMap.clear();
    }

    private synchronized void tryToLoad(String langCode) {
        if (alreadyTriedToLoad.contains(langCode)) {
            return;
        }
        //check once more now that we're in a
        //synchronized block
        if (commonTokenMap.get(langCode) != null) {
            return;
        }
        InputStream is = null;
        Path p = null;
        if (commonTokensDir != null) {
            p = commonTokensDir.resolve(langCode);
        }

        try {
            if (p == null || !Files.isRegularFile(p)) {
                is = this.getClass().getResourceAsStream("/common_tokens/" + langCode);
            } else {
                is = Files.newInputStream(p);
            }


            if (is == null) {
                String path = (p == null) ? "resource on class path: /common_tokens/" + langCode :
                        p.toAbsolutePath().toString();
                LOG.warn("Couldn't find common tokens file for: '" + langCode + "' tried here: " +
                        path);
                alreadyTriedToLoad.add(langCode);
                return;
            }

            LangModel model = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, COMMON_TOKENS_CHARSET))) {
                alreadyTriedToLoad.add(langCode);
                String line = reader.readLine();
                while (line != null) {
                    line = line.trim();
                    if (line.startsWith("#")) {
                        if (line.startsWith(TERM_FREQS)) {
                            digitsMatcher.reset(line);
                            if (digitsMatcher.find()) {
                                model = new LangModel(Long.parseLong(digitsMatcher.group(1)));
                            }
                        }
                        line = reader.readLine();
                        continue;
                    }
                    //allow language models with, e.g. tab-delimited counts after the term
                    String[] cols = line.split("\t");
                    String t = cols[0].trim();
                    if (t.length() > 0 && cols.length > 2) {
                        if (model == null) {
                            throw new IllegalArgumentException(
                                    "Common tokens file must have included comment line " +
                                            " with " + TERM_FREQS);
                        }
                        //document frequency
                        String df = cols[1];
                        //token frequency
                        long tf = Long.parseLong(cols[2]);
                        model.add(t, tf);
                    }

                    line = reader.readLine();
                }
            }
            commonTokenMap.put(langCode, model);
        } catch (IOException e) {
            LOG.warn("IOException trying to read: '" + langCode + "'");
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

}
