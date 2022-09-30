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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * This is intended to write summary statistics to disk
 * periodically.
 *
 *  As of the 2.5.0 release, this is ALPHA version.  There may be breaking changes
 *  in the future.
 */
public class FileSystemStatusReporter extends PipesReporter
        implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemStatusReporter.class);

    ObjectMapper objectMapper = new ObjectMapper();
    private Path statusFile;

    private long reportUpdateMillis = 1000;

    Thread reporterThread;
    private ConcurrentHashMap<PipesResult.STATUS, LongAdder> counts = new ConcurrentHashMap<>();

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
        reporterThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Thread.sleep(reportUpdateMillis);
                        report(statusFile, objectMapper, counts);
                    }
                } catch (InterruptedException e) {
                    //no op
                }

            }
        });
        reporterThread.start();
    }

    private static void report(Path statusFile, ObjectMapper objectMapper,
                               ConcurrentHashMap<PipesResult.STATUS, LongAdder> counts) {
        try (Writer writer = Files.newBufferedWriter(statusFile, StandardCharsets.UTF_8)) {
            objectMapper.writeValue(writer, counts);
        } catch (IOException e) {
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
        report(statusFile, objectMapper, counts);
    }

    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        counts.computeIfAbsent(result.getStatus(),
                k -> new LongAdder()).increment();
    }

}
