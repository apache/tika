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
package org.apache.tika.pipes.iterator.fs;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.api.pipesiterator.TotalCounter;
import org.apache.tika.pipes.pipesiterator.PipesIteratorBase;
import org.apache.tika.plugins.ExtensionConfig;

public class FileSystemPipesIterator extends PipesIteratorBase implements TotalCounter, Closeable {

    public static FileSystemPipesIterator build(ExtensionConfig pluginConfig) throws TikaConfigException, IOException {
        FileSystemPipesIterator pipesIterator = new FileSystemPipesIterator(pluginConfig);
        pipesIterator.configure();
        return pipesIterator;
    }

    private FileSystemPipesIteratorConfig config;


    private void configure() throws IOException, TikaConfigException {
        config = FileSystemPipesIteratorConfig.load(pluginConfig.json());
        checkConfig(config);
        if (config.isCountTotal()) {
            fileCountWorker = new FileCountWorker(config.getBasePath());
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemPipesIterator.class);

    private FileCountWorker fileCountWorker;

    private FileSystemPipesIterator(ExtensionConfig pluginConfig) {
        super(pluginConfig);
    }


    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        if (!Files.isDirectory(config.getBasePath())) {
            throw new IllegalArgumentException(
                    "\"basePath\" directory does not exist: " + config
                            .getBasePath().toAbsolutePath());
        }
        PipesIteratorBaseConfig config = this.config.getBaseConfig();
        try {
            Files.walkFileTree(this.config.getBasePath(), new FSFileVisitor(config.fetcherId(), config.emitterId()));
        } catch (IOException e) {
            Throwable cause = e.getCause();
            if (cause != null && cause instanceof TimeoutException) {
                throw (TimeoutException) cause;
            }
            throw e;
        }
    }

    public void checkConfig(FileSystemPipesIteratorConfig config)
            throws TikaConfigException {
        //these should all be fatal
        TikaConfig.mustNotBeEmpty("basePath", config.getBasePath());
    }


    @Override
    public void startTotalCount() {
        if (!config.isCountTotal()) {
            return;
        }
        fileCountWorker.startTotalCount();
    }

    @Override
    public TotalCountResult getTotalCount() {
        if (!config.isCountTotal()) {
            return TotalCountResult.UNSUPPORTED;
        }
        return fileCountWorker.getTotalCount();
    }

    @Override
    public void close() throws IOException {
        if (fileCountWorker != null) {
            fileCountWorker.close();
        }
    }

    private class FSFileVisitor implements FileVisitor<Path> {

        private final String fetcherId;
        private final String emitterId;

        private FSFileVisitor(String fetcherId, String emitterId) {
            this.fetcherId = fetcherId;
            this.emitterId = emitterId;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String relPath = config
                    .getBasePath().relativize(file).toString();
            PipesIteratorBaseConfig config = FileSystemPipesIterator.this.config.getBaseConfig();
            try {
                ParseContext parseContext = new ParseContext();
                parseContext.set(HandlerConfig.class, config.handlerConfig());
                tryToAdd(new FetchEmitTuple(relPath, new FetchKey(fetcherId, relPath),
                        new EmitKey(emitterId, relPath), new Metadata(), parseContext,
                        config.onParseException()));
            } catch (TimeoutException e) {
                throw new IOException(e);
            } catch (InterruptedException e) {
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
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


    private static class FileCountWorker implements TotalCounter, Closeable {

        private Thread totalCounterThread;

        private final AtomicLong totalCount = new AtomicLong(0);
        private TotalCountResult.STATUS status;
        private TotalCountResult finalResult;
        private final Path basePath;

        public FileCountWorker(Path basePath) {
            this.basePath = basePath;
            this.status = TotalCountResult.STATUS.NOT_COMPLETED;
        }

        @Override
        public void startTotalCount() {
            totalCounterThread = new Thread(() -> {
                try {
                    Files.walkFileTree(basePath, new FSFileCounter(totalCount));
                    status = TotalCountResult.STATUS.COMPLETED;
                    finalResult = new TotalCountResult(totalCount.get(), status);
                } catch (IOException e) {
                    LOG.warn("problem counting files", e);
                    status = TotalCountResult.STATUS.EXCEPTION;
                    finalResult = new TotalCountResult(totalCount.get(), status);
                }
            });
            totalCounterThread.setDaemon(true);
            totalCounterThread.start();
        }

        @Override
        public TotalCountResult getTotalCount() {
            if (finalResult != null) {
                return finalResult;
            }
            return new TotalCountResult(totalCount.get(), status);
        }

        @Override
        public void close() throws IOException {
            totalCounterThread.interrupt();
        }

        private static class FSFileCounter implements FileVisitor<Path> {

            private final AtomicLong count;
            private FSFileCounter(AtomicLong count) {
                this.count = count;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                count.incrementAndGet();
                return FileVisitResult.CONTINUE;
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
