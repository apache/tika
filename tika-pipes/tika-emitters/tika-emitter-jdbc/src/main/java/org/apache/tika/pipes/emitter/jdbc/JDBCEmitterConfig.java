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
package org.apache.tika.pipes.emitter.jdbc;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public record JDBCEmitterConfig(
        String connection,
        String insert,
        String createTable,
        String alterTable,
        String postConnection,
        @JsonProperty(defaultValue = "0") int maxRetries,
        @JsonProperty(defaultValue = "64000") int maxStringLength,
        LinkedHashMap<String, String> keys,
        @JsonProperty(defaultValue = "FIRST_ONLY") String attachmentStrategy,
        @JsonProperty(defaultValue = "CONCATENATE") String multivaluedFieldStrategy,
        @JsonProperty(defaultValue = ", ") String multivaluedFieldDelimiter
) {

    public enum AttachmentStrategy {
        FIRST_ONLY, ALL
    }

    public enum MultivaluedFieldStrategy {
        FIRST_ONLY, CONCATENATE
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static JDBCEmitterConfig load(JsonNode jsonNode) throws IOException {
        return OBJECT_MAPPER.treeToValue(jsonNode, JDBCEmitterConfig.class);
    }

    public void validate() throws TikaConfigException {
        if (connection == null || connection.isBlank()) {
            throw new TikaConfigException("'connection' must not be empty");
        }
        if (insert == null || insert.isBlank()) {
            throw new TikaConfigException("'insert' must not be empty");
        }
        if (keys == null || keys.isEmpty()) {
            throw new TikaConfigException("'keys' must not be empty");
        }
    }

    public AttachmentStrategy getAttachmentStrategyEnum() throws TikaConfigException {
        if (attachmentStrategy == null) {
            return AttachmentStrategy.FIRST_ONLY;
        }
        String lc = attachmentStrategy.toLowerCase(Locale.US);
        if ("all".equals(lc)) {
            return AttachmentStrategy.ALL;
        } else if ("first_only".equals(lc)) {
            return AttachmentStrategy.FIRST_ONLY;
        } else {
            throw new TikaConfigException("attachmentStrategy must be 'all' or 'first_only'");
        }
    }

    public MultivaluedFieldStrategy getMultivaluedFieldStrategyEnum() throws TikaConfigException {
        if (multivaluedFieldStrategy == null) {
            return MultivaluedFieldStrategy.CONCATENATE;
        }
        String lc = multivaluedFieldStrategy.toLowerCase(Locale.US);
        if ("first_only".equals(lc)) {
            return MultivaluedFieldStrategy.FIRST_ONLY;
        } else if ("concatenate".equals(lc)) {
            return MultivaluedFieldStrategy.CONCATENATE;
        } else {
            throw new TikaConfigException("multivaluedFieldStrategy must be 'first_only' or 'concatenate'");
        }
    }

    /**
     * Returns the effective maxStringLength, using the default of 64000 if not set or 0.
     */
    public int getEffectiveMaxStringLength() {
        return maxStringLength <= 0 ? 64000 : maxStringLength;
    }
}
