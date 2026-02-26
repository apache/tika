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

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.ComponentRegistry;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.extractor.UnpackSelector;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.writefilter.MetadataWriteLimiterFactory;
import org.apache.tika.parser.Parser;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.sax.ContentHandlerDecoratorFactory;
import org.apache.tika.sax.ContentHandlerFactory;

/**
 * Utility class that resolves friendly component names to classes using ComponentRegistry.
 * <p>
 * Supports friendly names like "pdf-parser" as well as fully qualified class names.
 * Registries must be registered via {@link #registerRegistry(String, ComponentRegistry)}
 * before use.
 * <p>
 * Also stores {@link ComponentConfig} registrations for top-level component loading.
 */
public final class ComponentNameResolver {

    /**
     * Interfaces that use compact format serialization and serve as ParseContext keys.
     * Types implementing these interfaces will be serialized as:
     * - "type-name" for defaults
     * - {"type-name": {...}} for configured instances
     */
    private static final Set<Class<?>> CONTEXT_KEY_INTERFACES = new HashSet<>();

    static {
        CONTEXT_KEY_INTERFACES.add(Parser.class);
        CONTEXT_KEY_INTERFACES.add(Detector.class);
        CONTEXT_KEY_INTERFACES.add(EncodingDetector.class);
        CONTEXT_KEY_INTERFACES.add(MetadataFilter.class);
        CONTEXT_KEY_INTERFACES.add(Translator.class);
        CONTEXT_KEY_INTERFACES.add(Renderer.class);
        CONTEXT_KEY_INTERFACES.add(DigesterFactory.class);
        CONTEXT_KEY_INTERFACES.add(EmbeddedDocumentExtractorFactory.class);
        CONTEXT_KEY_INTERFACES.add(MetadataWriteLimiterFactory.class);
        CONTEXT_KEY_INTERFACES.add(ContentHandlerDecoratorFactory.class);
        CONTEXT_KEY_INTERFACES.add(ContentHandlerFactory.class);
        CONTEXT_KEY_INTERFACES.add(UnpackSelector.class);
    }

    private static final Map<String, ComponentRegistry> REGISTRIES = new ConcurrentHashMap<>();

    // Component configuration storage (keyed by JSON field name and by component class)
    private static final Map<String, ComponentConfig<?>> FIELD_TO_CONFIG = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ComponentConfig<?>> CLASS_TO_CONFIG = new ConcurrentHashMap<>();

    private ComponentNameResolver() {
        // Utility class
    }

    /**
     * Registers a ComponentRegistry for name resolution.
     *
     * @param indexName the index file name (e.g., "parsers", "detectors")
     * @param registry the registry to register
     */
    public static void registerRegistry(String indexName, ComponentRegistry registry) {
        REGISTRIES.put(indexName, registry);
    }

    /**
     * Resolves a friendly name or FQCN to a Class.
     * Searches all registered component registries, falling back to Class.forName.
     *
     * @param name friendly name or fully qualified class name
     * @param classLoader the class loader to use for FQCN fallback
     * @return the resolved class
     * @throws ClassNotFoundException if not found in any registry and not a valid FQCN
     */
    public static Class<?> resolveClass(String name, ClassLoader classLoader)
            throws ClassNotFoundException {
        for (ComponentRegistry registry : REGISTRIES.values()) {
            if (registry.hasComponent(name)) {
                try {
                    return registry.getComponentClass(name);
                } catch (TikaConfigException e) {
                    // continue to next registry
                }
            }
        }
        throw new ClassNotFoundException(
                "Component '" + name + "' is not registered. " +
                "Components must be registered via @TikaComponent annotation or .idx file. " +
                "Arbitrary class names are not allowed for security reasons.");
    }

    /**
     * Gets the friendly name for a class, or null if not registered.
     *
     * @param clazz the class to look up
     * @return the friendly name, or null if not found
     */
    public static String getFriendlyName(Class<?> clazz) {
        for (ComponentRegistry registry : REGISTRIES.values()) {
            String friendlyName = registry.getFriendlyName(clazz);
            if (friendlyName != null) {
                return friendlyName;
            }
        }
        return null;
    }

    /**
     * Checks if a component with the given name is registered in any registry.
     *
     * @param name the component name to check
     * @return true if the component is registered
     */
    public static boolean hasComponent(String name) {
        for (ComponentRegistry registry : REGISTRIES.values()) {
            if (registry.hasComponent(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the component info for a given friendly name.
     *
     * @param name the friendly name to look up
     * @return Optional containing the ComponentInfo, or empty if not found
     */
    public static Optional<ComponentInfo> getComponentInfo(String name) {
        for (ComponentRegistry registry : REGISTRIES.values()) {
            if (registry.hasComponent(name)) {
                try {
                    return Optional.of(registry.getComponentInfo(name));
                } catch (TikaConfigException e) {
                    // continue to next registry
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if any registered component implements or extends the given abstract type.
     * <p>
     * This is used by TikaModule to determine if an abstract type (interface or abstract class)
     * should use compact component serialization.
     *
     * @param abstractType the abstract type to check
     * @return true if at least one registered component is assignable to this type
     */
    public static boolean hasImplementationsOf(Class<?> abstractType) {
        for (ComponentRegistry registry : REGISTRIES.values()) {
            for (ComponentInfo info : registry.getAllComponents().values()) {
                if (abstractType.isAssignableFrom(info.componentClass())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== Component Config Methods ====================

    /**
     * Registers a ComponentConfig for top-level component loading.
     *
     * @param config the component configuration
     */
    public static <T> void registerComponentConfig(ComponentConfig<T> config) {
        FIELD_TO_CONFIG.put(config.getJsonField(), config);
        CLASS_TO_CONFIG.put(config.getComponentClass(), config);
    }

    /**
     * Gets component configuration by JSON field name.
     *
     * @param jsonField the JSON field name (e.g., "parsers")
     * @return the component config, or null if not registered
     */
    public static ComponentConfig<?> getComponentConfig(String jsonField) {
        return FIELD_TO_CONFIG.get(jsonField);
    }

    /**
     * Gets component configuration by component class.
     *
     * @param componentClass the component class (e.g., Parser.class)
     * @return the component config, or null if not registered
     */
    @SuppressWarnings("unchecked")
    public static <T> ComponentConfig<T> getComponentConfig(Class<T> componentClass) {
        return (ComponentConfig<T>) CLASS_TO_CONFIG.get(componentClass);
    }

    /**
     * Checks if a component config is registered for the given JSON field.
     */
    public static boolean hasComponentConfig(String jsonField) {
        return FIELD_TO_CONFIG.containsKey(jsonField);
    }

    /**
     * Checks if a component config is registered for the given class.
     */
    public static boolean hasComponentConfig(Class<?> componentClass) {
        return CLASS_TO_CONFIG.containsKey(componentClass);
    }

    /**
     * Gets all registered component JSON field names.
     */
    public static Set<String> getComponentFields() {
        return Collections.unmodifiableSet(FIELD_TO_CONFIG.keySet());
    }

    // ==================== Context Key Resolution Methods ====================

    /**
     * Returns the set of interfaces that use compact format serialization.
     *
     * @return unmodifiable set of context key interfaces
     */
    public static Set<Class<?>> getContextKeyInterfaces() {
        return Collections.unmodifiableSet(CONTEXT_KEY_INTERFACES);
    }

    /**
     * Finds the appropriate context key interface for a given type.
     * This is used to determine which interface should be used as the ParseContext key
     * when storing instances of this type.
     *
     * @param type the type to find the context key for
     * @return the interface to use as context key, or null if none found
     */
    public static Class<?> findContextKeyInterface(Class<?> type) {
        for (Class<?> iface : CONTEXT_KEY_INTERFACES) {
            if (iface.isAssignableFrom(type)) {
                return iface;
            }
        }
        return null;
    }

    /**
     * Checks if a type should use compact format serialization.
     * Returns true if the type implements any of the registered context key interfaces.
     *
     * @param type the type to check
     * @return true if the type uses compact format
     */
    public static boolean usesCompactFormat(Class<?> type) {
        return findContextKeyInterface(type) != null;
    }

    /**
     * Determines the ParseContext key for a component.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Explicit contextKey from .idx file (via @TikaComponent annotation)</li>
     *   <li>Auto-detect from implemented interfaces (using CONTEXT_KEY_INTERFACES)</li>
     *   <li>Fall back to the component class itself</li>
     * </ol>
     *
     * @param info the component info
     * @return the class to use as ParseContext key
     */
    public static Class<?> determineContextKey(ComponentInfo info) {
        if (info.contextKey() != null) {
            return info.contextKey();
        }
        Class<?> interfaceKey = findContextKeyInterface(info.componentClass());
        if (interfaceKey != null) {
            return interfaceKey;
        }
        return info.componentClass();
    }

    /**
     * Gets the contextKey for a class from the component registry.
     * The contextKey is recorded in the .idx file by the annotation processor.
     *
     * @param clazz the class to check
     * @return the contextKey class if specified, or null if not registered or no contextKey
     */
    public static Class<?> getContextKey(Class<?> clazz) {
        for (ComponentRegistry registry : REGISTRIES.values()) {
            String friendlyName = registry.getFriendlyName(clazz);
            if (friendlyName != null) {
                try {
                    ComponentInfo info = registry.getComponentInfo(friendlyName);
                    return info.contextKey();
                } catch (TikaConfigException e) {
                    // continue to next registry
                }
            }
        }
        return null;
    }
}
