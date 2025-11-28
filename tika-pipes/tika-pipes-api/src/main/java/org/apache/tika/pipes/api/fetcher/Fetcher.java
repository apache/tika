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
package org.apache.tika.pipes.api.fetcher;

import java.io.IOException;
import java.io.InputStream;

import org.pf4j.ExtensionPoint;

import org.apache.tika.config.ComponentConfigs;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.plugins.TikaExtension;

/**
 * Interface for an object that will fetch an InputStream given
 * a fetch string. This will also update the metadata object
 * based on the fetch.
 * <p>
 * Implementations of Fetcher must be thread safe.
 * <p>
 * The {@code componentConfigs} parameter contains component-specific JSON
 * configuration strings that can be used for runtime configuration overrides.
 * Fetchers can extract their specific configuration using their component name.
 */
public interface Fetcher extends TikaExtension, ExtensionPoint {

    /**
     * Fetches an InputStream for the given fetch key.
     *
     * @param fetchKey the key identifying what to fetch
     * @param metadata metadata object to populate with fetch-related metadata
     * @param componentConfigs component-specific configurations for runtime overrides
     * @return the fetched input stream
     * @throws TikaException if there's an error during fetching
     * @throws IOException if there's an I/O error
     */
    InputStream fetch(String fetchKey, Metadata metadata, ComponentConfigs componentConfigs) throws TikaException, IOException;
}
