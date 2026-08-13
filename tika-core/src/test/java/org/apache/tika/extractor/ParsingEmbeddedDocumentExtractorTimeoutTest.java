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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.EmbeddedLimitReachedException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;
import org.apache.tika.parser.Parser;

/**
 * Tests the two ways a recursive parse can run out of time, per the 4.0 timeout
 * redesign: a single embedded document's own operation timing out (recorded, siblings
 * continue) versus the task's total deadline being exhausted (a document-level fact,
 * remaining children skipped cleanly, no exception unless explicitly configured).
 */
public class ParsingEmbeddedDocumentExtractorTimeoutTest {

    private static TikaInputStream tis() throws IOException {
        return TikaInputStream.get(new byte[]{1, 2, 3});
    }

    private static ParseContext contextWithAmpleBudget() {
        ParseContext context = new ParseContext();
        ParseRecord parseRecord = ParseRecord.newInstance(context);
        context.set(ParseRecord.class, parseRecord);
        context.set(ParseTimeout.class, ParseTimeout.start(new TimeoutLimits(60_000, 60_000)));
        return context;
    }

    private static ParseContext contextWithExhaustedBudget() {
        ParseContext context = new ParseContext();
        ParseRecord parseRecord = ParseRecord.newInstance(context);
        context.set(ParseRecord.class, parseRecord);
        // total=0 -> remainingMillis() is already 0 by the time anything checks it
        context.set(ParseTimeout.class, ParseTimeout.start(new TimeoutLimits(0, 0)));
        return context;
    }

    @Test
    public void testChildTimeoutIsRecordedAndSiblingsContinue() throws Exception {
        ParseContext context = contextWithAmpleBudget();
        AtomicInteger calls = new AtomicInteger(0);
        context.set(Parser.class, new Parser() {
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Set.of();
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) throws IOException, SAXException, TikaException {
                if (calls.getAndIncrement() == 0) {
                    throw new TikaTimeoutException("op timed out", 1000, 1000);
                }
                // second call: "succeeds" -- does nothing
            }
        });

        ParsingEmbeddedDocumentExtractor extractor = new ParsingEmbeddedDocumentExtractor(context);
        ContentHandler handler = new DefaultHandler();

        // First embedded document: its own operation times out.
        extractor.parseEmbedded(tis(), handler, new Metadata(), context, false);

        ParseRecord parseRecord = context.get(ParseRecord.class);
        assertEquals(1, parseRecord.getExceptions().size(),
                "the child timeout should be recorded as an exception");
        assertTrue(parseRecord.getExceptions().get(0) instanceof TikaTimeoutException);
        assertFalse(parseRecord.isTaskDeadlineReached(),
                "a single child timing out is not a task-deadline event");

        // Second embedded document (sibling): must still be attempted and succeed.
        extractor.parseEmbedded(tis(), handler, new Metadata(), context, false);
        assertEquals(2, calls.get(), "the second sibling must still be parsed");
        assertEquals(1, parseRecord.getExceptions().size(),
                "no new exception should be recorded for the successful sibling");
    }

    @Test
    public void testChildTimeoutMessageReportsRequestedAndGranted() {
        TikaTimeoutException clipped = new TikaTimeoutException("timed out", 5000, 1200);
        assertTrue(clipped.isClippedByRemaining());
        assertTrue(clipped.getMessage().contains("task remaining"));

        TikaTimeoutException exhausted = new TikaTimeoutException("timed out", 5000, 5000);
        assertFalse(exhausted.isClippedByRemaining());
        assertTrue(exhausted.getMessage().contains("budget exhausted"));
    }

    @Test
    public void testDeadlineExhaustedSkipsRemainingChildrenCleanly() throws Exception {
        ParseContext context = contextWithExhaustedBudget();
        context.set(Parser.class, new Parser() {
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Set.of();
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) {
                fail("a child must not be attempted once the task deadline is reached");
            }
        });

        ParsingEmbeddedDocumentExtractor extractor = new ParsingEmbeddedDocumentExtractor(context);
        ContentHandler handler = new DefaultHandler();

        assertFalse(extractor.shouldParseEmbedded(new Metadata()));

        ParseRecord parseRecord = context.get(ParseRecord.class);
        assertTrue(parseRecord.isTaskDeadlineReached());

        // parseEmbedded enforces the limit even if the caller skipped shouldParseEmbedded,
        // and must not throw by default -- it returns having done nothing.
        extractor.parseEmbedded(tis(), handler, new Metadata(), context, false);
        // A second sibling: still skipped, same as a hard count limit.
        extractor.parseEmbedded(tis(), handler, new Metadata(), context, false);

        assertTrue(parseRecord.getExceptions().isEmpty(),
                "skipping for deadline is not itself recorded as an exception");
    }

    @Test
    public void testThrowOnDeadlineThrowsEmbeddedLimitReachedException() {
        ParseContext context = contextWithExhaustedBudget();
        context.get(ParseRecord.class).setThrowOnDeadline(true);

        ParsingEmbeddedDocumentExtractor extractor = new ParsingEmbeddedDocumentExtractor(context);

        EmbeddedLimitReachedException ex = assertThrows(EmbeddedLimitReachedException.class,
                () -> extractor.shouldParseEmbedded(new Metadata()));
        assertEquals(EmbeddedLimitReachedException.LimitType.DEADLINE, ex.getLimitType());
    }

    /**
     * TIKA-4813 follow-up: the deadline flag is only set to true once (by whichever
     * embedded doc discovers it first). throwOnDeadline previously checked that flag via
     * an early-return that skipped straight to "return false" -- meaning only the very
     * first sibling to notice the deadline actually threw; every later sibling silently
     * took the skip path instead, even with throwOnDeadline configured.
     */
    @Test
    public void testThrowOnDeadlineThrowsForEverySiblingOnceLimitHit() {
        ParseContext context = contextWithExhaustedBudget();
        context.get(ParseRecord.class).setThrowOnDeadline(true);
        ParsingEmbeddedDocumentExtractor extractor = new ParsingEmbeddedDocumentExtractor(context);

        EmbeddedLimitReachedException first = assertThrows(EmbeddedLimitReachedException.class,
                () -> extractor.shouldParseEmbedded(new Metadata()));
        assertEquals(EmbeddedLimitReachedException.LimitType.DEADLINE, first.getLimitType());

        EmbeddedLimitReachedException second = assertThrows(EmbeddedLimitReachedException.class,
                () -> extractor.shouldParseEmbedded(new Metadata()),
                "a second sibling must also throw, not silently skip just because a " +
                        "different embedded doc already recorded the deadline");
        assertEquals(EmbeddedLimitReachedException.LimitType.DEADLINE, second.getLimitType());
    }

    /**
     * TIKA-4813 follow-up: identical bug to the deadline case above, in the MAX_COUNT
     * branch -- isEmbeddedCountLimitReached()'s early-return skipped throwOnMaxCount for
     * every sibling after the first.
     */
    @Test
    public void testThrowOnMaxCountThrowsForEverySiblingOnceLimitHit() {
        ParseContext context = new ParseContext();
        context.set(EmbeddedLimits.class, new EmbeddedLimits(-1, false, 0, true));
        ParseRecord parseRecord = ParseRecord.newInstance(context);
        context.set(ParseRecord.class, parseRecord);
        context.set(ParseTimeout.class, ParseTimeout.start(new TimeoutLimits(60_000, 60_000)));

        ParsingEmbeddedDocumentExtractor extractor = new ParsingEmbeddedDocumentExtractor(context);

        EmbeddedLimitReachedException first = assertThrows(EmbeddedLimitReachedException.class,
                () -> extractor.shouldParseEmbedded(new Metadata()));
        assertEquals(EmbeddedLimitReachedException.LimitType.MAX_COUNT, first.getLimitType());

        EmbeddedLimitReachedException second = assertThrows(EmbeddedLimitReachedException.class,
                () -> extractor.shouldParseEmbedded(new Metadata()),
                "a second sibling must also throw once the count limit is hit, not " +
                        "silently skip just because a different embedded doc noticed first");
        assertEquals(EmbeddedLimitReachedException.LimitType.MAX_COUNT, second.getLimitType());
    }

    /**
     * TIKA-4813 follow-up: EmbeddedLimitReachedException (a RuntimeException) is thrown
     * from inside a container parser's own parse() call, which runs inside
     * CompositeParser.parse()'s try block. That block's catch(RuntimeException) used to
     * unconditionally wrap every RuntimeException into a checked TikaException --
     * indistinguishable, to any enclosing catch(TikaException) (e.g. a shallower
     * embedded-document extractor recording ordinary per-document failures), from a
     * ordinary parse failure that's supposed to be recorded and skipped past. That meant
     * throwOnDeadline's exception could get silently swallowed one level up instead of
     * reaching the caller as the hard failure it's supposed to be. Proven here at the
     * minimal layer where the wrapping actually happens: one CompositeParser.parse() call
     * whose delegate parser triggers the throw.
     */
    @Test
    public void testThrowOnDeadlinePropagatesThroughCompositeParserUnwrapped() throws Exception {
        // Deliberately built from TimeoutLimits rather than contextWithExhaustedBudget()'s
        // helper: CompositeParser.parse() is the entry point here, so it must be the one
        // to install ParseRecord/ParseTimeout from context, exactly as a real top-level
        // parse would.
        ParseContext context = new ParseContext();
        TimeoutLimits limits = new TimeoutLimits(0, 0);
        limits.setThrowOnDeadline(true);
        context.set(TimeoutLimits.class, limits);

        Parser containerParser = new Parser() {
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.TEXT_PLAIN);
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) throws TikaException {
                // Mirrors what a real container parser (ZipParser, MockParser, etc.)
                // does for each entry it finds.
                new ParsingEmbeddedDocumentExtractor(context).shouldParseEmbedded(new Metadata());
            }
        };

        CompositeParser composite = new CompositeParser(MediaTypeRegistry.getDefaultRegistry(), containerParser);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());

        EmbeddedLimitReachedException ex = assertThrows(EmbeddedLimitReachedException.class,
                () -> composite.parse(tis(), new DefaultHandler(), metadata, context),
                "must reach the caller as the original EmbeddedLimitReachedException, not " +
                        "get wrapped into a generic TikaException that an enclosing handler " +
                        "could mistake for an ordinary recorded failure");
        assertEquals(EmbeddedLimitReachedException.LimitType.DEADLINE, ex.getLimitType());
    }
}
