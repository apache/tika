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
package org.apache.tika.pipes.kafka.tests;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.tika.cli.TikaCLI;
import org.apache.tika.pipes.HandlerConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;

/**
 * Test will emit some documents into a Kafka "pipe_iterator_topic", then kafka pipe iterator will
 * poll those documents and send them to tika pipes. Tika pipes will then use a file fetcher to fetch/parse, then
 * the kafka emitter will send the now-parsed output to the "emitter_topic".
 * Will then wait for the messages to come from the emitter and assert they are correct.
 */
public class TikaPipesKafkaTest {
    private static final Logger LOG = LoggerFactory.getLogger(TikaPipesKafkaTest.class);

    public static final String PIPE_ITERATOR_TOPIC = "pipe_iterator_topic";
    public static final String EMITTER_TOPIC = "emitter_topic";
    /**
     * Wait up to this many minutes before you give up waiting for the emitted documents to poll from the
     * emitter_topic and fail the test.
     */
    public static final int WAIT_FOR_EMITTED_DOCS_TIMEOUT_MINUTES = 2;
    private final int numDocs = 42;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final File testFileFolder = new File("target", "test-files");

    private final Set<String> waitingFor = new HashSet<>();

    private void createTestFiles(String bodyContent) throws Exception {
        testFileFolder.mkdirs();
        for (int i = 0; i < numDocs; ++i) {
            String nextFileName = "test-" + i + ".html";
            FileUtils.writeStringToFile(new File(testFileFolder, nextFileName),
                    "<html><body>" + bodyContent + "</body></html>", StandardCharsets.UTF_8);
            waitingFor.add(nextFileName);
        }
    }

    KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1"));

    @Before
    public void before() {
        kafka.start();
    }

    @After
    public void after() {
        kafka.close();
    }

    @Test
    public void testKafkaPipeIteratorAndEmitter()
            throws Exception {
        createTestFiles("initial");
        File tikaConfigFile = new File("target", "ta.xml");
        File log4jPropFile = new File("target", "tmp-log4j2.xml");
        try (InputStream is = this.getClass()
                .getResourceAsStream("/pipes-fork-server-custom-log4j2.xml")) {
            FileUtils.copyInputStreamToFile(is, log4jPropFile);
        }
        String tikaConfigTemplateXml;
        try (InputStream is = this.getClass()
                .getResourceAsStream("/tika-config-kafka.xml")) {
            tikaConfigTemplateXml = IOUtils.toString(is, StandardCharsets.UTF_8);
        }

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put("group.id", UUID.randomUUID().toString());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        LOG.info("Listening to EMITTER_TOPIC={}", EMITTER_TOPIC);
        consumer.subscribe(Collections.singletonList(EMITTER_TOPIC));

        ExecutorService es = Executors.newCachedThreadPool();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            int numSent = 0;
            for (int i = 0; i < numDocs; ++i) {
                File nextFile = new File(testFileFolder, "test-" + i + ".html");
                Map<String, Object> meta = new HashMap<>();
                meta.put("name", nextFile.getName());
                meta.put("path", nextFile.getAbsolutePath());
                meta.put("totalSpace", nextFile.getTotalSpace());
                try {
                    producer.send(new ProducerRecord<>(PIPE_ITERATOR_TOPIC,
                            nextFile.getAbsolutePath(),
                            objectMapper.writeValueAsString(meta))).get();
                    LOG.info("Sent fetch request : {}", nextFile.getAbsolutePath());
                    ++numSent;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            LOG.info("Producer is now complete - sent {}.", numSent);
        }

        es.execute(() -> {
            try {
                String tikaConfigXml =
                        createTikaConfigXml(tikaConfigFile, log4jPropFile, tikaConfigTemplateXml,
                                HandlerConfig.PARSE_MODE.RMETA);

                FileUtils.writeStringToFile(tikaConfigFile, tikaConfigXml, StandardCharsets.UTF_8);
                TikaCLI.main(new String[] {"-a", "--config=" + tikaConfigFile.getAbsolutePath()});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        LOG.info("Tika pipes have been started. See if we can pull the response messages from the EMITTER_TOPIC={}", EMITTER_TOPIC);

        Stopwatch stopwatch = Stopwatch.createStarted();
        while (!waitingFor.isEmpty()) {
            Assert.assertFalse("Timed out after " + WAIT_FOR_EMITTED_DOCS_TIMEOUT_MINUTES + " minutes waiting for the emitted docs",
                    stopwatch.elapsed(TimeUnit.MINUTES) > WAIT_FOR_EMITTED_DOCS_TIMEOUT_MINUTES);
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    String val = record.value();
                    Map<String, Object> valMap = objectMapper.readValue(val, Map.class);
                    waitingFor.remove(FilenameUtils.getName(record.key()));
                    Assert.assertNotNull(valMap.get("content_s"));
                    Assert.assertNotNull(valMap.get("mime_s"));
                    Assert.assertNotNull(valMap.get("length_i"));
                    LOG.info("Received message key={}, offset={}", record.key(), record.offset());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        LOG.info("Done");
    }

    @NotNull
    private String createTikaConfigXml(File tikaConfigFile, File log4jPropFile,
                                       String tikaConfigTemplateXml,
                                       HandlerConfig.PARSE_MODE parseMode) {
        String res =
                tikaConfigTemplateXml.replace("{TIKA_CONFIG}", tikaConfigFile.getAbsolutePath())
                        .replace("{LOG4J_PROPERTIES_FILE}", log4jPropFile.getAbsolutePath())
                        .replace("{PATH_TO_DOCS}", testFileFolder.getAbsolutePath())
                        .replace("{PARSE_MODE}", parseMode.name())
                        .replace("{PIPE_ITERATOR_TOPIC}", PIPE_ITERATOR_TOPIC)
                        .replace("{EMITTER_TOPIC}", EMITTER_TOPIC)
                        .replace("{BOOTSTRAP_SERVERS}", kafka.getBootstrapServers());
        return res;
    }
}
