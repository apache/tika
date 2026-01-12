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

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Package-private cache that stores bytes in memory up to a threshold,
 * then spills to a temporary file. Supports reading from any offset.
 */
class StreamCache implements Closeable {

    private static final int DEFAULT_MEMORY_THRESHOLD = 1024 * 1024; // 1MB

    private final int memoryThreshold;
    private final TemporaryResources tmp;

    // Memory storage (null after spill)
    private byte[] memoryBuffer;
    private int memorySize;

    // File storage (null until spill)
    private Path spillFile;
    private OutputStream spillOutputStream;
    private long totalSize;

    private boolean closed;

    StreamCache(TemporaryResources tmp) {
        this(tmp, DEFAULT_MEMORY_THRESHOLD);
    }

    StreamCache(TemporaryResources tmp, int memoryThreshold) {
        this.tmp = tmp;
        this.memoryThreshold = memoryThreshold;
        this.memoryBuffer = new byte[Math.min(memoryThreshold, 8192)];
        this.memorySize = 0;
        this.totalSize = 0;
    }

    /**
     * Append a single byte to the cache.
     */
    void append(int b) throws IOException {
        if (closed) {
            throw new IOException("StreamCache is closed");
        }

        if (memoryBuffer != null) {
            // Still in memory mode
            if (memorySize >= memoryThreshold) {
                spillToFile();
                spillOutputStream.write(b);
            } else {
                ensureMemoryCapacity(memorySize + 1);
                memoryBuffer[memorySize++] = (byte) b;
            }
        } else {
            // Already spilled to file
            spillOutputStream.write(b);
        }
        totalSize++;
    }

    /**
     * Append multiple bytes to the cache.
     */
    void append(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("StreamCache is closed");
        }

        if (memoryBuffer != null) {
            if (memorySize + len > memoryThreshold) {
                spillToFile();
                spillOutputStream.write(b, off, len);
            } else {
                ensureMemoryCapacity(memorySize + len);
                System.arraycopy(b, off, memoryBuffer, memorySize, len);
                memorySize += len;
            }
        } else {
            spillOutputStream.write(b, off, len);
        }
        totalSize += len;
    }

    private void ensureMemoryCapacity(int needed) {
        if (needed <= memoryBuffer.length) {
            return;
        }
        int newSize = Math.min(memoryThreshold, Math.max(memoryBuffer.length * 2, needed));
        byte[] newBuffer = new byte[newSize];
        System.arraycopy(memoryBuffer, 0, newBuffer, 0, memorySize);
        memoryBuffer = newBuffer;
    }

    private void spillToFile() throws IOException {
        if (spillFile != null) {
            return; // Already spilled
        }

        spillFile = tmp.createTempFile((String) null);
        spillOutputStream = new BufferedOutputStream(Files.newOutputStream(spillFile));

        // Write existing memory content to file
        if (memorySize > 0) {
            spillOutputStream.write(memoryBuffer, 0, memorySize);
        }

        // Release memory buffer
        memoryBuffer = null;
        memorySize = 0;
    }

    /**
     * Read a single byte at the given position.
     */
    int readAt(long position) throws IOException {
        if (position < 0 || position >= totalSize) {
            return -1;
        }

        if (memoryBuffer != null) {
            return memoryBuffer[(int) position] & 0xFF;
        } else {
            flushSpillStream();
            try (RandomAccessFile raf = new RandomAccessFile(spillFile.toFile(), "r")) {
                raf.seek(position);
                return raf.read();
            }
        }
    }

    /**
     * Read multiple bytes starting at the given position.
     */
    int readAt(long position, byte[] b, int off, int len) throws IOException {
        if (position < 0 || position >= totalSize) {
            return -1;
        }

        int available = (int) Math.min(len, totalSize - position);

        if (memoryBuffer != null) {
            System.arraycopy(memoryBuffer, (int) position, b, off, available);
            return available;
        } else {
            flushSpillStream();
            try (RandomAccessFile raf = new RandomAccessFile(spillFile.toFile(), "r")) {
                raf.seek(position);
                return raf.read(b, off, available);
            }
        }
    }

    /**
     * Get an InputStream that reads from the given offset.
     */
    InputStream getInputStreamFrom(long offset) throws IOException {
        return new CacheInputStream(offset);
    }

    private void flushSpillStream() throws IOException {
        if (spillOutputStream != null) {
            spillOutputStream.flush();
        }
    }

    /**
     * Force all content to a file and return the path.
     * After this call, the cache is in file-backed mode.
     */
    Path toFile() throws IOException {
        if (spillFile == null) {
            spillToFile();
        }
        flushSpillStream();
        return spillFile;
    }

    /**
     * Finish writing (drain remaining source bytes) and return the file path.
     */
    Path toFile(InputStream remainingSource) throws IOException {
        // Copy remaining bytes from source
        byte[] buffer = new byte[8192];
        int n;
        while ((n = remainingSource.read(buffer)) != -1) {
            append(buffer, 0, n);
        }
        return toFile();
    }

    /**
     * Number of bytes currently cached.
     */
    long size() {
        return totalSize;
    }

    /**
     * Whether the cache has spilled to a file.
     */
    boolean isFileBacked() {
        return spillFile != null;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        memoryBuffer = null;

        if (spillOutputStream != null) {
            spillOutputStream.close();
            spillOutputStream = null;
        }
        // spillFile cleanup is handled by TemporaryResources
    }

    /**
     * Inner class for reading from the cache at a specific offset.
     */
    private class CacheInputStream extends InputStream {
        private long position;

        CacheInputStream(long startOffset) {
            this.position = startOffset;
        }

        @Override
        public int read() throws IOException {
            if (position >= totalSize) {
                return -1;
            }
            int b = readAt(position);
            if (b != -1) {
                position++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (position >= totalSize) {
                return -1;
            }
            int n = StreamCache.this.readAt(position, b, off, len);
            if (n > 0) {
                position += n;
            }
            return n;
        }
    }
}
