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
import java.io.InputStream;
import java.io.Serializable;
import java.util.Map;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.utils.ExceptionUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Utility class to handle common issues with embedded documents.
 * <p/>
 * Use statically if all that is needed is getting the EmbeddedDocumentExtractor.
 * Otherwise, instantiate an instance.
 * <p/>
 * Note: This is not thread safe.  Make sure to instantiate one per thread.
 */
public class EmbeddedDocumentUtil implements Serializable {


    private final ParseContext context;
    private final EmbeddedDocumentExtractor embeddedDocumentExtractor;
    //these are lazily initialized and can be null
    private TikaConfig tikaConfig;
    private MimeTypes mimeTypes;
    private Detector detector;

    public EmbeddedDocumentUtil(ParseContext context) {
        this.context = context;
        this.embeddedDocumentExtractor = getEmbeddedDocumentExtractor(context);
    }

    /**
     * This offers a uniform way to get an EmbeddedDocumentExtractor from a ParseContext.
     * As of Tika 1.15, an AutoDetectParser will automatically be added to parse
     * embedded documents if no Parser.class is specified in the ParseContext.
     * <p/>
     * If you'd prefer not to parse embedded documents, set Parser.class
     * to {@link org.apache.tika.parser.EmptyParser} in the ParseContext.
     *
     * @param context
     * @return EmbeddedDocumentExtractor
     */
    public static EmbeddedDocumentExtractor getEmbeddedDocumentExtractor(ParseContext context) {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class);
        if (extractor == null) {
            //ensure that an AutoDetectParser is
            //available for parsing embedded docs TIKA-2096
            Parser embeddedParser = context.get(Parser.class);
            if (embeddedParser == null) {
                TikaConfig tikaConfig = context.get(TikaConfig.class);
                if (tikaConfig == null) {
                    context.set(Parser.class, new AutoDetectParser());
                } else {
                    context.set(Parser.class, new AutoDetectParser(tikaConfig));
                }
            }
            extractor = new ParsingEmbeddedDocumentExtractor(context);
        }
        return extractor;
    }


    public PasswordProvider getPasswordProvider() {
        return context.get(PasswordProvider.class);
    }

    public Detector getDetector() {
        //be as lazy as possible and cache
        Detector localDetector = context.get(Detector.class);
        if (localDetector != null) {
            return localDetector;
        }
        if (detector != null) {
            return detector;
        }

        detector = getTikaConfig().getDetector();
        return detector;
    }

    public MimeTypes getMimeTypes() {
        MimeTypes localMimeTypes = context.get(MimeTypes.class);
        //be as lazy as possible and cache the mimeTypes
        if (localMimeTypes != null) {
            return localMimeTypes;
        }
        if (mimeTypes != null) {
            return mimeTypes;
        }
        mimeTypes = getTikaConfig().getMimeRepository();
        return mimeTypes;
    }

    /**
     * @return Returns a {@link TikaConfig} -- trying to find it first in the ParseContext
     * that was included during initialization, and then creating a new one from
     * via {@link TikaConfig#getDefaultConfig()} if it can't find one in the
     * ParseContext. This caches the default config so that it only has to be created once.
     */
    public TikaConfig getTikaConfig() {
        //be as lazy as possible and cache the TikaConfig
        if (tikaConfig == null) {
            tikaConfig = context.get(TikaConfig.class);
            if (tikaConfig == null) {
                tikaConfig = TikaConfig.getDefaultConfig();
            }
        }
        return tikaConfig;
    }

    public String getExtension(TikaInputStream is, Metadata metadata) {
        String mimeString = metadata.get(Metadata.CONTENT_TYPE);

        //use the buffered mimetypes as default
        MimeTypes localMimeTypes = getMimeTypes();

        MimeType mimeType = null;
        boolean detected = false;
        if (mimeString != null) {
            try {
                mimeType = localMimeTypes.forName(mimeString);
            } catch (MimeTypeException e) {
                //swallow
            }
        }
        if (mimeType == null) {
            try {
                MediaType mediaType = getDetector().detect(is, metadata);
                mimeType = localMimeTypes.forName(mediaType.toString());
                detected = true;
                is.reset();
            } catch (IOException e) {
                //swallow
            } catch (MimeTypeException e) {
                //swallow
            }
        }
        if (mimeType != null) {
            if (detected) {
                //set or correct the mime type
                metadata.set(Metadata.CONTENT_TYPE, mimeType.toString());
            }
            return mimeType.getExtension();
        }
        return ".bin";
    }

    /**
     * @return Returns a {@link TikaConfig} -- trying to find it first in the ParseContext
     * that was included in the initialization, and then creating a new one from
     * via {@link TikaConfig#getDefaultConfig()} if it can't find one in the
     * ParseContext.
     *
     * @deprecated as of 1.17, use {@link #getTikaConfig()} instead
     */
    @Deprecated
    public TikaConfig getConfig() {
        TikaConfig config = context.get(TikaConfig.class);
        if (config == null) {
            config = TikaConfig.getDefaultConfig();
        }
        return config;
    }

    public static void recordException(Throwable t, Metadata m) {
        String ex = ExceptionUtils.getFilteredStackTrace(t);
        m.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, ex);
    }

    public static void recordEmbeddedStreamException(Throwable t, Metadata m) {
        String ex = ExceptionUtils.getFilteredStackTrace(t);
        m.add(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM, ex);
    }

    public boolean shouldParseEmbedded(Metadata m) {
        return getEmbeddedDocumentExtractor().shouldParseEmbedded(m);
    }

    private EmbeddedDocumentExtractor getEmbeddedDocumentExtractor() {
        return embeddedDocumentExtractor;
    }

    public void parseEmbedded(InputStream inputStream, ContentHandler handler,
                              Metadata metadata, boolean outputHtml) throws IOException, SAXException {
        embeddedDocumentExtractor.parseEmbedded(inputStream, handler, metadata, outputHtml);
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
     * @param clazz parser class to search for
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
                p = findInDecorated((ParserDecorator)p, clazz);
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
            candidate = findInDecorated((ParserDecorator)candidate, clazz);
        }
        return candidate;
    }

    private static Parser findInComposite(CompositeParser p, Class clazz, ParseContext context) {
        Map<MediaType, Parser> map = p.getParsers(context);
        for (Map.Entry<MediaType, Parser> e : map.entrySet()) {
            Parser candidate = e.getValue();
            if (equals(candidate, clazz)) {
                return candidate;
            }
            if (candidate instanceof ParserDecorator) {
                candidate = findInDecorated((ParserDecorator)candidate, clazz);
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
