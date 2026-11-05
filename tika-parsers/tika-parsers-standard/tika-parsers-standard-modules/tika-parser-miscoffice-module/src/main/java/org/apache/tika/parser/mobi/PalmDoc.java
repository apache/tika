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
package org.apache.tika.parser.mobi;

import java.util.Arrays;

/**
 * The PalmDOC flavour of LZ77 that Mobipocket text records use (compression type 2),
 * and the trailing entries a MOBI text record carries after its text.
 */
final class PalmDoc {

    private PalmDoc() {
    }

    /**
     * Decompresses one text record. The output is bounded by {@code maxLength}; a record
     * that claims more is cut there, since a text record never holds more than the
     * header's record size.
     */
    static byte[] decompress(PalmDatabase.Slice record, int maxLength) {
        byte[] data = record.data();
        // a pair code expands two bytes to ten at most, so this is all a record can become
        byte[] window = new byte[Math.min(maxLength, record.length() * 5)];
        maxLength = window.length;
        int written = 0;
        int i = record.offset();
        int end = record.offset() + record.length();
        while (i < end && written < maxLength) {
            int c = data[i++] & 0xff;
            if (c == 0) {
                window[written++] = 0;
            } else if (c <= 8) {
                int n = Math.min(c, Math.min(end - i, maxLength - written));
                System.arraycopy(data, i, window, written, n);
                written += n;
                i += c;
            } else if (c <= 0x7f) {
                window[written++] = (byte) c;
            } else if (c >= 0xc0) {
                window[written++] = ' ';
                if (written < maxLength) {
                    window[written++] = (byte) (c ^ 0x80);
                }
            } else {
                if (i >= end) {
                    break;
                }
                int pair = (c << 8) | (data[i++] & 0xff);
                int distance = (pair >> 3) & 0x7ff;
                int runLength = (pair & 7) + 3;
                if (distance == 0 || distance > written) {
                    break;
                }
                for (int n = 0; n < runLength && written < maxLength; n++) {
                    window[written] = window[written - distance];
                    written++;
                }
            }
        }
        return Arrays.copyOf(window, written);
    }

    /**
     * The number of bytes at the end of a text record that are not text: one entry per
     * set bit above the lowest in the header's trailing-entry flags, each ending in its
     * own backward-encoded size, then, for the lowest bit, the multibyte overflow whose
     * count sits in the low two bits of its last byte.
     */
    static int trailingEntriesLength(PalmDatabase.Slice record, int flags) {
        byte[] data = record.data();
        int offset = record.offset();
        int length = record.length();
        int trailing = 0;
        for (int bits = flags >> 1; bits != 0; bits >>= 1) {
            if ((bits & 1) != 0) {
                trailing = Math.min(length, trailing + trailingEntrySize(data, offset, length - trailing));
            }
        }
        if ((flags & 1) != 0 && length - trailing > 0) {
            trailing += (data[offset + length - trailing - 1] & 3) + 1;
        }
        return Math.min(trailing, length);
    }

    private static int trailingEntrySize(byte[] data, int offset, int length) {
        int size = 0;
        int shift = 0;
        while (length > 0) {
            int b = data[offset + length - 1] & 0xff;
            size |= (b & 0x7f) << shift;
            shift += 7;
            length--;
            if ((b & 0x80) != 0 || shift >= 28) {
                break;
            }
        }
        return size;
    }
}
