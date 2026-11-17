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
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

public class EncodingProbeCacheTest {

    @Test
    public void kindIsPartOfTheKey() {
        EncodingProbeCache cache = new EncodingProbeCache();
        byte[] probe = {'a'};
        cache.put(probe, "evidence", 16384, 524288);
        assertSame(probe, cache.get("evidence", 16384, 524288));
        assertNull(cache.get(16384, 524288));
        assertNull(cache.get("evidence", 16384, 1024));
        cache.put(probe, 16384, 524288);
        assertSame(probe, cache.get(EncodingProbeCache.PREFIX, 16384, 524288));
        assertNull(cache.get("evidence", 16384, 524288));
    }
}
