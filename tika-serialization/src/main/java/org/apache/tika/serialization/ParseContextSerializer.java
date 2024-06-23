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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.parser.ParseContext;

public class ParseContextSerializer extends JsonSerializer<ParseContext> {


    @Override
    public void serialize(ParseContext parseContext, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeFieldName("parseContext");
        jsonGenerator.writeStartObject();
        for (String className : parseContext.keySet()) {
            try {
                Class clazz = Class.forName(className);
                TikaJsonSerializer.serialize(className, parseContext.get(clazz), clazz, jsonGenerator);
            } catch (TikaSerializationException e) {
                throw new IOException(e);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }
        jsonGenerator.writeEndObject();
    }
}
