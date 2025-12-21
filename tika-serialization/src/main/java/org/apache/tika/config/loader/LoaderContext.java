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
package org.apache.tika.config.loader;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.renderer.Renderer;

/**
 * Shared context passed to ComponentLoaders.
 * <p>
 * Provides access to dependencies and utilities without tight coupling.
 * Component loaders can use this to:
 * <ul>
 *   <li>Access the ClassLoader for SPI loading</li>
 *   <li>Use the ObjectMapper for JSON deserialization</li>
 *   <li>Get cross-component dependencies (e.g., EncodingDetector for parsers)</li>
 *   <li>Instantiate components via the shared ComponentInstantiator</li>
 * </ul>
 */
public class LoaderContext {

    private final ClassLoader classLoader;
    private final ObjectMapper objectMapper;
    private final DependencyProvider dependencyProvider;

    /**
     * Interface for lazy access to cross-component dependencies.
     * This avoids circular dependencies during loading.
     */
    @FunctionalInterface
    public interface DependencyProvider {
        <T> T get(Class<T> componentClass) throws TikaConfigException;
    }

    public LoaderContext(ClassLoader classLoader, ObjectMapper objectMapper,
                         DependencyProvider dependencyProvider) {
        this.classLoader = classLoader;
        this.objectMapper = objectMapper;
        this.dependencyProvider = dependencyProvider;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Get a dependency by class type.
     * Uses lazy loading to avoid circular dependencies.
     *
     * @param componentClass the component class to get
     * @return the component instance
     * @throws TikaConfigException if loading fails
     */
    public <T> T get(Class<T> componentClass) throws TikaConfigException {
        return dependencyProvider.get(componentClass);
    }

    /**
     * Get the EncodingDetector for injection into parsers.
     *
     * @return the encoding detector
     * @throws TikaConfigException if loading fails
     */
    public EncodingDetector getEncodingDetector() throws TikaConfigException {
        return get(EncodingDetector.class);
    }

    /**
     * Get the Renderer for injection into rendering parsers.
     *
     * @return the renderer
     * @throws TikaConfigException if loading fails
     */
    public Renderer getRenderer() throws TikaConfigException {
        return get(Renderer.class);
    }

    /**
     * Instantiate a component by name and config.
     *
     * @param name the component name (friendly name or FQCN)
     * @param configNode the JSON configuration for the component
     * @return the instantiated component
     * @throws TikaConfigException if instantiation fails
     */
    public <T> T instantiate(String name, JsonNode configNode) throws TikaConfigException {
        return ComponentInstantiator.instantiate(name, configNode, objectMapper, classLoader);
    }
}
