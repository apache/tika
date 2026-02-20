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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Base64;

/**
 * Serializes and deserializes float vectors as base64-encoded little-endian
 * float32 byte arrays. Little-endian matches numpy/PyTorch convention so
 * vectors from Python inference servers round-trip cleanly.
 */
public final class VectorSerializer {

    private VectorSerializer() {
    }

    /**
     * Encode a float array as a base64 string (little-endian float32).
     */
    public static String encode(float[] vector) {
        ByteBuffer buf = ByteBuffer.allocate(vector.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.asFloatBuffer().put(vector);
        return Base64.getEncoder().encodeToString(buf.array());
    }

    /**
     * Decode a base64 string back to a float array (little-endian float32).
     */
    public static float[] decode(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        FloatBuffer fb = ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer();
        float[] vector = new float[fb.remaining()];
        fb.get(vector);
        return vector;
    }
}
