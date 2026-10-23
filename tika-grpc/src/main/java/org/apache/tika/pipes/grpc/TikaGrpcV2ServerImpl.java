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
        FetchRequest fetch = new FetchRequest(request.getFetcherId(), request.getFetchKey(),
                request.getAdditionalFetchConfigJson(), request.getParseContextJson());
        if (reply(fetch, responseObserver, (fetchKey, document) -> FetchAndParseResponse
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
        FetchRequest fetch = new FetchRequest(request.getFetcherId(), request.getFetchKey(),
                request.getAdditionalFetchConfigJson(), request.getParseContextJson());
        if (reply(fetch, responseObserver, (fetchKey, document) ->
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
        return new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseBiDirectionalStreamingRequest request) {
                FetchRequest fetch = new FetchRequest(request.getFetcherId(),
                        request.getFetchKey(), request.getAdditionalFetchConfigJson(),
                        request.getParseContextJson());
                reply(fetch, responseObserver, (fetchKey, document) ->
                        FetchAndParseBiDirectionalStreamingResponse
                                .newBuilder()
                                .setFetchKey(fetchKey)
                                .setDocument(document)
                                .build());
            }

            @Override
            public void onError(Throwable throwable) {
                LOG.error("Parse error occurred", throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * Validates one request, runs the fetch and parse, and sends its typed response, built by
     * {@code response} from the fetch key and the {@link Document}. Returns {@code false} when
     * the call was closed with an error instead (per-request config refused, an invalid
     * request, or an interrupted fetch and parse), so the caller must not complete it.
     */
    private <R> boolean reply(FetchRequest request, StreamObserver<R> responseObserver,
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
        TikaGrpcServerImpl.FetchParseOutcome outcome = v1.runFetchAndParse(
                request.fetcherId(), request.fetchKey(), parseContext);
        if (outcome == null) {
            TikaGrpcServerImpl.closeInterrupted(request.fetchKey(), responseObserver);
            return false;
        }
        responseObserver.onNext(response.apply(outcome.fetchKey(), DocumentBuilder.build(
                outcome.primary(),
                outcome.fetchKey(),
                outcome.status(),
                outcome.fetchParseTimeMs())));
        return true;
    }

    /**
     * The fields every v2 FetchAndParse request carries. Each RPC has its own request
     * message, so the handlers copy those fields here before sharing one code path.
     */
    private record FetchRequest(String fetcherId, String fetchKey,
                                String additionalFetchConfigJson, String parseContextJson) {
    }
}
