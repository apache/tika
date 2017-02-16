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

package org.apache.tika.eval.tokens;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommonTokenCountManager {

    private final static Charset COMMON_TOKENS_CHARSET = StandardCharsets.UTF_8;
    private final static Logger LOGGER = LoggerFactory.getLogger(CommonTokenCountManager.class);

    private final Path commonTokensDir;

    Map<String, Set<String>> commonTokenMap = new ConcurrentHashMap<>();
    Set<String> alreadyTriedToLoad = new HashSet<>();

    //if we have no model or if no langid is passed in
    //make this configurable
    String defaultLangCode = "en";

    public CommonTokenCountManager(Path commonTokensDir) throws IOException {
        this.commonTokensDir = commonTokensDir;
        tryToLoad(defaultLangCode);
        //if you couldn't load it, make sure to add an empty
        //set to prevent npes later
        Set<String> set = commonTokenMap.get(defaultLangCode);
        if (set == null) {
            LOGGER.warn("No common tokens for default language: '"+defaultLangCode+"'");
            commonTokenMap.put(defaultLangCode, new HashSet<String>());
        }
    }

    public CommonTokenResult countTokenOverlaps(String langCode,
                                                Map<String, MutableInt> tokens) throws IOException {
        String actualLangCode = getActualLangCode(langCode);
        int overlap = 0;
        Set<String> commonTokens = commonTokenMap.get(actualLangCode);
        for (Map.Entry<String, MutableInt> e : tokens.entrySet()) {
            if (commonTokens.contains(e.getKey())) {
                overlap += e.getValue().intValue();
            }
        }
        return new CommonTokenResult(actualLangCode, overlap);
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
        Set<String> set = commonTokenMap.get(langCode);
        if (set == null) {
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
        Path p = commonTokensDir.resolve(langCode);
        if (!Files.isRegularFile(p)) {
            LOGGER.warn("Couldn't find common tokens file for: '"+langCode+"': "+
            p.toAbsolutePath());
            alreadyTriedToLoad.add(langCode);
            return;
        }

        Set<String> set = commonTokenMap.get(langCode);
        if (set == null) {
            set = new HashSet<>();
            commonTokenMap.put(langCode, set);
        }
        try (BufferedReader reader = Files.newBufferedReader(p, COMMON_TOKENS_CHARSET)) {
            alreadyTriedToLoad.add(langCode);
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (line.startsWith("#")) {
                    line = reader.readLine();
                    continue;
                }
                //allow language models with, e.g. tab-delimited counts after the term
                String[] cols = line.split("\t");
                String t = cols[0].trim();
                if (t.length() > 0) {
                    set.add(t);
                }

                line = reader.readLine();
            }
        } catch (IOException e) {
            LOGGER.warn("IOException trying to read: '"+langCode+"'");
        }
    }

}
