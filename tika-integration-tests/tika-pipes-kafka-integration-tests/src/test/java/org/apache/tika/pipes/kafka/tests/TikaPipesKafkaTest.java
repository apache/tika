package org.apache.tika.pipes.kafka.tests;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class TikaPipesKafkaTest {
    private static final Logger LOG = LoggerFactory.getLogger(TikaPipesKafkaTest.class);
    public static final String MY_GROUP_ID = "my-group-id";
    public static final String TOPIC = "topic";
    public static final int NUM_DOCS = 100;

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
    public void testPipes() throws ExecutionException, InterruptedException {

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put("group.id", MY_GROUP_ID);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ExecutorService es = Executors.newCachedThreadPool();

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        AtomicInteger remaining = new AtomicInteger();
        Future producerFuture = es.submit(() -> {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
                while (remaining.get() <= NUM_DOCS) {
                    String msg = "Message " + remaining.getAndIncrement();
                    producer.send(new ProducerRecord<>(TOPIC, msg));
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        producerFuture.get();

        Future consumerFuture = es.submit(() -> {
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
                consumer.subscribe(Collections.singletonList(TOPIC));

                while (remaining.get() > 0) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<String, String> record : records) {
                        remaining.decrementAndGet();
                        LOG.info("Thread: {}, Topic: {}, Partition: {}, Offset: {}, key: {}, value: {}", Thread.currentThread().getName(), record.topic(), record.partition(), record.offset(), record.key(), record.value().toUpperCase());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        consumerFuture.get();

        try {
            consumerFuture.get(3, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            throw new AssertionError("Could not get the consumers from the queue in 30 minutes", e);
        }

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(TOPIC));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(NUM_DOCS));

            Assert.assertTrue(records.isEmpty());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
