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

import org.apache.tika.sax.BasicContentHandlerFactory;

class SimpleAsyncConfig {

    enum ExtractBytesMode {
        NONE,       // no byte extraction
        SHALLOW,    // -z: depth=1, no throw on max depth
        RECURSIVE   // -Z: full recursion
    }

    private String inputDir;
    private String outputDir;
    private Integer numClients;
    private Long timeoutMs;
    private String xmx;
    private String fileList;
    private String tikaConfig;//path to the tikaConfig file to be used in the forked process
    private ExtractBytesMode extractBytesMode;
    private final BasicContentHandlerFactory.HANDLER_TYPE handlerType;
    private final String pluginsDir;

    // Frictionless Data Package options
    private final String unpackFormat;  // "REGULAR" or "FRICTIONLESS"
    private final String unpackMode;    // "ZIPPED" or "DIRECTORY"
    private final boolean unpackIncludeMetadata;

    //TODO -- switch to a builder
    public SimpleAsyncConfig(String inputDir, String outputDir, Integer numClients, Long timeoutMs, String xmx, String fileList,
                             String tikaConfig, BasicContentHandlerFactory.HANDLER_TYPE handlerType,
                             ExtractBytesMode extractBytesMode, String pluginsDir) {
        this(inputDir, outputDir, numClients, timeoutMs, xmx, fileList, tikaConfig, handlerType,
                extractBytesMode, pluginsDir, null, null, false);
    }

    public SimpleAsyncConfig(String inputDir, String outputDir, Integer numClients, Long timeoutMs, String xmx, String fileList,
                             String tikaConfig, BasicContentHandlerFactory.HANDLER_TYPE handlerType,
                             ExtractBytesMode extractBytesMode, String pluginsDir,
                             String unpackFormat, String unpackMode, boolean unpackIncludeMetadata) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.numClients = numClients;
        this.timeoutMs = timeoutMs;
        this.xmx = xmx;
        this.fileList = fileList;
        this.tikaConfig = tikaConfig;
        this.handlerType = handlerType;
        this.extractBytesMode = extractBytesMode;
        this.pluginsDir = pluginsDir;
        this.unpackFormat = unpackFormat;
        this.unpackMode = unpackMode;
        this.unpackIncludeMetadata = unpackIncludeMetadata;
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

    public String getTikaConfig() {
        return tikaConfig;
    }

    public ExtractBytesMode getExtractBytesMode() {
        return extractBytesMode;
    }

    public BasicContentHandlerFactory.HANDLER_TYPE getHandlerType() {
        return handlerType;
    }

    public String getPluginsDir() {
        return pluginsDir;
    }

    public String getUnpackFormat() {
        return unpackFormat;
    }

    public String getUnpackMode() {
        return unpackMode;
    }

    public boolean isUnpackIncludeMetadata() {
        return unpackIncludeMetadata;
    }

    @Override
    public String toString() {
        return "SimpleAsyncConfig{" +
                "inputDir='" + inputDir + '\'' +
                ", outputDir='" + outputDir + '\'' +
                ", numClients=" + numClients +
                ", timeoutMs=" + timeoutMs +
                ", xmx='" + xmx + '\'' +
                ", fileList='" + fileList + '\'' +
                ", tikaConfig='" + tikaConfig + '\'' +
                ", extractBytesMode=" + extractBytesMode +
                ", handlerType=" + handlerType +
                ", pluginsDir='" + pluginsDir + '\'' +
                ", unpackFormat='" + unpackFormat + '\'' +
                ", unpackMode='" + unpackMode + '\'' +
                ", unpackIncludeMetadata=" + unpackIncludeMetadata +
                '}';
    }
}
