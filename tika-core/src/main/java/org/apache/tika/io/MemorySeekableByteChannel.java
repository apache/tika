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
package org.apache.tika.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * Read-only {@link SeekableByteChannel} over an in-memory byte array. Wraps the array directly
 * (no copy); callers must not mutate the backing array while the channel is in use. Used to give
 * random access to already-cached content (e.g. a small embedded zip) without spilling to disk.
 */
class MemorySeekableByteChannel implements SeekableByteChannel {

    private final byte[] data;
    private final int length;
    private int position;
    private boolean open = true;

    MemorySeekableByteChannel(byte[] data, int length) {
        this.data = data;
        this.length = length;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (position >= length) {
            return -1;
        }
        int n = Math.min(dst.remaining(), length - position);
        dst.put(data, position, n);
        position += n;
        return n;
    }

    @Override
    public int write(ByteBuffer src) {
        throw new NonWritableChannelException();
    }

    @Override
    public long position() throws IOException {
        ensureOpen();
        return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        ensureOpen();
        if (newPosition < 0) {
            throw new IllegalArgumentException("negative position: " + newPosition);
        }
        position = (int) Math.min(newPosition, length);
        return this;
    }

    @Override
    public long size() throws IOException {
        ensureOpen();
        return length;
    }

    @Override
    public SeekableByteChannel truncate(long size) {
        throw new NonWritableChannelException();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
    }

    private void ensureOpen() throws IOException {
        if (!open) {
            throw new ClosedChannelException();
        }
    }
}
