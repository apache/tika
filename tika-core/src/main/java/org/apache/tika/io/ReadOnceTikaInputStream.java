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
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import org.apache.tika.metadata.Metadata;

/**
 * A lightweight TikaInputStream for single-pass reading.
 * <p>
 * This class provides basic buffered stream functionality without the overhead
 * of caching all bytes read. It is suitable for use cases where you only need
 * to read through the stream once, such as detection-only scenarios.
 * <p>
 * Limited mark/reset support is provided via the underlying {@link BufferedInputStream},
 * but only within its buffer size (typically 8KB). Methods that require full
 * stream caching ({@link #getPath()}, {@link #rewind()}) will throw
 * {@link UnsupportedOperationException}.
 * <p>
 * For full rewind and file-spooling capabilities, use {@link TikaInputStream} instead.
 */
public class ReadOnceTikaInputStream extends InputStream {

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final int MAX_CONSECUTIVE_EOFS = 1000;

    private final BufferedInputStream in;
    private final int bufferSize;
    private long position = 0;
    private long mark = -1;
    private Object openContainer;
    private int consecutiveEOFs = 0;
    private int closeShieldDepth = 0;
    private long length;

    private ReadOnceTikaInputStream(InputStream stream, int bufferSize, long length) {
        this.bufferSize = bufferSize;
        this.in = stream instanceof BufferedInputStream
                ? (BufferedInputStream) stream
                : new BufferedInputStream(stream, bufferSize);
        this.length = length;
    }

    // ========== Static Factory Methods ==========

    public static ReadOnceTikaInputStream get(InputStream stream) {
        return get(stream, DEFAULT_BUFFER_SIZE);
    }

    public static ReadOnceTikaInputStream get(InputStream stream, int bufferSize) {
        if (stream == null) {
            throw new NullPointerException("The Stream must not be null");
        }
        if (stream instanceof ReadOnceTikaInputStream) {
            return (ReadOnceTikaInputStream) stream;
        }
        return new ReadOnceTikaInputStream(stream, bufferSize, -1);
    }

    public static ReadOnceTikaInputStream get(InputStream stream, Metadata metadata) {
        return get(stream, DEFAULT_BUFFER_SIZE);
    }

    public static ReadOnceTikaInputStream get(byte[] data) {
        return new ReadOnceTikaInputStream(
                new java.io.ByteArrayInputStream(data),
                DEFAULT_BUFFER_SIZE,
                data.length
        );
    }

    // ========== InputStream Methods ==========

    @Override
    public int read() throws IOException {
        int b = in.read();
        if (b != -1) {
            position++;
            consecutiveEOFs = 0;
        } else {
            consecutiveEOFs++;
            if (consecutiveEOFs > MAX_CONSECUTIVE_EOFS) {
                throw new IOException("Read too many -1 (EOFs); there could be an infinite loop. " +
                        "If you think your file is not corrupt, please open an issue on Tika's JIRA");
            }
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = in.read(b, off, len);
        if (n > 0) {
            position += n;
            consecutiveEOFs = 0;
        } else if (n == -1) {
            consecutiveEOFs++;
            if (consecutiveEOFs > MAX_CONSECUTIVE_EOFS) {
                throw new IOException("Read too many -1 (EOFs); there could be an infinite loop. " +
                        "If you think your file is not corrupt, please open an issue on Tika's JIRA");
            }
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        // Must read bytes (not skip) to ensure they're in BufferedInputStream's buffer
        // for mark/reset to work correctly
        if (skipBuffer == null) {
            skipBuffer = new byte[4096];
        }
        long skipped = IOUtils.skip(in, n, skipBuffer);
        position += skipped;
        return skipped;
    }

    private byte[] skipBuffer;

    @Override
    public int available() throws IOException {
        return in.available();
    }

    /**
     * Marks the current position in the stream.
     * <p>
     * Note: The mark is only valid within the buffer size limit (default 8KB).
     * Reading beyond this limit will invalidate the mark.
     *
     * @param readlimit the maximum number of bytes that can be read before
     *                  the mark becomes invalid. This is limited by the buffer size.
     */
    @Override
    public void mark(int readlimit) {
        if (readlimit > bufferSize) {
            throw new IllegalArgumentException(
                    "Mark readlimit " + readlimit + " exceeds buffer size " + bufferSize +
                            ". Use TikaInputStream for larger mark limits.");
        }
        in.mark(readlimit);
        mark = position;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    /**
     * Resets the stream to the previously marked position.
     * <p>
     * Note: This only works if the mark is still valid (i.e., fewer than
     * readlimit bytes have been read since mark() was called).
     *
     * @throws IOException if the mark has been invalidated or was never set
     */
    @Override
    public void reset() throws IOException {
        in.reset();
        position = mark;
        mark = -1;
        consecutiveEOFs = 0;
    }

    @Override
    public void close() throws IOException {
        if (closeShieldDepth > 0) {
            return;
        }
        in.close();
    }

    // ========== TikaInputStream-compatible Methods ==========

    /**
     * Fills the given buffer with upcoming bytes without advancing position.
     * <p>
     * Note: This only works if the peek size is within the buffer limit.
     *
     * @param buffer byte buffer to fill
     * @return number of bytes written to the buffer
     * @throws IOException if the stream cannot be read
     * @throws IllegalArgumentException if peek size exceeds buffer limit
     */
    public int peek(byte[] buffer) throws IOException {
        int n = 0;
        mark(buffer.length);  // throws IllegalArgumentException if > bufferSize

        int m = read(buffer);
        while (m != -1) {
            n += m;
            if (n < buffer.length) {
                m = read(buffer, n, buffer.length - n);
            } else {
                m = -1;
            }
        }

        reset();
        return n;
    }

    public Object getOpenContainer() {
        return openContainer;
    }

    public void setOpenContainer(Object container) {
        openContainer = container;
    }

    public void addCloseableResource(Closeable closeable) {
        throw new UnsupportedOperationException(
                "ReadOnceTikaInputStream does not track resources. " +
                        "Use TikaInputStream.get() for resource tracking.");
    }

    /**
     * @return always false - ReadOnceTikaInputStream does not support file backing
     */
    public boolean hasFile() {
        return false;
    }

    /**
     * Returns {@code null} because ReadOnceTikaInputStream does not support
     * spilling to a file. Detectors and parsers that require file access
     * should check for null and handle gracefully.
     *
     * @return always {@code null}
     */
    public Path getPath() throws IOException {
        return null;
    }

    /**
     * Returns {@code null} because ReadOnceTikaInputStream does not support
     * spilling to a file. Detectors and parsers that require file access
     * should check for null and handle gracefully.
     *
     * @return always {@code null}
     */
    public File getFile() throws IOException {
        return null;
    }

    /**
     * Not supported. Use {@link TikaInputStream} for file access.
     *
     * @throws UnsupportedOperationException always
     */
    public FileChannel getFileChannel() throws IOException {
        throw new UnsupportedOperationException(
                "ReadOnceTikaInputStream does not support getFileChannel(). " +
                        "Use TikaInputStream.get() for file access.");
    }

    /**
     * Not supported. Use {@link TikaInputStream} for rewind capability.
     *
     * @throws UnsupportedOperationException always
     */
    public void rewind() throws IOException {
        throw new UnsupportedOperationException(
                "ReadOnceTikaInputStream does not support rewind(). " +
                        "Use TikaInputStream.get() for rewind capability.");
    }

    public boolean hasLength() {
        return length != -1;
    }

    public long getLength() {
        return length;
    }

    public long getPosition() {
        return position;
    }

    public void setCloseShield() {
        this.closeShieldDepth++;
    }

    public void removeCloseShield() {
        this.closeShieldDepth--;
    }

    public boolean isCloseShield() {
        return closeShieldDepth > 0;
    }

    /**
     * Returns the buffer size used for mark/reset operations.
     */
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public String toString() {
        return "ReadOnceTikaInputStream[position=" + position + ", bufferSize=" + bufferSize + "]";
    }
}
