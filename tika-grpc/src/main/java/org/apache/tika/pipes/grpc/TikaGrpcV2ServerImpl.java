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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import com.google.protobuf.ByteString;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.detect.gzip.GZipSpecializationDetector;
import org.apache.tika.grpc.mapper.DocumentBuilder;
import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.FetchAndParseBiDirectionalStreamingRequest;
import org.apache.tika.grpc.v2.FetchAndParseBiDirectionalStreamingResponse;
import org.apache.tika.grpc.v2.FetchAndParseRequest;
import org.apache.tika.grpc.v2.FetchAndParseResponse;
import org.apache.tika.grpc.v2.FetchAndParseServerSideStreamingRequest;
import org.apache.tika.grpc.v2.FetchAndParseServerSideStreamingResponse;
import org.apache.tika.grpc.v2.ParseBytesRequest;
import org.apache.tika.grpc.v2.ParseBytesResponse;
import org.apache.tika.grpc.v2.ParseStatus;
import org.apache.tika.grpc.v2.SourceOrigin;
import org.apache.tika.grpc.v2.TikaV2ServiceGrpc;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;

/**
 * Experimental v2 parse surface. Reuses the same pipes/fetcher runtime as the v1
 * {@link TikaGrpcServerImpl}; fetcher management stays on v1. Replies carry the typed
 * {@link Document} contract instead of the v1 fields map.
 * <p>
 * A client that cancels a call, or whose deadline expires, stops it waiting for a pipes
 * worker at once. A parse already running on a worker finishes and its result is dropped,
 * unless {@code grpc.cancelRunningParse} is set: see
 * {@link TikaGrpcConfig#isCancelRunningParse()}.
 * <p>
 * A Document with {@code STATUS_FAILED} means this document could not be fetched or parsed.
 * When the server could not take the request at all (no worker free, the pipes runtime
 * failed to start, a fetcher that failed to initialize) the call is closed with a gRPC
 * status instead, so retry policies keyed on status codes see it: see
 * {@link #serviceFailure(String)}.
 */
class TikaGrpcV2ServerImpl extends TikaV2ServiceGrpc.TikaV2ServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaGrpcV2ServerImpl.class);

    private final TikaGrpcServerImpl v1;

    TikaGrpcV2ServerImpl(TikaGrpcServerImpl v1) {
        this.v1 = v1;
    }

    private Cancellation cancellation(StreamObserver<?> responseObserver) {
        return Cancellation.of(responseObserver, v1.isCancelRunningParse());
    }

    @Override
    public void fetchAndParse(FetchAndParseRequest request,
                              StreamObserver<FetchAndParseResponse> responseObserver) {
        Cancellation cancellation = cancellation(responseObserver);
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
        Cancellation cancellation = cancellation(responseObserver);
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
        Cancellation cancellation = cancellation(responseObserver);
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


    @Override
    public void parseBytes(ParseBytesRequest request,
                           StreamObserver<ParseBytesResponse> responseObserver) {
        if (request.getContent().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("content is required")
                    .asRuntimeException());
            return;
        }
        Cancellation cancellation = cancellation(responseObserver);
        if (v1.denyPerRequestConfig(null, request.getParseContextJson(), responseObserver)) {
            return;
        }
        ParseContext parseContext = v1.buildRequestParseContext(null, null,
                request.getParseContextJson(), responseObserver);
        if (parseContext == null) {
            return;
        }
        // The permit is held until the reply is sent: hashing and detection below still
        // read the request's bytes, so parseBytesMaxConcurrent bounds them too.
        TikaGrpcServerImpl.ParseBytesPermit permit;
        try {
            permit = v1.admitParseBytes(request.getContent().size());
        } catch (TikaGrpcServerImpl.ParseBytesTooLargeException
                 | TikaGrpcServerImpl.ParseBytesBusyException e) {
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription(e.getMessage())
                    .asRuntimeException());
            return;
        }
        try (permit) {
            parseBytes(request, responseObserver, cancellation, parseContext, permit);
        }
    }

    private void parseBytes(ParseBytesRequest request,
                            StreamObserver<ParseBytesResponse> responseObserver,
                            Cancellation cancellation, ParseContext parseContext,
                            TikaGrpcServerImpl.ParseBytesPermit permit) {
        TikaGrpcServerImpl.ParseBytesOutcome parseBytesOutcome;
        if (!cancellation.begin()) {
            return;
        }
        try {
            // newInput() reads the bytes protobuf already holds without copying them.
            parseBytesOutcome = v1.parseAdmitted(
                    request.getContent().newInput(),
                    request.getContent().size(),
                    request.getResourceName(),
                    parseContext,
                    cancellation::dispatching,
                    permit);
        } catch (TikaGrpcServerImpl.ParseBytesSpoolException e) {
            // The server's disk, not the request: a full or unwritable spool directory.
            LOG.warn("could not stage ParseBytes content", e.getCause());
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription(e.getMessage())
                    .asRuntimeException());
            return;
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
            return;
        } catch (Exception e) {
            closeFailed("ParseBytes", e, cancellation, responseObserver);
            return;
        } finally {
            cancellation.end();
        }
        if (cancellation.isCancelled()) {
            return;
        }
        if (parseBytesOutcome == null) {
            // The parse was interrupted, so there is no Document to return.
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("parse was interrupted before a reply could be built")
                    .asRuntimeException());
            return;
        }
        Status failure = serviceFailure(parseBytesOutcome.status());
        if (failure != null) {
            responseObserver.onError(failure
                    .withDescription(serviceFailureDescription(parseBytesOutcome.status(),
                            "ParseBytes content", null))
                    .asRuntimeException());
            return;
        }
        Document.Builder document = DocumentBuilder.build(
                        parseBytesOutcome.primary(),
                        request.getCorrelationId(),
                        parseBytesOutcome.status(),
                        parseBytesOutcome.diagnostic(),
                        parseBytesOutcome.fetchParseTimeMs())
                .toBuilder();
        SourceOrigin.Builder origin = document.getOriginBuilder();
        if (!request.getResourceName().isBlank()) {
            origin.setFilename(request.getResourceName().trim());
        }
        origin.setByteSize(request.getContent().size());
        if (!request.getSourceUri().isBlank()) {
            origin.setSourceUri(request.getSourceUri());
        }
        if (!request.getEffectiveUri().isBlank()) {
            origin.setEffectiveUri(request.getEffectiveUri());
        }
        if (!request.getBaseUri().isBlank()) {
            origin.setBaseUri(request.getBaseUri());
        }
        origin.setTruncated(request.getTruncated());
        // A parser can recover cut-off bytes without any sign of it (PDFBox rebuilds a PDF
        // whose trailer is gone and loses the Info dictionary, title included), so a
        // "success" on bytes the caller says are incomplete is a partial result.
        // Whatever the status, the Document says the bytes were cut; only a success
        // changes, to PARTIAL. A FAILED parse stays FAILED.
        if (request.getTruncated()) {
            ParseStatus.Builder status = document.getStatusBuilder();
            if (status.getStatus() == ParseStatus.Status.STATUS_SUCCESS) {
                status.setStatus(ParseStatus.Status.STATUS_PARTIAL);
            }
            status.addErrors(TRUNCATED_BY_CALLER);
        }
        // The caller's bytes are here, so a parse that failed before recording its digest
        // or content type (or a pipeline with no digester) still says what was sent. A
        // digest the pipeline recorded in another form (another algorithm, upper case,
        // base32) is replaced: origin.sha256 is always lowercase hex SHA-256.
        if (!isSha256Hex(origin.getSha256())) {
            origin.setSha256(sha256Hex(request.getContent()));
        }
        document.setOrigin(origin);
        if (document.getContentType().isEmpty()) {
            String detected = detectContentType(request.getContent(), request.getResourceName());
            if (detected != null) {
                document.setContentType(detected);
            }
        }
        ParseBytesResponse.Builder response = ParseBytesResponse.newBuilder()
                .setDocument(document);
        if (!request.getCorrelationId().isEmpty()) {
            response.setCorrelationId(request.getCorrelationId());
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    static final String TRUNCATED_BY_CALLER = "content was truncated by the caller; metadata"
            + " stored past the cut, such as a PDF's document info, may be missing";

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    static boolean isSha256Hex(String digest) {
        return digest != null && SHA256_HEX.matcher(digest).matches();
    }

    private static String sha256Hex(ByteString content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            content.asReadOnlyByteBufferList().forEach(digest::update);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // Every JVM must provide SHA-256.
            throw new IllegalStateException(e);
        }
    }

    /**
     * Detects the content type from the magic bytes and the resource name with tika-core's
     * MIME registry, and looks one level inside gzip: a gzipped WARC or ARC is
     * {@code application/warc+gz} or {@code application/arc+gz}, not plain
     * {@code application/gzip}, whatever its name. Used only when the parse recorded no
     * content type.
     * <p>
     * Deliberately not the full default detector: this runs in the server JVM, on bytes whose
     * parse may just have crashed a worker, so it stays with checks that read a bounded
     * prefix (container detectors open whole zip and OLE files).
     */
    static String detectContentType(ByteString content, String resourceName) {
        Metadata metadata = new Metadata();
        if (!resourceName.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, resourceName.trim());
        }
        try (TikaInputStream tis = TikaInputStream.get(content.newInput())) {
            MediaType type = MimeTypes.getDefaultMimeTypes().detect(tis, metadata,
                    new ParseContext());
            if (GZipSpecializationDetector.GZ.equals(type)
                    || GZipSpecializationDetector.WARC_GZ.equals(type)
                    || GZipSpecializationDetector.ARC_GZ.equals(type)) {
                // Reads at most 1 KiB of compressed bytes. The magic bytes win over a name
                // like crawl.warc.gz on a file that is not a WARC.
                type = GZIP_SPECIALIZATION.detect(tis, metadata, new ParseContext());
            }
            return type.toString();
        } catch (IOException e) {
            LOG.debug("content type detection failed", e);
            return null;
        }
    }

    private static final GZipSpecializationDetector GZIP_SPECIALIZATION =
            new GZipSpecializationDetector();

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
        // A missing or broken fetcher is NOT_FOUND or UNAVAILABLE whatever else the request
        // carries: checked before additional_fetch_config_json, which also names the fetcher.
        if (StringUtils.isNotBlank(request.additionalFetchConfigJson())) {
            try {
                v1.resolveFetcher(request.fetcherId());
            } catch (RuntimeException e) {
                if (fetcherFailure(e, request.fetcherId(), responseObserver)) {
                    return false;
                }
                throw e;
            }
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
            outcome = v1.runPublicFetchAndParse(request.fetcherId(), request.fetchKey(),
                    parseContext, cancellation::dispatching);
        } catch (RuntimeException e) {
            if (fetcherFailure(e, request.fetcherId(), responseObserver)) {
                return false;
            }
            closeFailed("the fetch and parse of '" + request.fetchKey() + "'", e,
                    cancellation, responseObserver);
            return false;
        } finally {
            cancellation.end();
        }
        if (cancellation.isCancelled()) {
            return false;
        }
        if (outcome == null) {
            TikaGrpcServerImpl.closeInterrupted(request.fetchKey(), responseObserver);
            return false;
        }
        Status failure = serviceFailure(outcome.status());
        if (failure != null) {
            responseObserver.onError(failure
                    .withDescription(serviceFailureDescription(outcome.status(),
                            request.fetchKey(), request.fetcherId()))
                    .asRuntimeException());
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
     * Closes the call for a fetcher that does not exist ({@code NOT_FOUND}) or could not be
     * loaded ({@code UNAVAILABLE}), and returns {@code true}; {@code false} for any other
     * failure.
     */
    private static boolean fetcherFailure(RuntimeException e, String fetcherId,
                                          StreamObserver<?> responseObserver) {
        if (e instanceof TikaGrpcServerImpl.FetcherNotFoundException) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("no fetcher named '" + fetcherId + "'")
                    .asRuntimeException());
            return true;
        }
        if (e instanceof TikaGrpcServerImpl.FetcherUnavailableException) {
            LOG.warn("fetcher '{}' could not be loaded", fetcherId, e.getCause());
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("fetcher '" + fetcherId + "' could not be loaded")
                    .asRuntimeException());
            return true;
        }
        return false;
    }

    /**
     * The gRPC status for a pipes outcome that says the server, not the document, failed,
     * or {@code null} for an outcome that belongs in the Document. Names are
     * {@code PipesResult.RESULT_STATUS} constants.
     * <ul>
     *   <li>{@code CLIENT_UNAVAILABLE_WITHIN_MS}: every worker stayed busy for
     *   {@code pipes.maxWaitForClientMillis}. {@code RESOURCE_EXHAUSTED}; retry later or
     *   elsewhere.</li>
     *   <li>{@code FAILED_TO_INITIALIZE}, {@code FETCHER_INITIALIZATION_EXCEPTION},
     *   {@code EMITTER_INITIALIZATION_EXCEPTION}: the pipes runtime or a component could not
     *   start. {@code UNAVAILABLE}.</li>
     *   <li>{@code FETCHER_NOT_FOUND}, {@code EMITTER_NOT_FOUND}, {@code PRESET_NOT_FOUND}:
     *   {@code NOT_FOUND}, as when the server's own check finds no fetcher.</li>
     * </ul>
     * Everything else stays in the Document, including {@code PAYLOAD_LIMIT_EXCEEDED} (this
     * document is too big, and a retry will not change that) and the crashes:
     * {@code TIMEOUT}, {@code OOM} and {@code UNSPECIFIED_CRASH} are a worker dying on this
     * document, so they are a {@code STATUS_FAILED} Document whose {@code pipes_status} says
     * which. A retry of the same bytes would most likely crash the same way.
     */
    static Status serviceFailure(String pipesStatus) {
        if (pipesStatus == null) {
            return null;
        }
        return switch (pipesStatus) {
            case "CLIENT_UNAVAILABLE_WITHIN_MS" -> Status.RESOURCE_EXHAUSTED;
            case "FAILED_TO_INITIALIZE", "FETCHER_INITIALIZATION_EXCEPTION",
                 "EMITTER_INITIALIZATION_EXCEPTION" -> Status.UNAVAILABLE;
            case "FETCHER_NOT_FOUND", "EMITTER_NOT_FOUND", "PRESET_NOT_FOUND" ->
                    Status.NOT_FOUND;
            default -> null;
        };
    }

    /** The description for a {@link #serviceFailure(String)} status. */
    static String serviceFailureDescription(String pipesStatus, String fetchKey,
                                            String fetcherId) {
        return switch (pipesStatus) {
            case "CLIENT_UNAVAILABLE_WITHIN_MS" ->
                    "no pipes worker became free for '" + fetchKey + "'; retry later";
            case "FAILED_TO_INITIALIZE" -> "the pipes runtime failed to start";
            case "FETCHER_INITIALIZATION_EXCEPTION" ->
                    "fetcher '" + fetcherId + "' failed to initialize";
            case "FETCHER_NOT_FOUND" -> "no fetcher named '" + fetcherId + "'";
            default -> "the fetch and parse of '" + fetchKey + "' could not run: "
                    + pipesStatus;
        };
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
     * Ties one call to the thread parsing for it. When the client cancels, a request still
     * waiting for a pipes worker is interrupted, later {@link #begin()}s refuse, and a
     * worker that frees up is handed back unused ({@link #dispatching()}), so no new parse
     * starts for a call nobody is waiting on. A parse already on a worker is interrupted
     * only when {@code interruptRunning} is set; otherwise it finishes and its result is
     * dropped. Registering the cancel handler also stops gRPC from throwing when a reply
     * races the cancel.
     */
    static final class Cancellation {
        private final boolean interruptRunning;
        private boolean cancelled;
        private Thread parsing;
        private boolean running;

        Cancellation() {
            this(false);
        }

        Cancellation(boolean interruptRunning) {
            this.interruptRunning = interruptRunning;
        }

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
        static Cancellation of(StreamObserver<?> responseObserver, boolean interruptRunning) {
            Cancellation cancellation = new Cancellation(interruptRunning);
            if (responseObserver instanceof ServerCallStreamObserver<?> call) {
                call.setOnCancelHandler(cancellation::cancel);
            }
            Context.current().addListener(context -> cancellation.cancel(),
                    Runnable::run);
            return cancellation;
        }

        synchronized void cancel() {
            cancelled = true;
            if (parsing != null && (!running || interruptRunning)) {
                parsing.interrupt();
            }
        }

        /**
         * Called on the parsing thread once its parse has a pipes worker, just before the
         * parse starts on it. Returns {@code false} when the call is already cancelled, so
         * the worker goes back unused.
         */
        synchronized boolean dispatching() {
            if (cancelled) {
                return false;
            }
            running = true;
            return true;
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
         * Ends what {@link #begin()} started. Clears the thread's interrupt, whether
         * {@link #cancel()} sent it or a shutdown did, so it does not leak into the next call
         * this pooled thread serves.
         */
        synchronized void end() {
            if (parsing == Thread.currentThread()) {
                parsing = null;
                running = false;
            }
            Thread.interrupted();
        }
    }
}
