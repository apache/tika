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
package org.apache.tika.pipes.core;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;

public class PipesConfig {


    public static final long DEFAULT_TIMEOUT_MILLIS = 60000;

    public static final long DEFAULT_STARTUP_TIMEOUT_MILLIS = 240000;

    public static final long DEFAULT_SHUTDOWN_CLIENT_AFTER_MILLS = 300000;

    public static final int DEFAULT_NUM_CLIENTS = 4;

    public static final int DEFAULT_MAX_FILES_PROCESSED_PER_PROCESS = 10000;

    public static final long DEFAULT_MAX_WAIT_FOR_CLIENT_MS = 60000;

    public static final long DEFAULT_SOCKET_TIMEOUT_MS = 60000;

    public static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 1000;

    /**
     * The emit strategy configuration determines how the forked PipesServer handles emitting data.
     * See {@link EmitStrategyConfig} for details.
     */
    private EmitStrategyConfig emitStrategy = new EmitStrategyConfig(EmitStrategyConfig.DEFAULT_EMIT_STRATEGY);

    private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    private long socketTimeoutMs = DEFAULT_SOCKET_TIMEOUT_MS;
    private long heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS;
    private long startupTimeoutMillis = DEFAULT_STARTUP_TIMEOUT_MILLIS;
    private long sleepOnStartupTimeoutMillis = DEFAULT_STARTUP_TIMEOUT_MILLIS;

    private long shutdownClientAfterMillis = DEFAULT_SHUTDOWN_CLIENT_AFTER_MILLS;
    private int numClients = DEFAULT_NUM_CLIENTS;

    private long maxWaitForClientMillis = DEFAULT_MAX_WAIT_FOR_CLIENT_MS;
    private int maxFilesProcessedPerProcess = DEFAULT_MAX_FILES_PROCESSED_PER_PROCESS;
    public static final int DEFAULT_STALE_FETCHER_TIMEOUT_SECONDS = 600;
    private int staleFetcherTimeoutSeconds = DEFAULT_STALE_FETCHER_TIMEOUT_SECONDS;
    public static final int DEFAULT_STALE_FETCHER_DELAY_SECONDS = 60;
    private int staleFetcherDelaySeconds = DEFAULT_STALE_FETCHER_DELAY_SECONDS;

    // Async-specific fields (used by AsyncProcessor, ignored by PipesServer)
    public static final long DEFAULT_EMIT_WITHIN_MILLIS = 10000;
    public static final long DEFAULT_EMIT_MAX_ESTIMATED_BYTES = 100000;
    public static final int DEFAULT_QUEUE_SIZE = 10000;
    public static final int DEFAULT_NUM_EMITTERS = 1;

    private long emitWithinMillis = DEFAULT_EMIT_WITHIN_MILLIS;
    private long emitMaxEstimatedBytes = DEFAULT_EMIT_MAX_ESTIMATED_BYTES;
    private int queueSize = DEFAULT_QUEUE_SIZE;
    private int numEmitters = DEFAULT_NUM_EMITTERS;
    private boolean emitIntermediateResults = false;
    /**
     * When true, only stop processing on fatal errors (FAILED_TO_INITIALIZE).
     * When false (default), also stop on initialization failures and not-found errors.
     * <p>
     * Use true for server mode (tika-server /pipes, /async) where different requests
     * may use different fetchers/emitters.
     * Use false (default) for CLI batch mode where all tasks typically use the same
     * fetcher/emitter configuration.
     */
    private boolean stopOnlyOnFatal = false;

    /**
     * Default parse mode for how embedded documents are handled.
     * Can be overridden per-file via ParseContext.
     */
    private ParseMode parseMode = ParseMode.RMETA;

    /**
     * Default behavior when a parse exception occurs.
     */
    private FetchEmitTuple.ON_PARSE_EXCEPTION onParseException = FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT;

    private ArrayList<String> forkedJvmArgs = new ArrayList<>();
    private String javaPath = "java";

    /**
     * Loads PipesConfig from the "pipes" section of the JSON configuration.
     * <p>
     * This configuration is used by both PipesServer (forking process) and
     * AsyncProcessor (async processing). Some fields are specific to each:
     * <ul>
     *   <li>PipesServer uses: numClients, timeoutMillis, directEmitThresholdBytes, etc.</li>
     *   <li>AsyncProcessor uses: emitWithinMillis, queueSize, numEmitters, etc.</li>
     * </ul>
     * Unused fields in each context are simply ignored.
     *
     * @param tikaJsonConfig the JSON configuration to load from
     * @return the loaded PipesConfig, or a new default instance if not found in config
     * @throws IOException if deserialization fails
     * @throws TikaConfigException if configuration is invalid
     */
    public static PipesConfig load(TikaJsonConfig tikaJsonConfig) throws IOException, TikaConfigException {
        PipesConfig config = tikaJsonConfig.deserialize("pipes", PipesConfig.class);
        if (config == null) {
            config = new PipesConfig();
        }
        return config;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * How long to wait in milliseconds before timing out the forked process.
     * @param timeoutMillis
     */
    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public long getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    /**
     * Socket timeout in milliseconds for reading from the forked process.
     * If no data is received within this time, the connection is considered timed out.
     * This is different from timeoutMillis which is the parse/processing timeout.
     * @param socketTimeoutMs
     */
    public void setSocketTimeoutMs(long socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    /**
     * Interval in milliseconds between heartbeat messages sent from server to client.
     * Should be significantly less than socketTimeoutMs to ensure the client doesn't timeout.
     * WARNING: Setting this >= socketTimeoutMs will cause socket timeouts during normal processing.
     * This only exists for testing. We encourage you never to use it.
     * @param heartbeatIntervalMs
     */
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public long getShutdownClientAfterMillis() {
        return shutdownClientAfterMillis;
    }

    /**
     * If the client has been inactive after this many milliseconds,
     * shut it down.
     *
     * @param shutdownClientAfterMillis
     */
    public void setShutdownClientAfterMillis(long shutdownClientAfterMillis) {
        this.shutdownClientAfterMillis = shutdownClientAfterMillis;
    }

    public int getNumClients() {
        return numClients;
    }

    public void setNumClients(int numClients) {
        this.numClients = numClients;
    }

    public void setForkedJvmArgs(ArrayList<String> jvmArgs) {
        this.forkedJvmArgs = jvmArgs;
    }
    //ArrayList to make jackson happy
    public ArrayList<String> getForkedJvmArgs() {
        return forkedJvmArgs;
    }

    public void setStartupTimeoutMillis(long startupTimeoutMillis) {
        this.startupTimeoutMillis = startupTimeoutMillis;
    }


    /**
     * Restart the forked PipesServer after it has processed this many files to avoid
     * slow-building memory leaks.
     * @return
     */
    public int getMaxFilesProcessedPerProcess() {
        return maxFilesProcessedPerProcess;
    }

    public void setMaxFilesProcessedPerProcess(int maxFilesProcessedPerProcess) {
        this.maxFilesProcessedPerProcess = maxFilesProcessedPerProcess;
    }

    public String getJavaPath() {
        return javaPath;
    }

    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public long getStartupTimeoutMillis() {
        return startupTimeoutMillis;
    }

    /**
     * Get the emit strategy configuration.
     *
     * @return the emit strategy configuration
     */
    public EmitStrategyConfig getEmitStrategy() {
        return emitStrategy;
    }

    /**
     * Set the emit strategy configuration.
     *
     * @param emitStrategy the emit strategy configuration
     */
    public void setEmitStrategy(EmitStrategyConfig emitStrategy) {
        this.emitStrategy = emitStrategy;
    }

    public long getSleepOnStartupTimeoutMillis() {
        return sleepOnStartupTimeoutMillis;
    }

    public void setSleepOnStartupTimeoutMillis(long sleepOnStartupTimeoutMillis) {
        this.sleepOnStartupTimeoutMillis = sleepOnStartupTimeoutMillis;
    }

    public int getStaleFetcherTimeoutSeconds() {
        return staleFetcherTimeoutSeconds;
    }

    public void setStaleFetcherTimeoutSeconds(int staleFetcherTimeoutSeconds) {
        this.staleFetcherTimeoutSeconds = staleFetcherTimeoutSeconds;
    }

    public int getStaleFetcherDelaySeconds() {
        return staleFetcherDelaySeconds;
    }

    public void setStaleFetcherDelaySeconds(int staleFetcherDelaySeconds) {
        this.staleFetcherDelaySeconds = staleFetcherDelaySeconds;
    }

    public long getMaxWaitForClientMillis() {
        return maxWaitForClientMillis;
    }

    public void setMaxWaitForClientMillis(long maxWaitForClientMillis) {
        this.maxWaitForClientMillis = maxWaitForClientMillis;
    }

    // Async-specific getters/setters (used by AsyncProcessor, ignored by PipesServer)

    public long getEmitWithinMillis() {
        return emitWithinMillis;
    }

    /**
     * If nothing has been emitted in this amount of time
     * and the {@link #getEmitMaxEstimatedBytes()} has not been reached yet,
     * emit what's in the emit queue.
     *
     * @param emitWithinMillis time in milliseconds
     */
    public void setEmitWithinMillis(long emitWithinMillis) {
        this.emitWithinMillis = emitWithinMillis;
    }

    /**
     * When the emit queue hits this estimated size (sum of
     * estimated extract sizes), emit the batch.
     *
     * @return the maximum estimated bytes before emitting
     */
    public long getEmitMaxEstimatedBytes() {
        return emitMaxEstimatedBytes;
    }

    public void setEmitMaxEstimatedBytes(long emitMaxEstimatedBytes) {
        this.emitMaxEstimatedBytes = emitMaxEstimatedBytes;
    }

    /**
     * FetchEmitTuple queue size
     *
     * @return the queue size
     */
    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    /**
     * Number of emitters
     *
     * @return the number of emitters
     */
    public int getNumEmitters() {
        return numEmitters;
    }

    public void setNumEmitters(int numEmitters) {
        this.numEmitters = numEmitters;
    }

    public boolean isEmitIntermediateResults() {
        return emitIntermediateResults;
    }

    public void setEmitIntermediateResults(boolean emitIntermediateResults) {
        this.emitIntermediateResults = emitIntermediateResults;
    }

    /**
     * When true, only stop processing on fatal errors (FAILED_TO_INITIALIZE).
     * When false (default), also stop on initialization failures (FETCHER_INITIALIZATION_EXCEPTION,
     * EMITTER_INITIALIZATION_EXCEPTION, CLIENT_UNAVAILABLE_WITHIN_MS) and not-found errors
     * (FETCHER_NOT_FOUND, EMITTER_NOT_FOUND).
     * <p>
     * Use true for server mode (tika-server /pipes, /async) where different requests
     * may use different fetchers/emitters - a bad request shouldn't kill the server.
     * Use false (default) for CLI batch mode where all tasks typically use the same
     * fetcher/emitter configuration - no point continuing if configuration is wrong.
     *
     * @return true if only fatal errors should stop processing
     */
    public boolean isStopOnlyOnFatal() {
        return stopOnlyOnFatal;
    }

    public void setStopOnlyOnFatal(boolean stopOnlyOnFatal) {
        this.stopOnlyOnFatal = stopOnlyOnFatal;
    }

    /**
     * Gets the default parse mode for how embedded documents are handled.
     *
     * @return the default parse mode
     */
    public ParseMode getParseMode() {
        return parseMode;
    }

    /**
     * Sets the default parse mode for how embedded documents are handled.
     * This can be overridden per-file via ParseContext.
     *
     * @param parseMode the parse mode (RMETA or CONCATENATE)
     */
    public void setParseMode(ParseMode parseMode) {
        this.parseMode = parseMode;
    }

    /**
     * Sets the default parse mode from a string.
     *
     * @param parseMode the parse mode name (rmeta or concatenate)
     */
    public void setParseMode(String parseMode) {
        this.parseMode = ParseMode.parse(parseMode);
    }

    /**
     * Gets the default behavior when a parse exception occurs.
     *
     * @return the parse exception behavior
     */
    public FetchEmitTuple.ON_PARSE_EXCEPTION getOnParseException() {
        return onParseException;
    }

    /**
     * Sets the default behavior when a parse exception occurs.
     *
     * @param onParseException the parse exception behavior
     */
    public void setOnParseException(FetchEmitTuple.ON_PARSE_EXCEPTION onParseException) {
        this.onParseException = onParseException;
    }
}
