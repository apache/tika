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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

@TikaComponent(spi = false)
public class RegexCaptureParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.TEXT_PLAIN);

    private final RegexCaptureParserConfig config;
    private final Map<String, Pattern> captureMap;
    private final Map<String, Pattern> matchMap;
    private final boolean writeContent;

    public RegexCaptureParser() {
        this(new RegexCaptureParserConfig());
    }

    public RegexCaptureParser(RegexCaptureParserConfig config) {
        this.config = config;
        this.captureMap = new HashMap<>();
        for (Map.Entry<String, String> e : config.getCaptureMap().entrySet()) {
            this.captureMap.put(e.getKey(), Pattern.compile(e.getValue()));
        }
        this.matchMap = new HashMap<>();
        for (Map.Entry<String, String> e : config.getMatchMap().entrySet()) {
            this.matchMap.put(e.getKey(), Pattern.compile(e.getValue()));
        }
        this.writeContent = config.isWriteContent();
    }

    public RegexCaptureParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, RegexCaptureParserConfig.class));
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public RegexCaptureParserConfig getConfig() {
        return config;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(tis,
                StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            Map<String, Matcher> localCaptureMap = new HashMap();
            for (Map.Entry<String, Pattern> e : captureMap.entrySet()) {
                localCaptureMap.put(e.getKey(), e.getValue().matcher(""));
            }
            Map<String, Matcher> localMatchMap = new HashMap<>();
            for (Map.Entry<String, Pattern> e : matchMap.entrySet()) {
                localMatchMap.put(e.getKey(), e.getValue().matcher(""));
            }

            Map<String, Set<String>> keyVals = new HashMap<>();
            while (line != null) {
                for (Map.Entry<String, Matcher> e : localCaptureMap.entrySet()) {
                    Matcher m = e.getValue();
                    if (m.reset(line).find()) {
                        String val = m.group(1);
                        Set<String> vals = keyVals.get(e.getKey());
                        if (vals == null) {
                            vals = new LinkedHashSet<>();
                            keyVals.put(e.getKey(), vals);
                        }
                        vals.add(val);
                    }
                }
                for (Map.Entry<String, Matcher> e : localMatchMap.entrySet()) {
                    if (e.getValue().reset(line).find()) {
                        metadata.set(e.getKey(), "true");
                    }
                }
                if (writeContent) {
                    char[] chars = line.toCharArray();
                    handler.characters(chars, 0, chars.length);
                }
                line = reader.readLine();
            }
            for (Map.Entry<String, Set<String>> e : keyVals.entrySet()) {
                for (String val : e.getValue()) {
                    metadata.add(e.getKey(), val);
                }
            }
        }
    }
}
