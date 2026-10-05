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

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
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
import org.apache.tika.pipes.core.protocol.ForkBootstrap;

/**
 * A per-client fork presents the token it got on stdin; the parent turns away anything else.
 */
public class PipesForkTokenTest {

    private static final String TEST_DOC = "testOverlappingText.pdf";

    /** A local process that reaches the parent's port before the fork is turned away. */
    @Test
    public void perClientRejectsConnectionWithoutToken(@TempDir Path tmp) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, TEST_DOC);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        try (PerClientServerManager manager = new PerClientServerManager(
                pipesConfig, ForkBootstrap.toBytes(tikaJsonConfig), 0);
                PipesClient client = new PipesClient(pipesConfig, manager)) {
            manager.ensureRunning();
            // Connects while the fork's JVM is still starting, so it is first in the backlog.
            try (Socket stranger = new Socket()) {
                stranger.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),
                        manager.getPort()));
                stranger.getOutputStream().write(new byte[ForkBootstrap.TOKEN_LENGTH_BYTES]);
                stranger.getOutputStream().flush();

                PipesResult result = client.process(new FetchEmitTuple(TEST_DOC,
                        new FetchKey("fsf", TEST_DOC), new EmitKey(), new Metadata(),
                        new ParseContext(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
                assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS, result.status());

                stranger.setSoTimeout(30000);
                try (InputStream in = stranger.getInputStream()) {
                    assertEquals(-1, in.read(), "the parent must close the stranger's connection");
                }
            }
        }
    }
}
