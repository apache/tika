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
package org.apache.tika.pipes.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.extractor.UnpackHandler;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.extractor.FrictionlessUnpackHandler;
import org.apache.tika.pipes.core.extractor.TempFileUnpackHandler;
import org.apache.tika.pipes.core.extractor.UnpackConfig;

/** The unpack handler's temp dir is created before the fetch and must not outlive a failed task. */
public class PipesWorkerUnpackCleanupTest {

    private static final PipesResult FETCH_FAILED =
            new PipesResult(PipesResult.RESULT_STATUS.FETCH_EXCEPTION, "no such file");
    private static final PipesResult PARSE_FAILED =
            new PipesResult(PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH, "parse failed");

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFetchFailure(boolean frictionless) throws Exception {
        ParseContext context = unpackContext(frictionless);
        FetchHandler fetchHandler = new FetchHandler(null) {
            @Override
            public TisOrResult fetch(FetchEmitTuple t, Metadata metadata, ParseContext ctx) {
                return new TisOrResult(null, FETCH_FAILED);
            }
        };
        PipesResult result = worker(context, fetchHandler, null).call();
        assertEquals(PipesResult.RESULT_STATUS.FETCH_EXCEPTION, result.status());
        assertTempDirGone(context, frictionless);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testParseFailure(boolean frictionless) throws Exception {
        ParseContext context = unpackContext(frictionless);
        FetchHandler fetchHandler = new FetchHandler(null) {
            @Override
            public TisOrResult fetch(FetchEmitTuple t, Metadata metadata, ParseContext ctx) {
                return new TisOrResult(TikaInputStream.get(new byte[0]), null);
            }
        };
        ParseHandler parseHandler = new ParseHandler(null, null, null, null, null, null,
                ParseMode.UNPACK) {
            @Override
            PipesWorker.ParseDataOrPipesResult parseWithStream(FetchEmitTuple t,
                    TikaInputStream stream, Metadata metadata, ParseContext ctx) {
                return new PipesWorker.ParseDataOrPipesResult(null, PARSE_FAILED);
            }
        };
        PipesResult result = worker(context, fetchHandler, parseHandler).call();
        assertEquals(PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH, result.status());
        assertTempDirGone(context, frictionless);
    }

    private static ParseContext unpackContext(boolean frictionless) {
        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter("fse");
        if (!frictionless) {
            unpackConfig.setOutputFormat(UnpackConfig.OUTPUT_FORMAT.REGULAR);
            unpackConfig.setZipEmbeddedFiles(true);
        }
        ParseContext context = new ParseContext();
        context.set(ParseMode.class, ParseMode.UNPACK);
        context.set(UnpackConfig.class, unpackConfig);
        return context;
    }

    private static PipesWorker worker(ParseContext context, FetchHandler fetchHandler,
                                      ParseHandler parseHandler) {
        FetchEmitTuple tuple = new FetchEmitTuple("id", new FetchKey("fsf", "missing.txt"),
                new EmitKey("fse", "missing.txt"), new Metadata(), context,
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        return new PipesWorker(tuple, context, null, null, fetchHandler, parseHandler, null,
                null, ParseMode.UNPACK);
    }

    private static void assertTempDirGone(ParseContext context, boolean frictionless) {
        UnpackHandler handler = context.get(UnpackHandler.class);
        assertNotNull(handler);
        Path dir = frictionless ? ((FrictionlessUnpackHandler) handler).getTempDirectory()
                : ((TempFileUnpackHandler) handler).getTempDirectory();
        assertFalse(Files.exists(dir), dir.toString());
    }
}
