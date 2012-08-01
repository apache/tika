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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Content type detection of plain text documents. This detector looks at the
 * beginning of the document input stream and considers the document to be
 * a text document if no ASCII (ISO-Latin-1, UTF-8, etc.) control bytes are
 * found. As a special case some control bytes (up to 2% of all characters)
 * are also allowed in a text document if it also contains no or just a few
 * (less than 10%) characters above the 7-bit ASCII range.
 * <p>
 * Note that text documents with a character encoding like UTF-16 are better
 * detected with {@link MagicDetector} and an appropriate magic byte pattern.
 *
 * @since Apache Tika 0.3
 */
public class TextDetector implements Detector {

    /** Serial version UID */
    private static final long serialVersionUID = 4774601079503507765L;

    /**
     * The number of bytes from the beginning of the document stream
     * to test for control bytes.
     */
    private static final int DEFAULT_NUMBER_OF_BYTES_TO_TEST = 512;

    /**
     * Lookup table for all the ASCII/ISO-Latin/UTF-8/etc. control bytes
     * in the range below 0x20 (the space character). If an entry in this
     * table is <code>true</code> then that byte is very unlikely to occur
     * in a plain text document.
     * <p>
     * The contents of this lookup table are based on the following definition
     * from section 4 of the "Content-Type Processing Model" Internet-draft
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
     */
    private static final boolean[] IS_CONTROL_BYTE = new boolean[0x20];

    static {
        Arrays.fill(IS_CONTROL_BYTE, true);
        IS_CONTROL_BYTE[0x09] = false; // tabulator
        IS_CONTROL_BYTE[0x0A] = false; // new line
        IS_CONTROL_BYTE[0x0C] = false; // new page
        IS_CONTROL_BYTE[0x0D] = false; // carriage return
        IS_CONTROL_BYTE[0x1B] = false; // escape
    }

    private final int bytesToTest;
    
    /**
     * Constructs a {@link TextDetector} which will look at the default number
     * of bytes from the beginning of the document.
     */
    public TextDetector() {
        this(DEFAULT_NUMBER_OF_BYTES_TO_TEST);
    }

    /**
     * Constructs a {@link TextDetector} which will look at a given number of
     * bytes from the beginning of the document.
     */
    public TextDetector(int bytesToTest) {
        this.bytesToTest = bytesToTest;
    }
    
    /**
     * Looks at the beginning of the document input stream to determine
     * whether the document is text or not.
     *
     * @param input document input stream, or <code>null</code>
     * @param metadata ignored
     * @return "text/plain" if the input stream suggest a text document,
     *         "application/octet-stream" otherwise
     */
    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        input.mark(bytesToTest);
        try {
            TextStatistics stats = new TextStatistics();

            byte[] buffer = new byte[1024];
            int n = 0;
            int m = input.read(buffer, 0, Math.min(bytesToTest, buffer.length));
            while (m != -1 && n < bytesToTest) {
                stats.addData(buffer, 0, m);
                n += m;
                m = input.read(buffer, 0, Math.min(bytesToTest - n, buffer.length));
            }

            if (stats.isMostlyAscii() || stats.looksLikeUTF8()) {
                return MediaType.TEXT_PLAIN;
            } else {
                return MediaType.OCTET_STREAM;
            }
        } finally {
            input.reset();
        }
    }

}
