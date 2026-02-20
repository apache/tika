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

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.writefilter.MetadataWriteLimiterFactory;

/**
 * Parse context. Used to pass context information to Tika parsers.
 *
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-275">TIKA-275</a>
 * @since Apache Tika 0.5
 */
public class ParseContext implements Serializable {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = -5921436862145826534L;

    /**
     * Map of typed objects in this context, keyed by class name.
     */
    private final Map<String, Object> context = new HashMap<>();

    /**
     * Map of JSON configs, keyed by component name (e.g., "pdf-parser").
     * This is the source of truth for round-trip serialization.
     * Using JsonConfig interface allows for future extension with metadata.
     */
    private final Map<String, JsonConfig> jsonConfigs = new HashMap<>();

    /**
     * Cache of resolved objects from jsonConfigs, keyed by component name.
     * This is ignored during serialization to preserve round-trip fidelity.
     * Note: Not final because Java serialization bypasses constructor initialization.
     */
    private transient Map<String, Object> resolvedConfigs = new HashMap<>();

    /**
     * Adds the given value to the context as an implementation of the given
     * interface.
     *
     * @param key   the interface implemented by the given value
     * @param value the value to be added, or <code>null</code> to remove
     */
    public <T> void set(Class<T> key, T value) {
        if (value != null) {
            context.put(key.getName(), value);
        } else {
            context.remove(key.getName());
        }
    }

    /**
     * Returns the object in this context that implements the given interface.
     *
     * @param key the interface implemented by the requested object
     * @return the object that implements the given interface,
     * or <code>null</code> if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> key) {
        return (T) context.get(key.getName());
    }

    /**
     * Returns the object in this context that implements the given interface,
     * or the given default value if such an object is not found.
     *
     * @param key          the interface implemented by the requested object
     * @param defaultValue value to return if the requested object is not found
     * @return the object that implements the given interface,
     * or the given default value if not found
     */
    public <T> T get(Class<T> key, T defaultValue) {
        T value = get(key);
        if (value != null) {
            return value;
        } else {
            return defaultValue;
        }
    }

    /**
     * Sets a JSON configuration by component name.
     * <p>
     * This stores the JSON config for later resolution. The JSON will be
     * deserialized when requested via the component registry in tika-serialization.
     * <p>
     * Example:
     * <pre>
     * parseContext.setJsonConfig("pdf-parser", () -&gt; "{\"ocrStrategy\": \"AUTO\"}");
     * parseContext.setJsonConfig("handler-config", () -&gt; "{\"type\": \"XML\"}");
     * </pre>
     *
     * @param name   the component name (e.g., "pdf-parser", "handler-config")
     * @param config the JSON configuration
     * @since Apache Tika 4.0
     */
    public void setJsonConfig(String name, JsonConfig config) {
        if (config != null) {
            jsonConfigs.put(name, config);
        } else {
            jsonConfigs.remove(name);
            if (resolvedConfigs != null) {
                resolvedConfigs.remove(name);
            }
        }
    }

    /**
     * Sets a JSON configuration by component name using a raw JSON string.
     * <p>
     * Convenience method that wraps the string in a JsonConfig.
     *
     * @param name the component name (e.g., "pdf-parser", "handler-config")
     * @param json the JSON configuration string
     * @since Apache Tika 4.0
     */
    public void setJsonConfig(String name, String json) {
        setJsonConfig(name, json != null ? new StringJsonConfig(json) : null);
    }

    /**
     * A simple Serializable implementation of JsonConfig that holds a JSON string.
     * This is used internally to ensure JSON configs can be serialized via Java serialization.
     */
    private record StringJsonConfig(String json) implements JsonConfig, Serializable {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Gets a JSON configuration by component name.
     *
     * @param name the component name
     * @return the JsonConfig, or null if not found
     * @since Apache Tika 4.0
     */
    public JsonConfig getJsonConfig(String name) {
        return jsonConfigs.get(name);
    }

    /**
     * Returns all JSON configurations for serialization.
     *
     * @return unmodifiable map of component name to JsonConfig
     * @since Apache Tika 4.0
     */
    public Map<String, JsonConfig> getJsonConfigs() {
        return Collections.unmodifiableMap(jsonConfigs);
    }

    /**
     * Gets a resolved configuration object from the cache.
     * <p>
     * This is used by tika-serialization after deserializing a JSON config.
     * The resolved object is cached here to avoid repeated deserialization.
     *
     * @param name the component name
     * @return the resolved object, or null if not cached
     * @since Apache Tika 4.0
     */
    @SuppressWarnings("unchecked")
    public <T> T getResolvedConfig(String name) {
        if (resolvedConfigs == null) {
            return null;
        }
        return (T) resolvedConfigs.get(name);
    }

    /**
     * Caches a resolved configuration object.
     * <p>
     * Called by tika-serialization after deserializing a JSON config.
     *
     * @param name   the component name
     * @param config the resolved configuration object
     * @since Apache Tika 4.0
     */
    public void setResolvedConfig(String name, Object config) {
        if (resolvedConfigs == null) {
            resolvedConfigs = new HashMap<>();
        }
        if (config != null) {
            resolvedConfigs.put(name, config);
        } else {
            resolvedConfigs.remove(name);
        }
    }

    /**
     * Checks if a JSON configuration exists for the given component name.
     *
     * @param name the component name
     * @return true if a JSON config exists
     * @since Apache Tika 4.0
     */
    public boolean hasJsonConfig(String name) {
        return jsonConfigs.containsKey(name);
    }

    public boolean isEmpty() {
        return context.isEmpty() && jsonConfigs.isEmpty();
    }

    /**
     * Copies all entries from the source ParseContext into this one.
     * Existing entries in this context are overwritten by source entries.
     * <p>
     * This copies both typed objects (from context map) and JSON configs.
     *
     * @param source the ParseContext to copy from
     * @since Apache Tika 4.0
     */
    public void copyFrom(ParseContext source) {
        if (source == null) {
            return;
        }
        // Copy typed objects
        context.putAll(source.context);
        // Copy JSON configs, invalidating stale resolved state for overridden keys.
        // When a source jsonConfig overrides an existing entry, the previously resolved
        // object is stale and must be cleared so resolveAll() will re-resolve from the
        // new JSON config.
        for (Map.Entry<String, JsonConfig> entry : source.jsonConfigs.entrySet()) {
            String key = entry.getKey();
            jsonConfigs.put(key, entry.getValue());
            if (resolvedConfigs != null) {
                resolvedConfigs.remove(key);
            }
        }
        // Copy resolved configs from source (if any)
        if (source.resolvedConfigs != null && !source.resolvedConfigs.isEmpty()) {
            if (resolvedConfigs == null) {
                resolvedConfigs = new HashMap<>();
            }
            resolvedConfigs.putAll(source.resolvedConfigs);
        }
    }

    /**
     * Creates a new Metadata object with any configured limits applied.
     * <p>
     * If a {@link MetadataWriteLimiterFactory} is configured in this ParseContext, the returned
     * Metadata will have a write limiter that enforces those limits. Otherwise,
     * returns a plain Metadata object.
     * <p>
     * Parsers should use this method instead of {@code new Metadata()} when creating
     * metadata for embedded documents, to ensure limits are applied at creation time
     * rather than later during parsing.
     * <p>
     * Example usage:
     * <pre>
     * Metadata embeddedMetadata = Metadata.newInstance(context);
     * embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
     * // limits are already applied, no data bypasses the limiter
     * </pre>
     *
     * @return a new Metadata object, with limits applied if configured
     * @since Apache Tika 4.0
     * @see Metadata#newInstance(ParseContext)
     */
    public Metadata newMetadata() {
        return Metadata.newInstance(this);
    }


    /**
     * Returns the internal context map for serialization purposes.
     * The returned map is unmodifiable.
     * <p>
     * This method is intended for use by serialization frameworks only.
     * Keys are fully-qualified class names, values are the objects stored in the context.
     *
     * @return an unmodifiable view of the context map
     * @since Apache Tika 4.0
     */
    public Map<String, Object> getContextMap() {
        return Collections.unmodifiableMap(context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ParseContext that = (ParseContext) o;
        if (!context.equals(that.context)) {
            return false;
        }
        return jsonConfigs.equals(that.jsonConfigs);
    }

    @Override
    public int hashCode() {
        int result = context.hashCode();
        result = 31 * result + jsonConfigs.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ParseContext{" +
                "context=" + context +
                ", jsonConfigs=" + jsonConfigs +
                '}';
    }
}
