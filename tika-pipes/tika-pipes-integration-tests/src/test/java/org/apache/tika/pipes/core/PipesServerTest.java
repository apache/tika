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

import org.apache.tika.TikaTest;

public class PipesServerTest extends TikaTest {

    /**
     * This test is useful for stepping through the debugger on PipesServer
     * without having to attach the debugger to the forked process.
     *
     * @param tmp
     * @throws Exception
     */
    /*
    @Test
    public void testBasic(@TempDir Path tmp) throws Exception {
        String testDoc = "mock_times.xml";
        Path tikaConfig = PluginsTestHelper.getFileSystemFetcherConfig(tmp);
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testDoc);

        TikaLoader tikaLoader = TikaLoader.load(tikaConfig);
        PipesConfig pipesConfig = PipesConfig.load(tikaLoader.getConfig());
        PipesServer pipesServer = PipesServer.load(40, tikaConfig);

        FetchEmitTuple fetchEmitTuple = new FetchEmitTuple("id",
                new FetchKey("fsf", testDoc),
                new EmitKey("", ""));
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfig);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);
        Fetcher fetcher = FetcherManager.load(pluginManager, tikaJsonConfig).getFetcher();

                parseData = pipesServer.parseFromTuple(fetchEmitTuple, fetcher);
        assertEquals("5f3b924303e960ce35d7f705e91d3018dd110a9c3cef0546a91fe013d6dad6fd",
                parseData.metadataList.get(0).get("X-TIKA:digest:SHA-256"));
    }
    */
}
