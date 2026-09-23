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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.DefaultPluginManager;
import org.pf4j.RuntimeMode;

public class TikaPluginManagerTest {

    @Test
    public void testDefaultRuntimeModeIsDeployment(@TempDir Path tmpDir) throws Exception {
        TikaPluginManager manager = new TikaPluginManager(Collections.singletonList(tmpDir));
        assertEquals(RuntimeMode.DEPLOYMENT, manager.getRuntimeMode());
    }

    @Test
    public void testDevelopmentModeViaSystemProperty(@TempDir Path tmpDir) throws Exception {
        System.setProperty("tika.plugin.dev.mode", "true");
        try {
            TikaPluginManager manager = new TikaPluginManager(Collections.singletonList(tmpDir));
            assertEquals(RuntimeMode.DEVELOPMENT, manager.getRuntimeMode());
        } finally {
            System.clearProperty("tika.plugin.dev.mode");
        }
    }

    @Test
    public void testDeploymentModeWhenPropertyIsFalse(@TempDir Path tmpDir) throws Exception {
        System.setProperty("tika.plugin.dev.mode", "false");
        try {
            TikaPluginManager manager = new TikaPluginManager(Collections.singletonList(tmpDir));
            assertEquals(RuntimeMode.DEPLOYMENT, manager.getRuntimeMode());
        } finally {
            System.clearProperty("tika.plugin.dev.mode");
        }
    }

    @Test
    public void classpathExtensionsAreNotDiscovered(@TempDir Path tmpDir) throws Exception {
        // the index is on the test classpath and pf4j's stock manager does see it
        assertNotNull(getClass().getResource("/META-INF/extensions.idx"));
        DefaultPluginManager stock = new DefaultPluginManager(tmpDir);
        stock.loadPlugins();
        stock.startPlugins();
        assertFalse(stock.getExtensions(TikaExtensionFactory.class).isEmpty());

        TikaPluginManager manager = new TikaPluginManager(Collections.singletonList(tmpDir));
        manager.loadPlugins();
        manager.startPlugins();
        assertTrue(manager.getExtensions(TikaExtensionFactory.class).isEmpty(),
                "a factory on the application classpath must not be discovered");
    }

    @Test
    public void developmentModeLoadsAnExplodedClassesDirectory(@TempDir Path classes)
            throws Exception {
        // the documented recipe: plugin-roots points at target/classes, no zip
        Files.writeString(classes.resolve("plugin.properties"),
                "plugin.id=exploded-test\nplugin.class=" + TestPlugin.class.getName()
                        + "\nplugin.version=1\n");
        Files.createDirectories(classes.resolve("META-INF"));
        Files.writeString(classes.resolve("META-INF/extensions.idx"),
                ClasspathTestFactory.class.getName() + "\n");
        System.setProperty("tika.plugin.dev.mode", "true");
        try {
            TikaPluginManager manager = new TikaPluginManager(Collections.singletonList(classes));
            manager.loadPlugins();
            manager.startPlugins();
            assertEquals(1, manager.getExtensions(TikaExtensionFactory.class).size());
        } finally {
            System.clearProperty("tika.plugin.dev.mode");
        }
    }

    @Test
    public void classpathExtensionsDiscoveredWhenOptedIn(@TempDir Path tmpDir) throws Exception {
        System.setProperty(TikaPluginManager.CLASSPATH_PLUGINS_PROPERTY, "true");
        try {
            TikaPluginManager manager = new TikaPluginManager(Collections.singletonList(tmpDir));
            manager.loadPlugins();
            manager.startPlugins();
            assertEquals(1, manager.getExtensions(TikaExtensionFactory.class).size());
        } finally {
            System.clearProperty(TikaPluginManager.CLASSPATH_PLUGINS_PROPERTY);
        }
    }
}
