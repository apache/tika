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
package org.apache.tika.server.core.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ComponentIds;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.ContentBytesConfig;
import org.apache.tika.pipes.core.EmitStrategy;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.PipesException;
import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.pipes.core.fetcher.BytesFetcher;
import org.apache.tika.pipes.core.fetcher.InlineBytes;
import org.apache.tika.pipes.core.fetcher.PayloadRouter;
import org.apache.tika.server.core.TikaServerParseException;

/**
 * Helper class for pipes-based parsing in tika-server endpoints.
 * Handles temp file management, FetchEmitTuple creation, and result processing.
 * <p>
 * The helper manages a dedicated temp directory for input files. A file-system-fetcher
 * is configured with basePath pointing to this directory, ensuring child processes
 * can only access files within the designated temp directory (no absolute paths).
 */
public class PipesParsingHelper {

    private static final Logger LOG = LoggerFactory.getLogger(PipesParsingHelper.class);
    /** Per-request server-layer latency breakdown; joins the pipes lines on {@code id}. */
    private static final Logger TIMING_LOG =
            LoggerFactory.getLogger("org.apache.tika.pipes.timing.server");

    /**
     * The fetcher ID used for reading temp files.
     * This fetcher is configured with basePath = inputTempDirectory.
     */
    public static final String DEFAULT_FETCHER_ID = "__tika-server";

    private final PipesParser pipesParser;
    private final PipesConfig pipesConfig;
    private final Path inputTempDirectory;
    private final int maxInlineBytes;
    private final Path unpackEmitterBasePath;

    /**
     * Creates a PipesParsingHelper.
     *
     * @param pipesParser the PipesParser instance
     * @param pipesConfig the PipesConfig instance
     * @param inputTempDirectory the temp directory for input files. The file-system-fetcher
     *                           is configured with basePath = this directory.
     * @param unpackEmitterBasePath the basePath where the unpack-emitter writes files.
     *                              This is where the server will find the zip files created
     *                              by UNPACK mode. May be null if UNPACK mode won't be used.
     */
    public PipesParsingHelper(PipesParser pipesParser, PipesConfig pipesConfig,
                              Path inputTempDirectory, Path unpackEmitterBasePath) {
        this.pipesParser = pipesParser;
        this.pipesConfig = pipesConfig;
        this.inputTempDirectory = inputTempDirectory;
        this.maxInlineBytes = pipesConfig.getMaxInlineBytes();
        this.unpackEmitterBasePath = unpackEmitterBasePath;

        if (inputTempDirectory == null || !Files.isDirectory(inputTempDirectory)) {
            throw new IllegalArgumentException(
                    "inputTempDirectory must be a valid directory: " + inputTempDirectory);
        }
        LOG.info("PipesParsingHelper initialized with inputTempDirectory: {}", inputTempDirectory);
    }

    /**
     * Closes the shared PipesParser (destroying its forked workers) and deletes the input
     * and unpack temp directories. Invoked from the server's ordered shutdown sequence,
     * after the HTTP endpoint has stopped -- so workers are torn down only once no new
     * request can arrive.
     */
    public void shutdown() {
        try {
            pipesParser.close();
        } catch (Exception e) {
            LOG.warn("Error closing PipesParser", e);
        }
        deleteTempDirectory(inputTempDirectory);
        deleteTempDirectory(unpackEmitterBasePath);
    }

    private static void deleteTempDirectory(Path tempDir) {
        if (tempDir == null) {
            return;
        }
        try {
            if (!Files.exists(tempDir)) {
                return;
            }
            Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b)) // children before their parent
                    .forEach(PipesParsingHelper::deleteWithRetry);
        } catch (IOException e) {
            LOG.warn("Error cleaning up temp directory: {}", tempDir, e);
        }
    }

    private static void deleteWithRetry(Path p) {
        // On Windows a forked child that outlived destroyForcibly may still hold a handle;
        // a short retry gives its exit time to release the lock before we give up (TIKA-4740).
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.deleteIfExists(p);
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        LOG.warn("Failed to delete temp path after retries: {}", p);
    }

    /**
     * Parses content using pipes-based parsing with process isolation.
     * <p>
     * This method spools the input to the dedicated temp directory and uses a relative
     * filename in the FetchKey. The file-system-fetcher is configured with basePath
     * pointing to this directory, so the child process can only access files there.
     * <p>
     * The caller is responsible for closing the TikaInputStream.
     *
     * @param tis the TikaInputStream containing the content to parse
     * @param metadata metadata to pass to the parser (may include filename, content-type, etc.)
     * @param parseContext parse context with handler configuration
     * @param parseMode the parse mode (RMETA or CONCATENATE)
     * @return list of metadata objects from parsing
     * @throws IOException if temp file operations fail
     * @throws TikaServerParseException if parsing fails
     */
    public List<Metadata> parse(TikaInputStream tis, Metadata metadata,
                                 ParseContext parseContext, ParseMode parseMode) throws IOException {
        return parseInternal(tis, metadata, parseContext, parseMode, false).metadataList();
    }

    /**
     * The metadata plus, when requested via {@code content-bytes-config}, the extracted
     * content as raw UTF-8 -- {@code TIKA_CONTENT} is then absent from the metadata.
     */
    public record ParseOutput(List<Metadata> metadataList, byte[] contentBytes) {
    }

    /**
     * Like {@link #parse} but asks the worker for the content as raw UTF-8 bytes, which
     * travel as binary over the IPC instead of a Smile-encoded string -- the win is one
     * UTF-8 encode in the worker instead of a string transcode on both sides plus a
     * re-encode at the HTTP layer. CONTENT_ONLY only.
     */
    public ParseOutput parseContentOnlyToBytes(TikaInputStream tis, Metadata metadata,
                                               ParseContext parseContext) throws IOException {
        return parseInternal(tis, metadata, parseContext, ParseMode.CONTENT_ONLY, true);
    }

    private ParseOutput parseInternal(TikaInputStream tis, Metadata metadata,
                                      ParseContext parseContext, ParseMode parseMode,
                                      boolean contentAsBytes) throws IOException {
        String requestId = UUID.randomUUID().toString();
        PayloadRouter.Routed routed = null;
        String callerSuppliedName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        long entryNanos = System.nanoTime();
        long routeNanos = -1;
        long pipesNanos = -1;
        long postNanos = -1;

        try {
            routed = PayloadRouter.route(tis, maxInlineBytes,
                    () -> Files.createTempFile(inputTempDirectory, "tika-", getSuffix(metadata)));
            routeNanos = System.nanoTime() - entryNanos;

            String relativeName = null;
            FetchKey fetchKey;
            if (routed.isInline()) {
                parseContext.set(InlineBytes.class, routed.inlineBytes());
                // Fetch key doubles as the caller's filename, so no spool name to scrub later.
                fetchKey = new FetchKey(BytesFetcher.FETCHER_ID,
                        callerSuppliedName == null ? "" : callerSuppliedName);
                LOG.debug("parse: {} bytes inline", routed.inlineBytes().length());
            } else {
                relativeName = routed.path().getFileName().toString();
                fetchKey = new FetchKey(DEFAULT_FETCHER_ID, relativeName);
                LOG.debug("parse: spooled to {} ({} bytes)", relativeName,
                        Files.size(routed.path()));
            }

            // Set parse mode in context
            parseContext.set(ParseMode.class, parseMode);
            if (contentAsBytes) {
                parseContext.set(ContentBytesConfig.class, new ContentBytesConfig());
            }

            String presetName = liftPresetSelection(parseContext);

            // This parser is shared with /pipes, whose own default is EMIT_ALL. No
            // emitter is configured for /tika/rmeta/unpack requests (EmitKey.NO_EMIT
            // below) -- results must come back over the socket, so set PASSBACK_ALL
            // explicitly per-request rather than relying on the parser-level default.
            parseContext.set(EmitStrategyConfig.class, new EmitStrategyConfig(EmitStrategy.PASSBACK_ALL));

            FetchEmitTuple tuple = new FetchEmitTuple(
                    requestId,
                    fetchKey,
                    EmitKey.NO_EMIT,
                    metadata,
                    parseContext,
                    FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT,
                    presetName
            );

            // Execute parse via pipes - results will be passed back through socket
            long pipesStart = System.nanoTime();
            PipesResult result = pipesParser.parse(tuple);
            pipesNanos = System.nanoTime() - pipesStart;

            // Process result
            long postStart = System.nanoTime();
            List<Metadata> metadataList = processResult(result);
            if (relativeName != null) {
                stripSpoolIdentity(metadataList, relativeName, callerSuppliedName);
            }
            byte[] contentBytes = (contentAsBytes && result.emitData() != null)
                    ? result.emitData().getContentBytes() : null;
            postNanos = System.nanoTime() - postStart;
            logTiming(requestId, routed.route().name(), routeNanos, pipesNanos, postNanos,
                    System.nanoTime() - entryNanos);
            return new ParseOutput(metadataList, contentBytes);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TikaServerParseException("Parsing interrupted");
        } catch (PipesException e) {
            throw new TikaServerParseException(e);
        } finally {
            // The payload is request-owned: contexts are per-request today, but do not let
            // safety depend on that call-site discipline.
            parseContext.set(InlineBytes.class, null);
            if (routed != null) {
                routed.close();
            }
        }
    }

    // Only the name travels: the worker resolves the preset from its own config.
    private static String liftPresetSelection(ParseContext parseContext) {
        PresetSelection preset = parseContext.get(PresetSelection.class);
        if (preset == null) {
            return null;
        }
        parseContext.set(PresetSelection.class, null);
        return preset.name();
    }

    /**
     * One line per request on {@code org.apache.tika.pipes.timing.server}, microseconds.
     * {@code route_us} covers reading the request body and deciding inline-vs-spool;
     * {@code pipes_us} is the whole pipes round trip; {@code post_us} is result unpacking.
     * The HTTP/JAX-RS layer outside this method is measured from the client.
     */
    private static void logTiming(String id, String route, long routeNanos, long pipesNanos,
                                  long postNanos, long totalNanos) {
        if (!TIMING_LOG.isInfoEnabled()) {
            return;
        }
        TIMING_LOG.info("SERVER_TIMING id={} route={} route_us={} pipes_us={} post_us={} total_us={}",
                id, route, us(routeNanos), us(pipesNanos), us(postNanos), us(totalNanos));
    }

    private static long us(long nanos) {
        return nanos < 0 ? nanos : nanos / 1000L;
    }

    /** Longest suffix carried over from a client filename; keeps well clear of NAME_MAX. */
    private static final int MAX_SUFFIX_LENGTH = 20;

    /**
     * Removes the server's spool filename from the returned metadata.
     * <p>
     * The document is fetched from a temp file, so the fetcher records that path as
     * {@code tk:source-path} and, when the caller supplied no filename, it also becomes
     * {@code tk:resource-name} -- the field downstream consumers key document identity on.
     * Neither describes the caller's document: they name a file that has already been
     * deleted, and they expose the server's spooling scheme.
     */
    private static void stripSpoolIdentity(List<Metadata> metadataList, String spoolName,
                                           String callerSuppliedName) {
        if (metadataList == null) {
            return;
        }
        for (Metadata m : metadataList) {
            if (spoolName.equals(m.get(TikaCoreProperties.SOURCE_PATH))) {
                m.remove(TikaCoreProperties.SOURCE_PATH.getName());
            }
            if (callerSuppliedName == null
                    && spoolName.equals(m.get(TikaCoreProperties.RESOURCE_NAME_KEY))) {
                m.remove(TikaCoreProperties.RESOURCE_NAME_KEY.getName());
            }
        }
    }

    /**
     * Extracts a file suffix from the resource name for the spool file.
     * <p>
     * The resource name is client-supplied ({@code Content-Disposition}),
     * so the suffix is sanitized here rather than left for {@code Files.createTempFile} to
     * reject: a suffix containing a path separator makes it throw {@code IllegalArgumentException}
     * — not a traversal, since the JDK refuses it, but an uncaught 500 driven by a request
     * header. An over-long suffix likewise fails at the filesystem. The suffix is a parser
     * hint, so anything unusable is simply dropped in favour of {@code .tmp}.
     */
    private String getSuffix(Metadata metadata) {
        String resourceName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (resourceName != null) {
            int lastDot = resourceName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < resourceName.length() - 1) {
                String suffix = resourceName.substring(lastDot);
                if (isUsableSuffix(suffix)) {
                    return suffix;
                }
            }
        }
        return ".tmp";
    }

    private static boolean isUsableSuffix(String suffix) {
        if (suffix.length() > MAX_SUFFIX_LENGTH) {
            return false;
        }
        for (int i = 0; i < suffix.length(); i++) {
            char c = suffix.charAt(i);
            if (c == '/' || c == '\\' || c == '\0' || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds a JSON error response carrying a subset of the {@code PipesResult}
     * serialization. By default the body is just {@code {"status": "TIMEOUT"}}. The
     * {@code PipesResult} message frequently contains a server-side stack trace
     * (e.g. for {@code *_EXCEPTION} statuses) and is included: exception detail already
     * travels in {@code tk:exception:*} metadata on successful parses, so withholding it
     * here bought nothing while implying a confidentiality boundary that did not exist.
     * Successful-parse fields such as {@code emitData} are never part of an error body.
     * <p>
     * This allows clients to distinguish failure modes (TIMEOUT, OOM, UNSPECIFIED_CRASH, …)
     * without parsing plain-text bodies or inspecting custom headers.
     */
    private Response buildProcessFailureResponse(PipesResult result) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("status", result.status().name());
        if (result.message() != null && !result.message().isBlank()) {
            node.put("message", result.message());
        }
        String json;
        try {
            json = mapper.writeValueAsString(node);
        } catch (Exception e) {
            LOG.warn("Failed to serialize PipesResult error response as JSON; falling back to status-only body", e);
            json = "{\"status\":\"" + result.status().name() + "\"}";
        }
        return responseBuilder(result.status(), pipesConfig.getMaxWaitForClientMillis())
                .entity(json)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Processes the PipesResult and returns the metadata list.
     */
    private List<Metadata> processResult(PipesResult result) {
        if (result.isProcessCrash()) {
            // Process crashed (OOM, timeout, unspecified crash) — 503 with JSON status body
            LOG.warn("Parse process crashed: {}", result.status());
            throw new WebApplicationException(buildProcessFailureResponse(result));
        }

        if (result.isFatal() || result.isInitializationFailure()) {
            // Initialization/fatal error — JSON status body, HTTP status per mapStatusToHttpResponse
            // (500, or 429 for CLIENT_UNAVAILABLE_WITHIN_MS)
            LOG.error("Parse initialization/fatal error: {} - {}",
                    result.status(), result.message());
            throw new WebApplicationException(buildProcessFailureResponse(result));
        }

        if (result.isTaskException()) {
            // Task-level exception (fetch/emit error) — 500 with JSON status body
            LOG.warn("Parse task exception: {} - {}", result.status(), result.message());
            throw new WebApplicationException(buildProcessFailureResponse(result));
        }

        // Get metadata from result
        EmitData emitData = result.emitData();
        if (emitData != null && emitData.getMetadataList() != null) {
            return emitData.getMetadataList();
        }

        // Empty result
        LOG.debug("Parse returned empty result, status: {}", result.status());
        String message = result.message();
        if (message != null && !message.isEmpty()) {
            // Unbounded Metadata: this holds only our own error message, and this class is
            // constructed before TikaResource (which takes it as a constructor arg), so
            // reaching back for the configured write limiter would be circular.
            Metadata errorMetadata = new Metadata();
            errorMetadata.add(TikaCoreProperties.CONTAINER_EXCEPTION, message);
            return Collections.singletonList(errorMetadata);
        }

        return Collections.emptyList();
    }


    /**
     * 422 whose body is the already policy-formatted container exception. Re-wrapping it as
     * an exception message would let the mapper redact it a second time and append this
     * server's own frames.
     */
    public static WebApplicationException containerExceptionResponse(String containerException) {
        return new WebApplicationException(Response
                .status(422)
                .entity(containerException)
                .type(MediaType.TEXT_PLAIN)
                .build());
    }

    /**
     * Maps PipesResult status to HTTP response status. Private so no caller can map a
     * status without the Retry-After headers {@link #responseBuilder} attaches.
     */
    private static Response.Status mapStatusToHttpResponse(PipesResult.RESULT_STATUS status) {
        return switch (status) {
            case PARSE_SUCCESS, PARSE_SUCCESS_WITH_EXCEPTION, EMPTY_OUTPUT,
                 EMIT_SUCCESS, EMIT_SUCCESS_PARSE_EXCEPTION, EMIT_SUCCESS_PASSBACK,
                 PARSE_EXCEPTION_NO_EMIT, PARTIAL_TIMEOUT ->
                    Response.Status.OK;
            case TIMEOUT, OOM, UNSPECIFIED_CRASH ->
                    Response.Status.SERVICE_UNAVAILABLE;
            // Distinct from the crash statuses above: nothing failed here, the client
            // pool was simply at capacity for longer than maxWaitForClientMillis. 429
            // lets monitoring/alerting on HTTP status alone tell "we're at capacity"
            // (scale up numClients) apart from "a worker is actually crashing" (503).
            case CLIENT_UNAVAILABLE_WITHIN_MS ->
                    Response.Status.TOO_MANY_REQUESTS;
            // The caller named a fetcher/emitter this server does not have. Nothing failed
            // on our side, and retrying the same request will never succeed -- 500 told
            // clients to retry a request that is permanently malformed.
            case FETCHER_NOT_FOUND, EMITTER_NOT_FOUND, PRESET_NOT_FOUND ->
                    Response.Status.BAD_REQUEST;
            case PAYLOAD_LIMIT_EXCEEDED ->
                    Response.Status.REQUEST_ENTITY_TOO_LARGE;
            case FETCH_EXCEPTION, EMIT_EXCEPTION,
                 FETCHER_INITIALIZATION_EXCEPTION, EMITTER_INITIALIZATION_EXCEPTION,
                 FAILED_TO_INITIALIZE ->
                    Response.Status.INTERNAL_SERVER_ERROR;
        };
    }

    /** A crashed child is replaced promptly; no point holding clients off for a minute. */
    private static final long CRASH_RETRY_AFTER_SECONDS = 5;

    /**
     * Response builder for a pipes status, carrying {@code Retry-After} on the two families
     * where the server knows the condition is transient. Without it, a client loop's only
     * options are to give up or to hammer a pool that is already saturated.
     */
    public static Response.ResponseBuilder responseBuilder(PipesResult.RESULT_STATUS status,
                                                           long maxWaitForClientMillis) {
        Response.Status httpStatus = mapStatusToHttpResponse(status);
        Response.ResponseBuilder builder = Response.status(httpStatus);
        if (httpStatus == Response.Status.TOO_MANY_REQUESTS) {
            // The pool was already full for this long, so a faster retry just re-queues.
            builder.header(HttpHeaders.RETRY_AFTER, retryAfterSeconds(maxWaitForClientMillis));
        } else if (httpStatus == Response.Status.SERVICE_UNAVAILABLE) {
            builder.header(HttpHeaders.RETRY_AFTER, CRASH_RETRY_AFTER_SECONDS);
        }
        return builder;
    }

    /** Retry-After is whole seconds; clamp to >= 1 so a short wait doesn't round to 0. */
    static long retryAfterSeconds(long millis) {
        return Math.max(1, TimeUnit.MILLISECONDS.toSeconds(millis));
    }

    /**
     * Gets the PipesParser instance.
     */
    public PipesParser getPipesParser() {
        return pipesParser;
    }


    /**
     * Gets the PipesConfig instance.
     */
    public PipesConfig getPipesConfig() {
        return pipesConfig;
    }

    /**
     * Name of the file-system emitter used for UNPACK mode.
     * This emitter must be configured in tika-config.json with a basePath
     * pointing to a writable temp directory.
     */
    public static final String UNPACK_EMITTER_ID = "__unpack";

    /**
     * Fetcher/emitter ids the server wires up for its own request plumbing. Both are rooted at
     * the server's spool directories, so a caller who names one is reaching into other requests'
     * in-flight files rather than into storage of their own -- reading a pending upload through
     * the fetcher, or planting a file the unpack download path will hand back through the
     * emitter. The ids are not secret; they are compiled in and documented.
     * <p>
     * Applies only to caller-supplied tuples (/pipes, /async). This class names them itself when
     * it builds the tuples for /tika, /rmeta, and /unpack, which is exactly the use being
     * reserved.
     */
    /**
     * Backstop for ids the tuple deserializer cannot reach. {@code fetcher} and {@code emitter}
     * are already refused there for any tuple parsed from a request; the UnpackConfig emitter is
     * buried in a parse-context component, so it is checked only here.
     *
     * @throws BadRequestException if a caller-supplied tuple names a server-internal component.
     */
    public static void rejectReservedComponentIds(FetchEmitTuple t) {
        checkNotReserved(t.getFetchKey() == null ? null : t.getFetchKey().getFetcherId(), "fetcher");
        checkNotReserved(t.getEmitKey() == null ? null : t.getEmitKey().getEmitterId(), "emitter");
        UnpackConfig unpackConfig = t.getParseContext() == null
                ? null : t.getParseContext().get(UnpackConfig.class);
        if (unpackConfig != null) {
            checkNotReserved(unpackConfig.getEmitter(), "emitter");
        }
    }

    private static void checkNotReserved(String id, String kind) {
        if (ComponentIds.isSystem(id)) {
            throw new BadRequestException(
                    "'" + id.trim() + "' is reserved for tika-server's internal use and may not be named as a "
                            + kind + " by a request");
        }
    }

    /**
     * Parses content using UNPACK mode and returns a path to the zip file containing
     * extracted embedded documents.
     * <p>
     * This method:
     * 1. Spools input to the dedicated temp directory
     * 2. Configures UnpackConfig with zipEmbeddedFiles=true
     * 3. The pipes child process extracts embedded files and creates a zip
     * 4. The zip is emitted to the configured file-system emitter
     * 5. Returns the path to the zip file for streaming
     * <p>
     * The caller is responsible for deleting the zip file after streaming.
     *
     * @param tis the TikaInputStream containing the content to parse
     * @param metadata metadata to pass to the parser
     * @param parseContext parse context (may contain UnpackConfig, UnpackSelector, EmbeddedLimits)
     * @param saveAll if true, includes container text and metadata in the zip
     * @return UnpackResult containing path to zip file and metadata list
     * @throws IOException if parsing or file operations fail
     */
    public UnpackResult parseUnpack(TikaInputStream tis, Metadata metadata,
                                    ParseContext parseContext, boolean saveAll) throws IOException {
        String requestId = UUID.randomUUID().toString();
        PayloadRouter.Routed routed = null;
        String callerSuppliedName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        // The child emits the zip during parse, before we know whether the request
        // succeeds. Unless we hand it off to the caller (who streams then deletes it),
        // any failure path below must delete it -- otherwise it lingers in the unpack
        // temp dir until the JVM shutdown hook, filling disk under a stream of failures.
        boolean handedOff = false;

        try {
            routed = PayloadRouter.route(tis, maxInlineBytes, () ->
                    Files.createTempFile(inputTempDirectory, "tika-unpack-", getSuffix(metadata)));

            String relativeName = null;
            FetchKey fetchKey;
            if (routed.isInline()) {
                parseContext.set(InlineBytes.class, routed.inlineBytes());
                fetchKey = new FetchKey(BytesFetcher.FETCHER_ID,
                        callerSuppliedName == null ? "" : callerSuppliedName);
                LOG.debug("parseUnpack: {} bytes inline, requestId={}",
                        routed.inlineBytes().length(), requestId);
            } else {
                relativeName = routed.path().getFileName().toString();
                fetchKey = new FetchKey(DEFAULT_FETCHER_ID, relativeName);
                LOG.debug("parseUnpack: spooled to {} ({} bytes), requestId={}",
                        relativeName, Files.size(routed.path()), requestId);
            }

            // Set parse mode to UNPACK
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

            String presetName = liftPresetSelection(parseContext);

            // Shared parser (see parse() above) -- PASSBACK_ALL is also required here
            // for correctness: with UNPACK mode, EmitHandler.shouldEmit() only skips
            // re-emitting metadata (already emitted as part of the zip) when the
            // effective strategy is PASSBACK_ALL.
            parseContext.set(EmitStrategyConfig.class, new EmitStrategyConfig(EmitStrategy.PASSBACK_ALL));

            // Configure UnpackConfig - use existing or create new
            UnpackConfig unpackConfig = parseContext.get(UnpackConfig.class);
            if (unpackConfig == null) {
                unpackConfig = new UnpackConfig();
            }

            // Enable zip creation in the child process
            unpackConfig.setZipEmbeddedFiles(true);

            // Set suffix strategy to DETECTED so files get their proper extensions (e.g., .wav, .jpg)
            unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);

            // Set emitter to our file-system emitter
            unpackConfig.setEmitter(UNPACK_EMITTER_ID);

            // Include original document if saveAll is requested
            if (saveAll) {
                unpackConfig.setIncludeOriginal(true);
                unpackConfig.setIncludeMetadataInZip(true);
            }

            parseContext.set(UnpackConfig.class, unpackConfig);

            EmitKey emitKey = new EmitKey(UNPACK_EMITTER_ID, requestId);

        FetchEmitTuple tuple = new FetchEmitTuple(
                requestId,
                fetchKey,
                emitKey,
                metadata,
                parseContext,
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT,
                presetName
        );

            // Execute parse via pipes
            PipesResult result;
            try {
                result = pipesParser.parse(tuple);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TikaServerParseException("Parsing interrupted");
            } catch (PipesException e) {
                throw new TikaServerParseException(e);
            }

            // Check for errors
            if (result.isProcessCrash() || result.isFatal() || result.isInitializationFailure()) {
                LOG.warn("UNPACK parse failed: {} - {}", result.status(), result.message());
                throw new WebApplicationException(buildProcessFailureResponse(result));
            }

            if (result.isTaskException()) {
                LOG.warn("UNPACK task exception: {} - {}", result.status(), result.message());
                throw new WebApplicationException(buildProcessFailureResponse(result));
            }

            // Get metadata list from result
            List<Metadata> metadataList = Collections.emptyList();
            EmitData emitData = result.emitData();
            if (emitData != null && emitData.getMetadataList() != null) {
                metadataList = emitData.getMetadataList();
            }

            // Check for parse exceptions in the container document metadata
            // These should return appropriate HTTP status codes
            if (!metadataList.isEmpty()) {
                Metadata containerMetadata = metadataList.get(0);
                String containerException = containerMetadata.get(TikaCoreProperties.CONTAINER_EXCEPTION);
                if (containerException != null) {
                    throw containerExceptionResponse(containerException);
                }
            }

            // Determine the zip file path
            // Regular format: emitter.basePath + "/" + emitKey + "-embedded.zip"
            // Frictionless format: emitter.basePath + "/" + emitKey + "-frictionless.zip"
            boolean isFrictionless = unpackConfig.getOutputFormat() == UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS;
            Path zipFile = getEmittedZipPath(requestId, isFrictionless);

            if (relativeName != null) {
                stripSpoolIdentity(metadataList, relativeName, callerSuppliedName);
            }
            handedOff = true;
            return new UnpackResult(zipFile, metadataList);
        } finally {
            // See parse(): the inline payload must not outlive its request.
            parseContext.set(InlineBytes.class, null);
            if (routed != null) {
                routed.close();
            }
            if (!handedOff) {
                deleteEmittedZips(requestId);
            }
        }
    }

    /**
     * Deletes any zip the child may have emitted for this request, across both output
     * formats, when the request fails before the zip is handed to the caller.
     */
    private void deleteEmittedZips(String requestId) {
        if (unpackEmitterBasePath == null) {
            return;
        }
        Path base = unpackEmitterBasePath.normalize();
        for (String suffix : new String[] {"-embedded.zip", "-frictionless.zip"}) {
            Path zip = base.resolve(requestId + suffix).normalize();
            // requestId is a server-generated UUID, so this cannot escape today; the
            // containment check keeps the delete in-tree if that ever changes.
            if (!zip.startsWith(base)) {
                LOG.warn("Refusing to delete out-of-tree unpack path: {}", zip);
                continue;
            }
            try {
                Files.deleteIfExists(zip);
            } catch (IOException e) {
                LOG.warn("Failed to delete orphaned unpack zip: {}", zip, e);
            }
        }
    }

    /**
     * Gets the path where the zip file was emitted by the child process.
     * Regular format: unpackEmitterBasePath + "/" + requestId + "-embedded.zip"
     * Frictionless format: unpackEmitterBasePath + "/" + requestId + "-frictionless.zip"
     *
     * @param requestId the request ID used as emit key
     * @param isFrictionless true if Frictionless Data Package format was requested
     */
    private Path getEmittedZipPath(String requestId, boolean isFrictionless) throws IOException {
        if (unpackEmitterBasePath == null) {
            throw new IOException("Unpack emitter basePath not configured. " +
                    "UNPACK mode requires unpackEmitterBasePath to be set.");
        }

        String suffix = isFrictionless ? "-frictionless.zip" : "-embedded.zip";
        Path zipPath = unpackEmitterBasePath.resolve(requestId + suffix);
        if (!Files.exists(zipPath)) {
            // No embedded files were extracted - return null path
            LOG.debug("No zip file created (no embedded files): {}", zipPath);
            return null;
        }

        return zipPath;
    }

    /**
     * Result of UNPACK parsing containing the zip file path and metadata.
     *
     * @param zipFile path to the zip file containing extracted embedded documents,
     *                or null if no embedded documents were found. Caller must delete after use.
     * @param metadataList list of metadata objects from parsing
     */
    public record UnpackResult(
            Path zipFile,
            List<Metadata> metadataList
    ) {
        /**
         * Deletes the zip file. Call this after streaming is complete.
         */
        public void cleanup() {
            if (zipFile != null) {
                try {
                    Files.deleteIfExists(zipFile);
                } catch (IOException e) {
                    LOG.warn("Failed to delete zip file: {}", zipFile, e);
                }
            }
        }

        private static final Logger LOG = LoggerFactory.getLogger(UnpackResult.class);
    }
}
