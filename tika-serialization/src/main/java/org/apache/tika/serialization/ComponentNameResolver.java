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
import java.util.TreeSet;
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

    /**
     * Subset of {@link #CONTEXT_KEY_INTERFACES} a request-supplied (wire) ParseContext may
     * instantiate and bind. Default-deny: a type is listed only if every registered implementation
     * is confined to transforming this request's metadata or shaping its output -- no exec, IO,
     * network, or control over which other components run. Enforced only on untrusted wire input;
     * trusted load-time config via TikaLoader is unrestricted.
     */
    private static final Set<Class<?>> WIRE_INSTANTIABLE_CONTEXT_KEYS = new HashSet<>();

    /**
     * Complement of {@link #WIRE_INSTANTIABLE_CONTEXT_KEYS}: context keys a wire ParseContext may
     * NOT instantiate (exec/IO/network or parse-graph control). Explicit so the exhaustiveness test
     * can assert every context-key interface is classified as exactly one of allowed/blocked.
     */
    private static final Set<Class<?>> WIRE_BLOCKED_CONTEXT_KEYS = new HashSet<>();

    static {
        // Allowed: bounded to this request's metadata/output; no exec or IO.
        WIRE_INSTANTIABLE_CONTEXT_KEYS.add(MetadataFilter.class);
        WIRE_INSTANTIABLE_CONTEXT_KEYS.add(ContentHandlerFactory.class);
        WIRE_INSTANTIABLE_CONTEXT_KEYS.add(ContentHandlerDecoratorFactory.class);
        WIRE_INSTANTIABLE_CONTEXT_KEYS.add(DigesterFactory.class);
        WIRE_INSTANTIABLE_CONTEXT_KEYS.add(MetadataWriteLimiterFactory.class);
        WIRE_INSTANTIABLE_CONTEXT_KEYS.add(UnpackSelector.class);

        // Blocked: exec / IO / network / parse-graph control.
        WIRE_BLOCKED_CONTEXT_KEYS.add(Parser.class);
        WIRE_BLOCKED_CONTEXT_KEYS.add(Detector.class);
        WIRE_BLOCKED_CONTEXT_KEYS.add(EncodingDetector.class);
        WIRE_BLOCKED_CONTEXT_KEYS.add(Renderer.class);
        WIRE_BLOCKED_CONTEXT_KEYS.add(Translator.class);
        WIRE_BLOCKED_CONTEXT_KEYS.add(EmbeddedDocumentExtractorFactory.class);
    }

    private static final Map<String, ComponentRegistry> REGISTRIES = new ConcurrentHashMap<>();

    // Component configuration storage (keyed by component class)
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
        throw new ClassNotFoundException(unregisteredMessage(name));
    }

    /**
     * Builds a diagnostic message for an unregistered component name. It calls out the
     * two usual causes -- the name is misspelled, or the module that provides it is not
     * on the classpath (optional components such as the Tess4J OCR parser ship in
     * separate jars that must be added explicitly) -- and lists the names that
     * <em>are</em> registered so the caller can find the right one (or notice that
     * nothing registered, which means no {@code .idx} files were on the classpath).
     */
    private static String unregisteredMessage(String name) {
        TreeSet<String> known = new TreeSet<>();
        for (ComponentRegistry registry : REGISTRIES.values()) {
            known.addAll(registry.getAllComponents().keySet());
        }
        StringBuilder sb = new StringBuilder()
                .append("Component '").append(name).append("' is not registered. ")
                .append("Either the name is misspelled, or the module that provides it is ")
                .append("not on the classpath -- optional components (for example the Tess4J ")
                .append("OCR parser in tika-parser-tess4j-module) ship as separate jars that ")
                .append("must be added explicitly. ");
        if (known.isEmpty()) {
            sb.append("No components are currently registered "
                    + "(no META-INF/tika/*.idx files were found on the classpath). ");
        } else {
            sb.append(known.size()).append(" registered component(s): ");
            int shown = 0;
            int cap = 50;
            for (String registered : known) {
                if (shown == cap) {
                    sb.append(", ...");
                    break;
                }
                if (shown > 0) {
                    sb.append(", ");
                }
                sb.append(registered);
                shown++;
            }
            sb.append(". ");
        }
        sb.append("Arbitrary class names are not allowed for security reasons.");
        return sb.toString();
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
        CLASS_TO_CONFIG.put(config.getComponentClass(), config);
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

    // ==================== Context Key Resolution Methods ====================

    /**
     * Returns the set of interfaces that use compact format serialization.
     *
     * @return unmodifiable set of context key interfaces
     */
    public static Set<Class<?>> getContextKeyInterfaces() {
        return Collections.unmodifiableSet(CONTEXT_KEY_INTERFACES);
    }

    /** Wire-instantiable context-key interfaces; see {@link #WIRE_INSTANTIABLE_CONTEXT_KEYS}. */
    public static Set<Class<?>> getWireInstantiableContextKeys() {
        return Collections.unmodifiableSet(WIRE_INSTANTIABLE_CONTEXT_KEYS);
    }

    /** True if a wire ParseContext may bind this context-key type (default-deny). */
    public static boolean isWireInstantiable(Class<?> contextKey) {
        return WIRE_INSTANTIABLE_CONTEXT_KEYS.contains(contextKey);
    }

    /**
     * True if a wire ParseContext must NOT instantiate this context-key type. Fail-closed: any
     * context-key interface not on the wire allowlist is blocked, so a newly-added one is refused
     * until consciously allow-listed. Non-component keys (plain config DTOs) are never blocked.
     */
    public static boolean isWireBlocked(Class<?> contextKey) {
        return CONTEXT_KEY_INTERFACES.contains(contextKey)
                && !WIRE_INSTANTIABLE_CONTEXT_KEYS.contains(contextKey);
    }

    /** Explicitly wire-blocked context-key interfaces; complement of the wire allowlist. */
    public static Set<Class<?>> getWireBlockedContextKeys() {
        return Collections.unmodifiableSet(WIRE_BLOCKED_CONTEXT_KEYS);
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
