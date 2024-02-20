/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * Very slight modification of Commons' BoundedInputStream
 * so that we can figure out if this hit the bound or not.
 * <p>
 * This relies on IOUtils' skip and read to try to fully
 * read/skip inputstream.
 */
public class BoundedInputStream extends InputStream {


    private final static int EOF = -1;
    private final long max;
    private final InputStream in;
    private long pos;

    public BoundedInputStream(long max, InputStream in) {
        this.max = max;
        this.in = in;
    }

    @Override
    public int read() throws IOException {
        if (max >= 0 && pos >= max) {
            return EOF;
        }
        final int result = in.read();
        pos++;
        return result;
    }

    /**
     * Invokes the delegate's <code>read(byte[])</code> method.
     *
     * @param b the buffer to read the bytes into
     * @return the number of bytes read or -1 if the end of stream or
     * the limit has been reached.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read(final byte[] b) throws IOException {
        return this.read(b, 0, b.length);
    }

    /**
     * Invokes the delegate's <code>read(byte[], int, int)</code> method.
     * <p>
     * This does not have the same guarantees as IOUtil's readFully()...be careful.
     *
     * @param b   the buffer to read the bytes into
     * @param off The start offset
     * @param len The number of bytes to read
     * @return the number of bytes read or -1 if the end of stream or
     * the limit has been reached.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (max >= 0 && pos >= max) {
            return EOF;
        }
        final long maxRead = max >= 0 ? Math.min(len, max - pos) : len;
        final int bytesRead = in.read(b, off, (int) maxRead);

        if (bytesRead == EOF) {
            return EOF;
        }

        pos += bytesRead;
        return bytesRead;
    }

    /**
     * Invokes the delegate's <code>skip(long)</code> method.
     * As with InputStream generally, this does not guarantee reading n bytes.
     * Use IOUtils' skipFully for that functionality.
     *
     * @param n the number of bytes to skip
     * @return the actual number of bytes skipped
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long skip(final long n) throws IOException {
        final long toSkip = max >= 0 ? Math.min(n, max - pos) : n;
        final long skippedBytes = in.skip(toSkip);
        pos += skippedBytes;
        return skippedBytes;
    }

    @Override
    public void reset() throws IOException {
        in.reset();
        pos = 0;
    }

    @Override
    public void mark(int readLimit) {
        in.mark(readLimit);
    }

    public boolean hasHitBound() {
        return pos >= max;
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }

    @Override
    public boolean markSupported() {
        return in.markSupported();
    }

}

