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
package org.apache.tika.pipes.iterator.azblob;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.pipesiterator.PipesIteratorConfig;

public class AZBlobPipesIteratorConfig extends PipesIteratorConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static AZBlobPipesIteratorConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json,
                    AZBlobPipesIteratorConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse AZBlobPipesIteratorConfig from JSON", e);
        }
    }

    private String sasToken;
    private String endpoint;
    private String container;
    private String prefix = "";
    private long timeoutMillis = 360000;

    public String getSasToken() {
        return sasToken;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getContainer() {
        return container;
    }

    public String getPrefix() {
        return prefix;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AZBlobPipesIteratorConfig that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return timeoutMillis == that.timeoutMillis &&
                Objects.equals(sasToken, that.sasToken) &&
                Objects.equals(endpoint, that.endpoint) &&
                Objects.equals(container, that.container) &&
                Objects.equals(prefix, that.prefix);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(sasToken);
        result = 31 * result + Objects.hashCode(endpoint);
        result = 31 * result + Objects.hashCode(container);
        result = 31 * result + Objects.hashCode(prefix);
        result = 31 * result + Long.hashCode(timeoutMillis);
        return result;
    }
}
