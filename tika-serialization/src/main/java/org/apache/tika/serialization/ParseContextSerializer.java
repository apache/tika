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
package org.apache.tika.serialization;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.config.ComponentConfigs;
import org.apache.tika.parser.ParseContext;

public class ParseContextSerializer extends JsonSerializer<ParseContext> {
    public static final String PARSE_CONTEXT = "parseContext";

    @Override
    public void serialize(ParseContext parseContext, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        Set<String> objectKeySet = parseContext.keySet();
        ComponentConfigs p = parseContext.get(ComponentConfigs.class);
        if ((p != null && objectKeySet.size() > 1) || (p == null && ! objectKeySet.isEmpty())) {
            jsonGenerator.writeFieldName("objects");
            jsonGenerator.writeStartObject();
            for (String className : parseContext.keySet()) {
                if (className.equals(ComponentConfigs.class.getName())) {
                    continue;
                }
                try {
                    Class clazz = Class.forName(className);
                    TikaJsonSerializer.serialize(className, parseContext.get(clazz), jsonGenerator);
                } catch (TikaSerializationException e) {
                    throw new IOException(e);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            jsonGenerator.writeEndObject();
        }
        if (p != null) {
            for (String k : p.getComponentNames()) {
                jsonGenerator.writeStringField(k, p.get(k).orElse(null));
            }
        }
        jsonGenerator.writeEndObject();
    }
}
