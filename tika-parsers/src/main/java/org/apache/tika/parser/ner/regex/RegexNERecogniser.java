/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright owlocationNameEntitieship.
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

package org.apache.tika.parser.ner.regex;

import org.apache.commons.io.IOUtils;
import org.apache.tika.parser.ner.NERecogniser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entity name recogniser using regex
 * An implementation of {@link NERecogniser}
 *
 * @since Nov. 7, 2015
 */
public class RegexNERecogniser implements NERecogniser {

    private static Logger LOG = LoggerFactory.getLogger(RegexNERecogniser.class);


    private static final String NER_REGEX_FILE = "ner-regex.txt";

    public Set<String> entityTypes = new HashSet<>();
    public Map<String, Pattern> patterns;
    private boolean available = false;

    private static RegexNERecogniser INSTANCE;

    public RegexNERecogniser(){
        this(RegexNERecogniser.class.getResourceAsStream(NER_REGEX_FILE));
    }

    public RegexNERecogniser(InputStream stream){
        try {
            patterns = new HashMap<>();
            List<String> lines = IOUtils.readLines(stream, StandardCharsets.UTF_8);
            IOUtils.closeQuietly(stream);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")){ //empty or comment
                    //skip
                    continue;
                }

                int delim = line.indexOf('=');
                if (delim < 0) { //delim not found
                    //skip
                    LOG.error("Skip : Invalid config : " + line);
                    continue;
                }
                String type = line.substring(0, delim).trim();
                String patternStr = line.substring(delim+1, line.length()).trim();
                patterns.put(type, Pattern.compile(patternStr));
                entityTypes.add(type);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        available = !entityTypes.isEmpty();
    }

    public synchronized static RegexNERecogniser getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RegexNERecogniser();
        }
        return INSTANCE;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Set<String> getEntityTypes() {
        return entityTypes;
    }

    /**
     * finds matching sub groups in text
     * @param text text containing interesting sub strings
     * @param pattern pattern to find sub strings
     * @return set of sub strings if any found, or null if none found
     */
    public Set<String> findMatches(String text, Pattern pattern){
        Set<String> results = null;
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            results = new HashSet<>();
            results.add(matcher.group(0));
            while (matcher.find()) {
                results.add(matcher.group(0));
            }
        }
        return results;
    }

    @Override
    public Map<String, Set<String>> recognise(String text) {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
            Set<String> names = findMatches(text, entry.getValue());
            if (names != null) {
                result.put(entry.getKey(), names);
            }
        }
        return result;
    }
}
