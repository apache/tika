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
package org.apache.tika.pipes.core.emitter;

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
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.emitter.EmitterNotFoundException;
import org.apache.tika.pipes.core.PluginsTestHelper;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.TikaPluginManager;

public class EmitterManagerTest {

    @Test
    public void testBasicLoad(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig);

        assertNotNull(emitterManager);
        assertEquals(1, emitterManager.getSupported().size());
        assertTrue(emitterManager.getSupported().contains("fse"));
    }

    @Test
    public void testEmptyConfig(@TempDir Path tmpDir) throws Exception {
        // Create config with no emitters
        String configJson = """
                {
                  "plugin-roots": "target/plugins"
                }
                """;

        Path configPath = tmpDir.resolve("config.json");
        Files.writeString(configPath, configJson, StandardCharsets.UTF_8);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig);

        assertNotNull(emitterManager);
        assertEquals(0, emitterManager.getSupported().size());

        // Try to get an emitter when none are configured
        EmitterNotFoundException exception = assertThrows(EmitterNotFoundException.class, () -> {
            emitterManager.getEmitter("any-id");
        });

        assertTrue(exception.getMessage().contains("any-id"));
        assertTrue(exception.getMessage().contains("Available: []"));
    }

    @Test
    public void testLazyInstantiation(@TempDir Path tmpDir) throws Exception {
        // Create config with multiple emitters
        String configJson = String.format(Locale.ROOT, """
                {
                  "emitters": {
                    "file-system-emitter": {
                      "fse1": {
                        "basePath": "%s",
                        "onExists": "REPLACE"
                      },
                      "fse2": {
                        "basePath": "%s",
                        "onExists": "REPLACE"
                      }
                    }
                  },
                  "plugin-roots": "target/plugins"
                }
                """, PluginsTestHelper.toJsonPath(tmpDir.resolve("output1")),
                     PluginsTestHelper.toJsonPath(tmpDir.resolve("output2")));

        Path configPath = tmpDir.resolve("config.json");
        Files.writeString(configPath, configJson, StandardCharsets.UTF_8);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig);

        // After load, both emitters should be in supported list but not instantiated yet
        assertEquals(2, emitterManager.getSupported().size());

        // Request only fse1 - only it should be instantiated
        Emitter emitter1 = emitterManager.getEmitter("fse1");
        assertNotNull(emitter1);
        assertEquals("fse1", emitter1.getExtensionConfig().id());

        // fse2 has not been requested yet - verify it exists in config
        assertTrue(emitterManager.getSupported().contains("fse2"));

        // Now request fse2
        Emitter emitter2 = emitterManager.getEmitter("fse2");
        assertNotNull(emitter2);
        assertEquals("fse2", emitter2.getExtensionConfig().id());
    }

    @Test
    public void testCaching(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig);

        // Get the same emitter multiple times
        Emitter emitter1 = emitterManager.getEmitter("fse");
        Emitter emitter2 = emitterManager.getEmitter("fse");
        Emitter emitter3 = emitterManager.getEmitter("fse");

        // Should be the exact same instance (reference equality)
        assertSame(emitter1, emitter2);
        assertSame(emitter2, emitter3);
    }

    @Test
    public void testThreadSafety(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Future<Emitter>> futures = new ArrayList<>();

        // Start multiple threads that all request the same emitter simultaneously
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    // All threads try to get the emitter at once
                    return emitterManager.getEmitter("fse");
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        // Start all threads at once
        startLatch.countDown();

        // Wait for all threads to complete
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

        // Collect all emitters
        List<Emitter> emitters = new ArrayList<>();
        for (Future<Emitter> future : futures) {
            emitters.add(future.get());
        }

        executor.shutdown();

        // All threads should have gotten the same instance
        Emitter first = emitters.get(0);
        for (Emitter emitter : emitters) {
            assertSame(first, emitter, "All threads should get the same emitter instance");
        }
    }

    @Test
    public void testUnknownEmitterId(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig);

        EmitterNotFoundException exception = assertThrows(EmitterNotFoundException.class, () -> {
            emitterManager.getEmitter("non-existent-emitter");
        });

        assertTrue(exception.getMessage().contains("non-existent-emitter"));
        assertTrue(exception.getMessage().contains("Available:"));
    }

    @Test
    public void testUnknownEmitterType(@TempDir Path tmpDir) throws Exception {
        String configJson = """
                {
                  "emitters": {
                    "non-existent-emitter-type": {
                      "emitter1": {
                        "someProp": "value"
                      }
                    }
                  },
                  "plugin-roots": "target/plugins"
                }
                """;

        Path configPath = tmpDir.resolve("config.json");
        Files.writeString(configPath, configJson, StandardCharsets.UTF_8);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Should fail during load (early validation)
        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            EmitterManager.load(pluginManager, tikaJsonConfig);
        });

        assertTrue(exception.getMessage().contains("Unknown emitter type"));
        assertTrue(exception.getMessage().contains("non-existent-emitter-type"));
    }

    @Test
    public void testDuplicateEmitterId(@TempDir Path tmpDir) throws Exception {
        String configJson = String.format(Locale.ROOT, """
                {
                  "emitters": {
                    "file-system-emitter": {
                      "fse1": {
                        "basePath": "%s",
                        "onExists": "REPLACE"
                      },
                      "fse1": {
                        "basePath": "%s",
                        "onExists": "REPLACE"
                      }
                    }
                  },
                  "plugin-roots": "target/plugins"
                }
                """, PluginsTestHelper.toJsonPath(tmpDir.resolve("output1")),
                     PluginsTestHelper.toJsonPath(tmpDir.resolve("output2")));

        Path configPath = tmpDir.resolve("config.json");
        Files.writeString(configPath, configJson, StandardCharsets.UTF_8);

        // PolymorphicObjectMapperFactory has FAIL_ON_READING_DUP_TREE_KEY enabled
        // so duplicate keys are caught during JSON parsing
        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            TikaJsonConfig.load(configPath);
        });

        assertTrue(exception.getMessage().contains("Failed to parse JSON") &&
                exception.getCause() != null &&
                exception.getCause().getMessage().contains("Duplicate field"));
    }

    @Test
    public void testGetSingleEmitter(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig);

        // When only one emitter exists, no-arg getEmitter() should work
        Emitter emitter = emitterManager.getEmitter();
        assertNotNull(emitter);
        assertEquals("fse", emitter.getExtensionConfig().id());
    }

    @Test
    public void testGetSingleEmitterWithMultipleConfigured(@TempDir Path tmpDir) throws Exception {
        String configJson = String.format(Locale.ROOT, """
                {
                  "emitters": {
                    "file-system-emitter": {
                      "fse1": {
                        "basePath": "%s",
                        "onExists": "REPLACE"
                      },
                      "fse2": {
                        "basePath": "%s",
                        "onExists": "REPLACE"
                      }
                    }
                  },
                  "plugin-roots": "target/plugins"
                }
                """, PluginsTestHelper.toJsonPath(tmpDir.resolve("output1")),
                     PluginsTestHelper.toJsonPath(tmpDir.resolve("output2")));

        Path configPath = tmpDir.resolve("config.json");
        Files.writeString(configPath, configJson, StandardCharsets.UTF_8);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig);

        // When multiple emitters exist, no-arg getEmitter() should fail
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            emitterManager.getEmitter();
        });

        assertTrue(exception.getMessage().contains("exactly 1"));
    }

    @Test
    public void testSaveEmitter(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Load with runtime modifications enabled
        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig, true);

        // Initially only fse exists
        assertEquals(1, emitterManager.getSupported().size());

        // Dynamically add a new emitter configuration
        String newConfigJson = String.format(Locale.ROOT, """
                {
                  "basePath": "%s",
                  "onExists": "REPLACE"
                }
                """, PluginsTestHelper.toJsonPath(tmpDir.resolve("output2")));
        ExtensionConfig newConfig = new ExtensionConfig("fse2", "file-system-emitter", newConfigJson);

        emitterManager.saveEmitter(newConfig);

        // Now both should be available
        assertEquals(2, emitterManager.getSupported().size());
        assertTrue(emitterManager.getSupported().contains("fse"));
        assertTrue(emitterManager.getSupported().contains("fse2"));

        // Emitter should be lazily instantiated when requested
        Emitter emitter2 = emitterManager.getEmitter("fse2");
        assertNotNull(emitter2);
        assertEquals("fse2", emitter2.getExtensionConfig().id());
    }

    @Test
    public void testSaveEmitterDuplicate(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig, true);

        // Try to add an emitter with the same ID as existing one
        String newConfigJson = String.format(Locale.ROOT, """
                {
                  "basePath": "%s",
                  "onExists": "REPLACE"
                }
                """, PluginsTestHelper.toJsonPath(tmpDir.resolve("output2")));
        ExtensionConfig duplicateConfig = new ExtensionConfig("fse", "file-system-emitter", newConfigJson);

        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            emitterManager.saveEmitter(duplicateConfig);
        });

        assertTrue(exception.getMessage().contains("already exists"));
        assertTrue(exception.getMessage().contains("fse"));
    }

    @Test
    public void testSaveEmitterUnknownType(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig, true);

        // Try to add an emitter with unknown type
        ExtensionConfig unknownTypeConfig = new ExtensionConfig("emitter2", "unknown-emitter-type", "{}");

        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            emitterManager.saveEmitter(unknownTypeConfig);
        });

        assertTrue(exception.getMessage().contains("Unknown emitter type"));
        assertTrue(exception.getMessage().contains("unknown-emitter-type"));
    }

    @Test
    public void testSaveEmitterNull(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig, true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            emitterManager.saveEmitter(null);
        });

        assertTrue(exception.getMessage().contains("cannot be null"));
    }

    @Test
    public void testSaveEmitterLazyInstantiation(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig, true);

        // Add multiple emitters
        for (int i = 2; i <= 5; i++) {
            String configJson = String.format(Locale.ROOT, """
                    {
                      "basePath": "%s",
                      "onExists": "REPLACE"
                    }
                    """, PluginsTestHelper.toJsonPath(tmpDir.resolve("output" + i)));
            ExtensionConfig config2 = new ExtensionConfig("fse" + i, "file-system-emitter", configJson);
            emitterManager.saveEmitter(config2);
        }

        // All 5 should be in supported list
        assertEquals(5, emitterManager.getSupported().size());

        // Request only fse3 - only it should be instantiated
        Emitter emitter3 = emitterManager.getEmitter("fse3");
        assertNotNull(emitter3);
        assertEquals("fse3", emitter3.getExtensionConfig().id());

        // Others are still available but not instantiated yet
        assertTrue(emitterManager.getSupported().contains("fse2"));
        assertTrue(emitterManager.getSupported().contains("fse4"));
        assertTrue(emitterManager.getSupported().contains("fse5"));
    }

    @Test
    public void testSaveEmitterNotAllowed(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Load with default (runtime modifications disabled)
        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig);

        // Try to add an emitter - should fail
        String newConfigJson = String.format(Locale.ROOT, """
                {
                  "basePath": "%s",
                  "onExists": "REPLACE"
                }
                """, PluginsTestHelper.toJsonPath(tmpDir.resolve("output2")));
        ExtensionConfig newConfig = new ExtensionConfig("fse2", "file-system-emitter", newConfigJson);

        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            emitterManager.saveEmitter(newConfig);
        });

        assertTrue(exception.getMessage().contains("Runtime modifications are not allowed"));
        assertTrue(exception.getMessage().contains("allowRuntimeModifications=true"));
    }

    @Test
    public void testSaveEmitterNotAllowedExplicit(@TempDir Path tmpDir) throws Exception {
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(tmpDir);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Load with explicit false
        EmitterManager emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig, false);

        // Try to add an emitter - should fail
        String newConfigJson = String.format(Locale.ROOT, """
                {
                  "basePath": "%s",
                  "onExists": "REPLACE"
                }
                """, PluginsTestHelper.toJsonPath(tmpDir.resolve("output2")));
        ExtensionConfig newConfig = new ExtensionConfig("fse2", "file-system-emitter", newConfigJson);

        TikaConfigException exception = assertThrows(TikaConfigException.class, () -> {
            emitterManager.saveEmitter(newConfig);
        });

        assertTrue(exception.getMessage().contains("Runtime modifications are not allowed"));
    }
}
