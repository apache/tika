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
package org.apache.tika.parser.http;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.netpreserve.jwarc.LengthedBody;
import org.netpreserve.jwarc.MessageBody;
import org.netpreserve.jwarc.MessageHeaders;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

public class HttpParser extends AbstractParser {

    private static final MediaType MEDIA_TYPE = MediaType.application("x-httpresponse");
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MEDIA_TYPE);
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        org.netpreserve.jwarc.HttpParser parser = new org.netpreserve.jwarc.HttpParser();
        parser.lenientRequest();
        parser.lenientResponse();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try (ReadableByteChannel channel =
                     Channels.newChannel(CloseShieldInputStream.wrap(stream))) {

            int len = channel.read(buffer);
            buffer.flip();
            if (len < 0) {
                throw new EOFException();
            }
            parser.parse(channel, buffer);

            MessageHeaders messageHeaders = parser.headers();
            updateMetadata(messageHeaders, metadata);
            //check for ok status before continuing?
            long contentLength =
                    messageHeaders.sole("Content-Length").map(Long::parseLong).orElse(0L);
            //is there a way to handle non-lengthed bodies?
            if (contentLength > 0) {
                MessageBody messageBody = LengthedBody.create(channel, buffer, contentLength);
                Metadata payloadMetadata = new Metadata();
                try (InputStream messageStream = messageBody.stream()) {
                    parsePayload(messageStream, xhtml, payloadMetadata, context);
                }
            }
        } finally {
            xhtml.endDocument();
        }
    }

    private void parsePayload(InputStream stream, ContentHandler handler, Metadata metadata,
                        ParseContext context) throws IOException, SAXException {
        EmbeddedDocumentExtractor ex = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (ex.shouldParseEmbedded(metadata)) {
            ex.parseEmbedded(stream, handler, metadata, true);
        }
    }

    private void updateMetadata(MessageHeaders messageHeaders, Metadata metadata) {
        //TODO
        //metadata.set(HttpHeaders.CONTENT_LENGTH, messageHeaders.)
    }
}
