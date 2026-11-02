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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.TikaEmitterException;

/** An emit succeeds only once Kafka has accepted every record (TIKA-5008). */
public class KafkaEmitterTest {

    private MockProducer<String, String> producer;
    private KafkaEmitter emitter;
    private ExecutorService executor;

    @BeforeEach
    public void setUp() {
        // autoComplete=false: each send stays pending until the test acks or fails it
        producer = new MockProducer<>(false, null, new StringSerializer(), new StringSerializer());
        emitter = new KafkaEmitter();
        emitter.setTopic("t");
        emitter.producer = producer;
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    public void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void testEmitWaitsForAck() throws Exception {
        Future<?> emit = executor.submit(() -> {
            emitter.emit("doc", List.of(metadata("a"), metadata("b")), new ParseContext());
            return null;
        });
        awaitSends(2);
        assertTrue(producer.completeNext());
        assertFalse(waitDone(emit), "reported success with a record still unacknowledged");
        assertTrue(producer.completeNext());
        emit.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testFailedSendFailsTheEmit() throws Exception {
        Future<?> emit = executor.submit(() -> {
            emitter.emit("doc", List.of(metadata("a")), new ParseContext());
            return null;
        });
        awaitSends(1);
        assertTrue(producer.errorNext(new RuntimeException("broker said no")));
        ExecutionException e = assertThrows(
                ExecutionException.class, () -> emit.get(10, TimeUnit.SECONDS));
        assertTrue(e.getCause() instanceof TikaEmitterException, String.valueOf(e.getCause()));
    }

    @Test
    public void testBatchSendsAllBeforeWaiting() throws Exception {
        List<EmitData> batch = List.of(emitData("d1", "a"), emitData("d2", "b"));
        Future<?> emit = executor.submit(() -> {
            emitter.emit(batch);
            return null;
        });
        // both on the wire before the first ack: one round trip for the batch
        awaitSends(2);
        assertTrue(producer.completeNext());
        assertTrue(producer.completeNext());
        emit.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testTransactionalIdRefused() {
        // never initTransactions(): the producer would refuse every send
        KafkaEmitter e = new KafkaEmitter();
        e.setTopic("t");
        e.setBootstrapServers("h:9092");
        e.setTransactionalId("x");
        assertThrows(TikaConfigException.class, () -> e.initialize(Collections.emptyMap()));
    }

    private void awaitSends(int n) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (producer.history().size() < n) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("expected " + n + " sends, saw " + producer.history().size());
            }
            Thread.sleep(10);
        }
        assertEquals(n, producer.history().size());
    }

    private static boolean waitDone(Future<?> f) throws InterruptedException {
        Thread.sleep(200);
        return f.isDone();
    }

    private static EmitData emitData(String key, String v) {
        return new EmitData(new EmitKey("k", key), List.of(metadata(v)));
    }

    private static Metadata metadata(String v) {
        Metadata m = new Metadata();
        m.set("k", v);
        return m;
    }
}
