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
package org.apache.tika.parser;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for {@link RegexCaptureParser}.
 */
public class RegexCaptureParserConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, String> captureMap = new HashMap<>();
    private Map<String, String> matchMap = new HashMap<>();
    private boolean writeContent = false;

    public RegexCaptureParserConfig() {
    }

    public Map<String, String> getCaptureMap() {
        return captureMap;
    }

    public void setCaptureMap(Map<String, String> captureMap) {
        this.captureMap = captureMap;
    }

    public Map<String, String> getMatchMap() {
        return matchMap;
    }

    public void setMatchMap(Map<String, String> matchMap) {
        this.matchMap = matchMap;
    }

    public boolean isWriteContent() {
        return writeContent;
    }

    public void setWriteContent(boolean writeContent) {
        this.writeContent = writeContent;
    }
}
