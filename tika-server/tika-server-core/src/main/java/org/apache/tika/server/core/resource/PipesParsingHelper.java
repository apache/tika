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
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import org.apache.tika.server.core.TikaServerParseException;

/**
 * Helper class for pipes-based parsing in tika-server endpoints.
 * Handles temp file management, FetchEmitTuple creation, and result processing.
 * <p>
 * To use pipes-based parsing, your tika-config.json must include a file-system fetcher
 * with allowAbsolutePaths enabled:
 * <pre>
 * {
 *   "fetchers": {
 *     "file-system-fetcher": {
 *       "class": "org.apache.tika.pipes.fetcher.fs.FileSystemFetcher",
 *       "allowAbsolutePaths": true
 *     }
 *   }
 * }
 * </pre>
 */
public class PipesParsingHelper {

    private static final Logger LOG = LoggerFactory.getLogger(PipesParsingHelper.class);

    /**
     * The fetcher ID used for reading temp files.
     * This fetcher must be configured in the JSON config with allowAbsolutePaths=true.
     */
    public static final String DEFAULT_FETCHER_ID = "file-system-fetcher";

    private final PipesParser pipesParser;
    private final PipesConfig pipesConfig;
    private final Path tempDirectory;

    public PipesParsingHelper(PipesParser pipesParser, PipesConfig pipesConfig) {
        this.pipesParser = pipesParser;
        this.pipesConfig = pipesConfig;

        // Determine temp directory
        String configTempDir = pipesConfig.getTempDirectory();
        if (configTempDir != null && !configTempDir.isBlank()) {
            this.tempDirectory = Paths.get(configTempDir);
            if (!Files.isDirectory(this.tempDirectory)) {
                throw new IllegalArgumentException(
                        "Configured tempDirectory does not exist or is not a directory: " + configTempDir);
            }
        } else {
            this.tempDirectory = null; // Use system default
        }
    }

    /**
     * Parses content using pipes-based parsing with process isolation.
     *
     * @param inputStream the input stream containing the content to parse
     * @param metadata metadata to pass to the parser (may include filename, content-type, etc.)
     * @param parseContext parse context with handler configuration
     * @param parseMode the parse mode (RMETA or CONCATENATE)
     * @return list of metadata objects from parsing
     * @throws IOException if temp file operations fail
     * @throws TikaServerParseException if parsing fails
     */
    public List<Metadata> parse(InputStream inputStream, Metadata metadata,
                                 ParseContext parseContext, ParseMode parseMode) throws IOException {
        Path tempFile = null;
        try {
            // Write input stream to temp file
            tempFile = createTempFile();
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            // Set parse mode in context
            parseContext.set(ParseMode.class, parseMode);

            // Set emit strategy override to PASSBACK_ALL - we want results returned, not emitted
            parseContext.set(EmitStrategyConfig.class, new EmitStrategyConfig(EmitStrategy.PASSBACK_ALL));

            // Create FetchEmitTuple
            FetchKey fetchKey = new FetchKey(DEFAULT_FETCHER_ID, tempFile.toAbsolutePath().toString());
            FetchEmitTuple tuple = new FetchEmitTuple(
                    UUID.randomUUID().toString(),
                    fetchKey,
                    EmitKey.NO_EMIT,
                    metadata,
                    parseContext
            );

            // Execute parse via pipes
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
     * Processes the PipesResult and extracts metadata list.
     * Throws appropriate exceptions for error states.
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

        // Success cases
        EmitData emitData = result.emitData();
        if (emitData == null) {
            LOG.debug("Parse returned null emitData, status: {}", result.status());
            // Check if there's an exception message in the result
            String message = result.message();
            if (message != null && !message.isEmpty()) {
                // Create metadata with exception info
                Metadata metadata = new Metadata();
                metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION, message);
                return Collections.singletonList(metadata);
            }
            return Collections.emptyList();
        }

        List<Metadata> metadataList = emitData.getMetadataList();
        if (metadataList == null) {
            return Collections.emptyList();
        }

        // Handle parse success with exception - always add exception info to metadata
        // This includes PARSE_SUCCESS_WITH_EXCEPTION, EMIT_SUCCESS_PARSE_EXCEPTION, EMIT_SUCCESS_PASSBACK
        String stackTrace = emitData.getContainerStackTrace();
        boolean hasException = stackTrace != null && !stackTrace.isEmpty();

        if (hasException && !metadataList.isEmpty()) {
            // Check if this was a WriteLimitReached exception and set the flag
            checkWriteLimitReached(metadataList, stackTrace);
            // Add the stack trace to the metadata if not already set by pipes
            Metadata firstMetadata = metadataList.get(0);
            if (firstMetadata.get(TikaCoreProperties.CONTAINER_EXCEPTION) == null) {
                firstMetadata.set(TikaCoreProperties.CONTAINER_EXCEPTION, stackTrace);
            }
        }

        return metadataList;
    }

    /**
     * Checks if the parse result was due to write limit being reached.
     * This is a "soft" exception that should still return HTTP 200.
     * If detected from stack trace but not in metadata, sets the metadata flag.
     */
    private boolean checkWriteLimitReached(List<Metadata> metadataList, String stackTrace) {
        if (metadataList.isEmpty()) {
            return false;
        }
        Metadata metadata = metadataList.get(0);
        // Check metadata flag (set by RecursiveParserWrapper or CompositeParser)
        String flagValue = metadata.get(TikaCoreProperties.WRITE_LIMIT_REACHED);
        if ("true".equals(flagValue)) {
            return true;
        }
        // Also check stack trace for WriteLimitReachedException
        if (stackTrace != null && stackTrace.contains("WriteLimitReachedException")) {
            // Set the metadata flag if not already set (for consistency)
            metadata.set(TikaCoreProperties.WRITE_LIMIT_REACHED, "true");
            return true;
        }
        return false;
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
     * Creates a temp file in the configured temp directory.
     */
    private Path createTempFile() throws IOException {
        if (tempDirectory != null) {
            return Files.createTempFile(tempDirectory, "tika-server-", ".tmp");
        } else {
            return Files.createTempFile("tika-server-", ".tmp");
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
}
