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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TikaServerCli {


    //used in fork mode -- restart after processing this many files
    private static final long DEFAULT_MAX_FILES = 100000;


    public static final int DEFAULT_PORT = 9998;
    private static final int DEFAULT_DIGEST_MARK_LIMIT = 20*1024*1024;
    public static final String DEFAULT_HOST = "localhost";
    public static final Set<String> LOG_LEVELS = new HashSet<>(Arrays.asList("debug", "info"));
    private static final Logger LOG = LoggerFactory.getLogger(TikaServerCli.class);

    private static final String UNSECURE_WARNING =
            "WARNING: You have chosen to run tika-server with unsecure features enabled.\n"+
            "Whoever has access to your service now has the same read permissions\n"+
            "as you've given your fetchers and the same write permissions as your emitters.\n" +
            "Users could request and receive a sensitive file from your\n" +
            "drive or a webpage from your intranet and/or send malicious content to\n" +
            " your emitter endpoints.  See CVE-2015-3271.\n"+
            "Please make sure you know what you are doing.";

    private static final List<String> ONLY_IN_FORK_MODE =
            Arrays.asList(new String[] { "taskTimeoutMillis", "taskPulseMillis",
            "pingTimeoutMillis", "pingPulseMillis", "maxFiles", "javaHome", "maxRestarts",
                    "numRestarts",
            "forkedStatusFile", "maxForkedStartupMillis", "tmpFilePrefix"});

    private static Options getOptions() {
        Options options = new Options();
        options.addOption("C", "cors", true, "origin allowed to make CORS requests (default=NONE)\nall allowed if \"all\"");
        options.addOption("h", "host", true, "host name (default = " + DEFAULT_HOST + ", use * for all)");
        options.addOption("p", "port", true,
                "listen port(s) (default = " + DEFAULT_PORT + ").\n" +
                        "Can specify multiple ports with inclusive ranges (e.g. 9990-9999)\n" +
                        "or with comma delimited list (e.g. 9996,9998,9995)");
        options.addOption("c", "config", true, "Tika Configuration file to override default config with.");
        options.addOption("d", "digest", true, "include digest in metadata, e.g. md5,sha1:32,sha256");
        options.addOption("dml", "digestMarkLimit", true, "max number of bytes to mark on stream for digest");
        options.addOption("l", "log", true, "request URI log level ('debug' or 'info')");
        options.addOption("s", "includeStack", false, "whether or not to return a stack trace\nif there is an exception during 'parse'");
        options.addOption("i", "id", true, "id to use for server in server status endpoint");
        options.addOption("status", false, "enable the status endpoint");
        options.addOption("?", "help", false, "this help message");
        options.addOption("enableUnsecureFeatures", false, "this is required to enable fetchers and emitters. "+
            " The user acknowledges that fetchers and emitters introduce potential security vulnerabilities.");
        options.addOption("noFork", false, "legacy mode, less robust -- this starts up tika-server" +
                " without forking a process.");
        options.addOption("taskTimeoutMillis", true,
                "Not allowed in -noFork: how long to wait for a task (e.g. parse) to finish");
        options.addOption("taskPulseMillis", true,
                "Not allowed in -noFork: how often to check if a task has timed out.");
        options.addOption("pingTimeoutMillis", true,
                "Not allowed in -noFork: how long to wait to wait for a ping and/or ping response.");
        options.addOption("pingPulseMillis", true,
                "Not allowed in -noFork: how often to check if a ping has timed out.");
        options.addOption("maxForkedStartupMillis", true,
                "Not allowed in -noFork: Maximum number of millis to wait for the forked process to startup.");
        options.addOption("maxRestarts", true,
                "Not allowed in -noFork: how many times to restart forked process, default is -1 (always restart)");
        options.addOption("maxFiles", true,
                "Not allowed in -noFork: shutdown server after this many files (to handle parsers that might introduce " +
                "slowly building memory leaks); the default is "+DEFAULT_MAX_FILES +". Set to -1 to turn this off.");
        options.addOption("javaHome", true,
                "Not allowed in -noFork: override system property JAVA_HOME for calling java for the forked process");
        options.addOption("forkedStatusFile", true,
                "Not allowed in -noFork: temporary file used as to communicate " +
                "with forking process -- do not use this! Should only be invoked by forking process.");
        options.addOption("tmpFilePrefix", true,
                "Not allowed in -noFork: prefix for temp file - for debugging only");
        options.addOption("numRestarts", true,
                "Not allowed in -noFork: number of times that the forked server has had to be restarted.");
        return options;
    }

    public static void main(String[] args) {
        LOG.info("Starting {} server", new Tika());
        try {
            execute(args);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Can't start: ", e);
            System.exit(-1);
        }
    }

    private static void execute(String[] args) throws Exception {
        Options options = getOptions();

        CommandLineParser cliParser = new DefaultParser();

        CommandLine line = cliParser.parse(options, stripForkedArgs(args));
        String[] newArgs = addDefaults(line, args);
        line = cliParser.parse(options, stripForkedArgs(newArgs));
        if (line.hasOption("noFork")) {
            noFork(line, newArgs);
        } else {
            try {
                mainLoop(line, newArgs);
            } catch (InterruptedException e) {
                e.printStackTrace();
                //swallow
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void mainLoop(CommandLine line, String[] origArgs) throws Exception {

        List<String> argList = new ArrayList<>();
        argList.addAll(Arrays.asList(origArgs));

        NonForkedValues nonForkedValues = extractNonForkedValues(argList);
        int maxRestarts = nonForkedValues.maxRestarts;
        List<PortIdPair> portIdPairs = getPortIdPairs(nonForkedValues.id, nonForkedValues.portString);

        String[] args = argList.toArray(new String[0]);

        ExecutorService executorService = Executors.newFixedThreadPool(portIdPairs.size());
        ExecutorCompletionService<WatchDogResult> executorCompletionService = new ExecutorCompletionService<>(executorService);
        ServerTimeoutConfig serverTimeoutConfig = configureServerTimeouts(line);
        for (PortIdPair p : portIdPairs) {
            executorCompletionService.submit(
                    new TikaServerWatchDog(args, p.port, p.id,0, serverTimeoutConfig));
        }

        int finished = 0;
        try {
            while (finished < portIdPairs.size()) {
                Future<WatchDogResult> future = executorCompletionService.poll(1, TimeUnit.MINUTES);
                if (future != null) {
                    LOG.debug("main loop future is available");
                    WatchDogResult result = future.get();
                    LOG.debug("main loop future: ({}); about to restart", result);
                    if (maxRestarts < 0 || result.getNumRestarts() < maxRestarts) {
                        System.err.println("starting up again");
                        executorCompletionService.submit(
                                new TikaServerWatchDog(args, result.getPort(),
                                result.getId(),
                                result.getNumRestarts(), serverTimeoutConfig));
                    } else {
                        System.err.println("finished!");
                        LOG.warn("id {} with port {} has exceeded maxRestarts {}. Shutting down and not restarting.",
                                result.getId(), result.getPort(), maxRestarts);
                        finished++;
                    }
                }
            }
        } finally {
            //this is just asking nicely...there is no guarantee!
            executorService.shutdownNow();
        }
    }

    private static String[] stripForkedArgs(String[] args) {
        List<String> ret = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("-J")) {
                ret.add(arg);
            }
        }
        return ret.toArray(new String[0]);
    }

    //removes and records values that either shouldn't go into the forked
    //process or need to be modified
    private static NonForkedValues extractNonForkedValues(List<String> args) {
        int idIndex = -1;
        int portIndex = -1;
        int maxRestartIndex = -1;
        NonForkedValues nonForked = new NonForkedValues();

        for (int i = 0; i < args.size()-1; i++) {
            if (args.get(i).equals("-i") || args.get(i).equals("--id")) {
                idIndex = i;
                nonForked.id = args.get(i+1);
            } else if (args.get(i).equals("-p") ||
                    args.get(i).equals("--port") || args.get(i).equals("--ports")) {
                portIndex = i;
                nonForked.portString = args.get(i+1);
            } else if (args.get(i).equals("-maxRestarts")
                    || args.get(i).equals("--maxRestarts")) {
                maxRestartIndex = i;
                nonForked.maxRestarts = Integer.parseInt(args.get(i+1));
            }
        }


        //now remove -i and -p and their values from args
        List<String> copy = new ArrayList<>();
        copy.addAll(args);
        args.clear();
        for(int i = 0; i < copy.size(); i++) {
            if (i == idIndex || i == portIndex || i == maxRestartIndex) {
                i++;
                continue;
            }
            args.add(copy.get(i));
        }

        return nonForked;
    }

    public static void noFork(CommandLine line, String[] args) {
        //make sure the user didn't misunderstand the options
        for (String forkedOnly : ONLY_IN_FORK_MODE) {
            if (line.hasOption(forkedOnly)) {
                System.err.println("The option '" + forkedOnly +
                        "' can't be used with '-noFork'");
                usage(getOptions());
            }
        }
        if (line.hasOption("p")) {
            String val = line.getOptionValue("p");
            try {
                Integer.parseInt(val);
            } catch (NumberFormatException e) {
                System.err.println("-p must be a single integer in -noFork mode. I see: "+val);
                usage(getOptions());
            }
        }
        TikaServerProcess.main(args);
    }

    private static String[] addDefaults(CommandLine line, String[] args) {
        List<String> newArr = new ArrayList<>(Arrays.asList(args));
        if (! line.hasOption("p")) {
            newArr.add("-p");
            newArr.add(Integer.toString(DEFAULT_PORT));
        }
        if (! line.hasOption("h")) {
            newArr.add("-h");
            newArr.add(DEFAULT_HOST);
        }

        if (! line.hasOption("i")) {
            newArr.add("-i");
            newArr.add(UUID.randomUUID().toString());
        }
        return newArr.toArray(new String[0]);
    }

    private static List<PortIdPair> getPortIdPairs(String idString, String portsArg) {
        List<PortIdPair> pairs = new ArrayList<>();
        Matcher m = Pattern.compile("^(\\d+)-(\\d+)\\Z").matcher("");
        for (String val : portsArg.split(",")) {
            m.reset(val);
            if (m.find()) {
                int min = Math.min(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                int max = Math.max(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                for (int i = min; i <= max; i++) {
                    pairs.add(new PortIdPair(i, idString+"-"+i));
                }
            } else {
                pairs.add(new PortIdPair(Integer.parseInt(val), idString+"-"+val));
            }
        }
        return pairs;
    }


    private static void usage(Options options) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("tikaserver", options);
        System.exit(-1);
    }

    private static ServerTimeoutConfig configureServerTimeouts(CommandLine line) {
        ServerTimeoutConfig serverTimeouts = new ServerTimeoutConfig();
        /*TODO -- add these in
        if (line.hasOption("forkedProcessStartupMillis")) {
            serverTimeouts.setForkedProcessStartupMillis(
                    Long.parseLong(line.getOptionValue("forkedProcessStartupMillis")));
        }
        if (line.hasOption("forkedProcessShutdownMillis")) {
            serverTimeouts.setForkedProcessShutdownMillis(
                    Long.parseLong(line.getOptionValue("forkedProcesShutdownMillis")));
        }*/
        if (line.hasOption("taskTimeoutMillis")) {
            serverTimeouts.setTaskTimeoutMillis(
                    Long.parseLong(line.getOptionValue("taskTimeoutMillis")));
        }
        if (line.hasOption("pingTimeoutMillis")) {
            serverTimeouts.setPingTimeoutMillis(
                    Long.parseLong(line.getOptionValue("pingTimeoutMillis")));
        }
        if (line.hasOption("pingPulseMillis")) {
            serverTimeouts.setPingPulseMillis(
                    Long.parseLong(line.getOptionValue("pingPulseMillis")));
        }

        if (line.hasOption("maxRestarts")) {
            serverTimeouts.setMaxRestarts(Integer.parseInt(line.getOptionValue("maxRestarts")));
        }

        if (line.hasOption("maxForkedStartupMillis")) {
            serverTimeouts.setMaxForkedStartupMillis(
                    Long.parseLong(line.getOptionValue("maxForkedStartupMillis")));
        }

        return serverTimeouts;
    }

    private static class PortIdPair {
        int port;
        String id;

        public PortIdPair(int port, String id) {
            this.port = port;
            this.id = id;
        }
    }

    /**
     * these are parameters that should not go
     * directly into the forked process.  They
     * are either used by the forking process or
     * they are modified or may be modified before
     * creating the forked process.
     */
    private static class NonForkedValues {
        String portString;
        String id;
        int maxRestarts = -1;

    }
}
