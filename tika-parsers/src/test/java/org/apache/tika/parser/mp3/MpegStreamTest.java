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
package org.apache.tika.parser.mp3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.tika.io.IOUtils;
import org.junit.After;
import org.junit.Test;

/**
 * Test class for {@code MpegStream}.
 */
public class MpegStreamTest
{
    /** The stream to be tested. */
    private MpegStream stream;

    @After
    public void tearDown() throws Exception
    {
        if (stream != null)
        {
            stream.close();
        }
    }

    /**
     * Tests whether the default test header can be found in a stream.
     * 
     * @param bos the stream
     * @throws IOException if an error occurs
     */
    private void checkDefaultHeader(ByteArrayOutputStream bos)
            throws IOException
    {
        ByteArrayInputStream in = new ByteArrayInputStream(bos.toByteArray());
        stream = new MpegStream(in);
        AudioFrame header = stream.nextFrame();
        assertNotNull("No header found", header);
        assertEquals("Wrong MPEG version", AudioFrame.MPEG_V2,
                header.getVersionCode());
        assertEquals("Wrong layer", AudioFrame.LAYER_3, header.getLayer());
        assertEquals("Wrong bit rate", 80000, header.getBitRate());
        assertEquals("Wrong sample rate", 24000, header.getSampleRate());
    }

    /**
     * Writes the given byte the given number of times into an output stream.
     * 
     * @param out the output stream
     * @param value the value to write
     * @param count the number of bytes to write
     * @throws IOException if an error occurs
     */
    private static void writeBytes(OutputStream out, int value, int count)
            throws IOException
    {
        for (int i = 0; i < count; i++)
        {
            out.write(value);
        }
    }

    /**
     * Writes a frame header in the given output stream.
     * 
     * @param out the output stream
     * @param b2 byte 2 of the header
     * @param b3 byte 3 of the header
     * @param b4 byte 4 of the header
     * @throws IOException if an error occurs
     */
    private static void writeFrame(OutputStream out, int b2, int b3, int b4)
            throws IOException
    {
        out.write(0xFF);
        out.write(b2);
        out.write(b3);
        out.write(b4);
    }

    /**
     * Tests whether an audio frame header can be found somewhere in a stream.
     */
    @Test
    public void testSearchNextFrame() throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writeBytes(bos, 0xFF, 32);
        writeBytes(bos, 0, 16);
        writeBytes(bos, 0xFF, 8);
        bos.write(0xF3);
        bos.write(0x96);
        bos.write(0);
        checkDefaultHeader(bos);
    }

    /**
     * Tests whether invalid frame headers are detected and skipped.
     */
    @Test
    public void testSearchNextFrameInvalid() throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writeFrame(bos, 0xEB, 0x96, 0);
        writeFrame(bos, 0xF9, 0x96, 0);
        writeFrame(bos, 0xF3, 0, 0);
        writeFrame(bos, 0xF3, 0xF0, 0);
        writeFrame(bos, 0xF3, 0x7C, 0);
        writeFrame(bos, 0xF3, 0x96, 0);
        checkDefaultHeader(bos);
    }

    /**
     * Tests a search for another frame which is interrupted because the stream
     * ends.
     */
    @Test
    public void testSeachNextFrameEOS() throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0xFF);
        bos.write(0xFF);
        bos.write(0xF3);
        bos.write(0x96);
        ByteArrayInputStream in = new ByteArrayInputStream(bos.toByteArray());
        stream = new MpegStream(in);
        assertNull("Got a frame", stream.nextFrame());
    }

    /**
     * Tries to skip a frame if no current header is available.
     */
    @Test
    public void testSkipNoCurrentHeader() throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write("This is a test".getBytes(IOUtils.UTF_8));
        ByteArrayInputStream in = new ByteArrayInputStream(bos.toByteArray());
        stream = new MpegStream(in);
        assertFalse("Wrong result", stream.skipFrame());
    }
}
