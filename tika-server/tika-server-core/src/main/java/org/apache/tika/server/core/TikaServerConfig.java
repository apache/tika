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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.ProcessUtils;
import org.apache.tika.utils.StringUtils;

public class TikaServerConfig extends ConfigBase {

    public static final int DEFAULT_PORT = 9998;
    public static final String DEFAULT_HOST = "localhost";
    public static final Set<String> LOG_LEVELS = new HashSet<>(Arrays.asList("debug", "info"));
    /**
     * Number of milliseconds to wait per server task (parse, detect, unpack, translate,
     * etc.) before timing out and shutting down the forked process.
     */
    public static final long DEFAULT_TASK_TIMEOUT_MILLIS = 120000;
    /**
     * How often to check to see that the task hasn't timed out
     */
    public static final long DEFAULT_TASK_PULSE_MILLIS = 10000;
    /**
     * Number of milliseconds to wait for forked process to startup
     */
    public static final long DEFAULT_FORKED_STARTUP_MILLIS = 120000;
    //used in fork mode -- restart after processing this many files
    private static final long DEFAULT_MAX_FILES = 100000;
    private static final int DEFAULT_DIGEST_MARK_LIMIT = 20 * 1024 * 1024;
    private static final String UNSECURE_WARNING =
            "WARNING: You have chosen to run tika-server with unsecure features enabled.\n" +
                    "Whoever has access to your service now has the same read permissions\n" +
                    "as you've given your fetchers and the same write permissions " +
                    "as your emitters.\n" +
                    "Users could request and receive a sensitive file from your\n" +
                    "drive or a webpage from your intranet and/or send malicious content to\n" +
                    " your emitter endpoints.  See CVE-2015-3271.\n" +
                    "Please make sure you know what you are doing.";
    private static final List<String> ONLY_IN_FORK_MODE = Arrays.asList(
            new String[]{"taskTimeoutMillis", "taskPulseMillis", "pingTimeoutMillis",
                    "pingPulseMillis", "maxFiles", "javaHome", "maxRestarts", "numRestarts",
                    "forkedStatusFile", "maxForkedStartupMillis", "tmpFilePrefix"});

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
    private int maxRestarts = -1;
    private long maxFiles = 100000;
    private long taskTimeoutMillis = DEFAULT_TASK_TIMEOUT_MILLIS;
    private long taskPulseMillis = DEFAULT_TASK_PULSE_MILLIS;
    private long maxforkedStartupMillis = DEFAULT_FORKED_STARTUP_MILLIS;
    private boolean enableUnsecureFeatures = false;
    private String cors = "";
    private boolean returnStackTrace = false;
    private boolean noFork = false;
    private String tempFilePrefix = "apache-tika-server-forked-tmp-"; //can be set for debugging
    private List<String> forkedJvmArgs = new ArrayList<>();
    private String idBase = UUID.randomUUID().toString();
    private String port = Integer.toString(DEFAULT_PORT);
    private String host = DEFAULT_HOST;
    private int digestMarkLimit = DEFAULT_DIGEST_MARK_LIMIT;
    private String digest = "";
    //debug or info only
    private String logLevel = "";
    private Path configPath;
    private List<String> endpoints = new ArrayList<>();
    //these should only be set in the forked process
    //and they are automatically set by the forking process
    private String forkedStatusFile;
    private int numRestarts = 0;

    /**
     * Config with only the defaults
     */
    public static TikaServerConfig load() {
        return new TikaServerConfig();
    }

    public static TikaServerConfig load(CommandLine commandLine)
            throws IOException, TikaException {

        TikaServerConfig config = null;
        Set<String> settings = new HashSet<>();
        if (commandLine.hasOption("c")) {
            config = load(Paths.get(commandLine.getOptionValue("c")), commandLine, settings);
            config.setConfigPath(commandLine.getOptionValue("c"));
        } else {
            config = new TikaServerConfig();
        }

        //port, host, nofork and id can be overwritten on the commandline at runtime
        if (commandLine.hasOption("p")) {
            config.setPort(commandLine.getOptionValue("p"));
            settings.add("port");
        }

        if (commandLine.hasOption("h")) {
            config.setHost(commandLine.getOptionValue("h"));
            settings.add("host");
        }

        if (commandLine.hasOption("noFork")) {
            config.setNoFork(true);
            settings.add("noFork");
        }

        if (commandLine.hasOption("i")) {
            config.setId(commandLine.getOptionValue("i"));
            settings.add("id");
        }

        //this should only be set by the parent process
        if (commandLine.hasOption("numRestarts")) {
            config.setNumRestarts(Integer.parseInt(commandLine.getOptionValue("numRestarts")));
            settings.add("numRestarts");
        }

        if (commandLine.hasOption("forkedStatusFile")) {
            config.setForkedStatusFile(commandLine.getOptionValue("forkedStatusFile"));
            settings.add("forkedStatusFile");
        }
        config.validateConsistency(settings);
        return config;
    }

    static TikaServerConfig load(Path p, CommandLine commandLine, Set<String> settings) throws IOException,
            TikaException {
        try (InputStream is = Files.newInputStream(p)) {
            return TikaServerConfig.load(is, commandLine, settings);
        }
    }

    static TikaServerConfig load(InputStream is, CommandLine commandLine, Set<String> settings)
            throws IOException, TikaException {
        TikaServerConfig tikaServerConfig = new TikaServerConfig();
        Set<String> configSettings = tikaServerConfig.configure("server", is);
        settings.addAll(configSettings);
        //override a few things in config file
        if (commandLine.hasOption("noFork")) {
            tikaServerConfig.setNoFork(true);
        }
        return tikaServerConfig;
    }

    public boolean isNoFork() {
        return noFork;
    }

    private void setNoFork(boolean noFork) {
        this.noFork = noFork;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
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

    public int getMaxRestarts() {
        return maxRestarts;
    }

    public void setMaxRestarts(int maxRestarts) {
        this.maxRestarts = maxRestarts;
    }

    /**
     * Maximum time in millis to allow for the forked process to startup
     * or restart
     *
     * @return
     */
    public long getMaxForkedStartupMillis() {
        return maxforkedStartupMillis;
    }

    public void setMaxForkedStartupMillis(long maxForkedStartupMillis) {
        this.maxforkedStartupMillis = maxForkedStartupMillis;
    }

    public List<String> getForkedProcessArgs(String portString, String id) {
        int[] ports = getPorts(portString);
        if (ports.length > 1 || ports.length == 0) {
            throw new IllegalArgumentException(
                    "must specify one and only one port here:" + portString);
        }
        int port = ports[0];
        return getForkedProcessArgs(port, id);
    }

    public List<String> getForkedProcessArgs(int port, String id) {
        //these are the arguments for the forked process
        List<String> args = new ArrayList<>();
        args.add("-h");
        args.add(getHost());
        args.add("-p");
        args.add(Integer.toString(port));
        args.add("-i");
        args.add(id);
        if (hasConfigFile()) {
            args.add("-c");
            args.add(ProcessUtils.escapeCommandLine(configPath.toAbsolutePath().toString()));
        }
        return args;
    }

    public String getIdBase() {
        return idBase;
    }

    /**
     * full path to the java executable
     *
     * @return
     */
    public String getJavaPath() {
        return "java";
    }

    public List<String> getForkedJvmArgs() {
        //defensively copy
        return new ArrayList<>(forkedJvmArgs);
    }

    public void setForkedJvmArgs(List<String> forkedJvmArgs) {
        this.forkedJvmArgs = new ArrayList<>(forkedJvmArgs);
    }

    public String getTempFilePrefix() {
        return tempFilePrefix;
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

        if (!StringUtils.isBlank(port)) {
            setPort(port);
        }

        if (isNoFork()) {
            for (String onlyFork : ONLY_IN_FORK_MODE) {
                if (settings.contains(onlyFork)) {
                    throw new TikaConfigException(
                            "Can't set param=" + onlyFork + "if you've selected noFork");
                }
            }
        }
        //add headless if not already configured
        boolean foundHeadlessOption = forkedJvmArgs.stream().anyMatch(arg -> arg.contains("java.awt.headless"));
        //if user has already specified headless...don't modify
        if (!foundHeadlessOption) {
            forkedJvmArgs.add("-Djava.awt.headless=true");
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
        this.digest = digest;
    }

    /**
     * maximum number of files before the forked server restarts.
     * This is useful for avoiding any slow-building memory leaks/bloat.
     *
     * @return
     */
    public long getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(long maxFiles) {
        this.maxFiles = maxFiles;
    }

    public boolean isReturnStackTrace() {
        return returnStackTrace;
    }

    public void setReturnStackTrace(boolean returnStackTrace) {
        this.returnStackTrace = returnStackTrace;
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

    private void setId(String id) {
        this.idBase = id;
    }

    private void addEndPoints(List<String> endPoints) {
        this.endpoints.addAll(endPoints);
    }

    private void addJVMArgs(List<String> args) {
        this.forkedJvmArgs.addAll(args);
    }

    public int getNumRestarts() {
        return numRestarts;
    }

    /******
     * these should only be used in the commandline for a forked process
     ******/


    private void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    public String getForkedStatusFile() {
        return forkedStatusFile;
    }

    private void setForkedStatusFile(String forkedStatusFile) {
        this.forkedStatusFile = forkedStatusFile;
    }

    public void setMaxforkedStartupMillis(long maxforkedStartupMillis) {
        this.maxforkedStartupMillis = maxforkedStartupMillis;
    }

    public int[] getPorts() {
        return getPorts(port);
    }

    private int[] getPorts(String portString) {
        //throws NumberFormatException
        Matcher rangeMatcher = Pattern.compile("^(\\d+)-(\\d+)\\Z").matcher("");
        String[] commaDelimited = portString.split(",");
        List<Integer> indivPorts = new ArrayList<>();
        for (String val : commaDelimited) {
            rangeMatcher.reset(val);
            if (rangeMatcher.find()) {
                int min = Math.min(Integer.parseInt(rangeMatcher.group(1)),
                        Integer.parseInt(rangeMatcher.group(2)));
                int max = Math.max(Integer.parseInt(rangeMatcher.group(1)),
                        Integer.parseInt(rangeMatcher.group(2)));
                for (int i = min; i <= max; i++) {
                    indivPorts.add(i);
                }
            } else {
                indivPorts.add(Integer.parseInt(val));
            }
        }
        return indivPorts.stream().mapToInt(Integer::intValue).toArray();
    }

}
