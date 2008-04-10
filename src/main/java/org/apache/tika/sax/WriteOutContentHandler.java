/**
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
import java.io.Writer;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX event handler that writes all character content out to
 * a {@link Writer} character stream.
 */
public class WriteOutContentHandler extends DefaultHandler {

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
    public WriteOutContentHandler(Writer writer) {
        this.writer = writer;
    }

    /**
     * Creates a content handler that writes character events to
     * the given output stream using the default encoding.
     *
     * @param stream output stream
     */
    public WriteOutContentHandler(OutputStream stream) {
        this(new OutputStreamWriter(stream));
    }

    /**
     * Creates a content handler that writes character events
     * to an internal string buffer. Use the {@link #toString()}
     * method to access the collected character content.
     */
    public WriteOutContentHandler() {
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
            throw new SAXException("Error writing out character content", e);
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
