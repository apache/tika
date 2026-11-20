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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TempLimitReachedException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TempBytesBudget;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

/**
 * Reaching the temp limit fails the task: the exception reaches the top-level caller
 * unwrapped, from wherever the stream entered the parse.
 */
public class TempLimitFailsTheTaskTest {

    private static final int MB = 1024 * 1024;
    private static final MediaType SPOOLS = MediaType.application("x-tika-test-spools");
    private static final MediaType CONTAINER = MediaType.application("x-tika-test-container");

    // spools its input, as a parser that needs random access does
    private enum OnLimit { THROW, SWALLOW, WRAP_TIKA, WRAP_SAX }

    private static class SpoolingParser implements Parser {
        private final OnLimit onLimit;
        private final AtomicInteger parsed = new AtomicInteger();

        SpoolingParser(OnLimit onLimit) {
            this.onLimit = onLimit;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Set.of(SPOOLS);
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            parsed.incrementAndGet();
            try {
                tis.getPath();
            } catch (RuntimeException e) {
                switch (onLimit) {
                    case SWALLOW:
                        return;
                    case WRAP_TIKA:
                        throw new TikaException("wrapped", e);
                    case WRAP_SAX:
                        throw new SAXException("wrapped", e);
                    default:
                        throw e;
                }
            }
        }
    }

    // two embedded streams; the first is over the budget, and a failed entry is skipped
    private static class ContainerParser implements Parser {
        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Set.of(CONTAINER);
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException {
            for (int size : new int[]{3 * MB, 1024}) {
                try (TikaInputStream entry =
                        TikaInputStream.get(new ByteArrayInputStream(new byte[size]))) {
                    EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context)
                            .parseEmbedded(entry, handler, new Metadata(), context, false);
                } catch (Exception e) {
                    //next entry
                }
            }
        }
    }

    private static ParseContext contextWithBudget() {
        ParseContext context = new ParseContext();
        context.set(TempBytesBudget.class, new TempBytesBudget(MB));
        return context;
    }

    private static void parse(Parser parser, MediaType type, ParseContext context)
            throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE, type.toString());
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(new byte[3 * MB]))) {
            parser.parse(tis, new DefaultHandler(), metadata, context);
        }
    }

    @ParameterizedTest
    @EnumSource(OnLimit.class)
    public void testLimitReachesTheCallerAsItself(OnLimit onLimit) {
        AutoDetectParser parser = new AutoDetectParser(new SpoolingParser(onLimit));
        assertThrows(TempLimitReachedException.class,
                () -> parse(parser, SPOOLS, contextWithBudget()));
    }

    @Test
    public void testEmbeddedLimitEndsTheContainer() {
        SpoolingParser embedded = new SpoolingParser(OnLimit.THROW);
        ParseContext context = contextWithBudget();
        // not an AutoDetectParser: only the extractor can put the entry under the budget
        context.set(Parser.class, embedded);
        AutoDetectParser parser = new AutoDetectParser(new ContainerParser());
        assertThrows(TempLimitReachedException.class, () -> parse(parser, CONTAINER, context));
        assertEquals(1, embedded.parsed.get(), "the entry after the limit was parsed");
    }

    @Test
    public void testDetectionIsBudgeted() throws Exception {
        Detector spooling = (tis, metadata, context) -> {
            tis.getPath();
            return MediaType.OCTET_STREAM;
        };
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(new byte[3 * MB]))) {
            assertThrows(TempLimitReachedException.class,
                    () -> new CompositeDetector(spooling).detect(tis, new Metadata(), contextWithBudget()));
        }
    }
}
