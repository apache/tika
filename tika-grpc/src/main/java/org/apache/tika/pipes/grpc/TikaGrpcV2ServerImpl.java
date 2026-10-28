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

import java.util.function.BiFunction;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.mapper.DocumentBuilder;
import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.FetchAndParseBiDirectionalStreamingRequest;
import org.apache.tika.grpc.v2.FetchAndParseBiDirectionalStreamingResponse;
import org.apache.tika.grpc.v2.FetchAndParseRequest;
import org.apache.tika.grpc.v2.FetchAndParseResponse;
import org.apache.tika.grpc.v2.FetchAndParseServerSideStreamingRequest;
import org.apache.tika.grpc.v2.FetchAndParseServerSideStreamingResponse;
import org.apache.tika.grpc.v2.TikaV2ServiceGrpc;
import org.apache.tika.parser.ParseContext;

/**
 * Experimental v2 parse surface. Reuses the same pipes/fetcher runtime as the v1
 * {@link TikaGrpcServerImpl}; fetcher management stays on v1. Replies carry the typed
 * {@link Document} contract instead of the v1 fields map.
 * <p>
 * A client that cancels a call stops its parse: the thread running it is interrupted, which
 * the pipes client answers by abandoning the forked worker's connection, so a cancelled call
 * does not hold a pipes worker until its parse ends.
 */
class TikaGrpcV2ServerImpl extends TikaV2ServiceGrpc.TikaV2ServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaGrpcV2ServerImpl.class);

    private final TikaGrpcServerImpl v1;

    TikaGrpcV2ServerImpl(TikaGrpcServerImpl v1) {
        this.v1 = v1;
    }

    @Override
    public void fetchAndParse(FetchAndParseRequest request,
                              StreamObserver<FetchAndParseResponse> responseObserver) {
        Cancellation cancellation = Cancellation.of(responseObserver);
        FetchRequest fetch = new FetchRequest(request.getFetcherId(), request.getFetchKey(),
                request.getAdditionalFetchConfigJson(), request.getParseContextJson());
        if (reply(fetch, responseObserver, cancellation, (fetchKey, document) ->
                FetchAndParseResponse
                        .newBuilder()
                        .setFetchKey(fetchKey)
                        .setDocument(document)
                        .build())) {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void fetchAndParseServerSideStreaming(FetchAndParseServerSideStreamingRequest request,
            StreamObserver<FetchAndParseServerSideStreamingResponse> responseObserver) {
        Cancellation cancellation = Cancellation.of(responseObserver);
        FetchRequest fetch = new FetchRequest(request.getFetcherId(), request.getFetchKey(),
                request.getAdditionalFetchConfigJson(), request.getParseContextJson());
        if (reply(fetch, responseObserver, cancellation, (fetchKey, document) ->
                FetchAndParseServerSideStreamingResponse
                        .newBuilder()
                        .setFetchKey(fetchKey)
                        .setDocument(document)
                        .build())) {
            responseObserver.onCompleted();
        }
    }

    @Override
    public StreamObserver<FetchAndParseBiDirectionalStreamingRequest> fetchAndParseBiDirectionalStreaming(
            StreamObserver<FetchAndParseBiDirectionalStreamingResponse> responseObserver) {
        Cancellation cancellation = Cancellation.of(responseObserver);
        return new StreamObserver<>() {
            // Set once the call is closed, by an error sent for one request or by the client.
            // The client may still have requests in flight; they are dropped, because a
            // closed call accepts no more replies.
            private boolean closed;

            @Override
            public void onNext(FetchAndParseBiDirectionalStreamingRequest request) {
                if (closed) {
                    return;
                }
                FetchRequest fetch = new FetchRequest(request.getFetcherId(),
                        request.getFetchKey(), request.getAdditionalFetchConfigJson(),
                        request.getParseContextJson());
                closed = !reply(fetch, responseObserver, cancellation, (fetchKey, document) ->
                        FetchAndParseBiDirectionalStreamingResponse
                                .newBuilder()
                                .setFetchKey(fetchKey)
                                .setDocument(document)
                                .build());
            }

            @Override
            public void onError(Throwable throwable) {
                // The client cancelled or failed the stream; the cancel handler has already
                // stopped any parse in progress.
                closed = true;
                LOG.debug("v2 bidirectional stream closed by the client", throwable);
            }

            @Override
            public void onCompleted() {
                if (!closed) {
                    closed = true;
                    responseObserver.onCompleted();
                }
            }
        };
    }

    /**
     * Validates one request, runs the fetch and parse, and sends its typed response, built by
     * {@code response} from the fetch key and the {@link Document}. Returns {@code false} when
     * the call was closed with an error instead (per-request config refused, an invalid
     * request, an unknown fetcher, a failed or interrupted fetch and parse) or was cancelled
     * by the client, so the caller must not complete it.
     */
    private <R> boolean reply(FetchRequest request, StreamObserver<R> responseObserver,
                              Cancellation cancellation,
                              BiFunction<String, Document, R> response) {
        if (v1.denyPerRequestConfig(request.additionalFetchConfigJson(),
                request.parseContextJson(), responseObserver)) {
            return false;
        }
        ParseContext parseContext = v1.buildRequestParseContext(request.fetcherId(),
                request.additionalFetchConfigJson(), request.parseContextJson(),
                responseObserver);
        if (parseContext == null) {
            return false;
        }
        if (!cancellation.begin()) {
            return false;
        }
        TikaGrpcServerImpl.FetchParseOutcome outcome;
        try {
            outcome = v1.runFetchAndParse(request.fetcherId(), request.fetchKey(),
                    parseContext);
        } catch (TikaGrpcServerImpl.FetcherNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("no fetcher named '" + request.fetcherId() + "'")
                    .asRuntimeException());
            return false;
        } catch (RuntimeException e) {
            closeFailed("the fetch and parse of '" + request.fetchKey() + "'", e,
                    cancellation, responseObserver);
            return false;
        } finally {
            cancellation.end();
        }
        if (outcome == null) {
            TikaGrpcServerImpl.closeInterrupted(request.fetchKey(), responseObserver);
            return false;
        }
        if (cancellation.isCancelled()) {
            return false;
        }
        responseObserver.onNext(response.apply(outcome.fetchKey(), DocumentBuilder.build(
                outcome.primary(),
                outcome.fetchKey(),
                outcome.status(),
                outcome.diagnostic(),
                outcome.fetchParseTimeMs())));
        return true;
    }

    /**
     * Closes the call with {@code INTERNAL} for a failure the caller cannot fix. The cause
     * goes to the log, not to the caller. A call the client already cancelled is left alone:
     * the failure is most likely the interrupt that cancelling sent.
     */
    private static void closeFailed(String what, Exception e, Cancellation cancellation,
                                    StreamObserver<?> responseObserver) {
        if (cancellation.isCancelled()) {
            LOG.debug("{} failed after the client cancelled", what, e);
            return;
        }
        LOG.warn("{} failed", what, e);
        responseObserver.onError(Status.INTERNAL
                .withDescription(what + " failed on the server")
                .asRuntimeException());
    }

    /**
     * The fields every v2 FetchAndParse request carries. Each RPC has its own request
     * message, so the handlers copy those fields here before sharing one code path.
     */
    private record FetchRequest(String fetcherId, String fetchKey,
                                String additionalFetchConfigJson, String parseContextJson) {
    }

    /**
     * Ties one call to the thread parsing for it. When the client cancels, the parse in
     * progress is interrupted and later {@link #begin()}s refuse, so no new parse starts for
     * a call nobody is waiting on. Registering the cancel handler also stops gRPC from
     * throwing when a reply races the cancel.
     */
    static final class Cancellation {
        private boolean cancelled;
        private Thread parsing;

        /**
         * Must be called from the RPC method itself, before it returns: gRPC only accepts a
         * cancel handler then, and only the method's thread sees the call's {@link Context}.
         * <p>
         * The cancel handler alone cannot stop a parse: gRPC runs it on the call's
         * serializing executor, after the blocked handler method returns. The call's
         * {@link Context} is cancelled from the transport as soon as the client goes away,
         * so its listener is what interrupts the parse. The handler is still registered, so
         * a reply racing the cancel is dropped instead of throwing. Outside a gRPC call (a
         * unit test's observer), nothing ever cancels.
         */
        static Cancellation of(StreamObserver<?> responseObserver) {
            Cancellation cancellation = new Cancellation();
            if (responseObserver instanceof ServerCallStreamObserver<?> call) {
                call.setOnCancelHandler(cancellation::cancel);
            }
            Context.current().addListener(context -> cancellation.cancel(),
                    Runnable::run);
            return cancellation;
        }

        synchronized void cancel() {
            cancelled = true;
            if (parsing != null) {
                parsing.interrupt();
            }
        }

        synchronized boolean isCancelled() {
            return cancelled;
        }

        /**
         * Marks the current thread as parsing for this call. Returns {@code false}, and marks
         * nothing, when the call is already cancelled.
         */
        synchronized boolean begin() {
            if (cancelled) {
                return false;
            }
            parsing = Thread.currentThread();
            return true;
        }

        /**
         * Ends what {@link #begin()} started. Clears an interrupt that {@link #cancel()} may
         * have sent, so it does not leak into the next call this thread serves.
         */
        synchronized void end() {
            if (parsing == Thread.currentThread()) {
                parsing = null;
                if (cancelled) {
                    Thread.interrupted();
                }
            }
        }
    }
}
