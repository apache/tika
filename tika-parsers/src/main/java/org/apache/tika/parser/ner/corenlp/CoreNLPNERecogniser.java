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
package org.apache.tika.parser.ner.corenlp;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.tika.io.IOUtils;
import org.apache.tika.parser.ner.NERecogniser;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  This class offers an implementation of {@link NERecogniser} based on
 *  CRF classifiers from Stanford CoreNLP. This NER requires additional setup,
 *  due to runtime binding to Stanford CoreNLP.
 *  See <a href="http://wiki.apache.org/tika/TikaAndNER#CoreNLP">
 *      Tika NER Wiki</a> for configuring this recogniser.
 *  @see NERecogniser
 *
 */
public class CoreNLPNERecogniser implements NERecogniser {

    private static final Logger LOG = LoggerFactory.getLogger(CoreNLPNERecogniser.class);

    //default model paths
    public static final String NER_3CLASS_MODEL = "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz";
    public static final String NER_4CLASS_MODEL = "edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz";
    public static final String NER_7CLASS_MODEL = "edu/stanford/nlp/models/ner/english.muc.7class.distsim.crf.ser.gz";
    /**
     * default Model path
     */
    public static final String DEFAULT_MODEL_PATH = NER_7CLASS_MODEL;
    public static final String MODEL_PROP_NAME = "ner.corenlp.model";

    public static final Set<String> ENTITY_TYPES = new HashSet<String>(){{
        add(PERSON);
        add(TIME);
        add(LOCATION);
        add(ORGANIZATION);
        add(MONEY);
        add(PERCENT);
        add(DATE);
    }};
    private static final String CLASSIFIER_CLASS_NAME = "edu.stanford.nlp.ie.crf.CRFClassifier";

    private boolean available = false;
    private Field firstField;
    private Field secondField;
    private Field thirdField;
    private Object classifierInstance;
    private Method classifyMethod;

    public CoreNLPNERecogniser(){
        this(System.getProperty(MODEL_PROP_NAME, DEFAULT_MODEL_PATH));
    }

    /**
     * Creates a NERecogniser by loading model from given path
     * @param modelPath path to NER model file
     */
    public CoreNLPNERecogniser(String modelPath) {
        try {
            Properties props = new Properties();
            Class<?> classifierClass = Class.forName(CLASSIFIER_CLASS_NAME);
            Method loadMethod = classifierClass.getMethod("getClassifier", String.class, Properties.class);
            classifierInstance = loadMethod.invoke(classifierClass, modelPath, props);
            classifyMethod = classifierClass.getMethod("classifyToCharacterOffsets", String.class);

            //these fields are for accessing result
            Class<?> tripleClass = Class.forName("edu.stanford.nlp.util.Triple");
            this.firstField = tripleClass.getField("first");
            this.secondField = tripleClass.getField("second");
            this.thirdField = tripleClass.getField("third");
            this.available = true;
        } catch (Exception e) {
            LOG.warn("{} while trying to load the model from {}", e.getMessage(), modelPath);
        }
        LOG.info("Available for service ? {}", available);
    }

    /**
     *
     * @return {@code true} if model was available, valid and was able to initialise the classifier.
     * returns {@code false} when this recogniser is not available for service.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Gets set of entity types recognised by this recogniser
     * @return set of entity classes/types
     */
    public Set<String> getEntityTypes() {
        return ENTITY_TYPES;
    }

    /**
     * recognises names of entities in the text
     * @param text text which possibly contains names
     * @return map of entity type -> set of names
     */
    public Map<String, Set<String>> recognise(String text) {
        Map<String, Set<String>> names = new HashMap<>();
        try {
            Object result = classifyMethod.invoke(classifierInstance, text);
            List entries = (List) result;
            for (Object entry : entries) {
                String entityType = (String) firstField.get(entry);
                if (!names.containsKey(entityType)) {
                    names.put(entityType, new HashSet<String>());
                }
                Integer start = (Integer) secondField.get(entry);
                Integer end = (Integer) thirdField.get(entry);
                String name = text.substring(start, end);
                //Clean repeating spaces, replace line breaks and tabs with single space
                name = name.trim().replaceAll("(\\s\\s+)|\n|\t", " ");
                if (!name.isEmpty()) {
                    names.get(entityType).add(name);
                }
            }

        } catch (Exception e) {
            LOG.debug(e.getMessage(), e);
        }
        return names;
    }

    public static void main(String[] args) throws IOException, JSONException {
        if (args.length != 1) {
            System.err.println("Error: Invalid Args");
            System.err.println("This tool finds names inside text");
            System.err.println("Usage: <path/to/text/file>");
            return;
        }

        try (FileInputStream stream = new FileInputStream(args[0])) {
            String text = IOUtils.toString(stream);
            CoreNLPNERecogniser ner = new CoreNLPNERecogniser();
            Map<String, Set<String>> names = ner.recognise(text);
            JSONObject jNames = new JSONObject(names);
            System.out.println(jNames.toString(2));
        }
    }
}
