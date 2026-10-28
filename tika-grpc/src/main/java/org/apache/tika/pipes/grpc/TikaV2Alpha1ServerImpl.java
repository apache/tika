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

import java.io.IOException;
import java.util.Arrays;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.grpc.v2alpha1.Cardinality;
import org.apache.tika.grpc.v2alpha1.FetchAndParseBidiRequest;
import org.apache.tika.grpc.v2alpha1.FetchAndParseBidiResponse;
import org.apache.tika.grpc.v2alpha1.FetchAndParseRequest;
import org.apache.tika.grpc.v2alpha1.FetchAndParseResponse;
import org.apache.tika.grpc.v2alpha1.FetchAndParseStreamRequest;
import org.apache.tika.grpc.v2alpha1.FetchAndParseStreamResponse;
import org.apache.tika.grpc.v2alpha1.ParsedDocument;
import org.apache.tika.grpc.v2alpha1.RequestError;
import org.apache.tika.grpc.v2alpha1.TikaServiceGrpc;
import org.apache.tika.grpc.v2alpha1.ValueType;
import org.apache.tika.grpc.v2alpha1.Values;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.schema.MetadataKeyValidator;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.fetcher.FetcherNotFoundException;
import org.apache.tika.pipes.core.PipesException;

/**
 * The experimental v2alpha1 service: what /rmeta returns, as protobuf. Goes through the same
 * {@link FetchParseRuntime} as v1, so it reaches nothing v1 does not.
 */
class TikaV2Alpha1ServerImpl extends TikaServiceGrpc.TikaServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaV2Alpha1ServerImpl.class);
    private static final String CONTENT_KEY = TikaCoreProperties.TIKA_CONTENT.getName();
    private static final int MAX_ECHOED_ID = 200;

    private final FetchParseRuntime runtime;
    private final MetadataKeyValidator registry;

    TikaV2Alpha1ServerImpl(FetchParseRuntime runtime) {
        this.runtime = runtime;
        this.registry = MetadataKeyValidator.fromClasspath();
    }

    @Override
    public void fetchAndParse(FetchAndParseRequest request,
                              StreamObserver<FetchAndParseResponse> responseObserver) {
        try {
            responseObserver.onNext(run(request.getFetcherId(), request.getFetchKey(),
                    request.getParseContextJson(), request.getAdditionalFetchConfigJson()));
            responseObserver.onCompleted();
        } catch (StatusException e) {
            responseObserver.onError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(interrupted());
        }
    }

    @Override
    public void fetchAndParseStream(FetchAndParseStreamRequest request,
                                    StreamObserver<FetchAndParseStreamResponse> responseObserver) {
        try {
            FetchAndParseResponse response = run(request.getFetcherId(), request.getFetchKey(),
                    request.getParseContextJson(), request.getAdditionalFetchConfigJson());
            for (ParsedDocument document : response.getDocumentsList()) {
                responseObserver.onNext(
                        FetchAndParseStreamResponse.newBuilder().setDocument(document).build());
            }
            responseObserver.onCompleted();
        } catch (StatusException e) {
            responseObserver.onError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(interrupted());
        }
    }

    @Override
    public StreamObserver<FetchAndParseBidiRequest> fetchAndParseBidi(
            StreamObserver<FetchAndParseBidiResponse> responseObserver) {
        return new StreamObserver<>() {
            // set once the stream is closed with an error; later requests have nowhere to go
            private boolean closed;

            @Override
            public void onNext(FetchAndParseBidiRequest request) {
                if (closed) {
                    return;
                }
                FetchAndParseBidiResponse.Builder response =
                        FetchAndParseBidiResponse.newBuilder().setRequestId(request.getRequestId());
                try {
                    response.setResult(run(request.getFetcherId(), request.getFetchKey(),
                            request.getParseContextJson(), request.getAdditionalFetchConfigJson()));
                } catch (StatusException e) {
                    response.setError(toRequestError(e.getStatus()));
                } catch (InterruptedException e) {
                    // shutdown: the only failure that ends the stream
                    Thread.currentThread().interrupt();
                    closed = true;
                    responseObserver.onError(interrupted());
                    return;
                }
                responseObserver.onNext(response.build());
            }

            @Override
            public void onError(Throwable throwable) {
                LOG.debug("v2alpha1 bidi stream ended by the client", throwable);
            }

            @Override
            public void onCompleted() {
                if (!closed) {
                    responseObserver.onCompleted();
                }
            }
        };
    }

    /** Failures that are not about the document are thrown as a gRPC status. */
    private FetchAndParseResponse run(String fetcherId, String fetchKey, String parseContextJson,
                                      String additionalFetchConfigJson)
            throws StatusException, InterruptedException {
        Status denial = runtime.perRequestConfigDenial(additionalFetchConfigJson, parseContextJson);
        if (denial != null) {
            throw denial.asException();
        }
        ParseContext parseContext =
                runtime.requestParseContext(fetcherId, parseContextJson, additionalFetchConfigJson);
        String resolvedFetcherId;
        try {
            resolvedFetcherId = runtime.resolveFetcherId(fetcherId);
        } catch (FetcherNotFoundException e) {
            throw Status.NOT_FOUND.withDescription("no fetcher named '" + echo(fetcherId) + "'")
                    .asException();
        } catch (TikaException | IOException | RuntimeException e) {
            LOG.warn("could not load fetcher '{}'", fetcherId, e);
            throw Status.UNAVAILABLE.withDescription("fetcher '" + echo(fetcherId)
                    + "' could not be loaded").asException();
        }
        PipesResult result;
        try {
            result = runtime.fetchAndParse(resolvedFetcherId, fetchKey, parseContext);
        } catch (IOException | PipesException e) {
            LOG.warn("fetch and parse failed", e);
            throw Status.INTERNAL.withDescription("fetch and parse failed").asException();
        }
        Status failure = serviceFailure(result.status(), fetcherId);
        if (failure != null) {
            throw failure.asException();
        }
        return toResponse(fetchKey, result);
    }

    /** The gRPC status for a pipes result that is not about the document, else {@code null}. */
    static Status serviceFailure(PipesResult.RESULT_STATUS status, String fetcherId) {
        // exhaustive on purpose: a new RESULT_STATUS fails compilation until it is classified here
        return switch (status) {
            case CLIENT_UNAVAILABLE_WITHIN_MS -> Status.RESOURCE_EXHAUSTED
                    .withDescription("no pipes worker became free in time; retry later");
            case FAILED_TO_INITIALIZE -> Status.UNAVAILABLE
                    .withDescription("the pipes worker failed to start");
            case FETCHER_INITIALIZATION_EXCEPTION -> Status.UNAVAILABLE.withDescription(
                    "fetcher '" + echo(fetcherId) + "' failed to initialize in the pipes worker");
            case EMITTER_INITIALIZATION_EXCEPTION -> Status.UNAVAILABLE
                    .withDescription("an emitter failed to initialize in the pipes worker");
            case FETCHER_NOT_FOUND -> Status.NOT_FOUND.withDescription(
                    "the pipes worker has no fetcher '" + echo(fetcherId) + "'");
            case EMITTER_NOT_FOUND -> Status.NOT_FOUND
                    .withDescription("the pipes worker has no such emitter");
            case PRESET_NOT_FOUND -> Status.NOT_FOUND.withDescription("unknown preset");
            case FETCH_EXCEPTION, EMIT_EXCEPTION, PAYLOAD_LIMIT_EXCEEDED, OOM, TIMEOUT,
                    UNSPECIFIED_CRASH, EMPTY_OUTPUT, PARSE_SUCCESS, PARSE_SUCCESS_WITH_EXCEPTION,
                    PARSE_EXCEPTION_NO_EMIT, EMIT_SUCCESS, EMIT_SUCCESS_PARSE_EXCEPTION,
                    EMIT_SUCCESS_PASSBACK, PARTIAL_TIMEOUT -> null;
        };
    }

    private FetchAndParseResponse toResponse(String fetchKey, PipesResult result) {
        FetchAndParseResponse.Builder response = FetchAndParseResponse.newBuilder()
                .setFetchKey(fetchKey)
                .setPipesStatus(result.status().name());
        if (StringUtils.isNotBlank(result.message())) {
            response.setMessage(result.message());
        }
        if (result.emitData() != null && result.emitData().getMetadataList() != null) {
            for (Metadata metadata : result.emitData().getMetadataList()) {
                response.addDocuments(toDocument(metadata));
            }
        }
        return response.build();
    }

    ParsedDocument toDocument(Metadata metadata) {
        ParsedDocument.Builder document = ParsedDocument.newBuilder();
        String path = metadata.get(TikaCoreProperties.EMBEDDED_ID_PATH);
        if (path != null) {
            document.setEmbeddedPath(path);
        }
        String content = metadata.get(CONTENT_KEY);
        if (content != null) {
            document.setContent(content);
        }
        for (String name : metadata.names()) {
            if (name.equals(CONTENT_KEY)) {
                continue;
            }
            document.putMetadata(name, Values.newBuilder()
                    .addAllValues(Arrays.asList(metadata.getValues(name)))
                    .setType(valueType(registry.valueTypeOf(name)))
                    .setCardinality(cardinality(registry.cardinalityOf(name)))
                    .build());
        }
        return document.build();
    }

    // only the types that change how a value parses; everything else reads as text
    static ValueType valueType(String registered) {
        if (registered == null) {
            return ValueType.VALUE_TYPE_UNSPECIFIED;
        }
        return switch (registered) {
            case "BOOLEAN" -> ValueType.VALUE_TYPE_BOOLEAN;
            case "INTEGER" -> ValueType.VALUE_TYPE_INTEGER;
            case "REAL" -> ValueType.VALUE_TYPE_REAL;
            case "RATIONAL" -> ValueType.VALUE_TYPE_RATIONAL;
            case "DATE" -> ValueType.VALUE_TYPE_DATE;
            case "URI" -> ValueType.VALUE_TYPE_URI;
            case "JSON" -> ValueType.VALUE_TYPE_JSON;
            default -> ValueType.VALUE_TYPE_UNSPECIFIED;
        };
    }

    static Cardinality cardinality(String registered) {
        if (registered == null) {
            return Cardinality.CARDINALITY_UNSPECIFIED;
        }
        return switch (registered) {
            case "BAG" -> Cardinality.CARDINALITY_BAG;
            case "SEQ" -> Cardinality.CARDINALITY_SEQ;
            case "ALT" -> Cardinality.CARDINALITY_ALT;
            default -> Cardinality.CARDINALITY_UNSPECIFIED;
        };
    }

    private static RequestError toRequestError(Status status) {
        return RequestError.newBuilder()
                .setCode(status.getCode().value())
                .setMessage(StringUtils.defaultString(status.getDescription()))
                .build();
    }

    private static StatusException interrupted() {
        return Status.UNAVAILABLE.withDescription("the server is shutting down").asException();
    }

    private static String echo(String id) {
        return StringUtils.abbreviate(StringUtils.defaultString(id), MAX_ECHOED_ID);
    }
}
