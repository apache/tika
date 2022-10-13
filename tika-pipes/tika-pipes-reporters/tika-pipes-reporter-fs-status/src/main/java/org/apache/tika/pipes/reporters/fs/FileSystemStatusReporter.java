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
package org.apache.tika.pipes.reporters.fs;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesReporter;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.async.AsyncStatus;
import org.apache.tika.pipes.pipesiterator.TotalCountResult;

/**
 * This is intended to write summary statistics to disk
 * periodically.
 *
 *  As of the 2.5.0 release, this is ALPHA version.  There may be breaking changes
 *  in the future.
 *
 *  Because {@link AsyncStatus uses {@link java.time.Instant}, if you are deserializing
 *  with jackson-databind, you'll need to add jackson-datatype-jsr310. See
 *  the unit tests for how to deserialize AsyncStatus.
 *
 */
public class FileSystemStatusReporter extends PipesReporter
        implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemStatusReporter.class);

    ObjectMapper objectMapper;
    private Path statusFile;

    private long reportUpdateMillis = 1000;

    Thread reporterThread;
    private ConcurrentHashMap<PipesResult.STATUS, LongAdder> counts = new ConcurrentHashMap<>();
    private AsyncStatus asyncStatus = new AsyncStatus();

    private TotalCountResult totalCountResult = new TotalCountResult(0,
            TotalCountResult.STATUS.NOT_COMPLETED);
    @Field
    public void setStatusFile(String path) {
        this.statusFile = Paths.get(path);
    }

    @Field
    public void setReportUpdateMillis(long millis) {
        this.reportUpdateMillis = millis;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        reporterThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Thread.sleep(reportUpdateMillis);
                        report(AsyncStatus.ASYNC_STATUS.STARTED);
                    }
                } catch (InterruptedException e) {
                    //no op
                }

            }
        });
        reporterThread.setDaemon(true);
        reporterThread.start();
    }

    private synchronized void report(AsyncStatus.ASYNC_STATUS status) {
        Map<PipesResult.STATUS, Long> localCounts = new HashMap<>();
        counts.entrySet().forEach( e -> localCounts.put(e.getKey(), e.getValue().longValue()));
        asyncStatus.update(localCounts, totalCountResult, status);
        try (Writer writer = Files.newBufferedWriter(statusFile, StandardCharsets.UTF_8)) {
            objectMapper.writeValue(writer, asyncStatus);
        } catch (IOException e) {
            e.printStackTrace();
            LOG.warn("couldn't write report", e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        if (statusFile == null) {
            throw new TikaConfigException("must initialize 'statusFile'");
        }
        if (! Files.isDirectory(statusFile.getParent())) {
            try {
                Files.createDirectories(statusFile.getParent());
            } catch (IOException e) {
                throw new TikaConfigException("couldn't create directory for status file", e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        LOG.debug("finishing and writing last report");
        reporterThread.interrupt();
        try {
            reporterThread.join(1000);
        } catch (InterruptedException e) {
            //swallow
        }
        report(AsyncStatus.ASYNC_STATUS.COMPLETED);
    }

    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        counts.computeIfAbsent(result.getStatus(),
                k -> new LongAdder()).increment();
    }

    @Override
    public void report(TotalCountResult totalCountResult) {
        _report(totalCountResult);
    }

    private synchronized void _report(TotalCountResult totalCountResult) {
        this.totalCountResult = totalCountResult;
    }

    @Override
    public boolean supportsTotalCount() {
        return true;
    }
}
