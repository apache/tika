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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Backing strategy for byte array inputs.
 * <p>
 * Data is already in memory and natively seekable via array indexing.
 * No caching is needed. A temp file is only created if {@link #getPath}
 * is explicitly called.
 */
class ByteArrayBackedStrategy implements InputStreamBackingStrategy {

    private final byte[] data;
    private int position;
    private Path spilledPath;

    ByteArrayBackedStrategy(byte[] data) {
        this.data = data;
        this.position = 0;
        this.spilledPath = null;
    }

    @Override
    public int read() throws IOException {
        if (position >= data.length) {
            return -1;
        }
        return data[position++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (position >= data.length) {
            return -1;
        }
        int available = data.length - position;
        int toRead = Math.min(len, available);
        System.arraycopy(data, position, b, off, toRead);
        position += toRead;
        return toRead;
    }

    @Override
    public long skip(long n, byte[] skipBuffer) throws IOException {
        // For byte arrays, we can skip directly by advancing position
        if (n <= 0) {
            return 0;
        }
        long available = data.length - position;
        long skipped = Math.min(n, available);
        position += (int) skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return data.length - position;
    }

    @Override
    public void seekTo(long newPosition) throws IOException {
        if (newPosition < 0 || newPosition > data.length) {
            throw new IOException("Invalid seek position: " + newPosition +
                    " (length: " + data.length + ")");
        }
        this.position = (int) newPosition;
    }

    @Override
    public boolean hasPath() {
        return spilledPath != null;
    }

    @Override
    public Path getPath(TemporaryResources tmp, String suffix) throws IOException {
        if (spilledPath == null) {
            // Spill to temp file on first call
            spilledPath = tmp.createTempFile(suffix);
            try (OutputStream out = Files.newOutputStream(spilledPath)) {
                out.write(data);
            }
        }
        return spilledPath;
    }

    @Override
    public long getLength() {
        return data.length;
    }

    @Override
    public void close() throws IOException {
        // Nothing to close - byte array doesn't need cleanup
        // Temp file cleanup is handled by TemporaryResources
    }
}
