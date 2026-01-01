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
package org.apache.tika.pipes.iterator.fs;

import java.nio.file.Path;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.pipesiterator.PipesIteratorConfig;

public class FileSystemPipesIteratorConfig extends PipesIteratorConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static FileSystemPipesIteratorConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json,
                    FileSystemPipesIteratorConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse FileSystemPipesIteratorConfig from JSON",
                    e);
        }
    }

    private Path basePath = null;
    private boolean countTotal = true;

    public Path getBasePath() {
        return basePath;
    }

    public boolean isCountTotal() {
        return countTotal;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FileSystemPipesIteratorConfig that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return countTotal == that.countTotal && Objects.equals(basePath, that.basePath);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(basePath);
        result = 31 * result + Boolean.hashCode(countTotal);
        return result;
    }
}
