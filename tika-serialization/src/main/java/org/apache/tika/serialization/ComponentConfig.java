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

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.tika.config.loader.ComponentLoader;

/**
 * Configuration for how to load a top-level component from JSON.
 * <p>
 * Specifies:
 * <ul>
 *   <li>The JSON field name (e.g., "parsers", "detectors")</li>
 *   <li>Whether to load as a list or single object</li>
 *   <li>How to wrap a list into a composite object (e.g., List&lt;Parser&gt; → CompositeParser)</li>
 *   <li>What default to return if the field is absent</li>
 *   <li>Optional custom loader for complex components</li>
 * </ul>
 *
 * @param <T> the component type (e.g., Parser, Detector)
 */
public class ComponentConfig<T> {

    private final String jsonField;
    private final Class<T> componentClass;
    private final boolean loadAsList;
    private final Function<List<?>, T> listWrapper;
    private final Supplier<T> defaultProvider;
    private final ComponentLoader<T> customLoader;

    private ComponentConfig(Builder<T> builder) {
        this.jsonField = builder.jsonField;
        this.componentClass = builder.componentClass;
        this.loadAsList = builder.loadAsList;
        this.listWrapper = builder.listWrapper;
        this.defaultProvider = builder.defaultProvider;
        this.customLoader = builder.customLoader;
    }

    public String getJsonField() {
        return jsonField;
    }

    public Class<T> getComponentClass() {
        return componentClass;
    }

    public boolean isLoadAsList() {
        return loadAsList;
    }

    @SuppressWarnings("unchecked")
    public T wrapList(List<?> list) {
        if (listWrapper == null) {
            throw new IllegalStateException("No list wrapper configured for " + jsonField);
        }
        return listWrapper.apply(list);
    }

    public boolean hasListWrapper() {
        return listWrapper != null;
    }

    public T getDefault() {
        return defaultProvider != null ? defaultProvider.get() : null;
    }

    public boolean hasDefault() {
        return defaultProvider != null;
    }

    /**
     * Returns true if this component has a custom loader.
     */
    public boolean hasCustomLoader() {
        return customLoader != null;
    }

    /**
     * Gets the custom loader for this component.
     */
    public ComponentLoader<T> getCustomLoader() {
        return customLoader;
    }

    /**
     * Creates a new builder for ComponentConfig.
     *
     * @param jsonField the JSON field name (e.g., "parsers")
     * @param componentClass the component interface (e.g., Parser.class)
     * @return a new builder
     */
    public static <T> Builder<T> builder(String jsonField, Class<T> componentClass) {
        return new Builder<>(jsonField, componentClass);
    }

    /**
     * Builder for ComponentConfig.
     */
    public static class Builder<T> {
        private final String jsonField;
        private final Class<T> componentClass;
        private boolean loadAsList = false;
        private Function<List<?>, T> listWrapper;
        private Supplier<T> defaultProvider;
        private ComponentLoader<T> customLoader;

        Builder(String jsonField, Class<T> componentClass) {
            this.jsonField = jsonField;
            this.componentClass = componentClass;
        }

        /**
         * Configure this component to be loaded as a list from JSON.
         */
        public Builder<T> loadAsList() {
            this.loadAsList = true;
            return this;
        }

        /**
         * Configure how to wrap a list into the component type.
         * For example, List&lt;Parser&gt; → CompositeParser.
         *
         * @param wrapper function that takes a list and returns the wrapped component
         */
        public Builder<T> wrapWith(Function<List<?>, T> wrapper) {
            this.listWrapper = wrapper;
            return this;
        }

        /**
         * Configure a default value to return when the JSON field is absent.
         *
         * @param provider supplier that creates the default instance
         */
        public Builder<T> defaultProvider(Supplier<T> provider) {
            this.defaultProvider = provider;
            return this;
        }

        /**
         * Configure a custom loader for complex components that need
         * special handling (SPI fallback, dependency injection, etc.).
         * <p>
         * When a custom loader is set, it takes precedence over the
         * default list-based loading.
         *
         * @param loader the custom loader
         */
        public Builder<T> customLoader(ComponentLoader<T> loader) {
            this.customLoader = loader;
            return this;
        }

        /**
         * Build the ComponentConfig.
         */
        public ComponentConfig<T> build() {
            return new ComponentConfig<>(this);
        }

        /**
         * Build and register with ComponentNameResolver.
         */
        public void register() {
            ComponentNameResolver.registerComponentConfig(new ComponentConfig<>(this));
        }
    }
}
