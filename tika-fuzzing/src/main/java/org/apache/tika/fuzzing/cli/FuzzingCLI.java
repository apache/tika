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

import org.apache.tika.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class FuzzingCLI {
    private static final Logger LOG = LoggerFactory.getLogger(FuzzingCLI.class);

    private static final Path POISON = Paths.get("");

    private int maxFiles = -1;

    public static void main (String[] args) throws Exception {
        FuzzingCLIConfig config = FuzzingCLIConfig.parse(args);
        if (config.getMaxTransformers() == 0) {
            LOG.warn("max transformers == 0!");
        }
        if (! Files.isDirectory(config.inputDir)) {
            throw new IllegalArgumentException("input directory doesn't exist: " + config.inputDir);
        }
        FuzzingCLI fuzzingCLI = new FuzzingCLI();
        Files.createDirectories(config.getOutputDirectory());
        fuzzingCLI.execute(config);
    }

    private void execute(FuzzingCLIConfig config) {
        ArrayBlockingQueue<Path> q = new ArrayBlockingQueue(10000);
        ExecutorService executorService = Executors.newFixedThreadPool(config.getNumThreads()+1);
        ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(executorService);
        FileAdder fileAdder = new FileAdder(config.getInputDirectory(), config.getNumThreads(), q);
        executorCompletionService.submit(fileAdder);
        for (int i = 0; i < config.numThreads; i++) {
            executorCompletionService.submit(new Fuzzer(q, config));
        }
        int finished = 0;
        while (finished < config.getNumThreads()+1) {
            Future<Integer> future = null;
            try {
                future = executorCompletionService.poll(1, TimeUnit.SECONDS);
                if (future != null) {
                    future.get();
                    finished++;
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                break;
            }
        }
        executorService.shutdownNow();
    }

    private static class Fuzzer implements Callable<Integer> {
        static AtomicInteger COUNTER = new AtomicInteger();
        private final int threadId = COUNTER.getAndIncrement();
        private final ArrayBlockingQueue<Path> q;
        private final FuzzingCLIConfig config;
        public Fuzzer(ArrayBlockingQueue<Path> q, FuzzingCLIConfig config) {
            this.q = q;
            this.config = config;
        }

        @Override
        public Integer call() throws Exception {
            while (true) {
                Path p = q.take();
                if (p.equals(POISON)) {
                    LOG.debug("Thread "+threadId + " stopping");
                    return 1;
                }
                boolean success = false;
                int tries = 0;
                while (! success && tries < config.getRetries()) {
                    if (tries > 0) {
                        LOG.warn("Retrying ("+tries+") "+p);
                    }
                    success = fuzzIt(config, p, tries);
                    tries++;
                }
            }
        }

        private boolean fuzzIt(FuzzingCLIConfig config, Path p, int retryId) {
            //the target files should be flattened so that
            //problematic files are all in one directory...may rethink this option later
            Path target = config.getOutputDirectory().resolve(
                    p.getFileName());
            String cp = System.getProperty("java.class.path");

            String[] args = new String[] {
                    "java",
                    "-XX:-OmitStackTraceInFastThrow",
                    "-Xmx"+config.xmx,
                    "-ea",
                    "-cp",
                    ProcessUtils.escapeCommandLine(cp),
                    "org.apache.tika.fuzzing.cli.FuzzOne",
                    "-i",
                    ProcessUtils.escapeCommandLine(p.toAbsolutePath().toString()),
                    "-o",
                    ProcessUtils.escapeCommandLine(target.toAbsolutePath().toString()),
                    "-p",
                    Integer.toString(config.getPerFileIterations()),
                    "-t",
                    Integer.toString(config.getMaxTransformers()),
                    "-n",
                    Integer.toString(threadId),
                    "-r",
                    Integer.toString(retryId),
                    "-m",
                    Long.toString(config.getTimeoutMs())
            };
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.inheritIO();
            Process process = null;
            boolean success = false;
            try {
                process = pb.start();
            } catch (IOException e) {
                LOG.warn("problem starting process", e);
            }
            try {
                long totalTime = 2 * config.getTimeoutMs() * config.getPerFileIterations();
                success = process.waitFor(totalTime, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                LOG.warn("problem waiting for process to finish", e);
            } finally {
                if (process.isAlive()) {
                    LOG.warn("process still alive for " + target.toAbsolutePath());
                    process.destroyForcibly();
                }
                try {
                    int exitValue = process.exitValue();
                    if (exitValue != 0) {
                        success = false;
                        LOG.warn("bad exit value for " + target.toAbsolutePath());
                    }
                } catch (IllegalThreadStateException e) {
                    success = false;
                    LOG.warn("not exited");
                    process.destroyForcibly();
                }
            }
            return success;
        }

    }

    private class FileAdder implements Callable<Integer> {
        private final Path inputDir;
        private final int numThreads;
        private final ArrayBlockingQueue<Path> queue;
        private int added = 0;
        public FileAdder(Path inputDirectory, int numThreads, ArrayBlockingQueue<Path> queue) {
            this.inputDir = inputDirectory;
            this.numThreads = numThreads;
            this.queue = queue;
        }

        @Override
        public Integer call() throws Exception {
            Files.walkFileTree(inputDir, new DirWalker());
            for (int i = 0; i < numThreads; i++) {
                queue.add(POISON);
            }
            return 1;
        }

        private class DirWalker implements FileVisitor<Path> {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (maxFiles > -1 && added >= maxFiles) {
                    LOG.info("hit maxfiles; file crawler is stopping early");
                    return FileVisitResult.TERMINATE;
                }
                if (!file.getFileName().toString().contains("sas7bdat")) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    boolean offered = queue.offer(file, 10, TimeUnit.MINUTES);
                    if (offered) {
                        added++;
                        return FileVisitResult.CONTINUE;
                    } else {
                        LOG.error("couldn't add a file after 10 minutes!");
                        return FileVisitResult.TERMINATE;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return FileVisitResult.TERMINATE;
                }
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        }
    }
}
