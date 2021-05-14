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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.config.ConfigBase;

public class PipesConfigBase extends ConfigBase {

    private long timeoutMillis = 30000;
    private long startupTimeoutMillis = 240000;
    private long shutdownClientAfterMillis = 300000;
    private int numClients = 10;
    private List<String> forkedJvmArgs = new ArrayList<>();
    private int maxFilesProcessed = 10000;
    private Path tikaConfig;
    private String javaPath = "java";

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public long getShutdownClientAfterMillis() {
        return shutdownClientAfterMillis;
    }

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
        return forkedJvmArgs;
    }

    public void setForkedJvmArgs(List<String> jvmArgs) {
        this.forkedJvmArgs = jvmArgs;
    }

    public int getMaxFilesProcessed() {
        return maxFilesProcessed;
    }

    public void setMaxFilesProcessed(int maxFilesProcessed) {
        this.maxFilesProcessed = maxFilesProcessed;
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
}
