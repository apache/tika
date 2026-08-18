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

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.grpc.mapper.DocumentBuilder;
import org.apache.tika.grpc.v2.FetchAndParseReply;
import org.apache.tika.grpc.v2.FetchAndParseRequest;
import org.apache.tika.grpc.v2.TikaV2Grpc;
import org.apache.tika.parser.ParseContext;

/**
 * Experimental v2 parse surface. Reuses the same pipes/fetcher runtime as the v1
 * {@link TikaGrpcServerImpl}; fetcher management stays on v1. Replies carry the typed
 * {@link org.apache.tika.grpc.v2.Document} contract instead of the legacy fields map.
 */
class TikaGrpcV2ServerImpl extends TikaV2Grpc.TikaV2ImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaGrpcV2ServerImpl.class);

    private final TikaGrpcServerImpl v1;

    TikaGrpcV2ServerImpl(TikaGrpcServerImpl v1) {
        this.v1 = v1;
    }

    @Override
    public void fetchAndParse(FetchAndParseRequest request,
                              StreamObserver<FetchAndParseReply> responseObserver) {
        if (v1.denyPerRequestConfig(request.getAdditionalFetchConfigJson(),
                request.getParseContextJson(), responseObserver)) {
            return;
        }
        ParseContext parseContext = v1.buildRequestParseContext(request.getFetcherId(),
                request.getAdditionalFetchConfigJson(), request.getParseContextJson(),
                responseObserver);
        if (parseContext == null) {
            return;
        }
        fetchAndParseImpl(request, parseContext, responseObserver);
        responseObserver.onCompleted();
    }

    @Override
    public void fetchAndParseServerSideStreaming(FetchAndParseRequest request,
                                                 StreamObserver<FetchAndParseReply> responseObserver) {
        if (v1.denyPerRequestConfig(request.getAdditionalFetchConfigJson(),
                request.getParseContextJson(), responseObserver)) {
            return;
        }
        ParseContext parseContext = v1.buildRequestParseContext(request.getFetcherId(),
                request.getAdditionalFetchConfigJson(), request.getParseContextJson(),
                responseObserver);
        if (parseContext == null) {
            return;
        }
        fetchAndParseImpl(request, parseContext, responseObserver);
    }

    @Override
    public StreamObserver<FetchAndParseRequest> fetchAndParseBiDirectionalStreaming(
            StreamObserver<FetchAndParseReply> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseRequest request) {
                if (v1.denyPerRequestConfig(request.getAdditionalFetchConfigJson(),
                        request.getParseContextJson(), responseObserver)) {
                    return;
                }
                ParseContext parseContext = v1.buildRequestParseContext(request.getFetcherId(),
                        request.getAdditionalFetchConfigJson(), request.getParseContextJson(),
                        responseObserver);
                if (parseContext == null) {
                    return;
                }
                fetchAndParseImpl(request, parseContext, responseObserver);
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

    private void fetchAndParseImpl(FetchAndParseRequest request, ParseContext parseContext,
                                   StreamObserver<FetchAndParseReply> responseObserver) {
        TikaGrpcServerImpl.FetchParseOutcome outcome = v1.runFetchAndParse(
                request.getFetcherId(), request.getFetchKey(), parseContext);
        if (outcome == null) {
            return;
        }
        responseObserver.onNext(FetchAndParseReply.newBuilder()
                .setFetchKey(outcome.fetchKey())
                .setDocument(DocumentBuilder.build(
                        outcome.primary(),
                        outcome.fetchKey(),
                        outcome.status(),
                        outcome.fetchParseTimeMs()))
                .build());
    }
}
