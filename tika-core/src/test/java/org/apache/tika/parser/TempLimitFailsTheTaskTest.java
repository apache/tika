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
    private static final MediaType SWALLOWS = MediaType.application("x-tika-test-swallows");
    private static final MediaType CONTAINER = MediaType.application("x-tika-test-container");

    // spools its input, as a parser that needs random access does
    private static class SpoolingParser implements Parser {
        private final boolean swallow;
        private final AtomicInteger parsed = new AtomicInteger();

        SpoolingParser(boolean swallow) {
            this.swallow = swallow;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Set.of(swallow ? SWALLOWS : SPOOLS);
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException {
            parsed.incrementAndGet();
            try {
                tis.getPath();
            } catch (IOException e) {
                if (!swallow) {
                    throw e;
                }
            }
        }
    }

    // two embedded streams; the first is over the budget
    private static class ContainerParser implements Parser {
        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Set.of(CONTAINER);
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            for (int size : new int[]{3 * MB, 1024}) {
                try (TikaInputStream entry =
                        TikaInputStream.get(new ByteArrayInputStream(new byte[size]))) {
                    EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context)
                            .parseEmbedded(entry, handler, new Metadata(), context, false);
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

    @Test
    public void testTopLevelSpoolIsBudgetedAndNotWrapped() {
        AutoDetectParser parser = new AutoDetectParser(new SpoolingParser(false));
        assertThrows(TempLimitReachedException.class,
                () -> parse(parser, SPOOLS, contextWithBudget()));
    }

    @Test
    public void testSwallowedLimitStillFailsTheParse() {
        AutoDetectParser parser = new AutoDetectParser(new SpoolingParser(true));
        assertThrows(TempLimitReachedException.class,
                () -> parse(parser, SWALLOWS, contextWithBudget()));
    }

    @Test
    public void testEmbeddedLimitEndsTheContainer() {
        SpoolingParser embedded = new SpoolingParser(false);
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
