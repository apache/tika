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

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This tests that initialize() is called after adding the parameters
 * configured via TikaConfig
 */
public class DummyInitializableParser extends AbstractParser implements Initializable {

    public static String SUM_FIELD = "SUM";
    private static Set<MediaType> MIMES = new HashSet<>();
    static {
        MIMES.add(MediaType.TEXT_PLAIN);
    }

    @Field private short shortA = -2;
    @Field private short shortB = -3;
    private int sum = 0;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MIMES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        metadata.set(SUM_FIELD, Integer.toString(sum));
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        shortA = (Short)params.get("shortA").getValue();
        shortB = (Short)params.get("shortB").getValue();
        sum = shortA+shortB;
    }
}
