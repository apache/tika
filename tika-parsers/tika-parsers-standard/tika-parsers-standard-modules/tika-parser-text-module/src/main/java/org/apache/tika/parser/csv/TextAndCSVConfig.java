/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.csv;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class TextAndCSVConfig implements Serializable {

    private static final Map<Character, String> DELIMITER_TO_NAME_MAP = new HashMap<>();
    private static final Map<String, Character> NAME_TO_DELIMITER_MAP = new HashMap<>();

    static {
        DELIMITER_TO_NAME_MAP.put(',', "comma");
        DELIMITER_TO_NAME_MAP.put('\t', "tab");
        DELIMITER_TO_NAME_MAP.put('|', "pipe");
        DELIMITER_TO_NAME_MAP.put(';', "semicolon");
    }

    static {
        for (Map.Entry<Character, String> e : DELIMITER_TO_NAME_MAP.entrySet()) {
            NAME_TO_DELIMITER_MAP.put(e.getValue(), e.getKey());
        }
    }

    private Map<String, Character> nameToDelimiterMap = NAME_TO_DELIMITER_MAP;
    private Map<Character, String> delimiterToNameMap = DELIMITER_TO_NAME_MAP;

    public Map<String, Character> getNameToDelimiterMap() {
        return nameToDelimiterMap;
    }

    public Map<Character, String> getDelimiterToNameMap() {
        return delimiterToNameMap;
    }

    public void setNameToDelimiterMap(Map<String, Character> nameToDelimiterMap) {
        this.nameToDelimiterMap = new HashMap<>(nameToDelimiterMap);
        this.delimiterToNameMap = new HashMap<>();
        nameToDelimiterMap.entrySet()
                          .forEach(e -> delimiterToNameMap.put(e.getValue(), e.getKey()));
    }
}
