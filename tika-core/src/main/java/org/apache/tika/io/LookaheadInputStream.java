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
 * Stream wrapper that make it easy to read up to n bytes ahead from
 * a stream that supports the mark feature. This class insulates the
 * underlying stream from things like possible mark(), reset() and close()
 * calls by external components that might otherwise invalidate the marked
 * state of a stream.
 * <p>
 * The recommended usage pattern of this class is:
 * <pre>
 *     InputStream lookahead = new LookaheadInputStream(stream, n);
 *     try {
 *         processStream(lookahead);
 *     } finally {
 *         lookahead.close();
 *     }
 * </pre>
 * <p>
 * This usage pattern guarantees that only up to n bytes from the original
 * stream can ever be read, and that the stream will have been marked and
 * then reset to its original state once the above code block exits. No
 * code in the fictional processStream() method can affect the the state of
 * the original stream.
 *
 * @since Apache Tika 0.10
 */
public class LookaheadInputStream extends InputStream {

    private InputStream stream;

    private final byte[] buffer;

    private int buffered = 0;

    private int position = 0;

    private int mark = 0;

    /**
     * Creates a lookahead wrapper for the given input stream.
     * The given input stream should support the mark feature,
     * as otherwise the state of that stream will be undefined
     * after the lookahead wrapper has been closed. As a special
     * case a <code>null</code> stream is treated as an empty stream.
     *
     * @param stream input stream, can be <code>null</code>
     * @param n maximum number of bytes to look ahead
     */
    public LookaheadInputStream(InputStream stream, int n) {
        this.stream = stream;
        this.buffer = new byte[n];
        if (stream != null) {
            stream.mark(n);
        }
    }

    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.reset();
            stream = null;
        }
    }

    private void fill() throws IOException {
        if (available() == 0 && buffered < buffer.length && stream != null) {
            int n = stream.read(buffer, buffered, buffer.length - buffered);
            if (n != -1) {
                buffered += n;
            } else {
                close();
            }
        }
    }

    @Override
    public int read() throws IOException {
        fill();
        if (buffered > position) {
            return 0xff & buffer[position++];
        } else {
            return -1;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        fill();
        if (buffered > position) {
            len = Math.min(len, buffered - position);
            System.arraycopy(buffer, position, b, off, len);
            position += len;
            return len;
        } else {
            return -1;
        }
    }

    @Override
    public long skip(long n) throws IOException {
        fill();
        n = Math.min(n, available());
        position += n;
        return n;
    }

    @Override
    public int available() {
        return buffered - position;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void mark(int readlimit) {
        mark = position;
    }

    @Override
    public synchronized void reset() {
        position = mark;
    }

}
