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

package org.apache.tika.parser.ner;

import java.util.Map;
import java.util.Set;

/**
 * Defines a contract for named entity recogniser. The NER contract includes {@link #isAvailable()},
 * {@link #getEntityTypes()} and {@link #recognise(String)}
 */
public interface NERecogniser {

    //the common named entity classes
    String LOCATION = "LOCATION";
    String PERSON = "PERSON";
    String ORGANIZATION = "ORGANIZATION";
    String MISCELLANEOUS = "MISCELLANEOUS";
    String TIME = "TIME";
    String DATE = "DATE";
    String PERCENT = "PERCENT";
    String MONEY = "MONEY";

    /**
     * checks if this Named Entity recogniser is available for service
     *
     * @return true if this recogniser is ready to recognise, false otherwise
     */
    boolean isAvailable();

    /**
     * gets a set of entity types whose names are recognisable by this
     *
     * @return set of entity types/classes
     */
    Set<String> getEntityTypes();

    /**
     * call for name recognition action from text
     *
     * @param text text with possibly contains names
     * @return map of entityType -&gt; set of names
     */
    Map<String, Set<String>> recognise(String text);
}
