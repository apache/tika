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
package org.apache.tika.pipes.core;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.api.ComponentIds;
import org.apache.tika.pipes.core.config.ConfigStore;
import org.apache.tika.pipes.core.config.InMemoryConfigStore;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.PluginComponentLoader;
import org.apache.tika.plugins.TikaExtension;
import org.apache.tika.plugins.TikaExtensionFactory;

/**
 * Abstract base class for managing Tika components (Fetchers, Emitters, etc.).
 * Provides lazy instantiation, early validation, and optional runtime modifications.
 * <p>
 * The manager owns every instance it builds. Callers {@link #acquire(String)} a {@link Lease}
 * and close it when done. A runtime save or delete retires the instance it replaces: no new
 * lease can get it, and it is closed (if it implements {@link AutoCloseable}) when its last
 * lease is released, at once if none is open. {@link #close()} retires every instance.
 *
 * @param <T> the component type (e.g., Fetcher, Emitter)
 * @param <F> the factory type for creating components
 */
public abstract class AbstractComponentManager<T extends TikaExtension,
                                                F extends TikaExtensionFactory<T>> implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractComponentManager.class);

    protected final PluginManager pluginManager;
    // from the config file: never written to the store, so a persistent store holds no resolved
    // ${env:...} secrets, and never replaced or deleted at runtime
    private final Map<String, ExtensionConfig> configured;
    // runtime registrations only
    private final ConfigStore configStore;
    private final Map<String, Holder<T>> holders = new ConcurrentHashMap<>();
    private final boolean allowRuntimeModifications;
    private volatile boolean closed = false;

    protected AbstractComponentManager(PluginManager pluginManager,
                                      Map<String, ExtensionConfig> componentConfigs,
                                      boolean allowRuntimeModifications) {
        this(pluginManager, componentConfigs, allowRuntimeModifications, new InMemoryConfigStore());
    }

    protected AbstractComponentManager(PluginManager pluginManager,
                                      Map<String, ExtensionConfig> componentConfigs,
                                      boolean allowRuntimeModifications,
                                      ConfigStore configStore) {
        this.pluginManager = pluginManager;
        this.configStore = configStore;
        try {
            configStore.init();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ConfigStore", e);
        }
        this.configured = Map.copyOf(componentConfigs);
        for (String id : configured.keySet()) {
            if (configStore.containsKey(id)) {
                // most likely a copy an older version persisted; it may hold resolved secrets
                LOG.warn("The config store holds a {} '{}' that the config file also defines; the "
                        + "config file's wins. Remove it from the store.", getComponentName(), id);
            }
        }
        this.allowRuntimeModifications = allowRuntimeModifications;
    }

    /**
     * Returns the JSON configuration key for this component type (e.g., "fetchers", "emitters").
     */
    protected abstract String getConfigKey();

    /**
     * Returns the config store used by this manager.
     * Useful for subclasses that need direct access to the store.
     */
    protected ConfigStore getConfigStore() {
        return configStore;
    }

    /**
     * Returns the factory class for this component type.
     */
    protected abstract Class<F> getFactoryClass();

    /**
     * Returns the component name for error messages (e.g., "fetcher", "emitter").
     */
    protected abstract String getComponentName();

    /**
     * Creates a not-found exception for this component type.
     */
    protected abstract TikaException createNotFoundException(String message);

    /**
     * Validates the configuration and collects component configs without instantiating.
     */
    protected Map<String, ExtensionConfig> validateAndCollectConfigs(
            PluginManager pluginManager, JsonNode configNode) throws TikaConfigException, IOException {

        Map<String, F> factories = getFactories(pluginManager);
        Map<String, ExtensionConfig> configs = new HashMap<>();

        if (configNode != null && !configNode.isNull()) {
            // Strict shape check. The section must be a JSON object keyed by
            // instance ID — e.g. {"my-fetcher": {"file-system-fetcher": {...}}}.
            // Without this, an array like
            //   "fetchers": [{"file-system-fetcher": {"id": "my-fetcher", ...}}]
            // would be silently walked past (JsonNode.fields() on an ArrayNode
            // returns an empty iterator), leaving the manager with no registered
            // components and the user with an "Available: []" error at lookup
            // time instead of at load time.
            if (!configNode.isObject()) {
                throw new TikaConfigException(
                        "Invalid '" + getConfigKey() + "' configuration: expected a JSON "
                                + "object keyed by instance ID, e.g. {\"my-id\": {\"type-name\": "
                                + "{...config...}}}. Got " + configNode.getNodeType() + ". "
                                + "(Array-style configurations are not supported.)");
            }
            // Outer loop: iterate over instance IDs
            Iterator<Map.Entry<String, JsonNode>> instanceFields = configNode.fields();
            while (instanceFields.hasNext()) {
                Map.Entry<String, JsonNode> instanceEntry = instanceFields.next();
                // Normalize before validating and storing: {"foo": ...} and {"foo ": ...} are two
                // distinct keys in valid JSON, and validating the raw id while resolving the
                // trimmed one would let " __foo" past the reserved-namespace check.
                // __ is permitted here -- ConfigMerger folds host-injected overrides into the same
                // JSON before this runs, and those legitimately use it.
                String instanceId;
                try {
                    instanceId = ComponentIds.requireLegalId(instanceEntry.getKey(),
                            getComponentName(), "configuration");
                } catch (IllegalArgumentException e) {
                    throw new TikaConfigException(e.getMessage(), e);
                }
                JsonNode typeNode = instanceEntry.getValue();

                if (configs.containsKey(instanceId)) {
                    throw new TikaConfigException("Duplicate " + getComponentName() +
                            " id: " + instanceId);
                }

                // Inner loop: extract the type name
                // The structure should be: { "type-name": { config } }
                // We expect exactly ONE type per instance
                Iterator<Map.Entry<String, JsonNode>> typeFields = typeNode.fields();

                if (!typeFields.hasNext()) {
                    throw new TikaConfigException(
                            "Invalid " + getComponentName() + " configuration for id '" + instanceId +
                            "': missing type specification. Expected format: {\"" + instanceId +
                            "\": {\"type-name\": {...}}}");
                }

                Map.Entry<String, JsonNode> typeEntry = typeFields.next();
                String typeName = typeEntry.getKey();
                JsonNode config = typeEntry.getValue();

                // Validate that there's only one type per instance
                if (typeFields.hasNext()) {
                    Map.Entry<String, JsonNode> extraTypeEntry = typeFields.next();
                    throw new TikaConfigException(
                            "Invalid " + getComponentName() + " configuration for id '" + instanceId +
                            "': multiple types specified ('" + typeName + "', '" +
                            extraTypeEntry.getKey() + "'). Each instance can only have one type.");
                }

                // Validate that factory exists for this type
                F factory = factories.get(typeName);
                if (factory == null) {
                    throw new TikaConfigException(
                            "Unknown " + getComponentName() + " type: '" + typeName +
                            "' for instance id '" + instanceId + "'. Available types: " +
                            factories.keySet());
                }

                configs.put(instanceId, new ExtensionConfig(instanceId, typeName,
                        toJsonString(config)));
            }
        }

        return configs;
    }

    protected Map<String, F> getFactories(PluginManager pluginManager) throws TikaConfigException {
        if (pluginManager.getStartedPlugins().isEmpty()) {
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
        }

        Map<String, F> factories = new HashMap<>();
        for (F factory : pluginManager.getExtensions(getFactoryClass())) {
            F existing = factories.get(factory.getName());
            // a plugin wins over the same factory found on the classpath (tika.plugins.classpath)
            if (existing == null || (isFromPlugin(factory) && !isFromPlugin(existing))) {
                factories.put(factory.getName(), factory);
            }
        }
        return factories;
    }

    private static boolean isFromPlugin(Object factory) {
        return factory.getClass().getClassLoader() instanceof org.pf4j.PluginClassLoader;
    }

    private static String toJsonString(final JsonNode node) throws TikaConfigException {
        try {
            return TikaObjectMapperFactory.getMapper().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException("Failed to serialize config to JSON string", e);
        }
    }

    /**
     * Leases a component by ID, lazily instantiating it if needed. Close the lease when done
     * with the component and with everything it returned.
     */
    public Lease<T> acquire(String id) throws IOException, TikaException {
        // Canonicalize on the way in so lookup agrees with what load() stored. __ is not refused
        // here: the host's own components resolve through this method.
        id = ComponentIds.normalize(id);
        while (true) {
            Holder<T> holder = holders.get(id);
            if (holder == null) {
                holder = build(id);
            }
            Lease<T> lease = holder.tryAcquire();
            if (lease != null) {
                return lease;
            }
            // retired between lookup and acquire: build the replacement
        }
    }

    private synchronized Holder<T> build(String id) throws IOException, TikaException {
        if (closed) {
            throw new IllegalStateException(getClass().getSimpleName() + " is closed");
        }
        Holder<T> holder = holders.get(id);
        if (holder != null) {
            return holder;
        }
        ExtensionConfig config = lookup(id);
        if (config == null) {
            throw createNotFoundException(
                    "Can't find " + getComponentName() + " for id=" + id +
                    ". Available: " + getSupported());
        }
        try {
            holder = new Holder<>(buildComponent(config), getComponentName(), id);
        } catch (TikaConfigException e) {
            throw new IOException("Failed to build " + getComponentName() + ": " + id, e);
        }
        holders.put(id, holder);
        LOG.debug("Lazily instantiated {}: {}", getComponentName(), id);
        return holder;
    }

    private synchronized void retire(String id) {
        Holder<T> holder = holders.remove(id);
        if (holder != null) {
            holder.retire();
        }
    }

    /**
     * Retires every instance: each is closed now if no lease is open, else when its last lease
     * is released. Later calls to {@link #acquire(String)} fail.
     */
    @Override
    public synchronized void close() {
        closed = true;
        for (String id : Set.copyOf(holders.keySet())) {
            retire(id);
        }
    }

    private ExtensionConfig lookup(String id) {
        ExtensionConfig config = configured.get(id);
        return config != null ? config : configStore.get(id);
    }

    private void requireNotConfigured(String componentId) throws TikaConfigException {
        if (configured.containsKey(componentId)) {
            throw new TikaConfigException(getComponentName().substring(0, 1).toUpperCase(Locale.ROOT)
                    + getComponentName().substring(1) + " '" + componentId
                    + "' is defined in the server config; change it there");
        }
    }

    /**
     * Builds a component instance from its configuration.
     */
    private T buildComponent(ExtensionConfig config) throws TikaConfigException, IOException {
        Map<String, F> factories = getFactories(pluginManager);
        F factory = factories.get(config.name());

        if (factory == null) {
            // This shouldn't happen since we validated in load(), but check anyway
            throw new TikaConfigException(
                    "Unknown " + getComponentName() + " type: " + config.name() +
                    ". Available: " + factories.keySet());
        }

        return PluginComponentLoader.buildExtension(factory, config);
    }

    /**
     * Dynamically adds or updates a component configuration at runtime.
     * The component will not be instantiated until it is first requested via {@link #acquire(String)}.
     * If a component with the same ID already exists, it will be replaced and its instance retired.
     * A component defined in the server config cannot be replaced.
     * <p>
     * This method is only available if the manager was loaded with allowRuntimeModifications=true.
     * <p>
     * Only authorized/authenticated users should be allowed to modify components. BE CAREFUL.
     *
     * @param config the extension configuration for the component
     * @throws TikaConfigException if the component type is unknown or if runtime modifications are not allowed
     * @throws IOException if there is an error accessing the plugin manager
     */
    public synchronized void saveComponent(ExtensionConfig config) throws TikaConfigException, IOException {
        if (!allowRuntimeModifications) {
            throw new TikaConfigException(
                    "Runtime modifications are not allowed. " + getClass().getSimpleName() +
                    " must be loaded with allowRuntimeModifications=true to use save" +
                    getComponentName().substring(0, 1).toUpperCase(Locale.ROOT) + getComponentName().substring(1) + "()");
        }

        if (config == null) {
            throw new IllegalArgumentException("ExtensionConfig cannot be null");
        }

        // Runtime registration is remote input (grpc saveFetcher), so unlike config load it must
        // not be able to define or replace a host component.
        String componentId;
        try {
            componentId = ComponentIds.requireUserId(config.id(), getComponentName(),
                    "a runtime registration");
        } catch (IllegalArgumentException e) {
            throw new TikaConfigException(e.getMessage(), e);
        }
        requireNotConfigured(componentId);
        config = new ExtensionConfig(componentId, config.name(), config.json());
        String typeName = config.name();

        // Validate that factory exists for this type
        Map<String, F> factories = getFactories(pluginManager);
        if (!factories.containsKey(typeName)) {
            throw new TikaConfigException(
                    "Unknown " + getComponentName() + " type: " + typeName +
                    ". Available: " + factories.keySet());
        }

        if (configStore.containsKey(componentId)) {
            LOG.debug("Updating existing {} config: id={}, type={}", getComponentName(), componentId, typeName);
        } else {
            LOG.debug("Creating new {} config: id={}, type={}", getComponentName(), componentId, typeName);
        }

        // Store config without instantiating
        configStore.put(componentId, config);
        retire(componentId);
    }

    /**
     * Deletes a component configuration by ID.
     * Removes the configuration and retires the instance.
     *
     * @param componentId the component ID to delete
     * @throws TikaConfigException if runtime modifications are not allowed, the component is not
     *         found, or it is defined in the server config
     */
    public synchronized void deleteComponent(String componentId) throws TikaConfigException {
        if (!allowRuntimeModifications) {
            throw new TikaConfigException(
                    "Runtime modifications are not allowed. " + getClass().getSimpleName() +
                    " must be loaded with allowRuntimeModifications=true to use delete" +
                    getComponentName().substring(0, 1).toUpperCase(Locale.ROOT) + getComponentName().substring(1) + "()");
        }

        if (componentId == null) {
            throw new IllegalArgumentException("Component ID cannot be null");
        }
        // As in saveComponent: a remote caller must not delete a host component out from under
        // an in-flight request.
        try {
            componentId = ComponentIds.requireUserId(componentId, getComponentName(),
                    "a runtime deletion");
        } catch (IllegalArgumentException e) {
            throw new TikaConfigException(e.getMessage(), e);
        }
        requireNotConfigured(componentId);

        if (!configStore.containsKey(componentId)) {
            throw new TikaConfigException(
                    getComponentName().substring(0, 1).toUpperCase(Locale.ROOT) + 
                    getComponentName().substring(1) + " with ID '" + componentId + "' not found");
        }

        configStore.remove(componentId);
        retire(componentId);
        LOG.debug("Deleted {} config: id={}", getComponentName(), componentId);
    }

    /**
     * Gets the configuration for a specific component by ID.
     *
     * @param componentId the component ID
     * @return the component configuration, or null if not found
     */
    public ExtensionConfig getComponentConfig(String componentId) {
        return lookup(ComponentIds.normalize(componentId));
    }

    /**
     * Returns the component IDs a caller may name. Host components are omitted: a request cannot
     * bind them, so listing them in a "not found" message only advertises internals.
     */
    public Set<String> getSupported() {
        return getAllIds().stream()
                .filter(id -> !ComponentIds.isSystem(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Every configured id, host components included. For lifecycle and existence checks, not for
     * anything a caller sees.
     */
    public Set<String> getAllIds() {
        Set<String> ids = new LinkedHashSet<>(configured.keySet());
        ids.addAll(configStore.keySet());
        return ids;
    }

    /**
     * Convenience method that leases a component if only one component
     * is configured. If 0 or > 1 components are configured, this throws an IllegalArgumentException.
     *
     * @return a lease on the single configured component
     */
    public Lease<T> acquire() throws IOException, TikaException {
        Set<String> ids = getAllIds();
        if (ids.size() != 1) {
            throw new IllegalArgumentException(
                    "No-arg get" + getComponentName().substring(0, 1).toUpperCase(Locale.ROOT) +
                    getComponentName().substring(1) + "() requires exactly 1 configured " +
                    getComponentName() + ". Found: " + ids.size() + " (" + ids + ")");
        }
        return acquire(ids.iterator().next());
    }

    /**
     * One built instance. Closed exactly once, when it is retired and no lease is open,
     * whichever comes last.
     */
    private static final class Holder<T> {
        private final T component;
        private final String componentName;
        private final String id;
        private int refs = 0;
        private boolean retired = false;

        Holder(T component, String componentName, String id) {
            this.component = component;
            this.componentName = componentName;
            this.id = id;
        }

        synchronized Lease<T> tryAcquire() {
            if (retired) {
                return null;
            }
            refs++;
            return new Lease<>(component, this::release);
        }

        private void release() {
            synchronized (this) {
                if (--refs > 0 || !retired) {
                    return;
                }
            }
            closeComponent();
        }

        void retire() {
            synchronized (this) {
                if (retired) {
                    return;
                }
                retired = true;
                if (refs > 0) {
                    return;
                }
            }
            closeComponent();
        }

        private void closeComponent() {
            if (component instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                    LOG.debug("Closed {}: {}", componentName, id);
                } catch (Exception e) {
                    LOG.warn("Failed to close {} '{}'", componentName, id, e);
                }
            }
        }
    }
}
