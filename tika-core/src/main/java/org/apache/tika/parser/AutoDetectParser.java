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
import java.nio.file.Path;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.digest.DigestHelper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.hook.ParseHooks;
import org.apache.tika.sax.SecureContentHandler;

public class AutoDetectParser extends CompositeParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 6110455808615143122L;
    //private final TikaConfig config;

    /**
     * The type detector used by this parser to auto-detect the type
     * of a document.
     */
    private Detector detector; // always set in the constructor

    /**
     * Configuration used when initializing a SecureContentHandler
     * and the TikaInputStream.
     */
    private AutoDetectParserConfig autoDetectParserConfig;

    /**
     * Creates an auto-detecting parser instance using the default Tika
     * configuration.
     */
    public AutoDetectParser() {
        this(new DefaultDetector(), new DefaultParser());
    }

    public AutoDetectParser(Detector detector) {
        this(detector, new DefaultParser());
    }

    /**
     * Creates an auto-detecting parser instance using the specified set of parser.
     * This allows one to create a Tika configuration where only a subset of the
     * available parsers have their 3rd party jars included, as otherwise the
     * use of the default TikaConfig will throw various "ClassNotFound" exceptions.
     *
     * @param parsers
     */
    public AutoDetectParser(Parser... parsers) {
        this(new DefaultDetector(), parsers);
    }

    public AutoDetectParser(Detector detector, Parser... parsers) {
        super(MediaTypeRegistry.getDefaultRegistry(), parsers);
        setDetector(detector);
        setAutoDetectParserConfig(AutoDetectParserConfig.DEFAULT);
    }

    public AutoDetectParser(MediaTypeRegistry mediaTypeRegistry, Parser parser, Detector detector,
                             AutoDetectParserConfig autoDetectParserConfig) {
        super(mediaTypeRegistry, parser);
        setFallback(getFallbackFrom(parser));
        setDetector(detector);
        setAutoDetectParserConfig(autoDetectParserConfig);
    }

    public static Parser build(CompositeParser parser, Detector detector,
                               AutoDetectParserConfig autoDetectParserConfig) {
        return new AutoDetectParser(parser.getMediaTypeRegistry(), parser, detector,
                autoDetectParserConfig);
    }

    private static Parser getFallbackFrom(Parser defaultParser) {
        if (defaultParser instanceof DefaultParser) {
            return ((DefaultParser) defaultParser).getFallback();
        }
        return new EmptyParser();
    }

    /**
     * Returns the type detector used by this parser to auto-detect the type
     * of a document.
     *
     * @return type detector
     * @since Apache Tika 0.4
     */
    public Detector getDetector() {
        return detector;
    }

    /**
     * Sets the type detector used by this parser to auto-detect the type
     * of a document.
     *
     * @param detector type detector
     * @since Apache Tika 0.4
     */
    public void setDetector(Detector detector) {
        this.detector = detector;
    }

    /**
     * Sets the configuration that will be used to create SecureContentHandlers
     * that will be used for parsing.
     *
     * @param autoDetectParserConfig type SecureContentHandlerConfig
     * @since Apache Tika 2.1.1
     */
    public void setAutoDetectParserConfig(AutoDetectParserConfig autoDetectParserConfig) {
        this.autoDetectParserConfig = autoDetectParserConfig;
    }

    public AutoDetectParserConfig getAutoDetectParserConfig() {
        return this.autoDetectParserConfig;
    }

    private ParseHooks parseHooks;

    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // Compute digests before type detection if configured
        // DigesterFactory is retrieved from ParseContext (configured via parse-context)
        DigestHelper.maybeDigest(tis, metadata, context);

        // Signal to detectors that parsing will follow - allows them to prepare
        // (e.g., salvage corrupted ZIP files for parser reuse)
        context.set(ParsingIntent.class, ParsingIntent.WILL_PARSE);

        // Automatically detect the MIME type of the document
        MediaType type = detector.detect(tis, metadata, context);
        metadata.set(HttpHeaders.CONTENT_TYPE, type.toString());

        // Metadata-only pseudo-parse: register the entry, skip the content parse.
        if (context.get(MetadataOnlyParse.class) != null) {
            return;
        }
        //check for zero-byte inputstream
        if (tis.getOpenContainer() == null) {
            if (autoDetectParserConfig.getThrowOnZeroBytes()) {
                tis.mark(1);
                if (tis.read() == -1) {
                    throw new ZeroByteFileException("InputStream must have > 0 bytes");
                }
                tis.reset();
            }
        }
        handler = decorateHandler(handler, metadata, context, autoDetectParserConfig);

        SecureContentHandler sch = handler != null ?
                createSecureContentHandler(handler, tis, context) : null;

        initializeEmbeddedParserAndDetector(context);
        // every document, top-level or embedded, enters here once: the hooks' seam
        boolean seeded = parseHooks != null && context.get(ParseHooks.class) == null;
        if (seeded) {
            context.set(ParseHooks.class, parseHooks);
        }
        ParseHooks hooks = context.get(ParseHooks.class);
        boolean topLevel = hooks != null && context.get(ParseHooks.Run.class) == null;
        if (topLevel) {
            context.set(ParseHooks.Run.class, new ParseHooks.Run());
        }
        ParseHooks.Run run = hooks == null ? null : context.get(ParseHooks.Run.class);
        Metadata parent = run == null ? null : run.enter(metadata);
        boolean failed = true;
        try {
            if (topLevel) {
                hooks.start(metadata, context);
            }
            Path pinned = hooks == null ? null : hooks.pin(type, metadata, tis, context);
            try {
                super.parse(tis, sch, metadata, context);
            } catch (SAXException e) {
                sch.throwIfCauseOf(e);
                throw e;
            }
            failed = false;
            if (pinned != null) {
                hooks.offer(type, metadata, parent, pinned, context);
            }
        } finally {
            if (run != null) {
                run.exit(parent);
            }
            if (topLevel) {
                try {
                    hooks.end(metadata, failed, context);
                } finally {
                    context.set(ParseHooks.Run.class, null);
                    if (seeded) {
                        context.set(ParseHooks.class, null);
                    }
                }
            }
        }
    }

    /** Hooks set at config load; seeded into each parse's context. */
    public void setParseHooks(ParseHooks parseHooks) {
        this.parseHooks = parseHooks;
    }

    public ParseHooks getParseHooks() {
        return parseHooks;
    }

    private ContentHandler decorateHandler(ContentHandler handler, Metadata metadata,
                                           ParseContext context,
                                           AutoDetectParserConfig autoDetectParserConfig) {
        if (context.get(RecursiveParserWrapper.RecursivelySecureContentHandler.class) != null) {
            //using the recursiveparserwrapper. we should decorate this handler
            return autoDetectParserConfig.getContentHandlerDecoratorFactory()
                    .decorate(handler, metadata, context);
        }
        ParseRecord parseRecord = context.get(ParseRecord.class);
        if (parseRecord == null || parseRecord.getDepth() == 0) {
            return autoDetectParserConfig.getContentHandlerDecoratorFactory()
                    .decorate(handler, metadata, context);
        }
        //else do not decorate
        return handler;
    }

    /**
     * Ensures embedded-document processing has a Parser and a Detector to fall back on,
     * regardless of whether an EmbeddedDocumentExtractor is already bound in the context --
     * every stock extractor (ParsingEmbeddedDocumentExtractor and its subclasses) reads
     * Parser.class via DelegatingParser, so leaving it unset silently drops embedded content
     * even when an extractor is present.
     */
    private void initializeEmbeddedParserAndDetector(ParseContext context) {
        // pass in self for embedded documents unless
        // the caller has specified a parser
        Parser p = context.get(Parser.class);
        if (p == null) {
            context.set(Parser.class, this);
        }
        // pass in own detector for embedded documents unless
        // the caller has specified one
        Detector d = context.get(Detector.class);
        if (d == null) {
            context.set(Detector.class, getDetector());
        }
    }

    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        ParseContext context = new ParseContext();
        context.set(Parser.class, this);
        parse(tis, handler, metadata, context);
    }

    private SecureContentHandler createSecureContentHandler(ContentHandler handler,
                                                            TikaInputStream tis,
                                                            ParseContext context) {
        // SecureContentHandler reads limits from OutputLimits in ParseContext
        return SecureContentHandler.newInstance(handler, tis, context);
    }

}
