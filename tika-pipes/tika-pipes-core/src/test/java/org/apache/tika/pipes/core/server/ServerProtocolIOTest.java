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
package org.apache.tika.pipes.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.emitter.EmitDataImpl;
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.protocol.PipesMessageType;
import org.apache.tika.pipes.core.serialization.JsonPipesIpc;

/**
 * Unit tests for payload-size protection in {@link ServerProtocolIO#writeFinished}
 * and timeout-limit clamping in {@link ServerProtocolIO#clampRequestTimeoutLimits}.
 */
class ServerProtocolIOTest {

    private static final long MAX = 3_600_000L;

    // ---- timeout clamping tests ----

    @Test
    public void testRequestLimitsOverCapAreClamped() {
        ParseContext request = new ParseContext();
        request.set(TimeoutLimits.class, new TimeoutLimits(Long.MAX_VALUE, Long.MAX_VALUE));
        ParseContext merged = new ParseContext();
        merged.copyFrom(request);

        ServerProtocolIO.clampRequestTimeoutLimits(request, merged, MAX);

        TimeoutLimits limits = merged.get(TimeoutLimits.class);
        assertEquals(MAX, limits.getTotalTaskTimeoutMillis());
        assertEquals(MAX, limits.getProgressTimeoutMillis());
    }

    @Test
    public void testUnresolvedJsonRequestLimitsAlsoTriggerClamp() {
        ParseContext request = new ParseContext();
        request.setJsonConfig("timeout-limits", "{\"totalTaskTimeoutMillis\": 9999999999}");
        ParseContext merged = new ParseContext();
        merged.set(TimeoutLimits.class, new TimeoutLimits(9_999_999_999L, 120_000));

        ServerProtocolIO.clampRequestTimeoutLimits(request, merged, MAX);

        assertEquals(MAX, merged.get(TimeoutLimits.class).getTotalTaskTimeoutMillis());
    }

    @Test
    public void testServerConfigLimitsAreTrustedAndNeverClamped() {
        ParseContext request = new ParseContext();
        ParseContext merged = new ParseContext();
        merged.set(TimeoutLimits.class, new TimeoutLimits(7_200_000L, 120_000));

        ServerProtocolIO.clampRequestTimeoutLimits(request, merged, MAX);

        assertEquals(7_200_000L, merged.get(TimeoutLimits.class).getTotalTaskTimeoutMillis());
    }

    // ---- payload guard tests ----

    /**
     * Runs a writeFinished() call through a pair of piped streams, acting as the
     * "client" in a background thread: reads the FINISHED message using the same
     * {@code maxPayloadBytes} limit the server uses, sends ACK, and returns the
     * deserialized PipesResult.
     */
    private PipesResult exchange(PipesResult toWrite, int maxPayloadBytes) throws Exception {
        PipedOutputStream serverOutPipe = new PipedOutputStream();
        PipedInputStream clientInPipe = new PipedInputStream(serverOutPipe, 1024 * 1024);
        PipedOutputStream clientOutPipe = new PipedOutputStream();
        PipedInputStream serverInPipe = new PipedInputStream(clientOutPipe, 1024);

        AtomicReference<PipesResult> received = new AtomicReference<>();
        AtomicReference<Exception> clientError = new AtomicReference<>();

        Thread clientThread = new Thread(() -> {
            try {
                DataInputStream clientDis = new DataInputStream(clientInPipe);
                DataOutputStream clientDos = new DataOutputStream(clientOutPipe);

                // Read with the same limit the server uses — mirrors production PipesClient behaviour.
                PipesMessage msg = PipesMessage.read(clientDis, maxPayloadBytes);
                assertEquals(PipesMessageType.FINISHED, msg.type());
                received.set(JsonPipesIpc.fromBytes(msg.payload(), PipesResult.class));
                PipesMessage.ack().write(clientDos);
            } catch (Exception e) {
                clientError.set(e);
            }
        });
        clientThread.setDaemon(true);
        clientThread.start();

        ServerProtocolIO io = new ServerProtocolIO(
                new DataInputStream(serverInPipe),
                new DataOutputStream(serverOutPipe),
                maxPayloadBytes, new ExceptionReporting());
        io.writeFinished(toWrite);

        clientThread.join(5000);

        if (clientError.get() != null) {
            throw clientError.get();
        }
        assertNotNull(received.get(), "client never received a FINISHED message");
        return received.get();
    }

    /**
     * A result whose serialized size is under the configured limit passes through
     * unchanged — original status is preserved.
     */
    @Test
    void testSmallResultPassesThrough() throws Exception {
        PipesResult original = new PipesResult(PipesResult.RESULT_STATUS.PARSE_SUCCESS,
                new EmitDataImpl("key", List.of(new Metadata())));

        PipesResult returned = exchange(original, PipesMessage.MAX_PAYLOAD_BYTES);

        assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS, returned.status());
    }

    /**
     * When the serialized payload is one byte over the configured limit the
     * BoundedOutputStream aborts serialization mid-stream and the server returns
     * PAYLOAD_LIMIT_EXCEEDED instead of writing the oversized frame.
     */
    @Test
    void testPayloadOneByteTooLargeReturnPayloadLimitExceeded() throws Exception {
        Metadata m = new Metadata();
        m.add("content", "a".repeat(500));
        PipesResult big = new PipesResult(PipesResult.RESULT_STATUS.PARSE_SUCCESS,
                new EmitDataImpl("mykey", List.of(m)));

        byte[] serialized = JsonPipesIpc.toBytes(big);
        int tinyLimit = serialized.length - 1;

        PipesResult returned = exchange(big, tinyLimit);

        assertEquals(PipesResult.RESULT_STATUS.PAYLOAD_LIMIT_EXCEEDED, returned.status());
    }

    /**
     * A result whose serialized size clearly exceeds the configured limit triggers
     * PAYLOAD_LIMIT_EXCEEDED. Uses a fixed small limit so the test is independent of
     * the estimate formula.
     */
    @Test
    void testLargePayloadReturnPayloadLimitExceeded() throws Exception {
        Metadata m = new Metadata();
        m.add("content", "x".repeat(10_000));
        PipesResult result = new PipesResult(PipesResult.RESULT_STATUS.PARSE_SUCCESS,
                new EmitDataImpl("key", List.of(m)));

        // The 10,000-char content serializes to ~10 KB; pick a limit well below that.
        int limit = 512;
        assertTrue(JsonPipesIpc.toBytes(result).length > limit,
                "test setup: serialized content must exceed limit");

        PipesResult returned = exchange(result, limit);

        assertEquals(PipesResult.RESULT_STATUS.PAYLOAD_LIMIT_EXCEEDED, returned.status());
    }

    /**
     * Status-only results (no emitData) always pass through unchanged.
     */
    @Test
    void testStatusOnlyResultPassesThrough() throws Exception {
        PipesResult statusOnly = new PipesResult(PipesResult.RESULT_STATUS.FETCH_EXCEPTION,
                "something went wrong");

        PipesResult returned = exchange(statusOnly, 1024);

        assertEquals(PipesResult.RESULT_STATUS.FETCH_EXCEPTION, returned.status());
    }

    /**
     * Regression for the "fallback-too-big" bug: when the configured limit equals
     * MIN_FALLBACK_PAYLOAD_BYTES (the tightest limit the constructor accepts), the
     * fallback PAYLOAD_LIMIT_EXCEEDED frame must still fit within that limit so the
     * client can read it with the same configured limit.
     */
    @Test
    void testFallbackFitsWithinMinimumConfiguredLimit() throws Exception {
        int limit = ServerProtocolIO.MIN_FALLBACK_PAYLOAD_BYTES;

        Metadata m = new Metadata();
        m.add("content", "x".repeat(10_000));
        PipesResult result = new PipesResult(PipesResult.RESULT_STATUS.PARSE_SUCCESS,
                new EmitDataImpl("key", List.of(m)));

        PipesResult returned = exchange(result, limit);

        assertEquals(PipesResult.RESULT_STATUS.PAYLOAD_LIMIT_EXCEEDED, returned.status());
    }

    /**
     * When an EMIT_SUCCESS_PASSBACK result overflows the limit, the server preserves
     * the EMIT_SUCCESS_PASSBACK status rather than replacing it with PAYLOAD_LIMIT_EXCEEDED,
     * so the client does not re-emit content that was already emitted server-side.
     */
    @Test
    void testEmitSuccessPassbackStatusPreservedOnOverflow() throws Exception {
        Metadata m = new Metadata();
        m.add("content", "x".repeat(10_000));
        PipesResult result = new PipesResult(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PASSBACK,
                new EmitDataImpl("key", List.of(m)));

        int limit = 512;
        assertTrue(JsonPipesIpc.toBytes(result).length > limit,
                "test setup: serialized content must exceed limit");

        PipesResult returned = exchange(result, limit);

        assertEquals(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PASSBACK, returned.status());
    }

    /**
     * EMIT_SUCCESS_PARSE_EXCEPTION also means the content was already emitted server-side,
     * so an overflow must not downgrade it to a failure status -- that would make the
     * client record an emitted document as failed and a retry would emit it twice.
     */
    @Test
    void testEmitSuccessParseExceptionStatusPreservedOnOverflow() throws Exception {
        Metadata m = new Metadata();
        m.add("content", "x".repeat(10_000));
        PipesResult result =
                new PipesResult(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PARSE_EXCEPTION,
                        new EmitDataImpl("key", List.of(m)));

        int limit = 512;
        assertTrue(JsonPipesIpc.toBytes(result).length > limit,
                "test setup: serialized content must exceed limit");

        PipesResult returned = exchange(result, limit);

        assertEquals(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PARSE_EXCEPTION, returned.status());
    }

    /**
     * The overflow can be the message itself -- EmitHandler accumulates parse-exception
     * stacks into it -- so the status-preserving retry must replace the message, not
     * carry it over.
     */
    @Test
    void testOversizedMessageOnAlreadyEmittedStatusPreservesStatus() throws Exception {
        PipesResult result =
                new PipesResult(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PARSE_EXCEPTION,
                        "stack".repeat(5_000));

        int limit = 512;
        assertTrue(JsonPipesIpc.toBytes(result).length > limit,
                "test setup: serialized message must exceed limit");

        PipesResult returned = exchange(result, limit);

        assertEquals(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PARSE_EXCEPTION, returned.status());
    }

    // ---- writeCrash tests ----

    /**
     * Helper: calls writeCrash on the server side, reads the crash message from the client side,
     * sends ACK, and returns the deserialized stack trace string.
     */
    private String exchangeCrash(Throwable t, int maxPayloadBytes) throws Exception {
        return exchangeCrash(t, maxPayloadBytes, new ExceptionReporting());
    }

    private String exchangeCrash(Throwable t, int maxPayloadBytes, ExceptionReporting reporting)
            throws Exception {
        PipedOutputStream serverOutPipe = new PipedOutputStream();
        PipedInputStream clientInPipe = new PipedInputStream(serverOutPipe, 1024 * 1024);
        PipedOutputStream clientOutPipe = new PipedOutputStream();
        PipedInputStream serverInPipe = new PipedInputStream(clientOutPipe, 1024);

        AtomicReference<String> received = new AtomicReference<>();
        AtomicReference<Exception> clientError = new AtomicReference<>();

        Thread clientThread = new Thread(() -> {
            try {
                DataInputStream clientDis = new DataInputStream(clientInPipe);
                DataOutputStream clientDos = new DataOutputStream(clientOutPipe);

                PipesMessage msg = PipesMessage.read(clientDis, maxPayloadBytes);
                assertEquals(PipesMessageType.UNSPECIFIED_CRASH, msg.type());
                received.set(JsonPipesIpc.fromBytes(msg.payload(), String.class));
                PipesMessage.ack().write(clientDos);
            } catch (Exception e) {
                clientError.set(e);
            }
        });
        clientThread.setDaemon(true);
        clientThread.start();

        ServerProtocolIO io = new ServerProtocolIO(
                new DataInputStream(serverInPipe),
                new DataOutputStream(serverOutPipe),
                maxPayloadBytes, reporting);
        io.writeCrash(PipesMessageType.UNSPECIFIED_CRASH, t);

        clientThread.join(5000);
        if (clientError.get() != null) {
            throw clientError.get();
        }
        assertNotNull(received.get(), "client never received a CRASH message");
        return received.get();
    }

    /**
     * A crash with a small stack trace passes through unchanged and the client reads
     * it within the configured limit.
     */
    @Test
    void testCrashSmallPayloadPassesThrough() throws Exception {
        RuntimeException ex = new RuntimeException("something failed");
        String returned = exchangeCrash(ex, PipesMessage.MAX_PAYLOAD_BYTES);
        assertTrue(returned.contains("something failed"));
    }

    /**
     * A crash whose stack trace would overflow the limit falls back to an empty string,
     * not a truncated string that could still exceed the limit due to multi-byte encoding.
     * The client must be able to read the response with the same configured limit.
     */
    @Test
    void testCrashOversizedPayloadFallsBackToEmptyString() throws Exception {
        // Build a large exception message that will serialize beyond a small limit.
        RuntimeException ex = new RuntimeException("x".repeat(10_000));
        int limit = 512;

        String returned = exchangeCrash(ex, limit);

        // Empty string fallback: the trace was too large, we get an empty payload.
        assertEquals("", returned);
    }

    @Test
    void testCrashHonorsExceptionReporting() throws Exception {
        RuntimeException ex = new RuntimeException("something failed");
        String returned = exchangeCrash(ex, PipesMessage.MAX_PAYLOAD_BYTES,
                new ExceptionReporting(ExceptionReporting.Level.MESSAGE_REDACTED, -1));
        assertTrue(returned.contains("java.lang.RuntimeException"));
        assertFalse(returned.contains("something failed"));
    }
}
