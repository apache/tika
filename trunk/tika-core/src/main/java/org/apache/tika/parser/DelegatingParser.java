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
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Base class for parser implementations that want to delegate parts of the
 * task of parsing an input document to another parser. The delegate parser
 * is looked up from the parsing context using the {@link Parser} class as
 * the key.
 *
 * @since Apache Tika 0.4, major changes in Tika 0.5
 */
public class DelegatingParser implements Parser {

    /**
     * Returns the parser instance to which parsing tasks should be delegated.
     * The default implementation looks up the delegate parser from the given
     * parse context, and uses an {@link EmptyParser} instance as a fallback.
     * Subclasses can override this method to implement alternative delegation
     * strategies.
     *
     * @since Apache Tika 0.7
     * @param context parse context
     * @return delegate parser
     */
    protected Parser getDelegateParser(ParseContext context) {
        return context.get(Parser.class, EmptyParser.INSTANCE);
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return getDelegateParser(context).getSupportedTypes(context);
    }

    /**
     * Looks up the delegate parser from the parsing context and
     * delegates the parse operation to it. If a delegate parser is not
     * found, then an empty XHTML document is returned.
     * <p>
     * Subclasses should override this method to parse the top level
     * structure of the given document stream. Parsed sub-streams can
     * be passed to this base class method to be parsed by the configured
     * delegate parser.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws SAXException, IOException, TikaException {
        getDelegateParser(context).parse(stream, handler, metadata, context);
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

}
