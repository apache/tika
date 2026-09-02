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
package org.apache.tika.parser.enricher;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Reproduces the pre-4.1 {@code image/ocr-*} dispatch when no {@code "content-enrichers"}
 * list is configured: mints the synthetic {@code ocr-} media type, sets
 * {@link TikaCoreProperties#CONTENT_TYPE_PARSER_OVERRIDE} and re-enters the composite
 * parser, restoring the metadata afterwards. Whichever engine won the {@code ocr-*}
 * registration in the composite still wins here, so precedence-by-presence (adding
 * e.g. tika-parser-tess4j-module to the classpath) is preserved exactly.
 * <p>
 * This confines the pseudo-mime dance formerly hand-rolled in both
 * {@code AbstractImageParser} and {@code AbstractPDF2XHTML} to one class, to be retired
 * once OCR engines are selected by name.
 *
 * @since Apache Tika 4.1
 */
public class LegacyDispatchEnricher implements Parser {

    private static final long serialVersionUID = 1L;

    public static final String OCR_MEDIATYPE_PREFIX = "ocr-";

    private final MediaType mediaType;

    private final Parser composite;

    /**
     * @param mediaType the real (already normalized) media type of the bytes to derive from
     * @param composite the composite parser to re-enter; the caller has already verified
     *                  it claims the synthetic {@code ocr-} type (re-verifying here would
     *                  rebuild the composite's full supported-types map per invocation)
     */
    public LegacyDispatchEnricher(MediaType mediaType, Parser composite) {
        this.mediaType = mediaType;
        this.composite = composite;
    }

    /**
     * @return the synthetic dispatch type for a real media type, or null if mediaType is null
     */
    public static MediaType toOcrMediaType(MediaType mediaType) {
        if (mediaType == null) {
            return null;
        }
        return new MediaType(mediaType.getType(), OCR_MEDIATYPE_PREFIX + mediaType.getSubtype());
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.singleton(mediaType);
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        MediaType ocrMediaType = toOcrMediaType(mediaType);
        if (composite == null) {
            throw new TikaException("No parser is registered for " + ocrMediaType);
        }
        String originalOverride = metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE);
        String originalContentType = metadata.get(HttpHeaders.CONTENT_TYPE);
        metadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE, ocrMediaType.toString());
        try {
            composite.parse(tis, handler, metadata, context);
        } finally {
            if (originalOverride == null) {
                metadata.remove(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE.getName());
            } else {
                metadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE, originalOverride);
            }
            if (originalContentType == null) {
                metadata.remove(HttpHeaders.CONTENT_TYPE);
            } else {
                metadata.set(HttpHeaders.CONTENT_TYPE, originalContentType);
            }
        }
    }
}
