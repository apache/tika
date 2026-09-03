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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

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

    private static void invoke(Parser enricher, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            enricher.parse(tis, new DefaultHandler(), metadata, context);
        }
    }

    @Test
    public void testExplicitEnricherWinsOverLegacy() throws Exception {
        RecordingParser explicit = new RecordingParser(Collections.singleton(PNG));
        RecordingParser composite = new RecordingParser(Collections.singleton(OCR_PNG));
        CompositeContentEnricher enrichers =
                new CompositeContentEnricher(List.of(explicit));
        ParseContext context = new ParseContext();
        context.set(Parser.class, composite);

        Parser enricher = ContentEnrichers.get(enrichers, PNG, context);
        assertNotNull(enricher);
        invoke(enricher, new Metadata(), context);
        assertEquals(1, explicit.calls);
        assertEquals(0, composite.calls);
        // the explicit path never mints the pseudo-mime
        assertNull(explicit.overrideSeenDuringParse);
    }

    @Test
    public void testLegacyFallbackMintsAndRestores() throws Exception {
        RecordingParser composite = new RecordingParser(Collections.singleton(OCR_PNG));
        ParseContext context = new ParseContext();
        context.set(Parser.class, composite);

        Parser enricher = ContentEnrichers.get(null, PNG, context);
        assertNotNull(enricher);

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, PNG.toString());
        invoke(enricher, metadata, context);

        assertEquals(1, composite.calls);
        assertEquals(OCR_PNG.toString(), composite.overrideSeenDuringParse);
        assertNull(metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE));
        assertEquals(PNG.toString(), metadata.get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testNoneAvailable() {
        ParseContext context = new ParseContext();
        assertNull(ContentEnrichers.get(null, PNG, context));
        // composite that claims nothing
        context.set(Parser.class, new RecordingParser(Collections.emptySet()));
        assertNull(ContentEnrichers.get(null, PNG, context));
        assertNull(ContentEnrichers.get(null, null, context));
    }

    @Test
    public void testConfiguredListIsAuthoritative() throws Exception {
        // the composite claims ocr-tiff, but a list that doesn't cover tiff wins anyway
        RecordingParser explicit = new RecordingParser(Collections.singleton(PNG));
        RecordingParser composite =
                new RecordingParser(Collections.singleton(MediaType.image("ocr-tiff")));
        CompositeContentEnricher enrichers = new CompositeContentEnricher(List.of(explicit));
        ParseContext context = new ParseContext();
        context.set(Parser.class, composite);

        assertNull(ContentEnrichers.get(enrichers, MediaType.image("tiff"), context));
        // with no list configured, the same composite is reachable via legacy dispatch
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
        invoke(enricher, new Metadata(), context);
        assertEquals(List.of("first", "second"), order);
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
}
