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
package org.apache.tika.async.cli;

class SimpleAsyncConfig {

    private String inputDir;
    private String outputDir;
    private Integer numClients;
    private Long timeoutMs;
    private String xmx;
    private String fileList;

    public SimpleAsyncConfig(String inputDir, String outputDir, Integer numClients, Long timeoutMs, String xmx, String fileList) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.numClients = numClients;
        this.timeoutMs = timeoutMs;
        this.xmx = xmx;
        this.fileList = fileList;
    }

    public String getInputDir() {
        return inputDir;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public Integer getNumClients() {
        return numClients;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public String getXmx() {
        return xmx;
    }

    public String getFileList() {
        return fileList;
    }
}
