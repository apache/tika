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
package org.apache.tika.detect;

/**
 * Utility class for computing a histogram of the bytes seen in a stream.
 *
 * @since Apache Tika 1.2
 */
public class TextStatistics {

    private static final int ASCII_CONTROL_END = 0x20;
    private static final int ASCII_PRINTABLE_START = 0x20;
    private static final int ASCII_PRINTABLE_END = 0x80;
    private static final int UTF8_CONTINUATION_START = 0x80;
    private static final int UTF8_CONTINUATION_END = 0xc0;
    private static final int UTF8_2BYTE_START = 0xc0;
    private static final int UTF8_2BYTE_END = 0xe0;
    private static final int UTF8_3BYTE_END = 0xf0;
    private static final int UTF8_4BYTE_END = 0xf8;
    private static final int INVALID_UTF8_START = 0xf8;
    private static final int INVALID_UTF8_END = 0x100;

    private final int[] byteFrequencies = new int[256];
    private int totalBytes = 0;

    public void addData(byte[] buffer, int offset, int length) {
        for (int i = 0; i < length; i++) {
            byteFrequencies[buffer[offset + i] & 0xff]++;
            totalBytes++;
        }
    }

    /**
     * Checks whether at least one byte was seen and that the bytes that
     * were seen were mostly plain text (i.e. < 2% control, > 90% ASCII range).
     *
     * @return <code>true</code> if the seen bytes were mostly safe ASCII,
     * <code>false</code> otherwise
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-483">TIKA-483</a>
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-688">TIKA-688</a>
     */
    public boolean isMostlyAscii() {
        int controlCount = countRange(0, ASCII_CONTROL_END);
        int asciiPrintableCount = countRange(ASCII_PRINTABLE_START, ASCII_PRINTABLE_END);
        int safeControlCount = countSafeControl();
        return totalBytes > 0 &&
                (controlCount - safeControlCount) * 100 < totalBytes * 2 &&
                (asciiPrintableCount + safeControlCount) * 100 > totalBytes * 90;
    }

    /**
     * Checks whether the observed byte stream looks like UTF-8 encoded text.
     *
     * @return <code>true</code> if the seen bytes look like UTF-8,
     * <code>false</code> otherwise
     * @since Apache Tika 1.3
     */
    public boolean looksLikeUTF8() {
        int controlCount = countRange(0, ASCII_CONTROL_END);
        int asciiUtf8Count = countRange(ASCII_PRINTABLE_START, ASCII_PRINTABLE_END);
        int safeControlCount = countSafeControl();

        int expectedContinuation = 0;
        int[] leadingBytes = new int[]{
                countRange(UTF8_2BYTE_START, UTF8_2BYTE_END),
                countRange(UTF8_2BYTE_END, UTF8_3BYTE_END),
                countRange(UTF8_3BYTE_END, UTF8_4BYTE_END)
        };

        for (int i = 0; i < leadingBytes.length; i++) {
            asciiUtf8Count += leadingBytes[i];
            expectedContinuation += (i + 1) * leadingBytes[i];
        }

        int continuationCount = countRange(UTF8_CONTINUATION_START, UTF8_CONTINUATION_END);

        return asciiUtf8Count > 0 &&
                continuationCount <= expectedContinuation &&
                continuationCount >= expectedContinuation - 3 &&
                countRange(INVALID_UTF8_START, INVALID_UTF8_END) == 0 &&
                (controlCount - safeControlCount) * 100 < asciiUtf8Count * 2;
    }

    /**
     * Returns the total number of bytes seen so far.
     *
     * @return count of all bytes
     */
    public int count() {
        return totalBytes;
    }

    /**
     * Returns the number of occurrences of the given byte.
     *
     * @param b byte
     * @return count of the given byte
     */
    public int count(int b) {
        return byteFrequencies[b & 0xff];
    }

    /**
     * Counts control characters (i.e. < 0x20, excluding tab, CR, LF,
     * page feed and escape).
     * <p>
     * This definition of control characters is based on section 4 of the
     * "Content-Type Processing Model" Internet-draft
     * (<a href="http://webblaze.cs.berkeley.edu/2009/mime-sniff/mime-sniff.txt"
     * >draft-abarth-mime-sniff-01</a>).
     * <pre>
     * +-------------------------+
     * | Binary data byte ranges |
     * +-------------------------+
     * | 0x00 -- 0x08            |
     * | 0x0B                    |
     * | 0x0E -- 0x1A            |
     * | 0x1C -- 0x1F            |
     * +-------------------------+
     * </pre>
     *
     * @return count of control characters
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-154">TIKA-154</a>
     */
    public int countControl() {
        return countRange(0, ASCII_CONTROL_END) - countSafeControl();
    }

    /**
     * Counts "safe" (i.e. seven-bit non-control) ASCII characters.
     *
     * @return count of safe ASCII characters
     * @see #countControl()
     */
    public int countSafeAscii() {
        return countRange(ASCII_PRINTABLE_START, ASCII_PRINTABLE_END) + countSafeControl();
    }

    /**
     * Counts eight bit characters, i.e. bytes with their highest bit set.
     *
     * @return count of eight bit characters
     */
    public int countEightBit() {
        return countRange(128, 256);
    }

    private int countRange(int from, int to) {
        assert 0 <= from && to <= byteFrequencies.length;
        int sum = 0;
        for (int i = from; i < to; i++) {
            sum += byteFrequencies[i];
        }
        return sum;
    }

    private int countSafeControl() {
        return count('\t') + count('\n') + count('\r') // tab, LF, CR
                + count(0x0c) + count(0x1b);           // new page, escape
    }
}