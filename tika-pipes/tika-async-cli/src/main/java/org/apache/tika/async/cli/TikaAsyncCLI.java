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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.core.FetchEmitTuple;
import org.apache.tika.pipes.core.async.AsyncProcessor;
import org.apache.tika.pipes.core.emitter.EmitKey;
import org.apache.tika.pipes.core.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.core.fetcher.FetchKey;
import org.apache.tika.pipes.core.pipesiterator.PipesIterator;
import org.apache.tika.utils.StringUtils;

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
        options.addOption("c", "config", true, "tikaConfig to inherit from -- " +
                "commandline options will not overwrite existing iterators, emitters, fetchers and async");
        options.addOption("Z", "unzip", false, "extract raw bytes from attachments");

        return options;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage(getOptions());
        } else {
            processCommandLine(args);
        }
    }

    private static void processCommandLine(String[] args) throws Exception {
        if (args.length == 1) {
            processWithTikaConfig(PipesIterator.build(Paths.get(args[0])), Paths.get(args[0]), false);
            return;

        }
        if (args.length == 2 && args[0].equals("-c")) {
            processWithTikaConfig(PipesIterator.build(Paths.get(args[1])), Paths.get(args[1]), false);
            return;
        }
        SimpleAsyncConfig simpleAsyncConfig = parseCommandLine(args);

        Path tikaConfig = null;
        try {
            tikaConfig = Files.createTempFile("tika-async-tmp-", ".xml");
            TikaConfigAsyncWriter tikaConfigAsyncWriter = new TikaConfigAsyncWriter(simpleAsyncConfig);
            tikaConfigAsyncWriter.write(tikaConfig);
            PipesIterator pipesIterator = buildPipesIterator(tikaConfig, simpleAsyncConfig);
            processWithTikaConfig(pipesIterator, tikaConfig, simpleAsyncConfig.isExtractBytes());
        } finally {
            if (tikaConfig != null) {
                Files.delete(tikaConfig);
            }
        }
    }

    private static PipesIterator buildPipesIterator(Path tikaConfig, SimpleAsyncConfig simpleAsyncConfig) throws TikaConfigException, IOException {
        String inputDirString = simpleAsyncConfig.getInputDir();
        if (StringUtils.isBlank(inputDirString)) {
            return PipesIterator.build(tikaConfig);
        }
        Path p = Paths.get(simpleAsyncConfig.getInputDir());
        if (Files.isRegularFile(p)) {
            return new SingleFilePipesIterator(p.getFileName().toString(), simpleAsyncConfig.isExtractBytes());
        }
        return PipesIterator.build(tikaConfig);
    }

    //not private for testing purposes
    static SimpleAsyncConfig parseCommandLine(String[] args) throws ParseException, IOException {
        if (args.length == 2 && ! args[0].startsWith("-")) {
            return new SimpleAsyncConfig(args[0], args[1], null,
                    null, null, null, null, false);
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
        String tikaConfig = null;
        boolean extractBytes = false;
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
        if (line.hasOption("c")) {
            tikaConfig = line.getOptionValue("c");
        }
        if (line.hasOption("Z")) {
            extractBytes = true;
        }

        return new SimpleAsyncConfig(inputDir, outputDir,
                numClients, timeoutMs, xmx, fileList, tikaConfig, extractBytes);
    }


    private static void processWithTikaConfig(PipesIterator pipesIterator, Path tikaConfigPath, boolean extractBytes) throws Exception {
        long start = System.currentTimeMillis();
        try (AsyncProcessor processor = new AsyncProcessor(tikaConfigPath, pipesIterator)) {

            for (FetchEmitTuple t : pipesIterator) {
                configureExtractBytes(t, extractBytes);
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

    private static void configureExtractBytes(FetchEmitTuple t, boolean extractBytes) {
        if (! extractBytes) {
            return;
        }
        ParseContext parseContext = t.getParseContext();
        EmbeddedDocumentBytesConfig config = new EmbeddedDocumentBytesConfig();
        config.setExtractEmbeddedDocumentBytes(true);
        config.setEmitter(TikaConfigAsyncWriter.EMITTER_NAME);
        config.setIncludeOriginal(false);
        config.setSuffixStrategy(EmbeddedDocumentBytesConfig.SUFFIX_STRATEGY.DETECTED);
        config.setEmbeddedIdPrefix("-");
        config.setZeroPadName(8);
        config.setKeyBaseStrategy(EmbeddedDocumentBytesConfig.KEY_BASE_STRATEGY.CONTAINER_NAME_AS_IS);
        parseContext.set(EmbeddedDocumentBytesConfig.class, config);
    }

    private static void usage(Options options) throws IOException {
        System.out.println("Two primary options:");
        System.out.println("\t1. Specify a tika-config.xml on the commandline that includes the definitions for async");
        System.out.println("\t2. Commandline:");
        org.apache.commons.cli.help.HelpFormatter helpFormatter = org.apache.commons.cli.help.HelpFormatter.builder().get();
        helpFormatter.printHelp("tikaAsyncCli", null, options, null, true);
        System.exit(1);
    }

    private static class SingleFilePipesIterator extends PipesIterator {
        private final String fName;
        private final boolean extractBytes;
        public SingleFilePipesIterator(String string, boolean extractBytes) {
            super();
            this.fName = string;
            this.extractBytes = extractBytes;
        }

        @Override
        protected void enqueue() throws IOException, TimeoutException, InterruptedException {
            FetchEmitTuple t = new FetchEmitTuple("0",
                    new FetchKey(TikaConfigAsyncWriter.FETCHER_NAME, fName),
                    new EmitKey(TikaConfigAsyncWriter.EMITTER_NAME, fName)
                    );
            tryToAdd(t);
        }
    }
}
