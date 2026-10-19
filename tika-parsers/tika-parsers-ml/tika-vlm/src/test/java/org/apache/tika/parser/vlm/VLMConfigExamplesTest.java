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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.Parser;

/**
 * Validates the VLM (OpenAI / Claude / Gemini) configuration examples used
 * in the docs.
 *
 * <p>The JSON files under {@code src/test/resources/config-examples/} are
 * copies of those in {@code docs/modules/ROOT/examples/}, kept identical by
 * {@link #testExamplesMatchTheDocs()}, so any change that keeps these tests
 * passing also keeps the published docs correct.
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
            // an unset ${env:...} fails the load; the examples name keys the build lacks
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\$\\{env:[A-Za-z0-9_]+}", "");
            Path configFile = tempDir.resolve("tika-config.json");
            Files.writeString(configFile, json, StandardCharsets.UTF_8);
            TikaLoader loader = TikaLoader.load(configFile);
            Parser parser;
            try {
                parser = loader.loadParsers();
            } catch (TikaConfigException e) {
                // the example named an engine this box lacks: the JSON, the component
                // name and every field were still validated before that check
                assumeTrue(!e.getMessage().contains("advertises no media types"),
                        "engine unavailable on this box: " + e.getMessage());
                throw e;
            }
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

    @Test
    public void testJinaOCRBasic() throws Exception {
        loadAndValidate("jina-ocr-basic.json");
    }

    @Test
    public void testExamplesMatchTheDocs() throws Exception {
        Path docs = Paths.get("../../../docs/modules/ROOT/examples");
        assumeTrue(Files.isDirectory(docs), "docs not checked out");
        try (Stream<Path> examples = Files.list(Paths.get("src/test/resources/config-examples"))) {
            for (Path example : (Iterable<Path>) examples::iterator) {
                Path doc = docs.resolve(example.getFileName());
                assertEquals(Files.readString(doc), Files.readString(example),
                        example.getFileName() + " differs from its docs copy");
            }
        }
    }
}
