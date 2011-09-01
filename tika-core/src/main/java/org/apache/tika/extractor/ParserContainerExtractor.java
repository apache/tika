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
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

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
        this(TikaConfig.getDefaultConfig());
    }

    public ParserContainerExtractor(TikaConfig config) {
        this(new AutoDetectParser(config),
                new DefaultDetector(config.getMimeRepository()));
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
            TikaInputStream stream, ContainerExtractor recurseExtractor,
            EmbeddedResourceHandler handler)
            throws IOException, TikaException {
        ParseContext context = new ParseContext();
        context.set(Parser.class, new RecursiveParser(recurseExtractor, handler));
        try {
            parser.parse(stream, new DefaultHandler(), new Metadata(), context);
        } catch (SAXException e) {
            throw new TikaException("Unexpected SAX exception", e);
        }
    }

    private class RecursiveParser extends AbstractParser {

        private final ContainerExtractor extractor;

        private final EmbeddedResourceHandler handler;

        private RecursiveParser(
                ContainerExtractor extractor,
                EmbeddedResourceHandler handler) {
            this.extractor = extractor;
            this.handler = handler;
        }

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return parser.getSupportedTypes(context);
        }

        public void parse(
                InputStream stream, ContentHandler ignored,
                Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {
            TemporaryResources tmp = new TemporaryResources();
            try {
                TikaInputStream tis = TikaInputStream.get(stream, tmp);

                // Figure out what we have to process
                String filename = metadata.get(Metadata.RESOURCE_NAME_KEY);
                MediaType type = detector.detect(tis, metadata);

                if (extractor == null) {
                    // Let the handler process the embedded resource 
                    handler.handle(filename, type, tis);
                } else {
                    // Use a temporary file to process the stream twice
                    File file = tis.getFile();

                    // Let the handler process the embedded resource
                    InputStream input = TikaInputStream.get(file);
                    try {
                        handler.handle(filename, type, input);
                    } finally {
                        input.close();
                    }

                    // Recurse
                    extractor.extract(tis, extractor, handler);
                }
            } finally {
                tmp.dispose();
            }
        }

    }

}
