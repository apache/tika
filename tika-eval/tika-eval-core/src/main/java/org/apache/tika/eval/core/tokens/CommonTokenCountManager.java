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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.bloomfilter.BloomFilter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lazily loads the per-language "common tokens" Bloom filters (see {@link CommonTokensBloom})
 * from the classpath ({@code /common_tokens_bloom/<langCode>}) or from a configured directory.
 */
public class CommonTokenCountManager {
    static final String COMMON_TOKENS_BLOOM_PATH = "/common_tokens_bloom/";

    private static final Logger LOG = LoggerFactory.getLogger(CommonTokenCountManager.class);

    private final Path commonTokensDir;
    //if we have no model or if no langid is passed in
    //make this configurable
    private final String defaultLangCode;
    Map<String, LangModel> commonTokenMap = new ConcurrentHashMap<>();
    Set<String> alreadyTriedToLoad = new HashSet<>();

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

    public Set<String> getLangs() {
        return commonTokenMap.keySet();
    }

    /**
     * @param lang requested language code
     * @return pair of the actual language code used and the common-tokens model for it
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
        if (commonTokenMap.get(langCode) != null) {
            return langCode;
        }

        return defaultLangCode;
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
                is = this.getClass().getResourceAsStream(COMMON_TOKENS_BLOOM_PATH + langCode);
            } else {
                is = Files.newInputStream(p);
            }

            alreadyTriedToLoad.add(langCode);

            if (is == null) {
                String path = (p == null) ?
                        "resource on class path: " + COMMON_TOKENS_BLOOM_PATH + langCode :
                        p.toAbsolutePath().toString();
                LOG.warn("Couldn't find common tokens file for: '" + langCode + "' tried here: " +
                        path);
                return;
            }

            BloomFilter filter = CommonTokensBloom.read(is);
            commonTokenMap.put(langCode, new LangModel(filter));
        } catch (IOException e) {
            LOG.warn("IOException trying to read: '" + langCode + "'", e);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

}
