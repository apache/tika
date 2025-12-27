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

import org.apache.tika.exception.TikaConfigException;

/**
 * Strategy interface for loading components from JSON config.
 * <p>
 * Implementations handle component-specific concerns like:
 * <ul>
 *   <li>Dependency injection (EncodingDetector into parsers)</li>
 *   <li>Decorations (mime filtering)</li>
 *   <li>SPI fallback with exclusions</li>
 * </ul>
 *
 * @param <T> the component type (e.g., Parser, Detector, EncodingDetector)
 */
@FunctionalInterface
public interface ComponentLoader<T> {

    /**
     * Load components from the JSON config.
     *
     * @param config the JSON configuration
     * @param context shared context with dependencies and utilities
     * @return the loaded component (typically a composite)
     * @throws TikaConfigException if loading fails
     */
    T load(TikaJsonConfig config, LoaderContext context) throws TikaConfigException;
}
