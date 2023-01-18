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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class FuzzingCLIConfig {

    private static final int DEFAULT_NUM_ITERATIONS = 100;

    //allow all transformers to operate
    private static final int DEFAULT_MAX_TRANSFORMERS = 1;

    private static final int DEFAULT_RETRIES = 1;

    static Options OPTIONS;

    static {
        Option problems = new Option("o", "output", true, "directory for problems files");
        problems.setRequired(true);


        OPTIONS = new Options().addOption(problems)
                .addOption(Option.builder("c").longOpt("config").hasArg(true)
                        .desc("tika config " +
                                "file with " +
                                "specs for pipes parser, pipes iterator, fetchers and emitters")
                        .required(true).build())
                .addOption(Option.builder("p").longOpt("perFile")
                .desc("number of iterations to run per seed file").hasArg(true).required(false)
                .build())
                .addOption(Option.builder("t").longOpt("maxTransformers")
                .desc("maximum number of transformers to run per iteration").hasArg(true)
                .required(false).build())
                .addOption(Option.builder("r").longOpt("retries")
                .desc("number of times to retry a seed file if there's a catastrophic failure")
                .hasArg(true).required(false).build());

    }
    //number of variants tried per file
    int perFileIterations = DEFAULT_NUM_ITERATIONS;
    //maxTransformers per file
    int maxTransformers = DEFAULT_MAX_TRANSFORMERS;
    //max time allowed to process each file in milliseconds
    long timeoutMS;
    //times to retry a seed file after a catastrophic failure
    int retries = DEFAULT_RETRIES;

    Path tikaConfig;

    Path problemsDir;

    public static FuzzingCLIConfig parse(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(OPTIONS, args);
        FuzzingCLIConfig config = new FuzzingCLIConfig();
        config.tikaConfig = Paths.get(commandLine.getOptionValue("c"));
        config.problemsDir = Paths.get(commandLine.getOptionValue("o"));
        config.retries =
                (commandLine.hasOption("r")) ? Integer.parseInt(commandLine.getOptionValue("r")) :
                        DEFAULT_RETRIES;
        config.maxTransformers = (commandLine.hasOption("t")) ?
                Integer.parseInt(commandLine.getOptionValue("t")) : DEFAULT_MAX_TRANSFORMERS;
        return config;
    }

    public Path getProblemsDirectory() {
        return problemsDir;
    }

    public Path getTikaConfig() {
        return tikaConfig;
    }

    public int getMaxTransformers() {
        return maxTransformers;
    }

    public int getPerFileIterations() {
        return perFileIterations;
    }

    public int getRetries() {
        return retries;
    }
}
