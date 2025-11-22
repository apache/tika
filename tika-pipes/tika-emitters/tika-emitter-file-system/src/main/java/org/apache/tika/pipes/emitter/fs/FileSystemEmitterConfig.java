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

public record FileSystemEmitterConfig(String basePath, String fileExtension, ON_EXISTS onExists, boolean prettyPrint) {

    enum ON_EXISTS {
        SKIP, EXCEPTION, REPLACE
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static FileSystemEmitterConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json,
                    FileSystemEmitterConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse FileSystemEmitterConfig from JSON", e);
        }
    }

}
