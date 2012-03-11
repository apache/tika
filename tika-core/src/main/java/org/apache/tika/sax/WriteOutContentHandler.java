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

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.util.UUID;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * SAX event handler that writes content up to an optional write
 * limit out to a character stream or other decorated handler.
 */
public class WriteOutContentHandler extends ContentHandlerDecorator {

    /**
     * The unique tag associated with exceptions from stream.
     */
    private final Serializable tag = UUID.randomUUID();

    /**
     * The maximum number of characters to write to the character stream.
     * Set to -1 for no limit.
     */
    private final int writeLimit;

    /**
     * Number of characters written so far.
     */
    private int writeCount = 0;

    /**
     * Creates a content handler that writes content up to the given
     * write limit to the given content handler.
     *
     * @since Apache Tika 0.10
     * @param handler content handler to be decorated
     * @param writeLimit write limit
     */
    public WriteOutContentHandler(ContentHandler handler, int writeLimit) {
        super(handler);
        this.writeLimit = writeLimit;
    }

    /**
     * Creates a content handler that writes content up to the given
     * write limit to the given character stream.
     *
     * @since Apache Tika 0.10
     * @param writer character stream
     * @param writeLimit write limit
     */
    public WriteOutContentHandler(Writer writer, int writeLimit) {
        this(new ToTextContentHandler(writer), writeLimit);
    }

    /**
     * Creates a content handler that writes character events to
     * the given writer.
     *
     * @param writer writer
     */
    public WriteOutContentHandler(Writer writer) {
        this(writer, -1);
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
     * <p>
     * The internal string buffer is bounded at the given number of characters.
     * If this write limit is reached, then a {@link SAXException} is thrown.
     * The {@link #isWriteLimitReached(Throwable)} method can be used to
     * detect this case.
     *
     * @since Apache Tika 0.7
     * @param writeLimit maximum number of characters to include in the string,
     *                   or -1 to disable the write limit
     */
    public WriteOutContentHandler(int writeLimit) {
        this(new StringWriter(), writeLimit);
    }

    /**
     * Creates a content handler that writes character events
     * to an internal string buffer. Use the {@link #toString()}
     * method to access the collected character content.
     * <p>
     * The internal string buffer is bounded at 100k characters. If this
     * write limit is reached, then a {@link SAXException} is thrown. The
     * {@link #isWriteLimitReached(Throwable)} method can be used to detect
     * this case.
     */
    public WriteOutContentHandler() {
        this(100 * 1000);
    }

    /**
     * Writes the given characters to the given character stream.
     */
    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (writeLimit == -1 || writeCount + length <= writeLimit) {
            super.characters(ch, start, length);
            writeCount += length;
        } else {
            super.characters(ch, start, writeLimit - writeCount);
            writeCount = writeLimit;
            throw new WriteLimitReachedException(
                    "Your document contained more than " + writeLimit
                    + " characters, and so your requested limit has been"
                    + " reached. To receive the full text of the document,"
                    + " increase your limit. (Text up to the limit is"
                    + " however available).", tag);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        if (writeLimit == -1 || writeCount + length <= writeLimit) {
            super.ignorableWhitespace(ch, start, length);
            writeCount += length;
        } else {
            super.ignorableWhitespace(ch, start, writeLimit - writeCount);
            writeCount = writeLimit;
            throw new WriteLimitReachedException(
                    "Your document contained more than " + writeLimit
                    + " characters, and so your requested limit has been"
                    + " reached. To receive the full text of the document,"
                    + " increase your limit. (Text up to the limit is"
                    + " however available).", tag);
        }
    }

    /**
     * Checks whether the given exception (or any of it's root causes) was
     * thrown by this handler as a signal of reaching the write limit.
     *
     * @since Apache Tika 0.7
     * @param t throwable
     * @return <code>true</code> if the write limit was reached,
     *         <code>false</code> otherwise
     */
    public boolean isWriteLimitReached(Throwable t) {
        if (t instanceof WriteLimitReachedException) {
            return tag.equals(((WriteLimitReachedException) t).tag);
        } else {
            return t.getCause() != null && isWriteLimitReached(t.getCause());
        }
    }

    /**
     * The exception used as a signal when the write limit has been reached.
     */
    private static class WriteLimitReachedException extends SAXException {

        /** Serial version UID */
        private static final long serialVersionUID = -1850581945459429943L;

        /** Serializable tag of the handler that caused this exception */
        private final Serializable tag;

        public WriteLimitReachedException(String message, Serializable tag) {
           super(message);
           this.tag = tag;
        }

    }

}
