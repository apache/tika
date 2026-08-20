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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.function.IOSupplier;

/**
 * Input source backed by a re-openable stream supplier (e.g. an entry in a
 * random-access {@code ZipFile}, which can be re-opened via
 * {@code zipFile.getInputStream(entry)}).
 * <p>
 * Because the underlying content can be re-read on demand, rewinding simply
 * re-opens the source, avoiding the memory-then-disk caching that
 * {@link CachingSource} performs for one-shot streams. A temp file is created
 * only if {@link #getPath} is called or {@link #getSeekableByteChannel()} is
 * asked for content that does not fit in memory.
 */
class ReopenableSource extends InputStream implements TikaInputSource {

    // Per-object bytes that may be buffered in memory without a budget reservation;
    // matches StreamCache's default per-object threshold.
    private static final int IN_MEMORY_FLOOR = 1024 * 1024;

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private final IOSupplier<InputStream> opener;
    private final TemporaryResources tmp;
    private final String suffix;
    private long length;

    private InputStream currentStream; // lazily opened
    private long position;
    private Path spilledPath;
    private long markPosition = -1;

    private CacheMemoryBudget budget;
    // Full content retained after an in-memory drain, with its budget reservation,
    // so repeated channel requests don't re-read (re-decompress) the entry.
    private byte[] retainedBuffer;
    private int retainedLength;
    private long retainedReservation;

    ReopenableSource(IOSupplier<InputStream> opener, TemporaryResources tmp, long length,
                     String suffix) {
        this.opener = opener;
        this.tmp = tmp;
        this.length = length;
        this.suffix = suffix;
        this.position = 0;
    }

    private void ensureOpen() throws IOException {
        if (currentStream == null) {
            currentStream = new BufferedInputStream(
                    spilledPath != null ? Files.newInputStream(spilledPath) : opener.get());
        }
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        int b = currentStream.read();
        if (b != -1) {
            position++;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        int n = currentStream.read(b, off, len);
        if (n > 0) {
            position += n;
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        ensureOpen();
        long skipped = IOUtils.skip(currentStream, n);
        position += skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        ensureOpen();
        return currentStream.available();
    }

    @Override
    public void seekTo(long newPosition) throws IOException {
        if (newPosition < 0) {
            throw new IOException("Cannot seek to negative position: " + newPosition);
        }
        if (currentStream != null) {
            currentStream.close();
        }
        currentStream = new BufferedInputStream(
                spilledPath != null ? Files.newInputStream(spilledPath) : opener.get());
        if (newPosition > 0) {
            IOUtils.skipFully(currentStream, newPosition);
        }
        this.position = newPosition;
    }

    @Override
    public boolean hasPath() {
        return spilledPath != null;
    }

    @Override
    public Path getPath(String suffix) throws IOException {
        if (spilledPath == null) {
            Path p = tmp.createTempFile(suffix == null ? this.suffix : suffix);
            try (OutputStream out = Files.newOutputStream(p)) {
                if (retainedBuffer != null) {
                    out.write(retainedBuffer, 0, retainedLength);
                } else {
                    try (InputStream in = opener.get()) {
                        IOUtils.copy(in, out);
                    }
                }
            }
            spilledPath = p;
            // The spooled size is ground truth, even over a lying declared length
            length = Files.size(p);
        }
        return spilledPath;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public void enableRewind(CacheMemoryBudget budget) throws IOException {
        if (position != 0) {
            throw new IOException("Cannot enable rewind: position is " + position +
                    ", must be 0. Call enableRewind() before reading.");
        }
        // No caching needed to rewind (the source re-opens); the budget is kept for
        // on-demand in-memory buffering in getSeekableByteChannel().
        if (this.budget == null) {
            this.budget = budget;
        }
    }

    @Override
    public SeekableByteChannel getSeekableByteChannel() throws IOException {
        if (retainedBuffer != null) {
            return new MemorySeekableByteChannel(retainedBuffer, retainedLength);
        }
        if (spilledPath == null && tryBufferInMemory()) {
            return new MemorySeekableByteChannel(retainedBuffer, retainedLength);
        }
        return FileChannel.open(getPath(null), StandardOpenOption.READ);
    }

    /**
     * Drains a fresh stream into memory if it fits within the per-object floor plus what
     * can be reserved from the shared budget, retaining the buffer (and its reservation)
     * until {@link #close()}. The declared length is a sizing hint only -- it can lie, so
     * the cap is enforced during the read. Does not disturb this source's read position.
     */
    private boolean tryBufferInMemory() throws IOException {
        if (length > MAX_ARRAY_SIZE || (length > IN_MEMORY_FLOOR && budget == null)) {
            return false;
        }
        long reservedHere = 0;
        // Reservation invariant: reservedHere == max(0, data.length - IN_MEMORY_FLOOR)
        if (length > IN_MEMORY_FLOOR) {
            long extra = length - IN_MEMORY_FLOOR;
            if (budget.tryReserve(extra) != extra) {
                return false;
            }
            reservedHere = extra;
        }
        byte[] data = new byte[length > 0 ? (int) length : 8192];
        int total = 0;
        boolean fits = false;
        try (InputStream in = opener.get()) {
            while (true) {
                if (total == data.length) {
                    int peek = in.read();
                    if (peek == -1) {
                        fits = true;
                        break;
                    }
                    long newCapacity =
                            Math.min(MAX_ARRAY_SIZE, Math.max((long) data.length * 2, 8192));
                    if (newCapacity > IN_MEMORY_FLOOR) {
                        if (budget == null) {
                            newCapacity = IN_MEMORY_FLOOR;
                        } else {
                            long delta = newCapacity - Math.max(data.length, IN_MEMORY_FLOOR);
                            if (delta > 0) {
                                if (budget.tryReserve(delta) != delta) {
                                    break;
                                }
                                reservedHere += delta;
                            }
                        }
                    }
                    if (newCapacity <= data.length) {
                        break;
                    }
                    data = Arrays.copyOf(data, (int) newCapacity);
                    data[total++] = (byte) peek;
                    continue;
                }
                int r = in.read(data, total, data.length - total);
                if (r == -1) {
                    fits = true;
                    break;
                }
                total += r;
            }
        } finally {
            if (!fits && reservedHere > 0) {
                budget.release(reservedHere);
            }
        }
        if (!fits) {
            return false;
        }
        // Trim over-allocation from a lying declared length, releasing the excess reservation
        if (data.length - total > 8192) {
            data = Arrays.copyOf(data, Math.max(total, 1));
            long target = Math.max(0, (long) data.length - IN_MEMORY_FLOOR);
            if (reservedHere > target) {
                budget.release(reservedHere - target);
                reservedHere = target;
            }
        }
        retainedBuffer = data;
        retainedLength = total;
        retainedReservation = reservedHere;
        // The full read is ground truth, even over a lying declared length
        length = total;
        return true;
    }

    @Override
    public void close() throws IOException {
        if (retainedReservation > 0) {
            budget.release(retainedReservation);
            retainedReservation = 0;
        }
        retainedBuffer = null;
        if (currentStream != null) {
            currentStream.close();
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        markPosition = position;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (markPosition < 0) {
            throw new IOException("Mark not set");
        }
        seekTo(markPosition);
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}
