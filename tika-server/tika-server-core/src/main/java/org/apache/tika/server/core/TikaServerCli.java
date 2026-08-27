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

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.help.HelpFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TikaServerCli {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerCli.class);
    private static Options getOptions() {
        Options options = new Options();
        options.addOption("h", "host", true, "host name (default = " + DEFAULT_HOST + ", use * for all)");
        options.addOption("p", "port", true,
                "listen port (default = 9998)\n");
        options.addOption("?", "help", false, "this help message");
        options.addOption("c", "config", true, "tika-config file");
        options.addOption("i", "id", true, "id for this server, written to the startup log");
        options.addOption(null, "metricsPort", true,
                "serve Prometheus metrics on this port (9404 by convention); "
                        + "unset means metrics are off\n");
        return options;
    }

    public static void main(String[] args) {
        try {
            Options options = getOptions();

            CommandLineParser cliParser = new DefaultParser();

            CommandLine line = cliParser.parse(options, args);
            if (line.hasOption("help")) {
                usage(options);
            }
            TikaServerProcess.main(args);
        } catch (Exception e) {
            LOG.error("Can't start: ", e);
            System.exit(-1);
        }
    }

    private static void usage(Options options) throws IOException {
        HelpFormatter helpFormatter = HelpFormatter.builder().get();
        helpFormatter.printHelp("tikaserver", null, options, null, true);
        System.exit(0);
    }

}
