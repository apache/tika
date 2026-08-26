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
package org.apache.tika.parser.image;

import java.nio.ByteBuffer;

import com.drew.lang.BufferBoundsException;
import com.drew.lang.RandomAccessReader;

/**
 * drewnoakes random-access reader over a {@link ByteBuffer}, for content already in memory.
 * The library's own stream reader retains every chunk it reads and its TIFF reader asks for
 * the length up front, which reads the whole stream; this reads the buffer in place.
 */
final class ByteBufferReader extends RandomAccessReader {

    private final ByteBuffer buffer;

    /** Reads {@code buffer} from position 0 to its limit; the buffer's position is not used. */
    ByteBufferReader(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int toUnshiftedOffset(int localOffset) {
        return localOffset;
    }

    @Override
    public long getLength() {
        return buffer.limit();
    }

    @Override
    public byte getByte(int index) throws java.io.IOException {
        validateIndex(index, 1);
        return buffer.get(index);
    }

    @Override
    public byte[] getBytes(int index, int count) throws java.io.IOException {
        validateIndex(index, count);
        byte[] bytes = new byte[count];
        buffer.get(index, bytes);
        return bytes;
    }

    @Override
    protected boolean isValidIndex(int index, int bytesRequested) {
        return bytesRequested >= 0 && index >= 0 &&
                (long) index + bytesRequested - 1L < buffer.limit();
    }

    @Override
    protected void validateIndex(int index, int bytesRequested) throws java.io.IOException {
        if (!isValidIndex(index, bytesRequested)) {
            throw new BufferBoundsException(index, bytesRequested, buffer.limit());
        }
    }
}
