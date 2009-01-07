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
package org.apache.tika.sax;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class SafeContentHandler extends ContentHandlerDecorator {

    private static final char[] REPLACEMENT = new char[] { ' ' };

    /**
     * Internal interface that allows both character and
     * ignorable whitespace content to be filtered the same way.
     */
    protected interface Output {
        void write(char[] ch, int start, int length) throws SAXException;
    }

    /**
     * Output through the {@link ContentHandler#characters(char[], int, int)}
     * method of the decorated content handler.
     */
    private final Output charactersOutput = new Output() {
        public void write(char[] ch, int start, int length)
                throws SAXException {
            SafeContentHandler.super.characters(ch, start, length);
        }
    };

    /**
     * Output through the
     * {@link ContentHandler#ignorableWhitespace(char[], int, int)}
     * method of the decorated content handler.
     */
    private final Output ignorableWhitespaceOutput = new Output() {
        public void write(char[] ch, int start, int length)
                throws SAXException {
            SafeContentHandler.super.characters(ch, start, length);
        }
    };

    public SafeContentHandler(ContentHandler handler) {
        super(handler);
    }

    private void filter(char[] ch, int start, int length, Output output)
            throws SAXException {
        int end = start + length;

        for (int i = start; i < end; i++) {
            if (isInvalid(ch[i])) {
                // Output any preceding valid characters
                if (i > start) {
                    output.write(ch, start, i - start);
                }

                // Output the replacement for this invalid character
                writeReplacement(output);

                // Continue with the rest of the array
                start = i + 1;
            }
        }

        // Output any remaining valid characters
        output.write(ch, start, end - start);
    }

    /**
     * Checks whether the given character (more accurately a UTF-16 code unit)
     * is an invalid XML character and should be replaced for output.
     * Subclasses can override this method to use an alternative definition
     * of which characters should be replaced in the XML output.
     *
     * @param ch character
     * @return <code>true</code> if the character should be replaced,
     *         <code>false</code> otherwise
     */
    protected boolean isInvalid(char ch) {
        // TODO: Detect also FFFE, FFFF, and the surrogate blocks
        return ch < 0x20 && ch != 0x09 && ch != 0x0A && ch != 0x0D;
    }

    /**
     * Outputs the replacement for an invalid character. Subclasses can
     * override this method to use a custom replacement.
     *
     * @param output where the replacement is written to
     * @throws SAXException if the replacement could not be written
     */
    protected void writeReplacement(Output output) throws SAXException {
        output.write(REPLACEMENT, 0, REPLACEMENT.length);
    }

    //------------------------------------------------------< ContentHandler >

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        filter(ch, start, length, charactersOutput);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        filter(ch, start, length, ignorableWhitespaceOutput);
    }

}
