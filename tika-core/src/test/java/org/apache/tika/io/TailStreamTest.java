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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.Test;

/**
 * Test class for {@code TailStream}.
 */
public class TailStreamTest
{
    /** Constant for generating test text. */
    private static final String TEXT =
            "Lorem ipsum dolor sit amet, consetetur "
                    + "sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut "
                    + "labore et dolore magna aliquyam erat, sed diam voluptua. At vero"
                    + " eos et accusam et justo duo dolores et ea rebum. Stet clita "
                    + "kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor "
                    + "sit amet.";

    /**
     * Generates a test text using the specified parameters.
     * 
     * @param from the start index of the text
     * @param length the length of the text
     * @return the generated test text
     */
    private static String generateText(int from, int length)
    {
        int count = from + length;
        StringBuilder buf = new StringBuilder(count);
        while (buf.length() < count)
        {
            buf.append(TEXT);
        }
        return buf.substring(from, from + length);
    }

    /**
     * Generates a stream which contains a test text.
     * 
     * @param from the start index of the text
     * @param length the length of the generated stream
     * @return the stream with the test text
     */
    private static InputStream generateStream(int from, int length)
    {
        return new ByteArrayInputStream(generateText(from, length).getBytes(IOUtils.UTF_8));
    }

    /**
     * Helper method for reading the content of an input stream.
     * 
     * @param in the stream to be read
     * @return an array with the content of the stream
     * @throws IOException if an error occurs
     */
    private static byte[] readStream(InputStream in) throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1)
        {
            bos.write(c);
        }
        return bos.toByteArray();
    }

    /**
     * Tests whether the tail buffer can be obtained before data was read.
     */
    @Test
    public void testTailBeforeRead() throws IOException
    {
        TailStream stream = new TailStream(generateStream(0, 100), 50);
        assertEquals("Wrong buffer length", 0, stream.getTail().length);
        stream.close();
    }

    /**
     * Tests the content of the tail buffer if it is only partly filled.
     */
    @Test
    public void testTailBufferPartlyRead() throws IOException
    {
        final int count = 64;
        TailStream stream = new TailStream(generateStream(0, count), 2 * count);
        byte[] data = readStream(stream);
        assertTrue("Wrong content", Arrays.equals(data, stream.getTail()));
        stream.close();
    }

    /**
     * Tests the content of the tail buffer if only single bytes were read.
     */
    @Test
    public void testTailSingleByteReads() throws IOException
    {
        final int count = 128;
        TailStream stream = new TailStream(generateStream(0, 2 * count), count);
        readStream(stream);
        assertEquals("Wrong buffer", generateText(count, count), new String(
                stream.getTail(), IOUtils.UTF_8));
    }

    /**
     * Tests the content of the tail buffer if larger chunks are read.
     */
    @Test
    public void testTailChunkReads() throws IOException
    {
        final int count = 16384;
        final int tailSize = 61;
        final int bufSize = 100;
        TailStream stream = new TailStream(generateStream(0, count), tailSize);
        byte[] buf = new byte[bufSize];
        int read = stream.read(buf, 10, 8);
        assertEquals("Wrong number of bytes read", 8, read);
        while (read != -1)
        {
            read = stream.read(buf);
        }
        assertEquals("Wrong buffer", generateText(count - tailSize, tailSize),
                new String(stream.getTail(), IOUtils.UTF_8));
        stream.close();
    }

    /**
     * Tests whether mark() and reset() work as expected.
     */
    @Test
    public void testReadWithMarkAndReset() throws IOException
    {
        final int tailSize = 64;
        TailStream stream =
                new TailStream(generateStream(0, 2 * tailSize), tailSize);
        byte[] buf = new byte[tailSize / 2];
        stream.read(buf);
        stream.mark(tailSize);
        stream.read(buf);
        stream.reset();
        readStream(stream);
        assertEquals("Wrong buffer", generateText(tailSize, tailSize),
                new String(stream.getTail(), IOUtils.UTF_8));
    }

    /**
     * Tests whether a reset() operation without a mark is simply ignored.
     */
    @Test
    public void testResetWithoutMark() throws IOException
    {
        final int tailSize = 75;
        final int count = 128;
        TailStream stream = new TailStream(generateStream(0, count), tailSize);
        stream.reset();
        byte[] buf = new byte[count];
        stream.read(buf);
        assertEquals("Wrong buffer", generateText(count - tailSize, tailSize),
                new String(stream.getTail(), IOUtils.UTF_8));
        stream.close();
    }

    /**
     * Tests whether skip() also fills the tail buffer.
     */
    @Test
    public void testSkip() throws IOException
    {
        final int tailSize = 128;
        final int count = 1024;
        final int skipCount = 512;
        TailStream stream = new TailStream(generateStream(0, count), tailSize);
        assertEquals("Wrong skip result", skipCount, stream.skip(skipCount));
        assertEquals("Wrong buffer",
                generateText(skipCount - tailSize, tailSize),
                new String(stream.getTail(), IOUtils.UTF_8));
        stream.close();
    }

    /**
     * Tests a skip operation at the end of the stream.
     */
    @Test
    public void testSkipEOS() throws IOException
    {
        final int count = 128;
        TailStream stream = new TailStream(generateStream(0, count), 2 * count);
        assertEquals("Wrong skip result", count, stream.skip(2 * count));
        assertEquals("Wrong buffer", generateText(0, count),
                new String(stream.getTail(), IOUtils.UTF_8));
        stream.close();
    }

    /**
     * Tests skip() if read reaches the end of the stream and returns -1.
     */
    @Test
    public void testSkipReadEnd() throws IOException
    {
        final int count = 128;
        TailStream stream = new TailStream(generateStream(0, count), 2 * count);
        readStream(stream);
        assertEquals("Wrong result", -1, stream.skip(1));
    }
}
