/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.pdf.updates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.io.RandomAccessRead;

/**
 * This is a first draft of a scanner to extract incremental updates
 * out of PDFs.  It effectively scans the bytestream looking
 * for startxref\\s*(\\d+)\\s*(%%EOF\n?)?  It does not validate that the
 * startxrefs point to actual xrefs.
 *
 * The good parts come directly from PDFBox.
 */
public class StartXRefScanner {

    static final int MAX_LENGTH_LONG = Long.toString(Long.MAX_VALUE).length();

    private static final char[] STARTXREF = new char[]{'s', 't', 'a', 'r', 't', 'x', 'r', 'e', 'f'};

    private static final char[] EOF_MARKER = new char[]{'%', '%', 'E', 'O', 'F'};


    /**
     * ASCII code for line feed.
     */
    private static final byte ASCII_LF = 10;
    /**
     * ASCII code for carriage return.
     */
    private static final byte ASCII_CR = 13;
    private static final byte ASCII_ZERO = 48;
    private static final byte ASCII_NINE = 57;
    private static final byte ASCII_SPACE = 32;
    private final RandomAccessRead source;

    public StartXRefScanner(RandomAccessRead source) {
        this.source = source;
    }

    public List<StartXRefOffset> scan() throws IOException {
        List<StartXRefOffset> offsets = new ArrayList<>();
        long read = 0;
        int match = 0;
        try {
            int b = source.read();
            while (b > -1) {
                if (b == STARTXREF[match]) {
                    match++;
                    if (match >= STARTXREF.length) {
                        long startXREFOffset = source.getPosition() - STARTXREF.length;
                        long startxref = readLong();
                        boolean hasEof = readEOF();
                        long endOfEOFOffset = source.getPosition();
                        offsets.add(new StartXRefOffset(startxref,
                                startXREFOffset, endOfEOFOffset, hasEof));
                        match = 0;
                    }
                } else {
                    match = 0;
                }
                read++;
                b = source.read();
            }
        } finally {
            if (source.getPosition() >= Integer.MAX_VALUE) {
                throw new IOException("read more than " + Integer.MAX_VALUE + " bytes");
            }
            //TODO: if we're opening a new file for the source
            //we shouldn't bother with this.
            source.rewind((int)source.getPosition());
        }
        return offsets;
    }

    private boolean readEOF() throws IOException {
        //this expects %%EOF, with possibly some white space before it
        //it will fail if there's a comment before %%EOF
        //TODO -- make this more robust
        skipWhiteSpaces();
        int c = source.read();
        int i = 0;
        while (c > -1 && c == EOF_MARKER[i] && ++i < EOF_MARKER.length) {
            c = source.read();
        }

        if (i == EOF_MARKER.length) {
            //now look for a single new line following the eof
            c = source.read();
            if (c == -1) {
                //do nothing
            } else if (isEOL(c)) {
                //do nothing
            } else {
                source.rewind(1);
            }
            return true;
        }
        if (c == -1) {
            source.rewind(i - 1);
        } else {
            source.rewind(i);
        }
        return false;
    }

    protected void skipWhiteSpaces() throws IOException {

        int whitespace = source.read();
        while (whitespace > -1 && isWhitespace(whitespace)) {

            whitespace = source.read();
        }
        if (whitespace > -1) {
            source.rewind(1);
        }
    }

    protected boolean isWhitespace(int c) {
        return c == 0 || c == 9 || c == 12 || c == ASCII_LF || c == ASCII_CR || c == ASCII_SPACE;
    }

    protected long readLong() throws IOException {
        skipSpaces();
        long retval = 0;

        StringBuilder longBuffer = readStringNumber();

        try {
            retval = Long.parseLong(longBuffer.toString());
        } catch (NumberFormatException e) {
            source.rewind(longBuffer.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            throw new IOException("Error: Expected a long type at offset " + source.getPosition() +
                    ", instead got '" + longBuffer + "'", e);
        }
        return retval;
    }

    /**
     * This will skip all spaces and comments that are present.
     *
     * @throws IOException If there is an error reading from the stream.
     */
    protected void skipSpaces() throws IOException {
        int c = source.read();
        // 37 is the % character, a comment
        while (isWhitespace(c) || c == 37) {
            if (c == 37) {
                // skip past the comment section
                c = source.read();
                while (!isEOL(c) && c != -1) {
                    c = source.read();
                }
            } else {
                c = source.read();
            }
        }
        if (c != -1) {
            source.rewind(1);
        }
    }

    /**
     * This method is used to read a token by the {@linkplain #readLong()} method. Valid
     * delimiters are any non digit values.
     *
     * @return the token to parse as integer or long by the calling method.
     * @throws IOException throws by the {@link #source} methods.
     */
    protected final StringBuilder readStringNumber() throws IOException {
        int lastByte;
        StringBuilder buffer = new StringBuilder();
        while ((lastByte = source.read()) >= '0' && lastByte <= '9') {
            buffer.append((char) lastByte);
            if (buffer.length() > MAX_LENGTH_LONG) {
                throw new IOException(
                        "Number '" + buffer + "' is getting too long, stop reading at offset " +
                                source.getPosition());
            }
        }
        if (lastByte != -1) {
            source.rewind(1);
        }
        return buffer;
    }

    /**
     * This will tell if the next byte to be read is an end of line byte.
     *
     * @param c The character to check against end of line
     * @return true if the next byte is 0x0A or 0x0D.
     */
    protected boolean isEOL(int c) {
        return isLF(c) || isCR(c);
    }

    private boolean isLF(int c) {
        return ASCII_LF == c;
    }

    private boolean isCR(int c) {
        return ASCII_CR == c;
    }
}

