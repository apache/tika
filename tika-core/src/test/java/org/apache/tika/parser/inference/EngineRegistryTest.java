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
package org.apache.tika.parser.inference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class EngineRegistryTest {

    static final class Recording implements Engine {
        final IOException failure;
        boolean closed;

        Recording(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (failure != null) {
                throw failure;
            }
        }
    }

    @Test
    public void testCloseReachesEveryEngineAndRethrowsTheFirstFailure() {
        Recording a = new Recording(null);
        Recording b = new Recording(new IOException("b down"));
        Recording c = new Recording(new IOException("c down"));
        Map<String, Engine> engines = new LinkedHashMap<>();
        engines.put("a", a);
        engines.put("b", b);
        engines.put("c", c);
        engines.put("d", new Engine() { });

        IOException thrown = assertThrows(IOException.class,
                () -> new EngineRegistry(engines).close());
        assertEquals("b down", thrown.getMessage());
        assertTrue(a.closed && b.closed && c.closed, "a failure does not skip the rest");
    }
}
