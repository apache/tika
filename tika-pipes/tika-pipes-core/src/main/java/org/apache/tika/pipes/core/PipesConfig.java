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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;

public class PipesConfig {

    /**
     * default size to send back to the PipesClient for batch
     * emitting.  If an extract is larger than this, it will be emitted
     * directly from the forked PipesServer.
     */
    public static final long DEFAULT_MAX_FOR_EMIT_BATCH = 100000;

    public static final long DEFAULT_TIMEOUT_MILLIS = 60000;

    public static final long DEFAULT_STARTUP_TIMEOUT_MILLIS = 240000;

    public static final long DEFAULT_SHUTDOWN_CLIENT_AFTER_MILLS = 300000;

    public static final int DEFAULT_NUM_CLIENTS = 4;

    public static final int DEFAULT_MAX_FILES_PROCESSED_PER_PROCESS = 10000;

    public static final long DEFAULT_MAX_WAIT_FOR_CLIENT_MS = 60000;

    //if an extract is larger than this, the forked PipesServer should
    //emit the extract directly and not send the contents back to the PipesClient
    private long maxForEmitBatchBytes = DEFAULT_MAX_FOR_EMIT_BATCH;
    private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
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

    private ArrayList<String> forkedJvmArgs = new ArrayList<>();
    private String javaPath = "java";


    private String tikaConfig;

    /**
     * Loads PipesConfig from the "pipes" section of the JSON configuration.
     * <p>
     * This configuration is used by both PipesServer (forking process) and
     * AsyncProcessor (async processing). Some fields are specific to each:
     * <ul>
     *   <li>PipesServer uses: numClients, timeoutMillis, maxForEmitBatchBytes, etc.</li>
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
            return new PipesConfig();
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

    @JsonIgnore
    public Path getTikaConfigPath() {
        return tikaConfig != null ? Paths.get(tikaConfig) : null;
    }

    public String getTikaConfig() {
        return tikaConfig;
    }

    public void setTikaConfig(String tikaConfig) {
        this.tikaConfig = tikaConfig;
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
     *  What is the maximum bytes size per extract that
     *  will be allowed to be shipped back to the emit queue in the forking process.
     *  If an extract is too big, skip the emit queue and forward it directly from the
     *  forked PipesServer.
     *  If set to <code>0</code>, this will never send an extract back for batch emitting,
     *  but will always emit the extract directly from the forked PipeServer.
     *  If set to <code>-1</code>, this will always send the extract back for batch emitting.
     *
     * @return the threshold extract size at which to emit directly from the forked PipeServer
     */
    public long getMaxForEmitBatchBytes() {
        return maxForEmitBatchBytes;
    }

    public void setMaxForEmitBatchBytes(long maxForEmitBatchBytes) {
        this.maxForEmitBatchBytes = maxForEmitBatchBytes;
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
}
