package org.apache.tika.pipes.kafka.tests;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.TopicConfig;
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
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;

public class TikaPipesKafkaTest {
    private static final Logger LOG = LoggerFactory.getLogger(TikaPipesKafkaTest.class);

    public static final String PIPE_ITERATOR_TOPIC = "pipe_iterator_topic";
    public static final String EMITTER_TOPIC = "emitter_topic";
    public static final String EMITTER_GRPID = "emit";
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

        Properties adminProperties = new Properties();
        adminProperties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

        Admin admin = Admin.create(adminProperties);

        createTopic(admin, PIPE_ITERATOR_TOPIC);
        createTopic(admin, EMITTER_TOPIC);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put("group.id", EMITTER_GRPID);
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
            Assert.assertFalse(stopwatch.elapsed(TimeUnit.MINUTES) > 2);
            try {
                //consumer.seekToBeginning(consumer.assignment());
                ConsumerRecords<String, String> records = consumer.poll(2000);
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

    private void createTopic(Admin admin, String topic) throws InterruptedException, ExecutionException {
        NewTopic newTopic = new NewTopic(topic, 1, (short) 1);
        newTopic.configs(ImmutableMap.of(TopicConfig.RETENTION_MS_CONFIG, "1680000"));

        CreateTopicsResult result = admin.createTopics(
                Collections.singleton(newTopic)
        );

        KafkaFuture<Void> future = result.values().get(topic);
        future.get();
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
