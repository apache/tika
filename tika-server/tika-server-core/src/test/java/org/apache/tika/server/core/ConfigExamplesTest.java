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
package org.apache.tika.server.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaLoader;

/**
 * Validates server configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest {

    private static final String EXAMPLES_DIR = "/config-examples/";

    @TempDir
    Path tempDir;

    private void loadAndValidate(String resourceName) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(EXAMPLES_DIR + resourceName)) {
            assertNotNull(is, "Resource not found: " + resourceName);
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Path configFile = tempDir.resolve("tika-config.json");
            Files.writeString(configFile, json, StandardCharsets.UTF_8);
            TikaLoader loader = TikaLoader.load(configFile);
            assertNotNull(loader, "TikaLoader should not be null for: " + resourceName);
        }
    }

    @Test
    public void testServerBasicConfig() throws Exception {
        loadAndValidate("server-basic.json");
    }

    @Test
    public void testServerWithParsersConfig() throws Exception {
        loadAndValidate("server-with-parsers.json");
    }
}
