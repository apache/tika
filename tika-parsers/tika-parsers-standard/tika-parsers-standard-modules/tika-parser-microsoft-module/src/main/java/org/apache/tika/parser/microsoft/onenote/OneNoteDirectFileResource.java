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
 * come from a few cached pages of the file rather than one system call each. Several pages,
 * as file node lists and the property sets they declare are read in alternation.
 */
class OneNoteDirectFileResource implements Closeable {

    private static final int PAGE_SHIFT = 16;
    private static final int PAGE_SIZE = 1 << PAGE_SHIFT;
    private static final int PAGES = 4;
    // RandomAccessFile mallocs a native buffer the size of each read
    private static final int MAX_DIRECT_READ = 1024 * 1024;

    private final RandomAccessFile raf;
    private final long size;
    private final byte[][] pages = new byte[PAGES][];
    // page number held in each slot, or -1; how many of its bytes are valid; last use
    private final long[] pageNumbers = {-1, -1, -1, -1};
    private final int[] pageLengths = new int[PAGES];
    private final long[] lastUse = new long[PAGES];
    private long useClock;
    private long position;
    private long bytesFilled;

    public OneNoteDirectFileResource(File f) throws IOException {
        this.raf = new RandomAccessFile(f, "r");
        try {
            this.size = raf.length();
        } catch (IOException e) {
            raf.close();
            throw e;
        }
    }

    public int read() throws IOException {
        if (position >= size) {
            return -1;
        }
        int slot = page(position);
        return pages[slot][offsetIn(slot, position++)] & 0xFF;
    }

    public int read(ByteBuffer byteBuffer) throws IOException {
        int len = byteBuffer.remaining();
        int totalRead = 0;
        while (totalRead < len && position < size) {
            if (len - totalRead >= PAGE_SIZE && byteBuffer.hasArray()) {
                // large reads bypass the cache; not through a FileChannel, which would
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
            int slot = page(position);
            int offset = offsetIn(slot, position);
            int n = Math.min(len - totalRead, pageLengths[slot] - offset);
            byteBuffer.put(pages[slot], offset, n);
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
        int slot = page(position);
        int offset = offsetIn(slot, position);
        long res = 0;
        if (offset + n <= pageLengths[slot]) {
            byte[] page = pages[slot];
            for (int i = n - 1; i >= 0; i--) {
                res = (res << 8) | (page[offset + i] & 0xFF);
            }
            position += n;
        } else {
            // straddles a page boundary
            for (int i = 0; i < n; i++) {
                res |= ((long) read()) << (8 * i);
            }
        }
        return res;
    }

    /** @return the slot holding the page with {@code pos}, read in if needed */
    private int page(long pos) throws IOException {
        long number = pos >>> PAGE_SHIFT;
        int victim = 0;
        for (int i = 0; i < PAGES; i++) {
            if (pageNumbers[i] == number) {
                lastUse[i] = ++useClock;
                return i;
            }
            if (lastUse[i] < lastUse[victim]) {
                victim = i;
            }
        }
        if (pages[victim] == null) {
            pages[victim] = new byte[PAGE_SIZE];
        }
        long start = number << PAGE_SHIFT;
        int want = (int) Math.min(PAGE_SIZE, size - start);
        raf.seek(start);
        int filled = 0;
        while (filled < want) {
            int n = raf.read(pages[victim], filled, want - filled);
            if (n < 0) {
                break;
            }
            filled += n;
        }
        pageNumbers[victim] = number;
        pageLengths[victim] = filled;
        lastUse[victim] = ++useClock;
        bytesFilled += filled;
        return victim;
    }

    private int offsetIn(int slot, long pos) throws IOException {
        int offset = (int) (pos & (PAGE_SIZE - 1));
        if (offset >= pageLengths[slot]) {
            // the file shrank after it was opened
            throw new IOException("File ended before its recorded size");
        }
        return offset;
    }

    /** @return bytes read from the file into the cache so far */
    long bytesFilled() {
        return bytesFilled;
    }

    public long size() throws IOException {
        return size;
    }

    public long position() throws IOException {
        return position;
    }

    public void position(long nuPos) throws IOException {
        if (nuPos < 0 || nuPos > size) {
            throw new IOException("requesting seek outside the stream: " + nuPos);
        }
        position = nuPos;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

}
