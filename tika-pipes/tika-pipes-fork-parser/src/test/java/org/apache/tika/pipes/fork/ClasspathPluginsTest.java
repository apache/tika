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
package org.apache.tika.pipes.fork;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.DefaultPluginManager;

import org.apache.tika.pipes.fetcher.fs.FileSystemFetcherFactory;
import org.apache.tika.plugins.TikaExtensionFactory;
import org.apache.tika.plugins.TikaPluginManager;

/**
 * The file-system plugin jar is a compile dependency here, so its extensions.idx sits on the
 * application classpath exactly the way a Maven consumer of PipesForkParser sees it.
 */
public class ClasspathPluginsTest {

    @Test
    public void classpathExtensionsAreNotDiscovered(@TempDir Path emptyRoot) throws Exception {
        DefaultPluginManager stock = new DefaultPluginManager(emptyRoot);
        stock.loadPlugins();
        stock.startPlugins();
        assertFalse(stock.getExtensions(TikaExtensionFactory.class).isEmpty(),
                "pf4j's stock manager sees the index; the test setup is wrong otherwise");

        TikaPluginManager manager = new TikaPluginManager(Collections.singletonList(emptyRoot));
        manager.loadPlugins();
        manager.startPlugins();
        assertTrue(manager.getExtensions(TikaExtensionFactory.class).isEmpty(),
                "a factory on the application classpath must not be discovered");
    }

    @Test
    public void classpathExtensionsDiscoveredWhenOptedIn(@TempDir Path emptyRoot) throws Exception {
        System.setProperty(TikaPluginManager.CLASSPATH_PLUGINS_PROPERTY, "true");
        try {
            TikaPluginManager manager =
                    new TikaPluginManager(Collections.singletonList(emptyRoot));
            manager.loadPlugins();
            manager.startPlugins();
            List<TikaExtensionFactory> found = manager.getExtensions(TikaExtensionFactory.class);
            assertTrue(found.stream().anyMatch(f -> f instanceof FileSystemFetcherFactory),
                    "found: " + found);
        } finally {
            System.clearProperty(TikaPluginManager.CLASSPATH_PLUGINS_PROPERTY);
        }
    }
}
