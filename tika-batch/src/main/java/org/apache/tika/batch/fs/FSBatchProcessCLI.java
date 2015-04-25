package org.apache.tika.batch.fs;

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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.tika.batch.BatchProcess;
import org.apache.tika.batch.BatchProcessDriverCLI;
import org.apache.tika.batch.ParallelFileProcessingResult;
import org.apache.tika.batch.builders.BatchProcessBuilder;
import org.apache.tika.batch.builders.CommandLineParserBuilder;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

public class FSBatchProcessCLI {

    public static String FINISHED_STRING = "Main thread in TikaFSBatchCLI has finished processing.";

    private static Logger logger = LoggerFactory.getLogger(FSBatchProcessCLI.class);
    private final Options options;

    public FSBatchProcessCLI(String[] args) throws IOException {
        TikaInputStream configIs = null;
        try {
            configIs = getConfigInputStream(args, true);
            CommandLineParserBuilder builder = new CommandLineParserBuilder();
            options = builder.build(configIs);
        } finally {
            IOUtils.closeQuietly(configIs);
        }
    }

    public void usage() {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("tika filesystem batch", options);
    }

    private TikaInputStream getConfigInputStream(String[] args, boolean logDefault) throws IOException {
        TikaInputStream is = null;
        File batchConfigFile = getConfigFile(args);
        if (batchConfigFile != null) {
            //this will throw IOException if it can't find a specified config file
            //better to throw an exception than silently back off to default.
            is = TikaInputStream.get(batchConfigFile);
        } else {
            if (logDefault) {
                logger.info("No config file set via -bc, relying on default-tika-batch-config.xml");
            }
            is = TikaInputStream.get(
                    FSBatchProcessCLI.class.getResourceAsStream("default-tika-batch-config.xml"));
        }
        return is;
    }

    private void execute(String[] args) throws Exception {

        CommandLineParser cliParser = new GnuParser();
        CommandLine line = cliParser.parse(options, args);

        if (line.hasOption("help")) {
            usage();
            System.exit(BatchProcessDriverCLI.PROCESS_NO_RESTART_EXIT_CODE);
        }

        Map<String, String> mapArgs = new HashMap<String, String>();
        for (Option option : line.getOptions()) {
            String v = option.getValue();
            if (v == null || v.equals("")) {
                v = "true";
            }
            mapArgs.put(option.getOpt(), v);
        }

        BatchProcessBuilder b = new BatchProcessBuilder();
        TikaInputStream is = null;
        BatchProcess process = null;
        try {
            is = getConfigInputStream(args, false);
            process = b.build(is, mapArgs);
        } finally {
            IOUtils.closeQuietly(is);
        }
        final Thread mainThread = Thread.currentThread();


        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ParallelFileProcessingResult> futureResult = executor.submit(process);

        ParallelFileProcessingResult result = futureResult.get();
        System.out.println(FINISHED_STRING);
        System.out.println("\n");
        System.out.println(result.toString());
        System.exit(result.getExitStatus());
    }

    private File getConfigFile(String[] args) {
        File configFile = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-bc") || args[i].equals("-batch-config")) {
                if (i < args.length-1) {
                    configFile = new File(args[i+1]);
                }
            }
        }
        return configFile;
    }

    public static void main(String[] args) throws Exception {

        try{
            FSBatchProcessCLI cli = new FSBatchProcessCLI(args);
            cli.execute(args);
        } catch (Throwable t) {
            t.printStackTrace();
            logger.error(MarkerFactory.getMarker("FATAL"),
                    "Fatal exception from FSBatchProcessCLI: " + t.getMessage(), t);
            System.exit(BatchProcessDriverCLI.PROCESS_NO_RESTART_EXIT_CODE);
        }
    }

}
