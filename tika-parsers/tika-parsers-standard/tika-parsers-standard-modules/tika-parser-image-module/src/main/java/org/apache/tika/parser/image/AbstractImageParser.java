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
import java.nio.file.Files;
import java.nio.file.Path;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public abstract class AbstractImageParser extends AbstractParser {

    public static String OCR_MEDIATYPE_PREFIX = "ocr-";

    /**
     *
     * @param mediaType
     * @return ocr media type if mediatype is not null; returns null if mediatype is null
     */
    static MediaType convertToOCRMediaType(MediaType mediaType) {
        if (mediaType == null) {
            return null;
        }
        return new MediaType(mediaType.getType(), OCR_MEDIATYPE_PREFIX + mediaType.getSubtype());
    }

    abstract void extractMetadata(InputStream is, ContentHandler contentHandler, Metadata metadata,
                                  ParseContext parseContext)
            throws IOException, SAXException, TikaException;

    //if the parser needs to normalize the mediaType, override this.
    //this is a no-op, returning the mediaType that is sent in
    MediaType normalizeMediaType(MediaType mediaType) {
        return mediaType;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        String mediaTypeString = metadata.get(Metadata.CONTENT_TYPE);
        //note: mediaType can be null if mediaTypeString is null or
        //not parseable.
        MediaType mediaType = normalizeMediaType(MediaType.parse(mediaTypeString));
        MediaType ocrMediaType = convertToOCRMediaType(mediaType);
        Parser ocrParser = EmbeddedDocumentUtil.getStatelessParser(context);
        if (ocrMediaType == null ||
                ocrParser == null || !ocrParser.getSupportedTypes(context).contains(ocrMediaType)) {
            extractMetadata(stream, handler, metadata, context);
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            xhtml.endDocument();
            return;
        }

        TemporaryResources tmpResources = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmpResources, metadata);
        Exception metadataException = null;
        try {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            Path path = tis.getPath();
            try (InputStream pathStream = Files.newInputStream(path)) {
                extractMetadata(pathStream, new EmbeddedContentHandler(xhtml), metadata, context);
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                metadataException = e;
            }

            try (InputStream pathStream = Files.newInputStream(path)) {
                //specify ocr content type
                metadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE,
                        ocrMediaType.toString());
                //need to use bodycontenthandler to filter out re-dumping of metadata
                //in xhtmlhandler
                ocrParser.parse(pathStream,
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
