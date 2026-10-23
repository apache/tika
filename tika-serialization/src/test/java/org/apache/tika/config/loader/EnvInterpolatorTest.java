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
package org.apache.tika.config.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

public class EnvInterpolatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode resolve(String json, Map<String, String> env) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        EnvInterpolator.resolve(root, env::get);
        return root;
    }

    @Test
    public void testWholeValueAndEmbedded() throws Exception {
        JsonNode root = resolve("""
                { "engines": { "jina": { "openai-embedding-engine": {
                    "apiKey": "${env:JINA_API_KEY}",
                    "baseUrl": "https://${env:JINA_HOST}/v1",
                    "requestParameters": { "task": "retrieval.passage" } } } },
                  "parsers": [ "html-parser", { "pdf-parser": { "ocr": { "language": "${env:LANG_A}+${env:LANG_B}" } } } ] }
                """, Map.of("JINA_API_KEY", "s3cret", "JINA_HOST", "api.jina.ai",
                "LANG_A", "eng", "LANG_B", "deu"));
        JsonNode engine = root.at("/engines/jina/openai-embedding-engine");
        assertEquals("s3cret", engine.get("apiKey").asText());
        assertEquals("https://api.jina.ai/v1", engine.get("baseUrl").asText());
        assertEquals("retrieval.passage", engine.at("/requestParameters/task").asText(),
                "untouched strings stay");
        assertEquals("eng+deu", root.at("/parsers/1/pdf-parser/ocr/language").asText(),
                "resolved inside arrays too");
        assertEquals("html-parser", root.at("/parsers/0").asText());
    }

    @Test
    public void testOnlyTheEnvFormIsTouched() throws Exception {
        JsonNode root = resolve("""
                { "engines": { "vlm": { "openai-vlm-parser": {
                    "prompt": "Return ${fields} as $JSON and ${env:} and ${sys:HOME} and $${env:X}" } } } }
                """, Map.of("X", "no", "fields", "no", "HOME", "no"));
        assertEquals("Return ${fields} as $JSON and ${env:} and ${sys:HOME} and $no",
                root.at("/engines/vlm/openai-vlm-parser/prompt").asText());
    }

    @Test
    public void testUnsetFailsNamingTheVariableAndPathNotAValue() throws Exception {
        TikaConfigException e = assertThrows(TikaConfigException.class, () -> resolve("""
                { "emitters": { "es": { "es-emitter": { "apiKey": "${env:ES_KEY}",
                    "esUrl": "http://x" } } } }
                """, Map.of("OTHER", "value")));
        assertTrue(e.getMessage().contains("ES_KEY"), e.getMessage());
        assertTrue(e.getMessage().contains("/emitters/es/es-emitter/apiKey"), e.getMessage());
        assertFalse(e.getMessage().contains("value"), e.getMessage());
    }

    @Test
    public void testNonStringsUntouched() throws Exception {
        JsonNode root = resolve("""
                { "pipes": { "numClients": 4, "forkedJvmArgs": ["-Xmx${env:HEAP}"], "on": true, "n": null } }
                """, Map.of("HEAP", "2g"));
        assertEquals(4, root.at("/pipes/numClients").asInt());
        assertEquals("-Xmx2g", root.at("/pipes/forkedJvmArgs/0").asText());
        assertTrue(root.at("/pipes/on").asBoolean());
        assertTrue(root.at("/pipes/n").isNull());
    }

    /** The real entry point resolves against the process environment. */
    @Test
    public void testLoadResolvesFromTheProcessEnvironment() throws Exception {
        String var = System.getenv().containsKey("PATH") ? "PATH" : "HOME";
        String json = "{ \"pipes\": { \"tempDirectory\": \"${env:" + var + "}\" } }";
        TikaJsonConfig config = TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(System.getenv(var), config.getRootNode().at("/pipes/tempDirectory").asText());
        assertThrows(TikaConfigException.class, () -> TikaJsonConfig.load(new ByteArrayInputStream(
                "{ \"pipes\": { \"tempDirectory\": \"${env:TIKA_SURELY_UNSET_VARIABLE_4909}\" } }"
                        .getBytes(StandardCharsets.UTF_8))));
    }
}
