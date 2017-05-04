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
package org.apache.tika.parser.ner.mitie;


import org.apache.tika.parser.ner.NERecogniser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 *  This class offers an implementation of {@link NERecogniser} based on
 *  trained models using state-of-the-art information extraction tools. This NER requires additional setup,
 *  due to runtime binding to MIT Information Extraction.
 *  See <a href="http://wiki.apache.org/tika/TikaAndMITIE">
 *      Tika MITIE Wiki</a> for configuring this recogniser.
 *  @see NERecogniser
 *
 */
public class MITIENERecogniser implements NERecogniser {
    private static final Logger LOG = LoggerFactory.getLogger(MITIENERecogniser.class);

    public static final String MODEL_PROP_NAME = "ner.mitie.model";

    public static final Set<String> ENTITY_TYPES = new HashSet<String>(){{
        add(PERSON);
        add(LOCATION);
        add(ORGANIZATION);
        add("MISC");
    }};

    private static final String NamedEntityExtractor_Class = "edu.mit.ll.mitie.NamedEntityExtractor";
    private boolean available = false;
    private Object extractorInstance;

    public MITIENERecogniser(){
        this(System.getProperty(MODEL_PROP_NAME));
    }

    /**
     * Creates a NERecogniser by loading model from given path
     * @param modelPath path to NER model file
     */
    public MITIENERecogniser(String modelPath) {
        try {
            if(!(new File(modelPath)).exists()) {
                LOG.warn("{} does not exist", modelPath);
            }else {
                Class<?> namedEntityExtractorClass = Class.forName(NamedEntityExtractor_Class);
                extractorInstance = namedEntityExtractorClass.getDeclaredConstructor(new Class[]{String.class}).newInstance(modelPath);
                this.available = true;
            }
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

            Class<?> stringVectorClass = Class.forName("edu.mit.ll.mitie.StringVector");
            Class<?> entityMentionVectorClass = Class.forName("edu.mit.ll.mitie.EntityMentionVector");
            Class<?> entityMentionClass = Class.forName("edu.mit.ll.mitie.EntityMention");
            Object entityMentionObject = null;
            Class<?> globalClass = Class.forName("edu.mit.ll.mitie.global");
            Object stringVectorObject = extractorInstance.getClass().getMethod("getPossibleNerTags").invoke(extractorInstance);
            long size = (Long)stringVectorClass.getMethod("size").invoke(stringVectorObject);
            ArrayList<String> possibleTags = new ArrayList<>();
            for(long i=0; i<size; i++){
                String t = (String)stringVectorClass.getMethod("get", Integer.TYPE).invoke(stringVectorObject,(int)i);
                possibleTags.add(t);
            }
            Method tokenize = globalClass.getMethod("tokenize", String.class);
            stringVectorObject = tokenize.invoke(globalClass,text );

            ArrayList<String> stringVector = new ArrayList<>();
            size = (Long)stringVectorClass.getMethod("size").invoke(stringVectorObject);
            for(long i=0; i<size; i++){
                String t = (String)stringVectorClass.getMethod("get", Integer.TYPE).invoke(stringVectorObject,(int)i);
                stringVector.add(t);
            }
            Method extractEntities = extractorInstance.getClass().getMethod("extractEntities", stringVectorClass);
            Object entities = extractEntities.invoke(extractorInstance, stringVectorObject);
            size = (Long)entityMentionVectorClass.getMethod("size").invoke(entities);
            for(long i=0; i<size; i++){
                entityMentionObject = entityMentionVectorClass.getMethod("get", Integer.TYPE).invoke(entities, (int)i);
                int tag_index = (Integer)entityMentionClass.getMethod("getTag").invoke(entityMentionObject);
                String tag = possibleTags.get(tag_index);
                Set<String> x = new HashSet<String>();
                if(names.containsKey(tag)) {
                    x = names.get(tag);
                }
                else {
                    names.put(tag,x);
                }
                int start = (Integer)entityMentionClass.getMethod("getStart").invoke(entityMentionObject);
                int end = (Integer)entityMentionClass.getMethod("getEnd").invoke(entityMentionObject);
                String match = "";
                for(;start<end; start++) {
                    match += stringVector.get(start) + " ";
                }
                x.add(match.trim());
            }

        } catch (Exception e) {

            LOG.debug(e.getMessage(), e);
        }
        return names;
    }

}
