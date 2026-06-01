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
package org.apache.tika.parser.vlm;

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
 * Validates the VLM (OpenAI / Claude / Gemini) configuration examples used
 * in the docs.
 *
 * <p>The JSON files under {@code src/test/resources/config-examples/} are
 * symlinked from {@code docs/modules/ROOT/examples/}, so any change that
 * keeps these tests passing also keeps the published docs correct.
 *
 * <p>The tests only validate that the JSON deserializes and the parser
 * constructs — no HTTP call is made to any model endpoint, so they're safe
 * to run without network access or API keys.
 */
public class VLMConfigExamplesTest {

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
    public void testOpenAIVLMBasic() throws Exception {
        loadAndValidate("openai-vlm-basic.json");
    }

    @Test
    public void testOpenAIVLMFull() throws Exception {
        loadAndValidate("openai-vlm-full.json");
    }

    @Test
    public void testClaudeVLMBasic() throws Exception {
        loadAndValidate("claude-vlm-basic.json");
    }

    @Test
    public void testClaudeVLMFull() throws Exception {
        loadAndValidate("claude-vlm-full.json");
    }

    @Test
    public void testGeminiVLMBasic() throws Exception {
        loadAndValidate("gemini-vlm-basic.json");
    }

    @Test
    public void testGeminiVLMFull() throws Exception {
        loadAndValidate("gemini-vlm-full.json");
    }

    @Test
    public void testVLMForPdfParsing() throws Exception {
        loadAndValidate("vlm-pdf-parsing.json");
    }
}
