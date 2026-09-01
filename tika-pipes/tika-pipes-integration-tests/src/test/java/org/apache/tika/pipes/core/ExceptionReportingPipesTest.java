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
package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

/**
 * The config's exception-reporting policy must reach every message pipes hands a caller:
 * the container exception and the fetch/emit failures the forked worker reports, and the
 * results the parent fabricates when the fork never gets far enough to answer.
 */
public class ExceptionReportingPipesTest {

    private static final String FETCHER_NAME = "fsf";
    private static final String EMITTER_NAME = "fse";
    private static final String NPE_DOC = "mock-npe.xml";
    private static final String CLEAN_DOC = "mock_times.xml";
    private static final String MESSAGE = "secret null pointer message";

    private PipesClient init(Path tmp, String template, String... testDocs) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                template, tmp, tmp.resolve("input"), tmp.resolve("output"), false);
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testDocs);
        PipesConfig pipesConfig = PipesConfig.load(TikaJsonConfig.load(tikaConfigPath));
        return new PipesClient(pipesConfig, tikaConfigPath);
    }

    private static FetchEmitTuple tuple(String fetchKey) {
        return tuple(fetchKey, new EmitKey());
    }

    private static FetchEmitTuple tuple(String fetchKey, EmitKey emitKey) {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);
        return new FetchEmitTuple(fetchKey, new FetchKey(FETCHER_NAME, fetchKey), emitKey,
                new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
    }

    @Test
    public void containerExceptionRedacted(@TempDir Path tmp) throws Exception {
        try (PipesClient client = init(tmp, "tika-config-exception-reporting.json", NPE_DOC)) {
            PipesResult result = client.process(tuple(NPE_DOC));
            assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS_WITH_EXCEPTION, result.status());
            String trace = result.emitData().getMetadataList().get(0)
                    .get(TikaCoreProperties.CONTAINER_EXCEPTION);
            assertTrue(trace.contains("java.lang.NullPointerException"), trace);
            assertTrue(trace.contains("\tat "), trace);
            assertFalse(trace.contains(MESSAGE), trace);
            assertEquals(trace, result.emitData().getContainerStackTrace());
        }
    }

    @Test
    public void containerExceptionFullByDefault(@TempDir Path tmp) throws Exception {
        try (PipesClient client = init(tmp, "tika-config-basic.json", NPE_DOC)) {
            PipesResult result = client.process(tuple(NPE_DOC));
            assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS_WITH_EXCEPTION, result.status());
            String trace = result.emitData().getMetadataList().get(0)
                    .get(TikaCoreProperties.CONTAINER_EXCEPTION);
            assertTrue(trace.contains(MESSAGE), trace);
        }
    }

    @Test
    public void fetchExceptionRedacted(@TempDir Path tmp) throws Exception {
        try (PipesClient client = init(tmp, "tika-config-exception-reporting.json", NPE_DOC)) {
            PipesResult result = client.process(tuple("does-not-exist.xml"));
            assertEquals(PipesResult.RESULT_STATUS.FETCH_EXCEPTION, result.status());
            String msg = result.message();
            assertTrue(msg.contains("java.io.FileNotFoundException"), msg);
            assertTrue(msg.contains("\tat "), msg);
            // the fetcher's message names the missing path; the policy must strip it
            assertFalse(msg.contains("does-not-exist"), msg);
        }
    }

    @Test
    public void emitExceptionRedacted(@TempDir Path tmp) throws Exception {
        Path outputDir = tmp.resolve("output");
        try (PipesClient client = init(tmp, "tika-config-exception-reporting-emit-all.json",
                CLEAN_DOC)) {
            EmitKey emitKey = new EmitKey(EMITTER_NAME, CLEAN_DOC);
            assertEquals(PipesResult.RESULT_STATUS.EMIT_SUCCESS,
                    client.process(tuple(CLEAN_DOC, emitKey)).status());
            // onExists=EXCEPTION: the second emit of the same key fails
            PipesResult result = client.process(tuple(CLEAN_DOC, emitKey));
            assertEquals(PipesResult.RESULT_STATUS.EMIT_EXCEPTION, result.status());
            String msg = result.message();
            assertTrue(msg.contains("java.io.IOException"), msg);
            assertTrue(msg.contains("\tat "), msg);
            // the emitter's message names the absolute output path; the policy must strip it
            assertFalse(msg.contains(outputDir.toAbsolutePath().toString()), msg);
            assertFalse(msg.contains("onExists"), msg);
        }
    }

    /**
     * FAILED_TO_INITIALIZE is fabricated in the parent JVM, which has no request that could
     * carry the policy -- it must read it from the config, or the fork's startup trace
     * (config path, plugin roots) is returned verbatim.
     */
    @Test
    public void startupFailureRedacted(@TempDir Path tmp) throws Exception {
        try (PipesClient client = init(tmp,
                "tika-config-exception-reporting-startup-failure.json")) {
            FetchEmitTuple t = new FetchEmitTuple(NPE_DOC, new FetchKey("bad-fetcher", NPE_DOC),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
            PipesResult result = client.process(t);
            assertEquals(PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE, result.status());
            String msg = result.message();
            assertTrue(msg.contains("ServerInitializationException"), msg);
            assertTrue(msg.contains("\tat "), msg);
            // a MESSAGE_REDACTED trace carries no message text at all
            assertFalse(msg.contains("Exception:"), msg);
            assertFalse(msg.contains("non-existent-fetcher-plugin"), msg);
            assertFalse(msg.contains(tmp.toAbsolutePath().toString()), msg);
        }
    }
}
