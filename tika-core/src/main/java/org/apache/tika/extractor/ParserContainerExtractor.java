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
package org.apache.tika.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * An implementation of {@link ContainerExtractor} powered by the
 *  regular {@link Parser} classes.
 * This allows you to easily extract out all the embedded resources
 *  from within contain files, whilst using the normal parsers
 *  to do the work.
 * By default the {@link AutoDetectParser} will be used, to allow
 *  extraction from the widest range of containers.
 */
public class ParserContainerExtractor implements ContainerExtractor {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 2261131045580861514L;

    private final Parser parser;

    private final Detector detector;

    public ParserContainerExtractor() {
        this(TikaConfig.getDefaultConfig());
    }

    public ParserContainerExtractor(TikaConfig config) {
        this(new AutoDetectParser(config), config.getMimeRepository());
    }

    public ParserContainerExtractor(Parser parser, Detector detector) {
        this.parser = parser;
        this.detector = detector;
    }

    public boolean isSupported(TikaInputStream input) throws IOException {
        MediaType type = detector.detect(input, new Metadata());
        return parser.getSupportedTypes(new ParseContext()).contains(type);
    }

    public void extract(
            TikaInputStream stream, final ContainerExtractor recurseExtractor,
            final EmbeddedResourceHandler handler)
            throws IOException, TikaException {
        ParseContext context = new ParseContext();
        context.set(Parser.class, new Parser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return parser.getSupportedTypes(context);
            }
            public void parse(InputStream stream, ContentHandler ignored,
                    Metadata metadata, ParseContext context)
                    throws IOException, SAXException, TikaException {
                // Figure out what we have to process
                String filename = metadata.get(Metadata.RESOURCE_NAME_KEY);
                MediaType type;
                if(metadata.get(Metadata.CONTENT_TYPE) != null) {
                   type = MediaType.parse( metadata.get(Metadata.CONTENT_TYPE) );
                } else {
                   if(! stream.markSupported()) {
                      stream = TikaInputStream.get(stream);
                   }
                   type = detector.detect(stream, metadata);
                }
                
                // Let the handler process the embedded resource 
                handler.handle(filename, type, stream);
                
                // Recurse if requested
                if(recurseExtractor != null) {
                   if(recurseExtractor == ParserContainerExtractor.this) {
                      parser.parse(stream, new DefaultHandler(), metadata, context);
                   } else {
                      recurseExtractor.extract(
                            TikaInputStream.get(stream), recurseExtractor, handler
                      );
                   }
                }
            }
            public void parse(InputStream stream, ContentHandler handler,
                    Metadata metadata) throws IOException, SAXException,
                    TikaException {
                parse(stream, handler, metadata, new ParseContext());
            }
        });
        try {
            parser.parse(stream, new DefaultHandler(), new Metadata(), context);
        } catch (SAXException e) {
            throw new TikaException("Unexpected SAX exception", e);
        }
    }

}
