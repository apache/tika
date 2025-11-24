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

import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.plugins.TikaConfigs;
import org.apache.tika.plugins.TikaPluginManager;

public class PluginManagerTest {

    @Test
    public void testBasic(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaConfigs tikaConfigs = TikaConfigs.load(config);
        TikaPluginManager tikaPluginManager = TikaPluginManager.load(tikaConfigs);
        FetcherManager fetcherManager = FetcherManager.load(tikaPluginManager, tikaConfigs);
        assertEquals(1, fetcherManager.getSupported().size());
        Fetcher f = fetcherManager.getFetcher();
        assertEquals("fsf", f.getExtensionConfig().id());
        assertEquals("org.apache.tika.pipes.fetcher.fs.FileSystemFetcher", f.getClass().getName());
    }
}
