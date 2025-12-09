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
package org.apache.tika.pipes.core.fetcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherNotFoundException;
import org.apache.tika.pipes.core.PluginsTestHelper;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.TikaPluginManager;

public class FetcherManagerTest {

    @Test
    public void testBasicLoad(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig);

        assertNotNull(fetcherManager);
        assertEquals(1, fetcherManager.getSupported().size());
        assertTrue(fetcherManager.getSupported().contains("fsf"));
    }

    @Test
    public void testLazyInstantiation(@TempDir Path tmpDir) throws Exception {
        // Create config with multiple fetchers
        String configJson = "{\n" +
                "  \"fetchers\": {\n" +
                "    \"file-system-fetcher\": {\n" +
                "      \"fsf1\": {\n" +
                "        \"basePath\": \"" + tmpDir.resolve("path1").toString().replace("\\", "/") + "\"\n" +
                "      },\n" +
                "      \"fsf2\": {\n" +
                "        \"basePath\": \"" + tmpDir.resolve("path2").toString().replace("\\", "/") + "\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"plugin-roots\": \"target/plugins\"\n" +
                "}";

        Path configPath = tmpDir.resolve("config.json");
        Files.writeString(configPath, configJson, StandardCharsets.UTF_8);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig);

        // After load, both fetchers should be in supported list but not instantiated yet
        assertEquals(2, fetcherManager.getSupported().size());

        // Request only fsf1 - only it should be instantiated
        Fetcher fetcher1 = fetcherManager.getFetcher("fsf1");
        assertNotNull(fetcher1);
        assertEquals("fsf1", fetcher1.getExtensionConfig().id());

        // fsf2 has not been requested yet - verify it exists in config
        assertTrue(fetcherManager.getSupported().contains("fsf2"));

        // Now request fsf2
        Fetcher fetcher2 = fetcherManager.getFetcher("fsf2");
        assertNotNull(fetcher2);
        assertEquals("fsf2", fetcher2.getExtensionConfig().id());
    }

    @Test
    public void testCaching(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig);

        // Get the same fetcher multiple times
        Fetcher fetcher1 = fetcherManager.getFetcher("fsf");
        Fetcher fetcher2 = fetcherManager.getFetcher("fsf");
        Fetcher fetcher3 = fetcherManager.getFetcher("fsf");

        // Should be the exact same instance (reference equality)
        assertSame(fetcher1, fetcher2);
        assertSame(fetcher2, fetcher3);
    }

    @Test
    public void testThreadSafety(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Future<Fetcher>> futures = new ArrayList<>();

        // Start multiple threads that all request the same fetcher simultaneously
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    // All threads try to get the fetcher at once
                    return fetcherManager.getFetcher("fsf");
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        // Start all threads at once
        startLatch.countDown();

        // Wait for all threads to complete
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

        // Collect all fetchers
        List<Fetcher> fetchers = new ArrayList<>();
        for (Future<Fetcher> future : futures) {
            fetchers.add(future.get());
        }

        executor.shutdown();

        // All threads should have gotten the same instance
        Fetcher first = fetchers.get(0);
        for (Fetcher fetcher : fetchers) {
            assertSame(first, fetcher, "All threads should get the same fetcher instance");
        }
    }

    @Test
    public void testUnknownFetcherId(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig);

        FetcherNotFoundException exception = assertThrows(FetcherNotFoundException.class, () -> {
            fetcherManager.getFetcher("non-existent-fetcher");
        });

        assertTrue(exception.getMessage().contains("non-existent-fetcher"));
        assertTrue(exception.getMessage().contains("Available:"));
    }

    @Test
    public void testUnknownFetcherType(@TempDir Path tmpDir) throws Exception {
        String configJson = "{\n" +
                "  \"fetchers\": {\n" +
                "    \"non-existent-fetcher-type\": {\n" +
                "      \"fetcher1\": {\n" +
                "        \"someProp\": \"value\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"plugin-roots\": \"target/plugins\"\n" +
                "}";

        Path configPath = tmpDir.resolve("config.json");
        Files.writeString(configPath, configJson, StandardCharsets.UTF_8);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Should fail during load (early validation)
        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            FetcherManager.load(pluginManager, tikaJsonConfig);
        });

        assertTrue(exception.getMessage().contains("Unknown fetcher type"));
        assertTrue(exception.getMessage().contains("non-existent-fetcher-type"));
    }

    @Test
    public void testDuplicateFetcherId(@TempDir Path tmpDir) throws Exception {
        String configJson = "{\n" +
                "  \"fetchers\": {\n" +
                "    \"file-system-fetcher\": {\n" +
                "      \"fsf1\": {\n" +
                "        \"basePath\": \"" + tmpDir.resolve("path1").toString().replace("\\", "/") + "\"\n" +
                "      },\n" +
                "      \"fsf1\": {\n" +
                "        \"basePath\": \"" + tmpDir.resolve("path2").toString().replace("\\", "/") + "\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"plugin-roots\": \"target/plugins\"\n" +
                "}";

        Path configPath = tmpDir.resolve("config.json");
        Files.writeString(configPath, configJson, StandardCharsets.UTF_8);

        // PolymorphicObjectMapperFactory has FAIL_ON_READING_DUP_TREE_KEY enabled
        // so duplicate keys are caught during JSON parsing
        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            TikaJsonConfig.load(configPath);
        });

        assertTrue(exception.getMessage().contains("Failed to parse JSON") ||
                exception.getCause().getMessage().contains("Duplicate field"));
    }

    @Test
    public void testGetSingleFetcher(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig);

        // When only one fetcher exists, no-arg getFetcher() should work
        Fetcher fetcher = fetcherManager.getFetcher();
        assertNotNull(fetcher);
        assertEquals("fsf", fetcher.getExtensionConfig().id());
    }

    @Test
    public void testGetSingleFetcherWithMultipleConfigured(@TempDir Path tmpDir) throws Exception {
        String configJson = "{\n" +
                "  \"fetchers\": {\n" +
                "    \"file-system-fetcher\": {\n" +
                "      \"fsf1\": {\n" +
                "        \"basePath\": \"" + tmpDir.resolve("path1").toString().replace("\\", "/") + "\"\n" +
                "      },\n" +
                "      \"fsf2\": {\n" +
                "        \"basePath\": \"" + tmpDir.resolve("path2").toString().replace("\\", "/") + "\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"plugin-roots\": \"target/plugins\"\n" +
                "}";

        Path configPath = tmpDir.resolve("config.json");
        Files.writeString(configPath, configJson, StandardCharsets.UTF_8);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig);

        // When multiple fetchers exist, no-arg getFetcher() should fail
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fetcherManager.getFetcher();
        });

        assertTrue(exception.getMessage().contains("requires exactly 1"));
    }

    @Test
    public void testSaveFetcher(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Load with runtime modifications enabled
        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig, true);

        // Initially only fsf exists
        assertEquals(1, fetcherManager.getSupported().size());

        // Dynamically add a new fetcher configuration
        String newConfigJson = "{\"basePath\": \"" + tmpDir.resolve("path2").toString().replace("\\", "/") + "\"}";
        ExtensionConfig newConfig = new ExtensionConfig("fsf2", "file-system-fetcher", newConfigJson);

        fetcherManager.saveFetcher(newConfig);

        // Now both should be available
        assertEquals(2, fetcherManager.getSupported().size());
        assertTrue(fetcherManager.getSupported().contains("fsf"));
        assertTrue(fetcherManager.getSupported().contains("fsf2"));

        // Fetcher should be lazily instantiated when requested
        Fetcher fetcher2 = fetcherManager.getFetcher("fsf2");
        assertNotNull(fetcher2);
        assertEquals("fsf2", fetcher2.getExtensionConfig().id());
    }

    @Test
    public void testSaveFetcherDuplicate(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig, true);

        // Try to add a fetcher with the same ID as existing one
        String newConfigJson = "{\"basePath\": \"" + tmpDir.resolve("path2").toString().replace("\\", "/") + "\"}";
        ExtensionConfig duplicateConfig = new ExtensionConfig("fsf", "file-system-fetcher", newConfigJson);

        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            fetcherManager.saveFetcher(duplicateConfig);
        });

        assertTrue(exception.getMessage().contains("already exists"));
        assertTrue(exception.getMessage().contains("fsf"));
    }

    @Test
    public void testSaveFetcherUnknownType(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig, true);

        // Try to add a fetcher with unknown type
        ExtensionConfig unknownTypeConfig = new ExtensionConfig("fetcher2", "unknown-fetcher-type", "{}");

        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            fetcherManager.saveFetcher(unknownTypeConfig);
        });

        assertTrue(exception.getMessage().contains("Unknown fetcher type"));
        assertTrue(exception.getMessage().contains("unknown-fetcher-type"));
    }

    @Test
    public void testSaveFetcherNull(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig, true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fetcherManager.saveFetcher(null);
        });

        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    public void testSaveFetcherLazyInstantiation(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig, true);

        // Add multiple fetchers
        for (int i = 2; i <= 5; i++) {
            String configJson = "{\"basePath\": \"" + tmpDir.resolve("path" + i).toString().replace("\\", "/") + "\"}";
            ExtensionConfig config2 = new ExtensionConfig("fsf" + i, "file-system-fetcher", configJson);
            fetcherManager.saveFetcher(config2);
        }

        // All 5 should be in supported list
        assertEquals(5, fetcherManager.getSupported().size());

        // Request only fsf3 - only it should be instantiated
        Fetcher fetcher3 = fetcherManager.getFetcher("fsf3");
        assertNotNull(fetcher3);
        assertEquals("fsf3", fetcher3.getExtensionConfig().id());

        // Others are still available but not instantiated yet
        assertTrue(fetcherManager.getSupported().contains("fsf2"));
        assertTrue(fetcherManager.getSupported().contains("fsf4"));
        assertTrue(fetcherManager.getSupported().contains("fsf5"));
    }

    @Test
    public void testSaveFetcherNotAllowed(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Load with default (runtime modifications disabled)
        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig);

        // Try to add a fetcher - should fail
        String newConfigJson = "{\"basePath\": \"" + tmpDir.resolve("path2").toString().replace("\\", "/") + "\"}";
        ExtensionConfig newConfig = new ExtensionConfig("fsf2", "file-system-fetcher", newConfigJson);

        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            fetcherManager.saveFetcher(newConfig);
        });

        assertTrue(exception.getMessage().contains("Runtime modifications are not allowed"));
        assertTrue(exception.getMessage().contains("allowRuntimeModifications=true"));
    }

    @Test
    public void testSaveFetcherNotAllowedExplicit(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Load with explicit false
        FetcherManager fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig, false);

        // Try to add a fetcher - should fail
        String newConfigJson = "{\"basePath\": \"" + tmpDir.resolve("path2").toString().replace("\\", "/") + "\"}";
        ExtensionConfig newConfig = new ExtensionConfig("fsf2", "file-system-fetcher", newConfigJson);

        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            fetcherManager.saveFetcher(newConfig);
        });

        assertTrue(exception.getMessage().contains("Runtime modifications are not allowed"));
    }
}
