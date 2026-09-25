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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.core.PipesConfig;

/** TIKA-4931: code beats the user's config file, which beats PipesForkParser's defaults. */
public class PipesForkParserConfigTest {

    @TempDir
    Path tempDir;

    @Test
    public void testPrecedence() throws Exception {
        Path userConfig = tempDir.resolve("user-config.json");
        Files.writeString(userConfig,
                "{\"pipes\":{\"numClients\":4,\"socketTimeoutMillis\":4321,\"javaPath\":\"/file/java\"}}");

        PipesForkParserConfig config = new PipesForkParserConfig().setUserConfigPath(userConfig);
        config.getPipesConfig().setSocketTimeoutMillis(1234);
        config.setJavaPath("/code/java");

        PipesConfig pipes = config.getPipesConfig();
        assertEquals(4, pipes.getNumClients());
        assertEquals(1234, pipes.getSocketTimeoutMillis());
        assertEquals("/code/java", pipes.getJavaPath());
    }

    /** An explicit setting equal to the default still beats the file. */
    @Test
    public void testExplicitDefaultBeatsFile() throws Exception {
        Path userConfig = tempDir.resolve("user-config.json");
        Files.writeString(userConfig, "{\"pipes\":{\"numClients\":4}}");

        PipesForkParserConfig config = new PipesForkParserConfig()
                .setUserConfigPath(userConfig)
                .setNumClients(1);

        assertEquals(1, config.getPipesConfig().getNumClients());
    }

    @Test
    public void testNoPipesSectionKeepsDefaults() throws Exception {
        Path userConfig = tempDir.resolve("user-config.json");
        Files.writeString(userConfig, "{\"parse-context\":{}}");

        PipesForkParserConfig config = new PipesForkParserConfig().setUserConfigPath(userConfig);

        assertEquals(1, config.getPipesConfig().getNumClients());
    }

    @Test
    public void testBadPipesSectionFailsFast() throws Exception {
        Path userConfig = tempDir.resolve("user-config.json");
        Files.writeString(userConfig, "{\"pipes\":{\"numClients\":\"many\"}}");

        assertThrows(IllegalArgumentException.class,
                () -> new PipesForkParserConfig().setUserConfigPath(userConfig));
    }
}
