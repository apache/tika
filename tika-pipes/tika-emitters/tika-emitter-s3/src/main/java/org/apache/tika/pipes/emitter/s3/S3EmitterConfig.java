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
package org.apache.tika.pipes.emitter.s3;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public record S3EmitterConfig(
        String region,
        String bucket,
        String credentialsProvider,
        String profile,
        String accessKey,
        String secretKey,
        String endpointConfigurationService,
        String prefix,
        @JsonProperty(defaultValue = "json") String fileExtension,
        @JsonProperty(defaultValue = "true") boolean spoolToTemp,
        @JsonProperty(defaultValue = "50") int maxConnections,
        @JsonProperty(defaultValue = "false") boolean pathStyleAccessEnabled
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static S3EmitterConfig load(JsonNode jsonNode) throws IOException {
        return OBJECT_MAPPER.treeToValue(jsonNode, S3EmitterConfig.class);
    }

    public void validate() throws TikaConfigException {
        if (bucket == null || bucket.isBlank()) {
            throw new TikaConfigException("'bucket' must not be empty");
        }
        if (region == null || region.isBlank()) {
            throw new TikaConfigException("'region' must not be empty");
        }
        if (credentialsProvider == null || credentialsProvider.isBlank()) {
            throw new TikaConfigException("'credentialsProvider' must be set to 'profile', 'instance' or 'key_secret'");
        }
        if (!credentialsProvider.equals("profile") &&
            !credentialsProvider.equals("instance") &&
            !credentialsProvider.equals("key_secret")) {
            throw new TikaConfigException(
                    "credentialsProvider must be 'profile', 'instance' or 'key_secret', but was: " + credentialsProvider);
        }
        if (credentialsProvider.equals("profile") && (profile == null || profile.isBlank())) {
            throw new TikaConfigException("'profile' must be set when credentialsProvider is 'profile'");
        }
        if (credentialsProvider.equals("key_secret")) {
            if (accessKey == null || accessKey.isBlank()) {
                throw new TikaConfigException("'accessKey' must be set when credentialsProvider is 'key_secret'");
            }
            if (secretKey == null || secretKey.isBlank()) {
                throw new TikaConfigException("'secretKey' must be set when credentialsProvider is 'key_secret'");
            }
        }
    }

    // Handle prefix normalization (strip trailing /)
    public String normalizedPrefix() {
        if (prefix == null) {
            return null;
        }
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }
}
