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

import org.apache.commons.io.input.CountingInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Content handler decorator that attempts to prevent denial of service
 * attacks against Tika parsers.
 * <p>
 * Currently this class simply compares the number of output characters
 * to to the number of input bytes, and throws an exception if the output
 * is truly excessive when compared to the input. This is a strong indication
 * of a zip bomb.
 *
 * @since Apache Tika 0.4
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-216">TIKA-216</a>
 */
public class SecureContentHandler extends ContentHandlerDecorator {

    /**
     * The input stream that Tika is parsing.
     */
    private final CountingInputStream stream;

    /**
     * Number of output characters that Tika has produced so far.
     */
    private long count;

    /**
     * Decorates the given content handler with zip bomb prevention based
     * on the count of bytes read from the given counting input stream.
     * The resulting decorator can be passed to a Tika parser along with
     * the given counting input stream.
     *
     * @param handler the content handler to be decorated
     * @param stream the input stream to be parsed, wrapped into
     *        a {@link CountingInputStream} decorator
     */
    public SecureContentHandler(
            ContentHandler handler, CountingInputStream stream) {
        super(handler);
        this.stream = stream;
        this.count = 0;
    }

    /**
     * Records the given number of output characters (or more accurately
     * UTF-16 code units). Throws an exception if the recorded number of
     * characters highly exceeds the number of input bytes read.
     *
     * @param length number of new output characters produced
     * @throws SAXException if a zip bomb is detected
     */
    private void advance(int length) throws SAXException {
        count += length;
        if (count > 1000000 && count > 100 * stream.getByteCount()) {
            throw new SAXException("Zip Bomb detected!");
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        advance(length);
        super.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        advance(length);
        super.ignorableWhitespace(ch, start, length);
    }

}
