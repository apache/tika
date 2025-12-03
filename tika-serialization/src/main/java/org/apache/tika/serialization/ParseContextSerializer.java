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
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ConfigContainer;
import org.apache.tika.config.loader.PolymorphicObjectMapperFactory;
import org.apache.tika.parser.ParseContext;

public class ParseContextSerializer extends JsonSerializer<ParseContext> {
    private static final Logger LOG = LoggerFactory.getLogger(ParseContextSerializer.class);

    public static final String PARSE_CONTEXT = "parseContext";

    /**
     * Static ObjectMapper configured for polymorphic serialization/deserialization.
     * Initialized once when the class is loaded to avoid creating a new mapper on each call.
     * Package-private to allow ParseContextDeserializer to use the same mapper.
     */
    static final ObjectMapper POLYMORPHIC_MAPPER = PolymorphicObjectMapperFactory.getMapper();

    @Override
    public void serialize(ParseContext parseContext, JsonGenerator jsonGenerator,
                         SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();

        Map<String, Object> contextMap = parseContext.getContextMap();
        ConfigContainer configContainer = parseContext.get(ConfigContainer.class);

        // Serialize objects stored directly in ParseContext (legacy format)
        // These are objects set via context.set(SomeClass.class, someObject)
        boolean hasNonConfigObjects = contextMap.size() > (configContainer != null ? 1 : 0);
        if (hasNonConfigObjects) {
            jsonGenerator.writeFieldName("objects");
            jsonGenerator.writeStartObject();

            for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
                String className = entry.getKey();
                if (className.equals(ConfigContainer.class.getName())) {
                    continue;
                }

                Object value = entry.getValue();

                // Write the field name (superclass/interface name from key)
                jsonGenerator.writeFieldName(className);

                // Let Jackson handle type information and serialization
                // Use writerFor(Object.class) to ensure polymorphic type info is added
                POLYMORPHIC_MAPPER.writerFor(Object.class).writeValue(jsonGenerator, value);
            }

            jsonGenerator.writeEndObject();
        }

        // Write ConfigContainer fields as top-level properties (new friendly-name format)
        // Each field contains a JSON string representing a parser/component configuration
        // using the same friendly names as tika-config.json (e.g., "pdf-parser", "html-parser")
        if (configContainer != null) {
            for (String key : configContainer.getKeys()) {
                jsonGenerator.writeFieldName(key);
                // Write the JSON string as raw JSON (not as a quoted string)
                jsonGenerator.writeRawValue(configContainer.get(key).get().json());
            }
        }

        jsonGenerator.writeEndObject();
    }
}
