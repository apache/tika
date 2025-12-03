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
package org.apache.tika.plugins;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;

public class TikaConfigsTest {

    @Test
    public void testValidKnownKeysPass() {
        String json = """
                {
                    "fetchers": {},
                    "emitters": {},
                    "pipes-iterator": {},
                    "pipes-reporters": {},
                    "async": {},
                    "plugin-roots": "target/plugins"
                }
                """;

        assertDoesNotThrow(() -> loadFromString(json));
    }

    @Test
    public void testUnknownKeyThrows() {
        String json = """
                {
                    "fetchers": {},
                    "pipes-reporter": {}
                }
                """;

        TikaConfigException ex = assertThrows(TikaConfigException.class,
                () -> loadFromString(json));

        assertTrue(ex.getMessage().contains("pipes-reporter"));
        assertTrue(ex.getMessage().contains("Unknown pipes config key"));
    }

    @Test
    public void testTypoInKeyThrows() {
        String json = """
                {
                    "fethcers": {}
                }
                """;

        TikaConfigException ex = assertThrows(TikaConfigException.class,
                () -> loadFromString(json));

        assertTrue(ex.getMessage().contains("fethcers"));
    }

    @Test
    public void testExtensionKeyWithXPrefixAllowed() {
        String json = """
                {
                    "fetchers": {},
                    "x-custom-extension": {
                        "setting": "value"
                    },
                    "x-another-custom": {}
                }
                """;

        assertDoesNotThrow(() -> loadFromString(json));
    }

    @Test
    public void testEmptyConfigPasses() {
        String json = "{}";

        assertDoesNotThrow(() -> loadFromString(json));
    }

    @Test
    public void testSingleValidKeyPasses() {
        String json = """
                {
                    "plugin-roots": ["path1", "path2"]
                }
                """;

        assertDoesNotThrow(() -> loadFromString(json));
    }

    @Test
    public void testErrorMessageIncludesValidKeys() {
        String json = """
                {
                    "bad-key": {}
                }
                """;

        TikaConfigException ex = assertThrows(TikaConfigException.class,
                () -> loadFromString(json));

        assertTrue(ex.getMessage().contains("fetchers"));
        assertTrue(ex.getMessage().contains("emitters"));
        assertTrue(ex.getMessage().contains("x-"));
    }

    @Test
    public void testGetRootReturnsJsonNode() throws Exception {
        String json = """
                {
                    "fetchers": {
                        "file-system-fetcher": {}
                    }
                }
                """;

        TikaConfigs configs = loadFromString(json);
        assertNotNull(configs.getTikaJsonConfig().getRootNode());
        assertNotNull(configs.getTikaJsonConfig().getRootNode().get("fetchers"));
    }

    private TikaConfigs loadFromString(String json) throws Exception {
        return TikaConfigs.load(TikaJsonConfig.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }
}
