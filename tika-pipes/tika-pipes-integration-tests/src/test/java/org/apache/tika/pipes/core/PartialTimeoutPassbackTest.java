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

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

/**
 * TIKA-4813 follow-up: EmitHandler relabels any SUCCESS-category result to
 * PARTIAL_TIMEOUT when the task deadline was reached, but must not relabel
 * EMIT_SUCCESS_PASSBACK. That status's emitData is a copy of content already sent to a
 * real Emitter (see EmitHandler.emit()'s passbackFilter branch); relabeling it would make
 * AsyncProcessor.FetchEmitWorker.shouldEmit() -- which treats PARTIAL_TIMEOUT as "please
 * batch-emit this" -- attempt to re-emit content that's already on disk.
 */
public class PartialTimeoutPassbackTest {

    private static final String FETCHER_NAME = "fsf";
    // mock-embedded.xml has 4 embedded documents to skip once the deadline is reached
    private static final String TEST_DOC_WITH_EMBEDDED = "mock-embedded.xml";

    @Test
    public void testDeadlineReachedDoesNotRelabelPassback(@TempDir Path tmp) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, TEST_DOC_WITH_EMBEDDED);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);
        // total=0 makes ParseTimeout deterministically "already exhausted", so every
        // embedded doc is skipped and the task deadline is reached immediately.
        parseContext.set(TimeoutLimits.class, new TimeoutLimits(0, 10000));
        parseContext.set(EmitStrategyConfig.class, new EmitStrategyConfig(EmitStrategy.EMIT_ALL));
        // Registered friendly name for MockPassbackFilter -- see PassbackFilterTest.
        parseContext.setJsonConfig("mock-passback-filter", "{}");

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                            new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                            new EmitKey("fse", "partial-timeout-passback"), new Metadata(), parseContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

            assertEquals(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PASSBACK, pipesResult.status(),
                    "a deadline-reached result must not relabel EMIT_SUCCESS_PASSBACK to " +
                            "PARTIAL_TIMEOUT -- doing so risks a double emit of already-sent content");
        }
    }
}
