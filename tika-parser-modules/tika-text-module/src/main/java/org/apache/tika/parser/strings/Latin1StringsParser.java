/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.strings;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser to extract printable Latin1 strings from arbitrary files with pure
 * java. Useful for binary or unknown files, for files without a specific parser
 * and for corrupted ones causing a TikaException as a fallback parser.
 * 
 * Currently the parser does a best effort to extract Latin1 strings, used by
 * Western European languages, encoded with ISO-8859-1, UTF-8 or UTF-16 charsets
 * within the same file.
 * 
 * The implementation is optimized for fast parsing with only one pass.
 */
public class Latin1StringsParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    /**
     * The set of supported types
     */
    private static final Set<MediaType> SUPPORTED_TYPES = getTypes();

    /**
     * The valid ISO-8859-1 character map.
     */
    private static final boolean[] isChar = getCharMap();

    /**
     * The size of the internal buffers.
     */
    private static int BUF_SIZE = 64 * 1024;

    /**
     * The minimum size of a character sequence to be extracted.
     */
    private int minSize = 4;

    /**
     * The output buffer.
     */
    private byte[] output = new byte[BUF_SIZE];

    /**
     * The input buffer.
     */
    private byte[] input = new byte[BUF_SIZE];

    /**
     * The temporary position into the output buffer.
     */
    private int tmpPos = 0;

    /**
     * The current position into the output buffer.
     */
    private int outPos = 0;

    /**
     * The number of bytes into the input buffer.
     */
    private int inSize = 0;

    /**
     * The position into the input buffer.
     */
    private int inPos = 0;

    /**
     * The output content handler.
     */
    private XHTMLContentHandler xhtml;

    /**
     * Returns the minimum size of a character sequence to be extracted.
     * 
     * @return the minimum size of a character sequence
     */
    public int getMinSize() {
        return minSize;
    }

    /**
     * Sets the minimum size of a character sequence to be extracted.
     * 
     * @param minSize
     *            the minimum size of a character sequence
     */
    public void setMinSize(int minSize) {
        this.minSize = minSize;
    }

    /**
     * Populates the valid ISO-8859-1 character map.
     * 
     * @return the valid ISO-8859-1 character map.
     */
    private static boolean[] getCharMap() {

        boolean[] isChar = new boolean[256];
        for (int c = Byte.MIN_VALUE; c <= Byte.MAX_VALUE; c++)
            if ((c >= 0x20 && c <= 0x7E)
                    || (c >= (byte) 0xC0 && c <= (byte) 0xFE) || c == 0x0A
                    || c == 0x0D || c == 0x09) {
                isChar[c & 0xFF] = true;
            }
        return isChar;

    }

    /**
     * Returns the set of supported types.
     * 
     * @return the set of supported types
     */
    private static Set<MediaType> getTypes() {
        HashSet<MediaType> supportedTypes = new HashSet<MediaType>();
        supportedTypes.add(MediaType.OCTET_STREAM);
        return supportedTypes;
    }

    /**
     * Tests if the byte is a ISO-8859-1 char.
     * 
     * @param c
     *            the byte to test.
     * 
     * @return if the byte is a char.
     */
    private static final boolean isChar(byte c) {
        return isChar[c & 0xFF];
    }

    /**
     * Flushes the internal output buffer to the content handler.
     * 
     * @throws UnsupportedEncodingException
     * @throws SAXException
     */
    private void flushBuffer() throws UnsupportedEncodingException,
            SAXException {
        if (tmpPos - outPos >= minSize)
            outPos = tmpPos - minSize;

        xhtml.characters(new String(output, 0, outPos, "windows-1252"));

        for (int k = 0; k < tmpPos - outPos; k++)
            output[k] = output[outPos + k];
        tmpPos = tmpPos - outPos;
        outPos = 0;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    /**
     * @see org.apache.tika.parser.Parser#parse(java.io.InputStream,
     *      org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata,
     *      org.apache.tika.parser.ParseContext)
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException {
        /*
         * Creates a new instance because the object is not immutable.
         */
        new Latin1StringsParser().doParse(stream, handler, metadata, context);
    }

    /**
     * Does a best effort to extract Latin1 strings encoded with ISO-8859-1,
     * UTF-8 or UTF-16. Valid chars are saved into the output buffer and the
     * temporary buffer position is incremented. When an invalid char is read,
     * the difference of the temporary and current buffer position is checked.
     * If it is greater than the minimum string size, the current buffer
     * position is updated to the temp position. If it is not, the temp position
     * is reseted to the current position.
     * 
     * @param stream
     *            the input stream.
     * @param handler
     *            the output content handler
     * @param metadata
     *            the metadata of the file
     * @param context
     *            the parsing context
     * @throws IOException
     *             if an io error occurs
     * @throws SAXException
     *             if a sax error occurs
     */
    private void doParse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException {

        tmpPos = 0;
        outPos = 0;

        xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        int i = 0;
        do {
            inSize = 0;
            while ((i = stream.read(input, inSize, BUF_SIZE - inSize)) > 0) {
                inSize += i;
            }
            inPos = 0;
            while (inPos < inSize) {
                byte c = input[inPos++];
                boolean utf8 = false;
                /*
                 * Test for a possible UTF8 encoded char
                 */
                if (c == (byte) 0xC3) {
                    byte c_ = inPos < inSize ? input[inPos++] : (byte) stream
                            .read();
                    /*
                     * Test if the next byte is in the valid UTF8 range
                     */
                    if (c_ >= (byte) 0x80 && c_ <= (byte) 0xBF) {
                        utf8 = true;
                        output[tmpPos++] = (byte) (c_ + 0x40);
                    } else {
                        output[tmpPos++] = c;
                        c = c_;
                    }
                    if (tmpPos == BUF_SIZE)
                        flushBuffer();

                    /*
                     * Test for a possible UTF8 encoded char
                     */
                } else if (c == (byte) 0xC2) {
                    byte c_ = inPos < inSize ? input[inPos++] : (byte) stream
                            .read();
                    /*
                     * Test if the next byte is in the valid UTF8 range
                     */
                    if (c_ >= (byte) 0xA0 && c_ <= (byte) 0xBF) {
                        utf8 = true;
                        output[tmpPos++] = c_;
                    } else {
                        output[tmpPos++] = c;
                        c = c_;
                    }
                    if (tmpPos == BUF_SIZE)
                        flushBuffer();
                }
                if (!utf8)
                    /*
                     * Test if the byte is a valid char.
                     */
                    if (isChar(c)) {
                        output[tmpPos++] = c;
                        if (tmpPos == BUF_SIZE)
                            flushBuffer();
                    } else {
                        /*
                         * Test if the byte is an invalid char, marking a string
                         * end. If it is a zero, test 2 positions before or
                         * ahead for a valid char, meaning it marks the
                         * transition between ISO-8859-1 and UTF16 sequences.
                         */
                        if (c != 0
                                || (inPos >= 3 && isChar(input[inPos - 3]))
                                || (inPos + 1 < inSize && isChar(input[inPos + 1]))) {

                            if (tmpPos - outPos >= minSize) {
                                output[tmpPos++] = 0x0A;
                                outPos = tmpPos;

                                if (tmpPos == BUF_SIZE)
                                    flushBuffer();
                            } else
                                tmpPos = outPos;

                        }
                    }
            }
        } while (i != -1 && !Thread.currentThread().isInterrupted());

        if (tmpPos - outPos >= minSize) {
            output[tmpPos++] = 0x0A;
            outPos = tmpPos;
        }
        xhtml.characters(new String(output, 0, outPos, "windows-1252"));

        xhtml.endDocument();

    }

}
