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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.pipes.core.protocol.ForkBootstrap;
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.protocol.PipesMessageType;
import org.apache.tika.pipes.core.server.PipesServer;

/**
 * An idle fork whose parent drops the connection without SHUT_DOWN (the parent was killed)
 * exits as parent-gone, not as a crash.
 */
public class PipesServerParentGoneTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void idleForkExitsParentGoneWhenConnectionDrops(boolean reset, @TempDir Path tmp)
            throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                tmp, tmp.resolve("input"), tmp.resolve("output"));
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        try (PerClientServerManager manager = new PerClientServerManager(
                pipesConfig, ForkBootstrap.toBytes(tikaJsonConfig), 0)) {
            manager.ensureRunning();
            Process fork = manager.getProcess();
            Socket socket = manager.connect((int) pipesConfig.getSocketTimeoutMillis());
            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()));
            assertEquals(PipesMessageType.READY, PipesMessage.read(in).type());
            if (reset) {
                // RST: the fork reads "Connection reset", as when the parent JVM is killed
                socket.setSoLinger(true, 0);
            }
            socket.close();

            assertTrue(fork.waitFor(30, TimeUnit.SECONDS), "fork did not exit");
            assertEquals(PipesServer.PARENT_GONE_EXIT_CODE, fork.exitValue());
        }
    }
}
