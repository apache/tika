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

package org.apache.tika.parser;


import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class DigestingParser extends ParserDecorator {

    /**
     * Interface for digester. See
     * org.apache.parser.utils.CommonsDigester in tika-parsers for an
     * implementation.
     */
    public interface Digester {
        /**
         * Digests an InputStream and sets the appropriate value(s) in the metadata.
         * The Digester is also responsible for marking and resetting the stream.
         * <p>
         * The given stream is guaranteed to support the
         * {@link InputStream#markSupported() mark feature} and the detector
         * is expected to {@link InputStream#mark(int) mark} the stream before
         * reading any bytes from it, and to {@link InputStream#reset() reset}
         * the stream before returning. The stream must not be closed by the
         * detector.
         *
         * @param is InputStream to digest
         * @param m Metadata to set the values for
         * @param parseContext ParseContext
         * @throws IOException
         */
        void digest(InputStream is, Metadata m, ParseContext parseContext) throws IOException;
    };

    /**
     * Encodes byte array from a MessageDigest to String
     */
    public interface Encoder {
        String encode(byte[] bytes);
    }

    private final Digester digester;
    /**
     * Creates a decorator for the given parser.
     *
     * @param parser the parser instance to be decorated
     */
    public DigestingParser(Parser parser, Digester digester) {
        super(parser);
        this.digester = digester;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        try {
            if (digester != null) {
                digester.digest(tis, metadata, context);
            }
            super.parse(tis, handler, metadata, context);
        } finally {
            tmp.dispose();
        }
    }
}
