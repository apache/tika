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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

public class PluginJsonTest {

    enum Mode { FAST, SLOW }

    public record Sample(String name, long count, Mode mode) { }

    public static class Bean {
        private int size;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    @Test
    public void testCommentsAccepted() throws Exception {
        Sample s = PluginJson.read("""
                // leading
                { /* block */ "name": "a", "count": 2, "mode": "FAST" } // trailing
                """, Sample.class);
        assertEquals(new Sample("a", 2, Mode.FAST), s);
    }

    @Test
    public void testRejected() {
        String[] cases = {
                "{\"name\":\"a\",\"count\":2,\"mode\":\"FAST\",\"typo\":1}",   // unknown key
                "{\"name\":\"a\",\"count\":2,\"mode\":0}",                   // numeric enum
                "{\"name\":\"a\",\"count\":2,\"count\":3,\"mode\":\"FAST\"}"  // duplicate key
        };
        for (String json : cases) {
            TikaConfigException e = assertThrows(TikaConfigException.class,
                    () -> PluginJson.read(json, Sample.class), json);
            assertTrue(e.getMessage().startsWith("Failed to parse Sample"), e.getMessage());
        }
    }

    @Test
    public void testMissingPrimitiveTakesDefault() throws Exception {
        // Optional primitives are the norm in plugin configs; absence must not be an error.
        assertEquals(0, PluginJson.read("{\"name\":\"a\",\"mode\":\"SLOW\"}", Sample.class).count());
    }

    @Test
    public void testDuplicateKeyRejectedForBeans() {
        // Bean setters would silently take the last value without STRICT_DUPLICATE_DETECTION.
        assertThrows(TikaConfigException.class,
                () -> PluginJson.read("{\"size\":1,\"size\":2}", Bean.class));
    }
}
