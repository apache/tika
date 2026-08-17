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
package org.apache.tika.server.core;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;

public class TikaServerConfig {

    public static final int DEFAULT_PORT = 9998;
    public static final String DEFAULT_HOST = "localhost";
    private static final Logger LOG = LoggerFactory.getLogger(TikaServerConfig.class);
    /**
     * Endpoints that expose the pipes/fetch machinery (process-isolated pipes
     * parsing and async batch processing). Selecting any of these requires
     * {@code allowPipes=true} as an explicit, deliberate opt-in.
     * <p>
     * {@code status} is intentionally not in this set: it exposes only aggregate
     * counters and is enabled simply by listing it under {@code endpoints}.
     */
    private static final Set<String> ENDPOINTS_REQUIRING_PIPES =
            new HashSet<>(Arrays.asList("pipes", "async"));
    /**
     * Default request-body cap: 1 GiB. Generous enough for essentially any single-document
     * upload, bounded so one request cannot fill the temp directory. Operators who genuinely
     * need larger bodies raise it explicitly; setting a negative value restores no limit.
     */
    public static final long DEFAULT_MAX_REQUEST_SIZE_BYTES = 1024L * 1024L * 1024L;

    /** How long a full /async queue blocks a POST before it is rejected with 429. */
    public static final long DEFAULT_MAX_QUEUE_PAUSE_MILLIS = 60000;

    private boolean allowPipes = false;
    private boolean allowPerRequestConfig = false;
    private String cors = "";
    private long maxQueuePauseMillis = DEFAULT_MAX_QUEUE_PAUSE_MILLIS;
    private long maxRequestSizeBytes = DEFAULT_MAX_REQUEST_SIZE_BYTES;
    private String id = UUID
            .randomUUID()
            .toString();
    private int port = DEFAULT_PORT;
    private String host = DEFAULT_HOST;
    private String requestLogLevel = "";
    private Path configPath;
    private ArrayList<String> endpoints = new ArrayList<>();

    private TlsConfig tlsConfig = new TlsConfig();

    /**
     * Config with only the defaults
     */
    public static TikaServerConfig load() {
        return new TikaServerConfig();
    }

    public static TikaServerConfig load(CommandLine commandLine) throws IOException, TikaException {

        TikaServerConfig config = null;
        Set<String> settings = new HashSet<>();

        if (commandLine.hasOption("c")) {
            config = load(Paths.get(commandLine.getOptionValue("c")), commandLine, settings);
        } else {
            config = new TikaServerConfig();
        }

        //port, host and id can be overwritten on the commandline at runtime
        if (commandLine.hasOption("p")) {
            config.setPort(Integer.parseInt(commandLine.getOptionValue("p")));
            settings.add("port");
        }

        if (commandLine.hasOption("h")) {
            config.setHost(commandLine.getOptionValue("h"));
            settings.add("host");
        }

        if (commandLine.hasOption("i")) {
            config.setId(commandLine.getOptionValue("i"));
            settings.add("id");
        }

        config.validateConsistency(settings);
        return config;
    }

    static TikaServerConfig load(Path tikaConfigPath, CommandLine commandLine, Set<String> settings) throws IOException, TikaException {
        TikaServerConfig tikaServerConfig = TikaLoader.load(tikaConfigPath).getConfig().deserialize("server", TikaServerConfig.class);
        if (tikaServerConfig == null) {
            throw new TikaConfigException("Couldn't find 'server' element");
        }
        tikaServerConfig.setConfigPath(tikaConfigPath.toAbsolutePath().toString());
        return tikaServerConfig;
    }


    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Whether the pipes/fetch endpoints ({@code pipes}, {@code async}) may be
     * enabled. Off by default; selecting one of those endpoints without this set
     * causes the server to refuse to start.
     */
    public boolean isAllowPipes() {
        return allowPipes;
    }

    public void setAllowPipes(boolean allowPipes) {
        this.allowPipes = allowPipes;
    }

    /**
     * Whether callers may supply per-request parser configuration (the
     * {@code /config} endpoints and the multipart {@code config} part). Off by
     * default; when off, such requests are rejected with 403.
     */
    public boolean isAllowPerRequestConfig() {
        return allowPerRequestConfig;
    }

    public void setAllowPerRequestConfig(boolean allowPerRequestConfig) {
        this.allowPerRequestConfig = allowPerRequestConfig;
    }

    private void validateConsistency(Set<String> settings) throws TikaConfigException {
        if (host == null) {
            throw new TikaConfigException("Must specify 'host'");
        }
        if (!allowPipes) {
            List<String> requirePipes = new ArrayList<>();
            for (String endpoint : endpoints) {
                if (ENDPOINTS_REQUIRING_PIPES.contains(endpoint)
                        && !requirePipes.contains(endpoint)) {
                    requirePipes.add(endpoint);
                }
            }
            if (!requirePipes.isEmpty()) {
                throw new TikaConfigException(
                        "The following selected endpoint(s) require the pipes machinery to be " +
                        "enabled: " + requirePipes + ". Set 'allowPipes' to true " +
                        "in the 'server' section of your tika-config and confirm you understand " +
                        "the security implications (see the tika-server documentation). These " +
                        "endpoints expose process-isolated fetching and parsing, which can read " +
                        "files and reach network resources.");
            }
        }
        tlsConfig.checkInitialization();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        if ("*".equals(host)) {
            host = "0.0.0.0";
        }
        this.host = host;
    }

    /**
     * Severity at which each request URI is logged. Empty (the default) disables
     * request logging entirely; this does not change the log level of anything else.
     */
    public String getRequestLogLevel() {
        return requestLogLevel;
    }

    public void setRequestLogLevel(String level) throws TikaConfigException {
        if (level.equals("debug") || level.equals("info")) {
            this.requestLogLevel = level;
        } else {
            throw new TikaConfigException("requestLogLevel must be one of: 'debug' or 'info'");
        }
    }

    /**
     * @return the origin url for cors, can be "*"
     */
    public String getCors() {
        return cors;
    }

    public void setCors(String cors) {
        this.cors = cors;
    }

    public boolean hasConfigFile() {
        return configPath != null;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Path getConfigPath() {
        return configPath;
    }

    /** Set from the -c argument at load time; not a user-settable config key. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setConfigPath(String path) {
        this.configPath = Paths.get(path);
    }

    /**
     * Maximum request body in bytes; defaults to {@link #DEFAULT_MAX_REQUEST_SIZE_BYTES}
     * (1 GiB). tika-server spools uploads to disk, so this bounds how much one request can
     * write. A negative value disables the limit (the pre-4.0 behavior); an over-limit
     * request is rejected with 413.
     */
    public long getMaxRequestSizeBytes() {
        return maxRequestSizeBytes;
    }

    public void setMaxRequestSizeBytes(long maxRequestSizeBytes) {
        this.maxRequestSizeBytes = maxRequestSizeBytes;
    }

    /**
     * How long a POST to /async blocks when the queue is full before it is rejected with
     * 429 (Retry-After); defaults to {@link #DEFAULT_MAX_QUEUE_PAUSE_MILLIS}.
     */
    public long getMaxQueuePauseMillis() {
        return maxQueuePauseMillis;
    }

    public void setMaxQueuePauseMillis(long maxQueuePauseMillis) {
        this.maxQueuePauseMillis = maxQueuePauseMillis;
    }

    public TlsConfig getTlsConfig() {
        return tlsConfig;
    }

    public void setTlsConfig(TlsConfig tlsConfig) {
        this.tlsConfig = tlsConfig;
    }

    public ArrayList<String> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(ArrayList<String> endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * Identifier for this server, surfaced in the startup log. Defaults to a random UUID.
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
