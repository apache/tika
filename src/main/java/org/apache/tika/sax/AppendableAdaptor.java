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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Adaptor which turns a {@link ContentHandler} into an {@link Appendable}.
 *
 * @see ContentHandler
 * @version $Revision$
 */
public class AppendableAdaptor implements Appendable {

    /**
     * Decorated SAX event handler.
     */
    private final ContentHandler handler;

    /**
     * Creates a adaptor for the given SAX event handler.
     *
     * @param handler SAX event handler to be decorated, throws
     * {@link IllegalArgumentException} if null.
     */
    public AppendableAdaptor(ContentHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Content Handler is missing");
        }
        this.handler = handler;
    }

    /**
     * Return the content handler.
     *
     * @return The content handler
     */
    public ContentHandler getHandler() {
        return handler;
    }

    /**
     * Write a single character to the underling content handler.
     *
     * @param c The character to write
     * @return This appendabale instance
     * @throws IOException if an error occurs writing to the content handler
     */
    public Appendable append(char c) throws IOException {
        return append(Character.toString(c));
    }

    /**
     * Write a character sequence to the underling content handler.
     *
     * @param charSeq The sequence of characters, ignored if null
     * @return This appendabale instance
     * @throws IOException if an error occurs writing to the content handler
     */
    public Appendable append(CharSequence charSeq) throws IOException {
        if (charSeq != null) {
            append(charSeq, 0, charSeq.length());
        }
        return this;
    }

    /**
     * Write the specified characters to the underling content handler.
     *
     * @param charSeq The sequence of characters, ignored if null
     * @param start The starting index of the characters to write
     * @param end The index of the last character +1 to write
     * @return This appendabale instance
     * @throws IOException if a {@link SAXException} occurs writing to the
     *  content handler
     */
    public Appendable append(CharSequence charSeq, int start, int end)
        throws IOException {
        if (charSeq != null) {
            try {
                char[] chars = charSeq.toString().toCharArray();
                handler.characters(chars, start, (end - start));
            } catch (SAXException e) {
                throw new IOException(e.toString());
            }
        }
        return this;
    }
}
