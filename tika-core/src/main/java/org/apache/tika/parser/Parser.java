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
package org.apache.tika.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Tika parser interface.
 */
public interface Parser {

    /**
     * Returns the set of media types supported by this parser when used
     * with the given parse context.
     *
     * @since Apache Tika 0.7
     * @param context parse context
     * @return immutable set of media types
     */
    Set<MediaType> getSupportedTypes(ParseContext context);

    /**
     * Parses a document stream into a sequence of XHTML SAX events.
     * Fills in related document metadata in the given metadata object.
     * <p>
     * The given document stream is consumed but not closed by this method.
     * The responsibility to close the stream remains on the caller.
     * <p>
     * Information about the parsing context can be passed in the context
     * parameter. See the parser implementations for the kinds of context
     * information they expect.
     *
     * @since Apache Tika 0.5
     * @param stream the document stream (input)
     * @param handler handler for the XHTML SAX events (output)
     * @param metadata document metadata (input and output)
     * @param context parse context
     * @throws IOException if the document stream could not be read
     * @throws SAXException if the SAX events could not be processed
     * @throws TikaException if the document could not be parsed
     */
    void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException;

    /**
     * The parse() method from Tika 0.4 and earlier. Please use the
     * {@link #parse(InputStream, ContentHandler, Metadata, ParseContext)}
     * method instead in new code. Calls to this backwards compatibility
     * method are forwarded to the new parse() method with an empty parse
     * context.
     *
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    void parse(InputStream stream, ContentHandler handler, Metadata metadata)
        throws IOException, SAXException, TikaException;

}
