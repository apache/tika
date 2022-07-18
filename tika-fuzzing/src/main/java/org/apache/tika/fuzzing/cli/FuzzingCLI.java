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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.fuzzing.Transformer;
import org.apache.tika.fuzzing.general.ByteDeleter;
import org.apache.tika.fuzzing.general.ByteFlipper;
import org.apache.tika.fuzzing.general.ByteInjector;
import org.apache.tika.fuzzing.general.GeneralTransformer;
import org.apache.tika.fuzzing.general.SpanSwapper;
import org.apache.tika.fuzzing.general.Truncator;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesConfig;
import org.apache.tika.pipes.PipesParser;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

public class FuzzingCLI {

    private static final Logger LOG = LoggerFactory.getLogger(FuzzingCLI.class);
    private static final String TEMP_FETCHER_NAME = "temp";
    private static final String TEMP_EMITTER_NAME = "temp";

    public static void main(String[] args) throws Exception {
        FuzzingCLIConfig config = FuzzingCLIConfig.parse(args);
        if (config.getMaxTransformers() == 0) {
            LOG.warn("max transformers == 0!");
        }

        FuzzingCLI fuzzingCLI = new FuzzingCLI();
        Files.createDirectories(config.getProblemsDirectory());
        fuzzingCLI.execute(config);
    }


    private void execute(FuzzingCLIConfig config) throws Exception {
        ArrayBlockingQueue<FetchEmitTuple> q = new ArrayBlockingQueue(10000);

        PipesConfig pipesConfig = PipesConfig.load(config.getTikaConfig());
        FetcherManager fetcherManager = FetcherManager.load(config.getTikaConfig());

        int totalThreads = pipesConfig.getNumClients() + 1;

        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        ExecutorCompletionService executorCompletionService =
                new ExecutorCompletionService(executorService);
        PipesIterator pipesIterator = PipesIterator.build(config.getTikaConfig());

        FileAdder fileAdder = new FileAdder(pipesIterator, q);
        executorCompletionService.submit(fileAdder);
        try (PipesParser parser = new PipesParser(pipesConfig)) {

            for (int i = 0; i < pipesConfig.getNumClients(); i++) {
                executorCompletionService.submit(new Fuzzer(q, config, parser, fetcherManager));
            }
            int finished = 0;
            while (finished < totalThreads) {
                Future<Integer> future = null;
                try {
                    future = executorCompletionService.poll(1, TimeUnit.SECONDS);
                    if (future != null) {
                        future.get();
                        finished++;
                    }
                    LOG.info("Finished thread {} threads of {}", finished, totalThreads);
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    break;
                }
            }
            executorService.shutdown();
            executorService.shutdownNow();
        }

    }

    private static class Fuzzer implements Callable<Integer> {
        static AtomicInteger COUNTER = new AtomicInteger();
        static AtomicInteger FUZZED = new AtomicInteger();
        static AtomicInteger SOURCE_FILES = new AtomicInteger();
        private final int threadId = COUNTER.getAndIncrement();
        private final ArrayBlockingQueue<FetchEmitTuple> q;
        private final FuzzingCLIConfig config;

        private final PipesParser pipesParser;

        private final Transformer transformer;

        private final FetcherManager fetcherManager;

        public Fuzzer(ArrayBlockingQueue<FetchEmitTuple> q, FuzzingCLIConfig config,
                      PipesParser pipesParser, FetcherManager fetcherManager) {
            this.q = q;
            this.config = config;
            this.pipesParser = pipesParser;
            //TODO - parameterize this
            this.transformer =
                    new GeneralTransformer(config.getMaxTransformers(), new ByteDeleter(),
                            new ByteFlipper(), new ByteInjector(), new Truncator(),
                            new SpanSwapper());
            this.fetcherManager = fetcherManager;
        }

        @Override
        public Integer call() throws Exception {
            while (true) {
                FetchEmitTuple fetchEmitTuple = q.take();
                if (fetchEmitTuple.equals(PipesIterator.COMPLETED_SEMAPHORE)) {
                    LOG.debug("Thread " + threadId + " stopping");
                    q.put(PipesIterator.COMPLETED_SEMAPHORE);
                    return 1;
                }
                int inputFiles = SOURCE_FILES.getAndIncrement();
                if (inputFiles % 100 == 0) {
                    LOG.info("Processed {} source files", inputFiles);
                }
                for (int i = 0; i < config.perFileIterations; i++) {
                    try {
                        fuzzIt(fetchEmitTuple);
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Exception e) {
                        LOG.warn("serious problem with", e);
                    }
                }
            }
        }

        private void fuzzIt(FetchEmitTuple fetchEmitTuple)
                throws IOException, InterruptedException, TikaException {
            Path cwd = Files.createTempDirectory("tika-fuzz-");
            try {
                Path fuzzedPath = fuzz(fetchEmitTuple, cwd);
                Path extract = Files.createTempFile(cwd, "tika-extract-", ".json");
                FetchEmitTuple fuzzedTuple = new FetchEmitTuple(fetchEmitTuple.getId(),
                        new FetchKey(TEMP_FETCHER_NAME, fuzzedPath.toAbsolutePath().toString()),
                        new EmitKey(TEMP_EMITTER_NAME, extract.toAbsolutePath().toString()));
                int count = FUZZED.getAndIncrement();
                if (count % 100 == 0) {
                    LOG.info("processed {} fuzzed files", count);
                }
                boolean tryAgain = true;
                int tries = 0;
                while (tryAgain && tries < config.getRetries()) {
                    tries++;
                    try {
                        PipesResult result = pipesParser.parse(fuzzedTuple);
                        tryAgain = handleResult(result.getStatus(),
                                fetchEmitTuple.getFetchKey().getFetchKey(), fuzzedPath, tries,
                                config.getRetries());
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Exception e) {
                        tryAgain = handleResult(PipesResult.STATUS.UNSPECIFIED_CRASH,
                                fetchEmitTuple.getFetchKey().getFetchKey(), fuzzedPath, tries,
                                config.getRetries());
                    }
                }
            } finally {
                try {
                    FileUtils.deleteDirectory(cwd.toFile());
                } catch (IOException e) {
                    e.printStackTrace();
                    LOG.warn("Couldn't delete " + cwd.toAbsolutePath(), e);
                }
            }
        }

        private Path fuzz(FetchEmitTuple fetchEmitTuple, Path cwd)
                throws IOException, TikaException {
            Path target = Files.createTempFile(cwd, "tika-fuzz-target-",
                    "." + FilenameUtils.getExtension(fetchEmitTuple.getFetchKey().getFetchKey()));
            try (InputStream is = fetcherManager.getFetcher(
                            fetchEmitTuple.getFetchKey().getFetcherName())
                    .fetch(fetchEmitTuple.getFetchKey().getFetchKey(), new Metadata())) {
                try (OutputStream os = Files.newOutputStream(target)) {
                    transformer.transform(is, os);
                }
            }
            return target;
        }

        private boolean handleResult(PipesResult.STATUS status, String origFetchKey,
                                     Path fuzzedPath, int tries, int maxRetries)
                throws IOException {
            switch (status) {
                case OOM:
                case TIMEOUT:
                case UNSPECIFIED_CRASH:
                    if (tries < maxRetries) {
                        LOG.info("trying again ({} of {}) {} : {}", tries, maxRetries,
                                status.name());
                        return true;
                    }
                    Path problemFilePath = getProblemFile(status, origFetchKey);
                    LOG.info("found a problem {} -> {} : {}", origFetchKey, problemFilePath,
                            status.name());
                    Files.copy(fuzzedPath, problemFilePath);
                    return false;
                default:
                    //if there wasn't a problem
                    return false;
            }
        }

        private Path getProblemFile(PipesResult.STATUS status, String origFetchKey)
                throws IOException {
            String name = FilenameUtils.getName(origFetchKey) + "-" + UUID.randomUUID();
            Path problemFile =
                    config.getProblemsDirectory().resolve(status.name().toLowerCase(Locale.US))
                            .resolve(name);
            Files.createDirectories(problemFile.getParent());
            return problemFile;
        }

    }

    private class FileAdder implements Callable<Integer> {
        private final PipesIterator pipesIterator;
        private final ArrayBlockingQueue<FetchEmitTuple> queue;
        private int added = 0;

        public FileAdder(PipesIterator pipesIterator, ArrayBlockingQueue<FetchEmitTuple> queue) {
            this.pipesIterator = pipesIterator;
            this.queue = queue;
        }

        @Override
        public Integer call() throws Exception {
            int added = 0;
            for (FetchEmitTuple tuple : pipesIterator) {
                //hang forever -- should offer and timeout
                queue.put(tuple);
                added++;
            }
            queue.put(PipesIterator.COMPLETED_SEMAPHORE);
            LOG.info("file adder finished " + added);
            return 1;
        }
    }
}
