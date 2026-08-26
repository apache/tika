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
package org.apache.tika.parser.ocr.tess4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.parser.ParseContext;

/**
 * Locked fields must be rejected only when the caller actually sets them. The merge
 * clones the default first, and that clone must not trip the guards -- otherwise every
 * per-request config throws, including {@code {}}.
 */
public class Tess4JRuntimeConfigMergeTest {

    private Tess4JConfig runtime(String json) throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("tess4j-parser", json);
        return ParseContextConfig.getConfig(context, "tess4j-parser",
                Tess4JConfig.RuntimeConfig.class, new Tess4JConfig.RuntimeConfig());
    }

    @Test
    public void testEmptyConfigMerges() throws Exception {
        assertEquals(new Tess4JConfig().getPoolSize(), runtime("{}").getPoolSize());
    }

    @Test
    public void testUnrelatedFieldMerges() throws Exception {
        assertTrue(runtime("{\"skipOcr\": true}").isSkipOcr());
    }

    @Test
    public void testCallerSetPoolSizeStillRejected() {
        Exception e = assertThrows(Exception.class, () -> runtime("{\"poolSize\": 7}"));
        assertTrue(rootMessage(e).contains("Cannot modify poolSize"), rootMessage(e));
    }

    @Test
    public void testCallerSetMaxImagePixelsStillRejected() {
        Exception e = assertThrows(Exception.class, () -> runtime("{\"maxImagePixels\": 5}"));
        assertTrue(rootMessage(e).contains("Cannot modify maxImagePixels"), rootMessage(e));
    }

    @Test
    public void testCallerSetDataPathStillRejected() {
        Exception e = assertThrows(Exception.class, () -> runtime("{\"dataPath\": \"/tmp/evil\"}"));
        assertTrue(rootMessage(e).contains("Cannot modify dataPath"), rootMessage(e));
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }
}
