/**
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
package org.apache.tika.config;

import java.util.List;
import java.util.Map;

/**
 * Store all the informations regarding specific parser   
 * @author Rida Benjelloun (ridabenjelloun@apache.org)  
 */
public class ParserConfig {

    private String name;

    private String parserClass;

    private Map<String, String> mimes;

    private String nameSpace;

    private List<Content> contents;

    public List<Content> getContents() {
        return contents;
    }

    public void setContents(List<Content> contents) {
        this.contents = contents;
    }

    public Map<String, String> getMimes() {
        return mimes;
    }

    public void setMimes(Map<String, String> mimes) {
        this.mimes = mimes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameSpace() {
        return nameSpace;
    }

    public void setNameSpace(String nameSpace) {
        this.nameSpace = nameSpace;
    }

    public String getParserClass() {
        return parserClass;
    }

    public void setParserClass(String parserClass) {
        this.parserClass = parserClass;
    }

}
