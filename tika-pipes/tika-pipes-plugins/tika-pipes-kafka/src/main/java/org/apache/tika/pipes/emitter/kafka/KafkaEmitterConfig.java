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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.plugins.PluginJson;

/** Boxed: an unset value stays null so safePut omits the property and Kafka applies its own default. */
public record KafkaEmitterConfig(
        String topic,
        String bootstrapServers,
        String acks,
        Integer lingerMs,
        Integer batchSize,
        Integer bufferMemory,
        String compressionType,
        Integer connectionsMaxIdleMs,
        Integer deliveryTimeoutMs,
        Boolean enableIdempotence,
        String interceptorClasses,
        Integer maxBlockMs,
        Integer maxInFlightRequestsPerConnection,
        Integer maxRequestSize,
        Integer metadataMaxAgeMs,
        Integer requestTimeoutMs,
        Integer retries,
        Integer retryBackoffMs,
        Integer transactionTimeoutMs,
        String transactionalId,
        String clientId,
        String keySerializer,
        String valueSerializer
) {

    private static final ObjectMapper OBJECT_MAPPER = PluginJson.mapper();

    public static KafkaEmitterConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, KafkaEmitterConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse KafkaEmitterConfig from JSON", e);
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
