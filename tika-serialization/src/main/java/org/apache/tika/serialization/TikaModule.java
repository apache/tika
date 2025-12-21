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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.Serializers;

import org.apache.tika.config.Initializable;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.SelfConfiguring;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.writefilter.MetadataWriteFilterFactory;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.sax.ContentHandlerDecoratorFactory;
import org.apache.tika.serialization.serdes.DefaultDetectorSerializer;
import org.apache.tika.serialization.serdes.DefaultParserSerializer;

/**
 * Jackson module that provides compact serialization for Tika components.
 * <p>
 * Uses {@link ComponentNameResolver} for friendly name resolution (e.g., "pdf-parser").
 * <p>
 * Supports two formats:
 * <ol>
 *   <li>Simple string: {@code "text-parser"} → instance with defaults</li>
 *   <li>Object with type as key: {@code {"pdf-parser": {"ocrStrategy": "AUTO"}}} → instance with config</li>
 * </ol>
 * <p>
 * For components implementing {@link SelfConfiguring}, uses the {@link JsonConfig} constructor.
 * For other components, uses Jackson bean deserialization (readerForUpdating).
 */
public class TikaModule extends SimpleModule {

    private static ObjectMapper sharedMapper;

    /**
     * Interfaces that use compact format serialization.
     * Types implementing these interfaces will be serialized as:
     * - "type-name" for defaults
     * - {"type-name": {...}} for configured instances
     */
    private static final Set<Class<?>> COMPACT_FORMAT_INTERFACES = new HashSet<>();

    static {
        // Core component interfaces that use compact format
        COMPACT_FORMAT_INTERFACES.add(Parser.class);
        COMPACT_FORMAT_INTERFACES.add(Detector.class);
        COMPACT_FORMAT_INTERFACES.add(EncodingDetector.class);
        COMPACT_FORMAT_INTERFACES.add(MetadataFilter.class);
        COMPACT_FORMAT_INTERFACES.add(Translator.class);
        COMPACT_FORMAT_INTERFACES.add(Renderer.class);
        COMPACT_FORMAT_INTERFACES.add(DigesterFactory.class);
        COMPACT_FORMAT_INTERFACES.add(EmbeddedDocumentExtractorFactory.class);
        COMPACT_FORMAT_INTERFACES.add(MetadataWriteFilterFactory.class);
        COMPACT_FORMAT_INTERFACES.add(ContentHandlerDecoratorFactory.class);
    }

    /**
     * Checks if a type should use compact format serialization.
     * Returns true if the type implements any of the registered compact format interfaces.
     */
    private static boolean usesCompactFormat(Class<?> type) {
        for (Class<?> iface : COMPACT_FORMAT_INTERFACES) {
            if (iface.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    public TikaModule() {
        super("TikaModule");
    }

    /**
     * Sets the shared ObjectMapper for use during deserialization.
     * Must be called before deserializing components.
     *
     * @param mapper the ObjectMapper with TikaModule registered
     */
    public static void setSharedMapper(ObjectMapper mapper) {
        sharedMapper = mapper;
    }

    /**
     * Gets the shared ObjectMapper.
     *
     * @return the shared mapper, or null if not configured
     */
    public static ObjectMapper getSharedMapper() {
        return sharedMapper;
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addDeserializers(new TikaDeserializers());
        context.addSerializers(new TikaSerializers());
    }

    /**
     * Deserializers for Tika component types.
     * <p>
     * Only handles abstract types (interfaces/abstract classes) that are compact format interfaces.
     * Concrete implementations use normal Jackson bean deserialization for their properties.
     */
    private static class TikaDeserializers extends Deserializers.Base {
        @Override
        public JsonDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config,
                                                         BeanDescription beanDesc) {
            Class<?> rawClass = type.getRawClass();

            // Only use compact format deserializer for ABSTRACT types (interfaces or abstract classes)
            // that are in the compact format interfaces list.
            // Concrete implementations (like ExternalParser, HtmlParser) should use normal
            // Jackson bean deserialization for their properties.
            if (rawClass.isInterface() || Modifier.isAbstract(rawClass.getModifiers())) {
                if (COMPACT_FORMAT_INTERFACES.contains(rawClass) || usesCompactFormat(rawClass)) {
                    return new TikaComponentDeserializer(rawClass);
                }
            }

            return null;
        }
    }

    /**
     * Serializers for Tika component types.
     */
    private static class TikaSerializers extends Serializers.Base {
        @Override
        public JsonSerializer<?> findSerializer(SerializationConfig config, JavaType type,
                                                  BeanDescription beanDesc) {
            Class<?> rawClass = type.getRawClass();

            // Use dedicated serializers for SPI composite types
            if (rawClass == DefaultParser.class) {
                return new DefaultParserSerializer();
            }
            if (rawClass == DefaultDetector.class) {
                return new DefaultDetectorSerializer();
            }

            // Handle MimeFilteringDecorator specially - serialize wrapped parser with mime filters
            if (rawClass == ParserDecorator.MimeFilteringDecorator.class) {
                return new TikaComponentSerializer();
            }

            // Only serialize with compact format if type implements a compact format interface
            // AND has a registered friendly name
            if (usesCompactFormat(rawClass) && ComponentNameResolver.getFriendlyName(rawClass) != null) {
                return new TikaComponentSerializer();
            }

            return null;
        }
    }

    /**
     * Deserializer that handles both string and object formats for Tika components.
     */
    private static class TikaComponentDeserializer extends JsonDeserializer<Object> {
        private final Class<?> expectedType;
        // Plain mapper for property updates (avoids infinite recursion with registered types)
        private final ObjectMapper plainMapper;

        TikaComponentDeserializer(Class<?> expectedType) {
            this.expectedType = expectedType;
            this.plainMapper = new ObjectMapper();
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.readValueAsTree();

            ObjectMapper mapper = sharedMapper;
            if (mapper == null) {
                throw new IOException("Shared ObjectMapper not configured. " +
                        "Call TikaModule.setSharedMapper() before deserializing.");
            }

            if (node.isTextual()) {
                // Simple string format: "pdf-parser"
                String typeName = node.asText();
                return instantiate(typeName, null, mapper);
            } else if (node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                if (!fields.hasNext()) {
                    // Empty object {} - try to create default instance if expectedType is concrete
                    try {
                        return expectedType.getDeclaredConstructor().newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IOException("Empty object for abstract type " +
                                expectedType.getSimpleName() + " - specify a concrete type name");
                    }
                }
                Map.Entry<String, JsonNode> entry = fields.next();
                return instantiate(entry.getKey(), entry.getValue(), mapper);
            } else {
                throw new IOException("Expected string or object for " +
                        expectedType.getSimpleName() + ", got: " + node.getNodeType());
            }
        }

        private Object instantiate(String typeName, JsonNode configNode, ObjectMapper mapper) throws IOException {
            // Resolve the class using ComponentNameResolver
            Class<?> clazz;
            try {
                clazz = ComponentNameResolver.resolveClass(typeName,
                        Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IOException("Unknown type: " + typeName, e);
            }

            // Verify type compatibility
            if (!expectedType.isAssignableFrom(clazz)) {
                throw new IOException("Type " + typeName + " (" + clazz.getName() +
                        ") is not assignable to " + expectedType.getName());
            }

            // Extract mime filter fields before stripping them
            Set<MediaType> includeTypes = extractMimeTypes(configNode, "_mime-include");
            Set<MediaType> excludeTypes = extractMimeTypes(configNode, "_mime-exclude");

            // Strip decorator fields before passing to component
            JsonNode cleanedConfig = stripDecoratorFields(configNode);

            try {
                Object instance;

                // DefaultParser and DefaultDetector must be loaded via TikaLoader for proper dependency injection
                if (clazz == DefaultParser.class) {
                    throw new IOException("DefaultParser must be loaded via TikaLoader, not directly " +
                            "via Jackson deserialization. Use TikaLoader.load() to load configuration.");
                } else if (clazz == DefaultDetector.class) {
                    throw new IOException("DefaultDetector must be loaded via TikaLoader, not directly " +
                            "via Jackson deserialization. Use TikaLoader.load() to load configuration.");
                } else if (clazz == MimeTypes.class) {
                    // MimeTypes must use the singleton to have all type definitions loaded
                    instance = MimeTypes.getDefaultMimeTypes();
                } else if (cleanedConfig == null || cleanedConfig.isEmpty()) {
                    // If no config, use default constructor
                    instance = clazz.getDeclaredConstructor().newInstance();
                } else if (SelfConfiguring.class.isAssignableFrom(clazz)) {
                    // SelfConfiguring components: prefer JsonConfig constructor if available
                    Constructor<?> jsonConfigCtor = findJsonConfigConstructor(clazz);
                    if (jsonConfigCtor != null) {
                        String json = mapper.writeValueAsString(cleanedConfig);
                        instance = jsonConfigCtor.newInstance((JsonConfig) () -> json);
                    } else {
                        instance = clazz.getDeclaredConstructor().newInstance();
                    }
                } else {
                    // Non-SelfConfiguring: use Jackson bean deserialization
                    instance = clazz.getDeclaredConstructor().newInstance();
                    plainMapper.readerForUpdating(instance).readValue(cleanedConfig);
                }

                // Call initialize() on Initializable components
                if (instance instanceof Initializable) {
                    try {
                        ((Initializable) instance).initialize();
                    } catch (TikaConfigException e) {
                        throw new IOException("Failed to initialize " + typeName, e);
                    }
                }

                // Wrap parser with mime filtering if include/exclude types specified
                if (instance instanceof Parser && (!includeTypes.isEmpty() || !excludeTypes.isEmpty())) {
                    instance = ParserDecorator.withMimeFilters((Parser) instance, includeTypes, excludeTypes);
                }

                return instance;

            } catch (ReflectiveOperationException e) {
                throw new IOException("Failed to instantiate: " + typeName, e);
            }
        }

        private Set<MediaType> extractMimeTypes(JsonNode configNode, String fieldName) {
            Set<MediaType> types = new HashSet<>();
            if (configNode == null || !configNode.has(fieldName)) {
                return types;
            }
            JsonNode arrayNode = configNode.get(fieldName);
            if (arrayNode.isArray()) {
                for (JsonNode typeNode : arrayNode) {
                    types.add(MediaType.parse(typeNode.asText()));
                }
            }
            return types;
        }

        private Constructor<?> findJsonConfigConstructor(Class<?> clazz) {
            try {
                return clazz.getConstructor(JsonConfig.class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        /**
         * Deserializes a JsonNode using a dedicated deserializer.
         */
        private <T> T deserializeWithNode(JsonDeserializer<T> deserializer, JsonNode node,
                                          ObjectMapper mapper) throws IOException {
            if (node == null) {
                node = mapper.createObjectNode();
            }
            try (JsonParser p = mapper.treeAsTokens(node)) {
                p.nextToken();
                return deserializer.deserialize(p, mapper.getDeserializationContext());
            }
        }

        /**
         * Strips decorator fields (_mime-include, _mime-exclude) from config node.
         * These fields are handled by TikaLoader for wrapping, not by the component itself.
         * Note: _exclude is NOT stripped as it's used by DefaultParser for SPI exclusions.
         */
        private JsonNode stripDecoratorFields(JsonNode configNode) {
            if (configNode == null || !configNode.isObject()) {
                return configNode;
            }
            ObjectNode cleaned = configNode.deepCopy();
            cleaned.remove("_mime-include");
            cleaned.remove("_mime-exclude");
            return cleaned;
        }
    }

    /**
     * Serializer that produces compact output for Tika components.
     * Outputs simple string if using defaults, object with type key if configured.
     */
    private static class TikaComponentSerializer extends JsonSerializer<Object> {
        // Plain mapper for serializing without TikaModule (avoids infinite recursion)
        private final ObjectMapper plainMapper;

        TikaComponentSerializer() {
            this.plainMapper = new ObjectMapper();
            this.plainMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            // Handle MimeFilteringDecorator specially for round-trip
            Set<MediaType> includeTypes = null;
            Set<MediaType> excludeTypes = null;
            if (value instanceof ParserDecorator.MimeFilteringDecorator mfd) {
                includeTypes = mfd.getIncludeTypes();
                excludeTypes = mfd.getExcludeTypes();
                value = mfd.getWrappedParser();
            }

            String typeName = ComponentNameResolver.getFriendlyName(value.getClass());
            if (typeName == null) {
                typeName = value.getClass().getName();
            }

            ObjectMapper mapper = (ObjectMapper) gen.getCodec();

            // Get configured properties (only non-default values)
            ObjectNode configNode = getConfiguredProperties(value, mapper);

            // Add mime filter fields if present
            if (includeTypes != null && !includeTypes.isEmpty()) {
                configNode.set("_mime-include", mimeTypesToArray(includeTypes, mapper));
            }
            if (excludeTypes != null && !excludeTypes.isEmpty()) {
                configNode.set("_mime-exclude", mimeTypesToArray(excludeTypes, mapper));
            }

            if (configNode.isEmpty()) {
                // No config differences - output simple string
                gen.writeString(typeName);
            } else {
                // Has config - output object with type as key
                gen.writeStartObject();
                gen.writeObjectField(typeName, configNode);
                gen.writeEndObject();
            }
        }

        private JsonNode mimeTypesToArray(Set<MediaType> types, ObjectMapper mapper) {
            var arrayNode = mapper.createArrayNode();
            for (MediaType type : types) {
                arrayNode.add(type.toString());
            }
            return arrayNode;
        }

        private ObjectNode getConfiguredProperties(Object value, ObjectMapper mapper) throws IOException {
            try {
                // Check for getConfig() method (common pattern for config objects)
                Method getConfigMethod = findGetConfigMethod(value.getClass());

                if (getConfigMethod != null) {
                    // Serialize the config object's properties
                    Object config = getConfigMethod.invoke(value);
                    if (config == null) {
                        return mapper.createObjectNode();
                    }

                    // Create default config to compare against
                    Object defaultConfig = config.getClass().getDeclaredConstructor().newInstance();

                    ObjectNode configNode = plainMapper.valueToTree(config);
                    ObjectNode defaultNode = plainMapper.valueToTree(defaultConfig);

                    // Only keep properties that differ from defaults
                    ObjectNode result = mapper.createObjectNode();
                    Iterator<Map.Entry<String, JsonNode>> fields = configNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        JsonNode defaultValue = defaultNode.get(field.getKey());
                        if (!field.getValue().equals(defaultValue)) {
                            result.set(field.getKey(), field.getValue());
                        }
                    }
                    return result;
                } else {
                    // No config object - serialize the component directly
                    Object defaultInstance = value.getClass().getDeclaredConstructor().newInstance();

                    ObjectNode valueNode = plainMapper.valueToTree(value);
                    ObjectNode defaultNode = plainMapper.valueToTree(defaultInstance);

                    ObjectNode result = plainMapper.createObjectNode();
                    Iterator<Map.Entry<String, JsonNode>> fields = valueNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        JsonNode defaultValue = defaultNode.get(field.getKey());
                        if (!field.getValue().equals(defaultValue)) {
                            result.set(field.getKey(), field.getValue());
                        }
                    }
                    return result;
                }
            } catch (ReflectiveOperationException e) {
                throw new IOException("Failed to serialize config", e);
            }
        }

        private Method findGetConfigMethod(Class<?> clazz) {
            try {
                Method method = clazz.getMethod("getConfig");
                if (method.getReturnType() != void.class) {
                    return method;
                }
            } catch (NoSuchMethodException e) {
                // No getConfig method
            }
            return null;
        }
    }
}
