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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class VectorSerializerTest {

    @Test
    void testRoundTrip() {
        float[] original = {0.1f, -0.5f, 3.14f, 0.0f, -99.99f};
        String encoded = VectorSerializer.encode(original);
        float[] decoded = VectorSerializer.decode(encoded);
        assertArrayEquals(original, decoded, 1e-6f);
    }

    @Test
    void testEmptyVector() {
        float[] empty = {};
        String encoded = VectorSerializer.encode(empty);
        float[] decoded = VectorSerializer.decode(encoded);
        assertEquals(0, decoded.length);
    }

    @Test
    void testLargeVector() {
        float[] large = new float[768];
        for (int i = 0; i < large.length; i++) {
            large[i] = (float) Math.sin(i * 0.01);
        }
        String encoded = VectorSerializer.encode(large);
        assertNotNull(encoded);

        float[] decoded = VectorSerializer.decode(encoded);
        assertArrayEquals(large, decoded, 1e-6f);

        // 768 * 4 bytes = 3072 bytes → base64 should be ~4096 chars
        assertEquals(4096, encoded.length());
    }
}
