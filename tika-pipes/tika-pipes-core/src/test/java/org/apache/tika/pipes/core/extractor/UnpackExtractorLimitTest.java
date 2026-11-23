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
package org.apache.tika.pipes.core.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.EmbeddedLimitReachedException;
import org.apache.tika.extractor.UnpackHandler;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class UnpackExtractorLimitTest {

    private static final long MAX_UNPACK_BYTES = 10;

    private final List<Integer> stored = new ArrayList<>();
    private final UnpackedByteCount byteCount = new UnpackedByteCount();
    private ParseContext context;

    @BeforeEach
    public void setUp() {
        UnpackHandler unpackHandler = new UnpackHandler() {
            @Override
            public void add(int id, Metadata metadata, InputStream inputStream) throws IOException {
                inputStream.readAllBytes();
                stored.add(id);
            }

            @Override
            public List<Integer> getIds() {
                return stored;
            }

            @Override
            public void close() {
            }
        };
        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setMaxUnpackBytes(MAX_UNPACK_BYTES);
        context = new ParseContext();
        context.set(UnpackHandler.class, unpackHandler);
        context.set(UnpackConfig.class, unpackConfig);
        context.set(UnpackedByteCount.class, byteCount);
        context.set(Parser.class, EmptyParser.INSTANCE);
    }

    private void parseEmbedded(int id, int length, TikaInputStream[] seen) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.EMBEDDED_ID, id);
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(new byte[length]))) {
            seen[0] = tis;
            UnpackExtractor.INSTANCE.parseEmbedded(tis, new DefaultHandler(), metadata, context, false);
        }
    }

    @Test
    public void storesWhatFits() throws Exception {
        parseEmbedded(1, 4, new TikaInputStream[1]);
        parseEmbedded(2, 6, new TikaInputStream[1]);
        assertEquals(List.of(1, 2), stored);
        assertEquals(MAX_UNPACK_BYTES, byteCount.get());
        assertFalse(byteCount.isLimitReached());
    }

    @Test
    public void aFileThatDoesNotFitEndsTheParse() throws Exception {
        parseEmbedded(1, 4, new TikaInputStream[1]);
        EmbeddedLimitReachedException e = assertThrows(EmbeddedLimitReachedException.class,
                () -> parseEmbedded(2, 7, new TikaInputStream[1]));
        assertEquals(EmbeddedLimitReachedException.LimitType.MAX_UNPACK_BYTES, e.getLimitType());
        assertEquals(MAX_UNPACK_BYTES, e.getLimit());
        assertEquals(List.of(1), stored, "never a truncated file");
        assertTrue(byteCount.isLimitReached());
    }

    @Test
    public void staysReachedIfAParserSwallowsIt() throws Exception {
        byteCount.markLimitReached();
        TikaInputStream[] seen = new TikaInputStream[1];
        assertThrows(EmbeddedLimitReachedException.class, () -> parseEmbedded(1, 4, seen));
        assertFalse(seen[0].hasFile(), "spooled a file that will not be handed back");
        assertEquals(List.of(), stored);
    }

    @Test
    public void storesNothingOnceTheLimitIsReached() throws Exception {
        // a nested file trips the limit while this one, which would fit, is being parsed
        context.set(Parser.class, new EmptyParser() {
            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext parseContext) {
                byteCount.markLimitReached();
                throw new EmbeddedLimitReachedException(
                        EmbeddedLimitReachedException.LimitType.MAX_UNPACK_BYTES, MAX_UNPACK_BYTES);
            }
        });
        assertThrows(EmbeddedLimitReachedException.class,
                () -> parseEmbedded(1, 4, new TikaInputStream[1]));
        assertEquals(List.of(), stored);
    }
}
