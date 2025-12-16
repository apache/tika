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

import org.pf4j.ExtensionPoint;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.plugins.TikaExtension;

/**
 * Interface for an object that will fetch a TikaInputStream given
 * a fetch string.  This will also update the metadata object
 * based on the fetch.
 * <p>
 * Implementations of Fetcher must be thread safe.
 */
public interface Fetcher extends TikaExtension, ExtensionPoint {

    /**
     * Fetches a resource and returns it as a TikaInputStream.
     *
     * @param fetchKey the key identifying the resource to fetch (interpretation
     *                 depends on the implementation, e.g., file path, URL, S3 key)
     * @param metadata metadata object to be updated with resource information
     * @param parseContext the parse context
     * @return a TikaInputStream for reading the resource content
     * @throws TikaException if a Tika-specific error occurs during fetching
     * @throws IOException if an I/O error occurs during fetching
     * @throws SecurityException if the fetchKey attempts to access a resource
     *         outside permitted boundaries (e.g., path traversal attack)
     * @throws IllegalArgumentException if the fetchKey contains invalid characters
     *         (e.g., null bytes)
     */
    TikaInputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext)
            throws TikaException, IOException;
}
