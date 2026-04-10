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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.executable.ExecutableParser;
import org.apache.tika.parser.xml.XMLParser;

/**
 * Junit test class for parser configuration via JSON,
 * covering things that require the full set of parsers.
 */
public class TikaParserConfigTest extends TikaTest {

    protected static ParseContext context = new ParseContext();

    private TikaLoader getLoader(String config) throws Exception {
        Path path = Paths.get(TikaParserConfigTest.class.getResource(config).toURI());
        return TikaLoader.load(path);
    }

    @Test
    public void testMimeExcludeInclude() throws Exception {
        TikaLoader loader = getLoader("TIKA-1558-exclude.json");
        Parser parser = loader.loadParsers();
        assertNotNull(parser);
        assertNotNull(loader.loadDetectors());

        MediaType PDF = MediaType.application("pdf");
        MediaType JPEG = MediaType.image("jpeg");

        // Has two parsers: EmptyParser (decorated) and CompositeParser of SPI parsers (decorated)
        assertEquals(CompositeParser.class, parser.getClass());
        CompositeParser cParser = (CompositeParser) parser;
        assertEquals(2, cParser.getAllComponentParsers().size());

        // First parser should be EmptyParser decorated with mimeInclude for PDF
        Parser p0 = cParser.getAllComponentParsers().get(0);
        assertTrue(p0 instanceof ParserDecorator, "First parser should be decorated");
        ParserDecorator pd0 = (ParserDecorator) p0;
        assertEquals(EmptyParser.class, pd0.getWrappedParser().getClass());
        Set<MediaType> p0Types = pd0.getSupportedTypes(context);
        assertContains(PDF, p0Types);
        assertEquals(1, p0Types.size());

        // Second parser should be SPI parsers decorated with mimeExclude for PDF/JPEG
        Parser p1 = cParser.getAllComponentParsers().get(1);
        assertTrue(p1 instanceof ParserDecorator, "Second parser should be decorated");
        ParserDecorator pd1 = (ParserDecorator) p1;
        Set<MediaType> p1Types = pd1.getSupportedTypes(context);
        assertNotContained(PDF, p1Types);
        assertNotContained(JPEG, p1Types);
    }

    @Test
    public void testParserExcludeFromDefault() throws Exception {
        TikaLoader loader = getLoader("TIKA-1558-exclude.json");
        Parser parser = loader.loadParsers();
        assertNotNull(parser);

        MediaType PE_EXE = MediaType.application("x-msdownload");
        MediaType ELF = MediaType.application("x-elf");

        // Get a fresh "default" DefaultParser for comparison
        DefaultParser normParser = new DefaultParser(TikaLoader.getMediaTypeRegistry());

        // The default one will offer the Executable Parser
        assertContains(PE_EXE, normParser.getSupportedTypes(context));
        assertContains(ELF, normParser.getSupportedTypes(context));

        boolean hasExec = false;
        for (Parser p : normParser.getParsers().values()) {
            if (p instanceof ExecutableParser) {
                hasExec = true;
                break;
            }
        }
        assertTrue(hasExec);

        // The config-loaded parser should NOT support executable types
        // (ExecutableParser was excluded)
        CompositeParser cParser = (CompositeParser) parser;
        Set<MediaType> supportedTypes = cParser.getSupportedTypes(context);
        assertNotContained(PE_EXE, supportedTypes);
        assertNotContained(ELF, supportedTypes);
    }

    /**
     * TIKA-1558 It should be possible to exclude Parsers from being picked up by
     * DefaultParser.
     */
    @Test
    public void defaultParserExclude() throws Exception {
        // First verify default config includes XMLParser
        TikaLoader defaultLoader = TikaLoader.loadDefault();
        CompositeParser cp = (CompositeParser) defaultLoader.loadParsers();
        List<Parser> parsers = cp.getAllComponentParsers();

        boolean hasXML = false;
        for (Parser p : parsers) {
            if (p instanceof XMLParser) {
                hasXML = true;
                break;
            }
        }
        assertTrue(hasXML, "Default config should include an XMLParser.");

        // This custom config should exclude XMLParser
        TikaLoader loader = getLoader("TIKA-1558-excludesub.json");
        cp = (CompositeParser) loader.loadParsers();
        parsers = cp.getAllComponentParsers();

        // Flatten nested CompositeParser if present
        for (Parser p : parsers) {
            if (p instanceof CompositeParser) {
                for (Parser inner : ((CompositeParser) p).getAllComponentParsers()) {
                    if (inner instanceof XMLParser) {
                        fail("Custom config should not include an XMLParser (" + inner.getClass() + ").");
                    }
                }
            } else if (p instanceof ParserDecorator) {
                Parser wrapped = ((ParserDecorator) p).getWrappedParser();
                if (wrapped instanceof XMLParser) {
                    fail("Custom config should not include an XMLParser (" + wrapped.getClass() + ").");
                }
                if (wrapped instanceof CompositeParser) {
                    for (Parser inner : ((CompositeParser) wrapped).getAllComponentParsers()) {
                        if (inner instanceof XMLParser) {
                            fail("Custom config should not include an XMLParser (" + inner.getClass() + ").");
                        }
                    }
                }
            } else if (p instanceof XMLParser) {
                fail("Custom config should not include an XMLParser (" + p.getClass() + ").");
            }
        }
    }

    @Test
    public void testDefaultLoaderIncludesAllParsers() throws Exception {
        TikaLoader loader = TikaLoader.loadDefault();
        Parser parser = loader.loadParsers();
        assertNotNull(parser);
        assertTrue(parser instanceof CompositeParser);

        CompositeParser cp = (CompositeParser) parser;
        // Should have many parsers loaded from SPI
        assertFalse(cp.getAllComponentParsers().isEmpty());
    }
}
