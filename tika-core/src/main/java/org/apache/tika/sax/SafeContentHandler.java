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

/*
import java.util.ArrayList;
import java.util.List;
*/

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Content handler decorator that makes sure that the character events
 * ({@link #characters(char[], int, int)} or
 * {@link #ignorableWhitespace(char[], int, int)}) passed to the decorated
 * content handler contain only valid XML characters. All invalid characters
 * are replaced with spaces.
 * <p>
 * The XML standard defines the following Unicode character ranges as
 * valid XML characters:
 * <pre>
 * #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
 * </pre>
 * <p>
 * Note that currently this class only detects those invalid characters whose
 * UTF-16 representation fits a single char. Also, this class does not ensure
 * that the UTF-16 encoding of incoming characters is correct.
 */
public class SafeContentHandler extends ContentHandlerDecorator {

    /**
     * Replacement for invalid characters.
     */
    private static final char[] REPLACEMENT = new char[] { '\ufffd' };

    /**
     * Internal interface that allows both character and
     * ignorable whitespace content to be filtered the same way.
     */
    protected interface Output {
        void write(char[] ch, int start, int length) throws SAXException;
    }

    private static class StringOutput implements Output {

        private final StringBuilder builder = new StringBuilder();

        public void write(char[] ch, int start, int length) {
            builder.append(ch, start, length);
        }

        public String toString() {
            return builder.toString();
        }

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
            SafeContentHandler.super.ignorableWhitespace(ch, start, length);
        }
    };

    public SafeContentHandler(ContentHandler handler) {
        super(handler);
    }

    /**
     * Filters and outputs the contents of the given input buffer. Any
     * invalid characters in the input buffer area handled by sending a
     * replacement (a space character) to the given output. Any sequences
     * of valid characters are passed as-is to the given output. 
     * 
     * @param ch input buffer
     * @param start start offset within the buffer
     * @param length number of characters to read from the buffer
     * @param output output channel
     * @throws SAXException if the filtered characters could not be written out
     */
    private void filter(char[] ch, int start, int length, Output output)
            throws SAXException {
        int end = start + length;

        int i = start;
        while (i < end) {
            int c = Character.codePointAt(ch, i, end);
            int j = i + Character.charCount(c);

            if (isInvalid(c)) {
                // Output any preceding valid characters
                if (i > start) {
                    output.write(ch, start, i - start);
                }

                // Output the replacement for this invalid character
                writeReplacement(output);

                // Continue with the rest of the array
                start = j;
            }

            i = j;
        }

        // Output any remaining valid characters
        output.write(ch, start, end - start);
    }

    /**
     * Checks if the given string contains any invalid XML characters.
     *
     * @param value string to be checked
     * @return <code>true</code> if the string contains invalid XML characters,
     *         <code>false</code> otherwise
     */
    private boolean isInvalid(String value) {
        char[] ch = value.toCharArray();

        int i = 0;
        while (i < ch.length) {
            int c = Character.codePointAt(ch, i);
            if (isInvalid(c)) {
                return true;
            }
            i = i + Character.charCount(c);
        }

        return false;
    }

    /**
     * Checks whether the given Unicode character is an invalid XML character
     * and should be replaced for output. Subclasses can override this method
     * to use an alternative definition of which characters should be replaced
     * in the XML output. The default definition from the XML specification is:
     * <pre>
     * Char ::= #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
     * </pre>
     *
     * @param ch character
     * @return <code>true</code> if the character should be replaced,
     *         <code>false</code> otherwise
     */
    protected boolean isInvalid(int ch) {
        if (ch < 0x20) {
            return ch != 0x09 && ch != 0x0A && ch != 0x0D;
        } else if (ch < 0xE000) {
            return ch > 0xD7FF;
        } else if (ch < 0x10000) {
            return ch > 0xFFFD;
        } else {
            return ch > 0x10FFFF;
        }
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


    /*
    private final List<String> elements = new ArrayList<String>();

    // Called only from assert
    private boolean verifyStartElement(String name) {
        // TODO: we could strengthen this to do full
        // XTHML validation, eg you shouldn't start p inside
        // another p (but ODF parser, at least, seems to
        // violate this):
        //if (name.equals("p")) {
        //assert elements.size() == 0 || !elements.get(elements.size()-1).equals("p");
        //}
        elements.add(name);
        return true;
    }

    // Called only from assert
    private boolean verifyEndElement(String name) {
        assert elements.size() > 0: "end tag=" + name + " with no startElement";
        final String currentElement = elements.get(elements.size()-1);
        assert currentElement.equals(name): "mismatched elements open=" + currentElement + " close=" + name;
        elements.remove(elements.size()-1);
        return true;
    }

    // Called only from assert
    private boolean verifyEndDocument() {
        assert elements.size() == 0;
        return true;
    }
    */

    //------------------------------------------------------< ContentHandler >

    @Override
    public void startElement(
            String uri, String localName, String name, Attributes atts)
            throws SAXException {
        // TODO: enable this, but some parsers currently
        // trip it
        //assert verifyStartElement(name);
        // Look for any invalid characters in attribute values.
        for (int i = 0; i < atts.getLength(); i++) {
            if (isInvalid(atts.getValue(i))) {
                // Found an invalid character, so need to filter the attributes
                AttributesImpl filtered = new AttributesImpl();
                for (int j = 0; j < atts.getLength(); j++) {
                    String value = atts.getValue(j);
                    if (j >= i && isInvalid(value)) {
                        // Filter the attribute value when needed
                        Output buffer = new StringOutput();
                        filter(value.toCharArray(), 0, value.length(), buffer);
                        value = buffer.toString();
                    }
                    filtered.addAttribute(
                            atts.getURI(j), atts.getLocalName(j),
                            atts.getQName(j), atts.getType(j), value);
                }
                atts = filtered;
                break;
            }
        }
        super.startElement(uri, localName, name, atts);
    }

    @Override
    public void endElement(String uri, String localName, String name)
            throws SAXException {
        // TODO: enable this, but some parsers currently
        // trip it
        //assert verifyEndElement(name);
        super.endElement(uri, localName, name);
    }

    @Override
    public void endDocument() throws SAXException {
        // TODO: enable this, but some parsers currently
        // trip it
        //assert verifyEndDocument();
        super.endDocument();
    }

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
