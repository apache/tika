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
package org.apache.tika.inference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.parser.ParseContext;

/**
 * The endpoint, credential and model are where document bytes get sent and billed, so a
 * per-request block may not set them; operator JSON may.
 */
public class ImageEmbeddingOperatorOnlyConfigTest {

    private static final String KEY = "openai-image-embedding-parser";

    private ImageEmbeddingConfig resolve(String json, boolean trusted) throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig(KEY, json, trusted);
        return ParseContextConfig.getConfig(context, KEY, ImageEmbeddingConfig.class,
                new ImageEmbeddingConfig());
    }

    @Test
    public void testEmptyConfigMerges() throws Exception {
        assertEquals(new ImageEmbeddingConfig().getBaseUrl(), resolve("{}", false).getBaseUrl());
    }

    @Test
    public void testUnrelatedFieldMerges() throws Exception {
        assertTrue(resolve("{\"skipEmbedding\": true}", false).isSkipEmbedding());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"model\": \"evil\"}", "{\"baseUrl\": \"http://evil\"}",
            "{\"apiKey\": \"evil\"}"})
    public void testPerRequestCannotSetOperatorOnlyFields(String json) {
        Exception e = assertThrows(Exception.class, () -> resolve(json, false));
        assertTrue(e.getMessage().contains("at runtime"), e.getMessage());
    }

    @Test
    public void testOperatorConfigMaySetThem() throws Exception {
        assertEquals("http://internal", resolve("{\"baseUrl\": \"http://internal\"}", true).getBaseUrl());
    }
}
