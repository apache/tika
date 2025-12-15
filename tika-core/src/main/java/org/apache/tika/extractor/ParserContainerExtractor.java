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
package org.apache.tika.extractor;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.StatefulParser;

/**
 * An implementation of {@link ContainerExtractor} powered by the regular
 * {@link Parser} API. This allows you to easily extract out all the
 * embedded resources from within container files supported by normal Tika
 * parsers. By default the {@link AutoDetectParser} will be used, to allow
 * extraction from the widest range of containers.
 */
public class ParserContainerExtractor implements ContainerExtractor {

    /** Serial version UID */
    private static final long serialVersionUID = 2261131045580861514L;

    private final Parser parser;

    private final Detector detector;

    public ParserContainerExtractor() {
        this(new AutoDetectParser(), new DefaultDetector());
    }

    public ParserContainerExtractor(Parser parser, Detector detector) {
        this.parser = parser;
        this.detector = detector;
    }

    @Override
    public boolean isSupported(TikaInputStream input) throws IOException {
        MediaType type = detector.detect(input, new Metadata());
        return parser.getSupportedTypes(new ParseContext()).contains(type);
    }

    @Override
    public void extract(
            TikaInputStream stream, ContainerExtractor recurseExtractor,
            EmbeddedResourceHandler handler)
            throws IOException, TikaException {
        ParseContext context = new ParseContext();
        context.set(Parser.class, new RecursiveParser(parser, recurseExtractor, handler));
        try {
            parser.parse(stream, new DefaultHandler(), new Metadata(), context);
        } catch (SAXException e) {
            throw new TikaException("Unexpected SAX exception", e);
        }
    }

    private class RecursiveParser extends StatefulParser {

        private final ContainerExtractor extractor;

        private final EmbeddedResourceHandler handler;

        private RecursiveParser(Parser statelessParser,
                ContainerExtractor extractor,
                EmbeddedResourceHandler handler) {
            super(statelessParser);
            this.extractor = extractor;
            this.handler = handler;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return parser.getSupportedTypes(context);
        }

        @Override
        public void parse(
                TikaInputStream stream, ContentHandler ignored,
                Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {
            // Figure out what we have to process
            String filename = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            MediaType type = detector.detect(stream, metadata);

            if (extractor == null) {
                // Let the handler process the embedded resource
                handler.handle(filename, type, stream);
            } else {
                // Use a temporary file to process the stream twice
                File file = stream.getFile();

                // Let the handler process the embedded resource
                try (TikaInputStream input = TikaInputStream.get(file.toPath())) {
                    handler.handle(filename, type, input);
                }

                // Recurse
                extractor.extract(stream, extractor, handler);
            }
        }

    }

}
