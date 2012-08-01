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

    private final int[] counts = new int[256];

    private int total = 0;

    public void addData(byte[] buffer, int offset, int length) {
        for (int i = 0; i < length; i++) {
            counts[buffer[offset + i] & 0xff]++;
            total++;
        }
    }

    /**
     * Checks whether at least one byte was seen and that the bytes that
     * were seen were mostly plain text (i.e. < 2% control, > 90% ASCII range).
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-483">TIKA-483</a>
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-688">TIKA-688</a>
     * @return <code>true</code> if the seen bytes were mostly safe ASCII,
     *         <code>false</code> otherwise
     */
    public boolean isMostlyAscii() {
        int control = count(0, 0x20);
        int ascii = count(0x20, 128);
        int safe = countSafeControl();
        return total > 0
                && (control - safe) * 100 < total * 2
                && (ascii + safe) * 100 > total * 90;
    }

    /**
     * Checks whether the observed byte stream looks like UTF-8 encoded text.
     *
     * @since Apache Tika 1.3
     * @return <code>true</code> if the seen bytes look like UTF-8,
     *         <code>false</code> otherwise
     */
    public boolean looksLikeUTF8() {
        int control = count(0, 0x20);
        int utf8 = count(0x20, 0x80);
        int safe = countSafeControl();

        int expectedContinuation = 0;
        int[] leading = new int[] {
                count(0xc0, 0xe0), count(0xe0, 0xf0), count(0xf0, 0xf8) };
        for (int i = 0; i < leading.length; i++) {
            utf8 += leading[i];
            expectedContinuation += (i + 1) * leading[i];
        }

        int continuation = count(0x80, 0xc0);
        return utf8 > 0
                && continuation <= expectedContinuation
                && continuation >= expectedContinuation - 3
                && count(0xf80, 0x100) == 0
                && (control - safe) * 100 < utf8 * 2;
    }

    /**
     * Returns the total number of bytes seen so far.
     *
     * @return count of all bytes
     */
    public int count() {
        return total;
    }

    /**
     * Returns the number of occurrences of the given byte.
     *
     * @param b byte
     * @return count of the given byte
     */
    public int count(int b) {
        return counts[b & 0xff];
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
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-154">TIKA-154</a>
     * @return count of control characters
     */
    public int countControl() {
        return count(0, 0x20) - countSafeControl();
    }

    /**
     * Counts "safe" (i.e. seven-bit non-control) ASCII characters.
     *
     * @see #countControl()
     * @return count of safe ASCII characters
     */
    public int countSafeAscii() {
        return count(0x20, 128) + countSafeControl();
    }

    /**
     * Counts eight bit characters, i.e. bytes with their highest bit set.
     *
     * @return count of eight bit characters
     */
    public int countEightBit() {
        return count(128, 256);
    }

    private int count(int from, int to) {
        assert 0 <= from && to <= counts.length;
        int count = 0;
        for (int i = from; i < to; i++) {
            count += counts[i];
        }
        return count;
    }

    private int countSafeControl() {
        return count('\t') + count('\n') + count('\r') // tab, LF, CR
                + count(0x0c) + count(0x1b);           // new page, escape
    }

}
