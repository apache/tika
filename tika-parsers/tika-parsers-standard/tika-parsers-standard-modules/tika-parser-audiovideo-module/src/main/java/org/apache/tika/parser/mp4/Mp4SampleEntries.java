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
 * Walks the sample entries of a SampleDescriptionBox ('stsd') payload, shared
 * by the sound and video handlers. The payload is 4 bytes version and flags,
 * a 4 byte entry count, then one sample entry per count. Each entry is a box:
 * a 32-bit size and a FourCC, where a size of 1 announces a 64-bit largesize
 * and a size of 0 extends the entry to the end of the payload (ISO/IEC
 * 14496-12, 4.2).
 */
final class Mp4SampleEntries {

    /**
     * Size of the SampleEntry fields that follow the box header in every
     * entry: 6 reserved bytes and the 2 byte data reference index.
     */
    static final int SAMPLE_ENTRY_FIELDS = 8;

    interface Visitor {
        /**
         * @param fourCC the entry's FourCC, or null if it is not printable
         * @param b      the stsd payload
         * @param start  offset of the first byte after the entry's box header
         * @param end    offset one past the entry's last byte
         */
        void entry(String fourCC, byte[] b, int start, int end);
    }

    private Mp4SampleEntries() {
    }

    static void walk(byte[] b, Visitor visitor) {
        if (b.length < 8) {
            return;
        }
        long entryCount = EndianUtils.getUIntBE(b, 4);
        int pos = 8;
        for (long i = 0; i < entryCount && pos + 8 <= b.length; i++) {
            long size = EndianUtils.getUIntBE(b, pos);
            int header = 8;
            if (size == 1) {
                //largesize: the 64-bit size follows the FourCC
                if (pos + 16 > b.length) {
                    return;
                }
                //a value beyond 63 bits goes negative and fails the size check below
                size = (EndianUtils.getUIntBE(b, pos + 8) << 32) | EndianUtils.getUIntBE(b, pos + 12);
                header = 16;
            } else if (size == 0) {
                //the entry extends to the end of the box
                size = b.length - pos;
            }
            if (size < header + SAMPLE_ENTRY_FIELDS || size > b.length - pos) {
                return;
            }
            int end = pos + (int) size;
            visitor.entry(printableFourCC(b, pos + 4), b, pos + header, end);
            pos = end;
        }
    }

    /**
     * Reads a FourCC as it is, for comparing against known box types.
     */
    static String fourCC(byte[] b, int pos) {
        return new String(b, pos, 4, StandardCharsets.ISO_8859_1);
    }

    /**
     * Reads a FourCC for exposing it as a metadata value: null unless all four
     * bytes are printable ASCII, with trailing spaces trimmed (QuickTime pads
     * short codes such as 'raw ' and 'rle ' with spaces). Codes that are blank
     * after trimming are null as well.
     */
    static String printableFourCC(byte[] b, int pos) {
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
