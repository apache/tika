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

import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
}
