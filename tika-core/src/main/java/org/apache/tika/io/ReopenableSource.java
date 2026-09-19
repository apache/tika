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
import java.io.ByteArrayInputStream;
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
    // how far past a mark the open stream buffers before a reset falls back to a re-open
    private static final int MAX_BUFFERED_MARK = 1024 * 1024;

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private final IOSupplier<InputStream> opener;
    private final TemporaryResources tmp;
    private final String suffix;
    private long length;
    // getLength() is a declared hint until a drain/spill measures it
    private boolean lengthMeasured;

    private InputStream currentStream; // lazily opened
    private long position;
    private Path spilledPath;
    private long markPosition = -1;
    // the current stream's own mark is valid (set after it was opened, not yet invalidated)
    private boolean markInStream;

    private CacheMemoryBudget budget;
    // Full content retained after an in-memory drain, with its budget reservation,
    // so repeated channel requests don't re-read (re-decompress) the entry.
    private byte[] retainedBuffer;
    private int retainedLength;
    private long retainedReservation;
    private int channelPins;
    private boolean closed;

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
            currentStream = openAt(position);
        }
    }

    /**
     * A fresh stream positioned at {@code at}. Served from the retained content when there is
     * some: re-opening this source can mean inflating a zip entry again, which the page cache
     * does nothing for.
     */
    private InputStream openAt(long at) throws IOException {
        if (retainedBuffer != null && spilledPath == null) {
            int from = (int) Math.min(at, retainedLength);
            return new ByteArrayInputStream(retainedBuffer, from, retainedLength - from);
        }
        InputStream in = new BufferedInputStream(
                spilledPath != null ? Files.newInputStream(spilledPath) : opener.get());
        if (at > 0) {
            IOUtils.skipFully(in, at);
        }
        return in;
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
        currentStream = openAt(newPosition);
        markInStream = false;
        this.position = newPosition;
    }

    @Override
    public Path materializedPath() {
        return spilledPath;
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
            lengthMeasured = true;
        }
        return spilledPath;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public boolean hasReliableLength() {
        return lengthMeasured;
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

    /**
     * Drains into memory up front so the caller's read, and every later one, is served from
     * there. Only worth it when the content certainly fits: a doomed attempt reads as far as
     * the budget allows and throws it away, and {@link #getSeekableByteChannel()} would spill
     * instead, which is what TIKA-4835 removed.
     */
    @Override
    public boolean tryRetainInMemory() throws IOException {
        if (retainedBuffer != null) {
            return true;
        }
        if (spilledPath != null || closed || length < 0 || length > MAX_ARRAY_SIZE) {
            return false;
        }
        if (length > IN_MEMORY_FLOOR) {
            if (budget == null ||
                    budget.getMaxBytes() - budget.getReservedBytes() < length - IN_MEMORY_FLOOR) {
                return false;
            }
        }
        if (!tryBufferInMemory()) {
            return false;
        }
        if (currentStream != null) {
            currentStream.close();
            currentStream = openAt(position);
        }
        return true;
    }

    @Override
    public SeekableByteChannel getSeekableByteChannel() throws IOException {
        if (retainedBuffer != null) {
            return retainedChannel();
        }
        if (spilledPath == null && tryBufferInMemory()) {
            return retainedChannel();
        }
        return FileChannel.open(getPath(null), StandardOpenOption.READ);
    }

    /** Channels pin the retained buffer: the reservation is released only once this source
     *  is closed AND no handed-out channel still references the array. */
    private SeekableByteChannel retainedChannel() {
        channelPins++;
        return new MemorySeekableByteChannel(retainedBuffer, retainedLength, () -> {
            channelPins--;
            maybeReleaseRetained();
        });
    }

    private void dropRetained() {
        retainedBuffer = null;
        maybeReleaseRetained();
    }

    private void maybeReleaseRetained() {
        if ((closed || retainedBuffer == null) && channelPins == 0 && retainedReservation > 0) {
            budget.release(retainedReservation);
            retainedReservation = 0;
        }
    }

    /**
     * Drains a fresh stream into memory if it fits within the per-object floor plus what
     * can be reserved from the shared budget, retaining the buffer (and its reservation)
     * until {@link #close()}. The declared length is the file's claim: it may skip an
     * attempt that cannot succeed (a lie there costs a spill, nothing more), but it never
     * sizes a reservation or an allocation -- the buffer starts at the floor at most and
     * grows, reserving, on what is actually read. Does not disturb this source's read
     * position.
     */
    private boolean tryBufferInMemory() throws IOException {
        if (length > MAX_ARRAY_SIZE || (length > IN_MEMORY_FLOOR && budget == null)) {
            return false;
        }
        long reservedHere = 0;
        // Reservation invariant: reservedHere == max(0, data.length - IN_MEMORY_FLOOR)
        byte[] data = new byte[(int) Math.max(8192, Math.min(length, IN_MEMORY_FLOOR))];
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
        if (tmp != null && reservedHere > 0) {
            // tmp owns the retention: disposing tmp without closing this source still releases
            // the reservation (once no handed-out channel pins the array)
            tmp.addResource(this::dropRetained);
        }
        // The full read is ground truth, even over a lying declared length
        length = total;
        lengthMeasured = true;
        return true;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        retainedBuffer = null;
        maybeReleaseRetained();
        if (currentStream != null) {
            currentStream.close();
        }
    }

    /**
     * A mark is kept in the open stream's buffer, so a reset within {@code readlimit} costs
     * nothing. Re-opening on every reset is what a reader that marks and resets per record (POI
     * reading a metafile's bitmaps) turned into inflating a zip entry thousands of times.
     */
    @Override
    public synchronized void mark(int readlimit) {
        markPosition = position;
        markInStream = false;
        if (currentStream != null && retainedBuffer == null) {
            // the buffer grows to honour a mark; past the cap a reset re-opens instead
            currentStream.mark(Math.min(readlimit, MAX_BUFFERED_MARK));
            markInStream = true;
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        if (markPosition < 0) {
            throw new IOException("Mark not set");
        }
        if (markInStream && currentStream != null) {
            try {
                currentStream.reset();
                position = markPosition;
                return;
            } catch (IOException e) {
                // read past the readlimit: the buffer no longer holds the mark, re-open instead
            }
        }
        seekTo(markPosition);
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}
