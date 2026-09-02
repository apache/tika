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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.enricher.CompositeContentEnricher;

public class ContentEnricherLoaderTest {

    @TempDir
    Path tmp;

    private TikaLoader load(String json) throws Exception {
        Path config = tmp.resolve("tika-config.json");
        Files.writeString(config, json);
        return TikaLoader.load(config);
    }

    @Test
    public void testContentEnrichersLoadAndInject() throws Exception {
        TikaLoader loader = load("""
                {
                  "parsers": [ {"enriching-test-parser": {}} ],
                  "content-enrichers": [ {"test-png-enricher": {}} ]
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

    @Test
    public void testZeroTypeEnricherFailsLoad() throws Exception {
        // an explicitly named engine that cannot run (missing binary, unreachable
        // server) must fail config load, not become a silent no-op
        TikaLoader loader = load("""
                {
                  "content-enrichers": [ {"test-unavailable-enricher": {}} ]
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
    public void testNoContentEnrichersConfigured() throws Exception {
        TikaLoader loader = load("""
                {
                  "parsers": [ {"enriching-test-parser": {}} ]
                }
                """);
        assertNull(loader.get(CompositeContentEnricher.class));
        EnrichingTestParser enrichingParser = findEnrichingParser(loader.get(Parser.class));
        assertNotNull(enrichingParser);
        assertNull(enrichingParser.getContentEnrichers());
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
