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
import java.util.Iterator;
import java.util.List;
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
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.core.async.AsyncProcessor;
import org.apache.tika.pipes.core.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.core.pipesiterator.PipesIteratorManager;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.utils.StringUtils;

public class TikaAsyncCLI {

    private static final long TIMEOUT_MS = 600_000;
    private static final Logger LOG = LoggerFactory.getLogger(TikaAsyncCLI.class);

    private static Options getOptions() {
        Options options = new Options();
        options.addOption("i", "inputDir", true, "input directory");
        options.addOption("o", "outputDir", true, "output directory");
        options.addOption("n", "numClients", true, "number of forked clients");
        options.addOption("X", "Xmx", true, "heap for the forked clients in usual jvm heap amount, e.g. -X 1g");
        options.addOption("?", "help", false, "this help message");
        options.addOption("T", "timeoutMs", true, "timeout for each parse in milliseconds");
        options.addOption("h", "handlerType", true, "handler type: t=text, h=html, x=xml, b=body, i=ignore");
        options.addOption("p", "pluginsDir", true, "plugins directory");
        //options.addOption("l", "fileList", true, "file list");
        options.addOption("c", "config", true, "tikaConfig to inherit from -- " +
                "commandline options will not overwrite existing iterators, emitters, fetchers and async");
        options.addOption("a", "asyncConfig", true, "asyncConfig/plugins to use");
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
        LOG.warn("processing args " + args.length);
        if (args.length == 2) {
            if (args[0].endsWith(".xml") && args[1].endsWith(".json")) {
                LOG.warn("processing args");
                processWithTikaConfig(PipesIteratorManager.load(Paths.get(args[1])), Paths.get(args[0]), Paths.get(args[1]), null);
                return;
            }
        }

        SimpleAsyncConfig simpleAsyncConfig = parseCommandLine(args);

        Path tikaConfig = StringUtils.isBlank(simpleAsyncConfig.getTikaConfig()) ? null : Paths.get(simpleAsyncConfig.getTikaConfig());
        Path pluginsConfig = StringUtils.isBlank(simpleAsyncConfig.getAsyncConfig()) ? null : Paths.get(simpleAsyncConfig.getAsyncConfig());
        Path tmpPluginsConfig = null;
        Path tmpTikaConfig = null;
        PipesIterator pipesIterator = null;
        try {
            if (tikaConfig == null) {
                tmpTikaConfig = Files.createTempFile("tika-async-tmp-", ".xml");
                tikaConfig = tmpTikaConfig;
                TikaConfigAsyncWriter tikaConfigAsyncWriter = new TikaConfigAsyncWriter(simpleAsyncConfig);
                tikaConfigAsyncWriter.write(tikaConfig);
            }
            if (pluginsConfig == null) {
                tmpPluginsConfig = Files.createTempFile("tika-async-tmp-", ".json");
                pluginsConfig = tmpPluginsConfig;
                PluginsWriter pluginsWriter = new PluginsWriter(simpleAsyncConfig);
                pluginsWriter.write(pluginsConfig);
            }

            pipesIterator = buildPipesIterator(pluginsConfig, simpleAsyncConfig);


            processWithTikaConfig(pipesIterator, tikaConfig, pluginsConfig, simpleAsyncConfig);
        } finally {
            if (tmpTikaConfig != null) {
                Files.delete(tmpTikaConfig);
            }
            if (tmpPluginsConfig != null) {
                Files.delete(tmpPluginsConfig);
            }
        }
    }


    private static PipesIterator buildPipesIterator(Path pluginsConfig, SimpleAsyncConfig simpleAsyncConfig) throws TikaConfigException, IOException {
        String inputDirString = simpleAsyncConfig.getInputDir();
        if (StringUtils.isBlank(inputDirString)) {
            return PipesIteratorManager.load(pluginsConfig);
        }
        Path p = Paths.get(simpleAsyncConfig.getInputDir());
        if (Files.isRegularFile(p)) {
            return new SingleFilePipesIterator(p.getFileName().toString());
        }
        return PipesIteratorManager.load(pluginsConfig);
    }

    //not private for testing purposes
    static SimpleAsyncConfig parseCommandLine(String[] args) throws TikaConfigException, ParseException, IOException {
        if (args.length == 2 && ! args[0].startsWith("-")) {
            return new SimpleAsyncConfig(args[0], args[1], null,
                    null, null, null, null, null,
                    BasicContentHandlerFactory.HANDLER_TYPE.TEXT, false, null);
        }

        Options options = getOptions();

        CommandLineParser cliParser = new DefaultParser();

        CommandLine line = cliParser.parse(options, args, true);
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
        String asyncConfig = null;
        String pluginsDir = null;
        BasicContentHandlerFactory.HANDLER_TYPE handlerType = BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
        boolean extractBytes = false;
        if (line.hasOption("i")) {
            inputDir = line.getOptionValue("i");
        }
        if (line.hasOption("o")) {
            outputDir = line.getOptionValue("o");
        }
        if (line.hasOption("X")) {
            xmx = line.getOptionValue("X");
        }
        if (line.hasOption("T")) {
            timeoutMs = Long.parseLong(line.getOptionValue("T"));
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
        if (line.hasOption('h')) {
            handlerType = getHandlerType(line.getOptionValue('h'));
        }
        if (line.hasOption('a')) {
            asyncConfig = line.getOptionValue('a');
        }
        if (line.hasOption('p')) {
            pluginsDir = line.getOptionValue('p');
        }
        if (line.getArgList().size() > 2) {
            throw new TikaConfigException("Can't have more than 2 unknown args: " + line.getArgList());
        }

        if (line.getArgList().size() == 2) {
            if (inputDir != null || outputDir != null) {
                throw new TikaConfigException("Can only set inputDir and outputDir once. Extra args: " + line.getArgList());
            }
            String inString = line.getArgList().get(0);
            String outString = line.getArgList().get(1);
            if (inString.startsWith("-") || outString.startsWith("-")) {
                throw new TikaConfigException("Found an unknown arg in one of the last two args: " + line.getArgList());
            }
            Path p = Paths.get(inString);
            if (! Files.isDirectory(p) && ! Files.isRegularFile(p)) {
                throw new TikaConfigException("Input file/dir must exist: " + p);
            }
            inputDir = inString;
            outputDir = outString;
        } else if (line.getArgList().size() == 1) {
            if (inputDir != null) {
                throw new TikaConfigException("Can only set inputDir once. Extra args: " + line.getArgList());
            }
            String inString = line.getArgList().get(0);
            if (inString.startsWith("-")) {
                throw new TikaConfigException("Found an unknown arg in one of the last arg: " + inString);
            }
            Path inputPath = Paths.get(inString);
            if (! Files.isDirectory(inputPath) && ! Files.isRegularFile(inputPath)) {
                throw new TikaConfigException("Input file/dir must exist: " + inputPath);
            }
            inputDir = inString;
            if (Files.isRegularFile(inputPath)) {
                outputDir = Paths.get(".").toAbsolutePath().toString();
            } else {
                outputDir = Paths.get("output").toAbsolutePath().toString();
            }
        }

        return new SimpleAsyncConfig(inputDir, outputDir,
                numClients, timeoutMs, xmx, fileList, tikaConfig, asyncConfig, handlerType,
                extractBytes, pluginsDir);
    }

    private static BasicContentHandlerFactory.HANDLER_TYPE getHandlerType(String t) throws TikaConfigException {
        return switch (t) {
            case "x" -> BasicContentHandlerFactory.HANDLER_TYPE.XML;
            case "h" -> BasicContentHandlerFactory.HANDLER_TYPE.HTML;
            case "b" -> BasicContentHandlerFactory.HANDLER_TYPE.BODY;
            case "i" -> BasicContentHandlerFactory.HANDLER_TYPE.IGNORE;
            case "t" -> BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
            default -> throw new TikaConfigException("Can't understand " + t + " as a handler type. Must be one of: x(ml), h(tml), b(ody), i(gnore), t(ext)");
        };
    }


    private static void processWithTikaConfig(PipesIterator pipesIterator, Path tikaConfigPath, Path pluginsConfig, SimpleAsyncConfig asyncConfig) throws Exception {
        long start = System.currentTimeMillis();
        try (AsyncProcessor processor = new AsyncProcessor(tikaConfigPath, pluginsConfig, pipesIterator)) {

            for (FetchEmitTuple t : pipesIterator) {
                configureExtractBytes(t, asyncConfig);
                configureHandler(t, asyncConfig);
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

    private static void configureHandler(FetchEmitTuple t, SimpleAsyncConfig asyncConfig) {
        if (asyncConfig == null) {
            return;
        }
        if (asyncConfig.getHandlerType() == BasicContentHandlerFactory.HANDLER_TYPE.TEXT) {
            return;
        }
        HandlerConfig handlerConfig = new HandlerConfig(asyncConfig.getHandlerType(), HandlerConfig.PARSE_MODE.RMETA,
                -1, -1, false);
        t.getParseContext().set(HandlerConfig.class, handlerConfig);
    }

    private static void configureExtractBytes(FetchEmitTuple t, SimpleAsyncConfig asyncConfig) {
        if (asyncConfig == null) {
            return;
        }
        if (!asyncConfig.isExtractBytes()) {
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

    private static class SingleFilePipesIterator implements PipesIterator {
        private final String fName;
        public SingleFilePipesIterator(String fName) {
            this.fName = fName;
        }


        @Override
        public Iterator<FetchEmitTuple> iterator() {
            FetchEmitTuple t = new FetchEmitTuple("0",
                    new FetchKey(TikaConfigAsyncWriter.FETCHER_NAME, fName),
                    new EmitKey(TikaConfigAsyncWriter.EMITTER_NAME, fName)
            );
            return List.of(t).iterator();
        }

        @Override
        public Integer call() throws Exception {
            return 1;
        }

        @Override
        public ExtensionConfig getExtensionConfig() {
            return null;
        }
    }
}
