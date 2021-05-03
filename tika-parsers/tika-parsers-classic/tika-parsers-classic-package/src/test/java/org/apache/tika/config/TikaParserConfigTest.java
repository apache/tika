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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.executable.ExecutableParser;
import org.apache.tika.parser.xml.XMLParser;

/**
 * Junit test class for {@link TikaConfig}, which cover things
 * that {@link TikaConfigTest} can't do due to a need for the
 * full set of parsers
 */
public class TikaParserConfigTest extends AbstractTikaConfigTest {
    @Test
    public void testMimeExcludeInclude() throws Exception {
        TikaConfig config = getConfig("TIKA-1558-exclude.xml");
        assertNotNull(config.getParser());
        assertNotNull(config.getDetector());
        Parser parser = config.getParser();

        MediaType PDF = MediaType.application("pdf");
        MediaType JPEG = MediaType.image("jpeg");


        // Has two parsers
        assertEquals(CompositeParser.class, parser.getClass());
        CompositeParser cParser = (CompositeParser) parser;
        assertEquals(2, cParser.getAllComponentParsers().size());

        // Both are decorated
        assertTrue(cParser.getAllComponentParsers().get(0) instanceof ParserDecorator);
        assertTrue(cParser.getAllComponentParsers().get(1) instanceof ParserDecorator);
        ParserDecorator p0 = (ParserDecorator) cParser.getAllComponentParsers().get(0);
        ParserDecorator p1 = (ParserDecorator) cParser.getAllComponentParsers().get(1);


        // DefaultParser will be wrapped with excludes
        assertEquals(DefaultParser.class, p0.getWrappedParser().getClass());

        assertNotContained(PDF, p0.getSupportedTypes(context));
        assertContains(PDF, p0.getWrappedParser().getSupportedTypes(context));
        assertNotContained(JPEG, p0.getSupportedTypes(context));
        assertContains(JPEG, p0.getWrappedParser().getSupportedTypes(context));


        // Will have an empty parser for PDF
        assertEquals(EmptyParser.class, p1.getWrappedParser().getClass());
        assertEquals(1, p1.getSupportedTypes(context).size());
        assertContains(PDF, p1.getSupportedTypes(context));
        assertNotContained(PDF, p1.getWrappedParser().getSupportedTypes(context));
    }

    @Test
    public void testParserExcludeFromDefault() throws Exception {
        TikaConfig config = getConfig("TIKA-1558-exclude.xml");
        assertNotNull(config.getParser());
        assertNotNull(config.getDetector());
        CompositeParser parser = (CompositeParser) config.getParser();

        MediaType PE_EXE = MediaType.application("x-msdownload");
        MediaType ELF = MediaType.application("x-elf");


        // Get the DefaultParser from the config
        ParserDecorator confWrappedParser =
                (ParserDecorator) parser.getParsers().get(MediaType.APPLICATION_XML);
        assertNotNull(confWrappedParser);
        DefaultParser confParser = (DefaultParser) confWrappedParser.getWrappedParser();

        // Get a fresh "default" DefaultParser
        DefaultParser normParser = new DefaultParser(config.getMediaTypeRegistry());


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


        // The one from the config won't
        assertNotContained(PE_EXE, confParser.getSupportedTypes(context));
        assertNotContained(ELF, confParser.getSupportedTypes(context));

        for (Parser p : confParser.getParsers().values()) {
            if (p instanceof ExecutableParser) {
                fail("Shouldn't have the Executable Parser from config");
            }
        }
    }

    /**
     * TIKA-1558 It should be possible to exclude Parsers from being picked up by
     * DefaultParser.
     */
    @Test
    public void defaultParserExclude() throws Exception {
        TikaConfig config = new TikaConfig();
        assertNotNull(config.getParser());
        assertNotNull(config.getDetector());
        CompositeParser cp = (CompositeParser) config.getParser();
        List<Parser> parsers = cp.getAllComponentParsers();

        boolean hasXML = false;
        for (Parser p : parsers) {
            if (p instanceof XMLParser) {
                hasXML = true;
                break;
            }
        }
        assertTrue("Default config should include an XMLParser.", hasXML);

        // This custom TikaConfig should exclude XMLParser and all of its subclasses.
        config = getConfig("TIKA-1558-excludesub.xml");
        cp = (CompositeParser) config.getParser();
        parsers = cp.getAllComponentParsers();

        for (Parser p : parsers) {
            if (p instanceof XMLParser) {
                fail("Custom config should not include an XMLParser (" + p.getClass() + ").");
            }
        }
    }
}
