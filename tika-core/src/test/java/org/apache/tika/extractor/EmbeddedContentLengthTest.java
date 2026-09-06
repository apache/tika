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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * A parser builds the metadata of an embedded document beside the stream it
 * hands over, so a length the stream knows was missing from what a client
 * sees (TIKA-4873).
 */
public class EmbeddedContentLengthTest {

    private static final byte[] CONTENT = "0123456789".getBytes(UTF_8);

    @Test
    public void testTheLengthComesFromTheStream() throws Exception {
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(CONTENT)) {
            EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context)
                    .parseEmbedded(tis, new BodyContentHandler(), metadata, context, false);
        }
        assertEquals("10", metadata.get(HttpHeaders.CONTENT_LENGTH));
    }

    /**
     * What the parser says stands: it describes the item, which need not be
     * the whole of the stream it happens to hand over.
     */
    @Test
    public void testADeclaredLengthIsKept() throws Exception {
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_LENGTH, "7");
        try (TikaInputStream tis = TikaInputStream.get(CONTENT)) {
            EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context)
                    .parseEmbedded(tis, new BodyContentHandler(), metadata, context, false);
        }
        assertEquals("7", metadata.get(HttpHeaders.CONTENT_LENGTH));
    }

    /**
     * A stream that would have to be read to be measured is left alone: the
     * length is worth less than a copy of the content to find it.
     */
    @Test
    public void testAStreamOfUnknownLengthIsNotMeasured() throws Exception {
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        try (InputStream bare = new java.io.ByteArrayInputStream(CONTENT);
                TikaInputStream tis = TikaInputStream.get(bare)) {
            EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context)
                    .parseEmbedded(tis, new BodyContentHandler(), metadata, context, false);
        }
        assertNull(metadata.get(HttpHeaders.CONTENT_LENGTH));
    }
}
