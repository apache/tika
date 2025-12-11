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

import org.apache.tika.config.ConfigContainer;

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
     * Map of objects in this context
     */
    private final Map<String, Object> context = new HashMap<>();

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
     * Adds a configuration by friendly name for serialization.
     * <p>
     * This is a convenience method for adding configs that will be serialized
     * and resolved at runtime. The config is stored in a {@link ConfigContainer}
     * and will be resolved to an actual object via the component registry.
     * <p>
     * Example:
     * <pre>
     * parseContext.addConfig("tika-task-timeout", "{\"timeoutMillis\": 5000}");
     * parseContext.addConfig("handler-config", "{\"type\": \"XML\", \"parseMode\": \"RMETA\"}");
     * </pre>
     *
     * @param key  the friendly name of the config (e.g., "tika-task-timeout", "handler-config")
     * @param json the JSON configuration string
     * @since Apache Tika 4.0
     */
    public void addConfig(String key, String json) {
        ConfigContainer container = get(ConfigContainer.class);
        if (container == null) {
            container = new ConfigContainer();
            set(ConfigContainer.class, container);
        }
        container.set(key, json);
    }

    public boolean isEmpty() {
        return context.isEmpty();
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
        return context.equals(that.context);
    }

    @Override
    public int hashCode() {
        return context.hashCode();
    }

    @Override
    public String toString() {
        return "ParseContext{" + "context=" + context + '}';
    }
}
