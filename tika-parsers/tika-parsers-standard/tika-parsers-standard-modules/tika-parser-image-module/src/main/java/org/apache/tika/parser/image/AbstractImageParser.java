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
package org.apache.tika.parser.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.enricher.CompositeContentEnricher;
import org.apache.tika.parser.enricher.ContentEnrichers;
import org.apache.tika.parser.enricher.EnrichingParser;
import org.apache.tika.parser.enricher.LegacyDispatchEnricher;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public abstract class AbstractImageParser implements Parser, EnrichingParser {

    /** @deprecated use {@link LegacyDispatchEnricher#OCR_MEDIATYPE_PREFIX} */
    @Deprecated
    public static String OCR_MEDIATYPE_PREFIX = LegacyDispatchEnricher.OCR_MEDIATYPE_PREFIX;

    private CompositeContentEnricher contentEnrichers;

    abstract void extractMetadata(InputStream is, ContentHandler contentHandler, Metadata metadata,
                                  ParseContext parseContext)
            throws IOException, SAXException, TikaException;

    //if the parser needs to normalize the mediaType, override this.
    //this is a no-op, returning the mediaType that is sent in
    MediaType normalizeMediaType(MediaType mediaType) {
        return mediaType;
    }

    @Override
    public void setContentEnrichers(CompositeContentEnricher contentEnrichers) {
        this.contentEnrichers = contentEnrichers;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        String mediaTypeString = metadata.get(HttpHeaders.CONTENT_TYPE);
        //note: mediaType can be null if mediaTypeString is null or
        //not parseable.
        MediaType mediaType = normalizeMediaType(MediaType.parse(mediaTypeString));
        Parser enricher = ContentEnrichers.get(contentEnrichers, mediaType, context);
        if (enricher == null) {
            extractMetadata(tis, handler, metadata, context);
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            xhtml.endDocument();
            return;
        }

        TemporaryResources tmpResources = new TemporaryResources();
        Exception metadataException = null;
        try {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            Path path = tis.getPath();
            // a TikaInputStream over the path, not a raw stream: the content is already on
            // disk, so this takes the file path in extractMetadata instead of caching a
            // second copy in memory whose budget reservation nothing here would release
            try (TikaInputStream pathStream = TikaInputStream.get(path)) {
                extractMetadata(pathStream, new EmbeddedContentHandler(xhtml), metadata, context);
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                metadataException = e;
            }

            try (TikaInputStream pathStream = TikaInputStream.get(path)) {
                //need to use bodycontenthandler to filter out re-dumping of metadata
                //in xhtmlhandler
                enricher.parse(pathStream,
                        new EmbeddedContentHandler(new BodyContentHandler(xhtml)), metadata,
                        context);
            }
            xhtml.endDocument();
        } finally {
            tmpResources.close();
        }
        if (metadataException != null) {
            throw new TikaException("problem extracting metadata", metadataException);
        }
    }
}
