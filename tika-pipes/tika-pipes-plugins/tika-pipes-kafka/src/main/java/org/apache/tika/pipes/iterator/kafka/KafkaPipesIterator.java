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
package org.apache.tika.pipes.iterator.kafka;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.pipesiterator.PipesIteratorBase;
import org.apache.tika.plugins.ExtensionConfig;

public class KafkaPipesIterator extends PipesIteratorBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPipesIterator.class);

    public static KafkaPipesIterator build(ExtensionConfig extensionConfig) throws TikaConfigException, IOException {
        KafkaPipesIterator iterator = new KafkaPipesIterator(extensionConfig);
        iterator.configure();
        return iterator;
    }

    private KafkaPipesIteratorConfig config;
    private KafkaConsumer<String, String> consumer;

    private KafkaPipesIterator(ExtensionConfig extensionConfig) {
        super(extensionConfig);
    }

    private void configure() throws IOException, TikaConfigException {
        config = KafkaPipesIteratorConfig.load(pluginConfig.json());
        checkConfig(config);

        Properties props = new Properties();
        safePut(props, ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        safePut(props, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                serializerClass(config.getKeySerializer(), StringDeserializer.class));
        safePut(props, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                serializerClass(config.getValueSerializer(), StringDeserializer.class));
        safePut(props, ConsumerConfig.GROUP_ID_CONFIG, config.getGroupId());
        safePut(props, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.getAutoOffsetReset());
        safePut(props, "group.initial.rebalance.delay.ms", config.getGroupInitialRebalanceDelayMs());

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(config.getTopic()));
    }

    private void checkConfig(KafkaPipesIteratorConfig config) throws TikaConfigException {
        TikaConfig.mustNotBeEmpty("bootstrapServers", config.getBootstrapServers());
        TikaConfig.mustNotBeEmpty("topic", config.getTopic());
    }

    private void safePut(Properties props, String key, Object val) {
        if (val != null) {
            props.put(key, val);
        }
    }

    private Object serializerClass(String className, Class<?> defaultClass) {
        try {
            return className == null ? defaultClass : Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not find serializer class: {}", className);
            return defaultClass;
        }
    }

    @Override
    protected void enqueue() throws InterruptedException, TimeoutException {
        PipesIteratorBaseConfig baseConfig = config.getBaseConfig();
        String fetcherId = baseConfig.fetcherId();
        String emitterId = baseConfig.emitterId();
        HandlerConfig handlerConfig = baseConfig.handlerConfig();

        long start = System.currentTimeMillis();
        int count = 0;
        int emitMax = config.getEmitMax();
        ConsumerRecords<String, String> records;

        do {
            records = consumer.poll(Duration.ofMillis(config.getPollDelayMs()));
            for (ConsumerRecord<String, String> r : records) {
                long elapsed = System.currentTimeMillis() - start;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("adding ({}) {} in {} ms", count, r.key(), elapsed);
                }
                ParseContext parseContext = new ParseContext();
                parseContext.set(HandlerConfig.class, handlerConfig);
                tryToAdd(new FetchEmitTuple(r.key(), new FetchKey(fetcherId, r.key()),
                        new EmitKey(emitterId, r.key()), new Metadata(), parseContext,
                        baseConfig.onParseException()));
                ++count;
            }
        } while ((emitMax < 0 || count < emitMax) && !records.isEmpty());

        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("Finished enqueuing {} files in {} ms", count, elapsed);
    }
}
