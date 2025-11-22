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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.regex.Matcher;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import org.apache.tika.cli.TikaCLI;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.utils.SystemUtils;

/**
 * Test will emit some documents into a Kafka "pipe_iterator_topic", then kafka pipe iterator will
 * poll those documents and send them to tika pipes. Tika pipes will then use a file fetcher to fetch/parse, then
 * the kafka emitter will send the now-parsed output to the "emitter_topic".
 * Will then wait for the messages to come from the emitter and assert they are correct.
 */
@Testcontainers(disabledWithoutDocker = true)
public class TikaPipesKafkaTest {
    @BeforeAll
    public static void setUp() {
        assumeTrue(!SystemUtils.IS_OS_MAC_OSX && !SystemUtils.OS_VERSION.equals("12.6.1"),
                "This stopped working on macos x ... TIKA-3932");
    }
    public static final String PIPE_ITERATOR_TOPIC = "pipe_iterator_topic";
    public static final String EMITTER_TOPIC = "emitter_topic";
    /**
     * Wait up to this many minutes before you give up waiting for the emitted documents to poll from the
     * emitter_topic and fail the test.
     */
    public static final int WAIT_FOR_EMITTED_DOCS_TIMEOUT_MINUTES = 2;
    private static final Logger LOG = LoggerFactory.getLogger(TikaPipesKafkaTest.class);
    private final int numDocs = 42;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> waitingFor = new HashSet<>();
    // https://java.testcontainers.org/modules/kafka/#using-orgtestcontainerskafkaconfluentkafkacontainer
    ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    private void createTestFiles(Path testFileFolderPath) throws Exception {
        Files.createDirectories(testFileFolderPath);
        LOG.info("Created test folder: {}", testFileFolderPath);
        for (int i = 0; i < numDocs; ++i) {
            String nextFileName = "test-" + i + ".html";
            Files.writeString(testFileFolderPath.resolve(nextFileName),
                    "<html><body>body-" + i + "</body></html>", StandardCharsets.UTF_8);
            waitingFor.add(nextFileName);
        }
    }

    @BeforeEach
    public void before() {
        kafka.start();
    }

    @AfterEach
    public void after() {
        kafka.close();
    }

    @Test
    public void testKafkaPipeIteratorAndEmitter(@TempDir Path pipesDirectory) throws Exception {
        Path testFileFolderPath = pipesDirectory.resolve("test-files");
        createTestFiles(testFileFolderPath);

        Path tikaConfigFile = getTikaConfigFile(pipesDirectory);
        Path pluginsConfig = getPluginsConfig(tikaConfigFile, pipesDirectory, testFileFolderPath);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put("group.id", UUID.randomUUID().toString());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        LOG.info("Listening to EMITTER_TOPIC={}", EMITTER_TOPIC);
        consumer.subscribe(Collections.singletonList(EMITTER_TOPIC));

        ExecutorService es = Executors.newCachedThreadPool();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            int numSent = 0;
            for (int i = 0; i < numDocs; ++i) {
                File nextFile = testFileFolderPath.resolve("test-" + i + ".html").toFile();
                Map<String, Object> meta = new HashMap<>();
                meta.put("name", nextFile.getName());
                meta.put("path", nextFile.getAbsolutePath());
                meta.put("totalSpace", nextFile.getTotalSpace());
                try {
                    producer.send(
                            new ProducerRecord<>(PIPE_ITERATOR_TOPIC, nextFile.getAbsolutePath(),
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
                TikaCLI.main(new String[]{"-a", pluginsConfig.toAbsolutePath().toString(), "-c", tikaConfigFile.toAbsolutePath().toString()});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        LOG.info(
                "Tika pipes have been started. See if we can pull the response messages from the EMITTER_TOPIC={}",
                EMITTER_TOPIC);

        Stopwatch stopwatch = Stopwatch.createStarted();
        while (!waitingFor.isEmpty()) {
            assertFalse(stopwatch.elapsed(TimeUnit.MINUTES) > WAIT_FOR_EMITTED_DOCS_TIMEOUT_MINUTES,
                    "Timed out after " + WAIT_FOR_EMITTED_DOCS_TIMEOUT_MINUTES +
                            " minutes waiting for the emitted docs");
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    String val = record.value();
                    Map<String, Object> valMap =
                            objectMapper.readValue(val, new TypeReference<Map<String, Object>>() {
                            });
                    waitingFor.remove(FilenameUtils.getName(record.key()));
                    assertNotNull(valMap.get("content_s"));
                    assertNotNull(valMap.get("mime_s"));
                    assertNotNull(valMap.get("length_i"));
                    LOG.info("Received message key={}, offset={}", record.key(), record.offset());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        LOG.info("Done");
    }

    private Path getTikaConfigFile(Path pipesDirectory) throws Exception {
        Path tikaConfigFile = pipesDirectory.resolve("ta-kafka.xml");
        String tikaConfigTemplateXml;
        try (InputStream is = this.getClass().getResourceAsStream("/kafka/tika-config-kafka.xml")) {
            assert is != null;
            tikaConfigTemplateXml = IOUtils.toString(is, StandardCharsets.UTF_8);
        }
        Files.writeString(tikaConfigFile, tikaConfigTemplateXml, StandardCharsets.UTF_8);
        return tikaConfigFile;
    }

    @NotNull
    private Path getPluginsConfig(Path tikaConfig, Path pipesDirectory, Path testFileFolderPath) throws Exception {
        String json;
        try (InputStream is = this.getClass().getResourceAsStream("/kafka/plugins-template.json")) {
            assert is != null;
            json = IOUtils.toString(is, StandardCharsets.UTF_8);
        }

        String res = json.replace("PIPE_ITERATOR_TOPIC", PIPE_ITERATOR_TOPIC)
                .replace("EMITTER_TOPIC", EMITTER_TOPIC)
                .replace("BOOTSTRAP_SERVERS", kafka.getBootstrapServers())
                .replaceAll("FETCHER_BASE_PATH",
                        Matcher.quoteReplacement(testFileFolderPath.toAbsolutePath().toString()))
                .replace("PARSE_MODE", HandlerConfig.PARSE_MODE.RMETA.name());

        if (tikaConfig != null) {
            res = res.replace("TIKA_CONFIG", tikaConfig.toAbsolutePath().toString());
        }

        Path log4jPropFile = pipesDirectory.resolve("log4j2.xml");
        try (InputStream is = this.getClass().getResourceAsStream("/pipes-fork-server-custom-log4j2.xml")) {
            assert is != null;
            Files.copy(is, log4jPropFile);
        }
        res = res.replace("LOG4J_PROPERTIES_FILE", log4jPropFile.toAbsolutePath().toString());

        Path pluginsConfig = pipesDirectory.resolve("plugins-config.json");
        res = res.replace("PLUGINS_CONFIG", pluginsConfig.toAbsolutePath().toString());
        Files.writeString(pluginsConfig, res, StandardCharsets.UTF_8);
        return pluginsConfig;
    }
}
