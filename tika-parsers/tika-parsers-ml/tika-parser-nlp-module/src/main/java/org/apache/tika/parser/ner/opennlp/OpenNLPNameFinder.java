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

package org.apache.tika.parser.ner.opennlp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.Span;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.parser.ner.NERecogniser;

/**
 * An implementation of {@link NERecogniser} that finds names in text using Open NLP Model.
 * This implementation works with only one entity type. For chain this name finder instances,
 * see {@link OpenNLPNERecogniser}
 */
public class OpenNLPNameFinder implements NERecogniser {

    private static final Logger LOG = LoggerFactory.getLogger(OpenNLPNameFinder.class);
    private final String nameType;
    private final Set<String> nameTypes;
    private NameFinderME nameFinder;
    private boolean available;

    /**
     * Creates OpenNLP name finder
     *
     * @param nameType     the entity type recognised by the given NER model
     * @param nerModelPath path to ner model
     */
    public OpenNLPNameFinder(String nameType, String nerModelPath) {
        this.nameTypes = Collections.singleton(nameType);
        this.nameType = nameType;
        InputStream nerModelStream = getClass().getClassLoader().getResourceAsStream(nerModelPath);
        try {
            if (nerModelStream != null) {
                TokenNameFinderModel model = new TokenNameFinderModel(nerModelStream);
                this.nameFinder = new NameFinderME(model);
                this.available = true;
            } else {
                LOG.warn("Couldn't find model from {} using class loader", nerModelPath);
            }
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(nerModelStream);
        }
        LOG.info("{} NER : Available for service ? {}", nameType, available);
    }

    public static String[] tokenize(String text) {
        //NOTE: replace this with a NLP tokenizer tool
        //clean + split
        return text.trim().replaceAll("(\\s\\s+)", " ").split("\\s");
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Set<String> getEntityTypes() {
        return nameTypes;
    }

    @Override
    public synchronized Map<String, Set<String>> recognise(String text) {
        String[] tokens = tokenize(text);
        return findNames(tokens);
    }

    /**
     * finds names from given array of tokens
     *
     * @param tokens the tokens array
     * @return map of EntityType -&gt; set of entity names
     */
    public Map<String, Set<String>> findNames(String[] tokens) {
        Span[] nameSpans = nameFinder.find(tokens);
        String[] names = Span.spansToStrings(nameSpans, tokens);
        Map<String, Set<String>> result = new HashMap<>();
        if (names != null && names.length > 0) {
            result.put(nameType, new HashSet<>(Arrays.asList(names)));
        }
        nameFinder.clearAdaptiveData();
        return result;
    }
}
