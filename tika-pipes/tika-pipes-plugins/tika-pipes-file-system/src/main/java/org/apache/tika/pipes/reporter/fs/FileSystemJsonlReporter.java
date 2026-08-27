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
package org.apache.tika.pipes.reporter.fs;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.reporters.PipesReporterBase;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.ExceptionUtils;

/**
 * Append-only per-document audit log: one JSON object per line for every result
 * accepted by the includes/excludes filter. The line's {@code id} is the
 * {@link FetchEmitTuple#getId()} verbatim, so consumers join on it.
 * <p>
 * A single writer thread drains a bounded queue and flushes whenever the queue
 * runs dry, so a crash line is on disk within moments of being reported. A
 * writer failure (disk full, etc.) fails the next {@link #report} rather than
 * silently dropping lines.
 */
public class FileSystemJsonlReporter extends PipesReporterBase {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemJsonlReporter.class);

    private static final int QUEUE_SIZE = 10_000;
    private static final long MAX_OFFER_WAIT_MS = 60_000;
    private static final long CLOSE_WAIT_MS = 60_000;
    private static final Line END = new Line(null, null, null, -1, null);

    public record Line(String id, String status, String message, long elapsedMs, String ts) {
    }

    private record ErrorLine(String error, String ts) {
    }

    public static FileSystemJsonlReporter build(ExtensionConfig pluginConfig) throws TikaConfigException, IOException {
        FileSystemJsonlReporterConfig config = FileSystemJsonlReporterConfig.load(pluginConfig.json());
        return new FileSystemJsonlReporter(pluginConfig, config);
    }

    private final FileSystemJsonlReporterConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ArrayBlockingQueue<Object> queue = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private final Thread writerThread;
    private final BufferedWriter writer;
    private volatile IOException writerFailure;
    private volatile boolean closed;

    public FileSystemJsonlReporter(ExtensionConfig pluginConfig, FileSystemJsonlReporterConfig config) throws TikaConfigException, IOException {
        super(pluginConfig, config.includes(), config.excludes());
        this.config = config;
        if (config.path() == null) {
            throw new TikaConfigException("must initialize 'path'");
        }
        this.writer = open(config);
        this.writerThread = new Thread(this::drain, "tika-jsonl-reporter");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private static BufferedWriter open(FileSystemJsonlReporterConfig config) throws TikaConfigException, IOException {
        Path path = config.path();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        StandardOpenOption[] options = switch (config.onExists()) {
            case EXCEPTION -> new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
            case APPEND -> new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND};
            case REPLACE -> new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
        };
        try {
            return Files.newBufferedWriter(path, StandardCharsets.UTF_8, options);
        } catch (FileAlreadyExistsException e) {
            throw new TikaConfigException("'" + path + "' already exists; set onExists to APPEND or REPLACE to reuse it", e);
        }
    }

    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        if (!accept(result.status())) {
            return;
        }
        enqueue(new Line(t.getId(), result.status().name(), truncate(result.message()), elapsed, Instant.now().toString()));
    }

    private String truncate(String msg) {
        if (msg == null || msg.length() <= config.maxMessageLength()) {
            return msg;
        }
        return msg.substring(0, config.maxMessageLength()) + "...[truncated " + (msg.length() - config.maxMessageLength()) + " chars]";
    }

    private void enqueue(Object line) {
        if (writerFailure != null) {
            throw new IllegalStateException("jsonl reporter writer failed; refusing to drop lines silently", writerFailure);
        }
        if (closed) {
            throw new IllegalStateException("jsonl reporter is closed");
        }
        try {
            if (!queue.offer(line, MAX_OFFER_WAIT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("jsonl reporter queue full for " + MAX_OFFER_WAIT_MS + " ms");
            }
        } catch (InterruptedException e) {
            LOG.warn("interrupted before queuing report for {}; line dropped", line);
            Thread.currentThread().interrupt();
        }
    }

    private void drain() {
        try {
            while (true) {
                Object line = queue.take();
                if (line == END) {
                    return;
                }
                writer.write(mapper.writeValueAsString(line));
                writer.newLine();
                if (queue.isEmpty()) {
                    writer.flush();
                }
            }
        } catch (IOException e) {
            LOG.error("jsonl reporter failed writing {}", config.path(), e);
            writerFailure = e;
        } catch (InterruptedException e) {
            //fall through to close
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                LOG.warn("problem closing {}", config.path(), e);
            }
        }
    }

    @Override
    public void report(TotalCountResult totalCountResult) {
        //no-op
    }

    @Override
    public boolean supportsTotalCount() {
        return false;
    }

    @Override
    public void error(Throwable t) {
        error(ExceptionUtils.getStackTrace(t));
    }

    @Override
    public void error(String msg) {
        // close() may never be called after this; get the line on disk now
        try {
            enqueue(new ErrorLine(truncate(msg), Instant.now().toString()));
        } catch (IllegalStateException e) {
            LOG.warn("couldn't record error in jsonl reporter", e);
        }
        finish();
    }

    @Override
    public void close() throws IOException {
        finish();
        if (writerFailure != null) {
            throw writerFailure;
        }
    }

    private void finish() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (!queue.offer(END, CLOSE_WAIT_MS, TimeUnit.MILLISECONDS)) {
                LOG.warn("jsonl reporter queue never drained; interrupting writer");
                writerThread.interrupt();
            }
            writerThread.join(CLOSE_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
