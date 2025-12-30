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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.multiple.AbstractMultipleParser.MetadataPolicy;
import org.apache.tika.parser.multiple.FallbackParser;

/**
 * Decorator base class for the {@link Parser} interface.
 * <p>This class simply delegates all parsing calls to an underlying decorated
 * parser instance. Subclasses can provide extra decoration by overriding the
 * parse method.
 * <p>To decorate several different parsers at the same time, wrap them in
 * a {@link CompositeParser} instance first.
 */
public class ParserDecorator implements Parser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3861669115439125268L;
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
     * Decorates the given parser so that it always claims to support
     * parsing of the given media types.
     *
     * @param parser the parser to be decorated
     * @param types  supported media types
     * @return the decorated parser
     */
    public static Parser withTypes(Parser parser, Set<MediaType> types) {
        return new MimeFilteringDecorator(parser, types, null);
    }

    /**
     * Decorates the given parser so that it never claims to support
     * parsing of the given media types, but will work for all others.
     *
     * @param parser       the parser to be decorated
     * @param excludeTypes excluded/ignored media types
     * @return the decorated parser
     */
    public static Parser withoutTypes(Parser parser, Set<MediaType> excludeTypes) {
        return new MimeFilteringDecorator(parser, null, excludeTypes);
    }

    /**
     * Decorates the given parser with mime type filtering.
     * Supports both include and exclude lists for round-trip serialization.
     *
     * @param parser the parser to be decorated
     * @param includeTypes types to include (if non-empty, only these types are supported)
     * @param excludeTypes types to exclude (removed from supported types)
     * @return the decorated parser, or the original parser if no filtering needed
     */
    public static Parser withMimeFilters(Parser parser, Set<MediaType> includeTypes,
                                         Set<MediaType> excludeTypes) {
        if ((includeTypes == null || includeTypes.isEmpty()) &&
                (excludeTypes == null || excludeTypes.isEmpty())) {
            return parser;
        }
        return new MimeFilteringDecorator(parser, includeTypes, excludeTypes);
    }

    /**
     * Decorates the given parsers into a virtual parser, where they'll
     * be tried in preference order until one works without error.
     *
     * @deprecated This has been replaced by {@link FallbackParser}
     */
    @Deprecated
    public static final Parser withFallbacks(final Collection<? extends Parser> parsers,
                                             final Set<MediaType> types) {
        // Delegate to the new FallbackParser for now, until people upgrade
        // Keep old behaviour on metadata, which was to preseve all
        MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();
        Parser p = new FallbackParser(registry, MetadataPolicy.KEEP_ALL, parsers);

        if (types == null || types.isEmpty()) {
            return p;
        }
        return withTypes(p, types);
    }

    /**
     * A ParserDecorator that filters supported mime types.
     * Stores include/exclude sets for round-trip serialization.
     * Results are cached when includeTypes is specified (deterministic case).
     */
    public static class MimeFilteringDecorator extends ParserDecorator {
        private static final long serialVersionUID = 1L;

        private final Set<MediaType> includeTypes;
        private final Set<MediaType> excludeTypes;
        private volatile Set<MediaType> cachedTypes;

        public MimeFilteringDecorator(Parser parser, Set<MediaType> includeTypes,
                                      Set<MediaType> excludeTypes) {
            super(parser);
            this.includeTypes = includeTypes != null ?
                    Collections.unmodifiableSet(new HashSet<>(includeTypes)) : Collections.emptySet();
            this.excludeTypes = excludeTypes != null ?
                    Collections.unmodifiableSet(new HashSet<>(excludeTypes)) : Collections.emptySet();
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            // If includeTypes is specified, result is deterministic - use cache
            if (!includeTypes.isEmpty()) {
                if (cachedTypes == null) {
                    Set<MediaType> types = new HashSet<>(includeTypes);
                    types.removeAll(excludeTypes);
                    cachedTypes = Collections.unmodifiableSet(types);
                }
                return cachedTypes;
            }

            // Otherwise compute from wrapped parser (may vary by context)
            Set<MediaType> types = new HashSet<>(super.getSupportedTypes(context));
            types.removeAll(excludeTypes);
            return types;
        }

        @Override
        public String getDecorationName() {
            return "Mime Filtering";
        }

        /**
         * @return the included mime types, or empty set if no include filter
         */
        public Set<MediaType> getIncludeTypes() {
            return includeTypes;
        }

        /**
         * @return the excluded mime types, or empty set if no exclude filter
         */
        public Set<MediaType> getExcludeTypes() {
            return excludeTypes;
        }
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
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        parser.parse(tis, handler, metadata, context);
    }

    /**
     * @return A name/description of the decoration, or null if none available
     */
    public String getDecorationName() {
        return null;
    }

    /**
     * Gets the parser wrapped by this ParserDecorator
     *
     * @return the parser wrapped by this ParserDecorator
     */
    public Parser getWrappedParser() {
        return this.parser;
    }
}
