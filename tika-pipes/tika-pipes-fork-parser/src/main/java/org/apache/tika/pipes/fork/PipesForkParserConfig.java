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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;

/**
 * Configuration for {@link PipesForkParser}.
 * <p>
 * This provides a simplified configuration API that abstracts away the
 * complexity of the pipes infrastructure.
 */
public class PipesForkParserConfig {

    private final PipesConfig pipesConfig;
    private ContentHandlerFactory contentHandlerFactory;
    private ParseMode parseMode = ParseMode.RMETA;
    private String fetcherName = PipesForkParser.DEFAULT_FETCHER_NAME;
    private Path pluginsDir;

    public PipesForkParserConfig() {
        this.pipesConfig = new PipesConfig();
        this.contentHandlerFactory = new BasicContentHandlerFactory(
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1);
        // Default to single client for simple fork parser use case
        this.pipesConfig.setNumClients(1);
    }

    /**
     * Get the underlying PipesConfig for advanced configuration.
     *
     * @return the pipes configuration
     */
    public PipesConfig getPipesConfig() {
        return pipesConfig;
    }

    /**
     * Get the content handler factory that specifies how content should be handled.
     *
     * @return the content handler factory
     */
    public ContentHandlerFactory getContentHandlerFactory() {
        return contentHandlerFactory;
    }

    /**
     * Set the content handler factory.
     *
     * @param contentHandlerFactory the content handler factory
     * @return this config for chaining
     */
    public PipesForkParserConfig setContentHandlerFactory(ContentHandlerFactory contentHandlerFactory) {
        this.contentHandlerFactory = contentHandlerFactory;
        return this;
    }

    /**
     * Get the parse mode.
     *
     * @return the parse mode
     */
    public ParseMode getParseMode() {
        return parseMode;
    }

    /**
     * Set the handler type (TEXT, HTML, XML, etc.).
     *
     * @param type the handler type
     * @return this config for chaining
     */
    public PipesForkParserConfig setHandlerType(BasicContentHandlerFactory.HANDLER_TYPE type) {
        this.contentHandlerFactory = new BasicContentHandlerFactory(type, -1);
        return this;
    }

    /**
     * Set the parse mode (RMETA for recursive metadata, CONCATENATE for single document).
     *
     * @param parseMode the parse mode
     * @return this config for chaining
     */
    public PipesForkParserConfig setParseMode(ParseMode parseMode) {
        this.parseMode = parseMode;
        return this;
    }

    /**
     * Set the write limit for content extraction.
     *
     * @param writeLimit the maximum characters to extract (-1 for unlimited)
     * @return this config for chaining
     */
    public PipesForkParserConfig setWriteLimit(int writeLimit) {
        if (contentHandlerFactory instanceof BasicContentHandlerFactory bcf) {
            this.contentHandlerFactory = new BasicContentHandlerFactory(bcf.getType(), writeLimit);
        }
        return this;
    }

    /**
     * Set the maximum number of embedded resources to process.
     *
     * @param maxEmbeddedResources the maximum embedded resources (-1 for unlimited)
     * @return this config for chaining
     */
    public PipesForkParserConfig setMaxEmbeddedResources(int maxEmbeddedResources) {
        if (contentHandlerFactory instanceof BasicContentHandlerFactory bcf) {
            bcf.setMaxEmbeddedResources(maxEmbeddedResources);
        }
        return this;
    }

    /**
     * Get the fetcher name used for file system fetching.
     *
     * @return the fetcher name
     */
    public String getFetcherName() {
        return fetcherName;
    }

    /**
     * Set the fetcher name.
     *
     * @param fetcherName the fetcher name
     * @return this config for chaining
     */
    public PipesForkParserConfig setFetcherName(String fetcherName) {
        this.fetcherName = fetcherName;
        return this;
    }

    /**
     * Set the timeout in milliseconds for parsing operations.
     *
     * @param timeoutMillis the timeout in milliseconds
     * @return this config for chaining
     */
    public PipesForkParserConfig setTimeoutMillis(long timeoutMillis) {
        pipesConfig.setTimeoutMillis(timeoutMillis);
        return this;
    }

    /**
     * Set the JVM arguments for the forked process.
     *
     * @param jvmArgs the JVM arguments (e.g., "-Xmx512m")
     * @return this config for chaining
     */
    public PipesForkParserConfig setJvmArgs(List<String> jvmArgs) {
        pipesConfig.setForkedJvmArgs(new ArrayList<>(jvmArgs));
        return this;
    }

    /**
     * Add a JVM argument for the forked process.
     *
     * @param arg the JVM argument to add
     * @return this config for chaining
     */
    public PipesForkParserConfig addJvmArg(String arg) {
        pipesConfig.getForkedJvmArgs().add(arg);
        return this;
    }

    /**
     * Set the Java executable path.
     *
     * @param javaPath path to the java executable
     * @return this config for chaining
     */
    public PipesForkParserConfig setJavaPath(String javaPath) {
        pipesConfig.setJavaPath(javaPath);
        return this;
    }

    /**
     * Set the maximum number of files to process before restarting the forked process.
     * This helps prevent memory leaks from accumulating.
     *
     * @param maxFiles the maximum files per process (-1 for unlimited)
     * @return this config for chaining
     */
    public PipesForkParserConfig setMaxFilesPerProcess(int maxFiles) {
        pipesConfig.setMaxFilesProcessedPerProcess(maxFiles);
        return this;
    }

    /**
     * <b>EXPERT:</b> Set the number of forked JVM processes (clients) to use for parsing.
     * <p>
     * This enables concurrent parsing across multiple forked processes. Each client
     * is an independent JVM that can parse documents in parallel. When multiple threads
     * call {@link PipesForkParser#parse}, requests are distributed across the pool
     * of forked processes.
     * <p>
     * <b>When to use:</b> Set this higher than 1 when you need to parse many documents
     * concurrently and have sufficient CPU cores and memory. Each forked process
     * consumes memory independently (based on your JVM args like -Xmx).
     * <p>
     * <b>Default:</b> 1 (single forked process, suitable for simple sequential use)
     *
     * @param numClients the number of forked JVM processes (must be &gt;= 1)
     * @return this config for chaining
     * @throws IllegalArgumentException if numClients is less than 1
     */
    public PipesForkParserConfig setNumClients(int numClients) {
        if (numClients < 1) {
            throw new IllegalArgumentException("numClients must be >= 1");
        }
        pipesConfig.setNumClients(numClients);
        return this;
    }

    /**
     * Get the number of forked JVM processes configured.
     *
     * @return the number of clients
     */
    public int getNumClients() {
        return pipesConfig.getNumClients();
    }

    /**
     * Set the startup timeout in milliseconds.
     *
     * @param startupTimeoutMillis the startup timeout
     * @return this config for chaining
     */
    public PipesForkParserConfig setStartupTimeoutMillis(long startupTimeoutMillis) {
        pipesConfig.setStartupTimeoutMillis(startupTimeoutMillis);
        return this;
    }

    /**
     * Get the plugins directory.
     *
     * @return the plugins directory, or null if not set
     */
    public Path getPluginsDir() {
        return pluginsDir;
    }

    /**
     * Set the plugins directory where plugin zips are located.
     * This directory should contain the tika-pipes-file-system zip
     * and any other required plugins.
     *
     * @param pluginsDir the plugins directory
     * @return this config for chaining
     */
    public PipesForkParserConfig setPluginsDir(Path pluginsDir) {
        this.pluginsDir = pluginsDir;
        return this;
    }
}
