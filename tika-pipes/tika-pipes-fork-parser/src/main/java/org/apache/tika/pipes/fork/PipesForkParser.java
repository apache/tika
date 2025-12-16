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
package org.apache.tika.pipes.fork;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.PipesException;
import org.apache.tika.pipes.core.PipesParser;

/**
 * A ForkParser implementation backed by {@link PipesParser}.
 * <p>
 * <strong>This class is intended to replace the legacy
 * {@code org.apache.tika.fork.ForkParser}.</strong> The legacy ForkParser streamed
 * SAX events between processes, which was complex and error-prone. This implementation
 * uses the modern pipes infrastructure and returns parsed content in the metadata
 * (via {@link org.apache.tika.metadata.TikaCoreProperties#TIKA_CONTENT}).
 * <p>
 * This parser runs parsing in forked JVM processes, providing isolation from
 * crashes, memory leaks, and other issues that can occur during parsing.
 * Multiple forked processes can be used for concurrent parsing.
 * <p>
 * <strong>Getting Started:</strong> This class is designed as a simple entry point
 * to help users get started with forked parsing using files on the local filesystem.
 * Under the hood, it uses a {@code FileSystemFetcher} to read files. For more advanced
 * use cases, the Tika Pipes infrastructure supports many other sources and destinations
 * through plugins:
 * <ul>
 *   <li><strong>Fetchers</strong> (read from): S3, Azure Blob, Google Cloud Storage,
 *       HTTP, Microsoft Graph, and more</li>
 *   <li><strong>Emitters</strong> (write to): OpenSearch, Solr, S3, filesystem, and more</li>
 *   <li><strong>Pipes Iterators</strong> (batch processing): JDBC, CSV, filesystem crawling,
 *       and more</li>
 * </ul>
 * See the {@code tika-pipes} module and its submodules for available plugins. For
 * production batch processing, consider using {@code AsyncProcessor} or the
 * {@code tika-pipes-cli} directly with a JSON configuration file.
 * <p>
 * <strong>Thread Safety:</strong> This class is thread-safe. Multiple threads can
 * call {@link #parse} concurrently, and requests will be distributed across the
 * pool of forked processes.
 * <p>
 * <strong>Error Handling:</strong>
 * <ul>
 *   <li>Application errors (initialization failures, config errors) throw
 *       {@link PipesForkParserException}</li>
 *   <li>Process crashes (OOM, timeout) are returned in the result - the next
 *       parse will automatically restart the forked process</li>
 *   <li>Per-document errors (fetch/parse exceptions) are returned in the result</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * PipesForkParserConfig config = new PipesForkParserConfig();
 * config.setHandlerConfig(new HandlerConfig(HANDLER_TYPE.TEXT, PARSE_MODE.RMETA, -1, -1, true));
 *
 * try (PipesForkParser parser = new PipesForkParser(config)) {
 *     // Parse from a file
 *     try (TikaInputStream tis = TikaInputStream.get(Paths.get("/path/to/file.pdf"))) {
 *         PipesForkResult result = parser.parse(tis);
 *         for (Metadata m : result.getMetadataList()) {
 *             String content = m.get(TikaCoreProperties.TIKA_CONTENT);
 *             // process content and metadata
 *         }
 *     }
 *
 *     // Or parse from an InputStream (will be spooled to temp file)
 *     try (TikaInputStream tis = TikaInputStream.get(inputStream)) {
 *         PipesForkResult result = parser.parse(tis);
 *         // ...
 *     }
 * }
 * </pre>
 *
 * @see org.apache.tika.pipes.core.async.AsyncProcessor for batch processing
 */
public class PipesForkParser implements Closeable {

    public static final String DEFAULT_FETCHER_NAME = "fs";

    private final PipesForkParserConfig config;
    private final PipesParser pipesParser;
    private final Path tikaConfigPath;

    /**
     * Creates a new PipesForkParser with default configuration.
     *
     * @throws IOException if the temporary config file cannot be created
     * @throws TikaConfigException if configuration is invalid
     */
    public PipesForkParser() throws IOException, TikaConfigException {
        this(new PipesForkParserConfig());
    }

    /**
     * Creates a new PipesForkParser with the specified configuration.
     *
     * @param config the configuration for this parser
     * @throws IOException if the temporary config file cannot be created
     * @throws TikaConfigException if configuration is invalid
     */
    public PipesForkParser(PipesForkParserConfig config) throws IOException, TikaConfigException {
        this.config = config;
        this.tikaConfigPath = createTikaConfigFile();
        this.pipesParser = PipesParser.load(tikaConfigPath);
    }

    /**
     * Parse a file in a forked JVM process.
     *
     * @param tis the TikaInputStream to parse. If the stream doesn't have an underlying
     *            file, it will be spooled to a temporary file. The caller must keep
     *            the TikaInputStream open until this method returns.
     * @return the parse result containing metadata and content
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the parsing is interrupted
     * @throws PipesException if a pipes infrastructure error occurs
     * @throws PipesForkParserException if an application error occurs (initialization
     *         failure or configuration error)
     */
    public PipesForkResult parse(TikaInputStream tis)
            throws IOException, InterruptedException, PipesException, TikaException {
        return parse(tis, new Metadata(), new ParseContext());
    }

    /**
     * Parse a file in a forked JVM process with the specified metadata.
     *
     * @param tis the TikaInputStream to parse. If the stream doesn't have an underlying
     *            file, it will be spooled to a temporary file. The caller must keep
     *            the TikaInputStream open until this method returns.
     * @param metadata initial metadata (e.g., content type hint)
     * @return the parse result containing metadata and content
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the parsing is interrupted
     * @throws PipesException if a pipes infrastructure error occurs
     * @throws PipesForkParserException if an application error occurs (initialization
     *         failure or configuration error)
     */
    public PipesForkResult parse(TikaInputStream tis, Metadata metadata)
            throws IOException, InterruptedException, PipesException, TikaException {
        return parse(tis, metadata, new ParseContext());
    }

    /**
     * Parse a file in a forked JVM process with the specified metadata and parse context.
     *
     * @param tis the TikaInputStream to parse. If the stream doesn't have an underlying
     *            file, it will be spooled to a temporary file. The caller must keep
     *            the TikaInputStream open until this method returns.
     * @param metadata initial metadata (e.g., content type hint)
     * @param parseContext the parse context
     * @return the parse result containing metadata and content
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the parsing is interrupted
     * @throws PipesException if a pipes infrastructure error occurs
     * @throws PipesForkParserException if an application error occurs (initialization
     *         failure or configuration error)
     */
    public PipesForkResult parse(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException, InterruptedException, PipesException, TikaException {

        // Get the path - this will spool to a temp file if the stream doesn't have
        // an underlying file. The temp file is managed by TikaInputStream and will
        // be cleaned up when the TikaInputStream is closed.
        Path path = tis.getPath();
        String absolutePath = path.toAbsolutePath().toString();
        String id = absolutePath;

        FetchKey fetchKey = new FetchKey(config.getFetcherName(), absolutePath);
        EmitKey emitKey = new EmitKey("", id); // Empty emitter name since we're using PASSBACK_ALL

        // Add handler config to parse context so server knows how to handle content
        parseContext.set(HandlerConfig.class, config.getHandlerConfig());

        FetchEmitTuple tuple = new FetchEmitTuple(id, fetchKey, emitKey, metadata, parseContext);

        PipesResult result = pipesParser.parse(tuple);

        // Check for application errors and throw if necessary
        // Process crashes are NOT thrown - the next parse will restart the process
        checkForApplicationError(result);

        return new PipesForkResult(result);
    }

    /**
     * Checks if the result represents an application error and throws an exception if so.
     * <p>
     * Application errors that cause exceptions:
     * <ul>
     *   <li>Initialization failures (parser, fetcher, or emitter)</li>
     *   <li>Configuration errors (fetcher or emitter not found)</li>
     *   <li>Client unavailable within timeout</li>
     * </ul>
     * <p>
     * Process crashes (OOM, timeout, unspecified crash) are NOT thrown as exceptions.
     * The forked process will be automatically restarted on the next parse call.
     * Check {@link PipesForkResult#isProcessCrash()} to detect these cases.
     * <p>
     * Per-document errors (fetch exception, parse exception) are also NOT thrown.
     * These are returned in the result so the caller can handle them appropriately
     * (e.g., log and continue with the next file).
     *
     * @param result the pipes result to check
     * @throws PipesForkParserException if the result represents an application error
     */
    private void checkForApplicationError(PipesResult result) throws PipesForkParserException {
        PipesResult.RESULT_STATUS status = result.status();

        // Only throw for application errors that indicate infrastructure/config problems
        // Process crashes and per-document errors are returned to the caller
        switch (status) {
            case FAILED_TO_INITIALIZE:
                throw new PipesForkParserException(status,
                        "Failed to initialize parser" +
                        (result.message() != null ? ": " + result.message() : ""));

            case FETCHER_INITIALIZATION_EXCEPTION:
                throw new PipesForkParserException(status,
                        "Failed to initialize fetcher" +
                        (result.message() != null ? ": " + result.message() : ""));

            case EMITTER_INITIALIZATION_EXCEPTION:
                throw new PipesForkParserException(status,
                        "Failed to initialize emitter" +
                        (result.message() != null ? ": " + result.message() : ""));

            case FETCHER_NOT_FOUND:
                throw new PipesForkParserException(status,
                        "Fetcher not found" +
                        (result.message() != null ? ": " + result.message() : ""));

            case EMITTER_NOT_FOUND:
                throw new PipesForkParserException(status,
                        "Emitter not found" +
                        (result.message() != null ? ": " + result.message() : ""));

            case CLIENT_UNAVAILABLE_WITHIN_MS:
                throw new PipesForkParserException(status,
                        "No client available within timeout" +
                        (result.message() != null ? ": " + result.message() : ""));

            default:
                // Process crashes (OOM, TIMEOUT, UNSPECIFIED_CRASH) - not thrown,
                // next parse will restart the process automatically
                //
                // Per-document errors (FETCH_EXCEPTION, PARSE_EXCEPTION_NO_EMIT, etc.) -
                // not thrown, caller can check result and decide how to handle
                //
                // Success states - obviously not thrown
                break;
        }
    }

    @Override
    public void close() throws IOException {
        pipesParser.close();
        // Clean up temp config file
        if (tikaConfigPath != null) {
            Files.deleteIfExists(tikaConfigPath);
        }
    }

    /**
     * Creates a temporary tika-config.json file for the forked process.
     * This configures:
     * - FileSystemFetcher as the fetcher
     * - PASSBACK_ALL emit strategy (no emitter, return results to client)
     */
    private Path createTikaConfigFile() throws IOException {
        Path configFile = Files.createTempFile("tika-fork-config-", ".json");

        String jsonConfig = generateJsonConfig();
        Files.writeString(configFile, jsonConfig);

        return configFile;
    }

    private String generateJsonConfig() throws IOException {
        PipesConfig pc = config.getPipesConfig();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        StringWriter writer = new StringWriter();
        try (JsonGenerator gen = mapper.getFactory().createGenerator(writer)) {
            gen.writeStartObject();

            // Fetchers section
            gen.writeObjectFieldStart("fetchers");
            gen.writeObjectFieldStart(config.getFetcherName());
            gen.writeObjectFieldStart("file-system-fetcher");
            // No basePath - fetchKey will be treated as absolute path
            // Set allowAbsolutePaths to suppress the security warning since this is intentional
            gen.writeBooleanField("allowAbsolutePaths", true);
            gen.writeEndObject(); // file-system-fetcher
            gen.writeEndObject(); // fetcher name
            gen.writeEndObject(); // fetchers

            // Pipes configuration section
            gen.writeObjectFieldStart("pipes");
            gen.writeNumberField("numClients", pc.getNumClients());
            gen.writeNumberField("timeoutMillis", pc.getTimeoutMillis());
            gen.writeNumberField("startupTimeoutMillis", pc.getStartupTimeoutMillis());
            gen.writeNumberField("maxFilesProcessedPerProcess", pc.getMaxFilesProcessedPerProcess());

            // Emit strategy - PASSBACK_ALL means no emitter, return results to client
            gen.writeObjectFieldStart("emitStrategy");
            gen.writeStringField("type", "PASSBACK_ALL");
            gen.writeEndObject(); // emitStrategy

            // JVM args if specified
            ArrayList<String> jvmArgs = pc.getForkedJvmArgs();
            if (jvmArgs != null && !jvmArgs.isEmpty()) {
                gen.writeArrayFieldStart("forkedJvmArgs");
                for (String arg : jvmArgs) {
                    gen.writeString(arg);
                }
                gen.writeEndArray();
            }

            gen.writeEndObject(); // pipes

            // Plugin roots if specified
            if (config.getPluginsDir() != null) {
                gen.writeStringField("plugin-roots", config.getPluginsDir().toAbsolutePath().toString());
            }

            gen.writeEndObject(); // root
        }

        return writer.toString();
    }
}
