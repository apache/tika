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
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Composite parser that delegates parsing tasks to a component parser
 * based on the declared content type of the incoming document. A fallback
 * parser is defined for cases where a parser for the given content type is
 * not available.
 */
public class CompositeParser implements Parser {

    /**
     * Set of component parsers, keyed by the supported media types.
     */
    private Map<String, Parser> parsers = new HashMap<String, Parser>();

    /**
     * The fallback parser, used when no better parser is available.
     */
    private Parser fallback = new EmptyParser();

    /**
     * Returns the component parsers.
     *
     * @return component parsers, keyed by media type
     */
    public Map<String, Parser> getParsers() {
        return parsers;
    }

    /**
     * Sets the component parsers.
     *
     * @param parsers component parsers, keyed by media type
     */
    public void setParsers(Map<String, Parser> parsers) {
        this.parsers = parsers;
    }

    /**
     * Returns the fallback parser.
     *
     * @return fallback parser
     */
    public Parser getFallback() {
        return fallback;
    }

    /**
     * Sets the fallback parser.
     *
     * @param fallback fallback parser
     */
    public void setFallback(Parser fallback) {
        this.fallback = fallback;
    }

    /**
     * Returns the parser that best matches the given metadata. By default
     * looks for a parser that matches the content type metadata property,
     * and uses the fallback parser if a better match is not found.
     * <p>
     * Subclasses can override this method to provide more accurate
     * parser resolution.
     *
     * @param metadata document metadata
     * @return matching parser
     */
    protected Parser getParser(Metadata metadata) {
        Parser parser = parsers.get(metadata.get(Metadata.CONTENT_TYPE));
        if (parser == null) {
            parser = fallback;
        }
        return parser;
    }

    /**
     * Delegates the call to the matching component parser.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        getParser(metadata).parse(stream, handler, metadata);
    }

}
