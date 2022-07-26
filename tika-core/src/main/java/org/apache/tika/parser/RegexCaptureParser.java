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
import java.io.InputStream;
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

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class RegexCaptureParser extends AbstractParser implements Initializable {

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.TEXT_PLAIN);

    private Map<String, Pattern> captureMap = new HashMap<>();
    private Map<String, Pattern> matchMap = new HashMap<>();

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {

    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
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
                line = reader.readLine();
            }
            for (Map.Entry<String, Set<String>> e : keyVals.entrySet()) {
                for (String val : e.getValue()) {
                    metadata.add(e.getKey(), val);
                }
            }
        }
    }

    @Field
    @Deprecated
    /**
     * @deprecated use setCaptureMap
     */
    public void setRegexMap(Map<String, String> map) {
        setCaptureMap(map);
    }

    @Field
    public void setCaptureMap(Map<String, String> map) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            String field = e.getKey();
            Pattern pattern = Pattern.compile(e.getValue());
            captureMap.put(field, pattern);
        }
    }

    @Field
    public void setMatchMap(Map<String, String> map) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            String field = e.getKey();
            Pattern pattern = Pattern.compile(e.getValue());
            matchMap.put(field, pattern);
        }
    }
}
