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
package org.apache.tika.pipes.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.apache.tika.grpc.v2.FetchAndParseBiDirectionalStreamingRequest;
import org.apache.tika.grpc.v2.FetchAndParseBiDirectionalStreamingResponse;
import org.apache.tika.grpc.v2.FetchAndParseRequest;
import org.apache.tika.grpc.v2.FetchAndParseResponse;
import org.apache.tika.grpc.v2.FetchAndParseServerSideStreamingRequest;
import org.apache.tika.grpc.v2.FetchAndParseServerSideStreamingResponse;
import org.apache.tika.grpc.v2.ParseStatus;
import org.apache.tika.grpc.v2.TikaV2ServiceGrpc;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.PipesResult;

/**
 * The v2 FetchAndParse error paths and cancellation, with a mocked
 * {@link TikaGrpcServerImpl}: no forked pipes worker.
 */
class TikaGrpcV2ServerImplTest {

    private static final String STACK =
            "org.apache.tika.exception.TikaException: Unexpected RuntimeException\n\tat x.y(z)";

    private TikaGrpcServerImpl v1;
    private TikaGrpcV2ServerImpl v2;
    private final List<AutoCloseable> closeables = new ArrayList<>();

    @BeforeEach
    void setUp() {
        v1 = Mockito.mock(TikaGrpcServerImpl.class);
        Mockito.when(v1.denyPerRequestConfig(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(false);
        Mockito.when(v1.buildRequestParseContext(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any())).thenReturn(new ParseContext());
        v2 = new TikaGrpcV2ServerImpl(v1);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (AutoCloseable closeable : closeables) {
            closeable.close();
        }
    }

    static final class Recording<T> implements StreamObserver<T> {
        final List<T> replies = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        int completions;

        @Override
        public void onNext(T reply) {
            replies.add(reply);
        }

        @Override
        public void onError(Throwable t) {
            errors.add(t);
        }

        @Override
        public void onCompleted() {
            completions++;
        }
    }

    private static TikaGrpcServerImpl.FetchParseOutcome outcome(String key, String status,
                                                                Metadata primary,
                                                                String diagnostic) {
        return new TikaGrpcServerImpl.FetchParseOutcome(key, status, null, diagnostic,
                Map.of(), primary, 3L);
    }

    private static FetchAndParseRequest request(String key) {
        return FetchAndParseRequest.newBuilder().setFetcherId("fs").setFetchKey(key).build();
    }

    /** A parse that threw returns a failed Document that says why. */
    @Test
    void aFailedParseReturnsThePipesError() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome("bad.pdf", "PARSE_EXCEPTION_NO_EMIT", null, STACK));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(request("bad.pdf"), observer);

        assertEquals(1, observer.replies.size());
        assertEquals(1, observer.completions);
        ParseStatus status = observer.replies.get(0).getDocument().getStatus();
        assertEquals(ParseStatus.Status.STATUS_FAILED, status.getStatus());
        assertEquals(List.of(STACK), status.getErrorsList());
    }

    /** A fetch failure carries the fetcher's message, as v1's error_message does. */
    @Test
    void aFetchFailureReturnsTheFetchersMessage() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome("missing.txt", "FETCH_EXCEPTION", null,
                        "java.nio.file.NoSuchFileException: missing.txt"));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(request("missing.txt"), observer);

        assertEquals(List.of("java.nio.file.NoSuchFileException: missing.txt"),
                observer.replies.get(0).getDocument().getStatus().getErrorsList());
    }

    /** A document with no text and no metadata is a clean success, without errors. */
    @Test
    void emptyOutputIsASuccessWithoutErrors() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome("empty.txt", "EMPTY_OUTPUT", null, null));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(request("empty.txt"), observer);

        ParseStatus status = observer.replies.get(0).getDocument().getStatus();
        assertEquals(ParseStatus.Status.STATUS_SUCCESS, status.getStatus());
        assertTrue(status.getErrorsList().isEmpty(), "errors=" + status.getErrorsList());
    }

    @Test
    void anUnknownFetcherIsNotFound() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new TikaGrpcServerImpl.FetcherNotFoundException("nope", null));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(request("a"), observer);

        assertEquals(0, observer.completions);
        assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(observer.errors.get(0)).getCode());
    }

    /** An unexpected failure is INTERNAL, and its cause stays in the server log. */
    @Test
    void anUnexpectedFailureIsInternalWithoutTheCause() throws Exception {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new RuntimeException("secret /var/lib/tika path"));
        Recording<FetchAndParseResponse> fetch = new Recording<>();

        v2.fetchAndParse(request("a"), fetch);

        assertEquals(0, fetch.completions);
        Status status = Status.fromThrowable(fetch.errors.get(0));
        assertEquals(Status.Code.INTERNAL, status.getCode());
        assertFalse(status.getDescription().contains("secret"), status.getDescription());
    }

    /**
     * Once one request on a bidirectional stream closes the call with an error, later
     * requests and the client's completion are dropped instead of hitting a closed call.
     */
    @Test
    void aBidiStreamIgnoresRequestsAfterAnError() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.eq("bad"), Mockito.any(), Mockito.any()))
                .thenThrow(new TikaGrpcServerImpl.FetcherNotFoundException("fs", null));
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.eq("good"), Mockito.any(), Mockito.any()))
                .thenReturn(outcome("good", "PARSE_SUCCESS", html(), null));
        Recording<FetchAndParseBiDirectionalStreamingResponse> observer = new Recording<>();

        StreamObserver<FetchAndParseBiDirectionalStreamingRequest> requests =
                v2.fetchAndParseBiDirectionalStreaming(observer);
        requests.onNext(bidi("good"));
        requests.onNext(bidi("bad"));
        requests.onNext(bidi("good"));
        requests.onCompleted();

        assertEquals(1, observer.replies.size(), "only the request before the error is answered");
        assertEquals(1, observer.errors.size());
        assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(observer.errors.get(0)).getCode());
        assertEquals(0, observer.completions, "a call closed with an error is not completed too");
    }

    /**
     * Pool exhaustion is the server's problem, not the document's: the call closes with
     * RESOURCE_EXHAUSTED, which retry policies keyed on status codes act on, not OK with a
     * failed Document.
     */
    @Test
    void noFreeWorkerIsResourceExhausted() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome("a", "CLIENT_UNAVAILABLE_WITHIN_MS", null, null));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(request("a"), observer);

        assertTrue(observer.replies.isEmpty());
        assertEquals(0, observer.completions);
        assertEquals(Status.Code.RESOURCE_EXHAUSTED,
                Status.fromThrowable(observer.errors.get(0)).getCode());
    }

    @Test
    void aPipesRuntimeThatFailedToStartIsUnavailable() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome("a", "FAILED_TO_INITIALIZE", null, "boom"));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(request("a"), observer);

        Status status = Status.fromThrowable(observer.errors.get(0));
        assertEquals(Status.Code.UNAVAILABLE, status.getCode());
        assertFalse(status.getDescription().contains("boom"), status.getDescription());
    }

    /** The fork not finding the fetcher answers as the server's own check does. */
    @Test
    void aFetcherTheWorkerCannotFindIsNotFound() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome("a", "FETCHER_NOT_FOUND", null, null));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(request("a"), observer);

        assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(observer.errors.get(0)).getCode());
    }

    /**
     * A worker dying on a document (timeout, OOM, crash) is about that document: it stays a
     * failed Document whose pipes_status says which, not a call error.
     */
    @Test
    void aWorkerCrashIsAFailedDocument() {
        for (String documentLevel : List.of("TIMEOUT", "OOM", "UNSPECIFIED_CRASH",
                "PAYLOAD_LIMIT_EXCEEDED", "FETCH_EXCEPTION", "PARSE_EXCEPTION_NO_EMIT")) {
            assertNull(TikaGrpcV2ServerImpl.serviceFailure(documentLevel), documentLevel);
        }
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome("a", "TIMEOUT", null, null));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(request("a"), observer);

        ParseStatus status = observer.replies.get(0).getDocument().getStatus();
        assertEquals(ParseStatus.Status.STATUS_FAILED, status.getStatus());
        assertEquals("TIMEOUT", status.getPipesStatus());
    }

    /**
     * By default a cancel does not interrupt a parse already running on a worker: in the
     * per-client server mode that would restart the worker JVM. The parse finishes and
     * nothing is sent to the client that left.
     */
    @Test
    void cancellingLetsARunningParseFinishByDefault() throws Exception {
        CountDownLatch parsing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    TikaGrpcServerImpl.Dispatch dispatch = invocation.getArgument(3);
                    if (!dispatch.starting()) {
                        return null;
                    }
                    parsing.countDown();
                    try {
                        release.await(1, TimeUnit.MINUTES);
                        return outcome("slow", "PARSE_SUCCESS", html(), null);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                        return null;
                    } finally {
                        finished.countDown();
                    }
                });
        TikaV2ServiceGrpc.TikaV2ServiceStub stub = TikaV2ServiceGrpc.newStub(channel());
        Recording<FetchAndParseResponse> observer = new Recording<>();

        Context.CancellableContext context = Context.current().withCancellation();
        context.run(() -> stub.fetchAndParse(request("slow"), observer));
        assertTrue(parsing.await(10, TimeUnit.SECONDS), "the parse never started");
        context.cancel(null);
        Thread.sleep(200);
        release.countDown();

        assertTrue(finished.await(10, TimeUnit.SECONDS));
        assertFalse(interrupted.get(), "a running parse must not be interrupted by default");
        assertTrue(observer.replies.isEmpty(), "nothing is sent for a cancelled call");
    }

    /** With cancelRunningParse a cancel interrupts the parse running on the worker. */
    @Test
    void cancelRunningParseInterruptsARunningParse() {
        TikaGrpcV2ServerImpl.Cancellation cancellation =
                new TikaGrpcV2ServerImpl.Cancellation(true);
        assertTrue(cancellation.begin());
        assertTrue(cancellation.dispatching());
        cancellation.cancel();
        assertTrue(Thread.currentThread().isInterrupted());
        cancellation.end();
        assertFalse(Thread.currentThread().isInterrupted());
    }

    /** By default a running parse is left alone, and a waiting one is interrupted. */
    @Test
    void aCancelInterruptsOnlyAWaitingParseByDefault() {
        TikaGrpcV2ServerImpl.Cancellation running = new TikaGrpcV2ServerImpl.Cancellation();
        assertTrue(running.begin());
        assertTrue(running.dispatching());
        running.cancel();
        assertFalse(Thread.currentThread().isInterrupted(), "a running parse is not interrupted");
        running.end();

        TikaGrpcV2ServerImpl.Cancellation waiting = new TikaGrpcV2ServerImpl.Cancellation();
        assertTrue(waiting.begin());
        waiting.cancel();
        assertTrue(Thread.currentThread().isInterrupted(), "a waiting parse is interrupted");
        assertFalse(waiting.dispatching(), "a worker that frees up after the cancel goes back unused");
        waiting.end();
        assertFalse(Thread.currentThread().isInterrupted());
    }

    /**
     * Unless the operator set parse-context.exception-reporting, a caller gets the first line
     * of the pipes diagnostic, not the server's stack trace.
     */
    @Test
    void callersGetOnlyTheFirstLineOfAStackTraceByDefault() {
        PipesResult result = new PipesResult(PipesResult.RESULT_STATUS.PARSE_EXCEPTION_NO_EMIT,
                "org.apache.tika.exception.TikaException: bad header\n"
                        + "\tat org.apache.pdfbox.Foo.bar(Foo.java:12)\n"
                        + "\tat /opt/tika/lib/x.jar");
        assertEquals("org.apache.tika.exception.TikaException: bad header",
                TikaGrpcServerImpl.diagnostic(result, false));
        assertEquals(result.message(), TikaGrpcServerImpl.diagnostic(result, true),
                "an operator's own exception-reporting policy is passed through as formatted");

        PipesResult huge = new PipesResult(PipesResult.RESULT_STATUS.FETCH_EXCEPTION,
                "x".repeat(5000));
        String cut = TikaGrpcServerImpl.diagnostic(huge, false);
        assertEquals(TikaGrpcServerImpl.DEFAULT_DIAGNOSTIC_MAX_CHARS + "...[truncated]".length(),
                cut.length());
    }

    /**
     * A client that cancels a FetchAndParse call while it waits for a pipes worker stops
     * the wait at once.
     */
    @Test
    void cancellingTheCallInterruptsTheParse() throws Exception {
        CountDownLatch parsing = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    parsing.countDown();
                    try {
                        Thread.sleep(TimeUnit.MINUTES.toMillis(1));
                        return outcome("slow", "PARSE_SUCCESS", html(), null);
                    } catch (InterruptedException e) {
                        // What runFetchAndParse does when the pipes client is interrupted.
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                        return null;
                    } finally {
                        finished.countDown();
                    }
                });
        TikaV2ServiceGrpc.TikaV2ServiceStub stub = TikaV2ServiceGrpc.newStub(channel());
        Recording<FetchAndParseResponse> observer = new Recording<>();

        Context.CancellableContext context = Context.current().withCancellation();
        context.run(() -> stub.fetchAndParse(request("slow"), observer));
        assertTrue(parsing.await(10, TimeUnit.SECONDS), "the parse never started");
        context.cancel(null);

        assertTrue(finished.await(10, TimeUnit.SECONDS), "the parse was not interrupted");
        assertTrue(interrupted.get(), "the parse thread must see the interrupt");
    }

    /** A cancelled call never starts the parse of a request that arrives after the cancel. */
    @Test
    void aCancelledCallStartsNoParse() {
        TikaGrpcV2ServerImpl.Cancellation cancellation = new TikaGrpcV2ServerImpl.Cancellation();
        assertTrue(cancellation.begin());
        cancellation.end();
        cancellation.cancel();

        assertFalse(cancellation.begin(), "no parse starts once the call is cancelled");
        assertFalse(Thread.currentThread().isInterrupted(),
                "cancel interrupts only a thread that is parsing");
    }

    /** The interrupt a cancel sends does not leak into the next call the thread serves. */
    @Test
    void endClearsTheCancelInterrupt() {
        TikaGrpcV2ServerImpl.Cancellation cancellation = new TikaGrpcV2ServerImpl.Cancellation();
        assertTrue(cancellation.begin());
        cancellation.cancel();
        assertTrue(Thread.currentThread().isInterrupted());

        cancellation.end();

        assertFalse(Thread.currentThread().isInterrupted());
    }

    /** Requests answered on a bidi stream count once each, through a real gRPC call. */
    @Test
    void aBidiStreamAnswersEveryRequestOverGrpc() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    return outcome(invocation.getArgument(1), "PARSE_SUCCESS", html(), null);
                });
        TikaV2ServiceGrpc.TikaV2ServiceStub stub = TikaV2ServiceGrpc.newStub(channel());
        CountDownLatch done = new CountDownLatch(1);
        List<String> keys = new ArrayList<>();
        StreamObserver<FetchAndParseBiDirectionalStreamingRequest> requests =
                stub.fetchAndParseBiDirectionalStreaming(new StreamObserver<>() {
                    @Override
                    public void onNext(FetchAndParseBiDirectionalStreamingResponse reply) {
                        synchronized (keys) {
                            keys.add(reply.getFetchKey());
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        done.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        done.countDown();
                    }
                });
        requests.onNext(bidi("one"));
        requests.onNext(bidi("two"));
        requests.onCompleted();

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("one", "two"), keys);
        assertEquals(2, calls.get());
    }

    private ManagedChannel channel() throws Exception {
        String name = InProcessServerBuilder.generateName();
        // Not directExecutor: the server must run the parse on its own thread for the
        // cancel to have a thread to interrupt.
        Server server = InProcessServerBuilder.forName(name)
                .addService(v2)
                .build()
                .start();
        closeables.add(() -> server.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
        ManagedChannel channel = InProcessChannelBuilder.forName(name).build();
        closeables.add(0, () -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
        return channel;
    }

    /** A fetcher that exists but cannot be built is the server's fault: UNAVAILABLE. */
    @Test
    void aFetcherThatCannotBeLoadedIsUnavailable() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new TikaGrpcServerImpl.FetcherUnavailableException("fs",
                        new java.io.IOException("secret store path")));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(request("a"), observer);

        Status status = Status.fromThrowable(observer.errors.get(0));
        assertEquals(Status.Code.UNAVAILABLE, status.getCode());
        assertFalse(status.getDescription().contains("secret"), status.getDescription());
    }

    @Test
    void aFetcherThatFailedToInitializeInTheWorkerIsUnavailable() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome("a", "FETCHER_INITIALIZATION_EXCEPTION", null, "boom"));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(request("a"), observer);

        assertTrue(observer.replies.isEmpty());
        assertEquals(Status.Code.UNAVAILABLE, Status.fromThrowable(observer.errors.get(0)).getCode());
    }

    /**
     * additional_fetch_config_json for a fetcher that does not exist is NOT_FOUND, not the
     * INVALID_ARGUMENT the config check would give.
     */
    @Test
    void additionalConfigForAMissingFetcherIsNotFound() {
        Mockito.when(v1.resolveFetcher("nope"))
                .thenThrow(new TikaGrpcServerImpl.FetcherNotFoundException("nope", null));
        Recording<FetchAndParseResponse> observer = new Recording<>();

        v2.fetchAndParse(FetchAndParseRequest.newBuilder().setFetcherId("nope").setFetchKey("a")
                .setAdditionalFetchConfigJson("{\"basePath\":\"/x\"}").build(), observer);

        assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(observer.errors.get(0)).getCode());
        Mockito.verify(v1, Mockito.never()).buildRequestParseContext(Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any());
    }

    @Test
    void serverStreamingClosesWithTheSameStatusCodes() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new TikaGrpcServerImpl.FetcherNotFoundException("fs", null));
        Recording<FetchAndParseServerSideStreamingResponse> observer = new Recording<>();

        v2.fetchAndParseServerSideStreaming(FetchAndParseServerSideStreamingRequest.newBuilder()
                .setFetcherId("fs").setFetchKey("a").build(), observer);

        assertEquals(0, observer.completions);
        assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(observer.errors.get(0)).getCode());
    }

    @Test
    void aBidiStreamClosesWithTheFailuresStatusCode() {
        Mockito.when(v1.runFetchAndParse(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(outcome("a", "CLIENT_UNAVAILABLE_WITHIN_MS", null, null));
        Recording<FetchAndParseBiDirectionalStreamingResponse> observer = new Recording<>();

        StreamObserver<FetchAndParseBiDirectionalStreamingRequest> requests =
                v2.fetchAndParseBiDirectionalStreaming(observer);
        requests.onNext(bidi("a"));

        assertEquals(Status.Code.RESOURCE_EXHAUSTED,
                Status.fromThrowable(observer.errors.get(0)).getCode());
    }

    /** An interrupt from outside the call (a shutdown) does not leak into the next call. */
    @Test
    void endClearsAnInterruptEvenWithoutACancel() {
        TikaGrpcV2ServerImpl.Cancellation cancellation = new TikaGrpcV2ServerImpl.Cancellation();
        assertTrue(cancellation.begin());
        Thread.currentThread().interrupt();
        cancellation.end();
        assertFalse(Thread.currentThread().isInterrupted());
    }

    /** A Windows-style CRLF stack trace is cut at its first line too. */
    @Test
    void aCrlfDiagnosticIsCutAtTheFirstLine() {
        PipesResult result = new PipesResult(PipesResult.RESULT_STATUS.FETCH_EXCEPTION,
                "java.io.IOException: denied\r\n\tat C:\\tika\\x.jar");
        assertEquals("java.io.IOException: denied", TikaGrpcServerImpl.diagnostic(result, false));
    }

    /**
     * A v2 request from a client built before emitter_id (field 4) was removed still parses;
     * the old field is kept only as an unknown field and nothing reads it.
     */
    @Test
    void anOldClientsEmitterIdIsIgnored() throws Exception {
        byte[] old = FetchAndParseRequest.newBuilder().setFetcherId("fs").setFetchKey("a")
                .setUnknownFields(com.google.protobuf.UnknownFieldSet.newBuilder()
                        .addField(4, com.google.protobuf.UnknownFieldSet.Field.newBuilder()
                                .addLengthDelimited(com.google.protobuf.ByteString
                                        .copyFromUtf8("my-emitter")).build())
                        .build())
                .build().toByteArray();

        FetchAndParseRequest parsed = FetchAndParseRequest.parseFrom(old);

        assertEquals("fs", parsed.getFetcherId());
        assertEquals("a", parsed.getFetchKey());
        assertTrue(parsed.getUnknownFields().hasField(4));
    }

    private static Metadata html() {
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "text/html");
        return metadata;
    }

    private static FetchAndParseBiDirectionalStreamingRequest bidi(String key) {
        return FetchAndParseBiDirectionalStreamingRequest.newBuilder()
                .setFetcherId("fs")
                .setFetchKey(key)
                .build();
    }
}
