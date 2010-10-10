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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TaggedInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.sax.TaggedContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Composite parser that delegates parsing tasks to a component parser
 * based on the declared content type of the incoming document. A fallback
 * parser is defined for cases where a parser for the given content type is
 * not available.
 */
public class CompositeParser implements Parser {

    /** Serial version UID */
    private static final long serialVersionUID = 2192845797749627824L;

    /**
     * Media type registry.
     */
    private MediaTypeRegistry registry;

    /**
     * List of component parsers.
     */
    private List<Parser> parsers;

    /**
     * The fallback parser, used when no better parser is available.
     */
    private Parser fallback = new EmptyParser();

    public CompositeParser(MediaTypeRegistry registry, List<Parser> parsers) {
        this.parsers = parsers;
        this.registry = registry;
    }

    public CompositeParser(MediaTypeRegistry registry, Parser... parsers) {
        this(registry, Arrays.asList(parsers));
    }

    public CompositeParser() {
        this(new MediaTypeRegistry());
    }

    public Map<MediaType, Parser> getParsers(ParseContext context) {
        Map<MediaType, Parser> map = new HashMap<MediaType, Parser>();
        for (Parser parser : parsers) {
            for (MediaType type : parser.getSupportedTypes(context)) {
                map.put(type, parser);
            }
        }
        return map;
    }

    /**
     * Returns the media type registry used to infer type relationships.
     *
     * @since Apache Tika 0.8
     * @return media type registry
     */
    public MediaTypeRegistry getMediaTypeRegistry() {
        return registry;
    }

    /**
     * Sets the media type registry used to infer type relationships.
     *
     * @since Apache Tika 0.8
     * @param registry media type registry
     */
    public void setMediaTypeRegistry(MediaTypeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Returns the component parsers.
     *
     * @return component parsers, keyed by media type
     */
    public Map<MediaType, Parser> getParsers() {
        return getParsers(new ParseContext());
    }

    /**
     * Sets the component parsers.
     *
     * @param parsers component parsers, keyed by media type
     */
    public void setParsers(Map<MediaType, Parser> parsers) {
        this.parsers = new ArrayList<Parser>(parsers.size());
        for (Map.Entry<MediaType, Parser> entry : parsers.entrySet()) {
            this.parsers.add(ParserDecorator.withTypes(
                    entry.getValue(), Collections.singleton(entry.getKey())));
        }
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
     * and uses the fallback parser if a better match is not found. The
     * type hierarchy information included in the configured media type
     * registry is used when looking for a matching parser instance.
     * <p>
     * Subclasses can override this method to provide more accurate
     * parser resolution.
     *
     * @param metadata document metadata
     * @return matching parser
     */
    protected Parser getParser(Metadata metadata) {
        return getParser(metadata, new ParseContext());
    }

    protected Parser getParser(Metadata metadata, ParseContext context) {
        Map<MediaType, Parser> map = getParsers(context);
        MediaType type = MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
        while (type != null) {
            Parser parser = map.get(type);
            if (parser != null) {
                return parser;
            }
            type = registry.getSupertype(type);
        }
        return fallback;
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return getParsers(context).keySet();
    }

    /**
     * Delegates the call to the matching component parser.
     * <p>
     * Potential {@link RuntimeException}s, {@link IOException}s and
     * {@link SAXException}s unrelated to the given input stream and content
     * handler are automatically wrapped into {@link TikaException}s to better
     * honor the {@link Parser} contract.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        Parser parser = getParser(metadata);
        TaggedInputStream taggedStream = new TaggedInputStream(stream);
        TaggedContentHandler taggedHandler = new TaggedContentHandler(handler);
        try {
            parser.parse(taggedStream, taggedHandler, metadata, context);
        } catch (RuntimeException e) {
            throw new TikaException(
                    "Unexpected RuntimeException from " + parser, e);
        } catch (IOException e) {
            taggedStream.throwIfCauseOf(e);
            throw new TikaException(
                    "TIKA-198: Illegal IOException from " + parser, e);
        } catch (SAXException e) {
            taggedHandler.throwIfCauseOf(e);
            throw new TikaException(
                    "TIKA-237: Illegal SAXException from " + parser, e);
        }
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
