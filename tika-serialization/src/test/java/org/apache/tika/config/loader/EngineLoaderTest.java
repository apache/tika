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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.inference.EngineRegistry;

public class EngineLoaderTest {

    @TempDir
    Path tmp;

    private TikaLoader load(String json) throws Exception {
        Path config = Files.createTempFile(tmp, "tika-config", ".json");
        Files.writeString(config, json);
        return TikaLoader.load(config);
    }

    @Test
    public void testEnginesLoadByName() throws Exception {
        TikaLoader loader = load("{ \"engines\": {"
                + " \"one\": { \"test-engine\": { \"label\": \"first\" } },"
                + " \"two\": { \"test-engine\": { \"label\": \"second\" } } } }");
        EngineRegistry engines = loader.get(EngineRegistry.class);
        assertEquals("first", ((TestEngine) engines.get("one")).getLabel());
        assertEquals("second", ((TestEngine) engines.get("two")).getLabel());
        assertNull(engines.get("three"));
        assertEquals(2, engines.getEngines().size());
    }

    @Test
    public void testAbsentSectionLoadsNothing() throws Exception {
        assertNull(load("{ \"parsers\": [ { \"default-parser\": {} } ] }")
                .get(EngineRegistry.class));
    }

    @Test
    public void testMisconfigurationsFailLoad() throws Exception {
        String[] bad = {
            "{ \"engines\": [ { \"test-engine\": {} } ] }",
            "{ \"engines\": { \"one\": { \"test-engine\": {}, \"test-engine-2\": {} } } }",
            "{ \"engines\": { \"one\": {} } }",
            "{ \"engines\": { \"bad name\": { \"test-engine\": {} } } }",
            "{ \"engines\": { \"one\": { \"no-such-engine\": {} } } }",
            "{ \"engines\": { \"one\": { \"test-engine\": { \"nope\": 1 } } } }",
        };
        for (String json : bad) {
            TikaConfigException e = assertThrows(TikaConfigException.class,
                    () -> load(json).get(EngineRegistry.class), json);
            assertNotNull(e.getMessage());
        }
    }
}
