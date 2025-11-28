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
package org.apache.tika.pipes.fetcher.fs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

/**
 * Runtime configuration for FileSystemFetcher.
 * Only includes fields that are safe to update at runtime.
 * basePath is intentionally excluded for security reasons.
 */
public class FileSystemFetcherRuntimeConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static FileSystemFetcherRuntimeConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json,
                    FileSystemFetcherRuntimeConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse FileSystemFetcherRuntimeConfig from JSON", e);
        }
    }

    private boolean extractFileSystemMetadata;

    public boolean isExtractFileSystemMetadata() {
        return extractFileSystemMetadata;
    }

    public FileSystemFetcherRuntimeConfig setExtractFileSystemMetadata(boolean extractFileSystemMetadata) {
        this.extractFileSystemMetadata = extractFileSystemMetadata;
        return this;
    }
}
