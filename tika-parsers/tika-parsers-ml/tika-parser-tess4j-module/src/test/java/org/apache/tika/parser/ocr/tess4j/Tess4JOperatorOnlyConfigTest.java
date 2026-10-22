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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.parser.ParseContext;

/**
 * Paths point at native code and the pool and pixel ceilings are operator sizing, so a
 * per-request {@code tess4j-parser} block may not set them; operator JSON may.
 */
public class Tess4JOperatorOnlyConfigTest {

    private static final String KEY = "tess4j-parser";

    private Tess4JConfig resolve(String json, boolean trusted) throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig(KEY, json, trusted);
        return ParseContextConfig.getConfig(context, KEY, Tess4JConfig.class, new Tess4JConfig());
    }

    @Test
    public void testEmptyConfigMerges() throws Exception {
        assertEquals(new Tess4JConfig().getPoolSize(), resolve("{}", false).getPoolSize());
    }

    @Test
    public void testUnrelatedFieldMerges() throws Exception {
        assertTrue(resolve("{\"skipOcr\": true}", false).isSkipOcr());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"poolSize\": 7}", "{\"maxImagePixels\": 5}",
            "{\"dataPath\": \"/tmp/evil\"}", "{\"nativeLibPath\": \"/tmp/evil\"}"})
    public void testPerRequestCannotSetOperatorOnlyFields(String json) {
        Exception e = assertThrows(Exception.class, () -> resolve(json, false));
        assertTrue(e.getMessage().contains("at runtime"), e.getMessage());
    }

    @Test
    public void testOperatorConfigMaySetThem() throws Exception {
        assertEquals(7, resolve("{\"poolSize\": 7}", true).getPoolSize());
    }
}
