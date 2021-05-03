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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.parser.ner.NERecogniser;


/**
 * This implementation of {@link NERecogniser} chains an array of
 * {@link OpenNLPNameFinder}s for which NER models are
 * available in classpath.
 * <p>
 * The following models are scanned during initialization via class loader.:
 *
 * <table>
 *     <tr>
 *         <th>Entity Type</th><th>Path</th>
 *     </tr>
 *     <tr>
 *         <td>{@value PERSON}</td><td> {@value PERSON_FILE}</td>
 *     </tr>
 *     <tr>
 *         <td>{@value LOCATION}</td><td>{@value LOCATION_FILE}</td>
 *     </tr>
 *     <tr>
 *         <td>{@value ORGANIZATION}</td><td>{@value ORGANIZATION_FILE}</td>
 *     </tr>
 *     <tr>
 *         <td>{@value TIME}</td><td>{@value TIME_FILE}</td>
 *     </tr>
 *     <tr>
 *         <td>{@value DATE}</td><td>{@value DATE_FILE}</td>
 *     </tr>
 *     <tr>
 *         <td>{@value PERCENT}</td><td>{@value PERCENT_FILE}</td>
 *     </tr>
 *     <tr>
 *         <td>{@value MONEY}</td><td>{@value MONEY_FILE}</td>
 *     </tr>
 * </table>
 *
 * @see org.apache.tika.parser.ner.NamedEntityParser#DEFAULT_NER_IMPL
 */
public class OpenNLPNERecogniser implements NERecogniser {

    public static final String MODELS_DIR =
            OpenNLPNERecogniser.class.getPackage().getName().replace(".", "/");
    public static final String PERSON_FILE = "ner-person.bin";
    public static final String LOCATION_FILE = "ner-location.bin";
    public static final String ORGANIZATION_FILE = "ner-organization.bin";
    public static final String TIME_FILE = "ner-time.bin";
    public static final String DATE_FILE = "ner-date.bin";
    public static final String PERCENT_FILE = "ner-percentage.bin";
    public static final String MONEY_FILE = "ner-money.bin";


    //Default (English) Models for the common 7 classes of named types
    public static final String NER_PERSON_MODEL = MODELS_DIR + "/" + PERSON_FILE;
    public static final String NER_LOCATION_MODEL = MODELS_DIR + "/" + LOCATION_FILE;
    public static final String NER_ORGANIZATION_MODEL = MODELS_DIR + "/" + ORGANIZATION_FILE;
    public static final String NER_TIME_MODEL = MODELS_DIR + "/" + TIME_FILE;
    public static final String NER_DATE_MODEL = MODELS_DIR + "/" + DATE_FILE;
    public static final String NER_PERCENT_MODEL = MODELS_DIR + "/" + PERCENT_FILE;
    public static final String NER_MONEY_MODEL = MODELS_DIR + "/" + MONEY_FILE;

    public static final Map<String, String> DEFAULT_MODELS = new HashMap<String, String>() {
        {
            put(PERSON, NER_PERSON_MODEL);
            put(LOCATION, NER_LOCATION_MODEL);
            put(ORGANIZATION, NER_ORGANIZATION_MODEL);
            put(TIME, NER_TIME_MODEL);
            put(DATE, NER_DATE_MODEL);
            put(PERCENT, NER_PERCENT_MODEL);
            put(MONEY, NER_MONEY_MODEL);
        }
        };

    private Set<String> entityTypes;
    private List<OpenNLPNameFinder> nameFinders;
    private boolean available;

    /**
     * Creates a default chain of Name finders using default OpenNLP recognizers
     */
    public OpenNLPNERecogniser() {
        this(DEFAULT_MODELS);
    }

    /**
     * Creates a chain of Named Entity recognisers
     *
     * @param models map of entityType -&gt; model path
     *               NOTE: the model path should be known to class loader.
     */
    public OpenNLPNERecogniser(Map<String, String> models) {
        this.nameFinders = new ArrayList<>();
        this.entityTypes = new HashSet<>();
        for (Map.Entry<String, String> entry : models.entrySet()) {
            OpenNLPNameFinder finder = new OpenNLPNameFinder(entry.getKey(), entry.getValue());
            if (finder.isAvailable()) {
                this.nameFinders.add(finder);
                this.entityTypes.add(entry.getKey());
            }
        }
        this.entityTypes = Collections.unmodifiableSet(this.entityTypes);
        this.available = nameFinders.size() > 0; //at least one finder is present
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Set<String> getEntityTypes() {
        return entityTypes;
    }

    @Override
    public Map<String, Set<String>> recognise(String text) {
        String[] tokens = OpenNLPNameFinder.tokenize(text);
        Map<String, Set<String>> names = new HashMap<>();
        for (OpenNLPNameFinder finder : nameFinders) {
            names.putAll(finder.findNames(tokens));
        }
        return names;
    }
}
