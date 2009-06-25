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

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CountingInputStream;
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
    private long characterCount = 0;

    /**
     * Output threshold.
     */
    private long threshold = 1000000;

    /**
     * Maximum compression ratio.
     */
    private long ratio = 100;

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
    }

    /**
     * Returns the configured output threshold.
     *
     * @return output threshold
     */
    public long getOutputThreshold() {
        return threshold;
    }


    /**
     * Sets the threshold for output characters before the zip bomb prevention
     * is activated. This avoids false positives in cases where an otherwise
     * normal document for some reason starts with a highly compressible
     * sequence of bytes.
     *
     * @param threshold new output threshold
     */
    public void setOutputThreshold(long threshold) {
        this.threshold = threshold;
    }


    /**
     * Returns the maximum compression ratio.
     *
     * @return maximum compression ratio
     */
    public long getMaximumCompressionRatio() {
        return ratio;
    }


    /**
     * Sets the ratio between output characters and input bytes. If this
     * ratio is exceeded (after the output threshold has been reached) then
     * an exception gets thrown.
     *
     * @param ratio new maximum compression ratio
     */
    public void setMaximumCompressionRatio(long ratio) {
        this.ratio = ratio;
    }

    /**
     * Converts the given {@link SAXException} to a corresponding
     * {@link TikaException} if it's caused by this instance detecting
     * a zip bomb.
     *
     * @param e SAX exception
     * @throws TikaException zip bomb exception
     */
    public void throwIfCauseOf(SAXException e) throws TikaException {
        if (e instanceof SecureSAXException
                && ((SecureSAXException) e).isCausedBy(this)) {
            throw new TikaException("Zip bomb detected!", e);
        }
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
        characterCount += length;
        if (characterCount > threshold
                && characterCount > stream.getByteCount() * ratio) {
            throw new SecureSAXException();
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

    /**
     * Private exception class used to indicate a suspected zip bomb.
     *
     * @see SecureContentHandler#throwIfCauseOf(SAXException)
     */
    private class SecureSAXException extends SAXException {

        public SecureSAXException() {
            super("Suspected zip bomb: "
                    + stream.getByteCount() + " input bytes produced "
                    + characterCount + " output characters");
        }

        public boolean isCausedBy(SecureContentHandler handler) {
            return SecureContentHandler.this == handler;
        }

    }

}
