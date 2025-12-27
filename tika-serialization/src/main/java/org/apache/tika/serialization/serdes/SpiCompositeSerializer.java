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
package org.apache.tika.serialization.serdes;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.serialization.ComponentNameResolver;

/**
 * Abstract base serializer for SPI-loaded composite types that support exclusions.
 * <p>
 * Outputs JSON like:
 * <pre>
 * "default-detector"  // if no exclusions
 * { "default-detector": { "exclude": ["html-detector", "zip-detector"] } }  // with exclusions
 * </pre>
 *
 * @param <T> the composite type (e.g., DefaultDetector, DefaultParser)
 */
public abstract class SpiCompositeSerializer<T> extends JsonSerializer<T> {

    protected final String typeName;

    protected SpiCompositeSerializer(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public void serialize(T value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        Set<Class<?>> excludedClasses = getExcludedClasses(value);

        if (excludedClasses == null || excludedClasses.isEmpty()) {
            gen.writeString(typeName);
        } else {
            gen.writeStartObject();
            gen.writeObjectFieldStart(typeName);
            gen.writeArrayFieldStart("exclude");
            for (Class<?> clazz : excludedClasses) {
                String name = ComponentNameResolver.getFriendlyName(clazz);
                if (name != null) {
                    gen.writeString(name);
                } else {
                    // Fall back to fully qualified class name
                    gen.writeString(clazz.getName());
                }
            }
            gen.writeEndArray();
            gen.writeEndObject();
            gen.writeEndObject();
        }
    }

    /**
     * Get the excluded classes from the composite instance.
     *
     * @param value the composite instance
     * @return the set of excluded classes, or null/empty if none
     */
    protected abstract Set<Class<?>> getExcludedClasses(T value);
}
