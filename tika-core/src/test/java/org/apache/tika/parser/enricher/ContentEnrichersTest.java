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
package org.apache.tika.parser.enricher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;

public class ContentEnrichersTest {

    private static final MediaType PNG = MediaType.image("png");
    private static final MediaType OCR_PNG = MediaType.image("ocr-png");

    private static class RecordingParser implements Parser {
        private static final long serialVersionUID = 1L;
        private final Set<MediaType> types;
        int calls = 0;
        String overrideSeenDuringParse;

        RecordingParser(Set<MediaType> types) {
            this.types = types;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return types;
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) {
            calls++;
            overrideSeenDuringParse =
                    metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE);
        }
    }

    private static class RecognizingParser extends RecordingParser implements TextRecognizer {
        private static final long serialVersionUID = 1L;
        private final boolean recognizes;

        RecognizingParser(Set<MediaType> types, boolean recognizes) {
            super(types);
            this.recognizes = recognizes;
        }

        @Override
        public boolean recognizesText(ParseContext context) {
            return recognizes;
        }
    }

    private static class AnnotatingParser extends RecordingParser implements ContentEnricher {
        private static final long serialVersionUID = 1L;

        AnnotatingParser(Set<MediaType> types) {
            super(types);
        }
    }

    private static CompositeContentEnricher listOf(Parser... enrichers) {
        return new CompositeContentEnricher(List.of(enrichers));
    }

    private static CompositeParser compositeOf(Parser... parsers) {
        return new CompositeParser(MediaTypeRegistry.getDefaultRegistry(), parsers);
    }

    @Test
    public void testHasTextRecognizer() {
        ParseContext context = new ParseContext();
        RecordingParser annotator = new RecordingParser(Collections.singleton(PNG));
        assertFalse(ContentEnrichers.hasTextRecognizer(listOf(annotator), PNG, context),
                "an enricher that does not declare the capability is not a recognizer");
        assertTrue(ContentEnrichers.hasTextRecognizer(
                listOf(annotator, new RecognizingParser(Collections.singleton(PNG), true)),
                PNG, context));
        assertFalse(ContentEnrichers.hasTextRecognizer(
                listOf(new RecognizingParser(Collections.singleton(PNG), false)), PNG, context),
                "a recognizer that declines for this parse does not count");
        assertFalse(ContentEnrichers.hasTextRecognizer(
                listOf(new RecognizingParser(Collections.singleton(PNG), true)),
                MediaType.image("tiff"), context));
        assertFalse(ContentEnrichers.hasTextRecognizer(null, PNG, context));
        assertFalse(ContentEnrichers.hasTextRecognizer(null, null, context));
        // no list: found by interface
        context.set(Parser.class, compositeOf(new RecognizingParser(Set.of(PNG), true)));
        assertTrue(ContentEnrichers.hasTextRecognizer(null, PNG, context));
        assertFalse(ContentEnrichers.hasTextRecognizer(null, MediaType.image("tiff"), context));
        context.set(Parser.class, compositeOf(new RecognizingParser(Set.of(PNG), false)));
        assertFalse(ContentEnrichers.hasTextRecognizer(null, PNG, context),
                "a discovered recognizer that declines for this parse does not count");
        context.set(Parser.class, compositeOf(new AnnotatingParser(Set.of(PNG))));
        assertFalse(ContentEnrichers.hasTextRecognizer(null, PNG, context),
                "a discovered annotator is not a recognizer");
        // a legacy image/ocr-* claimant counts as a recognizer
        context.set(Parser.class, compositeOf(new RecordingParser(Set.of(OCR_PNG))));
        assertTrue(ContentEnrichers.hasTextRecognizer(null, PNG, context));
        // a list is authoritative
        assertFalse(ContentEnrichers.hasTextRecognizer(listOf(annotator), PNG, context));
    }

    @Test
    public void testHasTextRecognizerRefusedDuringEnrichment() throws Exception {
        ParseContext context = new ParseContext();
        Parser reentrant = new Parser() {
            private static final long serialVersionUID = 1L;

            @Override
            public Set<MediaType> getSupportedTypes(ParseContext ctx) {
                return Collections.singleton(PNG);
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext ctx) {
                metadata.set("nested-recognizer", ContentEnrichers.hasTextRecognizer(
                        ctx.get(CompositeContentEnricher.class), PNG, ctx) ? "yes" : "no");
            }
        };
        CompositeContentEnricher enrichers = listOf(
                reentrant, new RecognizingParser(Collections.singleton(PNG), true));
        context.set(CompositeContentEnricher.class, enrichers);
        assertTrue(ContentEnrichers.hasTextRecognizer(enrichers, PNG, context));
        Metadata metadata = new Metadata();
        invoke(ContentEnrichers.get(enrichers, PNG, context), metadata, context);
        assertEquals("no", metadata.get("nested-recognizer"));
    }

    private static void invoke(Parser enricher, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            enricher.parse(tis, new DefaultHandler(), metadata, context);
        }
    }

    @Test
    public void testExplicitListWinsOverDiscovered() throws Exception {
        RecordingParser explicit = new RecordingParser(Collections.singleton(PNG));
        RecognizingParser discovered = new RecognizingParser(Collections.singleton(PNG), true);
        CompositeContentEnricher enrichers =
                new CompositeContentEnricher(List.of(explicit));
        ParseContext context = new ParseContext();
        context.set(Parser.class, compositeOf(discovered));

        Parser enricher = ContentEnrichers.get(enrichers, PNG, context);
        assertNotNull(enricher);
        invoke(enricher, new Metadata(), context);
        assertEquals(1, explicit.calls);
        assertEquals(0, discovered.calls);
        assertNull(explicit.overrideSeenDuringParse);
    }

    /** No list: invoked directly on the real type. */
    @Test
    public void testDiscoveredRecognizerInvokedDirectly() throws Exception {
        RecognizingParser engine = new RecognizingParser(Collections.singleton(PNG), true);
        ParseContext context = new ParseContext();
        context.set(Parser.class, compositeOf(new RecordingParser(Set.of(PNG)), engine));

        Parser enricher = ContentEnrichers.get(null, PNG, context);
        assertNotNull(enricher);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, PNG.toString());
        invoke(enricher, metadata, context);
        assertEquals(1, engine.calls);
        assertNull(engine.overrideSeenDuringParse);
        assertNull(metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE));
        assertEquals(PNG.toString(), metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(RecognizingParser.class.getName(),
                metadata.get(TikaCoreProperties.TIKA_PARSED_BY));
        // a plain parser is dispatch, not enrichment
        context.set(Parser.class, compositeOf(new RecordingParser(Set.of(PNG))));
        assertNull(ContentEnrichers.get(null, PNG, context));
    }

    /**
     * No list: an engine under "parsers" beats a discovered one, the last claimant wins
     * within a tier, and a _mime-exclude on the entry applies.
     */
    @Test
    public void testDiscoveryPrecedence() throws Exception {
        RecognizingParser spiFirst = new RecognizingParser(Set.of(PNG), true);
        RecognizingParser spiLast = new RecognizingParser(Set.of(PNG), true);
        DefaultParser defaults = new DefaultParser(MediaTypeRegistry.getDefaultRegistry(),
                new ServiceLoader(new ClassLoader(null) { })) {
            private static final long serialVersionUID = 1L;

            @Override
            public List<Parser> getAllComponentParsers() {
                return List.of(spiFirst, spiLast);
            }
        };
        ParseContext context = new ParseContext();
        context.set(Parser.class, compositeOf(defaults));
        invoke(ContentEnrichers.get(null, PNG, context), new Metadata(), context);
        assertEquals(1, spiLast.calls, "within a tier the last claimant wins");
        assertEquals(0, spiFirst.calls);

        RecognizingParser configured = new RecognizingParser(Set.of(PNG), true);
        context.set(Parser.class, compositeOf(configured, defaults));
        invoke(ContentEnrichers.get(null, PNG, context), new Metadata(), context);
        assertEquals(1, configured.calls, "an engine configured under parsers wins");
        assertEquals(1, spiLast.calls);

        Parser excluded = ParserDecorator.withoutTypes(configured, Set.of(PNG));
        context.set(Parser.class, compositeOf(excluded, defaults));
        invoke(ContentEnrichers.get(null, PNG, context), new Metadata(), context);
        assertEquals(1, configured.calls, "an excluded type falls through to the next tier");
        assertEquals(2, spiLast.calls);
    }

    private static DefaultParser spiDefaults(Parser... found) {
        return new DefaultParser(MediaTypeRegistry.getDefaultRegistry(),
                new ServiceLoader(new ClassLoader(null) { })) {
            private static final long serialVersionUID = 1L;

            @Override
            public List<Parser> getAllComponentParsers() {
                return List.of(found);
            }
        };
    }

    /** A filter on the default-parser entry applies to the engines discovered inside it. */
    @Test
    public void testDefaultParserFilterAppliesToDiscovery() {
        MediaType tiff = MediaType.image("tiff");
        DefaultParser defaults = spiDefaults(new RecognizingParser(Set.of(PNG, tiff), true));
        ParseContext context = new ParseContext();
        context.set(Parser.class, compositeOf(
                ParserDecorator.withMimeFilters(defaults, null, Set.of(PNG))));
        assertNull(ContentEnrichers.get(null, PNG, context), "excluded on default-parser");
        assertNotNull(ContentEnrichers.get(null, tiff, context));
        context.set(Parser.class, compositeOf(
                ParserDecorator.withMimeFilters(defaults, Set.of(tiff), null)));
        assertNull(ContentEnrichers.get(null, PNG, context), "not included on default-parser");
        assertNotNull(ContentEnrichers.get(null, tiff, context));
    }

    /** The capability is asked of the engine, not the decorator around it. */
    @Test
    public void testTextRecognizerSeenThroughDecorator() {
        MediaType tiff = MediaType.image("tiff");
        Parser decorated = ParserDecorator.withMimeFilters(
                new RecognizingParser(Set.of(PNG, tiff), true), null, Set.of(tiff));
        ParseContext context = new ParseContext();
        assertTrue(ContentEnrichers.hasTextRecognizer(listOf(decorated), PNG, context));
        assertFalse(ContentEnrichers.hasTextRecognizer(listOf(decorated), tiff, context));
        context.set(Parser.class, compositeOf(decorated));
        assertTrue(ContentEnrichers.hasTextRecognizer(null, PNG, context));
        assertFalse(ContentEnrichers.hasTextRecognizer(null, tiff, context));
    }

    /** A real-type filter on a legacy engine's entry applies in discovery. */
    @Test
    public void testLegacyClaimantHonorsRealTypeFilter() {
        MediaType tiff = MediaType.image("tiff");
        RecordingParser legacy =
                new RecordingParser(Set.of(OCR_PNG, MediaType.image("ocr-tiff")));
        ParseContext context = new ParseContext();
        context.set(Parser.class, compositeOf(
                ParserDecorator.withMimeFilters(legacy, null, Set.of(tiff))));
        assertNull(ContentEnrichers.get(null, tiff, context));
        assertNotNull(ContentEnrichers.get(null, PNG, context));
        context.set(Parser.class, compositeOf(
                ParserDecorator.withMimeFilters(legacy, Set.of(tiff), null)));
        assertNotNull(ContentEnrichers.get(null, tiff, context));
        assertNull(ContentEnrichers.get(null, PNG, context));
    }

    @Test
    public void testExcludeAppliesToLegacyPseudoType() {
        // either spelling of the excluded type must work
        Set<MediaType> legacy = Set.of(OCR_PNG, MediaType.image("ocr-tiff"));
        for (String spelling : new String[]{"image/tiff", "image/ocr-tiff"}) {
            Parser excluded = ParserDecorator.withoutTypes(new RecordingParser(legacy),
                    Collections.singleton(MediaType.parse(spelling)));
            CompositeContentEnricher enrichers =
                    new CompositeContentEnricher(List.of(excluded));
            assertEquals(Set.of(PNG), enrichers.getSupportedTypes(), spelling);
            assertTrue(enrichers.getEnrichers(MediaType.image("tiff")).isEmpty(), spelling);
        }
    }

    /** A legacy image/ocr-* engine is discovered for the real type. */
    @Test
    public void testLegacyClaimantDiscovered() throws Exception {
        RecordingParser legacy = new RecordingParser(Collections.singleton(OCR_PNG));
        ParseContext context = new ParseContext();
        context.set(Parser.class, compositeOf(legacy));

        Parser enricher = ContentEnrichers.get(null, PNG, context);
        assertNotNull(enricher);

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, PNG.toString());
        invoke(enricher, metadata, context);

        assertEquals(1, legacy.calls);
        assertNull(legacy.overrideSeenDuringParse, "nothing is minted any more");
        assertEquals(PNG.toString(), metadata.get(HttpHeaders.CONTENT_TYPE));
        assertNull(ContentEnrichers.get(null, MediaType.image("tiff"), context));
    }

    @Test
    public void testNoneAvailable() {
        ParseContext context = new ParseContext();
        assertNull(ContentEnrichers.get(null, PNG, context));
        // composite that claims nothing
        context.set(Parser.class, compositeOf(new RecognizingParser(Collections.emptySet(), true)));
        assertNull(ContentEnrichers.get(null, PNG, context));
        assertNull(ContentEnrichers.get(null, null, context));
    }

    @Test
    public void testConfiguredListIsAuthoritative() throws Exception {
        RecordingParser explicit = new RecordingParser(Collections.singleton(PNG));
        RecognizingParser discovered =
                new RecognizingParser(Collections.singleton(MediaType.image("tiff")), true);
        CompositeContentEnricher enrichers = new CompositeContentEnricher(List.of(explicit));
        ParseContext context = new ParseContext();
        context.set(Parser.class, compositeOf(discovered));

        assertNull(ContentEnrichers.get(enrichers, MediaType.image("tiff"), context));
        assertNotNull(ContentEnrichers.get(null, MediaType.image("tiff"), context));
    }

    @Test
    public void testParametersIgnoredInMatching() throws Exception {
        RecordingParser explicit = new RecordingParser(Collections.singleton(PNG));
        CompositeContentEnricher enrichers = new CompositeContentEnricher(List.of(explicit));
        ParseContext context = new ParseContext();

        Parser enricher = ContentEnrichers.get(enrichers,
                MediaType.parse("image/png; charset=binary"), context);
        assertNotNull(enricher, "parameterized type must match the base-type registration");
        invoke(enricher, new Metadata(), context);
        assertEquals(1, explicit.calls);
    }

    @Test
    public void testEnricherCannotRewriteContentType() throws Exception {
        Parser rewriting = new Parser() {
            private static final long serialVersionUID = 1L;

            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(PNG);
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) {
                metadata.set(HttpHeaders.CONTENT_TYPE, "application/pdf");
            }
        };
        CompositeContentEnricher enrichers = new CompositeContentEnricher(List.of(rewriting));
        ParseContext context = new ParseContext();

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, PNG.toString());
        invoke(ContentEnrichers.get(enrichers, PNG, context), metadata, context);
        assertEquals(PNG.toString(), metadata.get(HttpHeaders.CONTENT_TYPE));

        Metadata unset = new Metadata();
        invoke(ContentEnrichers.get(enrichers, PNG, context), unset, context);
        assertNull(unset.get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testRuntimeFailureAbortsChainWithEarlierFailureSuppressed() throws Exception {
        List<String> order = new java.util.ArrayList<>();
        Parser failing = namedEnricher("failing", order, true);
        Parser blowingUp = new Parser() {
            private static final long serialVersionUID = 1L;

            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(PNG);
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) {
                order.add("blowingUp");
                throw new NullPointerException("boom");
            }
        };
        Parser third = namedEnricher("third", order, false);
        CompositeContentEnricher enrichers =
                new CompositeContentEnricher(List.of(failing, blowingUp, third));
        ParseContext context = new ParseContext();

        Parser enricher = ContentEnrichers.get(enrichers, PNG, context);
        assertNotNull(enricher);
        NullPointerException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class, () -> invoke(enricher, new Metadata(), context));
        assertEquals(List.of("failing", "blowingUp"), order);
        // the recorded checked failure rides along instead of vanishing
        assertEquals(1, thrown.getSuppressed().length);
        assertEquals("failing failed", thrown.getSuppressed()[0].getMessage());
    }

    @Test
    public void testAllMatchingEnrichersRunInOrder() throws Exception {
        List<String> order = new java.util.ArrayList<>();
        Parser first = namedEnricher("first", order, false);
        Parser second = namedEnricher("second", order, false);
        CompositeContentEnricher enrichers = new CompositeContentEnricher(List.of(first, second));
        ParseContext context = new ParseContext();

        Parser enricher = ContentEnrichers.get(enrichers, PNG, context);
        assertNotNull(enricher);
        Metadata metadata = new Metadata();
        invoke(enricher, metadata, context);
        assertEquals(List.of("first", "second"), order);
        // both members share one class: recorded once
        assertEquals(List.of(first.getClass().getName()),
                List.of(metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY)),
                "an invoked enricher is recorded as the composite would record it");
    }

    @Test
    public void testChainIsBestEffortAndStillReportsFailure() throws Exception {
        List<String> order = new java.util.ArrayList<>();
        Parser failing = namedEnricher("failing", order, true);
        Parser second = namedEnricher("second", order, false);
        CompositeContentEnricher enrichers = new CompositeContentEnricher(List.of(failing, second));
        ParseContext context = new ParseContext();

        Parser enricher = ContentEnrichers.get(enrichers, PNG, context);
        assertNotNull(enricher);
        TikaException thrown = org.junit.jupiter.api.Assertions.assertThrows(TikaException.class,
                () -> invoke(enricher, new Metadata(), context));
        // the failure did not stop the second enricher, and was still rethrown at the end
        assertEquals(List.of("failing", "second"), order);
        assertEquals("failing failed", thrown.getMessage());
        // the guard is released even when the chain throws
        assertNotNull(ContentEnrichers.get(enrichers, PNG, context));
    }

    @Test
    public void testTimeoutAbortsChainImmediately() throws Exception {
        List<String> order = new java.util.ArrayList<>();
        Parser timingOut = new Parser() {
            private static final long serialVersionUID = 1L;

            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(PNG);
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) throws TikaException {
                order.add("timingOut");
                throw new org.apache.tika.exception.TikaTimeoutException("budget spent", 1, 1);
            }
        };
        Parser second = namedEnricher("second", order, false);
        CompositeContentEnricher enrichers =
                new CompositeContentEnricher(List.of(timingOut, second));
        ParseContext context = new ParseContext();

        Parser enricher = ContentEnrichers.get(enrichers, PNG, context);
        assertNotNull(enricher);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.apache.tika.exception.TikaTimeoutException.class,
                () -> invoke(enricher, new Metadata(), context));
        assertEquals(List.of("timingOut"), order);
    }

    private static Parser namedEnricher(String name, List<String> order, boolean fail) {
        return new Parser() {
            private static final long serialVersionUID = 1L;

            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(PNG);
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) throws TikaException {
                order.add(name);
                if (fail) {
                    throw new TikaException(name + " failed");
                }
            }
        };
    }

    /**
     * A legacy engine's image/ocr-* advertisement must match the real type, and an engine
     * advertising both forms must run once, not twice.
     */
    @Test
    public void testLegacyOcrTypeAdvertisementsMatchRealTypes() throws Exception {
        List<String> order = new java.util.ArrayList<>();
        Parser legacyEngine = new Parser() {
            private static final long serialVersionUID = 1L;

            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Set.of(OCR_PNG, MediaType.image("jp2"), MediaType.image("ocr-jp2"));
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) {
                order.add("legacyEngine");
            }
        };
        CompositeContentEnricher enrichers =
                new CompositeContentEnricher(List.of(legacyEngine));
        ParseContext context = new ParseContext();

        Parser forPng = ContentEnrichers.get(enrichers, PNG, context);
        assertNotNull(forPng, "ocr-png advertisement must be nameable for image/png");
        invoke(forPng, new Metadata(), context);
        assertEquals(List.of("legacyEngine"), order);

        order.clear();
        Parser forJp2 = ContentEnrichers.get(enrichers, MediaType.image("jp2"), context);
        assertNotNull(forJp2);
        invoke(forJp2, new Metadata(), context);
        assertEquals(List.of("legacyEngine"), order,
                "real + pseudo advertisement of the same type must run once");
    }

    @Test
    public void testRecursionGuard() throws Exception {
        ParseContext context = new ParseContext();
        // an enricher that tries to re-enter enrichment from inside its own parse
        Parser reentrant = new Parser() {
            private static final long serialVersionUID = 1L;

            @Override
            public Set<MediaType> getSupportedTypes(ParseContext ctx) {
                return Collections.singleton(PNG);
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext ctx) {
                metadata.set("nested-enricher",
                        ContentEnrichers.get(
                                ctx.get(CompositeContentEnricher.class), PNG, ctx) == null
                                ? "refused" : "allowed");
            }
        };
        CompositeContentEnricher enrichers = new CompositeContentEnricher(List.of(reentrant));
        context.set(CompositeContentEnricher.class, enrichers);

        Parser enricher = ContentEnrichers.get(enrichers, PNG, context);
        assertNotNull(enricher);
        Metadata metadata = new Metadata();
        invoke(enricher, metadata, context);
        assertEquals("refused", metadata.get("nested-enricher"));

        // and enrichment is available again once the first one completes
        assertNotNull(ContentEnrichers.get(enrichers, PNG, context));
    }

    /** Load-time resolution applies the discovery rules once, for every type. */
    @Test
    public void testResolveOneEnginePerType() throws Exception {
        MediaType tiff = MediaType.image("tiff");
        RecognizingParser spiFirst = new RecognizingParser(Set.of(PNG, tiff), true);
        RecognizingParser spiLast = new RecognizingParser(Set.of(tiff), true);
        RecognizingParser configured = new RecognizingParser(Set.of(PNG), true);
        CompositeContentEnricher resolved = ContentEnrichers.resolve(
                compositeOf(configured, spiDefaults(spiFirst, spiLast)));
        assertEquals(Set.of(PNG, tiff), resolved.getSupportedTypes());
        assertEquals(List.of(configured), resolved.getEnrichers(PNG),
                "an engine configured under parsers wins its types");
        assertEquals(List.of(spiLast), resolved.getEnrichers(tiff),
                "within the default tier the last claimant wins");
        assertFalse(resolved.isLegacyClaimant(configured));

        ParseContext context = new ParseContext();
        assertTrue(ContentEnrichers.hasTextRecognizer(resolved, PNG, context));
        invoke(ContentEnrichers.get(resolved, tiff, context), new Metadata(), context);
        assertEquals(1, spiLast.calls);
        assertEquals(0, spiFirst.calls);

        // a filter on the default-parser entry applies to the engines inside it
        resolved = ContentEnrichers.resolve(compositeOf(ParserDecorator.withMimeFilters(
                spiDefaults(spiFirst, spiLast), null, Set.of(tiff))));
        assertEquals(Set.of(PNG), resolved.getSupportedTypes());
        assertEquals(List.of(spiFirst), resolved.getEnrichers(PNG));

        assertTrue(ContentEnrichers.resolve(compositeOf(new RecordingParser(Set.of(PNG))))
                .isEmpty(), "a tree without enrichers resolves to nothing");
    }

    /** A legacy image/ocr-* claimant counts as a text recognizer on both paths until 5.0. */
    @Test
    public void testLegacyClaimantIsRecognizerOnBothPaths() {
        RecordingParser legacy = new RecordingParser(Set.of(OCR_PNG));
        ParseContext context = new ParseContext();
        assertTrue(ContentEnrichers.hasTextRecognizer(listOf(legacy), PNG, context),
                "named in the list");
        CompositeContentEnricher resolved =
                ContentEnrichers.resolve(compositeOf(spiDefaults(legacy)));
        assertEquals(List.of(legacy), resolved.getEnrichers(PNG));
        assertTrue(resolved.isLegacyClaimant(legacy));
        assertTrue(ContentEnrichers.hasTextRecognizer(resolved, PNG, context), "resolved");
        assertFalse(ContentEnrichers.hasTextRecognizer(
                listOf(new AnnotatingParser(Set.of(PNG))), PNG, context),
                "an enricher advertising real types without the capability is not one");
    }
}
