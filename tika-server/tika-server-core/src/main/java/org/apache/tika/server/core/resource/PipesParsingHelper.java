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
    private final Path inputTempDirectory;

    public PipesParsingHelper(PipesParser pipesParser, PipesConfig pipesConfig) {
        this.pipesParser = pipesParser;
        this.pipesConfig = pipesConfig;

        // Determine input temp directory
        String configTempDir = pipesConfig.getTempDirectory();
        if (configTempDir != null && !configTempDir.isBlank()) {
            this.inputTempDirectory = Paths.get(configTempDir);
            if (!Files.isDirectory(this.inputTempDirectory)) {
                throw new IllegalArgumentException(
                        "Configured tempDirectory does not exist or is not a directory: " + configTempDir);
            }
        } else {
            this.inputTempDirectory = null; // Use system default
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
        Path inputTempFile = null;
        String requestId = UUID.randomUUID().toString();

        try {
            // Write input stream to temp file
            inputTempFile = createInputTempFile();
            Files.copy(inputStream, inputTempFile, StandardCopyOption.REPLACE_EXISTING);

            // Set parse mode in context
            parseContext.set(ParseMode.class, parseMode);

            // Create FetchEmitTuple - use NO_EMIT since we're using PASSBACK_ALL
            FetchKey fetchKey = new FetchKey(DEFAULT_FETCHER_ID, inputTempFile.toAbsolutePath().toString());

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
            // Clean up input temp file
            if (inputTempFile != null) {
                try {
                    Files.deleteIfExists(inputTempFile);
                } catch (IOException e) {
                    LOG.warn("Failed to delete input temp file: {}", inputTempFile, e);
                }
            }
        }
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
            Metadata errorMetadata = new Metadata();
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
}
