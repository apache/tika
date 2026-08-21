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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Package-private cache that stores bytes in memory up to a threshold,
 * then spills to a temporary file. Supports reading from any offset.
 */
class StreamCache implements Closeable {

    private static final int DEFAULT_MEMORY_THRESHOLD = 1024 * 1024; // 1MB

    // Max size of the in-memory byte[] (a single JVM array); with a budget we grow past the
    // per-object threshold but never past this.
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private final int memoryThreshold;
    private final TemporaryResources tmp;

    // Optional shared memory budget; when non-null it governs the memory-vs-spill decision
    // instead of the fixed per-object memoryThreshold.
    private final CacheMemoryBudget budget;
    private long reserved;
    // In-memory channels handed out that still reference memoryBuffer (see maybeReleaseReserved)
    private int channelPins;

    // Memory storage (null after spill)
    private byte[] memoryBuffer;
    private int memorySize;

    // File storage (null until spill)
    private String suffix;
    private Path spillFile;
    // one long-lived read handle; opening per read cost ~37x on byte-at-a-time readers
    private RandomAccessFile reader;
    private OutputStream spillOutputStream;
    private long totalSize;

    private boolean closed;

    /**
     * Suffix up front: a threshold spill precedes any getPath(suffix) call (TIKA-3903).
     * A non-null {@code budget} allows in-memory growth past the per-object threshold.
     */
    StreamCache(TemporaryResources tmp, String suffix, CacheMemoryBudget budget) {
        this(tmp, suffix, DEFAULT_MEMORY_THRESHOLD, budget);
    }

    StreamCache(TemporaryResources tmp, String suffix, int memoryThreshold, CacheMemoryBudget budget) {
        this.tmp = tmp;
        this.suffix = suffix;
        this.memoryThreshold = memoryThreshold;
        this.budget = budget;
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
            if (canKeepInMemory(1)) {
                ensureMemoryCapacity(memorySize + 1);
                memoryBuffer[memorySize++] = (byte) b;
            } else {
                spillToFile();
                spillOutputStream.write(b);
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
            if (canKeepInMemory(len)) {
                ensureMemoryCapacity(memorySize + len);
                System.arraycopy(b, off, memoryBuffer, memorySize, len);
                memorySize += len;
            } else {
                spillToFile();
                spillOutputStream.write(b, off, len);
            }
        } else {
            spillOutputStream.write(b, off, len);
        }
        totalSize += len;
    }

    /**
     * Decide whether {@code additional} more bytes can stay in memory. Content up to the
     * per-object threshold always may (budget or not, so budget exhaustion is never worse
     * than the no-budget default); beyond it, the shared budget must cover the *capacity*
     * the backing array will actually grow to (arrays grow in doubling steps, so logical
     * bytes would under-count real heap). Invariant:
     * {@code reserved == max(0, memoryBuffer.length - memoryThreshold)}.
     */
    private boolean canKeepInMemory(int additional) {
        long needed = memorySize + (long) additional;
        if (needed <= memoryThreshold) {
            return true;
        }
        if (budget == null || needed > MAX_ARRAY_SIZE) {
            return false;
        }
        if (needed <= memoryBuffer.length) {
            return true;
        }
        long targetCapacity =
                Math.min(MAX_ARRAY_SIZE, Math.max((long) memoryBuffer.length * 2, needed));
        long delta = targetCapacity - Math.max(memoryThreshold, memoryBuffer.length);
        if (budget.tryReserve(delta) != delta) {
            return false;
        }
        reserved += delta;
        return true;
    }

    /** Releases the reservation once the buffer is no longer held by this cache (spilled or
     *  closed) AND no handed-out in-memory channel still pins the array. */
    private void maybeReleaseReserved() {
        if (budget != null && reserved > 0 && channelPins == 0 &&
                (closed || memoryBuffer == null)) {
            budget.release(reserved);
            reserved = 0;
        }
    }

    private void ensureMemoryCapacity(int needed) {
        if (needed <= memoryBuffer.length) {
            return;
        }
        // With a budget, growth is capped at the reserved capacity (see canKeepInMemory)
        long cap = (budget == null) ? memoryThreshold
                : Math.min((long) memoryThreshold + reserved, MAX_ARRAY_SIZE);
        int newSize = (int) Math.min(cap, Math.max((long) memoryBuffer.length * 2, needed));
        byte[] newBuffer = new byte[newSize];
        System.arraycopy(memoryBuffer, 0, newBuffer, 0, memorySize);
        memoryBuffer = newBuffer;
    }

    private void spillToFile() throws IOException {
        if (closed) {
            throw new IOException("StreamCache is closed");
        }
        if (spillFile != null) {
            return; // Already spilled
        }

        spillFile = tmp.createTempFile(suffix);
        // registered after the file so tmp.close() releases these handles before deleting it,
        // and so a caller that disposes tmp without closing this cache does not leak them
        tmp.addResource(this);
        spillOutputStream = new BufferedOutputStream(Files.newOutputStream(spillFile));

        // Write existing memory content to file
        if (memorySize > 0) {
            spillOutputStream.write(memoryBuffer, 0, memorySize);
        }

        // Release memory buffer
        memoryBuffer = null;
        memorySize = 0;
        maybeReleaseReserved();
    }

    /**
     * Read a single byte at the given position.
     */
    int readAt(long position) throws IOException {
        if (closed) {
            throw new IOException("StreamCache is closed");
        }
        if (position < 0 || position >= totalSize) {
            return -1;
        }

        if (memoryBuffer != null) {
            return memoryBuffer[(int) position] & 0xFF;
        } else {
            flushSpillStream();
            RandomAccessFile raf = reader();
            raf.seek(position);
            return raf.read();
        }
    }

    /**
     * Read multiple bytes starting at the given position.
     */
    int readAt(long position, byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("StreamCache is closed");
        }
        if (position < 0 || position >= totalSize) {
            return -1;
        }

        int available = (int) Math.min(len, totalSize - position);

        if (memoryBuffer != null) {
            System.arraycopy(memoryBuffer, (int) position, b, off, available);
            return available;
        } else {
            flushSpillStream();
            RandomAccessFile raf = reader();
            raf.seek(position);
            return raf.read(b, off, available);
        }
    }

    /**
     * Get an InputStream that reads from the given offset.
     */
    InputStream getInputStreamFrom(long offset) throws IOException {
        return new CacheInputStream(offset);
    }

    private RandomAccessFile reader() throws IOException {
        if (reader == null) {
            reader = new RandomAccessFile(spillFile.toFile(), "r");
        }
        return reader;
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
        if (closed) {
            throw new IOException("StreamCache is closed");
        }
        if (spillFile == null) {
            spillToFile();
        }
        flushSpillStream();
        return spillFile;
    }

    /**
     * Finish writing (drain remaining source bytes) and return the file path.
     */
    Path toFile(InputStream remainingSource, String suffix) throws IOException {
        this.suffix = suffix;
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

    /**
     * If the full content is currently held in memory (not spilled, not closed), returns a
     * read-only random-access channel over it (no copy, no disk). Otherwise {@code null}.
     * The caller is responsible only for content that has actually been cached so far.
     */
    SeekableByteChannel getInMemorySeekableByteChannel() {
        if (closed || memoryBuffer == null) {
            return null;
        }
        // The channel pins the array: the budget reservation is held until this cache no
        // longer needs the buffer (spill/close) AND all handed-out channels are closed.
        channelPins++;
        return new MemorySeekableByteChannel(memoryBuffer, memorySize, () -> {
            channelPins--;
            maybeReleaseReserved();
        });
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        memoryBuffer = null;
        maybeReleaseReserved();

        if (spillOutputStream != null) {
            spillOutputStream.close();
            spillOutputStream = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
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
