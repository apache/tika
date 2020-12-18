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
package org.apache.tika.fuzzing.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.nio.file.Path;
import java.nio.file.Paths;

public class FuzzingCLIConfig {

    private static final int DEFAULT_NUM_THREADS = 4;
    private static final int DEFAULT_NUM_ITERATIONS = 1000;
    //allow all transformers to operate
    private static final int DEFAULT_MAX_TRANSFORMERS = -1;

    private static final long DEFAULT_TIMEOUT_MS = 120000;

    private static final int DEFAULT_RETRIES = 2;

    private static final String DEFAULT_XMX = "512m";

    static Options OPTIONS;
    static {
        //By the time this commandline is parsed, there should be both an extracts and an inputDir
        Option extracts = new Option("extracts", true, "directory for extract files");
        extracts.setRequired(true);


        OPTIONS = new Options()
                .addOption(Option.builder("i")
                        .longOpt("inputDir")
                        .desc("input directory for seed files")
                        .hasArg(true)
                        .required(true)
                        .build())
                .addOption(Option.builder("o")
                        .longOpt("outputDir")
                        .desc("output directory for files that triggered problems")
                        .hasArg(true)
                        .required(true)
                        .build())
                .addOption(Option.builder("n")
                        .longOpt("numThreads")
                        .desc("number of threads")
                        .hasArg(true)
                        .required(false)
                        .build())
                .addOption(Option.builder("p")
                        .longOpt("perFile")
                        .desc("number of iterations to run per seed file")
                        .hasArg(true)
                        .required(false)
                        .build())
                .addOption(Option.builder("t")
                        .longOpt("maxTransformers")
                        .desc("maximum number of transformers to run per iteration")
                        .hasArg(true)
                        .required(false)
                        .build())
                .addOption(Option.builder("m")
                        .longOpt("timeoutMs")
                        .desc("timeout in ms -- max time allowed to parse a file")
                        .hasArg(true)
                        .required(false)
                        .build())
                .addOption(Option.builder("x")
                        .longOpt("xmx")
                        .desc("e.g. 1G, max heap appended to -Xmx in the forked process")
                        .hasArg(true)
                        .required(false)
                        .build())
                .addOption(Option.builder("r")
                        .longOpt("retries")
                        .desc("number of times to retry a seed file if there's a catastrophic failure")
                        .hasArg(true)
                        .required(false)
                        .build());

    }

    public static FuzzingCLIConfig parse(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(OPTIONS, args);
        FuzzingCLIConfig config = new FuzzingCLIConfig();
        config.inputDir = Paths.get(commandLine.getOptionValue("i"));
        config.outputDir = Paths.get(commandLine.getOptionValue("o"));
        config.numThreads = (commandLine.hasOption("n")) ?
                Integer.parseInt(commandLine.getOptionValue("n")) :
                DEFAULT_NUM_THREADS;
        config.perFileIterations = (commandLine.hasOption("p")) ?
                Integer.parseInt(commandLine.getOptionValue("p")) :
                DEFAULT_NUM_ITERATIONS;
        config.maxTransformers = (commandLine.hasOption("t")) ?
                Integer.parseInt(commandLine.getOptionValue("t")) :
                DEFAULT_MAX_TRANSFORMERS;
        config.timeoutMS = (commandLine.hasOption("m")) ?
                Integer.parseInt(commandLine.getOptionValue("m")) :
                DEFAULT_TIMEOUT_MS;
        config.retries = (commandLine.hasOption("r")) ?
                Integer.parseInt(commandLine.getOptionValue("r")) :
                DEFAULT_RETRIES;
        config.xmx = (commandLine.hasOption("x")) ?
                commandLine.getOptionValue("x") :
                DEFAULT_XMX;
        return config;
    }


    int numThreads;
    //number of variants tried per file
    int perFileIterations;
    //maxTransformers per file
    int maxTransformers;

    //max time allowed to process each file in milliseconds
    long timeoutMS;

    //times to retry a seed file after a catastrophic failure
    int retries;

    //xmx for forked process, e.g. 512m or 1G
    String xmx;
    Path inputDir;
    Path outputDir;


    public int getNumThreads() {
        return numThreads;
    }

    public Path getInputDirectory() {
        return inputDir;
    }

    public Path getOutputDirectory() {
        return outputDir;
    }

    public int getMaxTransformers() {
        return maxTransformers;
    }

    public long getTimeoutMs() {
        return timeoutMS;
    }

    public int getPerFileIterations() {
        return perFileIterations;
    }

    public int getRetries() {
        return retries;
    }
}
