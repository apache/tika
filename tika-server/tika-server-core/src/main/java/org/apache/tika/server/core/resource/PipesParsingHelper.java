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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.EmitStrategy;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.PipesException;
import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
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

    /**
     * The fetcher ID used for reading temp files.
     * This fetcher is configured with basePath = inputTempDirectory.
     */
    public static final String DEFAULT_FETCHER_ID = "tika-server-fetcher";

    private final PipesParser pipesParser;
    private final PipesConfig pipesConfig;
    private final Path inputTempDirectory;
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
        this.unpackEmitterBasePath = unpackEmitterBasePath;

        if (inputTempDirectory == null || !Files.isDirectory(inputTempDirectory)) {
            throw new IllegalArgumentException(
                    "inputTempDirectory must be a valid directory: " + inputTempDirectory);
        }
        LOG.info("PipesParsingHelper initialized with inputTempDirectory: {}", inputTempDirectory);
    }

    /**
     * Gets the input temp directory path.
     * @return the input temp directory
     */
    public Path getInputTempDirectory() {
        return inputTempDirectory;
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
        String requestId = UUID.randomUUID().toString();
        Path tempFile = null;
        String callerSuppliedName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);

        try {
            // Spool input to our dedicated temp directory with proper suffix
            String suffix = getSuffix(metadata);
            tempFile = Files.createTempFile(inputTempDirectory, "tika-", suffix);
            Files.copy(tis, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String relativeName = tempFile.getFileName().toString();
            LOG.debug("parse: spooled to {} ({} bytes)", relativeName, Files.size(tempFile));

            // Set parse mode in context
            parseContext.set(ParseMode.class, parseMode);

            // This parser is shared with /pipes, whose own default is EMIT_ALL. No
            // emitter is configured for /tika/rmeta/unpack requests (EmitKey.NO_EMIT
            // below) -- results must come back over the socket, so set PASSBACK_ALL
            // explicitly per-request rather than relying on the parser-level default.
            parseContext.set(EmitStrategyConfig.class, new EmitStrategyConfig(EmitStrategy.PASSBACK_ALL));

            // Create FetchEmitTuple with relative filename (basePath is configured in fetcher)
            FetchKey fetchKey = new FetchKey(DEFAULT_FETCHER_ID, relativeName);

            FetchEmitTuple tuple = new FetchEmitTuple(
                    requestId,
                    fetchKey,
                    EmitKey.NO_EMIT,
                    metadata,
                    parseContext
            );

            // Execute parse via pipes - results will be passed back through socket
            PipesResult result = pipesParser.parse(tuple);

            // Process result
            List<Metadata> metadataList = processResult(result);
            stripSpoolIdentity(metadataList, relativeName, callerSuppliedName);
            return metadataList;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TikaServerParseException("Parsing interrupted");
        } catch (PipesException e) {
            throw new TikaServerParseException(e);
        } finally {
            // Clean up temp file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    LOG.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }
        }
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
     * The resource name is client-supplied ({@code Content-Disposition} / {@code File-Name}),
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
            if (c == '/' || c == '\\' || c == ' ' || Character.isISOControl(c)) {
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
            // (500, or 503 for CLIENT_UNAVAILABLE_WITHIN_MS)
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
            // Plain ParseContext, not TikaResource.createParseContext() -- this class is
            // constructed before TikaResource (which takes it as a constructor arg), so
            // depending back on TikaResource here would be circular. Only used to build
            // an error-result Metadata object; no actual parsing happens on this path.
            ParseContext context = new ParseContext();
            Metadata errorMetadata = Metadata.newInstance(context);
            errorMetadata.add(TikaCoreProperties.CONTAINER_EXCEPTION, message);
            return Collections.singletonList(errorMetadata);
        }

        return Collections.emptyList();
    }


    /**
     * Maps PipesResult status to HTTP response status.
     */
    public static Response.Status mapStatusToHttpResponse(PipesResult.RESULT_STATUS status) {
        return switch (status) {
            case PARSE_SUCCESS, PARSE_SUCCESS_WITH_EXCEPTION, EMPTY_OUTPUT,
                 EMIT_SUCCESS, EMIT_SUCCESS_PARSE_EXCEPTION, EMIT_SUCCESS_PASSBACK,
                 PARSE_EXCEPTION_NO_EMIT ->
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
            case FETCHER_NOT_FOUND, EMITTER_NOT_FOUND ->
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
            builder.header(HttpHeaders.RETRY_AFTER,
                    Math.max(1, TimeUnit.MILLISECONDS.toSeconds(maxWaitForClientMillis)));
        } else if (httpStatus == Response.Status.SERVICE_UNAVAILABLE) {
            builder.header(HttpHeaders.RETRY_AFTER, CRASH_RETRY_AFTER_SECONDS);
        }
        return builder;
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
    public static final String UNPACK_EMITTER_ID = "unpack-emitter";

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
    private static final Set<String> RESERVED_COMPONENT_IDS =
            Set.of(DEFAULT_FETCHER_ID, UNPACK_EMITTER_ID);

    /**
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
        if (id != null && RESERVED_COMPONENT_IDS.contains(id)) {
            throw new BadRequestException(
                    "'" + id + "' is reserved for tika-server's internal use and may not be named as a "
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
        Path tempFile = null;
        String callerSuppliedName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);

        try {
            // Spool input to our dedicated temp directory with proper suffix
            String suffix = getSuffix(metadata);
            tempFile = Files.createTempFile(inputTempDirectory, "tika-unpack-", suffix);
            Files.copy(tis, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String relativeName = tempFile.getFileName().toString();
            LOG.debug("parseUnpack: spooled to {} ({} bytes), requestId={}",
                    relativeName, Files.size(tempFile), requestId);

            // Set parse mode to UNPACK
            parseContext.set(ParseMode.class, ParseMode.UNPACK);

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

            // Create FetchEmitTuple with relative filename (basePath is configured in fetcher)
            FetchKey fetchKey = new FetchKey(DEFAULT_FETCHER_ID, relativeName);
            EmitKey emitKey = new EmitKey(UNPACK_EMITTER_ID, requestId);

        FetchEmitTuple tuple = new FetchEmitTuple(
                requestId,
                fetchKey,
                emitKey,
                metadata,
                parseContext
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
                    Response response = Response.status(422)
                            .entity(containerException)
                            .type("text/plain")
                            .build();
                    throw new WebApplicationException(response);
                }
            }

            // Determine the zip file path
            // Regular format: emitter.basePath + "/" + emitKey + "-embedded.zip"
            // Frictionless format: emitter.basePath + "/" + emitKey + "-frictionless.zip"
            boolean isFrictionless = unpackConfig.getOutputFormat() == UnpackConfig.OUTPUT_FORMAT.FRICTIONLESS;
            Path zipFile = getEmittedZipPath(requestId, isFrictionless);

            stripSpoolIdentity(metadataList, relativeName, callerSuppliedName);
            return new UnpackResult(zipFile, metadataList);
        } finally {
            // Clean up temp file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    LOG.warn("Failed to delete temp file: {}", tempFile, e);
                }
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
         * Returns an InputStream for the zip file.
         * Caller must close the stream and delete the file when done.
         */
        public InputStream getZipInputStream() throws IOException {
            if (zipFile == null) {
                return null;
            }
            return Files.newInputStream(zipFile);
        }

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
