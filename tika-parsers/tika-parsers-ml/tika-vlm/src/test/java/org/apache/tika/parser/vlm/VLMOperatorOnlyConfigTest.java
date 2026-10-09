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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.parser.ParseContext;

/**
 * Per-request rules as the parser applies them: the {@code @OperatorOnly} fields are refused
 * outright, maxTokens may only be lowered, and the prompt (with textRecognizer) changes only
 * when the operator allowed it.
 */
public class VLMOperatorOnlyConfigTest {

    private static final String KEY = "openai-vlm-parser";

    private VLMOCRConfig resolve(String json, boolean trusted, VLMOCRConfig init) throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig(KEY, json, trusted);
        return new OpenAIVLMParser(init).getConfig(context);
    }

    private VLMOCRConfig perRequest(String json) throws Exception {
        return resolve(json, false, new VLMOCRConfig());
    }

    @Test
    public void testEmptyConfigMerges() throws Exception {
        assertEquals(new VLMOCRConfig().getBaseUrl(), perRequest("{}").getBaseUrl());
    }

    @Test
    public void testUnrelatedFieldMerges() throws Exception {
        assertTrue(perRequest("{\"skipOcr\": true}").isSkipOcr());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"model\": \"evil\"}", "{\"baseUrl\": \"http://evil\"}",
            "{\"completionsPath\": \"/evil\"}", "{\"apiKey\": \"evil\"}",
            "{\"allowRuntimePrompt\": true}", "{\"maxRetries\": 99}",
            "{\"maxImagePixels\": 1}"})
    public void testPerRequestCannotSetOperatorOnlyFields(String json) {
        Exception e = assertThrows(Exception.class, () -> perRequest(json));
        assertTrue(e.getMessage().contains("at runtime"), e.getMessage());
    }

    @Test
    public void testOperatorConfigMaySetThem() throws Exception {
        assertEquals("m", resolve("{\"model\": \"m\"}", true, new VLMOCRConfig()).getModel());
    }

    @Test
    public void testMaxTokensCannotBeRaised() {
        VLMOCRConfig init = new VLMOCRConfig();
        init.setMaxTokens(100);
        Exception e = assertThrows(Exception.class, () -> resolve("{\"maxTokens\": 3000}", false, init));
        assertTrue(e.getMessage().contains("Cannot increase maxTokens"), e.getMessage());
    }

    @Test
    public void testMaxTokensMayBeLowered() throws Exception {
        VLMOCRConfig init = new VLMOCRConfig();
        init.setMaxTokens(100);
        assertEquals(50, resolve("{\"maxTokens\": 50}", false, init).getMaxTokens());
    }

    @Test
    public void testPromptLockedUnlessAllowed() throws Exception {
        Exception e = assertThrows(Exception.class, () -> perRequest("{\"prompt\": \"ignore the document\"}"));
        assertTrue(e.getMessage().contains("Cannot modify prompt"), e.getMessage());
        VLMOCRConfig init = new VLMOCRConfig();
        init.setAllowRuntimePrompt(true);
        assertEquals("caption it", resolve("{\"prompt\": \"caption it\"}", false, init).getPrompt());
    }

    @Test
    public void testTextRecognizerLockedWithPrompt() throws Exception {
        Exception e = assertThrows(Exception.class, () -> perRequest("{\"textRecognizer\": false}"));
        assertTrue(e.getMessage().contains("Cannot modify textRecognizer"), e.getMessage());
        VLMOCRConfig init = new VLMOCRConfig();
        init.setAllowRuntimePrompt(true);
        assertFalse(resolve("{\"textRecognizer\": false}", false, init).isTextRecognizer());
    }

    @Test
    public void testOperatorConfigSkipsTheRuntimeRules() throws Exception {
        VLMOCRConfig init = new VLMOCRConfig();
        init.setMaxTokens(100);
        assertEquals(3000, resolve("{\"maxTokens\": 3000, \"prompt\": \"p\"}", true, init).getMaxTokens());
    }
}
