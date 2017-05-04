/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.microsoft;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.poi.hemf.extractor.HemfExtractor;
import org.apache.poi.hemf.record.AbstractHemfComment;
import org.apache.poi.hemf.record.HemfCommentPublic;
import org.apache.poi.hemf.record.HemfCommentRecord;
import org.apache.poi.hemf.record.HemfRecord;
import org.apache.poi.hemf.record.HemfRecordType;
import org.apache.poi.hemf.record.HemfText;
import org.apache.poi.util.RecordFormatException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Extracts files embedded in EMF and offers a
 * very rough capability to extract text if there
 * is text stored in the EMF.
 * <p/>
 * To improve text extraction, we'd have to implement
 * quite a bit more at the POI level.  We'd want to track changes
 * in font and use that information for identifying character sets,
 * inserting spaces and new lines.
 */
public class EMFParser extends AbstractParser {

    private static final MediaType MEDIA_TYPE = MediaType.image("emf");
    private static final MediaType WMF_MEDIA_TYPE = MediaType.image("wmf");

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MEDIA_TYPE);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor embeddedDocumentExtractor = null;
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try {
            HemfExtractor ex = new HemfExtractor(stream);
            long lastY = -1;
            long lastX = -1;
            long fudgeFactorX = 1000;//derive this from the font or frame/bounds information
            StringBuilder buffer = new StringBuilder();
            for (HemfRecord record : ex) {
                if (record.getRecordType() == HemfRecordType.comment) {
                    AbstractHemfComment comment = ((HemfCommentRecord) record).getComment();
                    if (comment instanceof HemfCommentPublic.MultiFormats) {
                        if (embeddedDocumentExtractor == null) {
                            embeddedDocumentExtractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
                        }
                        handleMultiFormats((HemfCommentPublic.MultiFormats)comment, xhtml, embeddedDocumentExtractor);
                    } else if (comment instanceof  HemfCommentPublic.WindowsMetafile) {
                        if (embeddedDocumentExtractor == null) {
                            embeddedDocumentExtractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
                        }
                        handleWMF((HemfCommentPublic.WindowsMetafile)comment, xhtml, embeddedDocumentExtractor);
                    }
                } else if (record.getRecordType().equals(HemfRecordType.exttextoutw)) {
                    HemfText.ExtTextOutW extTextOutW = (HemfText.ExtTextOutW) record;
                    if (lastY > -1 && lastY != extTextOutW.getY()) {
                        xhtml.startElement("p");
                        xhtml.characters(buffer.toString());
                        xhtml.endElement("p");
                        buffer.setLength(0);
                        lastX = -1;
                    }
                    if (lastX > -1 && extTextOutW.getX() - lastX > fudgeFactorX) {
                        buffer.append(" ");
                    }
                    String txt = extTextOutW.getText();
                    buffer.append(txt);
                    lastY = extTextOutW.getY();
                    lastX = extTextOutW.getX();
                }
            }
            if (buffer.length() > 0) {
                xhtml.startElement("p");
                xhtml.characters(buffer.toString());
                xhtml.endElement("p");
            }
        } catch (RecordFormatException e) { //POI's hemfparser can throw these for "parse exceptions"
            throw new TikaException(e.getMessage(), e);
        } catch (RuntimeException e) { //convert Runtime to RecordFormatExceptions
            throw new TikaException(e.getMessage(), e);
        }
        xhtml.endDocument();
    }

    private void handleWMF(HemfCommentPublic.WindowsMetafile comment, ContentHandler contentHandler,
                           EmbeddedDocumentExtractor embeddedDocumentExtractor) throws IOException, SAXException, TikaException {
        Metadata embeddedMetadata = new Metadata();
        embeddedMetadata.set(Metadata.CONTENT_TYPE, WMF_MEDIA_TYPE.toString());
        if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
            try (InputStream is = TikaInputStream.get(comment.getWmfInputStream())) {
                embeddedDocumentExtractor.parseEmbedded(is,
                        new EmbeddedContentHandler(contentHandler), embeddedMetadata, false);

            }

        }

    }

    private void handleMultiFormats(HemfCommentPublic.MultiFormats comment, ContentHandler handler,
                                    EmbeddedDocumentExtractor embeddedDocumentExtractor) throws IOException, TikaException, SAXException {
        for (HemfCommentPublic.HemfMultiFormatsData data :
                ((HemfCommentPublic.MultiFormats) comment).getData()) {
            handleEmbedded(data.getData(), embeddedDocumentExtractor, handler);
        }
    }

    private static void handleEmbedded(byte[] data,
                                       EmbeddedDocumentExtractor embeddedDocumentExtractor,
                                       ContentHandler handler) throws TikaException, SAXException {
        try (InputStream is = TikaInputStream.get(data)) {
            Metadata embeddedMetadata = new Metadata();
            if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
                embeddedDocumentExtractor.parseEmbedded(is,
                        new EmbeddedContentHandler(handler), embeddedMetadata, false);
            }
        } catch (IOException e) {

        }
    }
}
