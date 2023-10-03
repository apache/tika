/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.warc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.netpreserve.jwarc.HttpResponse;
import org.netpreserve.jwarc.WarcPayload;
import org.netpreserve.jwarc.WarcReader;
import org.netpreserve.jwarc.WarcRecord;
import org.netpreserve.jwarc.WarcResponse;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.WARC;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

public class WARCParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MediaType.application("warc"),
                    MediaType.application("warc+gz"))));

    public static String WARC_PREFIX = "warc:";
    public static String WARC_HTTP_PREFIX = WARC_PREFIX + "http:";

    public static String WARC_HTTP_STATUS = WARC_HTTP_PREFIX + "status";

    public static String WARC_HTTP_STATUS_REASON = WARC_HTTP_PREFIX + "status:reason";

    private static String RESPONSE = "response";
    private static String WARCINFO = "warcinfo";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        EmbeddedDocumentExtractor embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        try (WarcReader warcreader = new WarcReader(stream)) {
            //TODO: record warnings in metadata: warcreader.onWarning();
            for (WarcRecord record : warcreader) {
                processRecord(record, xhtml, metadata, context, embeddedDocumentExtractor);
            }
        } finally {
            xhtml.endDocument();
        }
    }

    private void processRecord(WarcRecord record, XHTMLContentHandler xhtml, Metadata metadata,
                               ParseContext context,
                               EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws SAXException {
        if (RESPONSE.equals(record.type())) {
            try {
                processResponse((WarcResponse) record, xhtml, context, embeddedDocumentExtractor);
            } catch (IOException | TikaException e) {
                EmbeddedDocumentUtil.recordException(e, metadata);
            } catch (SAXException e) {
                if (WriteLimitReachedException.isWriteLimitReached(e)) {
                    throw e;
                } else {
                    EmbeddedDocumentUtil.recordException(e, metadata);
                }
            }
        } else if (WARCINFO.equals(record.type())) {
            processWarcInfo(record, xhtml, context);
        }
        //TODO - other warc record types

    }

    private void processWarcInfo(WarcRecord record, XHTMLContentHandler xhtml,
                                 ParseContext context) {
        //NO-OP for now
    }

    private void processResponse(WarcResponse warcResponse, XHTMLContentHandler xhtml,
                                 ParseContext context,
                                 EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException, TikaException {
        Optional<WarcPayload> optionalPayload = warcResponse.payload();
        if (!optionalPayload.isPresent()) {
            //TODO handle missing payload?  Report or ignore?
            return;
        }
        Metadata metadata = new Metadata();
        setNotNull(WARC.WARC_RECORD_CONTENT_TYPE, warcResponse.contentType(), metadata);
        setNotNull(WARC.WARC_PAYLOAD_CONTENT_TYPE, warcResponse.payloadType(), metadata);
        processWarcMetadata(warcResponse, metadata);
        processHttpResponseMetadata(warcResponse.http(), metadata);

        String id = warcResponse.id().toString();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, id);
        WarcPayload payload = optionalPayload.get();
        metadata.set(WARC.WARC_RECORD_CONTENT_TYPE, payload.type().toString());
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(payload.body().size()));

        if (embeddedDocumentExtractor.shouldParseEmbedded(metadata)) {
            //TODO check Content-Encoding on the warcResponse.http.headers and wrap the stream.
            //May need to sniff first few bytes to confirm accuracy, e.g. gzip compression ?
            try (InputStream tis = TikaInputStream.get(payload.body().stream())) {
                embeddedDocumentExtractor.parseEmbedded(tis, xhtml, metadata, true);
            }
        }

    }

    private void processWarcMetadata(WarcResponse warcResponse, Metadata metadata) {
        for (Map.Entry<String, List<String>> e : warcResponse.headers().map().entrySet()) {
            for (String val : e.getValue()) {
                metadata.add(WARC_PREFIX + e.getKey(), val);
            }
        }
    }

    private void processHttpResponseMetadata(HttpResponse http, Metadata metadata) {
        metadata.set(WARC_HTTP_STATUS, Integer.toString(http.status()));
        if (!StringUtils.isBlank(http.reason())) {
            metadata.set(WARC_HTTP_STATUS_REASON, http.reason());
        }
        for (Map.Entry<String, List<String>> e : http.headers().map().entrySet()) {
            for (String val : e.getValue()) {
                metadata.add(WARC_HTTP_PREFIX + e.getKey(), val);
            }
        }
    }

    private void setNotNull(Property key, org.netpreserve.jwarc.MediaType contentType,
                            Metadata metadata) {
        if (contentType == null) {
            return;
        }
        metadata.set(key, contentType.toString());
    }
}
