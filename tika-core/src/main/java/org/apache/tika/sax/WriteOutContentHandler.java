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
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;

/**
 * SAX event handler that writes content up to an optional write
 * limit out to a character stream or other decorated handler.
 */
public class WriteOutContentHandler extends ContentHandlerDecorator {


    /**
     * The maximum number of characters to write to the character stream.
     * Set to -1 for no limit.
     */
    private final int writeLimit;

    /**
     * Number of characters written so far.
     */
    private int writeCount = 0;

    private boolean throwOnWriteLimitReached = true;

    private ParseContext parseContext = null;

    private boolean writeLimitReached;

    /**
     * Creates a content handler that writes content up to the given
     * write limit to the given content handler.
     *
     * @param handler    content handler to be decorated
     * @param writeLimit write limit
     * @since Apache Tika 0.10
     */
    public WriteOutContentHandler(ContentHandler handler, int writeLimit) {
        super(handler);
        this.writeLimit = writeLimit;
    }

    /**
     * Creates a content handler that writes content up to the given
     * write limit to the given character stream.
     *
     * @param writer     character stream
     * @param writeLimit write limit
     * @since Apache Tika 0.10
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
     * @deprecated -- please use {@link WriteOutContentHandler#WriteOutContentHandler(Writer)}
     */
    @Deprecated
    public WriteOutContentHandler(OutputStream stream) {
        this(new OutputStreamWriter(stream, Charset.defaultCharset()));
    }

    /**
     * Creates a content handler that writes character events
     * to an internal string buffer. Use the {@link #toString()}
     * method to access the collected character content.
     * <p>
     * The internal string buffer is bounded at the given number of characters.
     * If this write limit is reached, then a {@link SAXException} is thrown.
     * The {@link WriteLimitReachedException#isWriteLimitReached(Throwable)} method can be used to
     * detect this case.
     *
     * @param writeLimit maximum number of characters to include in the string,
     *                   or -1 to disable the write limit
     * @since Apache Tika 0.7
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
     * {@link WriteLimitReachedException#isWriteLimitReached(Throwable)} method can be used to
     * detect this case.
     */
    public WriteOutContentHandler() {
        this(100 * 1000);
    }

    /**
     * The default is to throw a {@link WriteLimitReachedException}
     * @param handler
     * @param writeLimit
     * @param throwOnWriteLimitReached
     * @param parseContext
     */
    public WriteOutContentHandler(ContentHandler handler,
                                  int writeLimit, boolean throwOnWriteLimitReached,
                                  ParseContext parseContext) {
        super(handler);
        this.writeLimit = writeLimit;
        this.throwOnWriteLimitReached = throwOnWriteLimitReached;
        this.parseContext = parseContext;
    }

    /**
     * Writes the given characters to the given character stream.
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (writeLimitReached) {
            return;
        }
        if (writeLimit == -1 || writeCount + length <= writeLimit) {
            super.characters(ch, start, length);
            writeCount += length;
        } else {
            super.characters(ch, start, writeLimit - writeCount);
            handleWriteLimitReached();
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (writeLimitReached) {
            return;
        }
        if (writeLimit == -1 || writeCount + length <= writeLimit) {
            super.ignorableWhitespace(ch, start, length);
            writeCount += length;
        } else {
            super.ignorableWhitespace(ch, start, writeLimit - writeCount);
            handleWriteLimitReached();
        }
    }

    private void handleWriteLimitReached() throws WriteLimitReachedException {
        writeLimitReached = true;
        writeCount = writeLimit;
        if (throwOnWriteLimitReached) {
            throw new WriteLimitReachedException(writeLimit);
        } else {
            ParseRecord parseRecord = parseContext.get(ParseRecord.class);
            if (parseRecord != null) {
                parseRecord.setWriteLimitReached(true);
            }
        }
    }

}
