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
import java.lang.reflect.Modifier;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.ComponentInstantiator;
import org.apache.tika.exception.TikaConfigException;

/**
 * Jackson module that handles deserialization of abstract types using wrapper object format.
 * <p>
 * Automatically applies to ANY abstract type (interface or abstract class) without
 * requiring hardcoded type lists. Supports both formats:
 * <ul>
 *   <li>Wrapper format: {@code {"type-name": {"prop": "value"}}}</li>
 *   <li>Legacy @class format: {@code {"@class": "fqcn", "prop": "value"}}</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * "digesterFactory": {
 *   "commons-digester-factory": {
 *     "markLimit": 100000
 *   }
 * }
 * </pre>
 */
public final class TikaAbstractTypeMixins {

    private static final Logger LOG = LoggerFactory.getLogger(TikaAbstractTypeMixins.class);

    private TikaAbstractTypeMixins() {
        // Utility class
    }

    /**
     * Registers the abstract type handling module on the given ObjectMapper.
     * This includes both serializers (to add type wrappers) and deserializers
     * (to resolve type wrappers).
     *
     * @param mapper the ObjectMapper to configure
     */
    public static void registerDeserializers(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule("TikaAbstractTypes");
        module.setDeserializerModifier(new AbstractTypeDeserializerModifier(mapper));
        module.setSerializerModifier(new AbstractTypeSerializerModifier(mapper));
        mapper.registerModule(module);
    }

    /**
     * Modifier that intercepts deserialization of abstract types and applies
     * wrapper object handling.
     */
    private static class AbstractTypeDeserializerModifier extends BeanDeserializerModifier {

        private final ObjectMapper mapper;

        AbstractTypeDeserializerModifier(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                                                       BeanDescription beanDesc,
                                                       JsonDeserializer<?> deserializer) {
            Class<?> beanClass = beanDesc.getBeanClass();

            // Skip types that shouldn't use wrapper format
            if (shouldSkip(beanClass)) {
                return deserializer;
            }

            // Only handle abstract types (interfaces or abstract classes)
            if (beanClass.isInterface() || Modifier.isAbstract(beanClass.getModifiers())) {
                LOG.debug("Registering wrapper deserializer for abstract type: {}",
                        beanClass.getName());
                return new WrapperObjectDeserializer<>(beanClass, mapper);
            }

            return deserializer;
        }

        private boolean shouldSkip(Class<?> beanClass) {
            // Skip primitives and their wrappers
            if (beanClass.isPrimitive()) {
                return true;
            }

            // Skip common JDK types
            String name = beanClass.getName();
            if (name.startsWith("java.") || name.startsWith("javax.")) {
                return true;
            }

            // Skip arrays
            if (beanClass.isArray()) {
                return true;
            }

            return false;
        }
    }

    /**
     * Deserializer that handles wrapper object format for abstract types.
     */
    private static class WrapperObjectDeserializer<T> extends JsonDeserializer<T> {

        private final Class<?> abstractType;
        private final ObjectMapper mapper;

        WrapperObjectDeserializer(Class<?> abstractType, ObjectMapper mapper) {
            this.abstractType = abstractType;
            this.mapper = mapper;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.readValueAsTree();

            if (!node.isObject() || node.isEmpty()) {
                // Let Jackson's default handling fail appropriately
                return (T) ctxt.handleUnexpectedToken(abstractType, p);
            }

            // Check for legacy "@class" format
            if (node.has("@class")) {
                String typeName = node.get("@class").asText();
                // Create config node without @class
                com.fasterxml.jackson.databind.node.ObjectNode configObj =
                        mapper.createObjectNode();
                node.properties().forEach(entry -> {
                    if (!"@class".equals(entry.getKey())) {
                        configObj.set(entry.getKey(), entry.getValue());
                    }
                });
                return instantiateType(typeName, configObj, ctxt);
            }

            // Check for wrapper format: single field whose value is an object
            // e.g., {"commons-digester-factory": {"markLimit": 100000}}
            if (node.size() == 1) {
                String typeName = node.fieldNames().next();
                JsonNode configNode = node.get(typeName);
                // Only treat as wrapper if the value is an object (not primitive/array)
                if (configNode.isObject()) {
                    return instantiateType(typeName, configNode, ctxt);
                }
            }

            // Not wrapper format - this is likely an error (can't instantiate abstract type)
            // Throw JsonMappingException so ConfigLoader wraps it in TikaConfigException
            throw JsonMappingException.from(p,
                    "Cannot deserialize abstract type " + abstractType.getSimpleName() +
                    ". Use wrapper format: {\"concrete-type-name\": {...}} or " +
                    "legacy format: {\"@class\": \"fully.qualified.ClassName\", ...}");
        }

        private T instantiateType(String typeName, JsonNode configNode,
                                   DeserializationContext ctxt) throws IOException {
            try {
                Class<?> concreteClass = ComponentNameResolver.resolveClass(typeName,
                        TikaAbstractTypeMixins.class.getClassLoader());
                return ComponentInstantiator.instantiate(concreteClass, configNode, mapper);
            } catch (ClassNotFoundException e) {
                throw JsonMappingException.from(ctxt.getParser(),
                        "Unknown type '" + typeName + "' for " + abstractType.getSimpleName());
            } catch (TikaConfigException e) {
                throw JsonMappingException.from(ctxt.getParser(),
                        "Failed to instantiate " + typeName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Modifier that intercepts serialization of values declared as abstract types
     * and wraps them with type information.
     */
    private static class AbstractTypeSerializerModifier extends BeanSerializerModifier {

        private final ObjectMapper mapper;

        AbstractTypeSerializerModifier(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public JsonSerializer<?> modifySerializer(SerializationConfig config,
                                                   BeanDescription beanDesc,
                                                   JsonSerializer<?> serializer) {
            Class<?> beanClass = beanDesc.getBeanClass();

            // Skip types that shouldn't use wrapper format
            if (shouldSkip(beanClass)) {
                return serializer;
            }

            // For concrete Tika types, wrap with type name if they extend/implement an abstract type
            // This ensures polymorphic types in lists get properly wrapped
            if (isTikaPolymorphicType(beanClass)) {
                LOG.debug("Registering wrapper serializer for polymorphic type: {}",
                        beanClass.getName());
                return new WrapperObjectSerializer<>(serializer, mapper);
            }

            return serializer;
        }

        private boolean shouldSkip(Class<?> beanClass) {
            // Skip primitives and their wrappers
            if (beanClass.isPrimitive()) {
                return true;
            }

            // Skip common JDK types
            String name = beanClass.getName();
            if (name.startsWith("java.") || name.startsWith("javax.")) {
                return true;
            }

            // Skip arrays
            if (beanClass.isArray()) {
                return true;
            }

            // Skip abstract types (we want to wrap concrete implementations, not the abstract types themselves)
            if (beanClass.isInterface() || Modifier.isAbstract(beanClass.getModifiers())) {
                return true;
            }

            return false;
        }

        /**
         * Checks if this class should be wrapped with type information during serialization.
         * Only types registered in the component registry are wrapped - this excludes
         * container types (like CompositeMetadataFilter) that are not in the registry.
         */
        private boolean isTikaPolymorphicType(Class<?> beanClass) {
            // Only wrap types that have a registered friendly name in the registry
            return ComponentNameResolver.getFriendlyName(beanClass) != null;
        }
    }

    /**
     * Serializer that wraps objects with their type name.
     * Output format: {"type-name": {...properties...}}
     */
    private static class WrapperObjectSerializer<T> extends JsonSerializer<T> {

        private final JsonSerializer<T> delegate;
        private final ObjectMapper mapper;

        @SuppressWarnings("unchecked")
        WrapperObjectSerializer(JsonSerializer<?> delegate, ObjectMapper mapper) {
            this.delegate = (JsonSerializer<T>) delegate;
            this.mapper = mapper;
        }

        @Override
        public void serialize(T value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }

            // Get the friendly name (guaranteed to exist since we only wrap registered types)
            String typeName = ComponentNameResolver.getFriendlyName(value.getClass());

            // Write wrapper: {"type-name": {...}}
            gen.writeStartObject();
            gen.writeFieldName(typeName);
            delegate.serialize(value, gen, serializers);
            gen.writeEndObject();
        }
    }
}
