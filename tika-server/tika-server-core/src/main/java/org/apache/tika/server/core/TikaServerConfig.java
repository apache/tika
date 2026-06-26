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
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;

public class TikaServerConfig {

    public static final int DEFAULT_PORT = 9998;
    public static final String DEFAULT_HOST = "localhost";
    public static final Set<String> LOG_LEVELS = new HashSet<>(Arrays.asList("debug", "info"));
    /**
     * Number of milliseconds to wait for forked process to startup
     */
    public static final long DEFAULT_FORKED_STARTUP_MILLIS = 120000;
    private static final Logger LOG = LoggerFactory.getLogger(TikaServerConfig.class);
    //used in fork mode -- restart after processing this many files
    private static final long DEFAULT_MAX_FILES = 100000;
    private static final int DEFAULT_DIGEST_MARK_LIMIT = 20 * 1024 * 1024;
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
    private static final List<String> ONLY_IN_FORK_MODE = Arrays.asList(
            new String[]{"maxFiles", "javaPath", "maxRestarts", "numRestarts", "forkedStatusFile", "maxForkedStartupMillis",
                    "tmpFilePrefix"});
    private static Pattern SYS_PROPS = Pattern.compile("\\$\\{sys:([-_0-9A-Za-z]+)\\}");
    /*
TODO: integrate these settings:
 * Number of milliseconds to wait to start forked process.
public static final long DEFAULT_FORKED_PROCESS_STARTUP_MILLIS = 60000;

 * Maximum number of milliseconds to wait to shutdown forked process to allow
 * for current parses to complete.
public static final long DEFAULT_FORKED_PROCESS_SHUTDOWN_MILLIS = 30000;

private long forkedProcessStartupMillis = DEFAULT_FORKED_PROCESS_STARTUP_MILLIS;

private long forkedProcessShutdownMillis = DEFAULT_FORKED_PROCESS_SHUTDOWN_MILLIS;

 */
    private boolean allowPipes = false;
    private boolean allowPerRequestConfig = false;
    private String cors = "";
    private boolean returnStackTrace = false;
    private String idBase = UUID
            .randomUUID()
            .toString();
    private int port = DEFAULT_PORT;
    private String host = DEFAULT_HOST;
    private int digestMarkLimit = DEFAULT_DIGEST_MARK_LIMIT;
    private String digest = "";
    //debug or info only
    private String logLevel = "";
    private Path configPath;
    private ArrayList<String> endpoints = new ArrayList<>();

    private boolean preventStopMethod = false;

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
        Path pluginsConfig = null;

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

    public String getIdBase() {
        return idBase;
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

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String level) throws TikaConfigException {
        if (level.equals("debug") || level.equals("info")) {
            this.logLevel = level;
        } else {
            throw new TikaConfigException("log level must be one of: 'debug' or 'info'");
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

    public Path getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String path) {
        this.configPath = Paths.get(path);
    }

    public int getDigestMarkLimit() {
        return digestMarkLimit;
    }

    public void setDigestMarkLimit(int digestMarkLimit) {
        this.digestMarkLimit = digestMarkLimit;
    }

    /**
     * digest configuration string, e.g. md5 or sha256, alternately w 16 or 32 encoding,
     * e.g. md5:32,sha256:16 would result in two digests per file
     *
     * @return
     */
    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        LOG.info("As of Tika 2.5.0, you can set the digester via the AutoDetectParserConfig in " + "tika-config.xml. We plan to remove this commandline option in 2.8.0");
        this.digest = digest;
    }

    public boolean isReturnStackTrace() {
        return returnStackTrace;
    }

    public void setReturnStackTrace(boolean returnStackTrace) {
        this.returnStackTrace = returnStackTrace;
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

    public String getId() {
        //TODO fix this
        return idBase;
    }

    public void setId(String id) {
        this.idBase = id;
    }

    private void addEndPoints(List<String> endPoints) {
        this.endpoints.addAll(endPoints);
    }

}
