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

    private Map<String, Pattern> regexMap = new HashMap();

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
            Map<String, Matcher> matchers = new HashMap();
            for (Map.Entry<String, Pattern> e : regexMap.entrySet()) {
                matchers.put(e.getKey(), e.getValue().matcher(""));
            }
            while (line != null) {
                for (Map.Entry<String, Matcher> e : matchers.entrySet()) {
                    Matcher m = e.getValue();
                    if (m.reset(line).find()) {
                        String val = m.group(1);
                        metadata.set(e.getKey(), val);
                    }
                }
                line = reader.readLine();
            }
        }
    }

    @Field
    public void setRegexMap(Map<String, String> map) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            String field = e.getKey();
            Pattern pattern = Pattern.compile(e.getValue());
            regexMap.put(field, pattern);
        }
    }
}
