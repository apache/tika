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
package org.apache.tika.server.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.ResponseWriteDeadlineInterceptor.DeadlineOutputStream;
import org.apache.tika.server.core.ResponseWriteDeadlineInterceptor.ResponseWriteTimeoutException;

/**
 * The deadline as a timer: a write blocked by a client that reads a trickle (each byte resetting
 * the idle timeout) must still end at the deadline, which a check between writes cannot do.
 */
public class ResponseWriteDeadlineInterceptorTest {

    private static final long DEADLINE_MILLIS = 200;

    private final ScheduledExecutorScheduler scheduler = new ScheduledExecutorScheduler();

    @BeforeEach
    public void start() throws Exception {
        scheduler.start();
    }

    @AfterEach
    public void stop() throws Exception {
        scheduler.stop();
    }

    @Test
    public void testBlockedWriteAbortedAtDeadline() {
        BlockingSink sink = new BlockingSink();
        AtomicInteger aborts = new AtomicInteger();
        DeadlineOutputStream out = new DeadlineOutputStream(sink, DEADLINE_MILLIS, scheduler, () -> true, () -> {
            aborts.incrementAndGet();
            sink.closeConnection();
        }, null);
        long start = System.nanoTime();
        assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> assertThrows(ResponseWriteTimeoutException.class, () -> out.write(new byte[10])));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= DEADLINE_MILLIS);
        assertEquals(1, aborts.get());
    }

    /** Once aborted, CXF's flush and close must not finish the body as if it were complete. */
    @Test
    public void testCloseAfterAbortIsSilentNoOp() throws Exception {
        BlockingSink sink = new BlockingSink();
        DeadlineOutputStream out = new DeadlineOutputStream(sink, DEADLINE_MILLIS, scheduler,
                () -> true, sink::closeConnection, null);
        assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> assertThrows(ResponseWriteTimeoutException.class, () -> out.write(1)));
        out.flush();
        out.close();
        assertFalse(sink.closedByStream);
        assertThrows(ResponseWriteTimeoutException.class, () -> out.write(1));
    }

    /** CXF drains the unread request body before committing; that wait is not the write's. */
    @Test
    public void testDeadlineStartsAtCommit() throws Exception {
        BlockingSink sink = new BlockingSink();
        AtomicBoolean committed = new AtomicBoolean();
        AtomicInteger aborts = new AtomicInteger();
        DeadlineOutputStream out = new DeadlineOutputStream(sink, DEADLINE_MILLIS, scheduler,
                committed::get, () -> {
                    aborts.incrementAndGet();
                    sink.closeConnection();
                }, null);
        Thread writer = new Thread(() -> {
            try {
                out.write(1);
            } catch (IOException e) {
                // expected once aborted
            }
        });
        writer.start();
        Thread.sleep(4 * DEADLINE_MILLIS);
        assertEquals(0, aborts.get(), "aborted before the response committed");
        committed.set(true);
        writer.join(10_000);
        assertFalse(writer.isAlive());
        assertEquals(1, aborts.get());
    }

    @Test
    public void testCompletedResponseNeverAborted() throws Exception {
        AtomicInteger aborts = new AtomicInteger();
        CountingSink sink = new CountingSink();
        DeadlineOutputStream out = new DeadlineOutputStream(sink, DEADLINE_MILLIS, scheduler,
                () -> true, aborts::incrementAndGet, null);
        out.write(new byte[100]);
        out.close();
        out.complete();
        Thread.sleep(3 * DEADLINE_MILLIS);
        assertEquals(0, aborts.get());
        assertEquals(100, sink.count);
    }

    /** A write that blocks until the connection is closed under it, as a full socket does. */
    private static final class BlockingSink extends OutputStream {
        private final CountDownLatch connectionClosed = new CountDownLatch(1);
        private volatile boolean closedByStream;

        void closeConnection() {
            connectionClosed.countDown();
        }

        @Override
        public void write(int b) throws IOException {
            block();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            block();
        }

        private void block() throws IOException {
            try {
                connectionClosed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("connection closed");
        }

        @Override
        public void close() {
            closedByStream = true;
        }
    }

    private static final class CountingSink extends OutputStream {
        private long count;

        @Override
        public void write(int b) {
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            count += len;
        }
    }
}
