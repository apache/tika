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

import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.parser.ParseContext;

/**
 * The non-blank {@code baseUrl} default trips this config's own "reject any non-empty
 * value" guard when the merge copies the defaults through the setters (TIKA-4843).
 */
public class ImageEmbeddingRuntimeConfigMergeTest {

    private static final String KEY = "openai-image-embedding-parser";

    private ImageEmbeddingConfig runtime(String json) throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig(KEY, json);
        return ParseContextConfig.getConfig(context, KEY,
                ImageEmbeddingConfig.RuntimeConfig.class, new ImageEmbeddingConfig.RuntimeConfig());
    }

    @Test
    public void testEmptyConfigMerges() throws Exception {
        assertEquals(new ImageEmbeddingConfig().getBaseUrl(), runtime("{}").getBaseUrl());
    }

    @Test
    public void testUnrelatedFieldMerges() throws Exception {
        assertTrue(runtime("{\"skipEmbedding\": true}").isSkipEmbedding());
    }

    @Test
    public void testCallerSetModelStillRejected() {
        Exception e = assertThrows(Exception.class, () -> runtime("{\"model\": \"evil\"}"));
        assertTrue(rootMessage(e).contains("Cannot modify model"), rootMessage(e));
    }

    @Test
    public void testCallerSetBaseUrlStillRejected() {
        Exception e = assertThrows(Exception.class,
                () -> runtime("{\"baseUrl\": \"http://evil\"}"));
        assertTrue(rootMessage(e).contains("Cannot modify baseUrl"), rootMessage(e));
    }

    @Test
    public void testCallerSetApiKeyStillRejected() {
        Exception e = assertThrows(Exception.class, () -> runtime("{\"apiKey\": \"evil\"}"));
        assertTrue(rootMessage(e).contains("Cannot modify apiKey"), rootMessage(e));
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }
}
