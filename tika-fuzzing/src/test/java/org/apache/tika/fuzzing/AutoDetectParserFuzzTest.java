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
package org.apache.tika.fuzzing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.code_intelligence.jazzer.junit.FuzzTest;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

/**
 * AutoDetectParser over the three ways a caller hands Tika bytes. Findings are Errors
 * (OOM, stack overflow), hangs, and anything not a TikaException/IOException/SAXException.
 * With {@code -Dtika.fuzz.strict=true}, a parser RuntimeException that CompositeParser
 * wrapped in a TikaException is a finding too. A parse longer than {@code tika.fuzz.timeout}
 * seconds (default 60) is reported with the stuck thread's stack.
 */
class AutoDetectParserFuzzTest {

    private static final Parser PARSER = new RecursiveParserWrapper(new AutoDetectParser());
    private static final boolean STRICT = Boolean.getBoolean("tika.fuzz.strict");
    private static final long TIMEOUT_SECONDS = Long.getLong("tika.fuzz.timeout", 60);

    private static ExecutorService worker;
    private static volatile Thread workerThread;

    @FuzzTest
    void bytes(byte[] data) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            parse(tis);
        }
    }

    @FuzzTest
    void stream(byte[] data) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            parse(tis);
        }
    }

    @FuzzTest
    void path(byte[] data) throws Exception {
        Path tmp = Files.createTempFile("tika-fuzz-", ".bin");
        try {
            Files.write(tmp, data);
            try (TikaInputStream tis = TikaInputStream.get(tmp)) {
                parse(tis);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // A JUnit timeout would also cap a whole fuzzing session, so each parse is timed here.
    static synchronized void parse(TikaInputStream tis) throws Exception {
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "tika-fuzz-parse");
                t.setDaemon(true);
                workerThread = t;
                return t;
            });
        }
        Future<Void> f = worker.submit(() -> {
            parseInline(tis);
            return null;
        });
        try {
            f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // a spinning parse ignores interrupts; abandon its thread
            Thread stuck = workerThread;
            worker = null;
            AssertionError hang = new AssertionError(
                    "parse did not finish in " + TIMEOUT_SECONDS + " s");
            hang.setStackTrace(stuck.getStackTrace());
            throw hang;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error err) {
                throw err;
            }
            throw (Exception) e.getCause();
        }
    }

    private static void parseInline(TikaInputStream tis) throws Exception {
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        try {
            PARSER.parse(tis, handler, new Metadata(), new ParseContext());
        } catch (TikaException e) {
            if (STRICT && e.getCause() instanceof RuntimeException) {
                throw e;
            }
        } catch (IOException | SAXException e) {
            // expected on malformed input
        }
    }
}
