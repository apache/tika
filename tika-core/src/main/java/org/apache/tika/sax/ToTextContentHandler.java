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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX event handler that writes all character content out to a character
 * stream. No escaping or other transformations are made on the character
 * content.
 *
 * @since Apache Tika 0.10
 */
public class ToTextContentHandler extends DefaultHandler {

    /**
     * The character stream.
     */
    private final Writer writer;

    /**
     * Creates a content handler that writes character events to
     * the given writer.
     *
     * @param writer writer
     */
    public ToTextContentHandler(Writer writer) {
        this.writer = writer;
    }

    /**
     * Creates a content handler that writes character events to
     * the given output stream using the platform default encoding.
     *
     * @param stream output stream
     */
    public ToTextContentHandler(OutputStream stream) {
        this(new OutputStreamWriter(stream));
    }

    /**
     * Creates a content handler that writes character events to
     * the given output stream using the given encoding.
     *
     * @param stream output stream
     * @param encoding output encoding
     * @throws UnsupportedEncodingException if the encoding is unsupported
     */
    public ToTextContentHandler(OutputStream stream, String encoding)
            throws UnsupportedEncodingException {
        this(new OutputStreamWriter(stream, encoding));
    }

    /**
     * Creates a content handler that writes character events
     * to an internal string buffer. Use the {@link #toString()}
     * method to access the collected character content.
     */
    public ToTextContentHandler() {
        this(new StringWriter());
    }

    /**
     * Writes the given characters to the given character stream.
     */
    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        try {
            writer.write(ch, start, length);
        } catch (IOException e) {
            throw new SAXException(
                    "Error writing: " + new String(ch, start, length), e);
        }
    }


    /**
     * Writes the given ignorable characters to the given character stream.
     * The default implementation simply forwards the call to the
     * {@link #characters(char[], int, int)} method.
     */
    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        characters(ch, start, length);
    }

    /**
     * Flushes the character stream so that no characters are forgotten
     * in internal buffers.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-179">TIKA-179</a>
     * @throws SAXException if the stream can not be flushed
     */
    @Override
    public void endDocument() throws SAXException {
        try {
            writer.flush();
        } catch (IOException e) {
            throw new SAXException("Error flushing character output", e);
        }
    }

    /**
     * Returns the contents of the internal string buffer where
     * all the received characters have been collected. Only works
     * when this object was constructed using the empty default
     * constructor or by passing a {@link StringWriter} to the
     * other constructor.
     */
    @Override
    public String toString() {
        return writer.toString();
    }

}
