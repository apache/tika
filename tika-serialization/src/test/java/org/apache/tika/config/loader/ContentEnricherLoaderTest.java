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
package org.apache.tika.config.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.enricher.CompositeContentEnricher;
import org.apache.tika.parser.enricher.ContentEnrichers;
import org.apache.tika.parser.inference.EngineRegistry;
import org.apache.tika.utils.ParserUtils;

public class ContentEnricherLoaderTest {

    @TempDir
    Path tmp;

    private TikaLoader load(String json) throws Exception {
        Path config = tmp.resolve("tika-config.json");
        Files.writeString(config, json);
        return TikaLoader.load(config);
    }

    private static Metadata target(MediaType type) {
        Metadata target = new Metadata();
        target.set(HttpHeaders.CONTENT_TYPE, type.toString());
        return target;
    }

    @Test
    public void testContentEnrichersLoadAndInject() throws Exception {
        TikaLoader loader = load("""
                {
                  "parsers": [ {"enriching-test-parser": {}} ],
                  "text-recognizers": [ {"test-png-enricher": {}} ]
                }
                """);

        CompositeContentEnricher enrichers = loader.get(CompositeContentEnricher.class);
        assertNotNull(enrichers);
        java.util.List<Parser> matched = enrichers.getEnrichers(MediaType.image("png"));
        assertEquals(1, matched.size());
        assertTrue(matched.get(0) instanceof TestPngEnricher,
                "expected TestPngEnricher, got " + matched.get(0));
        assertEquals(1, enrichers.getSupportedTypes().size());

        EnrichingTestParser enrichingParser = findEnrichingParser(loader.get(Parser.class));
        assertNotNull(enrichingParser, "enriching-test-parser not found in loaded parsers");
        assertNotNull(enrichingParser.getContentEnrichers(),
                "content enrichers were not injected into the EnrichingParser");
        assertEquals(enrichers, enrichingParser.getContentEnrichers());
    }

    /** An entry names an engine configured once under "engines"; one instance serves both. */
    @Test
    public void testEntryNamesAnEngine() throws Exception {
        TikaLoader loader = load("""
                {
                  "engines": { "png": { "mock-enricher": {} } },
                  "text-recognizers": [ { "engine": "png", "_mime-include": ["image/png"] } ]
                }
                """);
        CompositeContentEnricher enrichers = loader.get(CompositeContentEnricher.class);
        List<Parser> matched = enrichers.getEnrichers(MediaType.image("png"));
        assertEquals(1, matched.size());
        Parser entry = matched.get(0);
        assertTrue(entry instanceof ParserDecorator, "the entry's mime filter wraps the engine");
        assertSame(loader.get(EngineRegistry.class).get("png"), unwrap(entry));
        assertTrue(ContentEnrichers.isEnricher(entry));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{ \"engines\": { \"png\": { \"mock-enricher\": {} } }, \"text-recognizers\": [ { \"engine\": \"nope\" } ] }| is not in \"engines\"",
            "{ \"text-recognizers\": [ { \"engine\": \"png\" } ] }| is not in \"engines\"",
            "{ \"engines\": { \"plain\": { \"test-engine\": {} } }, \"text-recognizers\": [ { \"engine\": \"plain\" } ] }| is not a text recognizer",
            "{ \"engines\": { \"png\": { \"mock-enricher\": {} } }, \"text-recognizers\": [ { \"engine\": \"png\", \"language\": \"eng\" } ] }| unknown key \"language\"",
            "{ \"engines\": { \"png\": { \"mock-enricher\": {} } }, \"text-recognizers\": [ { \"engine\": 3 } ] }| must be a name",
            "{ \"text-recognizers\": [ 3 ] }| entries are"
    })
    public void testEngineReferenceMisconfigurationsFailLoad(String json, String message) {
        TikaConfigException e = assertThrows(TikaConfigException.class,
                () -> load(json).get(CompositeContentEnricher.class));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    private static Parser unwrap(Parser parser) {
        while (parser instanceof ParserDecorator decorator) {
            parser = decorator.getWrappedParser();
        }
        return parser;
    }

    @Test
    public void testZeroTypeEnricherFailsLoad() throws Exception {
        // a named engine that cannot run must fail load, not become a silent no-op
        TikaLoader loader = load("""
                {
                  "text-recognizers": [ {"test-unavailable-enricher": {}} ]
                }
                """);
        org.apache.tika.exception.TikaConfigException e =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.apache.tika.exception.TikaConfigException.class,
                        () -> loader.get(CompositeContentEnricher.class));
        assertTrue(e.getMessage().contains("advertises no media types"),
                "unexpected message: " + e.getMessage());
    }

    @Test
    public void testMimeExcludeReachesLegacyPseudoType() throws Exception {
        TikaLoader loader = load("""
                {
                  "text-recognizers": [
                    {"test-legacy-ocr-enricher": {"_mime-exclude": ["image/tiff"]}}
                  ]
                }
                """);
        CompositeContentEnricher enrichers = loader.get(CompositeContentEnricher.class);
        assertEquals(java.util.Set.of(MediaType.image("png")), enrichers.getSupportedTypes());
        assertTrue(enrichers.getEnrichers(MediaType.image("tiff")).isEmpty());
        assertEquals(1, enrichers.getEnrichers(MediaType.image("png")).size());
    }

    /** The pseudo-type spelling of a filter fails load with the real type in the message. */
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"text-recognizers\": [{\"test-legacy-ocr-enricher\": {\"_mime-exclude\": [\"image/ocr-tiff\"]}}]}",
        "{\"text-recognizers\": [{\"test-png-enricher\": {\"_mime-include\": [\"image/ocr-tiff\"]}}]}",
        "{\"parsers\": [{\"enriching-test-parser\": {\"_mime-exclude\": [\"image/ocr-tiff\"]}}]}",
        "{\"parsers\": [{\"default-parser\": {\"_mime-exclude\": [\"image/ocr-tiff\"]}}]}"})
    public void testLegacyPseudoTypeInFilterFailsLoad(String json) throws Exception {
        TikaLoader loader = load(json);
        org.apache.tika.exception.TikaConfigException e =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.apache.tika.exception.TikaConfigException.class, () -> {
                            loader.get(CompositeContentEnricher.class);
                            loader.get(Parser.class);
                        });
        String message = e.getMessage() + (e.getCause() == null ? "" : e.getCause().getMessage());
        assertTrue(message.contains("image/ocr-tiff") && message.contains("\"image/tiff\""),
                "unexpected message: " + message);
    }

    /**
     * A parser configured before default-parser keeps its type over an SPI enricher inside
     * default-parser, which is still discovered (TIKA-4884).
     */
    @Test
    public void testConfiguredParserKeepsTypeOverSpiEnricher() throws Exception {
        TikaLoader loader = load("""
                {
                  "parsers": [ {"minimal-test-parser": {}}, {"default-parser": {}} ]
                }
                """);
        MediaType type = MediaType.parse("application/test+minimal");
        CompositeParser parsers = (CompositeParser) loader.loadParsers();
        ParseContext context = new ParseContext();
        assertEquals(MinimalTestParser.class.getName(),
                ParserUtils.getParserClassname(parsers.getParsers(context).get(type)));
        context.set(Parser.class, parsers);
        Parser enricher = ContentEnrichers.get(null, type, target(type), context);
        assertNotNull(enricher, "the SPI enricher is still discovered for the type");
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            enricher.parse(tis, new DefaultHandler(), metadata, context);
        }
        assertEquals("test-spi-enricher", metadata.get("derived-by"));
    }

    @Test
    public void testMimeIncludeDoesNotMaskUnavailableEngine() throws Exception {
        TikaLoader loader = load("""
                {
                  "text-recognizers": [
                    {"test-unavailable-enricher": {"_mime-include": ["image/png"]}}
                  ]
                }
                """);
        org.apache.tika.exception.TikaConfigException e =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.apache.tika.exception.TikaConfigException.class,
                        () -> loader.get(CompositeContentEnricher.class));
        assertTrue(e.getMessage().contains("advertises no media types"),
                "unexpected message: " + e.getMessage());
    }

    @Test
    public void testMimeIncludeNarrowsLegacyEngine() throws Exception {
        TikaLoader loader = load("""
                {
                  "text-recognizers": [
                    {"test-legacy-ocr-enricher": {"_mime-include": ["image/png"]}}
                  ]
                }
                """);
        CompositeContentEnricher enrichers = loader.get(CompositeContentEnricher.class);
        assertEquals(java.util.Set.of(MediaType.image("png")), enrichers.getSupportedTypes());
    }

    /** With no list, the loader resolves one engine per type from the loaded parsers. */
    @Test
    public void testAbsentListResolvesFromLoadedParsers() throws Exception {
        TikaLoader loader = load("""
                {
                  "parsers": [ {"enriching-test-parser": {}}, {"default-parser": {}} ]
                }
                """);
        assertNull(loader.get(CompositeContentEnricher.class), "nothing was configured");
        EnrichingTestParser enrichingParser = findEnrichingParser(loader.get(Parser.class));
        assertNotNull(enrichingParser);
        CompositeContentEnricher resolved = enrichingParser.getContentEnrichers();
        assertNotNull(resolved, "the resolved composite is injected");
        MediaType type = MediaType.parse("application/test+minimal");
        assertEquals(1, resolved.getEnrichers(type).size());
        assertTrue(resolved.getEnrichers(type).get(0) instanceof TestSpiEnricher);

        loader = load("""
                {
                  "parsers": [ {"enriching-test-parser": {}} ]
                }
                """);
        enrichingParser = findEnrichingParser(loader.get(Parser.class));
        assertNotNull(enrichingParser.getContentEnrichers());
        assertTrue(enrichingParser.getContentEnrichers().isEmpty(),
                "no enricher among the loaded parsers: empty, not null");
    }

    /** An empty list is an explicit off switch, unlike an absent key. */
    @Test
    public void testEmptyListDisablesEnrichment() throws Exception {
        TikaLoader loader = load("""
                {
                  "parsers": [ {"enriching-test-parser": {}}, {"default-parser": {}} ],
                  "text-recognizers": []
                }
                """);
        CompositeContentEnricher enrichers = loader.get(CompositeContentEnricher.class);
        assertNotNull(enrichers);
        assertTrue(enrichers.isEmpty());
        EnrichingTestParser enrichingParser = findEnrichingParser(loader.get(Parser.class));
        assertEquals(enrichers, enrichingParser.getContentEnrichers());
        ParseContext context = new ParseContext();
        context.set(Parser.class, loader.get(Parser.class));
        assertNull(ContentEnrichers.get(enrichers, MediaType.parse("application/test+minimal"),
                target(MediaType.parse("application/test+minimal")),
                context), "the SPI enricher in default-parser is not consulted");
    }

    /**
     * An enricher under "parsers" whose every type another parser claims is never dispatched
     * to; the loader names it so a config that looks like a parser choice is not a silent
     * enricher choice.
     */
    @Test
    public void testUndispatchedEnricherUnderParsersIsReported() throws Exception {
        TikaLoader loader = load("""
                {
                  "parsers": [ {"minimal-test-parser": {}}, {"test-spi-enricher": {}} ]
                }
                """);
        java.util.List<Parser> inert = ParserLoader.undispatchedEnrichers(loader.loadParsers());
        assertEquals(1, inert.size());
        assertTrue(inert.get(0) instanceof TestSpiEnricher);

        loader = load("""
                {
                  "parsers": [ {"test-spi-enricher": {}} ]
                }
                """);
        assertTrue(ParserLoader.undispatchedEnrichers(loader.loadParsers()).isEmpty(),
                "alone it is the parser for its type");
    }

    /** Two text recognizers on one type both run; the loader names them at startup. */
    @Test
    public void testOverlappingTextRecognizersAreReported() throws Exception {
        TikaLoader loader = load("""
                {
                  "text-recognizers": [
                    {"test-png-recognizer": {}}, {"test-legacy-ocr-enricher": {}}
                  ]
                }
                """);
        java.util.Map<java.util.List<Parser>, java.util.Set<MediaType>> overlaps =
                ParserLoader.overlappingTextRecognizers(loader.get(CompositeContentEnricher.class));
        assertEquals(1, overlaps.size());
        java.util.Map.Entry<java.util.List<Parser>, java.util.Set<MediaType>> overlap =
                overlaps.entrySet().iterator().next();
        assertEquals(2, overlap.getKey().size(), "the legacy claimant counts as a recognizer");
        assertEquals(java.util.Set.of(MediaType.image("png")), overlap.getValue(),
                "only the shared type; the legacy engine's tiff is not an overlap");

        loader = load("""
                {
                  "text-recognizers": [
                    {"test-png-recognizer": {}}, {"test-png-enricher": {}}
                  ]
                }
                """);
        assertTrue(ParserLoader.overlappingTextRecognizers(
                loader.get(CompositeContentEnricher.class)).isEmpty(),
                "a recognizer beside an annotator is the intended shape");
    }

    private EnrichingTestParser findEnrichingParser(Parser parser) {
        if (parser instanceof EnrichingTestParser dtp) {
            return dtp;
        }
        if (parser instanceof CompositeParser cp) {
            for (Parser child : cp.getAllComponentParsers()) {
                EnrichingTestParser found = findEnrichingParser(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
