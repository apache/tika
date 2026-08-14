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

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

/**
 * Client selection is LIFO: sequential (non-concurrent) parses reuse the warm
 * client instead of round-robining every fork awake. FIFO selection would
 * cold-start one fork per request here.
 */
public class PipesParserWarmClientTest {

    @Test
    public void sequentialParsesReuseTheWarmClient(@TempDir Path tmp) throws Exception {
        String testDoc = "testOverlappingText.pdf";
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testDoc);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        pipesConfig.setNumClients(3);

        try (PipesParser parser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            for (int i = 0; i < 4; i++) {
                PipesResult result = parser.parse(
                        new FetchEmitTuple(testDoc + "-" + i, new FetchKey("fsf", testDoc),
                                new EmitKey(), new Metadata(), new ParseContext(),
                                FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
                assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS, result.status(),
                        "parse " + i + ": " + result.message());
            }
            assertEquals(1, parser.startedServerCount(),
                    "sequential parses should stay on one warm fork");
        }
    }
}
