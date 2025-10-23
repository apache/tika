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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.StringUtils;
import org.apache.tika.utils.XMLReaderUtils;

public class TikaServerConfig extends ConfigBase {

    public static final int DEFAULT_PORT = 9998;
    public static final String DEFAULT_HOST = "localhost";
    public static final Set<String> LOG_LEVELS = new HashSet<>(Arrays.asList("debug", "info"));
    /**
     * Number of milliseconds to wait per server task (parse, detect, unpack, translate,
     * etc.) before timing out and shutting down the forked process.
     */
    public static final long DEFAULT_TASK_TIMEOUT_MILLIS = 300000;
    /**
     * Clients may not set a timeout less than this amount.  This hinders
     * malicious clients from setting the timeout to a very low value
     * and DoS the server by forcing timeout restarts.  Making tika-server
     * available to untrusted clients is dangerous.
     */
    public static final long DEFAULT_MINIMUM_TIMEOUT_MILLIS = 30000;
    /**
     * How often to check to see that the task hasn't timed out
     */
    public static final long DEFAULT_TASK_PULSE_MILLIS = 10000;
    /**
     * Number of milliseconds to wait for forked process to startup
     */
    public static final long DEFAULT_FORKED_STARTUP_MILLIS = 120000;
    private static final Logger LOG = LoggerFactory.getLogger(TikaServerConfig.class);
    //used in fork mode -- restart after processing this many files
    private static final long DEFAULT_MAX_FILES = 100000;
    private static final int DEFAULT_DIGEST_MARK_LIMIT = 20 * 1024 * 1024;
    private static final String UNSECURE_WARNING =
            "WARNING: You have chosen to run tika-server with unsecure features enabled.\n" + "Whoever has access to your service now has the same read permissions\n" +
                    "as you've given your fetchers and the same write permissions " + "as your emitters.\n" + "Users could request and receive a sensitive file from your\n" +
                    "drive or a webpage from your intranet and/or send malicious content to\n" + " your emitter endpoints.  See CVE-2015-3271.\n" +
                    "Please make sure you know what you are doing.";
    private static final List<String> ONLY_IN_FORK_MODE = Arrays.asList(
            new String[]{"taskTimeoutMillis", "taskPulseMillis", "maxFiles", "javaPath", "maxRestarts", "numRestarts", "forkedStatusFile", "maxForkedStartupMillis",
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
    private long taskTimeoutMillis = DEFAULT_TASK_TIMEOUT_MILLIS;
    private long minimumTimeoutMillis = DEFAULT_MINIMUM_TIMEOUT_MILLIS;
    private long taskPulseMillis = DEFAULT_TASK_PULSE_MILLIS;
    private boolean enableUnsecureFeatures = false;
    private String cors = "";
    private boolean returnStackTrace = false;
    private Set<String> supportedFetchers = new HashSet<>();
    private Set<String> supportedEmitters = new HashSet<>();
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
    private Path pipesConfigPath;
    private List<String> endpoints = new ArrayList<>();

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
        Path pipesConfig = null;
        if (commandLine.hasOption('a')) {
            pipesConfig = Paths.get(commandLine.getOptionValue('a'));
        }
        if (commandLine.hasOption("c")) {
            config = load(Paths.get(commandLine.getOptionValue("c")), pipesConfig, commandLine, settings);
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

    static TikaServerConfig load(Path p, Path pipesConfigPath, CommandLine commandLine, Set<String> settings) throws IOException, TikaException {
        try (InputStream is = Files.newInputStream(p)) {
            TikaServerConfig config = TikaServerConfig.load(is, pipesConfigPath, commandLine, settings);
            if (config.getConfigPath() == null) {
                config.setConfigPath(p
                        .toAbsolutePath()
                        .toString());
            }
            config.setPipesConfigPath(pipesConfigPath);
            loadSupportedFetchersEmitters(config);
            return config;
        }
    }

    private static TikaServerConfig load(InputStream is, Path pipesConfigPath, CommandLine commandLine, Set<String> settings) throws IOException, TikaException {
        TikaServerConfig tikaServerConfig = new TikaServerConfig();
        Set<String> configSettings = tikaServerConfig.configure("server", is);
        settings.addAll(configSettings);
        tikaServerConfig.setPipesConfigPath(pipesConfigPath);
        return tikaServerConfig;
    }

    private static void loadSupportedFetchersEmitters(TikaServerConfig tikaServerConfig) throws IOException, TikaConfigException {
        //this is an abomination... clean up this double read
        try (InputStream is = Files.newInputStream(tikaServerConfig.getConfigPath())) {
            Node properties = null;
            try {
                properties = XMLReaderUtils
                        .buildDOM(is)
                        .getDocumentElement();
            } catch (SAXException e) {
                throw new IOException(e);
            } catch (TikaException e) {
                throw new TikaConfigException("problem loading xml to dom", e);
            }
            if (!properties
                    .getLocalName()
                    .equals("properties")) {
                throw new TikaConfigException("expect properties as root node");
            }
            NodeList children = properties.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if ("fetchers".equals(child.getLocalName())) {
                    loadSupported(child, "fetcher", tikaServerConfig.supportedFetchers);
                } else if ("emitters".equals(child.getLocalName())) {
                    loadSupported(child, "emitter", tikaServerConfig.supportedEmitters);
                }
            }
        }
    }

    private static void loadSupported(Node compound, String itemName, Set<String> supported) {
        NodeList children = compound.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (itemName.equals(child.getLocalName())) {
                String name = getName(child);
                if (name != null) {
                    supported.add(name);
                }
            }
        }
    }

    private static String getName(Node fetcherOrEmitter) {
        NodeList children = fetcherOrEmitter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if ("params".equals(child.getLocalName())) {
                NodeList params = child.getChildNodes();
                for (int j = 0; j < params.getLength(); j++) {
                    Node param = params.item(j);
                    if ("name".equals(param.getLocalName())) {
                        return param.getTextContent();
                    }
                }
            } else if ("name".equals(child.getLocalName())) {
                return child.getTextContent();
            }
        }
        return null;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * How long to wait for a task before shutting down the forked server process
     * and restarting it.
     *
     * @return
     */
    public long getTaskTimeoutMillis() {
        return taskTimeoutMillis;
    }

    /**
     * @param taskTimeoutMillis number of milliseconds to allow per task
     *                          (parse, detection, unzipping, etc.)
     */
    public void setTaskTimeoutMillis(long taskTimeoutMillis) {
        this.taskTimeoutMillis = taskTimeoutMillis;
    }

    /**
     * How often to check to see that a task has timed out
     *
     * @return
     */
    public long getTaskPulseMillis() {
        return taskPulseMillis;
    }

    public void setTaskPulseMillis(long taskPulseMillis) {
        this.taskPulseMillis = taskPulseMillis;
    }

    public long getMinimumTimeoutMillis() {
        return minimumTimeoutMillis;
    }

    public void setMinimumTimeoutMillis(long minimumTimeoutMillis) {
        this.minimumTimeoutMillis = minimumTimeoutMillis;
    }

    public String getIdBase() {
        return idBase;
    }

    public boolean isEnableUnsecureFeatures() {
        return enableUnsecureFeatures;
    }

    public void setEnableUnsecureFeatures(boolean enableUnsecureFeatures) {
        this.enableUnsecureFeatures = enableUnsecureFeatures;
    }

    private void validateConsistency(Set<String> settings) throws TikaConfigException {
        if (host == null) {
            throw new TikaConfigException("Must specify 'host'");
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

    public void setPipesConfigPath(Path path) {
        this.pipesConfigPath = path;
    }

    public Optional<Path> getPipesConfigPath() {
        return Optional.ofNullable(pipesConfigPath);
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

    public List<String> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<String> endpoints) {
        this.endpoints = new ArrayList<>(endpoints);
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

    public Set<String> getSupportedFetchers() {
        return supportedFetchers;
    }

    public Set<String> getSupportedEmitters() {
        return supportedEmitters;
    }

}
