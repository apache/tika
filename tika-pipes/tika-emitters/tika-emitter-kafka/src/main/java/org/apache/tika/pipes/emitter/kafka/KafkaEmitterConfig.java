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
package org.apache.tika.pipes.emitter.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public record KafkaEmitterConfig(
        String topic,
        String bootstrapServers,
        @JsonProperty(defaultValue = "all") String acks,
        @JsonProperty(defaultValue = "5000") int lingerMs,
        @JsonProperty(defaultValue = "16384") int batchSize,
        @JsonProperty(defaultValue = "33554432") int bufferMemory,
        @JsonProperty(defaultValue = "none") String compressionType,
        @JsonProperty(defaultValue = "540000") int connectionsMaxIdleMs,
        @JsonProperty(defaultValue = "120000") int deliveryTimeoutMs,
        @JsonProperty(defaultValue = "false") boolean enableIdempotence,
        String interceptorClasses,
        @JsonProperty(defaultValue = "60000") int maxBlockMs,
        @JsonProperty(defaultValue = "5") int maxInFlightRequestsPerConnection,
        @JsonProperty(defaultValue = "1048576") int maxRequestSize,
        @JsonProperty(defaultValue = "300000") int metadataMaxAgeMs,
        @JsonProperty(defaultValue = "30000") int requestTimeoutMs,
        @JsonProperty(defaultValue = "2147483647") int retries,
        @JsonProperty(defaultValue = "100") int retryBackoffMs,
        @JsonProperty(defaultValue = "60000") int transactionTimeoutMs,
        String transactionalId,
        String clientId,
        String keySerializer,
        String valueSerializer
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static KafkaEmitterConfig load(String json) throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, KafkaEmitterConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException("Failed to parse KafkaEmitterConfig from JSON", e);
        }
    }

    public void validate() throws TikaConfigException {
        if (topic == null || topic.isBlank()) {
            throw new TikaConfigException("'topic' must not be empty");
        }
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new TikaConfigException("'bootstrapServers' must not be empty");
        }
    }
}
