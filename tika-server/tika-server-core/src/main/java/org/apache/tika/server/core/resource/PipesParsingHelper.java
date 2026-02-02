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
import java.util.UUID;

import jakarta.ws.rs.WebApplicationException;
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

        try {
            // Spool input to our dedicated temp directory with proper suffix
            String suffix = getSuffix(metadata);
            tempFile = Files.createTempFile(inputTempDirectory, "tika-", suffix);
            Files.copy(tis, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String relativeName = tempFile.getFileName().toString();
            LOG.debug("parse: spooled to {} ({} bytes)", relativeName, Files.size(tempFile));

            // Set parse mode in context
            parseContext.set(ParseMode.class, parseMode);

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
            return processResult(result);

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

    /**
     * Extracts file suffix from metadata (resource name or content-type).
     */
    private String getSuffix(Metadata metadata) {
        String resourceName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (resourceName != null) {
            int lastDot = resourceName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < resourceName.length() - 1) {
                return resourceName.substring(lastDot);
            }
        }
        // Default suffix
        return ".tmp";
    }

    /**
     * Processes the PipesResult and returns the metadata list.
     */
    private List<Metadata> processResult(PipesResult result) {
        if (result.isProcessCrash()) {
            // Process crashed (OOM, timeout, etc.) - return 503
            LOG.warn("Parse process crashed: {}", result.status());
            throw new WebApplicationException(
                    "Parse failed: " + result.status(),
                    mapStatusToHttpResponse(result.status()));
        }

        if (result.isFatal() || result.isInitializationFailure()) {
            // Fatal or initialization error - return 500
            LOG.error("Parse initialization/fatal error: {} - {}",
                    result.status(), result.message());
            throw new WebApplicationException(
                    "Parse failed: " + result.status(),
                    mapStatusToHttpResponse(result.status()));
        }

        if (result.isTaskException()) {
            // Task-level exception (fetch/emit error) - return 500
            LOG.warn("Parse task exception: {} - {}", result.status(), result.message());
            throw new WebApplicationException(
                    "Parse failed: " + result.status(),
                    Response.Status.INTERNAL_SERVER_ERROR);
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
            ParseContext context = TikaResource.createParseContext();
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
            case TIMEOUT, OOM, CLIENT_UNAVAILABLE_WITHIN_MS ->
                    Response.Status.SERVICE_UNAVAILABLE;
            case UNSPECIFIED_CRASH, FETCH_EXCEPTION, EMIT_EXCEPTION,
                 FETCHER_NOT_FOUND, EMITTER_NOT_FOUND,
                 FETCHER_INITIALIZATION_EXCEPTION, EMITTER_INITIALIZATION_EXCEPTION,
                 FAILED_TO_INITIALIZE ->
                    Response.Status.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Creates a temp file for input in the configured temp directory.
     */
    private Path createInputTempFile() throws IOException {
        if (inputTempDirectory != null) {
            return Files.createTempFile(inputTempDirectory, "tika-server-input-", ".tmp");
        } else {
            return Files.createTempFile("tika-server-input-", ".tmp");
        }
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
                throw new WebApplicationException(
                        "Parse failed: " + result.status(),
                        mapStatusToHttpResponse(result.status()));
            }

            if (result.isTaskException()) {
                LOG.warn("UNPACK task exception: {} - {}", result.status(), result.message());
                throw new WebApplicationException(
                        "Parse failed: " + result.message(),
                        Response.Status.INTERNAL_SERVER_ERROR);
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
                    // Map exception type to HTTP status
                    // 422 (Unprocessable Entity) for parse-related exceptions
                    int status = 422; // Default for parse exceptions
                    if (containerException.contains("EncryptedDocumentException") ||
                            containerException.contains("TikaException") ||
                            containerException.contains("NullPointerException") ||
                            containerException.contains("IllegalStateException")) {
                        status = 422;
                    }
                    // Build response with exception string as body for stack trace support
                    Response response = Response.status(status)
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
