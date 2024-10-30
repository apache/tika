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
package org.apache.tika.async.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.async.AsyncProcessor;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

public class TikaAsyncCLI {

    private static final long TIMEOUT_MS = 600_000;
    private static final Logger LOG = LoggerFactory.getLogger(TikaAsyncCLI.class);

    private static Options getOptions() {
        Options options = new Options();
        options.addOption("i", "inputDir", true, "input directory");
        options.addOption("o", "outputDir", true, "output directory");

        options.addOption("n", "numClients", true, "number of forked clients");
        options.addOption("x", "Xmx", true, "heap for the forked clients in usual jvm heap amount, e.g. -x 1g");
        options.addOption("?", "help", false, "this help message");
        options.addOption("t", "timeoutMs", true, "timeout for each parse in milliseconds");
        options.addOption("l", "fileList", true, "file list");

        return options;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage(getOptions());
        } else if (args.length == 1) {
            processWithTikaConfig(Paths.get(args[0]));
        } else {
            processCommandLine(args);
        }
    }

    private static void processCommandLine(String[] args) throws Exception {

        SimpleAsyncConfig simpleAsyncConfig = parseCommandLine(args);

        Path tikaConfig = null;
        try {
            tikaConfig = Files.createTempFile("tika-async-tmp-", ".xml");
            TikaConfigAsyncWriter tikaConfigAsyncWriter = new TikaConfigAsyncWriter(simpleAsyncConfig);
            tikaConfigAsyncWriter.write(tikaConfig);
            processWithTikaConfig(tikaConfig);
        } finally {
            if (tikaConfig != null) {
                Files.delete(tikaConfig);
            }
        }
    }

    //not private for testing purposes
    static SimpleAsyncConfig parseCommandLine(String[] args) throws ParseException {
        if (args.length == 2 && ! args[0].startsWith("-")) {
            return new SimpleAsyncConfig(args[0], args[1], null, null, null, null);
        }

        Options options = getOptions();

        CommandLineParser cliParser = new DefaultParser();

        CommandLine line = cliParser.parse(options, args);
        if (line.hasOption("help")) {
            usage(options);
        }
        String inputDir = null;
        String outputDir = null;
        String xmx = null;
        Long timeoutMs = null;
        Integer numClients = null;
        String fileList = null;
        if (line.hasOption("i")) {
            inputDir = line.getOptionValue("i");
        }
        if (line.hasOption("o")) {
            outputDir = line.getOptionValue("o");
        }
        if (line.hasOption("x")) {
            xmx = line.getOptionValue("x");
        }
        if (line.hasOption("t")) {
            timeoutMs = Long.parseLong(line.getOptionValue("t"));
        }
        if (line.hasOption("n")) {
            numClients = Integer.parseInt(line.getOptionValue("n"));
        }
        if (line.hasOption("l")) {
            fileList = line.getOptionValue("l");
        }
        return new SimpleAsyncConfig(inputDir, outputDir,
                numClients, timeoutMs, xmx, fileList);
    }


    private static void processWithTikaConfig(Path tikaConfigPath) throws Exception {
        PipesIterator pipesIterator = PipesIterator.build(tikaConfigPath);
        long start = System.currentTimeMillis();
        try (AsyncProcessor processor = new AsyncProcessor(tikaConfigPath, pipesIterator)) {

            for (FetchEmitTuple t : pipesIterator) {
                boolean offered = processor.offer(t, TIMEOUT_MS);
                if (!offered) {
                    throw new TimeoutException("timed out waiting to add a fetch emit tuple");
                }
            }
            processor.finished();
            while (true) {
                if (processor.checkActive()) {
                    Thread.sleep(500);
                } else {
                    break;
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            LOG.info("Successfully finished processing {} files in {} ms", processor.getTotalProcessed(), elapsed);

        }
    }

    private static void usage(Options options) {
        System.out.println("Two primary options:");
        System.out.println("\t1. Specify a tika-config.xml on the commandline that includes the definitions for async");
        System.out.println("\t2. Commandline:");
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("tikaAsynCli", options);
        System.exit(1);
    }
}
