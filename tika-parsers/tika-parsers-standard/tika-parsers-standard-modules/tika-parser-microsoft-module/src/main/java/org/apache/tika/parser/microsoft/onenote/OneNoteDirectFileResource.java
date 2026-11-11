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
package org.apache.tika.parser.microsoft.onenote;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * Seekable reads over a OneNote file. The parser mostly reads a few bytes at a time, so reads
 * come from a window of the file rather than one system call each.
 */
class OneNoteDirectFileResource implements Closeable {

    private static final int WINDOW_SIZE = 64 * 1024;
    // RandomAccessFile mallocs a native buffer the size of each read
    private static final int MAX_DIRECT_READ = 1024 * 1024;

    private final RandomAccessFile raf;
    private final long size;
    private final byte[] window = new byte[WINDOW_SIZE];
    // file offset of window[0], and how many bytes of the window are valid
    private long windowStart;
    private int windowLength;
    private long position;

    public OneNoteDirectFileResource(File f) throws IOException {
        this.raf = new RandomAccessFile(f, "r");
        this.size = raf.length();
    }

    public int read() throws IOException {
        if (position >= size) {
            return -1;
        }
        if (!inWindow(position)) {
            fill(position);
        }
        return window[(int) (position++ - windowStart)] & 0xFF;
    }

    public int read(ByteBuffer byteBuffer) throws IOException {
        int len = byteBuffer.remaining();
        int totalRead = 0;
        while (totalRead < len && position < size) {
            if (!inWindow(position)) {
                if (len - totalRead >= WINDOW_SIZE && byteBuffer.hasArray()) {
                    // large reads bypass the window; not through a FileChannel, which would
                    // cache a direct buffer of the read's size on this thread
                    raf.seek(position);
                    int n = raf.read(byteBuffer.array(),
                            byteBuffer.arrayOffset() + byteBuffer.position(),
                            Math.min(len - totalRead, MAX_DIRECT_READ));
                    if (n <= 0) {
                        throw new IOException("File ended before its recorded size");
                    }
                    byteBuffer.position(byteBuffer.position() + n);
                    position += n;
                    totalRead += n;
                    continue;
                }
                fill(position);
            }
            int offset = (int) (position - windowStart);
            int n = Math.min(len - totalRead, windowLength - offset);
            byteBuffer.put(window, offset, n);
            position += n;
            totalRead += n;
        }
        if (totalRead < len && position == size) {
            throw new IOException("End of stream reached earlier than expected");
        }
        return totalRead == 0 && len > 0 ? -1 : totalRead;
    }

    /** Reads {@code n} (at most 8) bytes as a little-endian value. */
    public long readLittleEndian(int n) throws IOException {
        if (position + n > size) {
            throw new IOException("End of stream reached earlier than expected");
        }
        if (position < windowStart || position + n > windowStart + windowLength) {
            fill(position);
        }
        long res = 0;
        if (windowLength >= n) {
            int offset = (int) (position - windowStart);
            for (int i = n - 1; i >= 0; i--) {
                res = (res << 8) | (window[offset + i] & 0xFF);
            }
            position += n;
        } else {
            // short read from the file
            for (int i = 0; i < n; i++) {
                res |= ((long) read()) << (8 * i);
            }
        }
        return res;
    }

    private boolean inWindow(long pos) {
        return pos >= windowStart && pos < windowStart + windowLength;
    }

    private void fill(long pos) throws IOException {
        raf.seek(pos);
        int n = raf.read(window, 0, (int) Math.min(WINDOW_SIZE, size - pos));
        if (n <= 0) {
            // the file shrank after it was opened
            throw new IOException("File ended before its recorded size");
        }
        windowStart = pos;
        windowLength = n;
    }

    public long size() throws IOException {
        return size;
    }

    public long position() throws IOException {
        return position;
    }

    public void position(long nuPos) throws IOException {
        if (nuPos > size) {
            throw new IOException("requesting seek past end of stream");
        }
        position = nuPos;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

}
