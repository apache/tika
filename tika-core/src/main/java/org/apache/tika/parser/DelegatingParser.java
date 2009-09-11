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
import java.util.Collections;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Base class for parser implementations that want to delegate parts of the
 * task of parsing an input document to another parser. The default base
 * class implementation simply delegates the entire parsing task to a dummy
 * {@link EmptyParser} instance, but subclasses can implement more complex
 * processing rules and a more complete delegate parser can be specified
 * through the {@link #setDelegate(Parser)} method.
 * <p>
 * The Tika configuration mechanism also contains a way to automatically
 * set the delegate parser of all configured delegating parsers
 * implementations. This feature is most notably used by the
 * {@link AutoDetectParser} class to make it the recursive target of all
 * delegated parsing tasks.
 *
 * @since Apache Tika 0.4
 */
public class DelegatingParser implements Parser {

    /**
     * The parser to which parts of the parsing tasks are delegated.
     */
    private transient Parser delegate = new EmptyParser();

    /**
     * Returns delegate parser instance.
     *
     * @return delegate parser
     */
    public Parser getDelegate() {
        return delegate;
    }

    /**
     * Sets the delegate parser instance.
     *
     * @param delegate delegate parser
     */
    public void setDelegate(Parser delegate) {
        if (delegate == null) {
            throw new NullPointerException(
                    "Delegate parser of " + this + " can not be null");
        } else {
            this.delegate = delegate;
        }
    }

    /**
     * Parses the given document using the specified delegate parser.
     * Subclasses should override this method with more complex delegation
     * rules based on the structure of the input document. The default
     * implementation simply delegates the entire parsing task to the
     * specified delegate parser.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, Map<String, Object> context)
            throws SAXException, IOException, TikaException {
        delegate.parse(stream, handler, metadata, context);
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        Map<String, Object> context = Collections.emptyMap();
        parse(stream, handler, metadata, context);
    }

}
