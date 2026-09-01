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


import java.io.IOException;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.NoOpDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.StatefulParser;
import org.apache.tika.utils.ExceptionUtils;

/**
 * Static utility methods to handle common issues with embedded documents.
 */
public class EmbeddedDocumentUtil {

    private EmbeddedDocumentUtil() {
    }

    /**
     * Looks up the {@link EmbeddedDocumentExtractor} configured for this parse.
     * <p>
     * A configured parse (one that has gone through {@link org.apache.tika.parser.AutoDetectParser})
     * always has one bound in the context. If none is bound -- e.g. a concrete parser was
     * invoked directly with a bare {@link ParseContext} -- this returns the stateless
     * {@link ParsingEmbeddedDocumentExtractor#INSTANCE}, which delegates to whatever
     * {@link Parser} is in the context (or silently skips embedded documents if none is set).
     *
     * @param context the parse context
     * @return the EmbeddedDocumentExtractor to use for this parse
     */
    public static EmbeddedDocumentExtractor getEmbeddedDocumentExtractor(ParseContext context) {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class);
        return extractor != null ? extractor : ParsingEmbeddedDocumentExtractor.INSTANCE;
    }

    /**
     * Utility function to get the Parser that was sent in to the
     * ParseContext to handle embedded documents.  If it is stateful,
     * unwrap it to get its stateless delegating parser.
     * <p>
     * If there is no Parser in the parser context, this will return null.
     *
     * @param context
     * @return
     */
    public static Parser getStatelessParser(ParseContext context) {
        Parser p = context.get(Parser.class);
        if (p == null) {
            return null;
        }
        if (p instanceof StatefulParser) {
            return ((StatefulParser) p).getWrappedParser();
        }
        return p;
    }

    /**
     * Looks up the {@link Detector} configured for this parse.
     * <p>
     * A configured parse (one that has gone through {@link org.apache.tika.parser.AutoDetectParser})
     * always has one bound in the context. If none is bound -- e.g. a concrete parser was
     * invoked directly with a bare {@link ParseContext} -- this returns
     * {@link NoOpDetector#INSTANCE} rather than constructing an SPI-discovered
     * {@link DefaultDetector}: an honest "unknown" beats a partially-informed guess from a
     * detector the caller never configured.
     *
     * @param context the parse context
     * @return the Detector to use for this parse
     */
    public static Detector getDetector(ParseContext context) {
        Detector detector = context.get(Detector.class);
        return detector != null ? detector : NoOpDetector.INSTANCE;
    }

    public static MimeTypes getMimeTypes(ParseContext context) {
        MimeTypes mimeTypes = context.get(MimeTypes.class);
        return mimeTypes != null ? mimeTypes : MimeTypes.getDefaultMimeTypes();
    }

    public static String getExtension(TikaInputStream is, Metadata metadata, ParseContext context) {
        String mimeString = metadata.get(HttpHeaders.CONTENT_TYPE);

        MimeTypes mimeTypes = getMimeTypes(context);

        //a parseable declared type wins, even if we have no glob for it. Don't
        //detect just because the registry lookup came back empty -- that would
        //overwrite a type the calling parser set deliberately.
        if (mimeString != null && MediaType.parse(mimeString) != null) {
            return extensionOf(getRegisteredMimeType(mimeTypes, mimeString));
        }
        try {
            MediaType mediaType = getDetector(context).detect(is, metadata, context);
            is.reset();
            //set or correct the mime type. Record what was detected, not the
            //registry match, which may have fallen back to the base type.
            metadata.set(HttpHeaders.CONTENT_TYPE, mediaType.toString());
            return extensionOf(getRegisteredMimeType(mimeTypes, mediaType.toString()));
        } catch (IOException e) {
            //swallow
        }
        return ".bin";
    }

    private static String extensionOf(MimeType mimeType) {
        return mimeType == null ? "" : mimeType.getExtension();
    }

    /**
     * Normalizes internal OCR routing media types (e.g., {@code image/ocr-png})
     * back to standard media types (e.g., {@code image/png}).
     * Returns the input unchanged if it is not an OCR routing type.
     *
     * @param mediaType the media type string
     * @return the normalized media type string, or the original if no normalization needed
     */
    public static String normalizeMediaType(String mediaType) {
        if (mediaType != null && mediaType.startsWith("image/ocr-")) {
            return "image/" + mediaType.substring("image/ocr-".length());
        }
        return mediaType;
    }

    /**
     * Looks up the file extension for a given media type string.
     *
     * @param mediaType the media type string (e.g., "image/png"), parameters allowed
     * @return the extension including the dot (e.g., ".png"), or empty string if unknown
     */
    public static String getExtensionForMediaType(String mediaType) {
        if (mediaType == null) {
            return "";
        }
        MimeType mimeType =
                getRegisteredMimeType(MimeTypes.getDefaultMimeTypes(),
                        normalizeMediaType(mediaType));
        return mimeType == null ? "" : mimeType.getExtension();
    }

    /**
     * Not {@link MimeTypes#forName(String)}: that registers a new, glob-less type for
     * any name it doesn't recognize, so <code>text/plain; charset=UTF-8</code> would
     * lose its extension and add a registry entry per charset seen. This prefers an
     * exact parameterized match (<code>application/dita+xml;format=map</code> is real)
     * and otherwise falls back to the base type.
     *
     * @return the registered type, or null if unknown or invalid
     */
    private static MimeType getRegisteredMimeType(MimeTypes mimeTypes, String name) {
        try {
            return mimeTypes.getRegisteredMimeType(name);
        } catch (MimeTypeException e) {
            return null;
        }
    }

    /**
     * Type of embedded resource, used for generating canonical resource names.
     */
    public enum EmbeddedResourcePrefix {
        EMBEDDED("embedded"),
        IMAGE("image"),
        THUMBNAIL("thumbnail");

        private final String prefix;

        EmbeddedResourcePrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    /**
     * Generates a canonical resource name from a type, counter, and media type.
     * For example: {@code generateResourceName(EmbeddedResourcePrefix.EMBEDDED, 0, "image/png")}
     * returns {@code "embedded-0.png"}.
     *
     * @param type      the embedded resource type
     * @param count     the counter value
     * @param mediaType the media type string, or null if unknown
     * @return the generated resource name with extension
     */
    public static String generateResourceName(EmbeddedResourcePrefix type, int count,
                                               String mediaType) {
        return type.getPrefix() + "-" + count + getExtensionForMediaType(mediaType);
    }

    /**
     * Sets a generated resource name on the metadata and marks the extension as inferred.
     *
     * @param metadata  the metadata to update
     * @param type      the embedded resource type
     * @param count     the counter value
     * @param mediaType the media type string, or null if unknown
     */
    public static void setGeneratedResourceName(Metadata metadata, EmbeddedResourcePrefix type,
                                                 int count, String mediaType) {
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                generateResourceName(type, count, mediaType));
        metadata.set(TikaCoreProperties.RESOURCE_NAME_EXTENSION_INFERRED, true);
    }

    public static void recordException(Throwable t, Metadata m, ParseContext context) {
        m.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                ExceptionUtils.format(t, context));
    }

    /**
     * @deprecated since 4.1, removal planned for 5.0; use
     * {@link #recordException(Throwable, Metadata, ParseContext)} so the configured
     * {@link org.apache.tika.config.ExceptionReporting} applies. Unlike in 4.0.0, this now
     * keeps a bare {@link org.apache.tika.exception.TikaException} wrapper rather than
     * stripping it, so the first line of the recorded value may name the wrapper.
     */
    @Deprecated
    public static void recordException(Throwable t, Metadata m) {
        recordException(t, m, null);
    }

    public static void recordEmbeddedStreamException(Throwable t, Metadata m,
                                                     ParseContext context) {
        m.add(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM,
                ExceptionUtils.format(t, context));
    }

    /**
     * @deprecated since 4.1, removal planned for 5.0; use
     * {@link #recordEmbeddedStreamException(Throwable, Metadata, ParseContext)} so the
     * configured {@link org.apache.tika.config.ExceptionReporting} applies. Unlike in 4.0.0,
     * this now keeps a bare {@link org.apache.tika.exception.TikaException} wrapper rather
     * than stripping it, so the first line of the recorded value may name the wrapper.
     */
    @Deprecated
    public static void recordEmbeddedStreamException(Throwable t, Metadata m) {
        recordEmbeddedStreamException(t, m, null);
    }

    /**
     * Tries to find an existing parser within the ParseContext.
     * It looks inside of CompositeParsers and ParserDecorators.
     * The use case is when a parser needs to parse an internal stream
     * that is _part_ of the document, e.g. rtf body inside an msg.
     * <p/>
     * Can return <code>null</code> if the context contains no parser or
     * the correct parser can't be found.
     *
     * @param clazz   parser class to search for
     * @param context
     * @return
     */
    public static Parser tryToFindExistingLeafParser(Class clazz, ParseContext context) {
        Parser p = context.get(Parser.class);
        if (equals(p, clazz)) {
            return p;
        }
        Parser returnParser = null;
        if (p != null) {
            if (p instanceof ParserDecorator) {
                p = findInDecorated((ParserDecorator) p, clazz);
            }
            if (equals(p, clazz)) {
                return p;
            }
            if (p instanceof CompositeParser) {
                returnParser = findInComposite((CompositeParser) p, clazz, context);
            }
        }
        if (returnParser != null && equals(returnParser, clazz)) {
            return returnParser;
        }

        return null;
    }

    private static Parser findInDecorated(ParserDecorator p, Class clazz) {
        Parser candidate = p.getWrappedParser();
        if (equals(candidate, clazz)) {
            return candidate;
        }
        if (candidate instanceof ParserDecorator) {
            candidate = findInDecorated((ParserDecorator) candidate, clazz);
        }
        return candidate;
    }

    private static Parser findInComposite(CompositeParser p, Class clazz, ParseContext context) {
        for (Parser candidate : p.getAllComponentParsers()) {
            if (equals(candidate, clazz)) {
                return candidate;
            }
            if (candidate instanceof ParserDecorator) {
                candidate = findInDecorated((ParserDecorator) candidate, clazz);
            }
            if (equals(candidate, clazz)) {
                return candidate;
            }
            if (candidate instanceof CompositeParser) {
                candidate = findInComposite((CompositeParser) candidate, clazz, context);
            }
            if (equals(candidate, clazz)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean equals(Parser parser, Class clazz) {
        if (parser == null) {
            return false;
        }
        return parser.getClass().equals(clazz);
    }
}
