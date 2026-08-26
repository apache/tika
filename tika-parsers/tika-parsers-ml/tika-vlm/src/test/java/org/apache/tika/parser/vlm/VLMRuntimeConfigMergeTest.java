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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.parser.ParseContext;

/**
 * VLM's locked fields must reject caller input without the merge's own copy of the
 * defaults tripping them first (TIKA-4843). Unlike Tess4J, several VLM defaults are
 * non-blank, so the "reject any non-empty value" guards fire on the copy.
 */
public class VLMRuntimeConfigMergeTest {

    private VLMOCRConfig runtime(String json, VLMOCRConfig init) throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("vlm-ocr-parser", json);
        return ParseContextConfig.getConfig(context, "vlm-ocr-parser",
                VLMOCRConfig.RuntimeConfig.class, new VLMOCRConfig.RuntimeConfig(init));
    }

    private VLMOCRConfig runtime(String json) throws Exception {
        return runtime(json, new VLMOCRConfig());
    }

    @Test
    public void testEmptyConfigMerges() throws Exception {
        assertEquals(new VLMOCRConfig().getBaseUrl(), runtime("{}").getBaseUrl());
    }

    @Test
    public void testUnrelatedFieldMerges() throws Exception {
        assertTrue(runtime("{\"skipOcr\": true}").isSkipOcr());
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

    @Test
    public void testCallerSetAllowRuntimePromptStillRejected() {
        Exception e = assertThrows(Exception.class,
                () -> runtime("{\"allowRuntimePrompt\": true}"));
        assertTrue(rootMessage(e).contains("Cannot modify allowRuntimePrompt"), rootMessage(e));
    }

    /**
     * The maxTokens ceiling is init-time state with no getter. If the merge's copy resets it
     * to the class default, a caller can raise maxTokens above what the operator configured.
     */
    @Test
    public void testMaxTokensCeilingIsNotWidenedByTheCopy() {
        VLMOCRConfig init = new VLMOCRConfig();
        init.setMaxTokens(100);
        Exception e = assertThrows(Exception.class, () -> runtime("{\"maxTokens\": 3000}", init));
        assertTrue(rootMessage(e).contains("Cannot increase maxTokens"), rootMessage(e));
    }

    @Test
    public void testMaxTokensBelowCeilingStillAllowed() throws Exception {
        VLMOCRConfig init = new VLMOCRConfig();
        init.setMaxTokens(100);
        assertEquals(50, runtime("{\"maxTokens\": 50}", init).getMaxTokens());
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }
}
