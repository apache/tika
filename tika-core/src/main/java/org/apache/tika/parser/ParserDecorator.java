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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Decorator base class for the {@link Parser} interface. This class
 * simply delegates all parsing calls to an underlying decorated parser
 * instance. Subclasses can provide extra decoration by overriding the
 * parse method.
 */
public class ParserDecorator extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = -3861669115439125268L;

    /**
     * Decorates the given parser so that it always claims to support
     * parsing of the given media types.
     *
     * @param parser the parser to be decorated
     * @param types supported media types
     * @return the decorated parser
     */
    public static final Parser withTypes(
            Parser parser, final Set<MediaType> types) {
        return new ParserDecorator(parser) {
            private static final long serialVersionUID = -7345051519565330731L;
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return types;
            }
        };
    }

    /**
     * Decorates the given parser so that it never claims to support
     * parsing of the given media types, but will work for all others.
     *
     * @param parser the parser to be decorated
     * @param types excluded/ignored media types
     * @return the decorated parser
     */
    public static final Parser withoutTypes(
            Parser parser, final Set<MediaType> excludeTypes) {
        return new ParserDecorator(parser) {
            private static final long serialVersionUID = 7979614774021768609L;
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                // Get our own, writable copy of the types the parser supports
                Set<MediaType> parserTypes = 
                        new HashSet<MediaType>(super.getSupportedTypes(context));
                // Remove anything on our excludes list
                parserTypes.removeAll(excludeTypes);
                // Return whatever is left
                return parserTypes;
            }
        };
    }
    
    /**
     * Decorates the given parsers into a virtual parser, where they'll
     *  be tried in preference order until one works without error.
     * TODO Is this the right name?
     * TODO Is this the right place to put this? Should it be in CompositeParser? Elsewhere?
     * TODO Should we reset the Metadata if we try another parser?
     * TODO Should we reset the ContentHandler if we try another parser?
     * TODO Should we log/report failures anywhere?
     * @deprecated Do not use until the TODOs are resolved, see TIKA-1509
     */
    public static final Parser withFallbacks(
            final Collection<? extends Parser> parsers, final Set<MediaType> types) {
        Parser parser = EmptyParser.INSTANCE;
        if (!parsers.isEmpty()) parser = parsers.iterator().next();
        
        return new ParserDecorator(parser) {
            private static final long serialVersionUID = 1625187131782069683L;
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return types;
            }
            @Override
            public void parse(InputStream stream, ContentHandler handler,
                    Metadata metadata, ParseContext context)
                    throws IOException, SAXException, TikaException {
                // Must have a TikaInputStream, so we can re-use it if parsing fails
                TikaInputStream tstream = TikaInputStream.get(stream);
                tstream.getFile();
                // Try each parser in turn
                for (Parser p : parsers) {
                    tstream.mark(-1);
                    try {
                        p.parse(tstream, handler, metadata, context);
                        return;
                    } catch (Exception e) {
                        // TODO How to log / record this failure?
                    }
                    // Prepare for the next parser, if present
                    tstream.reset();
                }
            }
        };
    }

    /**
     * The decorated parser instance.
     */
    private final Parser parser;

    /**
     * Creates a decorator for the given parser.
     *
     * @param parser the parser instance to be decorated
     */
    public ParserDecorator(Parser parser) {
        this.parser = parser;
    }

    /**
     * Delegates the method call to the decorated parser. Subclasses should
     * override this method (and use <code>super.getSupportedTypes()</code>
     * to invoke the decorated parser) to implement extra decoration.
     */
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return parser.getSupportedTypes(context);
    }

    /**
     * Delegates the method call to the decorated parser. Subclasses should
     * override this method (and use <code>super.parse()</code> to invoke
     * the decorated parser) to implement extra decoration.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        parser.parse(stream, handler, metadata, context);
    }


    /**
     * Gets the parser wrapped by this ParserDecorator
     * @return
     */
    public Parser getWrappedParser() {
        return this.parser;
    }

}
