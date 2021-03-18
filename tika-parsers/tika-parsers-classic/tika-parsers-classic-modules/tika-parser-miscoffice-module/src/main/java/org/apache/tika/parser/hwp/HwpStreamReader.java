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
package org.apache.tika.parser.hwp;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LittleEndian;

public class HwpStreamReader {
    private byte[] skipBuffer = new byte[4096];
    private InputStream input;
    private byte[] buf;

    public HwpStreamReader(InputStream inputStream) {
        this.input = inputStream;
        buf = new byte[4];
    }


    /**
     * unsigned 1 byte
     *
     * @return
     * @throws IOException
     */
    public short uint8() throws IOException {
        int read = IOUtils.readFully(input, buf, 0, 1);

        if (read == -1) {
            return -1;
        }

        return LittleEndian.getUByte(buf);
    }

    /**
     * unsigned 2 byte
     *
     * @return
     * @throws IOException
     */
    public int uint16() throws IOException {
        int read = IOUtils.readFully(input, buf, 0, 2);

        if (read == -1) {
            return -1;
        }

        if (read < 2) {
            throw new EOFException();
        }
        return LittleEndian.getUShort(buf);
    }

    /**
     * unsigned 2 byte array
     *
     * @param i
     * @return
     * @throws IOException
     */
    public int[] uint16(int i) throws IOException {
        if (i <= 0) {
            throw new IllegalArgumentException();
        }
        byte[] buf = new byte[i * 2];
        int read = IOUtils.readFully(input, buf, 0, i * 2);

        if (read != i * 2) {
            throw new EOFException();
        }
        int[] uints = new int[i];
        for (int ii = 0; ii < i; ii++) {
            uints[ii] = LittleEndian.getUShort(buf, ii * 2);
        }

        return uints;
    }

    /**
     * unsigned 4 byte
     *
     * @return
     * @throws IOException
     */
    public long uint32() throws IOException {
        int read = IOUtils.readFully(input, buf, 0, 4);

        if (read == -1) {
            return -1;
        }

        if (read < 4) {
            throw new EOFException();
        }

        return LittleEndian.getUInt(buf);
    }

    /**
     * ensure skip of n byte
     *
     * @param n
     * @throws IOException
     */
    public void ensureSkip(long n) throws IOException {
        //Leaving this for anyone who can figure out why this doesn't
        //work.  See HwpV5ParserTest#testMultiThreadedSkipFully
        //long skipped = org.apache.tika.io.IOUtils.skip(input, n);
        long skipped = org.apache.tika.io.IOUtils.skip(input, n, skipBuffer);
        if (skipped != n) {
            throw new EOFException();
        }
    }
}
