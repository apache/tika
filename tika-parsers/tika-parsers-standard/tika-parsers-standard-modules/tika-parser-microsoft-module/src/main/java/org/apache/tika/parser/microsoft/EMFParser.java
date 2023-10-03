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

package org.apache.tika.parser.microsoft;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.poi.hemf.record.emf.HemfComment;
import org.apache.poi.hemf.record.emf.HemfRecord;
import org.apache.poi.hemf.record.emf.HemfRecordType;
import org.apache.poi.hemf.record.emf.HemfText;
import org.apache.poi.hemf.usermodel.HemfPicture;
import org.apache.poi.util.RecordFormatException;
import org.apache.poi.util.StringUtil;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

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
public class EMFParser implements Parser {

    public static Property EMF_ICON_ONLY = Property.internalBoolean("emf:iconOnly");
    public static Property EMF_ICON_STRING = Property.internalText("emf:iconString");

    private static String ICON_ONLY = "IconOnly";

    private static final MediaType MEDIA_TYPE = MediaType.image("emf");
    private static final MediaType WMF_MEDIA_TYPE = MediaType.image("wmf");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MEDIA_TYPE);

    private static void handleEmbedded(byte[] data,
                                       EmbeddedDocumentExtractor embeddedDocumentExtractor,
                                       ContentHandler handler) throws TikaException, SAXException {
        try (InputStream is = TikaInputStream.get(data)) {
            Metadata embeddedMetadata = new Metadata();
            if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
                embeddedDocumentExtractor
                        .parseEmbedded(is, new EmbeddedContentHandler(handler), embeddedMetadata,
                                true);
            }
        } catch (IOException e) {
            //swallow
        }
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor embeddedDocumentExtractor = null;
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try {
            HemfPicture ex = new HemfPicture(stream);
            ParseState parseState = new ParseState();
            long fudgeFactorX = 1000;//derive this from the font or frame/bounds information
            StringBuilder buffer = new StringBuilder();
            //iterate through the records.  if you hit IconOnly in a comment
            //and it is the first IconOnly, grab the string in the next comment record
            //and that'll be the full name of the file.
            for (HemfRecord record : ex) {
                parseState.isIconOnly = false;
                if (record.getEmfRecordType() == HemfRecordType.comment) {
                    handleCommentData(
                            ((HemfComment.EmfComment) record).getCommentData(), parseState, xhtml, context);
                } else if (record.getEmfRecordType().equals(HemfRecordType.extTextOutW)) {
                    HemfText.EmfExtTextOutW extTextOutW = (HemfText.EmfExtTextOutW) record;
                    //change equality to delta diff;

                    if (parseState.lastY > -1 &&
                            parseState.lastY != extTextOutW.getReference().getY()) {
                        xhtml.startElement("p");
                        xhtml.characters(buffer.toString());
                        xhtml.endElement("p");
                        buffer.setLength(0);
                        parseState.lastX = -1;
                    }
                    if (parseState.lastX > -1 && extTextOutW.getReference().getX() -
                            parseState.lastX > fudgeFactorX) {
                        buffer.append(" ");
                    }
                    String txt = extTextOutW.getText();
                    buffer.append(txt);
                    parseState.lastY = extTextOutW.getReference().getY();
                    parseState.lastX = extTextOutW.getReference().getX();
                }
                if (parseState.isIconOnly) {
                    parseState.lastWasIconOnly = true;
                } else {
                    parseState.lastWasIconOnly = false;
                }
            }
            if (parseState.iconOnlyString != null) {
                metadata.set(EMF_ICON_ONLY, true);
                metadata.set(EMF_ICON_STRING, parseState.iconOnlyString);
            }
            if (buffer.length() > 0) {
                xhtml.startElement("p");
                xhtml.characters(buffer.toString());
                xhtml.endElement("p");
            }
        } catch (RecordFormatException e) { //POI's hemfparser can throw these for "parse
            // exceptions"
            throw new TikaException(e.getMessage(), e);
        } catch (RuntimeException e) { //convert Runtime to RecordFormatExceptions
            throw new TikaException(e.getMessage(), e);
        }
        xhtml.endDocument();
    }

    private void handleCommentData(
            HemfComment.EmfCommentData commentData, ParseState parseState,
            XHTMLContentHandler xhtml, ParseContext context)
            throws IOException, TikaException, SAXException {

        if (commentData instanceof HemfComment.EmfCommentDataMultiformats) {
            if (parseState.extractor == null) {
                parseState.extractor =
                        EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
            }
            handleMultiFormats((HemfComment.EmfCommentDataMultiformats) commentData,
                    xhtml, parseState.extractor);
        } else if (commentData instanceof HemfComment.EmfCommentDataWMF) {
            if (parseState.extractor == null) {
                parseState.extractor =
                        EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
            }
            handleWMF(((HemfComment.EmfCommentDataWMF) commentData).getWMFData(), xhtml,
                    parseState.extractor);
        } else if (commentData instanceof HemfComment.EmfCommentDataGeneric) {
            String val =
                    tryToReadAsString((((HemfComment.EmfCommentDataGeneric) commentData).getPrivateData()));
            if (ICON_ONLY.equals(val) && parseState.hitIconOnly == false) {
                parseState.hitIconOnly = true;
                parseState.isIconOnly = true;
            } else if (parseState.lastWasIconOnly && parseState.iconOnlyString == null) {
                parseState.iconOnlyString = val;
            }
        }
    }

    private String tryToReadAsString(byte[] bytes) {
        if (bytes.length < 2) {
            return null;
        }
        //act like this is a null terminated unicode le
        int stringLen = (bytes.length - 2) / 2;
        try {
            return StringUtil.getFromUnicodeLE0Terminated(bytes, 0, stringLen);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //didn't work out...oh, well
        }
        return null;
    }

    private void handleWMF(byte[] bytes, ContentHandler contentHandler,
                           EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException, TikaException {
        Metadata embeddedMetadata = new Metadata();
        embeddedMetadata.set(Metadata.CONTENT_TYPE, WMF_MEDIA_TYPE.toString());
        if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
            try (InputStream is = TikaInputStream.get(bytes)) {
                embeddedDocumentExtractor
                        .parseEmbedded(is, new EmbeddedContentHandler(contentHandler),
                                embeddedMetadata, true);

            }

        }

    }

    private void handleMultiFormats(HemfComment.EmfCommentDataMultiformats commentData,
                                    ContentHandler handler,
                                    EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, TikaException, SAXException {

        for (HemfComment.EmfCommentDataFormat dataFormat : commentData.getFormats()) {
            //is this right?!
            handleEmbedded(dataFormat.getRawData(), embeddedDocumentExtractor, handler);
        }
    }

    private static class ParseState {
        double lastY = -1;
        double lastX = -1;
        boolean hitIconOnly = false;
        boolean lastWasIconOnly = false;
        boolean isIconOnly = false;
        String iconOnlyString = null;

        EmbeddedDocumentExtractor extractor;
    }
}
