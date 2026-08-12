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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
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
        metadata.add(Metadata.CONTENT_TYPE, bmpCanonical.toString());
        canonical.parse(TikaInputStream.get(new byte[0]), handler, metadata,
                new ParseContext());
        assertEquals("True", metadata.get("BMP"));
        assertEquals("True", metadata.get("Canonical"));


        // Alias and Alias
        metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, bmpAlias.toString());
        alias.parse(TikaInputStream.get(new byte[0]), handler, metadata, new ParseContext());
        assertEquals("True", metadata.get("BMP"));
        assertEquals("True", metadata.get("Alias"));


        // Alias type and Canonical parser
        metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, bmpAlias.toString());
        canonical.parse(TikaInputStream.get(new byte[0]), handler, metadata,
                new ParseContext());
        assertEquals("True", metadata.get("BMP"));
        assertEquals("True", metadata.get("Canonical"));


        // Canonical type and Alias parser
        metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, bmpCanonical.toString());
        alias.parse(TikaInputStream.get(new byte[0]), handler, metadata, new ParseContext());
        assertEquals("True", metadata.get("BMP"));
        assertEquals("True", metadata.get("Alias"));


        // And when both are there, will go for the last one
        //  to be registered (which is the alias one)
        metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, bmpCanonical.toString());
        both.parse(TikaInputStream.get(new byte[0]), handler, metadata, new ParseContext());
        assertEquals("True", metadata.get("BMP"));
        assertEquals("True", metadata.get("Alias"));
    }

    /**
     * Pipes' ParseHandler pre-installs a fresh ParseRecord (and PipesServer a
     * ParseTimeout its watchdog holds a reference to) before the parse. Replacing
     * either would orphan the watchdog's reference, so its stall detector never
     * sees another checkpoint and kills a healthy parse.
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
        metadata.add(Metadata.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
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
