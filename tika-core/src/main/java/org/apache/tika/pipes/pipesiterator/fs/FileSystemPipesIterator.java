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
package org.apache.tika.pipes.pipesiterator.fs;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.async.AsyncProcessor;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.PipesIterator;
import org.apache.tika.pipes.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.pipesiterator.TotalCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemPipesIterator extends PipesIterator
        implements TotalCounter, Initializable, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessor.class);

    private Path basePath;
    private boolean countTotal = false;

    private FileCountWorker fileCountWorker;

    public FileSystemPipesIterator() {}

    public FileSystemPipesIterator(Path basePath) {
        this.basePath = basePath;
    }

    @Field
    public void setBasePath(String basePath) {
        this.basePath = Paths.get(basePath);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        if (!Files.isDirectory(basePath)) {
            throw new IllegalArgumentException(
                    "\"basePath\" directory does not exist: " + basePath.toAbsolutePath());
        }

        try {
            Files.walkFileTree(basePath, new FSFileVisitor(getFetcherName(), getEmitterName()));
        } catch (IOException e) {
            Throwable cause = e.getCause();
            if (cause != null && cause instanceof TimeoutException) {
                throw (TimeoutException) cause;
            }
            throw e;
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        // these should all be fatal
        TikaConfig.mustNotBeEmpty("basePath", basePath);
        TikaConfig.mustNotBeEmpty("fetcherName", getFetcherName());
        TikaConfig.mustNotBeEmpty("emitterName", getFetcherName());
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        if (countTotal) {
            fileCountWorker = new FileCountWorker(basePath);
        }
    }

    @Field
    public void setCountTotal(boolean countTotal) {
        this.countTotal = countTotal;
    }

    @Override
    public void startTotalCount() {
        if (!countTotal) {
            return;
        }
        fileCountWorker.startTotalCount();
    }

    @Override
    public TotalCountResult getTotalCount() {
        if (!countTotal) {
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

        private final String fetcherName;
        private final String emitterName;

        private FSFileVisitor(String fetcherName, String emitterName) {
            this.fetcherName = fetcherName;
            this.emitterName = emitterName;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String relPath = basePath.relativize(file).toString();

            try {
                tryToAdd(
                        new FetchEmitTuple(
                                relPath,
                                new FetchKey(fetcherName, relPath),
                                new EmitKey(emitterName, relPath),
                                new Metadata(),
                                getHandlerConfig(),
                                getOnParseException()));
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
            totalCounterThread =
                    new Thread(
                            () -> {
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

        private class FSFileCounter implements FileVisitor<Path> {

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
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                count.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                return FileVisitResult.CONTINUE;
            }
        }
    }
}
