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
import org.apache.tika.exception.TikaException;
import org.apache.tika.fuzzing.AutoDetectTransformer;
import org.apache.tika.fuzzing.Transformer;
import org.apache.tika.fuzzing.exceptions.CantFuzzException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Forked process that runs against a single input file
 */
public class FuzzOne {
    private static final Logger LOG = LoggerFactory.getLogger(FuzzOne.class);

    static Options OPTIONS;
    static {
        //By the time this commandline is parsed, there should be both an extracts and an inputDir
        Option extracts = new Option("extracts", true, "directory for extract files");
        extracts.setRequired(true);


        OPTIONS = new Options()
                .addOption(Option.builder("i")
                        .longOpt("inputFile")
                        .desc("input directory for seed files")
                        .hasArg(true)
                        .required(true)
                        .build())
                .addOption(Option.builder("o")
                        .longOpt("outputFile")
                        .desc("output file base")
                        .hasArg(true)
                        .required(true)
                        .build())
                .addOption(Option.builder("m")
                        .longOpt("timeoutMs")
                        .desc("timeout in ms -- max time allowed to parse a file")
                        .hasArg(true)
                        .required(true)
                        .build())
                .addOption(Option.builder("n")
                        .desc("thread id (thread number)")
                        .hasArg(true)
                        .required(true)
                        .build())
                .addOption(Option.builder("p")
                        .longOpt("perFile")
                        .desc("number of iterations to run per seed file")
                        .hasArg(true)
                        .required(true)
                        .build())
                .addOption(Option.builder("t")
                        .longOpt("maxTransformers")
                        .desc("maximum number of transformers to run per iteration")
                        .hasArg(true)
                        .required(true)
                        .build())
                .addOption(Option.builder("r")
                        .longOpt("retryId")
                        .desc("which retry is this")
                        .hasArg(true)
                        .required(true)
                        .build());
    }
    Parser parser = new AutoDetectParser();

    public static void main(String[] args) throws Exception {
        FuzzOneConfig config = FuzzOneConfig.parse(args);
        FuzzOne fuzzOne = new FuzzOne();
        fuzzOne.execute(config);
    }

    private void execute(FuzzOneConfig config) {
        Path src = config.inputFile;
        Path targetDir = config.outputFileBase;
        AutoDetectTransformer transformer = new AutoDetectTransformer();
        for (int i = 0; i < config.perFileIterations; i++) {
            try {
                String ext = "-"+config.threadNum + "-" + config.retryNum + "-"+i;
                fuzz(ext, src, targetDir, transformer, config.timeoutMs);
            } catch (IOException e) {
                LOG.warn("problem transforming file", e);
            } catch (CantFuzzException e) {
                LOG.warn("can't fuzz this file "+src, e);
                return;
            } catch (TikaException e) {
                e.printStackTrace();
            }
        }
    }

    private void fuzz(String ext, Path src, Path targetFileBase,
                      Transformer transformer, long timeoutMs) throws IOException, TikaException {

        Path target = targetFileBase.getParent().resolve(
                targetFileBase.getFileName().toString() +ext);

        try {
            transformFile(transformer, src, target);
        } catch (Throwable t) {
            LOG.warn("failed to transform: " + src.toString());
            Files.delete(target);
            throw t;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> future = executor.submit(new ParseTask(target));

        try {
            int result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (result == 1 && Files.exists(target)) {
                LOG.warn("failed to delete target: "+target);
            }
        } catch (TimeoutException e) {
            LOG.warn("timeout exception:"+target);
            future.cancel(true);
            writeErrFile(target, ".timeout");
            System.exit(1);
        } catch (InterruptedException|ExecutionException e) {
            LOG.warn("problem parsing "+target, e);
            System.exit(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void writeErrFile(Path target, String ext) {
        try {
            Path err = target.getParent().resolve(target.getFileName().toString()+ext);
            Files.write(err, new byte[0]);
        } catch (IOException e) {
            LOG.warn("things aren't going right today.", e);
        }
    }

    private void handleThrowable(Path target, Throwable t) {

        try {
            Path errMsg = target.getParent().resolve(target.getFileName().toString()+".stacktrace");
            Files.write(errMsg, ExceptionUtils.getStackTrace(t).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("things aren't going right today.", t);
        }

    }

    private void transformFile(Transformer transformer, Path src, Path target) throws IOException, TikaException {
        try (InputStream is = Files.newInputStream(src); OutputStream os =
                Files.newOutputStream(target)) {
            transformer.transform(is, os);
        }
    }

    private static class FuzzOneConfig {
        static FuzzOneConfig parse(String[] args) throws ParseException {
            CommandLineParser parser = new DefaultParser();
            CommandLine commandLine = parser.parse(OPTIONS, args);
            FuzzOneConfig config = new FuzzOneConfig();
            config.inputFile = Paths.get(commandLine.getOptionValue("i"));
            config.outputFileBase = Paths.get(commandLine.getOptionValue("o"));
            config.perFileIterations = Integer.parseInt(commandLine.getOptionValue("p"));
            config.maxTransformers = Integer.parseInt(commandLine.getOptionValue("t"));
            config.threadNum = Integer.parseInt(commandLine.getOptionValue("n"));
            config.retryNum = Integer.parseInt(commandLine.getOptionValue("r"));
            config.timeoutMs = Integer.parseInt(commandLine.getOptionValue("m"));
            return config;
        }

        private Path inputFile;
        private Path outputFileBase;
        int perFileIterations;
        int maxTransformers;
        int threadNum;
        int retryNum;
        long timeoutMs;

    }

    private class ParseTask implements Callable<Integer> {
        private final Path target;
        public ParseTask(Path target) {
            this.target = target;
        }

        /**
         *
         * @return 1 if success
         * @throws Exception
         */
        @Override
        public Integer call() throws Exception {
            boolean success = false;
            try (InputStream is = Files.newInputStream(target)) {
                LOG.debug("parsing "+target);
                parser.parse(is, new DefaultHandler(), new Metadata(), new ParseContext());
                success = true;
            } catch (TikaException e) {
                if (e.getCause() != null && e.getCause() instanceof RuntimeException) {
                    //handleThrowable(target, e.getCause());
                    success = true;
                } else {
                    success = true;
                }
            } catch (SAXException|IOException e) {
                success = true;
            } catch (Throwable t) {
                handleThrowable(target, t);
            } finally {
                if (success) {
                    try {
                        Files.delete(target);
                    } catch (IOException e) {
                        LOG.warn("couldn't delete: "+target.toAbsolutePath());
                    }
                } else {
                    LOG.info("FOUND PROBLEM: "+target);
                }
            }
            return success ? 1 : 0;
        }
    }
}
