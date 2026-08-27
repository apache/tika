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
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

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
 * Each line is written and flushed to the OS synchronously in {@link #report}, so
 * it survives the driver process dying (not a host crash; there is no fsync). A
 * write failure (disk full, etc.) throws from that {@link #report} and every later
 * one rather than dropping lines silently.
 */
public class FileSystemJsonlReporter extends PipesReporterBase {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemJsonlReporter.class);

    private record Line(String id, String status, String category, String message, long elapsedMs, String timestamp) {
    }

    private record ErrorLine(String error, String timestamp) {
    }

    public static FileSystemJsonlReporter build(ExtensionConfig pluginConfig) throws TikaConfigException, IOException {
        FileSystemJsonlReporterConfig config = FileSystemJsonlReporterConfig.load(pluginConfig.json());
        return new FileSystemJsonlReporter(pluginConfig, config);
    }

    private final FileSystemJsonlReporterConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final BufferedWriter writer;
    private IOException writerFailure;
    private boolean closed;

    public FileSystemJsonlReporter(ExtensionConfig pluginConfig, FileSystemJsonlReporterConfig config) throws TikaConfigException, IOException {
        super(pluginConfig, config.includes(), config.excludes());
        this.config = config;
        if (config.path() == null) {
            throw new TikaConfigException("must initialize 'path'");
        }
        this.writer = open(config);
    }

    private static BufferedWriter open(FileSystemJsonlReporterConfig config) throws TikaConfigException, IOException {
        Path path = config.path();
        if (Files.isDirectory(path)) {
            throw new TikaConfigException("'" + path + "' is a directory; 'path' must be a file");
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        StandardOpenOption[] options = switch (config.onExists()) {
            case EXCEPTION -> new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
            case APPEND -> new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND};
            case REPLACE -> new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING};
        };
        boolean needsNewline = config.onExists() == FileSystemJsonlReporterConfig.ON_EXISTS.APPEND && lacksTrailingNewline(path);
        if (needsNewline) {
            LOG.warn("'{}' ends in a partial line (a previous run died mid-write); terminating it at offset {}", path, Files.size(path));
        }
        try {
            // lone surrogates in ids/messages must not kill the log; default encoder would throw
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path, options),
                    StandardCharsets.UTF_8.newEncoder()
                            .onMalformedInput(CodingErrorAction.REPLACE)
                            .onUnmappableCharacter(CodingErrorAction.REPLACE)));
            if (needsNewline) {
                writer.write('\n');
            }
            return writer;
        } catch (FileAlreadyExistsException e) {
            throw new TikaConfigException("'" + path + "' already exists; set onExists to APPEND or REPLACE to reuse it", e);
        }
    }

    private static boolean lacksTrailingNewline(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) == 0) {
            return false;
        }
        try (SeekableByteChannel ch = Files.newByteChannel(path, StandardOpenOption.READ)) {
            ByteBuffer last = ByteBuffer.allocate(1);
            ch.position(ch.size() - 1);
            ch.read(last);
            return last.get(0) != '\n';
        } catch (AccessDeniedException e) {
            // write-only file: can't inspect, so don't require read permission just for this
            LOG.warn("can't read '{}' to check for a partial last line; appending as-is", path);
            return false;
        }
    }

    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        if (!accept(result.status())) {
            return;
        }
        write(new Line(t.getId(), result.status().name(), result.status().getCategory().name(),
                truncate(result.message()), elapsed, Instant.now().toString()), t.getId());
    }

    private String truncate(String msg) {
        int max = config.maxMessageLength();
        if (msg == null || msg.length() <= max) {
            return msg;
        }
        int cut = Character.isHighSurrogate(msg.charAt(max - 1)) ? max - 1 : max;
        return msg.substring(0, cut) + "...[truncated " + (msg.length() - cut) + " chars]";
    }

    private synchronized void write(Object line, String id) {
        if (writerFailure != null) {
            throw new IllegalStateException("jsonl reporter writer failed earlier; refusing to drop lines silently", writerFailure);
        }
        if (closed) {
            LOG.warn("jsonl reporter already closed; dropping report for {}", id);
            return;
        }
        try {
            // always \n, never the platform separator: jsonl is \n-delimited
            writer.write(mapper.writeValueAsString(line));
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            LOG.error("jsonl reporter failed writing {}", config.path(), e);
            writerFailure = e;
            throw new IllegalStateException("jsonl reporter failed writing " + config.path(), e);
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
    public synchronized void error(String msg) {
        // close() may never be called after this; get the line on disk now
        try {
            write(new ErrorLine(truncate(msg), Instant.now().toString()), "<error>");
        } catch (IllegalStateException e) {
            LOG.warn("couldn't record error in jsonl reporter", e);
        }
        try {
            closeWriter();
        } catch (IOException e) {
            LOG.warn("problem closing {}", config.path(), e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        closeWriter();
        if (writerFailure != null) {
            throw writerFailure;
        }
    }

    private void closeWriter() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        writer.close();
    }
}
