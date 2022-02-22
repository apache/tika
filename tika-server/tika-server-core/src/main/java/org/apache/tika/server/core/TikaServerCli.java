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

import static org.apache.tika.server.core.TikaServerConfig.DEFAULT_HOST;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TikaServerCli {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerCli.class);
    /**
     * This value is set to the server's id in the forked process.
     */
    public static String TIKA_SERVER_ID_ENV = "tika.server.id";

    private static Options getOptions() {
        Options options = new Options();
        options.addOption("h", "host", true,
                "host name (default = " + DEFAULT_HOST + ", use * for all)");
        options.addOption("p", "port", true, "listen port(s) (default = 9998)\n" +
                "Can specify multiple ports with inclusive ranges (e.g. 9990-9999)\n" +
                "or with comma delimited list (e.g. 9996,9998,9995)");
        options.addOption("?", "help", false, "this help message");
        options.addOption("c", "config", true, "tika-config file");

        options.addOption("i", "id", true,
                "id to use for server in" + " the server status endpoint and logging");
        options.addOption("noFork", false, "runs in legacy 1.x mode -- " +
                "server runs in process and is not safely isolated in a forked process");

        return options;
    }

    public static void main(String[] args) {
        try {
            execute(args);
        } catch (Exception e) {
            LOG.error("Can't start: ", e);
            System.exit(-1);
        }
    }

    private static void execute(String[] args) throws Exception {
        Options options = getOptions();

        CommandLineParser cliParser = new DefaultParser();

        CommandLine line = cliParser.parse(options, args);
        if (line.hasOption("help")) {
            usage(options);
        }
        TikaServerConfig tikaServerConfig = TikaServerConfig.load(line);
        if (tikaServerConfig.isNoFork()) {
            noFork(tikaServerConfig);
        } else {
            try {
                mainLoop(tikaServerConfig);
            } catch (InterruptedException e) {
                //swallow
                LOG.debug("interrupted", e);
            }
        }
    }

    private static void mainLoop(TikaServerConfig tikaServerConfig) throws Exception {

        List<PortIdPair> portIdPairs = getPortIdPairs(tikaServerConfig);

        ExecutorService executorService = Executors.newFixedThreadPool(portIdPairs.size());
        ExecutorCompletionService<WatchDogResult> executorCompletionService =
                new ExecutorCompletionService<>(executorService);
        List<TikaServerWatchDog> watchers = new ArrayList<>();
        for (PortIdPair p : portIdPairs) {
            TikaServerWatchDog watcher = new TikaServerWatchDog(p.port, p.id, tikaServerConfig);
            executorCompletionService.submit(watcher);
            watchers.add(watcher);
        }

        int finished = 0;
        try {
            while (finished < portIdPairs.size()) {
                Future<WatchDogResult> future = executorCompletionService.poll(1, TimeUnit.MINUTES);
                if (future != null) {
                    LOG.debug("main loop future is available");
                    WatchDogResult result = future.get();
                    LOG.debug("main loop future: ({}); finished", result);
                    finished++;
                }
            }
        } catch (InterruptedException e) {
            for (TikaServerWatchDog w : watchers) {
                w.shutDown();
            }
            LOG.debug("thread interrupted", e);
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

    public static void noFork(TikaServerConfig tikaServerConfig) throws Exception {
        List<String> args = tikaServerConfig
                .getForkedProcessArgs(tikaServerConfig.getPort(), tikaServerConfig.getIdBase());
        args.add("-noFork");
        TikaServerProcess.main(args.toArray(new String[0]));
    }

    private static void usage(Options options) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("tikaserver", options);
        System.exit(-1);
    }

    private static List<PortIdPair> getPortIdPairs(TikaServerConfig tikaServerConfig) {
        List<PortIdPair> pairs = new ArrayList<>();
        int[] ports = tikaServerConfig.getPorts();
        //if there's only one port, use only the idbase, otherwise append -$port
        if (ports.length == 0) {
            throw new IllegalArgumentException(
                    "Couldn't find any ports in: " + tikaServerConfig.getPort());
        } else if (ports.length == 1) {
            pairs.add(new PortIdPair(ports[0], tikaServerConfig.getIdBase()));
        } else {
            for (int p : ports) {
                pairs.add(new PortIdPair(p, tikaServerConfig.getIdBase() + "-" + p));
            }
        }

        return pairs;
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
