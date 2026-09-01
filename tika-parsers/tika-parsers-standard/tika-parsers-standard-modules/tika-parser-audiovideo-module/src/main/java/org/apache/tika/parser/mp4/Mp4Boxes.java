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
package org.apache.tika.parser.mp4;

import java.nio.charset.StandardCharsets;

import org.apache.tika.io.EndianUtils;

/**
 * The boxes an ISO base media file is made of, as far as reading them from a
 * byte array goes (ISO/IEC 14496-12, 4.2): a 32 bit size and a FourCC, where
 * a size of 1 announces a 64 bit largesize after the FourCC and a size of 0
 * extends the box to the end of what encloses it.
 * <p>
 * Shared by the parts of Tika that read these files without handing them to
 * a full parser: {@link Mp4SampleEntries} and the MP4 detector. Every method
 * takes the region the boxes live in and returns -1 rather than reading
 * outside it, so a crafted size ends a walk instead of a parse.
 */
public final class Mp4Boxes {

    /**
     * The header of a box with a 32 bit size: the size and the FourCC.
     */
    public static final int HEADER = 8;

    /**
     * The header of a box with a 64 bit largesize.
     */
    public static final int LARGE_HEADER = 16;

    private Mp4Boxes() {
    }

    /**
     * The offset one past the box at {@code pos}, or -1 when its size is
     * invalid or reaches past {@code end}.
     */
    public static int boxEnd(byte[] b, int pos, int end) {
        if (pos < 0 || pos > end - HEADER || end > b.length) {
            return -1;
        }
        long size = EndianUtils.getUIntBE(b, pos);
        if (size == 1) {
            if (pos > end - LARGE_HEADER) {
                return -1;
            }
            //a largesize beyond 63 bits goes negative and fails the check below
            size = (EndianUtils.getUIntBE(b, pos + HEADER) << 32)
                    + EndianUtils.getUIntBE(b, pos + HEADER + 4);
            if (size < LARGE_HEADER) {
                return -1;
            }
        } else if (size == 0) {
            //the box extends to the end of what encloses it
            size = end - pos;
        } else if (size < HEADER) {
            return -1;
        }
        if (size > end - pos) {
            return -1;
        }
        return pos + (int) size;
    }

    /**
     * The offset where the payload of the box at {@code pos} starts, which
     * follows the largesize where there is one, or -1 for an invalid box.
     */
    public static int payloadStart(byte[] b, int pos, int end) {
        if (boxEnd(b, pos, end) < 0) {
            return -1;
        }
        return EndianUtils.getUIntBE(b, pos) == 1 ? pos + LARGE_HEADER : pos + HEADER;
    }

    /**
     * The offset of the first box of the given type among the boxes in
     * {@code [pos, end)}, or -1 if there is none.
     *
     * @param maxBoxes how many boxes to look at before giving up
     */
    public static int findBox(byte[] b, int pos, int end, String type, int maxBoxes) {
        for (int box = 0; box < maxBoxes && pos >= 0 && pos <= end - HEADER; box++) {
            int boxEnd = boxEnd(b, pos, end);
            if (boxEnd < 0 || boxEnd <= pos) {
                return -1;
            }
            if (type.equals(fourCC(b, pos + 4))) {
                return pos;
            }
            pos = boxEnd;
        }
        return -1;
    }

    /**
     * Reads a FourCC as it is, for comparing against known box types.
     */
    public static String fourCC(byte[] b, int pos) {
        return new String(b, pos, 4, StandardCharsets.ISO_8859_1);
    }

    /**
     * Reads a FourCC for exposing it as a metadata value: null unless all four
     * bytes are printable ASCII, with trailing spaces trimmed (QuickTime pads
     * short codes such as 'raw ' and 'rle ' with spaces). Codes that are blank
     * after trimming are null as well.
     */
    public static String printableFourCC(byte[] b, int pos) {
        int len = 4;
        while (len > 0 && b[pos + len - 1] == ' ') {
            len--;
        }
        if (len == 0) {
            return null;
        }
        for (int i = 0; i < len; i++) {
            int c = b[pos + i] & 0xFF;
            if (c < 0x20 || c > 0x7E) {
                return null;
            }
        }
        return new String(b, pos, len, StandardCharsets.US_ASCII);
    }
}
