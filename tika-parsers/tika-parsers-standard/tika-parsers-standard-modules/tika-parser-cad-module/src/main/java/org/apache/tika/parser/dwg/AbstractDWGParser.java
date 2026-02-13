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
package org.apache.tika.parser.dwg;


import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;


public abstract class AbstractDWGParser implements Parser {


    /**
     *
     */
    private static final long serialVersionUID = 6261810259683381984L;
    private final DWGParserConfig defaultDwgParserConfig;

    public AbstractDWGParser() {
        this.defaultDwgParserConfig = new DWGParserConfig();
    }

    public AbstractDWGParser(DWGParserConfig config) {
        this.defaultDwgParserConfig = config;
    }

    public AbstractDWGParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, DWGParserConfig.class));
    }

    public void configure(ParseContext parseContext) {
        DWGParserConfig dwgParserConfig = parseContext.get(DWGParserConfig.class, defaultDwgParserConfig);
        parseContext.set(DWGParserConfig.class, dwgParserConfig);
    }

    public DWGParserConfig getDefaultConfig() {
        return defaultDwgParserConfig;
    }
}
