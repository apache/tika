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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.AbstractEmitter;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Emitter to write parsed documents into a specified Apache Kafka topic.
 *
 * <p>Example JSON configuration:</p>
 * <pre>
 * {
 *   "emitters": {
 *     "kafka-emitter": {
 *       "my-kafka": {
 *         "topic": "tika-output",
 *         "bootstrapServers": "localhost:9092",
 *         "acks": "all",
 *         "lingerMs": 5000
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public class KafkaEmitter extends AbstractEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaEmitter.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private final KafkaEmitterConfig config;
    private final Producer<String, String> producer;

    public static KafkaEmitter build(ExtensionConfig extensionConfig) throws TikaConfigException, IOException {
        KafkaEmitterConfig config = KafkaEmitterConfig.load(extensionConfig.jsonConfig());
        config.validate();
        Producer<String, String> producer = buildProducer(config);
        return new KafkaEmitter(extensionConfig, config, producer);
    }

    private KafkaEmitter(ExtensionConfig extensionConfig, KafkaEmitterConfig config, Producer<String, String> producer) throws IOException {
        super(extensionConfig);
        this.config = config;
        this.producer = producer;
    }

    private static Producer<String, String> buildProducer(KafkaEmitterConfig config) {
        Properties props = new Properties();

        safePut(props, ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        safePut(props, ProducerConfig.ACKS_CONFIG, config.acks());
        safePut(props, ProducerConfig.RETRIES_CONFIG, config.retries());
        safePut(props, ProducerConfig.BATCH_SIZE_CONFIG, config.batchSize());
        safePut(props, ProducerConfig.LINGER_MS_CONFIG, config.lingerMs());
        safePut(props, ProducerConfig.BUFFER_MEMORY_CONFIG, config.bufferMemory());
        safePut(props, ProducerConfig.CLIENT_ID_CONFIG, config.clientId());
        safePut(props, ProducerConfig.COMPRESSION_TYPE_CONFIG, config.compressionType());
        safePut(props, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, config.deliveryTimeoutMs());
        safePut(props, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, config.enableIdempotence());
        safePut(props, ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, config.interceptorClasses());
        safePut(props, ProducerConfig.MAX_BLOCK_MS_CONFIG, config.maxBlockMs());
        safePut(props, ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, config.maxInFlightRequestsPerConnection());
        safePut(props, ProducerConfig.MAX_REQUEST_SIZE_CONFIG, config.maxRequestSize());
        safePut(props, ProducerConfig.METADATA_MAX_AGE_CONFIG, config.metadataMaxAgeMs());
        safePut(props, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, config.requestTimeoutMs());
        safePut(props, ProducerConfig.RETRY_BACKOFF_MS_CONFIG, config.retryBackoffMs());
        safePut(props, ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, config.transactionTimeoutMs());
        safePut(props, ProducerConfig.TRANSACTIONAL_ID_CONFIG, config.transactionalId());

        safePut(props, ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                serializerClass(config.keySerializer(), StringSerializer.class));
        safePut(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                serializerClass(config.valueSerializer(), StringSerializer.class));

        return new KafkaProducer<>(props);
    }

    private static void safePut(Properties props, String key, Object val) {
        if (val != null) {
            props.put(key, val);
        }
    }

    private static Object serializerClass(String className, Class<?> defaultClass) {
        if (className == null) {
            return defaultClass;
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not find serializer class: {}", className);
            return defaultClass;
        }
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext) throws IOException {
        if (metadataList == null || metadataList.isEmpty()) {
            throw new IOException("metadata list must not be null or of size 0");
        }
        for (Metadata metadata : metadataList) {
            LOGGER.debug("about to emit to target topic: ({}) path:({})", config.topic(), emitKey);

            Map<String, Object> fields = new HashMap<>();
            for (String n : metadata.names()) {
                String[] vals = metadata.getValues(n);
                if (vals.length > 1) {
                    LOGGER.warn("Can only write the first value for key {}. I see {} values.", n, vals.length);
                }
                fields.put(n, vals[0]);
            }

            producer.send(new ProducerRecord<>(config.topic(), emitKey, OM.writeValueAsString(fields)));
        }
    }
}
