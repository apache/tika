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

package org.apache.tika.parser.geo;


import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;

public class NameEntityExtractor {
    ArrayList<String> locationNameEntities;
    String bestNameEntity;
    private HashMap<String, Integer> tf;
    private final NameFinderME nameFinder;

    public NameEntityExtractor(NameFinderME nameFinder) throws IOException {
        this.locationNameEntities = new ArrayList<String>();
        this.bestNameEntity = null;
        this.nameFinder = nameFinder;
        this.tf = new HashMap<String, Integer>();
    }

    /*
     * Use OpenNLP to extract location names that's appearing in the steam.
     * OpenNLP's default Name Finder accuracy is not very good, please refer to
     * its documentation.
     * 
     * @param stream stream that passed from this.parse()
     */
    public void getAllNameEntitiesfromInput(InputStream stream) throws IOException {
        String[] in = IOUtils.toString(stream, UTF_8).split(" ");
        Span nameE[];
        
        //name finder is not thread safe https://opennlp.apache.org/documentation/1.5.2-incubating/manual/opennlp.html#tools.namefind
        synchronized (nameFinder) {
            nameE = nameFinder.find(in);
            //the same name finder is reused, so clear adaptive data
            nameFinder.clearAdaptiveData();
        }

        String spanNames = Arrays.toString(Span.spansToStrings(nameE, in));
        spanNames = spanNames.substring(1, spanNames.length() - 1);
        String[] tmp = spanNames.split(",");

        for (String name : tmp) {
            name = name.trim();
            this.locationNameEntities.add(name);
        }


    }

    /*
     * Get the best location entity extracted from the input stream. Simply
     * return the most frequent entity, If there several highest frequent
     * entity, pick one randomly. May not be the optimal solution, but works.
     * 
     * @param locationNameEntities OpenNLP name finder's results, stored in
     * ArrayList
     */
    public void getBestNameEntity() {
        if (this.locationNameEntities.size() == 0)
            return;

        for (int i = 0; i < this.locationNameEntities.size(); ++i) {
            if (tf.containsKey(this.locationNameEntities.get(i)))
                tf.put(this.locationNameEntities.get(i),
                        tf.get(this.locationNameEntities.get(i)) + 1);
            else
                tf.put(this.locationNameEntities.get(i), 1);
        }
        int max = 0;
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(
                tf.entrySet());
        Collections.shuffle(list);
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1,
                    Map.Entry<String, Integer> o2) {
                // Descending Order
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        this.locationNameEntities.clear();// update so that they are in
                                          // descending order
        for (Map.Entry<String, Integer> entry : list) {
            this.locationNameEntities.add(entry.getKey());
            if (entry.getValue() > max) {
                max = entry.getValue();
                this.bestNameEntity = entry.getKey();
            }
        }
    }
}
