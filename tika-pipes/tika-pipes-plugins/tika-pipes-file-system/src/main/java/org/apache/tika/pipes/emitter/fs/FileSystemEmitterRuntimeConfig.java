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
package org.apache.tika.pipes.emitter.fs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

/**
 * Runtime configuration for FileSystemEmitter.
 * Only includes fields that are safe to update at runtime.
 * basePath is intentionally excluded for security reasons.
 */
public class FileSystemEmitterRuntimeConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static FileSystemEmitterRuntimeConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json,
                    FileSystemEmitterRuntimeConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse FileSystemEmitterRuntimeConfig from JSON", e);
        }
    }

    private String fileExtension;
    private FileSystemEmitterConfig.ON_EXISTS onExists;
    private boolean prettyPrint;

    public String getFileExtension() {
        return fileExtension;
    }

    public FileSystemEmitterRuntimeConfig setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
        return this;
    }

    public FileSystemEmitterConfig.ON_EXISTS getOnExists() {
        return onExists;
    }

    public FileSystemEmitterRuntimeConfig setOnExists(FileSystemEmitterConfig.ON_EXISTS onExists) {
        this.onExists = onExists;
        return this;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public FileSystemEmitterRuntimeConfig setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
        return this;
    }
}
