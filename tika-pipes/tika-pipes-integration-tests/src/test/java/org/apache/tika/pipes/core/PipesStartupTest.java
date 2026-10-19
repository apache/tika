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
import static org.junit.jupiter.api.Assertions.assertThrows;

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

/** {@link PipesParser#start()} brings the forks up before the first parse. */
public class PipesStartupTest {

    private static final String TEST_DOC = "testOverlappingText.pdf";

    private static Path config(Path tmp) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, TEST_DOC);
        return tikaConfigPath;
    }

    private static PipesResult parse(PipesParser parser) throws Exception {
        return parser.parse(new FetchEmitTuple(TEST_DOC, new FetchKey("fsf", TEST_DOC),
                new EmitKey(), new Metadata(), new ParseContext(),
                FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
    }

    @Test
    public void startBringsUpEveryFork(@TempDir Path tmp) throws Exception {
        Path tikaConfigPath = config(tmp);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        pipesConfig.setNumClients(2);
        try (PipesParser parser = PipesParser.load(tikaJsonConfig, pipesConfig)) {
            assertEquals(0, parser.startedServerCount());
            parser.start();
            assertEquals(2, parser.startedServerCount());
            assertEquals(2, parser.getIdleClientCount(), "start() must return every client");
            assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS, parse(parser).status());
        }
    }

    @Test
    public void startInSharedMode(@TempDir Path tmp) throws Exception {
        Path tikaConfigPath = config(tmp);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        pipesConfig.setNumClients(2);
        pipesConfig.setUseSharedServer(true);
        try (PipesParser parser = PipesParser.load(tikaJsonConfig, pipesConfig)) {
            parser.start();
            assertEquals(1, parser.startedServerCount());
            assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS, parse(parser).status());
        }
    }

    @Test
    public void startFailsOnBadConfig(@TempDir Path tmp) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-bad-class.json", tmp);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        try (PipesParser parser = PipesParser.load(tikaJsonConfig, pipesConfig)) {
            assertThrows(ServerInitializationException.class, parser::start);
            assertEquals(pipesConfig.getNumClients(), parser.getIdleClientCount(),
                    "a failed start() must still return every client");
        }
    }
}
