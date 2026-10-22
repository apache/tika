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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.config.ConfigMerger;
import org.apache.tika.pipes.core.config.ConfigOverrides;

public class TikaServerConfig {

    public static final int DEFAULT_PORT = 9998;
    public static final String DEFAULT_HOST = "localhost";
    private static final Logger LOG = LoggerFactory.getLogger(TikaServerConfig.class);

    /** Comma-separated catalog presets to activate, read at startup like {@code --preset}. */
    public static final String PRESETS_ENV = "TIKA_PRESETS";
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
    private List<String> presets = new ArrayList<>();
    private ArrayList<String> endpoints = new ArrayList<>();

    private TlsConfig tlsConfig = new TlsConfig();
    private Integer metricsPort;

    /**
     * Config with only the defaults
     */
    public static TikaServerConfig load() {
        return new TikaServerConfig();
    }

    public static TikaServerConfig load(CommandLine commandLine) throws IOException, TikaException {
        return load(commandLine, System.getenv(PRESETS_ENV));
    }

    static TikaServerConfig load(CommandLine commandLine, String presetsEnv)
            throws IOException, TikaException {

        Map<String, String> presets = new LinkedHashMap<>();
        if (commandLine.hasOption("preset")) {
            for (String name : commandLine.getOptionValues("preset")) {
                presets.putIfAbsent(name.trim(), "--preset");
            }
        }
        if (presetsEnv != null) {
            for (String name : presetsEnv.split(",")) {
                if (!name.isBlank()) {
                    presets.putIfAbsent(name.trim(), PRESETS_ENV);
                }
            }
        }

        TikaServerConfig config = null;
        Set<String> settings = new HashSet<>();

        if (commandLine.hasOption("c")) {
            config = load(Paths.get(commandLine.getOptionValue("c")), commandLine, settings,
                    presets);
        } else {
            config = new TikaServerConfig();
            presets.forEach((name, source) -> LOG.info("preset '{}' activated by {}", name, source));
        }
        config.presets = new ArrayList<>(presets.keySet());

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

        if (commandLine.hasOption("metricsPort")) {
            String value = commandLine.getOptionValue("metricsPort");
            try {
                config.setMetricsPort(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                throw new TikaConfigException("--metricsPort must be an integer, got '" + value + "'");
            }
            settings.add("metricsPort");
        }

        config.validateConsistency(settings);
        return config;
    }

    static TikaServerConfig load(Path tikaConfigPath, CommandLine commandLine, Set<String> settings) throws IOException, TikaException {
        return load(tikaConfigPath, commandLine, settings, Map.of());
    }

    private static TikaServerConfig load(Path tikaConfigPath, CommandLine commandLine,
                                         Set<String> settings, Map<String, String> presets)
            throws IOException, TikaException {
        TikaJsonConfig tikaJsonConfig = TikaLoader.load(tikaConfigPath).getConfig();
        JsonNode filePresets = tikaJsonConfig.getRootNode().get("presets");
        presets.forEach((name, source) -> {
            JsonNode entry = filePresets == null ? null : filePresets.get(name);
            if (entry == null) {
                LOG.info("preset '{}' activated by {}", name, source);
            } else if (entry.isObject()) {
                LOG.warn("preset '{}' activated by {}: the catalog preset replaces the inline "
                        + "definition in {}", name, source, tikaConfigPath);
            } else {
                LOG.warn("preset '{}' activated by {}: overrides \"{}\": {} in {}", name, source,
                        name, entry, tikaConfigPath);
            }
        });
        TikaServerConfig tikaServerConfig = tikaJsonConfig.deserialize("server", TikaServerConfig.class);
        if (tikaServerConfig == null) {
            throw new TikaConfigException("Couldn't find 'server' element");
        }
        PipesConfig pipesConfig = tikaJsonConfig.deserialize("pipes", PipesConfig.class);
        if (pipesConfig != null) {
            pipesConfig.refuseTrustedRequests("tika-server");
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
        if (metricsPort != null) {
            if (metricsPort < 0 || metricsPort > 65535) {
                throw new TikaConfigException("metricsPort must be 0-65535, got " + metricsPort);
            }
            if (metricsPort != 0 && metricsPort == port) {
                throw new TikaConfigException("metricsPort (" + metricsPort
                        + ") must differ from the server port");
            }
        }
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

    /**
     * Catalog presets named with {@code --preset} or in {@value #PRESETS_ENV}, activated like
     * {@code "presets": {"name": true}} in the config, over the config's own entry for the name.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<String> getPresets() {
        return presets;
    }

    /**
     * The config file with {@link #getPresets()} folded in: the file itself when there are
     * none, else a temp copy, for components that load their configuration from a path.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Path getConfigPathWithPresets() throws IOException {
        if (presets.isEmpty()) {
            return configPath;
        }
        ConfigOverrides.Builder overrides = ConfigOverrides.builder();
        presets.forEach(overrides::addPreset);
        return ConfigMerger.mergeOrCreate(configPath, overrides.build()).configPath();
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

    /** Prometheus scrape port; null means metrics are off. The listener binds to {@link #getHost()}. */
    public Integer getMetricsPort() {
        return metricsPort;
    }

    public void setMetricsPort(Integer metricsPort) {
        this.metricsPort = metricsPort;
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
