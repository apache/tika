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
package org.apache.tika.pipes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tika.config.ConfigBase;

public class PipesConfigBase extends ConfigBase {

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

    //if an extract is larger than this, the forked PipesServer should
    //emit the extract directly and not send the contents back to the PipesClient
    private long maxForEmitBatchBytes = DEFAULT_MAX_FOR_EMIT_BATCH;
    private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    private long startupTimeoutMillis = DEFAULT_STARTUP_TIMEOUT_MILLIS;
    private long sleepOnStartupTimeoutMillis = DEFAULT_STARTUP_TIMEOUT_MILLIS;

    private long shutdownClientAfterMillis = DEFAULT_SHUTDOWN_CLIENT_AFTER_MILLS;
    private int numClients = DEFAULT_NUM_CLIENTS;

    private int maxFilesProcessedPerProcess = DEFAULT_MAX_FILES_PROCESSED_PER_PROCESS;

    private List<String> forkedJvmArgs = new ArrayList<>();
    private Path tikaConfig;
    private String javaPath = "java";

    private Path pipesTmpDir = null;

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

    public List<String> getForkedJvmArgs() {
        //defensive copy
        List<String> ret = new ArrayList<>();
        ret.addAll(forkedJvmArgs);
        return ret;
    }

    public void setStartupTimeoutMillis(long startupTimeoutMillis) {
        this.startupTimeoutMillis = startupTimeoutMillis;
    }

    public void setForkedJvmArgs(List<String> jvmArgs) {
        this.forkedJvmArgs = Collections.unmodifiableList(jvmArgs);
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

    public Path getTikaConfig() {
        return tikaConfig;
    }

    public void setTikaConfig(Path tikaConfig) {
        this.tikaConfig = tikaConfig;
    }

    public void setTikaConfig(String tikaConfig) {
        setTikaConfig(Paths.get(tikaConfig));
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

    public void setPipesTmpDir(String pipesTmpDir) {
        setPipesTmpDirPath(Paths.get(pipesTmpDir));
    }

    public void setPipesTmpDirPath(Path pipesTmpDir) {
        this.pipesTmpDir = pipesTmpDir;
    }

    public Path getPipesTmpDir() throws IOException {
        if (pipesTmpDir == null) {
            pipesTmpDir = Files.createTempDirectory("tika-pipes-tmp-dir-");
        } else {
            if (! Files.isDirectory(pipesTmpDir)) {
                Files.createDirectories(pipesTmpDir);
            }
        }
        return pipesTmpDir;
    }
}
