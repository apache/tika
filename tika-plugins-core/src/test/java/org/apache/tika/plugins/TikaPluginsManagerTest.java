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
package org.apache.tika.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

public class TikaPluginsManagerTest {

    @Test
    public void testBasic() throws Exception {
        TikaExtensionConfigsManager manager = TikaExtensionConfigsManager.load(TikaPluginsManagerTest.class.getResourceAsStream("/test1.json"), true,
                TikaExtensionConfigsManager.EXTENSION_TYPES.FETCHERS);
        Optional<ExtensionConfigs> pluginConfigsOpt = manager.get(TikaExtensionConfigsManager.EXTENSION_TYPES.FETCHERS);
        assertTrue(pluginConfigsOpt.isPresent());
        ExtensionConfigs extensionConfigs = pluginConfigsOpt.get();
        Optional<ExtensionConfig> pluginConfigOpt = extensionConfigs.getById("fsf");
        assertTrue(pluginConfigOpt.isPresent());
        ExtensionConfig pluginConfig = pluginConfigOpt.get();
        assertEquals("file-system-fetcher", pluginConfig.extensionId());
    }

    @Test
    public void testPath() throws Exception {
        TikaExtensionConfigsManager manager = TikaExtensionConfigsManager.load(TikaPluginsManagerTest.class.getResourceAsStream("/test2.json"), true,
                TikaExtensionConfigsManager.EXTENSION_TYPES.FETCHERS);
        List<Path> paths = manager.getPluginRoots();
        assertEquals(1, paths.size());
        assertEquals("path1", paths.get(0).getFileName().toString());
    }

    @Test
    public void testPaths() throws Exception {
        TikaExtensionConfigsManager manager = TikaExtensionConfigsManager.load(TikaPluginsManagerTest.class.getResourceAsStream("/test3.json"), true,
                TikaExtensionConfigsManager.EXTENSION_TYPES.FETCHERS);
        List<Path> paths = manager.getPluginRoots();
        assertEquals(3, paths.size());
        assertEquals("path1", paths.get(0).getFileName().toString());
        assertEquals("path2", paths.get(1).getFileName().toString());
        assertEquals("path3", paths.get(2).getFileName().toString());
    }

    @Test
    public void testMissingItem() throws Exception {
        assertThrows(TikaConfigException.class, () ->
                TikaExtensionConfigsManager.load(TikaPluginsManagerTest.class.getResourceAsStream("/test1.json"),
                "qwerty")
        );
        assertThrows(TikaConfigException.class, () ->
                TikaExtensionConfigsManager.load(TikaPluginsManagerTest.class.getResourceAsStream("/testEmpty.json"), true,
                        TikaExtensionConfigsManager.EXTENSION_TYPES.FETCHERS)
        );
        assertThrows(TikaConfigException.class, () ->
                TikaExtensionConfigsManager.load(TikaPluginsManagerTest.class.getResourceAsStream("/testEmpty2.json"), true,
                        TikaExtensionConfigsManager.EXTENSION_TYPES.FETCHERS)
        );
        assertThrows(TikaConfigException.class, () ->
                TikaExtensionConfigsManager.load(TikaPluginsManagerTest.class.getResourceAsStream("/testNoPluginConfig.json"), true,
                        TikaExtensionConfigsManager.EXTENSION_TYPES.FETCHERS)
        );
    }
}
