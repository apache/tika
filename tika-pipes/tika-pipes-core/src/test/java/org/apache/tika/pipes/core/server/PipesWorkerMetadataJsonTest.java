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
package org.apache.tika.pipes.core.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;

public class PipesWorkerMetadataJsonTest {

    /** The unpack metadata files say U+FFFD for a lone surrogate, as /rmeta does; the Metadata itself is untouched. */
    @Test
    public void testLoneSurrogateBecomesReplacementCharacter() {
        Metadata metadata = new Metadata();
        metadata.set("dc:title", "t \uD800 end");
        metadata.add("dc:subject", "one");
        metadata.add("dc:subject", "two \uDC00");

        Map<String, Object> map = PipesWorker.metadataMap(metadata);

        assertEquals("t \uFFFD end", map.get("dc:title"));
        assertArrayEquals(new String[]{"one", "two \uFFFD"}, (String[]) map.get("dc:subject"));
        assertEquals("t \uD800 end", metadata.get("dc:title"), "the live values are not rewritten");
        assertEquals("two \uDC00", metadata.getValues("dc:subject")[1]);

        metadata.add("dc:creator", "fine");
        metadata.add("dc:creator", "also fine");
        assertSame(metadata.getValues("dc:creator"), PipesWorker.metadataMap(metadata).get("dc:creator"),
                "nothing to replace: no copy");
    }
}
