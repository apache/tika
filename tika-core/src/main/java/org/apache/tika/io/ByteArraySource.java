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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Input source backed by a byte array.
 * <p>
 * Data is already in memory and natively seekable via array indexing.
 * No caching is needed. A temp file is only created if {@link #getPath}
 * is explicitly called.
 * <p>
 * Would prefer to extend UnsynchronizedByteArrayInputStream, but we need direct
 * access to the internals.
 */
class ByteArraySource extends InputStream implements TikaInputSource {

    private final byte[] data;
    private final int length;
    private final TemporaryResources tmp;
    private int position;
    private Path spilledPath;

    ByteArraySource(byte[] data, TemporaryResources tmp) {
        this.data = data;
        this.length = data.length;
        this.tmp = tmp;
        this.position = 0;
        this.spilledPath = null;
    }

    @Override
    public int read() {
        if (position >= length) {
            return -1;
        }
        return data[position++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (position >= length) {
            return -1;
        }
        int available = length - position;
        int toRead = Math.min(len, available);
        System.arraycopy(data, position, b, off, toRead);
        position += toRead;
        return toRead;
    }

    @Override
    public long skip(long n) {
        if (n <= 0) {
            return 0;
        }
        int available = length - position;
        int toSkip = (int) Math.min(n, available);
        position += toSkip;
        return toSkip;
    }

    @Override
    public int available() {
        return length - position;
    }

    @Override
    public void seekTo(long newPosition) throws IOException {
        if (newPosition < 0 || newPosition > length) {
            throw new IOException("Invalid seek position: " + newPosition +
                    " (length: " + length + ")");
        }
        this.position = (int) newPosition;
    }

    @Override
    public boolean hasPath() {
        return spilledPath != null;
    }

    @Override
    public Path getPath(String suffix) throws IOException {
        if (spilledPath == null) {
            // Spill to temp file on first call
            spilledPath = tmp.createTempFile(suffix);
            try (OutputStream out = Files.newOutputStream(spilledPath)) {
                out.write(data, 0, length);
            }
        }
        return spilledPath;
    }

    @Override
    public long getLength() {
        return length;
    }
}
