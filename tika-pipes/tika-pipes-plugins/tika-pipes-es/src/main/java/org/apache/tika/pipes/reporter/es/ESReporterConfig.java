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
package org.apache.tika.pipes.reporter.es;

import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.ReservedNamespaces;
import org.apache.tika.pipes.emitter.es.HttpClientConfig;
import org.apache.tika.utils.StringUtils;

public record ESReporterConfig(String esUrl, Set<String> includes, Set<String> excludes,
                               String keyPrefix, boolean includeRouting,
                               String apiKey, HttpClientConfig httpClientConfig) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ESReporterConfig load(final String json) throws TikaConfigException {
        ESReporterConfig config;
        try {
            config = OBJECT_MAPPER.readValue(json, ESReporterConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse ESReporterConfig from JSON", e);
        }
        // keyPrefix is prepended to this reporter's own scratch-Metadata keys (parse_status/
        // parse_time_ms/exit_value); reject a reserved prefix here, before it fails every report() call.
        if (!StringUtils.isBlank(config.keyPrefix()) && ReservedNamespaces.isTikaNative(config.keyPrefix())) {
            throw new TikaConfigException("keyPrefix '" + config.keyPrefix() + "' is in the "
                    + "reserved Tika-native namespace (tk:/X-TIKA:); choose a different keyPrefix");
        }
        return config;
    }
}
