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
package org.apache.tika.pipes.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultPluginsDirTest {

    @Test
    public void pluginsDirNextToTheCodeSourceJar(@TempDir Path install) throws Exception {
        Path plugins = Files.createDirectories(install.resolve("plugins"));
        assertEquals(plugins.toAbsolutePath(),
                DefaultPluginsDir.resolve(install, Path.of("")));
    }

    @Test
    public void pluginsDirBesideTheLibDirectory(@TempDir Path install) throws Exception {
        //the resolving class lives in lib/, the plugins next to it
        Path lib = Files.createDirectories(install.resolve("lib"));
        Path plugins = Files.createDirectories(install.resolve("plugins"));
        assertEquals(plugins.toAbsolutePath(),
                DefaultPluginsDir.resolve(lib, Path.of("")));
    }

    @Test
    public void pluginsDirFromTheWorkingDirectory(@TempDir Path install, @TempDir Path cwd)
            throws Exception {
        Path plugins = Files.createDirectories(cwd.resolve("plugins"));
        assertEquals(plugins.toAbsolutePath(),
                DefaultPluginsDir.resolve(install.resolve("lib"), cwd));
    }

    @Test
    public void missingPluginsDirStaysAbsolute(@TempDir Path cwd) {
        //the forked pipes server must not re-resolve the path against its own cwd
        assertTrue(DefaultPluginsDir.resolve(null, cwd).isAbsolute());
    }
}
