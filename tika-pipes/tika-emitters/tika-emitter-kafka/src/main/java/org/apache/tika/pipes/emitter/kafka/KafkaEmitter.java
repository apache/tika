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

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

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

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;

/**
 * Emits the now-parsed documents into a specified Apache Kafka topic.
 */
public class KafkaEmitter extends AbstractEmitter implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaEmitter.class);

    private static final ObjectMapper OM = new ObjectMapper();

    String topic;
    String bootstrapServers;

    String acks = "all";
    int lingerMs = 5000;
    int batchSize = 16384;
    int bufferMemory = 32 * 1024 * 1024;
    String compressionType = "none";
    int connectionsMaxIdleMs = 9 * 60 * 1000;
    int deliveryTimeoutMs = 120 * 1000;
    boolean enableIdempotence = false;
    String interceptorClasses;
    int maxBlockMs = 60 * 1000;
    int maxInFlightRequestsPerConnection = 5;
    int maxRequestSize = 1024 * 1024;
    int metadataMaxAgeMs = 5 * 60 * 1000;
    int requestTimeoutMs = 30 * 1000;
    int retries = Integer.MAX_VALUE;
    int retryBackoffMs = 100;
    int transactionTimeoutMs = 60000;
    String transactionalId;
    String clientId;
    String keySerializer;
    String valueSerializer;

    private Producer<String, String> producer;

    @Field
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Field
    public void setAcks(String acks) {
        this.acks = acks;
    }

    @Field
    public void setLingerMs(int lingerMs) {
        this.lingerMs = lingerMs;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @Field
    public void setBufferMemory(int bufferMemory) {
        this.bufferMemory = bufferMemory;
    }

    @Field
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Field
    public void setCompressionType(String compressionType) {
        this.compressionType = compressionType;
    }

    @Field
    public void setConnectionsMaxIdleMs(int connectionsMaxIdleMs) {
        this.connectionsMaxIdleMs = connectionsMaxIdleMs;
    }

    @Field
    public void setDeliveryTimeoutMs(int deliveryTimeoutMs) {
        this.deliveryTimeoutMs = deliveryTimeoutMs;
    }

    @Field
    public void setEnableIdempotence(boolean enableIdempotence) {
        this.enableIdempotence = enableIdempotence;
    }

    @Field
    public void setInterceptorClasses(String interceptorClasses) {
        this.interceptorClasses = interceptorClasses;
    }

    @Field
    public void setMaxBlockMs(int maxBlockMs) {
        this.maxBlockMs = maxBlockMs;
    }

    @Field
    public void setMaxInFlightRequestsPerConnection(int maxInFlightRequestsPerConnection) {
        this.maxInFlightRequestsPerConnection = maxInFlightRequestsPerConnection;
    }

    @Field
    public void setMaxRequestSize(int maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    @Field
    public void setMetadataMaxAgeMs(int metadataMaxAgeMs) {
        this.metadataMaxAgeMs = metadataMaxAgeMs;
    }

    @Field
    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    @Field
    public void setRetries(int retries) {
        this.retries = retries;
    }

    @Field
    public void setRetryBackoffMs(int retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    @Field
    public void setTransactionTimeoutMs(int transactionTimeoutMs) {
        this.transactionTimeoutMs = transactionTimeoutMs;
    }

    @Field
    public void setTransactionalId(String transactionalId) {
        this.transactionalId = transactionalId;
    }

    @Field
    public void setKeySerializer(String keySerializer) {
        this.keySerializer = keySerializer;
    }

    @Field
    public void setValueSerializer(String valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    @Field
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext)
            throws IOException, TikaEmitterException {
        if (metadataList == null || metadataList.isEmpty()) {
            throw new TikaEmitterException("metadata list must not be null or of size 0");
        }
        for (Metadata metadata : metadataList) {
            LOGGER.debug("about to emit to target topic: ({}) path:({})", topic, emitKey);

            Map<String, Object> fields = new HashMap<>();
            for (String n : metadata.names()) {
                String[] vals = metadata.getValues(n);
                if (vals.length > 1) {
                    LOGGER.warn("Can only write the first value for key {}. I see {} values.",
                            n,
                            vals.length);
                }
                fields.put(n, vals[0]);
            }

            producer.send(new ProducerRecord<>(topic, emitKey, OM.writeValueAsString(fields)));
        }
    }

    private void safePut(Properties props, String key, Object val) {
        if (val != null) {
            props.put(key, val);
        }
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

        // create instance for properties to access producer configs   
        Properties props = new Properties();

        //Assign localhost id
        safePut(props, ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        //Set acknowledgements for producer requests.      
        safePut(props, ProducerConfig.ACKS_CONFIG, acks);

        //If the request fails, the producer can automatically retry,
        safePut(props, ProducerConfig.RETRIES_CONFIG, retries);

        //Specify buffer size in config
        safePut(props, ProducerConfig.BATCH_SIZE_CONFIG, batchSize);

        //Reduce the no of requests less than 0   
        safePut(props, ProducerConfig.LINGER_MS_CONFIG, lingerMs);

        //The buffer.memory controls the total amount of memory available to the producer for buffering.   
        safePut(props, ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);

        safePut(props, ProducerConfig.CLIENT_ID_CONFIG, clientId);
        safePut(props, ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        safePut(props, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
        safePut(props, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, enableIdempotence);
        safePut(props, ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, interceptorClasses);
        safePut(props, ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs);
        safePut(props, ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, maxInFlightRequestsPerConnection);
        safePut(props, ProducerConfig.MAX_REQUEST_SIZE_CONFIG, maxRequestSize);
        safePut(props, ProducerConfig.METADATA_MAX_AGE_CONFIG, metadataMaxAgeMs);
        safePut(props, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        safePut(props, ProducerConfig.RETRY_BACKOFF_MS_CONFIG, retryBackoffMs);
        safePut(props, ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, transactionTimeoutMs);
        safePut(props, ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);

        safePut(props, ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                serializerClass(keySerializer, StringSerializer.class));
        safePut(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                serializerClass(valueSerializer, StringSerializer.class));

        producer = new KafkaProducer<>(props);
    }

    private Object serializerClass(String className, Class defaultClass) {
        try {
            return className == null ? defaultClass : Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not find key serializer class: {}", className);
            return null;
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        mustNotBeEmpty("topic", this.topic);
        mustNotBeEmpty("server", this.bootstrapServers);
    }

}
