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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.parser.Parser;

/**
 * Validates configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 * This test class validates that each example is valid and can be loaded by TikaLoader.
 * <p>
 * <strong>Important:</strong> When modifying examples in the config-examples directory,
 * ensure the JSON remains valid and these tests pass. The documentation will automatically
 * reflect your changes.
 */
public class ConfigExamplesTest {

    private static final String EXAMPLES_DIR = "/config-examples/";

    @TempDir
    Path tempDir;

    private Parser loadAndValidate(String resourceName) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(EXAMPLES_DIR + resourceName)) {
            assertNotNull(is, "Resource not found: " + resourceName);
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Path configFile = tempDir.resolve("tika-config.json");
            Files.writeString(configFile, json, StandardCharsets.UTF_8);
            TikaLoader loader = TikaLoader.load(configFile);
            Parser parser = loader.loadParsers();
            assertNotNull(parser, "Parser should not be null for: " + resourceName);
            return parser;
        }
    }

    @Test
    public void testPdfParserBasicConfig() throws Exception {
        loadAndValidate("pdf-parser-basic.json");
    }

    @Test
    public void testPdfParserFullConfig() throws Exception {
        loadAndValidate("pdf-parser-full.json");
    }

    @Test
    public void testTesseractBasicConfig() throws Exception {
        loadAndValidate("tesseract-basic.json");
    }

    @Test
    public void testTesseractWithOtherConfig() throws Exception {
        loadAndValidate("tesseract-other-config.json");
    }

    @Test
    public void testFullMigrationExample() throws Exception {
        loadAndValidate("migration-full-example.json");
    }
}
