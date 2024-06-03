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
package org.apache.tika.pipes.pipesiterator.kafka;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

public class KafkaPipesIterator extends PipesIterator implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPipesIterator.class);
    String topic;
    String bootstrapServers;
    String keySerializer;
    String valueSerializer;
    String groupId;
    String autoOffsetReset = "earliest";
    int pollDelayMs = 100;
    int emitMax = -1;
    int groupInitialRebalanceDelayMs = 3000;

    private Properties props;
    private KafkaConsumer<String, String> consumer;


    @Field
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @Field
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Field
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Field
    public void setKeySerializer(String keySerializer) {
        this.keySerializer = keySerializer;
    }

    @Field
    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    @Field
    public void setValueSerializer(String valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    @Field
    public void setPollDelayMs(int pollDelayMs) {
        this.pollDelayMs = pollDelayMs;
    }

    @Field
    public void setGroupInitialRebalanceDelayMs(int groupInitialRebalanceDelayMs) {
        this.groupInitialRebalanceDelayMs = groupInitialRebalanceDelayMs;
    }

    /**
     * If the kafka pipe iterator will keep polling for more documents until it returns an empty result.
     * If you set emitMax is set to > 0, it will stop polling if the number of documents you
     * have emitted so far > emitMax.
     */
    @Field
    public void setEmitMax(int emitMax) {
        this.emitMax = emitMax;
    }

    private void safePut(Properties props, String key, Object val) {
        if (val != null) {
            props.put(key, val);
        }
    }

    @Override
    public void initialize(Map<String, Param> params) {
        props = new Properties();
        safePut(props, ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        safePut(props, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                serializerClass(keySerializer, StringDeserializer.class));
        safePut(props, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                serializerClass(valueSerializer, StringDeserializer.class));
        safePut(props, ConsumerConfig.GROUP_ID_CONFIG, groupId);
        safePut(props, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        safePut(props, "group.inital.rebalance.delay.ms", groupInitialRebalanceDelayMs);
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(topic));
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
        super.checkInitialization(problemHandler);
        TikaConfig.mustNotBeEmpty("bootstrapServers", this.bootstrapServers);
        TikaConfig.mustNotBeEmpty("topic", this.topic);
    }

    @Override
    protected void enqueue() throws InterruptedException, TimeoutException {
        String fetcherName = getFetcherName();
        String emitterName = getEmitterName();
        long start = System.currentTimeMillis();
        int count = 0;
        HandlerConfig handlerConfig = getHandlerConfig();
        ConsumerRecords<String, String> records;

        do {
            records = consumer.poll(Duration.ofMillis(pollDelayMs));
            for (ConsumerRecord<String, String> r : records) {
                long elapsed = System.currentTimeMillis() - start;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("adding ({}) {} in {} ms", count, r.key(), elapsed);
                }
                ParseContext parseContext = new ParseContext();
                parseContext.set(HandlerConfig.class, handlerConfig);
                tryToAdd(new FetchEmitTuple(r.key(), new FetchKey(fetcherName,
                        r.key()),
                        new EmitKey(emitterName, r.key()), new Metadata(), parseContext,
                        getOnParseException()));
                ++count;
            }
        } while ((emitMax > 0 || count < emitMax) && !records.isEmpty());
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("Finished enqueuing {} files in {} ms", count, elapsed);
    }
}
