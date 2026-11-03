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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
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
     * Prefix of the environment variables that set a field of the {@code server} block:
     * {@code TIKA_SERVER_SETTING_MAX_REQUEST_SIZE_BYTES=4294967296} is {@code "maxRequestSizeBytes":
     * 4294967296}, read at startup like {@code --serverSetting maxRequestSizeBytes=4294967296}.
     * Not {@code TIKA_SERVER_}: Kubernetes gives every pod {@code TIKA_SERVER_PORT},
     * {@code TIKA_SERVER_SERVICE_HOST} and more for a Service named {@code tika-server}, and
     * those must neither set a field nor fail the start.
     */
    public static final String SETTINGS_ENV_PREFIX = "TIKA_SERVER_SETTING_";

    /** The {@code --serverSetting <field>=<value>} option, repeatable. */
    static final String SETTING_OPTION = "serverSetting";

    private static final ObjectMapper OBJECT_MAPPER = TikaObjectMapperFactory.getMapper();

    /** A value for a {@code server} field and what set it, for the log and the error. */
    private record Setting(String value, String source) {
    }
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

    /** Default time allowed to receive a request body: 5 minutes, so 1 GiB needs about 29 Mbit/s. */
    public static final long DEFAULT_MAX_REQUEST_READ_MILLIS = 5L * 60L * 1000L;

    /** Default time allowed to write a response body: 5 minutes. */
    public static final long DEFAULT_MAX_RESPONSE_WRITE_MILLIS = 5L * 60L * 1000L;

    /** Default connector idle timeout: 2 minutes. Ends clients that stop sending or reading entirely. */
    public static final int DEFAULT_IDLE_TIMEOUT_MILLIS = 120_000;

    /** Default cap on request-body bytes in flight: 0, meaning maxRequestSizeBytes x 2 x pipes clients. */
    public static final long DEFAULT_MAX_IN_FLIGHT_REQUEST_BYTES = 0;

    /** How long a full /async queue blocks a POST before it is rejected with 429. */
    public static final long DEFAULT_MAX_QUEUE_PAUSE_MILLIS = 60000;

    private boolean allowPipes = false;
    private boolean allowPerRequestConfig = false;
    private boolean allowHttp2 = false;
    private String cors = "";
    private long maxQueuePauseMillis = DEFAULT_MAX_QUEUE_PAUSE_MILLIS;
    private long maxRequestSizeBytes = DEFAULT_MAX_REQUEST_SIZE_BYTES;
    private long maxRequestReadMillis = DEFAULT_MAX_REQUEST_READ_MILLIS;
    private long maxResponseWriteMillis = DEFAULT_MAX_RESPONSE_WRITE_MILLIS;
    private int idleTimeoutMillis = DEFAULT_IDLE_TIMEOUT_MILLIS;
    private long maxInFlightRequestBytes = DEFAULT_MAX_IN_FLIGHT_REQUEST_BYTES;
    private String id = UUID
            .randomUUID()
            .toString();
    private int port = DEFAULT_PORT;
    private String host = DEFAULT_HOST;
    private String requestLogLevel = "";
    private Path configPath;
    private List<String> presets = new ArrayList<>();
    private ArrayList<String> endpoints = new ArrayList<>();

    // null = plain HTTP; a tlsConfig block, however sparse, means TLS and must be complete
    private TlsConfig tlsConfig = null;
    private Integer metricsPort;

    /**
     * Config with only the defaults
     */
    public static TikaServerConfig load() {
        return new TikaServerConfig();
    }

    public static TikaServerConfig load(CommandLine commandLine) throws IOException, TikaException {
        return load(commandLine, System.getenv(PRESETS_ENV), System.getenv());
    }

    static TikaServerConfig load(CommandLine commandLine, String presetsEnv)
            throws IOException, TikaException {
        return load(commandLine, presetsEnv, Map.of());
    }

    static TikaServerConfig load(CommandLine commandLine, String presetsEnv,
                                 Map<String, String> environment)
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

        Map<String, Setting> serverSettings = serverSettings(commandLine, environment);

        TikaServerConfig config = null;
        Set<String> settings = new HashSet<>();

        if (commandLine.hasOption("c")) {
            config = load(Paths.get(commandLine.getOptionValue("c")), commandLine, settings,
                    presets, serverSettings);
        } else {
            config = new TikaServerConfig();
            presets.forEach((name, source) -> LOG.info("preset '{}' activated by {}", name, source));
            applySettings(config, serverSettings, null, null);
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
        return load(tikaConfigPath, commandLine, settings, Map.of(), Map.of());
    }

    private static TikaServerConfig load(Path tikaConfigPath, CommandLine commandLine,
                                         Set<String> settings, Map<String, String> presets,
                                         Map<String, Setting> serverSettings)
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
            // no "server" block: same as {}, so a tika-app config works as is
            tikaServerConfig = new TikaServerConfig();
        }
        applySettings(tikaServerConfig, serverSettings,
                tikaJsonConfig.getRootNode().get("server"), tikaConfigPath);
        PipesConfig pipesConfig = tikaJsonConfig.deserialize("pipes", PipesConfig.class);
        if (pipesConfig != null) {
            pipesConfig.refuseTrustedRequests("tika-server");
        }
        tikaServerConfig.setConfigPath(tikaConfigPath.toAbsolutePath().toString());
        return tikaServerConfig;
    }

    /**
     * The {@code server} fields given as {@code --serverSetting <field>=<value>} or as
     * {@value #SETTINGS_ENV_PREFIX}{@code <FIELD>} in the environment, the option winning over
     * the variable for the same field. {@code TIKA_SERVER_SETTING_MAX_REQUEST_SIZE_BYTES} names
     * {@code maxRequestSizeBytes}; the variable's name is upper snake case of the field's.
     */
    private static Map<String, Setting> serverSettings(CommandLine commandLine,
                                                       Map<String, String> environment)
            throws TikaConfigException {
        Map<String, Setting> serverSettings = new LinkedHashMap<>();
        if (commandLine.hasOption(SETTING_OPTION)) {
            for (String option : commandLine.getOptionValues(SETTING_OPTION)) {
                int eq = option.indexOf('=');
                if (eq < 1) {
                    throw new TikaConfigException("--" + SETTING_OPTION + " takes <field>=<value>, got '"
                            + option + "'");
                }
                serverSettings.putIfAbsent(option.substring(0, eq).trim(),
                        new Setting(option.substring(eq + 1), "--" + SETTING_OPTION));
            }
        }
        for (Map.Entry<String, String> e : environment.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(SETTINGS_ENV_PREFIX) || key.length() == SETTINGS_ENV_PREFIX.length()) {
                continue;
            }
            serverSettings.putIfAbsent(fieldName(key.substring(SETTINGS_ENV_PREFIX.length())),
                    new Setting(e.getValue(), key));
        }
        return serverSettings;
    }

    /** {@code MAX_REQUEST_SIZE_BYTES} to {@code maxRequestSizeBytes}. */
    static String fieldName(String upperSnake) {
        StringBuilder field = new StringBuilder();
        for (String part : upperSnake.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            field.append(field.isEmpty() ? part
                    : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return field.toString();
    }

    /**
     * Sets the fields through the same Jackson binding the config file goes through, so a
     * value is checked by the field's setter and an unknown field fails startup, as it does
     * in the file. {@code fileServer} is the file's {@code server} block, for the log.
     */
    private static void applySettings(TikaServerConfig config, Map<String, Setting> serverSettings,
                                      JsonNode fileServer, Path tikaConfigPath)
            throws TikaConfigException {
        if (serverSettings.isEmpty()) {
            return;
        }
        if (serverSettings.containsKey("configPath")) {
            throw new TikaConfigException(serverSettings.get("configPath").source()
                    + ": configPath is the -c option, not a server setting");
        }
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        serverSettings.forEach((field, setting) -> {
            JsonNode inFile = fileServer == null ? null : fileServer.get(field);
            if (inFile == null) {
                LOG.info("server setting '{}' = {} set by {}", field, setting.value(),
                        setting.source());
            } else {
                LOG.warn("server setting '{}' = {} set by {}: overrides \"{}\": {} in {}", field,
                        setting.value(), setting.source(), field, inFile, tikaConfigPath);
            }
            node.set(field, valueNode(setting.value()));
        });
        try {
            OBJECT_MAPPER.readerForUpdating(config).readValue(node);
        } catch (UnrecognizedPropertyException e) {
            Setting setting = serverSettings.get(e.getPropertyName());
            List<String> known = new ArrayList<>();
            for (Object id : e.getKnownPropertyIds()) {
                if (!"configPath".equals(id)) {
                    known.add(id.toString());
                }
            }
            throw new TikaConfigException(setting.source() + ": the server block has no field '"
                    + e.getPropertyName() + "'; it has " + known, e);
        } catch (JsonMappingException e) {
            String field = e.getPath().isEmpty() ? null : e.getPath().get(0).getFieldName();
            Setting setting = field == null ? null : serverSettings.get(field);
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new TikaConfigException((setting == null ? "server setting"
                    : setting.source() + ": '" + setting.value() + "' for " + field)
                    + " is not valid: " + cause.getMessage(), e);
        } catch (IOException e) {
            throw new TikaConfigException("could not apply the server settings", e);
        }
    }

    /** The JSON a config file would hold for the value: a number, a boolean or a string. */
    private static JsonNode valueNode(String value) {
        String trimmed = value.trim();
        if ("true".equals(trimmed) || "false".equals(trimmed)) {
            return OBJECT_MAPPER.getNodeFactory().booleanNode(Boolean.parseBoolean(trimmed));
        }
        if (trimmed.matches("-?\\d{1,18}")) {
            return OBJECT_MAPPER.getNodeFactory().numberNode(Long.parseLong(trimmed));
        }
        return OBJECT_MAPPER.getNodeFactory().textNode(value);
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

    /**
     * Whether to accept cleartext HTTP/2 (h2c) as well as HTTP/1.1; off by default. HTTP/2
     * requests get no {@code maxResponseWriteMillis}: its abort closes the connection, which
     * would end every request multiplexed on it. HTTP/2 over TLS is not offered either way
     * (tika-server ships no ALPN server provider).
     */
    public boolean isAllowHttp2() {
        return allowHttp2;
    }

    public void setAllowHttp2(boolean allowHttp2) {
        this.allowHttp2 = allowHttp2;
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
        if (tlsConfig != null) {
            tlsConfig.checkInitialization();
        }
        if (maxInFlightRequestBytes > 0 && maxRequestSizeBytes >= 0
                && maxInFlightRequestBytes < maxRequestSizeBytes) {
            throw new TikaConfigException("maxInFlightRequestBytes (" + maxInFlightRequestBytes
                    + ") must be at least maxRequestSizeBytes (" + maxRequestSizeBytes + ")");
        }
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
     * How long, from the start of the request, reading its body may take; defaults to
     * {@link #DEFAULT_MAX_REQUEST_READ_MILLIS} (5 minutes). A request still sending after
     * that is rejected with 408. A negative value disables the deadline; zero is rejected.
     */
    public long getMaxRequestReadMillis() {
        return maxRequestReadMillis;
    }

    public void setMaxRequestReadMillis(long maxRequestReadMillis) throws TikaConfigException {
        this.maxRequestReadMillis = nonZero("maxRequestReadMillis", maxRequestReadMillis);
    }

    /**
     * How long, from when its status line is sent, writing a response body may take; defaults to
     * {@link #DEFAULT_MAX_RESPONSE_WRITE_MILLIS} (5 minutes). A response still being written
     * after that is cut off. A negative value disables the deadline; zero is rejected.
     */
    public long getMaxResponseWriteMillis() {
        return maxResponseWriteMillis;
    }

    public void setMaxResponseWriteMillis(long maxResponseWriteMillis) throws TikaConfigException {
        this.maxResponseWriteMillis = nonZero("maxResponseWriteMillis", maxResponseWriteMillis);
    }

    // zero would fail every transfer, not disable the deadline
    private static long nonZero(String name, long millis) throws TikaConfigException {
        if (millis == 0) {
            throw new TikaConfigException(name + " must be positive, or negative to disable it");
        }
        return millis;
    }

    /**
     * How long a connection may go without any bytes moving before it is closed; defaults to
     * {@link #DEFAULT_IDLE_TIMEOUT_MILLIS} (2 minutes). Jetty applies it only to a read or
     * write waiting on the wire, so a request waiting on its parse is never ended by it.
     * Must be positive: Jetty treats zero as "use its own default", not "never".
     */
    public int getIdleTimeoutMillis() {
        return idleTimeoutMillis;
    }

    public void setIdleTimeoutMillis(int idleTimeoutMillis) throws TikaConfigException {
        if (idleTimeoutMillis <= 0) {
            throw new TikaConfigException("idleTimeoutMillis must be positive: " + idleTimeoutMillis);
        }
        this.idleTimeoutMillis = idleTimeoutMillis;
    }

    /**
     * How many request-body bytes all requests together may hold at once, counted as they
     * are read and freed when each response completes; a request that would go over gets 429.
     * This bounds the temp disk and memory request bodies take, without letting a client that
     * sends slowly hold capacity it has not used. 0 (the default) means
     * {@code maxRequestSizeBytes} x 2 x the pipes clients; negative disables it; a positive
     * value below {@code maxRequestSizeBytes} is rejected, as a request of that size could
     * never succeed.
     */
    public long getMaxInFlightRequestBytes() {
        return maxInFlightRequestBytes;
    }

    public void setMaxInFlightRequestBytes(long maxInFlightRequestBytes) {
        this.maxInFlightRequestBytes = maxInFlightRequestBytes;
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

    /** Null when the config has no {@code tlsConfig} block, i.e. plain HTTP. */
    public TlsConfig getTlsConfig() {
        return tlsConfig;
    }

    public boolean tlsEnabled() {
        return tlsConfig != null;
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
