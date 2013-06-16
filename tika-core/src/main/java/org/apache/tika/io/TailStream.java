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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * <p>
 * A specialized input stream implementation which records the last portion read
 * from an underlying stream.
 * </p>
 * <p>
 * This stream implementation is useful to deal with information which is known
 * to be located at the end of a stream (e.g. ID3 v1 tags). While reading bytes
 * from the underlying stream, a given number of bytes is kept in an internal
 * buffer. This buffer can then be queried after the whole stream was read. It
 * contains the last bytes read from the original input stream.
 * </p>
 * 
 * @param in the underlying input stream
 * @param tailSize the size of the tail buffer
 */
public class TailStream extends FilterInputStream
{
    /** Constant for the default skip buffer size. */
    private static final int SKIP_SIZE = 4096;
    
    /** The buffer in which the tail data is stored. */
    private final byte[] tailBuffer;

    /** The size of the internal tail buffer. */
    private final int tailSize;

    /** A copy of the internal tail buffer used for mark() operations. */
    private byte[] markBuffer;

    /** The number of bytes that have been read so far. */
    private long bytesRead;

    /** The number of bytes read at the last mark() operation. */
    private long markBytesRead;

    /** The current index into the tail buffer. */
    private int currentIndex;

    /** A copy of the current index used for mark() operations. */
    private int markIndex;

    /**
     * Creates a new instance of {@code TailStream}.
     * 
     * @param in the underlying input stream
     * @param size the size of the tail buffer
     */
    public TailStream(InputStream in, int size)
    {
        super(in);
        tailSize = size;
        tailBuffer = new byte[size];
    }

    /**
     * {@inheritDoc} This implementation adds the read byte to the internal tail
     * buffer.
     */
    @Override
    public int read() throws IOException
    {
        int c = super.read();
        if (c != -1)
        {
            appendByte((byte) c);
        }
        return c;
    }

    /**
     * {@inheritDoc} This implementation delegates to the underlying stream and
     * then adds the correct portion of the read buffer to the internal tail
     * buffer.
     */
    @Override
    public int read(byte[] buf) throws IOException
    {
        int read = super.read(buf);
        if (read > 0)
        {
            appendBuf(buf, 0, read);
        }
        return read;
    }

    /**
     * {@inheritDoc} This implementation delegates to the underlying stream and
     * then adds the correct portion of the read buffer to the internal tail
     * buffer.
     */
    @Override
    public int read(byte[] buf, int ofs, int length) throws IOException
    {
        int read = super.read(buf, ofs, length);
        if (read > 0)
        {
            appendBuf(buf, ofs, read);
        }
        return read;
    }
    
    /**
     * {@inheritDoc} This implementation delegates to the {@code read()} method
     * to ensure that the tail buffer is also filled if data is skipped.
     */
    @Override
    public long skip(long n) throws IOException
    {
        int bufSize = (int) Math.min(n, SKIP_SIZE);
        byte[] buf = new byte[bufSize];
        long bytesSkipped = 0;
        int bytesRead = 0;
        
        while(bytesSkipped < n && bytesRead != -1)
        {
            int len = (int) Math.min(bufSize, n - bytesSkipped);
            bytesRead = read(buf, 0, len);
            if(bytesRead != -1)
            {
                bytesSkipped += bytesRead;
            }
        }

        return (bytesRead < 0 && bytesSkipped == 0) ? -1 : bytesSkipped;
    }

    /**
     * {@inheritDoc} This implementation saves the internal state including the
     * content of the tail buffer so that it can be restored when ''reset()'' is
     * called later.
     */
    @Override
    public void mark(int limit)
    {
        markBuffer = new byte[tailSize];
        System.arraycopy(tailBuffer, 0, markBuffer, 0, tailSize);
        markIndex = currentIndex;
        markBytesRead = bytesRead;
    }

    /**
     * {@inheritDoc} This implementation restores this stream's state to the
     * state when ''mark()'' was called the last time. If ''mark()'' has not
     * been called before, this method has no effect.
     */
    @Override
    public void reset()
    {
        if (markBuffer != null)
        {
            System.arraycopy(markBuffer, 0, tailBuffer, 0, tailSize);
            currentIndex = markIndex;
            bytesRead = markBytesRead;
        }
    }

    /**
     * Returns an array with the last data read from the underlying stream. If
     * the underlying stream contained more data than the ''tailSize''
     * constructor argument, the returned array has a length of ''tailSize''.
     * Otherwise, its length equals the number of bytes read.
     * 
     * @return an array with the last data read from the underlying stream
     */
    public byte[] getTail()
    {
        int size = (int) Math.min(tailSize, bytesRead);
        byte[] result = new byte[size];
        System.arraycopy(tailBuffer, currentIndex, result, 0, size
                - currentIndex);
        System.arraycopy(tailBuffer, 0, result, size - currentIndex,
                currentIndex);
        return result;
    }

    /**
     * Adds the given byte to the internal tail buffer.
     * 
     * @param b the byte to be added
     */
    private void appendByte(byte b)
    {
        tailBuffer[currentIndex++] = b;
        if (currentIndex >= tailSize)
        {
            currentIndex = 0;
        }
        bytesRead++;
    }

    /**
     * Adds the content of the given buffer to the internal tail buffer.
     * 
     * @param buf the buffer
     * @param ofs the start offset in the buffer
     * @param length the number of bytes to be copied
     */
    private void appendBuf(byte[] buf, int ofs, int length)
    {
        if (length >= tailSize)
        {
            replaceTailBuffer(buf, ofs, length);
        }
        else
        {
            copyToTailBuffer(buf, ofs, length);
        }

        bytesRead += length;
    }

    /**
     * Replaces the content of the internal tail buffer by the last portion of
     * the given buffer. This method is called if a buffer was read from the
     * underlying stream whose length is larger than the tail buffer.
     * 
     * @param buf the buffer
     * @param ofs the start offset in the buffer
     * @param length the number of bytes to be copied
     */
    private void replaceTailBuffer(byte[] buf, int ofs, int length)
    {
        System.arraycopy(buf, ofs + length - tailSize, tailBuffer, 0, tailSize);
        currentIndex = 0;
    }

    /**
     * Copies the given buffer into the internal tail buffer at the current
     * position. This method is called if a buffer is read from the underlying
     * stream whose length is smaller than the tail buffer. In this case the
     * tail buffer is only partly overwritten.
     * 
     * @param buf the buffer
     * @param ofs the start offset in the buffer
     * @param length the number of bytes to be copied
     */
    private void copyToTailBuffer(byte[] buf, int ofs, int length)
    {
        int remaining = tailSize - currentIndex;
        int size1 = Math.min(remaining, length);
        System.arraycopy(buf, ofs, tailBuffer, currentIndex, size1);
        System.arraycopy(buf, ofs + size1, tailBuffer, 0, length - size1);
        currentIndex = (currentIndex + length) % tailSize;
    }
}
