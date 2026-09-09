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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.enricher.ContentEnricher;
import org.apache.tika.sax.BodyContentHandler;

public class CompositeParserTest {

    @Test
    @SuppressWarnings("serial")
    public void testFindDuplicateParsers() {
        Parser a = new EmptyParser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.TEXT_PLAIN);
            }
        };
        Parser b = new EmptyParser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.TEXT_PLAIN);
            }
        };
        Parser c = new EmptyParser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.OCTET_STREAM);
            }
        };

        CompositeParser composite =
                new CompositeParser(MediaTypeRegistry.getDefaultRegistry(), a, b, c);
        Map<MediaType, List<Parser>> duplicates =
                composite.findDuplicateParsers(new ParseContext());
        assertEquals(1, duplicates.size());
        List<Parser> parsers = duplicates.get(MediaType.TEXT_PLAIN);
        assertNotNull(parsers);
        assertEquals(2, parsers.size());
        assertEquals(a, parsers.get(0));
        assertEquals(b, parsers.get(1));
    }

    /** An enricher fills only the types no parser claims, whichever is registered later. */
    @Test
    @SuppressWarnings("serial")
    public void testEnricherFillsGapsOnly() {
        MediaType png = MediaType.image("png");
        MediaType jp2 = MediaType.image("jp2");
        Parser imageParser = new EmptyParser() {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(png);
            }
        };
        class Engine extends EmptyParser implements ContentEnricher {
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Set.of(png, jp2);
            }
        }
        Parser engine = new Engine();
        MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();
        for (CompositeParser composite : new CompositeParser[]{
                new CompositeParser(registry, imageParser, engine),
                new CompositeParser(registry, engine, imageParser)}) {
            Map<MediaType, Parser> map = composite.getParsers(new ParseContext());
            assertSame(imageParser, map.get(png));
            assertSame(engine, map.get(jp2));
        }
        CompositeParser composite = new CompositeParser(registry, imageParser,
                ParserDecorator.withTypes(engine, Set.of(png, jp2)));
        assertSame(imageParser, composite.getParsers(new ParseContext()).get(png));
    }

    /** An SPI-registered enricher; see {@link #servicesFor}. */
    public static class SpiEngine extends EmptyParser implements ContentEnricher {
        private static final long serialVersionUID = 1L;

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Set.of(MediaType.image("png"), MediaType.image("jp2"));
        }
    }

    private static ClassLoader servicesFor(Path dir, Class<? extends Parser> engine)
            throws IOException {
        Path services = dir.resolve("META-INF/services/" + Parser.class.getName());
        Files.createDirectories(services.getParent());
        Files.writeString(services, engine.getName() + "\n");
        return new URLClassLoader(new URL[]{dir.toUri().toURL()},
                CompositeParserTest.class.getClassLoader());
    }

    /**
     * An SPI enricher never dispatches, so its types cannot leak out of default-parser and
     * displace a parser configured beside it.
     */
    @Test
    @SuppressWarnings("serial")
    public void testDefaultParserNeverDispatchesToSpiEnricher(@TempDir Path tmp)
            throws Exception {
        MediaType png = MediaType.image("png");
        MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();
        DefaultParser defaults = new DefaultParser(registry,
                new ServiceLoader(servicesFor(tmp, SpiEngine.class), false));
        ParseContext context = new ParseContext();
        assertTrue(defaults.getAllComponentParsers().stream()
                .anyMatch(p -> p instanceof SpiEngine), "the SPI loaded the engine");
        assertNull(defaults.getParsers(context).get(png));
        assertNull(defaults.getParsers(context).get(MediaType.image("jp2")));

        // the documented "customize one parser" shape
        Parser imageParser = new EmptyParser() {
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(png);
            }
        };
        CompositeParser composite = new CompositeParser(registry, imageParser, defaults);
        assertSame(imageParser, composite.getParsers(context).get(png));
    }

    @Test
    public void testDefaultParser() throws Exception {
        DefaultParser parser = new DefaultParser();

        // Check it has the full registry
        assertEquals(MediaTypeRegistry.getDefaultRegistry(), parser.getMediaTypeRegistry());
    }

    @Test
    public void testMimeTypeAliases() throws Exception {
        MediaType bmpCanonical = MediaType.image("bmp");
        Map<String, String> bmpCanonicalMetadata = new HashMap<>();
        bmpCanonicalMetadata.put("BMP", "True");
        bmpCanonicalMetadata.put("Canonical", "True");
        Parser bmpCanonicalParser =
                new DummyParser(new HashSet<>(Collections.singletonList(bmpCanonical)),
                        bmpCanonicalMetadata, null);

        MediaType bmpAlias = MediaType.image("x-ms-bmp");
        Map<String, String> bmpAliasMetadata = new HashMap<>();
        bmpAliasMetadata.put("BMP", "True");
        bmpAliasMetadata.put("Alias", "True");
        Parser bmpAliasParser =
                new DummyParser(new HashSet<>(Collections.singletonList(bmpAlias)), bmpAliasMetadata,
                        null);

        MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();
        CompositeParser canonical =
                new CompositeParser(registry, bmpCanonicalParser);
        CompositeParser alias = new CompositeParser(registry, bmpAliasParser);
        CompositeParser both =
                new CompositeParser(registry, bmpCanonicalParser,
                        bmpAliasParser);

        ContentHandler handler = new BodyContentHandler();
        Metadata metadata;

        // Canonical and Canonical
        metadata = new Metadata();
        metadata.add(HttpHeaders.CONTENT_TYPE, bmpCanonical.toString());
        canonical.parse(TikaInputStream.get(new byte[0]), handler, metadata,
                new ParseContext());
        assertEquals("True", metadata.get("BMP"));
        assertEquals("True", metadata.get("Canonical"));


        // Alias and Alias
        metadata = new Metadata();
        metadata.add(HttpHeaders.CONTENT_TYPE, bmpAlias.toString());
        alias.parse(TikaInputStream.get(new byte[0]), handler, metadata, new ParseContext());
        assertEquals("True", metadata.get("BMP"));
        assertEquals("True", metadata.get("Alias"));


        // Alias type and Canonical parser
        metadata = new Metadata();
        metadata.add(HttpHeaders.CONTENT_TYPE, bmpAlias.toString());
        canonical.parse(TikaInputStream.get(new byte[0]), handler, metadata,
                new ParseContext());
        assertEquals("True", metadata.get("BMP"));
        assertEquals("True", metadata.get("Canonical"));


        // Canonical type and Alias parser
        metadata = new Metadata();
        metadata.add(HttpHeaders.CONTENT_TYPE, bmpCanonical.toString());
        alias.parse(TikaInputStream.get(new byte[0]), handler, metadata, new ParseContext());
        assertEquals("True", metadata.get("BMP"));
        assertEquals("True", metadata.get("Alias"));


        // And when both are there, will go for the last one
        //  to be registered (which is the alias one)
        metadata = new Metadata();
        metadata.add(HttpHeaders.CONTENT_TYPE, bmpCanonical.toString());
        both.parse(TikaInputStream.get(new byte[0]), handler, metadata, new ParseContext());
        assertEquals("True", metadata.get("BMP"));
        assertEquals("True", metadata.get("Alias"));
    }

    /**
     * A caller-pre-installed ParseRecord/ParseTimeout (as pipes does) must survive the
     * parse: replacing the ParseTimeout orphans the watchdog's reference and a healthy
     * parse gets killed as stalled.
     */
    @Test
    public void testPreInstalledRecordAndTimeoutSurviveFirstParse() throws Exception {
        Parser checkpointingParser = new EmptyParser() {
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(MediaType.TEXT_PLAIN);
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) {
                ParseTimeout.checkpoint(context);
            }
        };
        CompositeParser composite =
                new CompositeParser(MediaTypeRegistry.getDefaultRegistry(), checkpointingParser);

        ParseContext context = new ParseContext();
        context.set(TimeoutLimits.class, new TimeoutLimits(100_000, 100_000));
        // caller pre-installs, as PipesServer/ParseHandler do
        ParseTimeout watchdogHeld = ParseTimeout.getOrCreate(context);
        context.set(ParseRecord.class, ParseRecord.newInstance(context));
        ParseRecord preInstalledRecord = context.get(ParseRecord.class);

        long silentBefore = watchdogHeld.millisSinceLastProgress();
        Thread.sleep(20);

        Metadata metadata = new Metadata();
        metadata.add(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
        composite.parse(TikaInputStream.get(new byte[0]), new BodyContentHandler(), metadata,
                context);

        assertSame(watchdogHeld, context.get(ParseTimeout.class),
                "first parse must not replace a pre-installed ParseTimeout -- an external " +
                        "watchdog holds a reference to it");
        assertSame(preInstalledRecord, context.get(ParseRecord.class),
                "first parse must not replace a pre-installed, never-used ParseRecord");
        assertTrue(watchdogHeld.millisSinceLastProgress() < silentBefore + 20,
                "the parser's checkpoint must reach the watchdog-held ParseTimeout instance");
    }
}
